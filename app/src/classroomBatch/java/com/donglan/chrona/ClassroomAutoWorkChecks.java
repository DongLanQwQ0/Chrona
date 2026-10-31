package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Session/FGS/cache/UI; injected generator, unusable school identity and no network route. */
final class ClassroomAutoWorkChecks {
    private static final String ACCOUNT="00000000-0000-0000-0000-000000000036";
    private final Instrumentation test;private final Bundle args;private final Context app;
    private final List<ClassroomLesson> lessons=new ArrayList<>();private final List<Activity> opened=new ArrayList<>();
    private final AtomicInteger calls=new AtomicInteger(),routes=new AtomicInteger();
    private volatile boolean authorizationConsumed=true;
    private final List<Integer> limits=Collections.synchronizedList(new ArrayList<>());
    private final JSONArray report=new JSONArray();private int checks;private ClassroomBatchWork batch;
    private volatile boolean fail=true;private volatile String held="";private volatile CountDownLatch entered,release;
    private final Set<String> heldTogether=ConcurrentHashMap.newKeySet();
    private volatile String phase="setup";private volatile long heartbeat;private volatile boolean observing=true;
    private ClassroomAutoWorkChecks(Instrumentation t,Bundle a){test=t;args=a;app=t.getTargetContext();}
    static String run(Instrumentation test,Bundle args)throws Exception{return new ClassroomAutoWorkChecks(test,args).run();}
    private String run()throws Exception{
        if(!"com.donglan.chrona.batch.validation".equals(app.getPackageName())||!"ranchu".equals(Build.HARDWARE))throw new SecurityException("Owned batch AVD only");
        ZjuSettingsStore school=new ZjuSettingsStore(app);if(school.configured()&&!ACCOUNT.equals(school.account()))throw new SecurityException("Refusing existing school identity");
        if(getStatic(ClassroomWorkService.class,"instance")!=null)throw new SecurityException("Existing classroom owner");
        test.getUiAutomation().setRotation("landscape".equals(args.getString("orientation"))?UiAutomation.ROTATION_FREEZE_90:UiAutomation.ROTATION_FREEZE_0);SystemClock.sleep(800);
        Handler main=new Handler(Looper.getMainLooper());Runnable pulse=new Runnable(){public void run(){heartbeat=SystemClock.uptimeMillis();if(observing)main.postDelayed(this,250);}};main.post(pulse);
        Thread watchdog=new Thread(()->{while(observing){SystemClock.sleep(500);if(heartbeat>0&&SystemClock.uptimeMillis()-heartbeat>2500){try{JSONObject state=new JSONObject().put("phase",phase).put("main",Arrays.toString(Looper.getMainLooper().getThread().getStackTrace()));java.nio.file.Files.write(new File(output(),"main-stall.json").toPath(),state.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}}}},"AutoSummaryFixtureWatchdog");watchdog.setDaemon(true);watchdog.start();
        Map<String,?> before=new HashMap<>(school.prefs.getAll());SharedPreferences monitor=app.getSharedPreferences("chrona_classroom_monitor",0);String monitorBefore=monitor.getString(ACCOUNT,null);
        SharedPreferences schedule=app.getSharedPreferences("chrona_schedule_preferences",0);Map<String,?> scheduleBefore=new HashMap<>(schedule.getAll());
        ClassroomAutoSummary.Generator generator=ClassroomAutoSummary.generator;ClassroomAutoSummary.Settings settings=ClassroomAutoSummary.settings;java.util.function.BooleanSupplier foreground=ClassroomAutoSummary.foreground;
        ProxySelector proxy=ProxySelector.getDefault();ClassroomStore store=new ClassroomStore(app,ACCOUNT);List<ClassroomLesson> catalog=store.catalog();
        try{
            school.save("auto-summary-fixture","synthetic-no-network");school.prefs.edit().putString("account",ACCOUNT).putBoolean("enabled",true).putBoolean("homework",false).putBoolean("timetable",false).commit();
            if(Build.VERSION.SDK_INT>=33)test.getUiAutomation().grantRuntimePermission(app.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
            monitor.edit().remove(ACCOUNT).commit();new ClassroomMonitorStore(app,ACCOUNT).summaryImages(false);
            var configured=com.donglan.chrona.ai.SchedulePreferences.load(app);
            new com.donglan.chrona.ai.SchedulePreferences(configured.workStartMinutes,configured.workEndMinutes,configured.customPrompt,configured.classroomPrompt,configured.enhancedClassroom,3).save(app);
            ProxySelector.setDefault(new ProxySelector(){public List<Proxy> select(URI uri){routes.incrementAndGet();throw new SecurityException("Fixture has no network route");}public void connectFailed(URI u,SocketAddress a,IOException e){}});
            ClassroomAutoSummary.settings=c->new AiSettings("https://fixture.invalid","auto-work-fixture","synthetic-unused");
            ClassroomAutoSummary.generator=(job,ai,prompt)->{
                if(job.summaryAuthorized)authorizationConsumed=false;
                calls.incrementAndGet();limits.add(job.imageLimit);
                if(job.selected.id.equals(held)||heldTogether.contains(job.selected.id)){entered.countDown();while(!release.await(30,TimeUnit.MILLISECONDS))job.control.check();job.control.check();}
                if(fail&&job.selected.id.equals(lessons.get(1).id))throw new IOException("Synthetic generation failure");
                JSONObject summary=new JSONObject().put("lesson",job.selected.json()).put("complete",true).put("updated",System.currentTimeMillis()).put("parts",new JSONArray());
                ClassroomStore.write(new File(job.dir,"summary.json"),summary);return summary;
            };
            String id=Long.toUnsignedString(System.nanoTime());long now=System.currentTimeMillis();
            String[] names={"自动总结甲","自动总结失败","自动总结乙","已有完整总结","图片快照甲","图片快照乙","取消总结甲","取消总结乙","历史未总结","单节自动总结","缺失素材重试","未下载课堂","取消总结丙","未开始总结"};
            for(int i=0;i<names.length;i++){
                ClassroomLesson lesson=new ClassroomLesson("777164",id+i,names[i],"第 "+(i+1)+" 讲","隔离验收教室","6",now-(i+1)*3600000L);lessons.add(lesson);
                if(i<10||i>=12)materials(store.localLesson(lesson),lesson);
            }
            ClassroomStore.write(new File(store.localLesson(lessons.get(3)),"summary.json"),summary(lessons.get(3),true));
            ClassroomStore.write(new File(store.localLesson(lessons.get(10)),"run.json"),new JSONObject().put("execution",new JSONObject().put("state","failed").put("imageLimit",0).put("detail","Synthetic prior failure")));
            store.catalog(lessons);
            start(List.of(lessons.get(0),lessons.get(1),lessons.get(2),lessons.get(3),lessons.get(0)),Set.of());settled();
            check(batch.success==2&&batch.failures.size()==2&&batch.skipped==1&&calls.get()==3,"batch downloads and summarizes each unique unfinished lesson; failure advances and complete skips");
            check(limits.stream().allMatch(n->n==0),"default automatic summary never uploads images");
            JSONObject failed=ClassroomStore.read(new File(store.localLesson(lessons.get(1)),"run.json"));
            check("failed".equals(failed.getJSONObject("execution").getString("state")),"failed automatic attempt is persisted before UI teardown");
            int beforeRetry=calls.get();
            start(List.of(lessons.get(1)),Set.of());settled();
            check(batch.success==0&&calls.get()==beforeRetry,"unacknowledged failed work cannot issue a second request");
            ClassroomMonitorStore monitoring=new ClassroomMonitorStore(app,ACCOUNT);monitoring.add(lessons.get(1),false);monitoring.finished(lessons.get(1));
            ClassroomAutoSummary.foreground=()->true;test.runOnMainSync(()->ClassroomAutoSummary.dispatch(app));
            check("interrupted".equals(ClassroomMonitorStore.auto(monitoring.read(),lessons.get(1)).optString("state"))&&calls.get()==beforeRetry,"old queued monitor cannot retry a manual failure when its foreground returns");
            fail=false;start(List.of(lessons.get(1)),Set.of(ClassroomBatchWork.key(lessons.get(1))));settled();
            check(batch.success==1&&calls.get()==beforeRetry+1,"explicit per-lesson retry authorization permits exactly one continued summary");
            monitoring.summaryImages(true);held=lessons.get(4).id;entered=new CountDownLatch(1);release=new CountDownLatch(1);
            start(List.of(lessons.get(4),lessons.get(5)),Set.of());check(entered.await(10,TimeUnit.SECONDS),"image-authorized first lesson entered generator");
            check(app.getSystemService(ActivityManager.class).getRunningServices(100).stream().anyMatch(s->s.service.getClassName().equals(ClassroomWorkService.class.getName())&&s.foreground),"summary phase remains under the actual Android foreground service");
            monitoring.summaryImages(false);release.countDown();settled();
            check(limits.get(limits.size()-1)==40&&limits.get(limits.size()-2)==40,"whole batch preserves the original 40-image permission snapshot");
            held="";heldTogether.addAll(List.of(lessons.get(6).id,lessons.get(7).id,lessons.get(12).id));entered=new CountDownLatch(3);release=new CountDownLatch(1);
            start(List.of(lessons.get(6),lessons.get(7),lessons.get(12),lessons.get(13)),Set.of());
            check(entered.await(10,TimeUnit.SECONDS),"three batch lessons simultaneously enter the real process-wide summary gate");
            int atCancel=calls.get();test.runOnMainSync(()->batch.cancel("Fixture cancellation"));settled();release.countDown();
            check(batch.cancelledCount==4&&calls.get()==atCancel,"stop cancels all three running summaries and never starts the queued lesson");heldTogether.clear();
            phase="ui";ui(store);
            check(authorizationConsumed,"per-lesson retry authorization is consumed before any generator call");
            check(routes.get()==0,"all native paths used existing preparation cache and fake generator; zero school or AI network requests");
            return new JSONObject().put("assertions",checks).put("fakeGeneratorCalls",calls.get()).put("networkRoutes",routes.get()).put("checks",report).toString();
        }catch(Throwable failure){
            report.put(new JSONObject().put("failure",android.util.Log.getStackTraceString(failure)));
            try{capture("failure");}catch(Exception capture){failure.addSuppressed(capture);}throw failure;
        }finally{
            observing=false;main.removeCallbacks(pulse);
            java.nio.file.Files.write(new File(output(),"report.json").toPath(),report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(release!=null)release.countDown();
            if(batch!=null&&batch.running){test.runOnMainSync(()->batch.cancel("Fixture cleanup"));settled();}
            test.runOnMainSync(()->{for(Activity activity:opened)if(!activity.isDestroyed())activity.finish();});test.waitForIdleSync();
            ClassroomAutoSummary.generator=generator;ClassroomAutoSummary.settings=settings;ClassroomAutoSummary.foreground=foreground;ProxySelector.setDefault(proxy);
            store.catalog(catalog);NotificationManager notices=app.getSystemService(NotificationManager.class);
            for(ClassroomLesson lesson:lessons){File dir=store.localLesson(lesson);if(new File(dir,"materials.json").isFile()||new File(dir,"candidates.json").isFile()||new File(dir,"summary.json").isFile())store.deleteLesson(lesson,()->{});else{new File(dir,"run.json").delete();dir.delete();}notices.cancel("classroom:"+ACCOUNT+":"+lesson.courseId+":"+lesson.id,0);}
            if(monitorBefore==null)monitor.edit().remove(ACCOUNT).commit();else monitor.edit().putString(ACCOUNT,monitorBefore).commit();restore(school.prefs,before);restore(schedule,scheduleBefore);
            java.nio.file.Files.write(new File(output(),"report.json").toPath(),report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private void ui(ClassroomStore store)throws Exception{
        Activity directory=open(new Intent(app,ClassroomSummaryActivity.class).putExtra("classroom_mode",3));
        Object[] page={null};await(()->{page[0]=((Object[])field(directory,"pages"))[1];Object s=field(page[0],"s");return s!=null&&!(boolean)field(s,"busy")&&((List<?>)field(s,"history")).size()>=10;});
        View course=(View)field(page[0],"historyCourse"),order=(View)field(page[0],"historyOrder"),direction=(View)field(page[0],"historyDirection");
        check(Math.abs(course.getWidth()-order.getWidth())<=1,"history fields equally split width after the fixed direction button and both gaps");
        check(direction.getWidth()==Math.round(48*direction.getResources().getDisplayMetrics().density),"direction retains the shared 48dp size");
        TextView label=(TextView)order;check(label.getLayout()!=null&&label.getLayout().getLineEnd(label.getLineCount()-1)==label.length(),"summary time label lays out every character");capture("history-equal-fields");
        tap(order);tapText("未总结");await(()->(int)field(field(page[0],"s"),"historyOrder")==2);
        @SuppressWarnings("unchecked") List<ClassroomLesson> filtered=(List<ClassroomLesson>)field(page[0],"matchingLessons");
        check(filtered.stream().noneMatch(l->l.id.equals(lessons.get(0).id)||l.id.equals(lessons.get(3).id)),"unfinished filter excludes complete summaries and keeps cached/draft/cancelled work");
        @SuppressWarnings("unchecked") Map<String,TextView> markers=(Map<String,TextView>)field(page[0],"selectionMarkers");
        hold((View)markers.get(ClassroomBatchWork.key(lessons.get(6))).getParent());int beforeDialog=calls.get();
        tap((View)field(page[0],"batchDownload"));await(()->find(test.getUiAutomation().getRootInActiveWindow(),"继续总结 1 节课堂")!=null);capture("batch-retry-authorization");tapText("取消");
        check(calls.get()==beforeDialog&&(boolean)field(field(page[0],"s"),"selecting"),"cancelled batch retry authorization starts no request and retains selection");test.runOnMainSync(()->((ClassroomSummaryPage)page[0]).handleBack());
        View row=(View)markers.get(ClassroomBatchWork.key(lessons.get(8))).getParent();hold(row);
        Button summarize=(Button)field(page[0],"batchDownload");check("总结".contentEquals(summarize.getText()),"history multi-select action means download and summarize");capture("history-multi-summary");
        int beforeBatch=calls.get();tap(summarize);await(()->{ClassroomBatchWork b=ClassroomBatchWork.find(ACCOUNT);return b!=null&&!b.running&&calls.get()==beforeBatch+1;});
        check(!(boolean)field(field(page[0],"s"),"selecting"),"summary action exits selection while background pipeline completes");
        tap(findLabel((View)field(page[0],"navigation"),"课堂"));await(()->{Object state=field(page[0],"s");return (int)field(state,"mode")==1&&!(boolean)field(state,"busy")&&(int)field(page[0],"shownMode")==1&&field(page[0],"shownHistory")==field(state,"history")&&field(page[0],"shownCatalog")==field(state,"catalog");});
        @SuppressWarnings("unchecked") Map<String,TextView> classRows=(Map<String,TextView>)field(page[0],"selectionMarkers");hold((View)classRows.get(ClassroomBatchWork.key(lessons.get(11))).getParent());
        check("总结".contentEquals(((Button)field(page[0],"batchDownload")).getText()),"classroom list also exposes multi-select summary");capture("classroom-multi-summary");test.runOnMainSync(()->((ClassroomSummaryPage)page[0]).handleBack());
        phase="single";Activity single=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lessons.get(9).json().toString()));
        await(()->(boolean)field(field(single,"s"),"localInitialized")&&!(boolean)field(field(single,"s"),"busy"));
        int beforeSingle=calls.get();tap((View)field(single,"generate"));final int expected=beforeSingle+1;await(()->calls.get()==expected&&!(boolean)field(field(single,"s"),"busy"));
        check(((JSONObject)field(field(single,"s"),"summary")).optBoolean("complete"),"single cached download action summarizes without a second authorization dialog");
        phase="retry";Activity broken=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lessons.get(10).json().toString()));
        await(()->(boolean)field(field(broken,"s"),"localInitialized")&&!(boolean)field(field(broken,"s"),"busy"));
        check((int)field(field(broken,"s"),"imageLimit")==0,"failed text-only work restores its original image permission before retry");
        int noRetry=calls.get();tap((View)field(broken,"prepare"));await(()->find(test.getUiAutomation().getRootInActiveWindow(),"生成课堂总结")!=null);
        check(calls.get()==noRetry&&routes.get()==0,"failed single lesson with missing materials requires authorization before download or AI");
        await(()->{for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){View cancel=findLabel(root,"取消"),submit=findLabel(root,"上传并生成");if(cancel!=null&&submit!=null&&cancel.isShown()&&root.hasWindowFocus()){Rect visible=new Rect();return cancel.getParent()==submit.getParent()&&cancel.getGlobalVisibleRect(visible)&&visible.height()>=cancel.getHeight()-1;}}return false;});
        check(true,"retry dialog uses the shared paired actions fully visible outside the body scroll");capture("failed-retry-authorization");tapText("取消");await(()->find(test.getUiAutomation().getRootInActiveWindow(),"生成课堂总结")==null);
    }
    private void start(List<ClassroomLesson> chosen,Set<String> authorized)throws Exception{test.runOnMainSync(()->{try{batch=ClassroomBatchWork.start(app,ACCOUNT,chosen,authorized);}catch(Exception e){throw new AssertionError(e);}});}
    private void settled()throws Exception{await(()->batch!=null&&!batch.running);test.waitForIdleSync();}
    private Activity open(Intent intent)throws Exception{ComponentName component=intent.getComponent();Activity initial=test.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));opened.add(initial);Activity[] ready={null};await(()->{Activity current=(Activity)field(test,"lastResumed");if(current==null||!component.equals(current.getComponentName())||current.isDestroyed()||current.isFinishing()||!current.hasWindowFocus()||!current.getWindow().getDecorView().isShown())return false;ready[0]=current;return true;});if(!opened.contains(ready[0]))opened.add(ready[0]);return ready[0];}
    private void tap(View view)throws Exception{int[] p=touchPoint(view);touch(p[0],p[1],60);}
    private static View findLabel(View root,String text){if(root instanceof TextView label&&text.contentEquals(label.getText()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findLabel(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private void hold(View view)throws Exception{int[] p=touchPoint(view);touch(p[0],p[1],ViewConfiguration.getLongPressTimeout()+120);}
    private int[] touchPoint(View view)throws Exception{test.runOnMainSync(()->view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true));test.waitForIdleSync();int[] point=new int[2];await(()->{Rect visible=new Rect();view.getLocationOnScreen(point);point[0]+=view.getWidth()/2;point[1]+=view.getHeight()/2;return view.isAttachedToWindow()&&view.isShown()&&view.hasWindowFocus()&&view.getGlobalVisibleRect(visible)&&visible.contains(point[0],point[1])&&visible.width()>=view.getWidth()-1&&visible.height()>=view.getHeight()-1;});return point;}
    private void touch(float x,float y,long duration){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,0,x,y,0);test.sendPointerSync(down);down.recycle();SystemClock.sleep(duration);MotionEvent up=MotionEvent.obtain(now,SystemClock.uptimeMillis(),1,x,y,0);test.sendPointerSync(up);up.recycle();SystemClock.sleep(250);}
    private void tapText(String text)throws Exception{android.view.accessibility.AccessibilityNodeInfo[] node={null};await(()->{node[0]=find(test.getUiAutomation().getRootInActiveWindow(),text);return node[0]!=null;});Rect b=new Rect();node[0].getBoundsInScreen(b);touch(b.centerX(),b.centerY(),60);}
    private static android.view.accessibility.AccessibilityNodeInfo find(android.view.accessibility.AccessibilityNodeInfo root,String text){if(root==null)return null;if(text.equals(root.getText()==null?null:root.getText().toString())&&root.isVisibleToUser())return root;for(int i=0;i<root.getChildCount();i++){var n=find(root.getChild(i),text);if(n!=null)return n;}return null;}
    private interface Condition{boolean ready()throws Exception;}
    private void await(Condition c)throws Exception{long until=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<until){boolean[] ready={false};Throwable[] error={null};test.runOnMainSync(()->{try{ready[0]=c.ready();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);if(ready[0])return;SystemClock.sleep(30);}throw new AssertionError("Timed out waiting for auto-summary fixture");}
    private void check(boolean yes,String label)throws Exception{checks++;if(!yes)throw new AssertionError(label);report.put(new JSONObject().put("check",label).put("pass",true));}
    private static Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
    private static Object getStatic(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static JSONObject summary(ClassroomLesson lesson,boolean complete)throws Exception{return new JSONObject().put("lesson",lesson.json()).put("complete",complete).put("updated",System.currentTimeMillis()).put("parts",new JSONArray());}
    private static void materials(File dir,ClassroomLesson lesson)throws Exception{ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("id",0).put("start",0).put("end",1).put("text","Synthetic classroom"))).put("raw_images",0).put("updated",System.currentTimeMillis()));ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",new JSONArray()).put("lesson",lesson.json()).put("updated",System.currentTimeMillis()));}
    private File output(){File out=new File(app.getExternalFilesDir(null),"auto-summary/"+args.getString("orientation","portrait")+"-"+args.getString("theme","light"));out.mkdirs();return out;}
    private void capture(String name)throws Exception{SystemClock.sleep(350);Bitmap raw=test.getUiAutomation().takeScreenshot();float k=Math.min(1f,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*k),Math.round(raw.getHeight()*k),true);try(OutputStream out=new FileOutputStream(new File(output(),name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,75,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor edit=p.edit().clear();for(var e:values.entrySet()){Object v=e.getValue();if(v instanceof String s)edit.putString(e.getKey(),s);else if(v instanceof Boolean b)edit.putBoolean(e.getKey(),b);else if(v instanceof Integer i)edit.putInt(e.getKey(),i);else if(v instanceof Long l)edit.putLong(e.getKey(),l);else if(v instanceof Float f)edit.putFloat(e.getKey(),f);else if(v instanceof Set<?>)edit.putStringSet(e.getKey(),(Set<String>)v);}edit.commit();}
}
