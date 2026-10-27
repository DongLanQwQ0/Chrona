package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.zju.*;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Normal-screen owned fixture: real official transport with a non-network interceptor. */
final class ZjuExamsChecks {
    private final ClassroomMonitorInstrumentation test;private final Bundle args;private final Context app;
    private ZjuExamsActivity screen;private ZjuExamsActivity.Session owned;private int checks;
    private final AtomicInteger calls=new AtomicInteger();
    private volatile int scenario;private volatile CountDownLatch gate,entered;
    private ZjuExamsChecks(ClassroomMonitorInstrumentation test,Bundle args){this.test=test;this.args=args;app=test.getTargetContext();}
    static int run(ClassroomMonitorInstrumentation test,Bundle args)throws Exception{
        ZjuExamsChecks c=new ZjuExamsChecks(test,args);
        if(!c.app.getPackageName().equals("com.donglan.chrona.monitor.validation")||!Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned AVD only");
        SharedPreferences prefs=c.app.getSharedPreferences("chrona_zju",0),appearance=c.app.getSharedPreferences("appearance",0);
        Map<String,?> saved=new HashMap<>(prefs.getAll()),theme=new HashMap<>(appearance.getAll());
        File cache=new File(c.app.getCacheDir(),"zju-exams.json");byte[] previous=cache.isFile()?java.nio.file.Files.readAllBytes(cache.toPath()):null;
        try{
            appearance.edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();
            if(!"true".equals(args.getString("uiOnly")))c.core();
            c.ui();return c.checks;
        }finally{
            if(c.gate!=null)c.gate.countDown();
            test.runOnMainSync(()->{if(c.owned!=null)c.owned.dispose();if(c.screen!=null)c.screen.finish();});
            restore(prefs,saved);restore(appearance,theme);
            if(previous==null)new ZjuExamStore(c.app).clear();else java.nio.file.Files.write(cache.toPath(),previous);
        }
    }
    private static void restore(SharedPreferences prefs,Map<String,?> values){
        SharedPreferences.Editor edit=prefs.edit().clear();
        for(var e:values.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String s)edit.putString(k,s);else if(v instanceof Boolean b)edit.putBoolean(k,b);else if(v instanceof Long l)edit.putLong(k,l);else if(v instanceof Integer i)edit.putInt(k,i);else if(v instanceof Float f)edit.putFloat(k,f);else if(v instanceof Set<?> set)edit.putStringSet(k,(Set<String>)set);}
        edit.commit();
    }
    private void core()throws Exception{
        JSONArray source=new JSONArray().put(row(0,"2026年11月07日(14:00-16:00)").put("qzkssj","2026年10月27日（09：00-10：30）").put("qzjsmc","紫金港西2-101").put("qzzwxh",121))
                .put(row(1,"2027年01月06日(08:00-10:00)").put("jsmc",JSONObject.NULL).put("zwxh",JSONObject.NULL));
        List<ZjuExam> parsed=ZjuExam.parse(source);
        check(parsed.size()==3,"ordinary and midterm slots both retained");
        check(parsed.stream().anyMatch(e->e.kind.equals("期中")&&e.seat.equals("121")&&e.room.equals("紫金港西2-101")),"midterm uses its own room and numeric seat");
        ZjuExam first=parsed.get(0);
        check(first.displayTime().equals("2026-11-07 周六 14:00–16:00"),"school timezone and weekday are explicit");
        TimeZone before=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));check(first.displayTime().equals("2026-11-07 周六 14:00–16:00"),"device timezone cannot shift official exam");}finally{TimeZone.setDefault(before);}
        check(parsed.get(2).room.isEmpty()&&parsed.get(2).seat.isEmpty(),"null room and seat never become null text");
        check(ZjuExam.parse(new JSONArray().put(row(1,"")).put(new JSONObject().put("kssj",JSONObject.NULL))).isEmpty(),"unpublished slots are not invented");
        check(new ZjuExam("x","x","考试","另行通知","","").start==null,"unknown time retained without a guessed date");
        check(new ZjuExam("x","x","考试","2026年02月30日(14:00-16:00)","","").start==null,"invalid calendar date not normalised");
        check(new ZjuExam("x","x","考试","2026年11月07日(16:00-14:00)","","").start==null,"reversed time not silently moved overnight");
        check(ZjuExam.fromJson(first.json()).time.equals(first.time),"raw official time round trip");
        check(ZjuExam.parse(new JSONArray().put(row(0,first.time).put("qzkssj",first.time).put("qzjsmc","紫金港西2-101").put("qzzwxh","121"))).size()==1,"identical ordinary/midterm copy shown once");
        check(ZjuExam.parse(new JSONArray().put(row(0,first.time)).put(row(1,first.time))).size()==2,"different teaching groups never collapse");
        expect(()->ZjuExam.parse(new JSONArray().put(new JSONObject().put("kssj",first.time))),"missing course title fails complete snapshot");
        expect(()->ZjuExam.parse(new JSONArray().put(row(0,first.time).put("zwxh",new JSONObject()))),"invalid fields fail rather than erase cache");
        long now=Instant.parse("2026-11-01T00:00:00Z").toEpochMilli();
        List<ZjuExam> sorted=ZjuExam.ordered(parsed,now);
        check(sorted.get(0).time.equals(first.time)&&sorted.get(2).kind.equals("期中"),"upcoming first and past last without active-semester filter");
        try(ZjuClient client=connection()){List<ZjuExam> result=client.exams();check(result.size()==103,"all official exam pages fetched beyond first page");}
        check(calls.get()==3,"one entry GET and two POST pages");
        scenario=1;try(ZjuClient client=connection()){check(client.exams().isEmpty(),"successful empty official list");}
        scenario=2;try(ZjuClient client=connection()){expect(()->client.exams(),"malformed response rejected");}
        scenario=3;try(ZjuClient client=connection()){expect(()->client.exams(),"later page failure rejects partial list");}
        scenario=4;try(ZjuClient client=connection()){expect(()->client.exams(),"inconsistent total rejects truncated list");}
        scenario=5;try(ZjuClient client=connection()){expect(()->client.exams(),"repeated page detects no progress");}
        ZjuExamStore cache=new ZjuExamStore(app);cache.save("alpha",parsed);
        check(cache.load("alpha").size()==3,"atomic cache round trip");
        check(cache.load("beta")==null,"another account cannot read cached exam");
        cache.save("alpha",List.of());check(cache.load("alpha").isEmpty(),"successful empty snapshot removes withdrawn exams");
        cache.clear();check(cache.load("alpha")==null,"logout cleanup removes snapshot");
    }
    private ZjuClient connection(){
        try{
            ZjuClient c=new ZjuClient("exam-fixture","not-a-real-password");put(c,"timetableLoggedIn",true);
            OkHttpClient original=(OkHttpClient)get(c,"http");
            put(c,"http",original.newBuilder().addInterceptor(chain->{
                calls.incrementAndGet();Request r=chain.request();
                if(!r.url().host().equals("zdbk.zju.edu.cn")||!r.url().encodedPath().endsWith("/xskscx/kscx_cxXsgrksIndex.html"))throw new AssertionError("Unexpected external request");
                if(r.method().equals("GET"))return response(r,"text/html","exam entry",200);
                if(!"query".equals(r.url().queryParameter("doType")))throw new AssertionError("Missing official query action");
                FormBody body=(FormBody)r.body();int page=0;
                for(int i=0;i<body.size();i++)if(body.name(i).equals("queryModel.currentPage"))page=Integer.parseInt(body.value(i));
                if(page==0)throw new AssertionError("No page");
                CountDownLatch wait=gate;if(wait!=null){entered.countDown();try{if(!wait.await(8,TimeUnit.SECONDS))throw new IOException("Fixture timeout");}catch(InterruptedException e){throw new IOException(e);}}
                if(scenario==2)return response(r,"application/json","{}",200);
                if(scenario==3&&page==2)return response(r,"application/json","unavailable",503);
                JSONArray rows=new JSONArray();
                try{
                    if(scenario!=1)for(int i=(page-1)*100;i<Math.min(page*100,103);i++)rows.put(row(i,i%2==0?"2026年11月07日(14:00-16:00)":"2027年01月06日(08:00-10:00)"));
                    if(scenario==5)rows=new JSONArray().put(row(0,"2026年11月07日(14:00-16:00)"));
                    return response(r,"application/json",new JSONObject().put("items",rows).put("totalPage",scenario==4?1:scenario==1?0:scenario==5?3:2)
                            .put("totalResult",scenario==1?0:103).toString(),200);
                }catch(JSONException e){throw new IOException(e);}
            }).build());return c;
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private static Response response(Request r,String type,String value,int status){return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(status).message("Fixture").body(ResponseBody.create(value,MediaType.get(type))).build();}
    private static JSONObject row(int i,String time)throws JSONException{return new JSONObject().put("xkkh","(2026-2027-1)-"+i).put("kcmc",i==0?"微积分（甲）Ⅰ":i==1?"大学英语Ⅲ":"线性代数（甲） "+i).put("kssj",time).put("jsmc","紫金港西2-101").put("zwxh","121");}

    private void ui()throws Exception{
        ZjuSettingsStore settings=new ZjuSettingsStore(app);
        // Initial constructor cannot contact the network: deliberately undecryptable owned credentials.
        settings.prefs.edit().putBoolean("enabled",true).putString("account","exam-ui").putString("credentials","invalid-fixture").putString("iv","").commit();
        List<ZjuExam> visible=new ArrayList<>(List.of(new ZjuExam("1","微积分（甲）Ⅰ","期中","2026年11月07日(14:00-16:00)","紫金港西2-101","121"),
                new ZjuExam("2","线性代数（甲）","考试","2026年11月15日(14:00-16:00)","紫金港西2-104","53"),
                new ZjuExam("3","大学英语Ⅲ","考试","2027年01月06日(08:00-10:00)","",""),
                new ZjuExam("4","数字音视频基础与制作：较长课程名称与完整考试信息","考试","2027年01月07日(08:00-10:00)","紫金港东6-328","")));
        for(int i=5;i<=12;i++)visible.add(new ZjuExam(""+i,"考试课程 "+i,"考试","2027年01月08日(08:00-10:00)","教室 "+i,""));
        new ZjuExamStore(app).save(settings.account(),visible);
        screen=(ZjuExamsActivity)test.startActivitySync(new Intent(app,ZjuExamsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->!((ZjuExamsActivity.Session)get(screen,"session")).busy);test.waitForIdleSync();
        test.runOnMainSync(()->((ZjuExamsActivity.Session)getUnchecked(screen,"session")).dispose());
        settings.save("exam-fixture","not-a-real-password");scenario=1;
        owned=new ZjuExamsActivity.Session(app,(u,p)->connection());owned.rows=visible;
        test.runOnMainSync(()->{putUnchecked(screen,"session",owned);owned.listener=()->invoke(screen,"render");invoke(screen,"render");});
        test.waitForIdleSync();SystemClock.sleep(350);
        check(find(screen.getWindow().getDecorView(),"考试安排")!=null,"shared page title is present");
        check(find(screen.getWindow().getDecorView(),"期中 · 2026-11-07 周六 14:00–16:00")!=null,"exam time and weekday rendered");
        check(find(screen.getWindow().getDecorView(),"考场待公布 · 座位待公布")!=null,"missing official fields shown without guesses");
        TextView title=find(screen.getWindow().getDecorView(),"微积分（甲）Ⅰ");View card=(View)title.getParent();
        check(card.getWidth()>app.getResources().getDisplayMetrics().widthPixels/2,"cards use available content width");
        screenshot("initial");ScrollView scroll=parentScroll(card);int before=scroll.getScrollY();int h=scroll.getHeight();
        Rect box=new Rect();test.runOnMainSync(()->scroll.getGlobalVisibleRect(box));swipe(box.centerX(),box.bottom-60,box.centerX(),box.top+60);
        check(scroll.getScrollY()>before,"normal page scroll includes title and tools");
        check(scroll.getHeight()==h,"no fixed toolbar shrinks scrolling content");
        screenshot("scrolled");test.runOnMainSync(()->scroll.scrollTo(0,0));test.waitForIdleSync();
        scenario=1;tap((View)get(screen,"refresh"));await(()->!owned.busy);test.waitForIdleSync();
        check(find(screen.getWindow().getDecorView(),"暂无已公布考试")!=null,"actual refresh tap applies a successful empty response");
        check(new ZjuExamStore(app).load(settings.account()).isEmpty(),"successful tap persisted full snapshot");
        owned.rows=visible;new ZjuExamStore(app).save(settings.account(),visible);scenario=3;
        test.runOnMainSync(()->invoke(screen,"render"));tap((View)get(screen,"refresh"));await(()->!owned.busy);test.waitForIdleSync();
        check(owned.rows.size()==visible.size()&&new ZjuExamStore(app).load(settings.account()).size()==visible.size(),"later-page error retains old visible data and cache");
        scenario=0;gate=new CountDownLatch(1);entered=new CountDownLatch(1);tap((View)get(screen,"refresh"));
        check(entered.await(4,TimeUnit.SECONDS),"refresh entered owned transport");
        ZjuExamsActivity.Session retained=owned;int beforeRotation=calls.get();test.runOnMainSync(()->screen.recreate());
        await(()->test.lastResumed instanceof ZjuExamsActivity&&test.lastResumed!=screen);
        screen=(ZjuExamsActivity)test.lastResumed;check(get(screen,"session")==retained,"configuration change retains one request owner");
        check(calls.get()==beforeRotation,"rotation does not duplicate network request");
        test.runOnMainSync(()->settings.prefs.edit().putBoolean("enabled",false).commit());
        gate.countDown();await(()->!owned.busy);test.waitForIdleSync();
        check(new ZjuExamStore(app).load(settings.account()).size()==visible.size(),"disable during response cannot replace cache");
        check(find(screen.getWindow().getDecorView(),"微积分（甲）Ⅰ")==null,"disabled school cannot display cached data");
        check(!((View)get(screen,"refresh")).isEnabled(),"disabled school cannot refresh");
        test.runOnMainSync(()->settings.prefs.edit().putBoolean("enabled",true).commit());
        CountDownLatch connecting=new CountDownLatch(1),connectRelease=new CountDownLatch(1);
        ZjuExamsActivity.Session pending=new ZjuExamsActivity.Session(app,(u,p)->{
            connecting.countDown();
            try{if(!connectRelease.await(5,TimeUnit.SECONDS))throw new AssertionError("Connect fixture timeout");}
            catch(InterruptedException e){throw new RuntimeException(e);}return connection();
        });
        int beforeClose=calls.get();
        try{
            test.runOnMainSync(pending::load);check(connecting.await(4,TimeUnit.SECONDS),"leaving during connection setup is exercised");
            test.runOnMainSync(pending::dispose);connectRelease.countDown();await(()->!pending.busy);
            check(calls.get()==beforeClose,"leaving before the connection is assigned sends no request");
        }finally{connectRelease.countDown();pending.dispose();}
    }
    private interface Checked{void run()throws Exception;}private interface Condition{boolean ok()throws Exception;}
    private void expect(Checked work,String label)throws Exception{try{work.run();throw new AssertionError(label);}catch(IOException|JSONException expected){checks++;}}
    private void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private void await(Condition condition)throws Exception{long until=SystemClock.uptimeMillis()+10000;while(!condition.ok()){if(SystemClock.uptimeMillis()>until)throw new AssertionError("Timed out");SystemClock.sleep(30);}}
    private static Object get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void put(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static Object getUnchecked(Object o,String n){try{return get(o,n);}catch(Exception e){throw new RuntimeException(e);}}
    private static void putUnchecked(Object o,String n,Object v){try{put(o,n,v);}catch(Exception e){throw new RuntimeException(e);}}
    private static void invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
    private static TextView find(View view,String text){if(view instanceof TextView t&&text.contentEquals(t.getText()))return t;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView t=find(g.getChildAt(i),text);if(t!=null)return t;}return null;}
    private static ScrollView parentScroll(View v){while(!(v instanceof ScrollView))v=(View)v.getParent();return (ScrollView)v;}
    private void tap(View v){Rect r=new Rect();test.runOnMainSync(()->v.getGlobalVisibleRect(r));long d=SystemClock.uptimeMillis();for(int action:new int[]{0,1}){MotionEvent e=MotionEvent.obtain(d,d+(action==1?80:0),action,r.centerX(),r.centerY(),0);try{test.sendPointerSync(e);}finally{e.recycle();}}test.waitForIdleSync();}
    private void swipe(int x,int y,int ex,int ey){long d=SystemClock.uptimeMillis();for(int i=0;i<=14;i++){MotionEvent e=MotionEvent.obtain(d,d+i*20,i==0?0:i==14?1:2,x+(ex-x)*i/14f,y+(ey-y)*i/14f,0);try{test.sendPointerSync(e);}finally{e.recycle();}}test.waitForIdleSync();SystemClock.sleep(300);}
    private void screenshot(String stage)throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();float scale=Math.min(1,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true);File file=new File(app.getExternalFilesDir(null),"exams-"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+"-"+stage+".jpg");try(FileOutputStream out=new FileOutputStream(file)){small.compress(Bitmap.CompressFormat.JPEG,75,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
}
