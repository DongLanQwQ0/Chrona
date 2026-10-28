package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import com.donglan.chrona.data.*;
import com.donglan.chrona.zju.ZjuExam;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Owned normal-screen fixture: SQLite composition, real LAN framing and real filter/detail taps. */
final class ZjuExamScheduleChecks {
    private final ClassroomMonitorInstrumentation test;
    private final Bundle args;
    private final Context app;
    private final ZjuSettingsStore settings;
    private final ZjuExamStore cache;
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    private int checks;
    private Activity screen;
    private final long now=Instant.parse("2026-10-28T00:00:00Z").toEpochMilli();
    private ZjuExamScheduleChecks(ClassroomMonitorInstrumentation t,Bundle a){test=t;args=a;app=t.getTargetContext();settings=new ZjuSettingsStore(app);cache=new ZjuExamStore(app);}
    static int run(ClassroomMonitorInstrumentation t,Bundle args)throws Exception{
        ZjuExamScheduleChecks c=new ZjuExamScheduleChecks(t,args);
        if(!c.app.getPackageName().equals("com.donglan.chrona.monitor.validation")||!Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned AVD only");
        File file=new File(c.app.getCacheDir(),"zju-exams.json");byte[] previous=file.isFile()?java.nio.file.Files.readAllBytes(file.toPath()):null;
        for(String name:List.of("chrona_zju","appearance"))c.saved.put(name,new HashMap<>(c.app.getSharedPreferences(name,0).getAll()));
        try{
            c.settings.prefs.edit().clear().putString("account","schedule-exams").putString("credentials","invalid-fixture").putBoolean("enabled",true).commit();
            c.app.getSharedPreferences("appearance",0).edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();
            if(!"true".equals(args.getString("uiOnly"))){c.core();c.http();}
            c.ui();return c.checks;
        }catch(Throwable failure){c.screenshot("failure");throw failure;}
        finally{
            t.runOnMainSync(()->{if(c.screen!=null)c.screen.finish();if(t.lastResumed!=null)t.lastResumed.finish();});
            for(var entry:c.saved.entrySet())restore(c.app.getSharedPreferences(entry.getKey(),0),entry.getValue());
            if(previous==null)c.cache.clear();else java.nio.file.Files.write(file.toPath(),previous);
        }
    }
    private List<ZjuExam> exams(){return List.of(
        new ZjuExam("calculus","微积分（甲）Ⅰ","期中","2026年11月07日(14:00-16:00)","紫金港西2-101","121"),
        new ZjuExam("linear","线性代数（甲）","考试","2026年11月15日(14:00-16:00)","紫金港西2-104","53"),
        new ZjuExam("past","过去的课程","考试","2026年10月01日(08:00-10:00)","",""),
        new ZjuExam("unknown","时间待定%_","考试","另行通知","",""));}
    private ScheduleQuery query(int tab,String type,int pub,String search,LocalDate from,LocalDate until,int todo){return new ScheduleQuery(tab,type,pub,search,from,until,now,ZoneId.of("Asia/Shanghai"),todo);}
    private ScheduleQuery query(int tab,String type){return query(tab,type,0,"",null,null,0);}
    private void core()throws Exception{
        File db=new File(app.getCacheDir(),"exam-schedule-check.db");
        android.database.sqlite.SQLiteDatabase.deleteDatabase(db);
        try(TaskStore store=TaskStore.openSnapshot(app,db)){
            long task=store.insertTask("Owned exam schedule fixture",null,"test",now);
            List<EventCandidate> locals=new ArrayList<>();
            for(int i=0;i<55;i++)locals.add(new EventCandidate(0,task,i==0?"手动考试":("本地日程 "+i),now+i*86400000L,now+i*86400000L+3600000,"Asia/Shanghai","图书馆","",null,false));
            store.replaceCandidates(task,locals);cache.save(settings.account(),exams());
            ScheduleListing.Page official=ScheduleListing.load(app,store,query(3,"exam"),true,0,0);
            check(official.total==4&&official.ids.isEmpty()&&official.rows.stream().allMatch(r->r.exam!=null),"official-only never title-classifies a manual exam or exposes selection IDs");
            check(store.queryScheduleIds(query(3,null)).size()==55,"reading official exams creates no local rows");
            check(ScheduleListing.load(app,store,query(3,"event"),true,0,0).total==55,"existing activity type unchanged");
            check(ScheduleListing.load(app,store,query(0,"exam"),true,0,0).total==2,"upcoming includes only active official exams");
            check(ScheduleListing.load(app,store,query(1,"exam"),true,0,0).total==1,"unrecognised date remains unscheduled");
            check(ScheduleListing.load(app,store,query(2,"exam"),true,0,0).total==1,"ended official exams move to history");
            for(int pub=0;pub<=3;pub++)check(ScheduleListing.load(app,store,query(3,"exam",pub,"",null,null,0),true,0,0).total==(pub==0||pub==3?4:0),"publication semantics "+pub);
            check(ScheduleListing.load(app,store,query(3,"exam",0,"121",null,null,0),true,0,0).total==1,"seat search");
            check(ScheduleListing.load(app,store,query(3,"exam",0,"%_",null,null,0),true,0,0).total==1,"literal special-character search");
            check(ScheduleListing.load(app,store,query(3,"exam",0,"",LocalDate.of(2026,11,7),LocalDate.of(2026,11,8),0),true,0,0).total==1,"date range excludes unknown dates and other days");
            check(ScheduleListing.load(app,store,query(3,"exam",0,"",null,null,ScheduleQuery.TODO_OPEN),true,0,0).total==0,"official exams are not tasks");
            List<ScheduleListing.Row> expected=new ArrayList<>(official.rows);
            for(EventCandidate local:store.getCandidates(task))expected.add(new ScheduleListing.Row(local,null));
            for(boolean oldest:new boolean[]{true,false}){
                expected.sort((a,b)->query(3,null).comparator(oldest).compare(a.candidate,b.candidate));
                List<Long> actual=new ArrayList<>();
                for(int page=0;page<3;page++){
                    ScheduleListing.Page result=ScheduleListing.load(app,store,query(3,null),oldest,page,0);
                    check(result.total==59&&result.ids.size()==55&&result.ids.stream().allMatch(id->id>0),"mixed total and selectable IDs page "+page);
                    for(var row:result.rows)actual.add(row.candidate.id);
                }
                check(actual.equals(expected.stream().map(r->r.candidate.id).toList()),"mixed global pagination stable with no omissions "+oldest);
                for(int i:new int[]{23,24,47,48,57})if(expected.get(i).exam==null){
                    long id=expected.get(i).candidate.id;
                    ScheduleListing.Page focused=ScheduleListing.load(app,store,query(3,null),oldest,0,id);
                    check(focused.page==i/24&&focused.rows.stream().anyMatch(r->r.candidate.id==id),"local focus accounts for exam page shifts "+oldest+" "+i);
                }
            }
            String scope=ScheduleListing.scope(app);cache.save(settings.account(),List.of());
            check(!scope.equals(ScheduleListing.scope(app))&&ScheduleListing.load(app,store,query(3,"exam"),true,0,0).total==0,"cache refresh invalidates native snapshot and removes withdrawn exams");
            cache.save(settings.account(),exams());settings.prefs.edit().putBoolean("enabled",false).commit();
            check(ScheduleListing.load(app,store,query(3,null),true,0,0).total==55,"disabled source leaves original local usage intact");
            settings.prefs.edit().putBoolean("enabled",true).putString("account","other").commit();
            check(ScheduleListing.load(app,store,query(3,"exam"),true,0,0).total==0,"new account cannot display old exams");
            settings.prefs.edit().putString("account","schedule-exams").commit();
            java.nio.file.Files.write(new File(app.getCacheDir(),"zju-exams.json").toPath(),"invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ScheduleListing.Page failed=ScheduleListing.load(app,store,query(3,null),true,0,0);
            check(failed.total==55&&!failed.warning.isEmpty(),"invalid exam cache reports failure without breaking local schedules");
            check(EventCategory.VALUES.length==3&&ScheduleFilterState.categoryOptions().length==5&&"exam".equals(ScheduleFilterState.categoryValue(4)),"virtual filter does not expand editor or AI schema");
        }finally{android.database.sqlite.SQLiteDatabase.deleteDatabase(db);}
    }
    private void http()throws Exception{
        cache.save(settings.account(),exams());int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        LanWebServer server=new LanWebServer(app,"127.0.0.1",port);server.start();
        try{
            Reply paired=request(port,"/api/pair",new JSONObject().put("code",server.security.pairing()),null);
            check(paired.status==200,"real LAN pairing succeeds");
            Reply r=request(port,"/api/schedule?tab=3&category=exam&includeIds=true",null,paired.cookie);
            {
                JSONObject data=r.body;check(r.status==200&&data.getInt("total")==4&&data.getJSONArray("ids").length()==0,"LAN official totals and no bulk IDs");
                JSONObject item=data.getJSONArray("items").getJSONObject(0);
                check(item.getString("id").startsWith("exam:-")&&item.getBoolean("readOnly")&&!item.getBoolean("canAcknowledge")&&item.has("officialTime"),"LAN precise string identity and read-only contract");
            }
            check(request(port,"/api/inbox?category=exam",null,paired.cookie).status==400,"exam filter does not leak into editable inbox");
        }finally{server.closeAccess();}
    }
    private record Reply(int status,JSONObject body,String cookie){}
    /** Same raw loopback framing as LanAccessInstrumentation; release outbound policy stays intact. */
    private Reply request(int port,String path,JSONObject body,String cookie)throws Exception{
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress("127.0.0.1",port),5000);socket.setSoTimeout(5000);
            byte[] bytes=body==null?new byte[0]:body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String headers=(body==null?"GET":"POST")+" "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nConnection: close\r\n";
            if(cookie!=null)headers+="Cookie: "+cookie+"\r\n";
            if(body!=null)headers+="Origin: http://127.0.0.1:"+port+"\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\n";
            socket.getOutputStream().write((headers+"\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
            InputStream in=new BufferedInputStream(socket.getInputStream());int status=Integer.parseInt(line(in).split(" ")[1]),length=-1;String header;
            while(!(header=line(in)).isEmpty()){
                if(header.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());
                if(header.toLowerCase(Locale.ROOT).startsWith("set-cookie:"))cookie=header.substring(11).trim().split(";",2)[0];
            }
            if(length<0||length>1024*1024)throw new IOException("Invalid test response length");
            byte[] response=new byte[length];int offset=0;while(offset<length){int count=in.read(response,offset,length-offset);if(count<0)throw new EOFException();offset+=count;}
            return new Reply(status,new JSONObject(new String(response,java.nio.charset.StandardCharsets.UTF_8)),cookie);
        }
    }
    private static String line(InputStream in)throws IOException{ByteArrayOutputStream value=new ByteArrayOutputStream();int next;while((next=in.read())!=-1&&next!='\n'){if(next!='\r')value.write(next);if(value.size()>8192)throw new IOException("Header too long");}if(next==-1)throw new EOFException();return value.toString("US-ASCII");}
    private void ui()throws Exception{
        cache.save(settings.account(),exams());
        screen=test.startActivitySync(new Intent(app,DashboardActivity.class).putExtra(DashboardActivity.EXTRA_SECTION,DashboardActivity.SCHEDULE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->node("筛选日程：日期、类型、待办、写入状态和来源")!=null);tap("筛选日程：日期、类型、待办、写入状态和来源");
        await(()->node("类型，考试")!=null);screenshot("filter");tap("类型，考试");tap("应用筛选");
        await(()->node("微积分（甲）Ⅰ")!=null);screenshot("list");
        check(((Integer)get(screen,"scheduleCategory"))==4,"real type tap applies official exam filter");
        check(((List<?>)get(screen,"matchingInboxIds")).isEmpty(),"native official filter exposes no selectable local rows");
        tap("微积分（甲）Ⅰ");await(()->test.lastResumed instanceof ExternalCourseDetailActivity);screen=test.lastResumed;
        await(()->node("紫金港西2-101 · 座位 121")!=null);SystemClock.sleep(800);screenshot("detail");
        check(node("考试详情")!=null&&node("删除")==null&&node("写入日历")==null,"real card opens shared read-only exam detail");
        test.runOnMainSync(screen::finish);await(()->test.lastResumed instanceof DashboardActivity);screen=test.lastResumed;
        test.runOnMainSync(screen::recreate);await(()->test.lastResumed instanceof DashboardActivity&&test.lastResumed!=screen);screen=test.lastResumed;
        await(()->node("微积分（甲）Ⅰ")!=null);check(((Integer)get(screen,"scheduleCategory"))==4,"rotation retains exam type");
        tap("筛选日程：日期、类型、待办、写入状态和来源");await(()->node("类型，考试，已选中")!=null);
        tap("类型，活动");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
        check(((Integer)get(screen,"scheduleCategory"))==4,"cancelled filter draft preserves exam selection");
        tap("清除日期、类型、状态、来源和搜索条件");await(()->((Integer)get(screen,"scheduleCategory"))==0);check(true,"clear filter restores all types");
    }
    private AccessibilityNodeInfo node(String value){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();return find(root,value);}
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo n,String value){if(n==null)return null;if(value.contentEquals(n.getText()==null?"":n.getText())||value.contentEquals(n.getContentDescription()==null?"":n.getContentDescription()))return n;for(int i=0;i<n.getChildCount();i++){var child=find(n.getChild(i),value);if(child!=null)return child;}return null;}
    private void tap(String name)throws Exception{test.waitForIdleSync();SystemClock.sleep(450);var n=node(name);if(n==null)throw new AssertionError("Missing UI node: "+name);Rect r=new Rect();n.getBoundsInScreen(r);long d=SystemClock.uptimeMillis();for(int action:new int[]{0,1}){MotionEvent e=MotionEvent.obtain(d,d+(action==1?80:0),action,r.centerX(),r.centerY(),0);try{test.sendPointerSync(e);}finally{e.recycle();}}test.waitForIdleSync();SystemClock.sleep(350);}
    private interface Condition{boolean ok()throws Exception;}
    private void await(Condition c)throws Exception{long end=SystemClock.uptimeMillis()+12000;while(!c.ok()){if(SystemClock.uptimeMillis()>end)throw new AssertionError("UI timeout");SystemClock.sleep(40);}}
    private void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void restore(SharedPreferences p,Map<String,?> values){var e=p.edit().clear();for(var v:values.entrySet()){Object x=v.getValue();if(x instanceof String s)e.putString(v.getKey(),s);else if(x instanceof Boolean b)e.putBoolean(v.getKey(),b);else if(x instanceof Integer i)e.putInt(v.getKey(),i);else if(x instanceof Long l)e.putLong(v.getKey(),l);else if(x instanceof Float f)e.putFloat(v.getKey(),f);else if(x instanceof Set s)e.putStringSet(v.getKey(),s);}e.commit();}
    private void screenshot(String stage)throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();float scale=Math.min(1,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true);File file=new File(app.getExternalFilesDir(null),"exam-schedule-"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+"-"+stage+".jpg");try(FileOutputStream out=new FileOutputStream(file)){small.compress(Bitmap.CompressFormat.JPEG,75,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
}
