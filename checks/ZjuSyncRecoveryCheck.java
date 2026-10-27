package com.donglan.chrona;

import com.donglan.chrona.zju.ZjuClient;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.lang.reflect.Field;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import okhttp3.*;

/** Synthetic in-memory HTTP and JCE key fixtures; no external network or real credentials. */
public final class ZjuSyncRecoveryCheck {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void field(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static ZjuClient fixture(boolean homeworkAuth,boolean timetableAuth,boolean noActive,boolean cancel)throws Exception{
        ZjuClient client=new ZjuClient("fixture-user","fixture-password");
        field(client,"coursesLoggedIn",true);field(client,"timetableLoggedIn",true);
        OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();String path=request.url().encodedPath(),body="{}";int code=200;
            if(cancel){client.cancel();body="{}";}
            else if(request.url().host().equals("courses.zju.edu.cn")){
                if(homeworkAuth)code=401;
                else if(path.equals("/api/my-semesters"))body="{\"semesters\":[{\"id\":\"current\",\"is_active\":"+!noActive+"}]}";
                else if(path.equals("/api/my-courses"))body="{\"pages\":1,\"courses\":[{\"id\":\"new\",\"name\":\"Current course\",\"semester_id\":\"current\"},{\"id\":\"old\",\"name\":\"Previous course\",\"semester_id\":\"previous\"},{\"id\":\"untracked\",\"name\":\"Untracked\",\"semester_id\":\"previous\"}]}";
                else if(path.endsWith("/activities"))body="{\"activities\":[{\"type\":\"homework\",\"published\":true,\"id\":\"a\",\"title\":\"Homework\",\"deadline\":\"2026-10-20T23:59:00+08:00\"}]}";
                else if(path.endsWith("/submission-status"))body="{\"homework_activities\":[{\"id\":\"a\",\"status_code\":\"submitted\"}]}";
                else throw new AssertionError("Unexpected school endpoint: "+path);
            }else{
                if(timetableAuth)code=403;
                else if(path.endsWith("index_cxXiaoLi.html"))body="";
                else if(path.endsWith("index_cxFilterGnmkdm.html"))body="{\"dyym\":\"/kbcx/xskbcx_cxXskbcxIndex.html\",\"gnmkdm\":\"test\"}";
                else if(path.endsWith("xskbcx_cxXskbcxIndex.html"))body="<select id='xnm'><option value='2026-2027' selected>Year</option></select><select id='xqm'><option value='1|秋' selected>Term</option></select>";
                else if(path.endsWith("xskbcx_cxXsKb.html"))body="{\"kbList\":[]}";
                else throw new AssertionError("Unexpected timetable endpoint: "+path);
            }
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(ResponseBody.create(body,MediaType.get("application/json"))).build();
        }).build();
        field(client,"http",http);return client;
    }
    public static void main(String[]args)throws Exception{
        try(ZjuClient client=fixture(false,true,false,false)){
            ZjuSnapshot result=client.fetch(true,true,Set.of("old","missing"));
            check(result.homework.size()==2,"current plus previously tracked term fetched, unrelated old term excluded");
            check(result.homework.stream().anyMatch(h->h.title.equals("Previous course · Homework")&&Boolean.TRUE.equals(h.submitted)&&h.deadline!=null),"official course title/deadline/submission retained for old course");
            check(result.warnings.stream().anyMatch(w->w.contains("课表认证失败")),"successful homework survives timetable authentication failure with warning");
            check(result.warnings.stream().anyMatch(w->w.contains("未返回部分已追踪课程")),"absent tracked course warns and preserves local rows");
            check(result.completeTimetableTerms.isEmpty(),"failed timetable cannot replace cached terms");
        }
        try(ZjuClient client=fixture(true,false,false,false)){
            ZjuSnapshot result=client.fetch(true,true,Set.of());
            check(result.homework.isEmpty()&&!result.completeTimetableTerms.isEmpty(),"timetable still completes when homework authentication fails");
            check(result.warnings.stream().anyMatch(w->w.contains("作业认证失败")),"homework auth failure is visible");
        }
        try(ZjuClient client=fixture(true,true,false,false)){
            boolean failed=false;try{client.fetch(true,true,Set.of());}catch(ZjuClient.AuthenticationException expected){failed=true;}
            check(failed,"all sources failing authentication is an error");
        }
        try(ZjuClient client=fixture(false,false,true,false)){
            ZjuSnapshot result=client.fetch(true,false,Set.of("old"));
            check(result.homework.size()==1&&result.homework.get(0).courseId.equals("old"),"no active semester only refreshes explicitly tracked courses");
        }
        try(ZjuClient client=fixture(false,false,false,true)){
            boolean failed=false;try{client.fetch(true,true,Set.of());}catch(java.io.IOException expected){failed=expected.getMessage().contains("取消");}
            check(failed,"cancellation immediately aborts instead of trying another source");
        }
        Security.addProvider(new Provider("AndroidKeyStore",1.0,"Fixture only"){{put("KeyStore.AndroidKeyStore",FixtureKeys.class.getName());}});
        android.content.Context context=new android.content.Context();ZjuSettingsStore settings=new ZjuSettingsStore(context);
        check(!settings.homeworkAutoPlan(),"new homework automatic planning defaults off");
        settings.homeworkAutoPlan(true);check(settings.homeworkAutoPlan(),"explicit authorization enables automatic planning");
        settings.save("fixture-user","first-password");String account=settings.account();
        context.prefs.putString("credentials","invalid-ciphertext");
        boolean failed=false;try{settings.save("fixture-user","");}catch(Exception expected){failed=true;}
        check(failed,"empty password requires readable prior credentials");
        settings.save("fixture-user","replacement-password");
        check(settings.account().equals(account)&&settings.credentials()[1].equals("replacement-password"),"explicit password replaces unreadable credentials and preserves same identity");
        settings.save("fixture-user","");check(settings.credentials()[1].equals("replacement-password"),"blank password retains readable same-user password");
        failed=false;try{settings.save("other-user","");}catch(Exception expected){failed=true;}
        check(failed,"blank password cannot transfer to different user");
        settings.save("other-user","other-password");check(!settings.account().equals(account),"different user has isolated account");
        context.prefs.putString("credentials","invalid-ciphertext");settings.clear();
        check(!settings.configured()&&settings.account().isEmpty(),"logout succeeds without decrypting corrupt credentials");
        check(!settings.homeworkAutoPlan(),"logout removes homework AI authorization");
        settings.save("fixture-user","restored-password");check(settings.account().equals(account),"logout retains opaque historical identity mapping");
        System.out.println("PASS: "+checks+" production ZJU fetch/recovery checks (intercepted HTTP and isolated JCE key fixture)");
    }
    public static class FixtureKeys extends KeyStoreSpi {
        static final Key key=new javax.crypto.spec.SecretKeySpec(new byte[16],"AES");
        public Key engineGetKey(String a,char[]p){return key;}public Certificate[] engineGetCertificateChain(String a){return null;}public Certificate engineGetCertificate(String a){return null;}public Date engineGetCreationDate(String a){return new Date();}
        public void engineSetKeyEntry(String a,Key k,char[]p,Certificate[]c){}public void engineSetKeyEntry(String a,byte[]k,Certificate[]c){}public void engineSetCertificateEntry(String a,Certificate c){}public void engineDeleteEntry(String a){}
        public Enumeration<String> engineAliases(){return Collections.enumeration(List.of("chrona_zju_credentials"));}public boolean engineContainsAlias(String a){return true;}public int engineSize(){return 1;}public boolean engineIsKeyEntry(String a){return true;}public boolean engineIsCertificateEntry(String a){return false;}public String engineGetCertificateAlias(Certificate c){return null;}public void engineStore(java.io.OutputStream o,char[]p){}public void engineLoad(java.io.InputStream i,char[]p){}
    }
}
