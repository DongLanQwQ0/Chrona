package com.donglan.chrona;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.zju.*;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** Synthetic Android/SQLite-free classroom fixture; refuses to touch the production package. */
public final class ClassroomMonitorInstrumentation extends Instrumentation {
    private Bundle args;private Context app;private ZjuSettingsStore settings;private String account;
    private Activity activity;private int assertions;private volatile int phase;private volatile int downloads;
    private long starts;private ClassroomLesson selected,future;private byte[] red,blue;
    private volatile boolean liveFixture;private volatile int socketOpens;private volatile FakeSocket lastSocket;
    private volatile List<ClassroomLesson> lookupCatalog;
    private volatile CountDownLatch materialGate;
    final List<String> entryCreates=new ArrayList<>();
    final Map<String,Integer> entryFrameCounts=new HashMap<>();
    volatile Activity lastResumed;
    @Override public void callActivityOnCreate(Activity screen,Bundle state){
        entryCreates.add(screen.getClass().getSimpleName()+": restored="+(state!=null));
        screen.setRequestedOrientation("landscape".equals(args.getString("orientation"))?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.callActivityOnCreate(screen,state);
        if(args.containsKey("heightDp")){float density=screen.getResources().getDisplayMetrics().density;screen.getWindow().setLayout(Math.round(Integer.parseInt(args.getString("widthDp","400"))*density),Math.round(Integer.parseInt(args.getString("heightDp"))*density));}
    }
    final Set<String> enteredPages=new HashSet<>();
    @Override public void callActivityOnResume(Activity screen){
        super.callActivityOnResume(screen);lastResumed=screen;View decor=screen.getWindow().getDecorView();
        android.view.ViewTreeObserver.OnPreDrawListener observer=()->{entryFrameCounts.merge(screen.getClass().getSimpleName(),1,Integer::sum);if(entryFrame(decor))enteredPages.add(screen.getClass().getSimpleName());return true;};
        decor.getViewTreeObserver().addOnPreDrawListener(observer);decor.postDelayed(()->{if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnPreDrawListener(observer);},3000);
    }
    private static boolean entryFrame(View view){
        if(view instanceof ViewGroup&&view.getAlpha()>0&&view.getAlpha()<1&&view.getTranslationY()>0)return true;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(entryFrame(group.getChildAt(i)))return true;
        return false;
    }
    @Override public void onCreate(Bundle input){args=input==null?new Bundle():input;super.onCreate(input);start();}
    @Override public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        Activity result=super.newActivity(loader,name,intent);
        if(name.startsWith("com.donglan.chrona.")){
            var metrics=getTargetContext().getResources().getDisplayMetrics();int width=Integer.parseInt(args.getString("widthDp","400"));
            Configuration config=new Configuration();config.densityDpi=Math.round(metrics.widthPixels*160f/width);config.screenWidthDp=width;config.smallestScreenWidthDp=width;
            config.screenHeightDp=Math.round(metrics.heightPixels*160f/config.densityDpi);
            if("landscape".equals(args.getString("orientation"))){config.orientation=Configuration.ORIENTATION_LANDSCAPE;config.densityDpi=Math.round(Math.max(metrics.widthPixels,metrics.heightPixels)*160f/width);config.screenHeightDp=Math.round(Math.min(metrics.widthPixels,metrics.heightPixels)*160f/config.densityDpi);config.smallestScreenWidthDp=config.screenHeightDp;}
            else{config.orientation=Configuration.ORIENTATION_PORTRAIT;config.densityDpi=Math.round(Math.min(metrics.widthPixels,metrics.heightPixels)*160f/width);config.screenHeightDp=Math.round(Math.max(metrics.widthPixels,metrics.heightPixels)*160f/config.densityDpi);}
            if(args.containsKey("heightDp")){config.densityDpi=metrics.densityDpi;config.screenHeightDp=Integer.parseInt(args.getString("heightDp"));config.smallestScreenWidthDp=Math.min(width,config.screenHeightDp);}
            config.fontScale=Float.parseFloat(args.getString("font","1"));result.applyOverrideConfiguration(config);
        }return result;
    }
    @Override public void onStart(){Bundle report=new Bundle();try{
        app=getTargetContext();if(!app.getPackageName().equals("com.donglan.chrona.monitor.validation"))throw new SecurityException("Fixture package required");
        if(!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned emulator required");
        getUiAutomation().setRotation("landscape".equals(args.getString("orientation"))?UiAutomation.ROTATION_FREEZE_90:UiAutomation.ROTATION_FREEZE_0);SystemClock.sleep(800);
        for(String permission:List.of(android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR,android.Manifest.permission.POST_NOTIFICATIONS))getUiAutomation().grantRuntimePermission(app.getPackageName(),permission);
        getUiAutomation().executeShellCommand("input keyevent 224").close();getUiAutomation().executeShellCommand("wm dismiss-keyguard").close();SystemClock.sleep(300);
        app.getSharedPreferences("chrona_classroom_monitor",0).edit().clear().commit();app.getSharedPreferences("chrona_zju",0).edit().clear().commit();
        app.getSharedPreferences("appearance",0).edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();
        settings=new ZjuSettingsStore(app);settings.save("fixture","fixture");settings.options(true,false);account=settings.account();starts=System.currentTimeMillis()-600_000;
        selected=new ClassroomLesson("10","20","示例课程 · 实时课堂","课堂一","教室A","2",starts);future=new ClassroomLesson("10","21",selected.course,"课堂二","教室A","2",starts+3_600_000);
        red=png(android.graphics.Color.RED);blue=png(android.graphics.Color.BLUE);ClassroomMonitor.clients=(u,p)->client();
        new ClassroomStore(app,account).catalog(List.of(selected,future));
        String mode=args.getString("mode","polish");
        if("live".equals(mode))backgroundChecks();
        else if("pipeline".equals(mode))pipelineChecks();
        else if("auto".equals(mode))assertions+=ClassroomAutoSummaryChecks.run(this,args);
        else if("table".equals(mode))tableChecks();
        else if("workspace".equals(mode))workspaceChecks();
        else assertions+=ClassroomUiPolishChecks.run(this,args);
        report.putString("monitor_report","PASS: "+assertions+" assertions; "+args.getString("mode","ui")+" "+args.getString("widthDp","400")+"/"+args.getString("font","1")+" "+args.getString("theme","light"));finish(-1,report);
    }catch(Throwable failure){report.putString("failure",android.util.Log.getStackTraceString(failure));finish(0,report);}
    finally{try{new ClassroomMonitorStore(app,account).stopAll();ClassroomMonitor.changed(app);settings.options(false,false);getUiAutomation().executeShellCommand("input keyevent 224").close();}catch(Exception ignored){}if(activity!=null)runOnMainSync(activity::finish);}}
    private static byte[] png(int color){Bitmap bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray();}
    private void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void field(Object value,String name,Object next)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.set(value,next);}
    private static Response response(Request r,String type,byte[] bytes){return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("fixture").header("Content-Type",type).body(ResponseBody.create(bytes,MediaType.get(type))).build();}
    private ClassroomClient client(){try{
        ClassroomClient result=new ClassroomClient("fixture","fixture");ZjuClient school=(ZjuClient)field(result,"school");field(school,"classroomLoggedIn",true);
        field(school,"classroomSocketFactory",(java.util.function.BiFunction<Request,WebSocketListener,WebSocket>)(request,listener)->{FakeSocket socket=new FakeSocket(request,listener);lastSocket=socket;return socket;});
        OkHttpClient http=(OkHttpClient)field(school,"http");field(school,"http",http.newBuilder().addInterceptor(chain->{Request request=chain.request();String path=request.url().encodedPath();try{
            if(path.endsWith("catalogue")&&materialGate!=null&&Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame->frame.getClassName().equals(ClassroomClient.class.getName())&&frame.getMethodName().equals("materials")))try{if(!materialGate.await(15,TimeUnit.SECONDS))throw new IOException("Material fixture blocked");}catch(InterruptedException failure){throw new IOException(failure);}
            if(request.url().host().equals("video.cmc.zju.edu.cn")){downloads++;return response(request,"image/png",request.url().encodedPath().contains("first")?red:blue);}
            JSONObject json;
            if(path.endsWith("/course")&&lookupCatalog!=null){ClassroomLesson target=lookupCatalog.get(0);json=new JSONObject().put("code",1000).put("params",new JSONObject().put("result",new JSONObject().put("page",1).put("per-page",100).put("total",1).put("data",new JSONArray().put(new JSONObject().put("Id",target.courseId).put("Title",target.course)))));}
            else if(path.endsWith("catalogue")){JSONArray rows=new JSONArray();for(ClassroomLesson lesson:lookupCatalog==null?List.of(selected,future):lookupCatalog)rows.put(new JSONObject().put("course_id",lesson.courseId).put("sub_id",lesson.id).put("start_at",lesson.startsAt/1000).put("title",lesson.title).put("status",liveFixture?"1":phase==4?"6":"2"));json=new JSONObject().put("success",true).put("result",new JSONObject().put("code",200).put("data",rows));}
            else if(path.endsWith("get-sub-info"))json=new JSONObject().put("code",0).put("data",new JSONObject().put("sub_status",liveFixture?"1":"2").put("content",new JSONObject().put("trans_socket_url",liveFixture?"https://qlite2.cmc.zju.edu.cn/fixture":"")));
            else if(path.endsWith("search-trans-result")){JSONArray segments=new JSONArray();if(!liveFixture&&phase>0&&phase!=3){segments.put(new JSONObject().put("BeginSec",1).put("EndSec",2).put("Text","第一句"));if(phase>=2)segments.put(new JSONObject().put("BeginSec",3).put("EndSec",4).put("Text","第二句"));}
                json=new JSONObject().put("code",liveFixture||phase==0?10002:0).put("total",1).put("list",new JSONArray().put(new JSONObject().put("sub_id",20).put("all_content",segments)));}
            else if(path.endsWith("search-ppt")){JSONArray rows=new JSONArray();if(phase!=3){rows.put(new JSONObject().put("id",1).put("course_id",10).put("sub_id",20).put("created_sec",20).put("content",new JSONObject().put("pptimgurl","https://video.cmc.zju.edu.cn/first.png")));if(phase>=2)rows.put(new JSONObject().put("id",2).put("course_id",10).put("sub_id",20).put("created_sec",1).put("content",new JSONObject().put("pptimgurl","https://video.cmc.zju.edu.cn/earlier.png")));}json=new JSONObject().put("code",0).put("total",rows.length()).put("list",rows);}
            else throw new AssertionError("Unexpected external route: "+path);
            return response(request,"application/json",json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }catch(JSONException failure){throw new IOException(failure);}}).build());return result;
    }catch(Exception failure){throw new IllegalStateException(failure);}}
    private void pass()throws Exception{CountDownLatch done=new CountDownLatch(1);final boolean[] submitted={false};runOnMainSync(()->submitted[0]=ClassroomMonitor.run(app,active->done.countDown()));check(submitted[0],"one network owner accepts pass");check(done.await(15,TimeUnit.SECONDS),"bounded network pass completes");}
    private void storeChecks()throws Exception{
        ClassroomMonitorStore store=new ClassroomMonitorStore(app,account);store.add(selected,false);store.catalog(List.of(selected,future),System.currentTimeMillis());
        check(ClassroomMonitorStore.pending(store.read()).size()==1,"single registration does not expand to future lesson");
        store.add(selected,true);check(ClassroomMonitorStore.pending(store.read()).size()==2,"course registration includes its future actual lesson");
        check(new ClassroomMonitorStore(app,account).read().getJSONArray("rules").length()==1,"scope replacement survives a new store instance without overlapping registrations");
        store.finished(selected);check(store.read().getJSONArray("rules").length()==1&&ClassroomMonitorStore.pending(store.read()).size()==1,"finishing current lesson retains only course rule and next lesson");
        ClassroomMonitor.schedule(app);JobInfo job=app.getSystemService(JobScheduler.class).getPendingJob(ClassroomMonitor.JOB);check(job!=null&&job.isPersisted(),"future work persisted in Android JobScheduler");
        store.stopAll();ClassroomMonitor.schedule(app);check(app.getSystemService(JobScheduler.class).getPendingJob(ClassroomMonitor.JOB)==null,"stopping removes recovery job");
    }
    private void pipelineChecks()throws Exception{
        ClassroomMonitorStore store=new ClassroomMonitorStore(app,account);store.add(selected,false);File dir=new ClassroomStore(app,account).localLesson(selected);
        // This package is a fresh isolated fixture. Remove only its known synthetic material files.
        for(String name:List.of("materials.json","candidates.json","monitor-materials.json","monitor-candidates.json","raw-0.img","raw-1.img","live-transcript.json","live-current.json"))new File(dir,name).delete();
        downloads=0;phase=0;pass();JSONObject observed=ClassroomStore.read(new File(dir,"monitor-materials.json"));
        check(observed.getJSONArray("images").length()==1&&downloads==1&&new File(dir,"monitor-candidates.json").isFile(),"PPT downloads and validates while transcript API has no data");check(!new File(dir,"materials.json").exists(),"partial live materials never masquerade as a full download");
        phase=1;pass();int received=ClassroomStore.read(new File(dir,"monitor-materials.json")).getJSONArray("segments").length();check(received==1&&downloads==1,"new transcript appends without redownloading PPT: segments="+received+" downloads="+downloads+" status="+store.read().optString("message"));
        phase=2;pass();observed=ClassroomStore.read(new File(dir,"monitor-materials.json"));check(observed.getJSONArray("segments").length()==2&&downloads==2,"incremental subtitle and late earlier slide preserve previous cache");
        JSONObject candidates=ClassroomStore.read(new File(dir,"monitor-candidates.json"));check(candidates.getJSONArray("images").getJSONObject(0).getString("raw").equals("raw-1.img"),"late earlier slide cannot alias previously downloaded raw-0");
        phase=3;pass();check(ClassroomStore.read(new File(dir,"monitor-materials.json")).getJSONArray("segments").length()==2&&downloads==2,"empty observation preserves saved text and images");
        phase=4;pass();check(ClassroomMonitorStore.pending(store.read()).isEmpty()&&!ClassroomStore.read(new File(dir,"materials.json")).getBoolean("monitoring"),"ready replay atomically promotes full materials and finishes material monitoring");check(new ClassroomStore(app,account).history().size()==1,"finished monitored class appears in existing download history");
        check(ClassroomMonitorStore.auto(store.read(),selected)!=null,"formal completed recording registers automatic summary once");
    }
    private void readerChecks()throws Exception{
        File dir=new ClassroomStore(app,account).localLesson(selected);new File(dir,"materials.json").delete();new File(dir,"monitor-materials.json").delete();
        ClassroomStore.writeBytes(new File(dir,"raw-0.img"),red);ClassroomStore.writeBytes(new File(dir,"raw-1.img"),blue);
        ClassroomStore.write(new File(dir,"monitor-candidates.json"),new JSONObject().put("updated",System.currentTimeMillis()).put("images",new JSONArray().put(new JSONObject().put("raw","raw-0.img").put("second",1)).put(new JSONObject().put("raw","raw-1.img").put("second",20))));
        JSONArray segments=new JSONArray();for(int i=0;i<80;i++)segments.put(new JSONObject().put("start",i).put("end",i+1).put("origin","live").put("text","第"+i+"句：这里记录课堂知识点，向上翻看时保持当前位置。"));
        ClassroomStore.write(new File(dir,"live-transcript.json"),new JSONObject().put("segments",segments));ClassroomStore.write(new File(dir,"live-status.json"),new JSONObject().put("state","实时连接").put("at",System.currentTimeMillis()));
        activity=startActivitySync(new Intent(app,ClassroomTranscriptActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("account",account).putExtra("lesson",selected.json().toString()));
        idle(900);ScrollView viewport=(ScrollView)field(activity,"scroll");LinearLayout body=(LinearLayout)field(activity,"rows");
        check(body.getChildCount()==80,"all saved captions are readable");check(viewport.getScrollY()>0,"reader initially follows latest");capture("reader-latest");
        int[] xy=new int[2];ScrollView initialViewport=viewport;runOnMainSync(()->initialViewport.getLocationOnScreen(xy));swipe(xy[0]+viewport.getWidth()/2f,xy[1]+viewport.getHeight()*.2f,xy[1]+viewport.getHeight()*.8f);idle(500);
        int before=viewport.getScrollY();View first=body.getChildAt(0);check(!(Boolean)field(activity,"follow"),"real upward history browsing suspends follow");
        segments.put(new JSONObject().put("start",80).put("end",81).put("origin","live").put("text","老师刚刚补充的新一句。"));ClassroomStore.write(new File(dir,"live-transcript.json"),new JSONObject().put("segments",segments));idle(800);
        check(Math.abs(viewport.getScrollY()-before)<=3&&body.getChildAt(0)==first,"incoming text preserves history scroll and existing paragraphs");capture("reader-history");
        ActivityMonitor recreated=addMonitor(ClassroomTranscriptActivity.class.getName(),null,false);runOnMainSync(activity::recreate);Activity replacement=waitForMonitorWithTimeout(recreated,5000);removeMonitor(recreated);check(replacement!=null,"reader recreation returns an actual new Activity");activity=replacement;idle(800);viewport=(ScrollView)field(activity,"scroll");
        check(!(Boolean)field(activity,"follow")&&Math.abs(viewport.getScrollY()-before)<=3,"history scroll restores after asynchronous text layout");capture("reader-restored-history");
        tap((View)field(activity,"latest"));idle(650);capture("reader-return-latest");check((Boolean)field(activity,"follow")&&viewport.getScrollY()>before,"back-to-latest resumes follow: follow="+field(activity,"follow")+" y="+viewport.getScrollY()+" old="+before+" visibility="+((View)field(activity,"latest")).getVisibility());
        tapText("PPT");idle(450);check(((Integer)field(activity,"mode"))==1&&((ImageView)field(activity,"slideImage")).getDrawable()!=null,"PPT tab shows a verified cached image without network download");capture("monitor-ppt");
        DockNavigationLayout sectionDock=(DockNavigationLayout)field(activity,"sections");holdOut(sectionDock.itemAt(1),"monitor-section-held");idle(300);check(field(activity,"sections")==sectionDock,"PPT section survives hold and drag-out");
        tap((View)field(activity,"nextSlide"));idle(350);check(((Integer)field(activity,"pictureIndex"))==1,"PPT next button selects next cached frame");
        ActivityMonitor pptRestore=addMonitor(ClassroomTranscriptActivity.class.getName(),null,false);runOnMainSync(activity::recreate);Activity restored=waitForMonitorWithTimeout(pptRestore,5000);removeMonitor(pptRestore);check(restored!=null,"PPT recreation returns a real Activity");activity=restored;idle(700);body=(LinearLayout)field(activity,"rows");check(((Integer)field(activity,"mode"))==1&&((Integer)field(activity,"pictureIndex"))==1,"PPT mode and selected frame survive recreation");capture("monitor-ppt-restored");
        tap((View)field(activity,"slideImage"));idle(350);capture("monitor-ppt-large");getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle(250);
        tapText("字幕");idle(250);check((Boolean)field(activity,"follow")&&body.getChildCount()==81,"subtitle state remains intact after viewing PPT");
        runOnMainSync(activity::finish);activity=null;
    }
    private void scopeChecks()throws Exception{
        ClassroomMonitorStore store=new ClassroomMonitorStore(app,account);store.stopAll();
        activity=startActivitySync(new Intent(app,ClassroomTranscriptActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("account",account).putExtra("lesson",future.json().toString()));idle(500);
        check(!store.hasRules(),"opening monitor is read-only until start");
        CheckBox repeat=(CheckBox)field(activity,"repeat");check(!repeat.isChecked(),"single monitoring is the default");
        tap((View)field(activity,"action"));idle(500);
        check(store.read().getJSONArray("rules").length()==1&&!store.read().getJSONArray("rules").getJSONObject(0).getBoolean("repeat"),"actual start writes one single registration");
        tap(repeat);idle(500);
        check(store.read().getJSONArray("rules").length()==1&&store.read().getJSONArray("rules").getJSONObject(0).getBoolean("repeat"),"checkbox atomically replaces single with subsequent-course scope");capture("monitor-course-checkbox");
        tap(repeat);idle(400);check(store.read().getJSONArray("rules").length()==1&&!store.read().getJSONArray("rules").getJSONObject(0).getBoolean("repeat"),"unchecking restores a single scope");
        ClassroomLesson other=new ClassroomLesson("11","99","另一门课程","下一节","教室B","2",future.startsAt);store.add(other,false);
        tap((View)field(activity,"action"));idle(400);check(store.read().getJSONArray("rules").length()==1&&ClassroomMonitorStore.matches(store.read(),other),"stop only removes the selected scope and preserves other course");
        store.stopAll();ClassroomMonitor.changed(app);runOnMainSync(activity::finish);activity=null;
    }
    private void workspaceChecks()throws Exception{
        java.time.LocalDate monday=java.time.LocalDate.now(ClassroomLesson.ZONE).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        List<ClassroomLesson> catalog=new ArrayList<>(List.of(selected,future));
        for(int i=0;i<5;i++)catalog.add(new ClassroomLesson(""+(100+i),""+(200+i),new String[]{"大学英语Ⅲ","线性代数（甲）","数字音视频基础与制作","微积分（甲）Ⅰ","大学生思想文化素养"}[i],"第"+i+"讲","东2-203","6",monday.plusDays(i).atTime(8+i,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli()));
        catalog.add(new ClassroomLesson("110","210","同刻另一堂课","重叠","东4-201","2",catalog.get(2).startsAt));
        new ClassroomStore(app,account).catalog(catalog);activity=startActivitySync(new Intent(app,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle(900);
        Object page=((ScheduleWorkspaceActivity)activity).currentPage();ClassroomLessonPickerView picker=(ClassroomLessonPickerView)field(page,"weekPicker");TimetableGrid grid=(TimetableGrid)field(picker,"grid");
        check(grid!=null&&grid.getChildCount()>=7+catalog.size(),"classroom uses shared timetable renderer without dropping same-start lessons");
        capture("classroom-shared-grid");
        runOnMainSync(()->picker.refreshStates(System.currentTimeMillis()));check(field(picker,"grid")==grid,"state updates preserve shared grid instance");
        runOnMainSync(()->picker.scrollTo(100000,0));idle(120);capture("classroom-shared-grid-right");
        runOnMainSync(()->picker.scrollTo(0,0));
        runOnMainSync(()->picker.scrollTo(100,0));int retainedX=picker.getScrollX();
        View bar=(View)field(page,"weekBar");horizontalSwipe(bar,.8f,.2f);idle(600);Object session=field(page,"s");
        check(monday.plusWeeks(1).equals(field(session,"week"))&&((ScheduleWorkspaceActivity)activity).selectedPage()==1,"real date-bar swipe changes week without leaving classroom workspace");
        ClassroomLessonPickerView empty=(ClassroomLessonPickerView)field(page,"weekPicker");check(!empty.verticalViewport().canScrollVertically(1)&&empty.getScrollX()==retainedX,"empty shared week has no extra vertical scroll and retains horizontal position");capture("classroom-empty-week");
        horizontalSwipe(bar,.2f,.8f);idle(600);check(monday.equals(field(session,"week"))&&bar==field(page,"weekBar"),"reverse week swipe retains date-bar instance");
        ClassroomLessonPickerView settled=(ClassroomLessonPickerView)field(page,"weekPicker");TimetableGrid settledGrid=(TimetableGrid)field(settled,"grid");
        check(settled.getScrollX()==retainedX,"returning to populated week retains horizontal position");
        View futureCell=findLesson(settledGrid,ClassroomMonitorStore.key(future));check(futureCell!=null,"future classroom remains represented in the shared grid");
        runOnMainSync(()->{settled.scrollTo(0,0);settled.verticalViewport().fullScroll(View.FOCUS_DOWN);});idle(250);capture("classroom-last-reachable");
        ActivityMonitor direct=addMonitor(ClassroomTranscriptActivity.class.getName(),null,false);tap(futureCell);Activity dedicated=waitForMonitorWithTimeout(direct,5000);removeMonitor(direct);
        check(dedicated!=null,"classroom grid goes straight to dedicated monitor");Activity hostActivity=activity;activity=dedicated;idle(300);capture("monitor-from-classroom-grid");runOnMainSync(activity::finish);activity=hostActivity;idle(250);
        ClassroomMonitorStore store=new ClassroomMonitorStore(app,account);store.add(future,true);store.message(future,"等待上课");
        TextView compact=(TextView)field(page,"compactMode");
        DockNavigationLayout headerDock=(DockNavigationLayout)field(page,"navigation");
        if(compact.getVisibility()!=View.VISIBLE){holdOut(headerDock.itemAt(0),"classroom-header-first-held");holdOut(headerDock.itemAt(3),"classroom-header-last-held");horizontalSwipe(headerDock,.15f,.85f);idle(350);check(field(page,"navigation")==headerDock,"header keeps its instance across first/last holds, drag-out and fling");}
        if(compact.getVisibility()==View.VISIBLE){tap(compact);idle(250);tapText("监视");}else tapText("监视");
        idle(500);check(((Integer)field(field(page,"s"),"mode"))==2,"top monitor entry selects dedicated overview");
        capture("monitor-overview");
        // Opening a registration leads straight to the dedicated page, not another menu.
        View card=((LinearLayout)field(page,"listBody")).getChildAt(0);ActivityMonitor opening=addMonitor(ClassroomTranscriptActivity.class.getName(),null,false);tap(card);
        Activity next=waitForMonitorWithTimeout(opening,5000);removeMonitor(opening);check(next!=null,"overview directly opens selected monitor");Activity workspace=activity;activity=next;idle(400);
        check(((CheckBox)field(activity,"repeat")).isChecked(),"overview retains registered course scope");capture("monitor-dedicated");
        runOnMainSync(activity::finish);activity=workspace;store.stopAll();ClassroomMonitor.changed(app);runOnMainSync(activity::finish);activity=null;
        new ClassroomStore(app,account).catalog(List.of(selected,future));
    }
    private static View findLesson(View root,String key){if(root.getTag() instanceof ClassroomLesson lesson&&ClassroomMonitorStore.key(lesson).equals(key))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View value=findLesson(group.getChildAt(i),key);if(value!=null)return value;}return null;}
    private void tapText(String text){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)throw new AssertionError("No active menu");for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText())){android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);long at=SystemClock.uptimeMillis();pointer(at,at,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY());SystemClock.sleep(50);pointer(at,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY());return;}throw new AssertionError("Menu action absent: "+text);}
    private void tableChecks()throws Exception{
        List<ZjuSnapshot.Lesson> lessons=new ArrayList<>();java.time.LocalDate day=java.time.LocalDate.now(ClassroomLesson.ZONE).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        for(int w=0;w<6;w++)for(int d:new int[]{0,3})for(int part=0;part<2;part++){
            long begin=day.plusWeeks(w).plusDays(d).atTime(part==0?13:14,part==0?25:15).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();long end=day.plusWeeks(w).plusDays(d).atTime(part==0?14:15,part==0?10:50).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
            lessons.add(new ZjuSnapshot.Lesson("fixture/"+w+"/"+d+"/"+part,d==0?"线性代数（甲）":"数字音视频基础与制作",d==0?"东2-203":w%2==0?"北4-211":"东4-322","验证秋学期",begin,end));}
        new ZjuTimetableStore(app).save(account,lessons,List.of("验证秋学期"));settings.prefs.edit().putLong("timetable_attempt",System.currentTimeMillis()).commit();settings.options(true,true);
        ClassroomLesson linked=new ClassroomLesson("72","86","线性代数（甲）","对应课堂","东2-203","1",lessons.get(0).start);new ClassroomStore(app,account).catalog(List.of(linked));
        activity=startActivitySync(new Intent(app,TimetableActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle(1000);capture("timetable-consecutive");
        Object page=((ScheduleWorkspaceActivity)activity).currentPage();View grid=findType(((SchedulePage)page).content(),TimetableGrid.class);View course=null;for(int i=0;i<((ViewGroup)grid).getChildCount();i++){View item=((ViewGroup)grid).getChildAt(i);if(item.getContentDescription()!=null&&item.getContentDescription().toString().startsWith("线性代数")){course=item;break;}}
        check(course!=null,"school timetable monitor entry has a real course block");View courseView=course;runOnMainSync(()->courseView.requestRectangleOnScreen(new android.graphics.Rect(0,0,courseView.getWidth(),courseView.getHeight()),true));idle(200);
        ActivityMonitor opening=addMonitor(ClassroomTranscriptActivity.class.getName(),null,false);longTap(courseView);Activity next=waitForMonitorWithTimeout(opening,5000);removeMonitor(opening);check(next!=null,"school timetable long-press opens its actual matched classroom");
        Activity table=activity;activity=next;idle(400);check(ClassroomMonitorStore.key((ClassroomLesson)field(activity,"lesson")).equals("72/86"),"timetable monitor binding uses actual date and start identity");capture("monitor-from-school-grid");runOnMainSync(activity::finish);activity=table;idle(250);
        lookupCatalog=List.of(linked);new ClassroomStore(app,account).catalog(List.of());
        tap(courseView);idle(300);ActivityMonitor detail=addMonitor(ClassroomTranscriptActivity.class.getName(),null,false);tapText("课堂监视");next=waitForMonitorWithTimeout(detail,5000);removeMonitor(detail);check(next!=null,"school course detail refreshes an uncached catalogue and directly opens monitor");activity=next;idle(300);check(new ClassroomStore(app,account).catalog().size()==1,"matched catalogue is saved through the existing authenticated client");runOnMainSync(activity::finish);activity=table;lookupCatalog=null;
        runOnMainSync(activity::finish);activity=null;settings.options(true,false);
    }
    private static View findType(View root,Class<?> type){if(type.isInstance(root))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findType(group.getChildAt(i),type);if(found!=null)return found;}return null;}
    private void horizontalSwipe(View view,float start,float end){int[] xy=new int[2];runOnMainSync(()->view.getLocationOnScreen(xy));float y=xy[1]+view.getHeight()/2f;long now=SystemClock.uptimeMillis();pointer(now,now,MotionEvent.ACTION_DOWN,xy[0]+view.getWidth()*start,y);for(int i=1;i<=12;i++){SystemClock.sleep(18);pointer(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,xy[0]+view.getWidth()*(start+(end-start)*i/12),y);}pointer(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,xy[0]+view.getWidth()*end,y);}
    private void holdOut(View view,String screenshot)throws Exception{int[] xy=new int[2];runOnMainSync(()->view.getLocationOnScreen(xy));float x=xy[0]+view.getWidth()/2f,y=xy[1]+view.getHeight()/2f;long now=SystemClock.uptimeMillis();pointer(now,now,MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(450);capture(screenshot);pointer(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,y+100);pointer(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y+100);idle(450);}
    private void longTap(View view){android.graphics.Rect rect=new android.graphics.Rect();runOnMainSync(()->view.getGlobalVisibleRect(rect));long now=SystemClock.uptimeMillis();pointer(now,now,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY());SystemClock.sleep(700);pointer(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY());}
    private void backgroundChecks()throws Exception{
        ClassroomMonitorStore store=new ClassroomMonitorStore(app,account);store.stopAll();phase=0;liveFixture=true;socketOpens=0;materialGate=new CountDownLatch(1);
        File initial=new ClassroomStore(app,account).localLesson(selected);ClassroomStore.write(new File(initial,"monitor-stage.json"),new JSONObject().put("status","2"));store.add(selected,false);
        activity=startActivitySync(new Intent(app,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle(3500);
        check(running()&&socketOpens==1,"foreground Dashboard restores registered owner; stale stage2 and blocked materials cannot suppress live connection");materialGate.countDown();materialGate=null;
        File dir=new ClassroomStore(app,account).localLesson(selected);int before=ClassroomTranscript.snapshot(ClassroomLiveTranscripts.read(dir)).length();
        getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);getUiAutomation().executeShellCommand("input keyevent 223").close();SystemClock.sleep(34_000);
        int received=ClassroomTranscript.snapshot(ClassroomLiveTranscripts.read(dir)).length();check(received>before+10&&socketOpens==1,"socket keeps appending while screen off; PPT polling never recreates connection");check(running(),"foreground owner survives screen-off interval");
        getUiAutomation().executeShellCommand("input keyevent 224").close();lastSocket.abort();idle(7000);check(socketOpens==2&&ClassroomTranscript.snapshot(ClassroomLiveTranscripts.read(dir)).length()>received,"real controller reconnects and appends after stream interruption");
        FakeSocket old=lastSocket;liveFixture=false;field(old.listener,"verifiedAt",0L);java.lang.reflect.Method verify=old.listener.getClass().getDeclaredMethod("verifyCurrent");verify.setAccessible(true);runOnMainSync(()->{try{verify.invoke(old.listener);}catch(Exception failure){throw new RuntimeException(failure);}});
        long limit=SystemClock.elapsedRealtime()+4000;while(!old.closed&&SystemClock.elapsedRealtime()<limit)SystemClock.sleep(20);check(old.closed,"fresh non-live qualification closes the old classroom stream");
        synchronized(old.listener){String saved=((JSONObject)field(old.listener,"state")).toString();old.listener.onOpen(old,response(old.request,"text/plain",new byte[0]));old.listener.onMessage(old,"in{\"socketID\":\"late\"}");old.listener.onMessage(old,"pi");old.listener.onMessage(old,"cd1&m"+new JSONObject().put("sourcetext","结束后的迟到帧").put("end_time","1").put("text_begin_time",System.currentTimeMillis()).put("text_end_time",System.currentTimeMillis()));check(saved.equals(((JSONObject)field(old.listener,"state")).toString()),"invalidated old listener cannot append late captions or change its snapshot");}
        check(!"实时连接".equals(ClassroomStore.read(new File(dir,"live-status.json")).optString("state")),"late handshake cannot restore realtime status after qualification loss");
        liveFixture=true;idle(7500);check(socketOpens==3&&!lastSocket.closed,"independent live retry recovers without a PPT cache-state change");
        store.stopAll();runOnMainSync(()->ClassroomMonitor.changed(app));idle(500);check(!running()&&lastSocket.closed,"stop releases foreground owner and continuous socket");liveFixture=false;
        Field closing=ClassroomLiveTranscripts.class.getDeclaredField("cleanup");closing.setAccessible(true);java.util.concurrent.ExecutorService closeWorker=(java.util.concurrent.ExecutorService)closing.get(null);check(closeWorker.submit(()->Looper.myLooper()!=Looper.getMainLooper()).get(5,TimeUnit.SECONDS),"service TLS cleanup queue drains outside the main thread");
    }
    private boolean running(){for(ActivityManager.RunningServiceInfo service:app.getSystemService(ActivityManager.class).getRunningServices(100))if(service.service.getClassName().equals(ClassroomMonitorService.class.getName()))return service.foreground;return false;}
    private final class FakeSocket implements WebSocket {
        final Request request;final WebSocketListener listener;final ScheduledExecutorService sender=Executors.newSingleThreadScheduledExecutor();volatile boolean closed;int frames;long begin;
        FakeSocket(Request request,WebSocketListener listener){this.request=request;this.listener=listener;socketOpens++;sender.schedule(()->listener.onOpen(this,response(request,"text/plain",new byte[0])),50,TimeUnit.MILLISECONDS);}
        public Request request(){return request;}public long queueSize(){return 0;}
        public boolean send(String text){if(closed)return false;if(text.startsWith("in")){listener.onMessage(this,"in{\"socketID\":\"fixture\"}");sender.scheduleAtFixedRate(()->{if(closed)return;try{if(frames%4==0)begin=System.currentTimeMillis();JSONObject payload=new JSONObject().put("sourcetext","实时推送第"+frames/4+"句，版本"+frames%4).put("end_time",frames%4==3?"1":"0").put("text_begin_time",begin).put("text_end_time",System.currentTimeMillis());frames++;listener.onMessage(this,"cd1&m"+payload);}catch(Exception failure){throw new RuntimeException(failure);}},0,200,TimeUnit.MILLISECONDS);}else if(text.equals("pi"))listener.onMessage(this,"po");return true;}
        public boolean send(okio.ByteString bytes){return false;}public boolean close(int code,String reason){if(closed)return true;closed=true;sender.shutdownNow();listener.onClosed(this,code,reason);return true;}public void cancel(){close(1000,"cancel");}void abort(){if(closed)return;closed=true;sender.shutdownNow();listener.onFailure(this,new IOException("fixture interruption"),null);}
    }
    private void idle(long delay){waitForIdleSync();SystemClock.sleep(delay);waitForIdleSync();}
    private void tap(View view){int[] xy=new int[2];runOnMainSync(()->view.getLocationOnScreen(xy));float x=xy[0]+view.getWidth()/2f,y=xy[1]+view.getHeight()/2f;long at=SystemClock.uptimeMillis();pointer(at,at,MotionEvent.ACTION_DOWN,x,y);pointer(at,at+80,MotionEvent.ACTION_UP,x,y);}
    private void swipe(float x,float from,float to){long at=SystemClock.uptimeMillis();pointer(at,at,MotionEvent.ACTION_DOWN,x,from);for(int i=1;i<=12;i++){SystemClock.sleep(18);pointer(at,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,from+(to-from)*i/12);}pointer(at,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,to);}
    private void pointer(long down,long at,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,at,action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(event);event.recycle();}
    private void capture(String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();File dir=new File(app.getExternalFilesDir(null),"monitor-validation");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+"-"+args.getString("theme","light")+"-"+args.getString("widthDp","400")+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
