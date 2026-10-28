package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native interaction/render evidence in a dedicated synthetic package on the shared emulator. */
public final class ClassroomBatchInstrumentation extends Instrumentation {
    private Bundle args;private Activity lastResumed;private int assertions;
    private boolean manualSeed;
    private Activity backgroundGateScreen;
    private volatile boolean backgroundObserved;
    private volatile long backgroundAt;
    private Runnable backgroundGateReady;
    private int gestureNumber;
    @Override public void onCreate(Bundle args){this.args=args==null?new Bundle():args;super.onCreate(args);start();}
    @Override public Activity newActivity(ClassLoader loader,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        Activity screen=super.newActivity(loader,name,intent);Configuration config=new Configuration();config.fontScale=Float.parseFloat(args.getString("font","1"));
        if(args.containsKey("widthDp")){var metrics=getTargetContext().getResources().getDisplayMetrics();int width=Integer.parseInt(args.getString("widthDp"));boolean landscape="landscape".equals(args.getString("orientation"));int pixels=landscape?Math.max(metrics.widthPixels,metrics.heightPixels):Math.min(metrics.widthPixels,metrics.heightPixels);config.densityDpi=Math.round(pixels*160f/width);config.screenWidthDp=width;config.screenHeightDp=Math.round((landscape?Math.min(metrics.widthPixels,metrics.heightPixels):Math.max(metrics.widthPixels,metrics.heightPixels))*160f/config.densityDpi);config.smallestScreenWidthDp=Math.min(width,config.screenHeightDp);}
        screen.applyOverrideConfiguration(config);return screen;
    }
    @Override public void callActivityOnCreate(Activity screen,Bundle state){
        screen.setRequestedOrientation("landscape".equals(args.getString("orientation"))?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.callActivityOnCreate(screen,state);
    }
    @Override public void callActivityOnResume(Activity screen){super.callActivityOnResume(screen);lastResumed=screen;}
    @Override public void callActivityOnPause(Activity screen){super.callActivityOnPause(screen);if(screen==backgroundGateScreen){backgroundAt=SystemClock.elapsedRealtime();backgroundObserved=true;Runnable ready=backgroundGateReady;backgroundGateReady=null;if(ready!=null)new Handler(Looper.getMainLooper()).post(ready);}}
    @Override public void onStart(){Bundle report=new Bundle();Activity opened=null;try{
        Context app=getTargetContext();if(!"com.donglan.chrona.batch.validation".equals(app.getPackageName())||!"ranchu".equals(Build.HARDWARE))throw new SecurityException("Synthetic batch package on shared official emulator required");
        ZjuSettingsStore settings=new ZjuSettingsStore(app);if(settings.configured()&&!settings.account().equals("00000000-0000-0000-0000-000000000036"))throw new SecurityException("Fixture refuses another account");
        WebDavSettingsStore sync=new WebDavSettingsStore(app);if(sync.configured()||sync.automatic())throw new SecurityException("Fixture refuses external sync");
        if("dashboard_batch".equals(args.getString("mode"))){ThemeStore.setMode(app,args.getString("theme","light"));report.putString("dashboard_batch_report",dashboardBatch(app));finish(-1,report);return;}
        if("cached_background".equals(args.getString("mode"))){ThemeStore.setMode(app,args.getString("theme","light"));report.putString("cached_background_report",cachedBackground(app,settings));finish(-1,report);return;}
        settings.prefs.edit().putString("account","00000000-0000-0000-0000-000000000036").putString("credentials","synthetic-not-a-login").putBoolean("enabled",true).commit();
        ThemeStore.setMode(app,args.getString("theme","light"));ClassroomStore store=new ClassroomStore(app,settings.account());
        if("fixture_cleanup".equals(args.getString("mode"))){report.putString("fixture_cleanup_report",ClassroomWorkDeviceCheck.cleanupOwnedResults(this));cleanupSelectionResults(app,settings.account());finish(-1,report);return;}
        if("selection_navigation".equals(args.getString("mode"))){report.putString("selection_navigation_report",selectionNavigation(app,store));finish(-1,report);return;}
        if("classroom_work".equals(args.getString("mode"))){report.putString("classroom_work_report",ClassroomWorkDeviceCheck.run(this));finish(-1,report);return;}
        if("classroom_notifications".equals(args.getString("mode"))){if(Build.VERSION.SDK_INT>=33)getUiAutomation().grantRuntimePermission(app.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);report.putString("classroom_notifications_report",ClassroomNotificationDeviceCheck.run(this));finish(-1,report);return;}
        List<ClassroomLesson> catalog=new ArrayList<>();long now=System.currentTimeMillis();
        for(int i=0;i<54;i++){ClassroomLesson lesson=new ClassroomLesson("701",Integer.toString(71000+i),"批量课程甲","第 "+(i+1)+" 讲","教室A","6",now-(i+1)*3600000L);catalog.add(lesson);fixture(store.localLesson(lesson),lesson,i<2,i==1);}
        ClassroomLesson offline=new ClassroomLesson("702","72000","批量课程甲","离线共享课堂","","6",now-3600000L);
        File shared=new File(new File(ClassroomStore.root(app),"shared"),"702-72000");shared.mkdirs();fixture(shared,offline,true,false);
        String longCourseId="00000000000000000000704";ClassroomLesson longCourse=new ClassroomLesson(longCourseId,"72001","批量课程甲","长身份课堂","","6",now-3600000L);
        File longShared=new File(new File(ClassroomStore.root(app),"shared"),longCourseId+"-72001");longShared.mkdirs();fixture(longShared,longCourse,true,false);
        store.catalog(catalog);errorRecovery(app,catalog.get(1),store.localLesson(catalog.get(1)));
        opened=startActivitySync(new Intent(app,ClassroomSummaryActivity.class).putExtra("classroom_mode",3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->lastResumed instanceof ScheduleWorkspaceActivity&&page()!=null&&!busy(),"history loads locally");
        require(((List<?>)field(session(),"history")).size()==56,"complete history includes offline shared, draft and downloaded-only");
        tap((View)field(page(),"historyCourse"));tapText("批量课程甲（701）");await(()->"701".equals(field(session(),"historyCourse")),"courseId filter selected");
        require(((List<?>)field(page(),"matchingLessons")).size()==54,"filter applies to complete history before pagination");
        capture("history-filter");enterSelectionByLongPress();tap((View)field(page(),"selectAll"));require(((Set<?>)field(session(),"selected")).size()==54,"all selects across two pages");
        capture("selection-all");DateIntervalBar pages=(DateIntervalBar)field(page(),"pageBar");tap(pages.getChildAt(2));await(()->(int)field(session(),"page")==1,"second page reached by real tap");
        require(((Map<?,?>)field(page(),"selectionMarkers")).size()==4,"remaining four rows retain selection");
        Object retained=session();Activity previous=lastResumed;runOnMainSync(previous::recreate);await(()->lastResumed!=previous&&session()==retained&&!busy(),"rotation/recreate retains selection owner");require(((Set<?>)field(session(),"selected")).size()==54,"recreate keeps cross-page selection");
        tap((View)field(page(),"historyCourse"));tapText("批量课程甲（"+longCourseId+"）","long-course-menu");await(()->longCourseId.equals(field(session(),"historyCourse")),"complete long course identity selected");
        TextView longLabel=(TextView)field(page(),"historyCourse");require(longLabel.getLayout()!=null&&longLabel.getLayout().getLineEnd(longLabel.getLineCount()-1)==longLabel.length(),"multiline field lays out complete long identity");for(int line=0;line<longLabel.getLineCount();line++)require(longLabel.getLayout().getEllipsisCount(line)==0,"long identity has no ellipsis");capture("long-course-selected");
        tap((View)field(page(),"historyCourse"));tapText("批量课程甲（702）");await(()->"702".equals(field(session(),"historyCourse")),"offline-only same-name course is a distinct filter option");
        require("批量课程甲（702）".contentEquals(((TextView)field(page(),"historyCourse")).getText())&&((List<?>)field(page(),"matchingLessons")).size()==1,"selected label keeps complete courseId and same-name identities never merge");
        require(((Set<?>)field(session(),"selected")).isEmpty()&&!(boolean)field(session(),"selecting"),"course filter clears old selection");
        View row=(View)((TextView)((Map<?,?>)field(page(),"selectionMarkers")).values().iterator().next()).getParent();capture("offline-before-longpress");longPress(row,()->lastResumed instanceof ScheduleWorkspaceActivity&&page() instanceof ClassroomSummaryPage&&((Set<?>)field(session(),"selected")).contains("702/72000"));require(((Set<?>)field(session(),"selected")).size()==1,"history long press enters shared selection mode");
        capture("offline-selection");
        if("true".equals(args.getString("manual_export"))){manualSeed=true;report.putString("manual_export_seed",new JSONObject().put("seedOnly",true).put("savedPickerVerified",false).put("selectedCourseId","702").put("selectedLessonId","72000").put("readyFor","Real system document picker and automatic share, owned by main agent").toString());finish(-1,report);return;}
        // The real shared writer receives the exact frozen selection and preserves sparse image 17.
        List<ClassroomLesson> selected=(List<ClassroomLesson>)invoke(page(),"selectionSnapshot");File archive=new File(app.getExternalFilesDir(null),"batch-native.zip");
        try(OutputStream output=new FileOutputStream(archive)){ClassroomExport.BatchResult result=ClassroomExport.writeBatch(List.of(new ClassroomExport.LessonFiles(selected.get(0),store.lesson(selected.get(0)))),output,p->{},l->{},file->"jpg");require(result.success==1&&result.failures.isEmpty(),"native sparse prefixed summary exports");}
        // Active local work must be skipped without stopping it or marking the monitor deleted.
        ClassroomStore.begin(shared);try{tapDelete();await(()->!((String)field(session(),"operationMessage")).startsWith("正在"),"active delete result");require(new File(shared,"summary.json").isFile()&&ClassroomStore.active(shared),"active shared history is preserved");}finally{ClassroomStore.end(shared);}
        tapDelete();await(()->!shared.exists()&&!busy(),"inactive selected local copies delete");require(new ClassroomMonitorStore(app,settings.account()).read().getJSONObject("summaries").getJSONObject("702/72000").optString("state").equals("deleted"),"delete records terminal monitor state");
        capture("delete-result");
        report.putString("batch_report",new JSONObject().put("assertions",assertions).put("theme",args.getString("theme","light")).put("font",args.getString("font","1")).put("native",true).toString());finish(-1,report);
    }catch(Throwable failure){try{capture("failure-"+args.getString("mode","classroom"));}catch(Exception captureFailure){failure.addSuppressed(captureFailure);}report.putString("error",android.util.Log.getStackTraceString(failure));finish(0,report);}finally{
        if(!manualSeed){if(lastResumed!=null)runOnMainSync(()->lastResumed.finish());if(!"dashboard_batch".equals(args.getString("mode")))new ZjuSettingsStore(getTargetContext()).prefs.edit().putBoolean("enabled",false).commit();}
    }}
    private String selectionNavigation(Context app,ClassroomStore store)throws Exception {
        if(Build.VERSION.SDK_INT>=33)getUiAutomation().grantRuntimePermission(app.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
        List<ClassroomLesson> previous=store.catalog(),catalog=new ArrayList<>();
        String identity=Long.toUnsignedString(System.nanoTime());long now=System.currentTimeMillis();
        for(int i=0;i<24;i++)catalog.add(new ClassroomLesson("777171",identity+i,"选择导航课程","第 "+i+" 讲","教室A","6",now-(i+1)*3600000L));
        ClassroomLesson cached=new ClassroomLesson("777172",identity,"选择导航历史","完整缓存","教室A","6",now-3600000L);
        catalog.add(cached);fixture(store.localLesson(cached),cached,false,false);store.catalog(catalog);
        java.net.ProxySelector proxy=java.net.ProxySelector.getDefault();java.util.concurrent.atomic.AtomicInteger network=new java.util.concurrent.atomic.AtomicInteger();
        java.net.ProxySelector.setDefault(new java.net.ProxySelector(){public List<java.net.Proxy> select(java.net.URI uri){network.incrementAndGet();throw new SecurityException("Selection fixture forbids network");}public void connectFailed(java.net.URI uri,java.net.SocketAddress at,IOException error){}});
        try {
            Activity owner=startActivitySync(new Intent(app,ClassroomSummaryActivity.class).putExtra("classroom_mode",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->lastResumed==owner&&page()!=null&&!busy()&&((List<?>)field(page(),"matchingLessons")).size()==24,"classroom list ready");
            ClassroomSummaryPage selectedPage=(ClassroomSummaryPage)page();Object ownerSession=session();
            View firstCard=(View)((Map<?,TextView>)field(page(),"selectionMarkers")).values().iterator().next().getParent();
            assertHeldEnvelope(firstCard,"classroom-card-held");
            View originalBody=(View)field(page(),"listBody");TextView count=(TextView)field(page(),"resultsCount");
            int[] originalAt=new int[2];runOnMainSync(()->count.getLocationInWindow(originalAt));
            enterSelectionByLongPress();require((boolean)field(session(),"selecting")&&((Set<?>)field(session(),"selected")).size()==1,"long press enters mode and selects held lesson");
            assertHeldEnvelope((View)field(page(),"selectAll"),"selection-action-held");
            require(findText(owner.getWindow().getDecorView(),"完成")==null&&findText(owner.getWindow().getDecorView(),"选择")==null,"explicit selection buttons removed");
            ScrollView scroll=(ScrollView)field(page(),"listScroll");runOnMainSync(()->scroll.scrollTo(0,160));waitForIdleSync();int before=scroll.getScrollY();
            List<Integer> restoredX=new ArrayList<>();android.view.ViewTreeObserver.OnPreDrawListener observer=()->{if(count.isShown()){int[] at=new int[2];count.getLocationInWindow(at);restoredX.add(at[0]);}return true;};
            runOnMainSync(()->count.getViewTreeObserver().addOnPreDrawListener(observer));
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(()->!(boolean)field(ownerSession,"selecting")&&count.isShown(),"back exits selection only");
            require(lastResumed==owner&&!owner.isFinishing()&&page()==selectedPage&&session()==ownerSession,"back retains page and session");
            require(((Set<?>)field(ownerSession,"selected")).isEmpty()&&field(page(),"listBody")==originalBody,"back clears selection without rebuilding rows");
            require(scroll.getScrollY()==before,"back retains reading offset");
            runOnMainSync(()->count.getViewTreeObserver().removeOnPreDrawListener(observer));
            require(!restoredX.isEmpty()&&restoredX.stream().allMatch(x->Math.abs(x-originalAt[0])<=1),"normal count never shifts horizontally "+restoredX);
            for(int i=0;i<3;i++){enterSelectionByLongPress();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(count::isShown,"rapid selection returns to stable heading");}
            capture("selection-back-restored");
            tapText("历史");await(()->(int)field(session(),"mode")==3&&!busy(),"history ready");
            LinearLayout tools=(LinearLayout)field(page(),"historyTools");View course=(View)field(page(),"historyCourse"),order=(View)field(page(),"historyOrder"),direction=(View)field(page(),"historyDirection"),refresh=(View)field(page(),"refresh");
            require(Math.abs(course.getWidth()*2-tools.getWidth())<=1,"course filter occupies half row");
            require(direction.getWidth()==refresh.getWidth()&&direction.getHeight()==refresh.getHeight(),"direction matches refresh 48dp geometry");
            int[] c=new int[2],o=new int[2],d=new int[2];runOnMainSync(()->{course.getLocationInWindow(c);order.getLocationInWindow(o);direction.getLocationInWindow(d);});
            require(c[0]+course.getWidth()<o[0]&&o[0]+order.getWidth()<d[0],"course order direction stay left-to-right with gaps");
            capture("history-one-row-tools");
            View historyCard=(View)((Map<?,TextView>)field(page(),"selectionMarkers")).values().iterator().next().getParent();assertHeldEnvelope(historyCard,"history-card-held");
            tap(course);tapText("选择导航历史");await(()->"777172".equals(field(session(),"historyCourse"))&&!busy(),"cached history filter");
            enterSelectionByLongPress();
            View download=(View)field(page(),"batchDownload");if(download.isShown())tap(download);else{tap((View)field(page(),"batchMenu"));tapText("下载素材");}
            await(()->!(boolean)field(session(),"selecting")&&((Set<?>)field(session(),"selected")).isEmpty(),"download exits selection");
            await(()->{ClassroomBatchWork work=ClassroomBatchWork.find(new ZjuSettingsStore(app).account());return work==null||!work.running;},"cached download settles");
            require(network.get()==0,"cached download uses no network or AI");capture("download-exits-selection");
            tapText("监视");await(()->(int)field(session(),"mode")==2&&!busy(),"monitor overview ready");
            runOnMainSync(()->{try{Object ownerState=session();Field monitoring=ownerState.getClass().getDeclaredField("monitoring");monitoring.setAccessible(true);monitoring.set(ownerState,new JSONObject().put("rules",new JSONArray().put(new JSONObject().put("lesson",cached.json()).put("repeat",false))).put("catalog",new JSONArray()).put("done",new JSONArray()));invoke(page(),"update");}catch(Exception error){throw new AssertionError(error);}});
            assertHeldEnvelope(((LinearLayout)field(page(),"listBody")).getChildAt(0),"monitor-card-held");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(owner::isFinishing,"next back leaves workspace normally");
            return "PASS: "+assertions+" assertions; classroom/history long-press/back, stable heading, half-width tools and download exits; zero network";
        } finally {java.net.ProxySelector.setDefault(proxy);store.catalog(previous);store.deleteLesson(cached,()->{});app.getSystemService(android.app.NotificationManager.class).cancel("classroom:"+new ZjuSettingsStore(app).account()+":"+cached.courseId+":"+cached.id,0);}
    }
    private static void cleanupSelectionResults(Context app,String account){
        if(!"00000000-0000-0000-0000-000000000036".equals(account)||ClassroomBatchWork.find(account)!=null)throw new SecurityException("Owned idle selection fixture required");
        android.app.NotificationManager manager=app.getSystemService(android.app.NotificationManager.class);
        String prefix="classroom:"+account+":777172:";
        for(android.service.notification.StatusBarNotification notice:manager.getActiveNotifications()){
            String tag=notice.getTag();android.app.Notification notification=notice.getNotification();
            if(notice.getId()==0&&tag!=null&&tag.startsWith(prefix)&&tag.substring(prefix.length()).matches("[0-9]+")&&"chrona_classroom_results".equals(notification.getChannelId())&&(notification.flags&android.app.Notification.FLAG_ONGOING_EVENT)==0)manager.cancel(tag,0);
        }
    }
    private static TextView findText(View root,String value){if(root instanceof TextView text&&value.contentEquals(text.getText()))return text;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView text=findText(group.getChildAt(i),value);if(text!=null)return text;}return null;}
    private void enterSelectionByLongPress(){View row=(View)((Map<?,TextView>)field(page(),"selectionMarkers")).values().iterator().next().getParent();longPress(row,()->(boolean)field(session(),"selecting"));}
    private void assertHeldEnvelope(View target,String imageName)throws Exception {
        boolean selectingBefore=(boolean)field(session(),"selecting");
        runOnMainSync(()->target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),true));waitForIdleSync();
        int[] at=new int[2];runOnMainSync(()->target.getLocationOnScreen(at));long down=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,at[0]+target.getWidth()/2f,at[1]+target.getHeight()/2f,0);
        try{sendPointerSync(event);}finally{event.recycle();}
        Bitmap peak=null;
        try{
            SystemClock.sleep(180);
            runOnMainSync(()->{
                require(target.getScaleX()>1.04f,"real held feedback reaches original scale");
                Matrix matrix=new Matrix();target.transformMatrixToGlobal(matrix);RectF bounds=new RectF(0,0,target.getWidth(),target.getHeight());matrix.mapRect(bounds);
                ScrollView viewport=(ScrollView)field(page(),"listScroll");int[] origin=new int[2];viewport.getLocationOnScreen(origin);
                for(android.view.ViewParent parent=target.getParent();parent instanceof android.view.ViewGroup group;parent=parent.getParent()){
                    require(!group.getClipChildren()&&!group.getClipToPadding(),"held drawing traverses unclipped intermediate "+group.getClass().getSimpleName());
                    if(group==viewport)break;
                }
                require(viewport.getClipBounds()!=null,"actual viewport still protects outer bounds");
                require(bounds.left>=origin[0]-.5f&&bounds.right<=origin[0]+viewport.getWidth()+.5f,"held horizontal envelope fits real viewport "+bounds);
                require(bounds.top>=origin[1]-.5f&&bounds.bottom<=origin[1]+viewport.getHeight()+.5f,"held vertical envelope fits real viewport "+bounds);
            });
            peak=getUiAutomation().takeScreenshot();
        }finally{MotionEvent cancel=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,at[0]+target.getWidth()/2f,at[1]+target.getHeight()/2f,0);try{sendPointerSync(cancel);}finally{cancel.recycle();}SystemClock.sleep(200);}
        if(!selectingBefore&&(boolean)field(session(),"selecting"))runOnMainSync(()->page().handleBack());
        if(peak!=null){float scale=Math.min(1f,1200f/Math.max(peak.getWidth(),peak.getHeight()));Bitmap small=Bitmap.createScaledBitmap(peak,Math.round(peak.getWidth()*scale),Math.round(peak.getHeight()*scale),true);try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"batch-"+imageName+"-"+args.getString("theme","light")+"-font1.jpg"))){small.compress(Bitmap.CompressFormat.JPEG,75,out);}if(small!=peak)small.recycle();peak.recycle();}
    }

    private String cachedBackground(Context app,ZjuSettingsStore settings)throws Exception {
        require(staticField(ClassroomWorkService.class,"instance")==null,"cache fixture refuses an existing classroom foreground owner");
        settings.save("chrona-local-cache-fixture","synthetic-no-network");settings.prefs.edit().putString("account","00000000-0000-0000-0000-000000000036").putBoolean("enabled",true).putBoolean("timetable",false).commit();
        String account=settings.account();ClassroomStore store=new ClassroomStore(app,account);List<ClassroomLesson> lessons=new ArrayList<>();List<File> owned=new ArrayList<>();String run=Long.toUnsignedString(System.nanoTime());
        for(int i=0;i<3;i++){ClassroomLesson lesson=new ClassroomLesson("703",run+i,"后台缓存课程","完整缓存 "+(i+1),"","6",System.currentTimeMillis()-3600000L);lessons.add(lesson);File dir=store.localLesson(lesson);owned.add(dir);fixture(dir,lesson,false,false);}
        List<ClassroomLesson> previousCatalog=store.catalog();store.catalog(lessons);
        java.net.ProxySelector previousProxy=java.net.ProxySelector.getDefault();java.util.concurrent.atomic.AtomicInteger attempts=new java.util.concurrent.atomic.AtomicInteger();
        java.net.ProxySelector rejectNetwork=new java.net.ProxySelector(){public List<java.net.Proxy> select(java.net.URI uri){attempts.incrementAndGet();throw new SecurityException("Native cache fixture rejects all network routes");}public void connectFailed(java.net.URI uri,java.net.SocketAddress address,IOException failure){throw new SecurityException("Native cache fixture has no network routes");}};
        Activity screen=null;ClassroomBatchWork[] batch={null};JSONArray events=new JSONArray();Object[] service={null};boolean[] promoted={false};
        try{
            java.net.ProxySelector.setDefault(rejectNetwork);
            String[] credentials=settings.credentials();try(ClassroomClient probe=new ClassroomClient(credentials[0],credentials[1])){Object school=field(probe,"school");okhttp3.OkHttpClient http=(okhttp3.OkHttpClient)field(school,"http");require(http.proxy()==null&&http.proxySelector()==rejectNetwork,"same production transport captures rejecting route selector before requests");}finally{Arrays.fill(credentials,"");}
            screen=startActivitySync(new Intent(app,ClassroomSummaryActivity.class).putExtra("classroom_mode",3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity owner=screen;
            await(()->lastResumed==owner&&page()!=null&&!busy()&&owner.hasWindowFocus(),"cached catalogue foreground before real batch start");
            backgroundGateScreen=owner;backgroundObserved=false;backgroundAt=0;
            runOnMainSync(()->{
                batch[0]=ClassroomBatchWork.start(app,account,lessons);
                // Gate only this fixture's held ready callback. No Store lock is held: Store's
                // constructor also takes LOCK, so a worker-begin gate would block main-thread next().
                Map<?,?> holds=(Map<?,?>)staticField(ClassroomWorkService.class,"batches");Object hold=holds.get(batch[0]);if(hold==null)throw new AssertionError("Batch foreground hold missing");
                Runnable original=(Runnable)field(hold,"ready");setField(hold,"ready",(Runnable)()->{
                    service[0]=staticField(ClassroomWorkService.class,"instance");
                    promoted[0]=classroomForeground(app);
                    Runnable ready=()->{original.run();observeBatchHandoff(batch[0],service[0],events);};
                    if(backgroundObserved)ready.run();else backgroundGateReady=ready;
                });
                if(!owner.moveTaskToBack(true))throw new AssertionError("Could not move cache owner to background");
            });
            await(()->backgroundObserved,"real Activity onPause precedes cached prepare work");
            await(()->!batch[0].running,"real cached batch completes while Activity remains in background");
            require(!owner.hasWindowFocus()&&backgroundAt>0,"cached queue completed without restoring the Activity");
            require(promoted[0],"OS reports the real batch service foreground before its ready callback");
            require(batch[0].success==3&&batch[0].failures.isEmpty()&&batch[0].skipped==0&&batch[0].cancelledCount==0,"three independent prepare results succeed");
            require(events.length()==6,"all three real Session handoffs observed");
            long priorEnd=backgroundAt;for(int i=0;i<events.length();i+=2){JSONObject start=events.getJSONObject(i),end=events.getJSONObject(i+1);require(start.getString("event").equals("start")&&end.getString("event").equals("complete")&&start.getString("lesson").equals(end.getString("lesson")),"each start pairs with its own completion");require(start.getLong("at")>=priorEnd&&start.getLong("at")>=backgroundAt&&start.getBoolean("sameService")&&start.getBoolean("registered"),"sequential prepare starts after prior completion and actual background under same FGS");require(end.getBoolean("success")&&end.getBoolean("sameService"),"real Session finishes successfully before its batch hold releases");priorEnd=end.getLong("at");}
            require(attempts.get()==0,"cached prepare makes zero network-route attempts and no AI request");
            await(()->staticField(ClassroomWorkService.class,"instance")==null&&!classroomForeground(app),"final batch hold releases and OS foreground service exits");
            return new JSONObject().put("assertions",assertions).put("success",batch[0].success).put("networkAttempts",attempts.get()).put("actualBackgroundAt",backgroundAt).put("events",events).put("osForeground",promoted[0]).put("sameForegroundService",true).put("serviceExited",true).toString();
        }finally{
            if(batch[0]!=null&&batch[0].running){runOnMainSync(()->batch[0].cancel("Fixture cleanup"));await(()->!batch[0].running,"owned cache batch cancellation cleanup");}
            backgroundGateReady=null;backgroundGateScreen=null;java.net.ProxySelector.setDefault(previousProxy);
            if(screen!=null){Activity close=screen;runOnMainSync(close::finish);waitForIdleSync();}
            store.catalog(previousCatalog);for(int i=0;i<owned.size();i++){File dir=owned.get(i);ClassroomLesson lesson=lessons.get(i);if(!dir.getName().equals(lesson.courseId+"-"+lesson.id))throw new SecurityException("Unowned fixture directory");store.deleteLesson(lesson,()->{});}
        }
    }
    private void observeBatchHandoff(ClassroomBatchWork batch,Object service,JSONArray events){
        ClassroomLessonActivity.Session job=batch.current;if(job==null)return;
        events.put(batchEvent("start",job,service));Runnable completed=job.completed;
        job.completed=()->{events.put(batchEvent("complete",job,service));completed.run();observeBatchHandoff(batch,service,events);};
    }
    private JSONObject batchEvent(String event,ClassroomLessonActivity.Session job,Object service){try{return new JSONObject().put("event",event).put("lesson",job.selected.courseId+"/"+job.selected.id).put("at",event.equals("start")?job.startedAt:SystemClock.elapsedRealtime()).put("sameService",service!=null&&staticField(ClassroomWorkService.class,"instance")==service).put("registered",ClassroomWorkService.find(job.account,job.selected)==job).put("success",job.succeeded);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static boolean classroomForeground(Context app){ActivityManager manager=app.getSystemService(ActivityManager.class);for(ActivityManager.RunningServiceInfo service:manager.getRunningServices(16))if(service.service.getClassName().equals(ClassroomWorkService.class.getName())&&service.foreground)return true;return false;}
    private static Object staticField(Class<?> type,String name){try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(null);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static void setField(Object owner,String name,Object value){try{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private String dashboardBatch(Context app)throws Exception {
        List<Long> owned=new ArrayList<>();long[] candidates=new long[4];String token="批量调用者"+SystemClock.elapsedRealtime();
        SharedPreferences[] preferences={app.getSharedPreferences("usage_guide",0),app.getSharedPreferences("startup_permissions",0),ReleaseUpdates.preferences(app),AgendaReminderPreferences.prefs(app),new ZjuSettingsStore(app).prefs};
        String[] keys={"version","attempted","automatic","enabled","enabled"};Object[] old=new Object[keys.length];for(int i=0;i<keys.length;i++)old[i]=preferences[i].getAll().get(keys[i]);
        preferences[0].edit().putInt(keys[0],4).commit();preferences[1].edit().putBoolean(keys[1],true).commit();preferences[2].edit().putBoolean(keys[2],false).commit();preferences[3].edit().putBoolean(keys[3],false).commit();preferences[4].edit().putBoolean(keys[4],false).commit();
        Activity dashboard=null;JSONArray paths=new JSONArray();
        try{
            synchronized(AndroidSync.LOCK){try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(app)){
                long now=System.currentTimeMillis();for(int i=0;i<4;i++){
                    String title=token+" · "+(i+1);long task=store.insertTask(title,null,"classroom_batch_ui_fixture",now+i);owned.add(task);
                    Long start=i<2?now+86_400_000L+i*3_600_000L:null,end=start==null?null:start+1_800_000L;
                    candidates[i]=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,task,title,start,end,java.time.ZoneId.systemDefault().getId(),"","局部批量UI回归",null,true,null,"event",false));
                    store.updateStatus(task,com.donglan.chrona.data.TaskRecord.NEEDS_REVIEW,null);
                }
            }}
            dashboard=startActivitySync(new Intent(app,DashboardActivity.class).putExtra(DashboardActivity.EXTRA_SECTION,DashboardActivity.SCHEDULE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity screen=dashboard;
            await(()->dashboardReady(screen),"Dashboard schedule initialized");setDashboardQuery(screen,token);await(()->dashboardReady(screen)&&dashboardRows(screen).containsKey(candidates[0])&&dashboardRows(screen).containsKey(candidates[1])&&dashboardRows(screen).size()==2,"scheduled fixtures filtered independently");
            paths.put(exerciseDashboardSelection(screen,new long[]{candidates[0],candidates[1]},"selectedScheduleIds",false,"dashboard-schedule"));
            DockNavigationLayout scheduleFilter=(DockNavigationLayout)field(screen,"filterChips");tapDashboardFilter(scheduleFilter,1,"待安排");
            await(()->dashboardReady(screen)&&(int)field(screen,"scheduleTab")==1&&dashboardRows(screen).containsKey(candidates[2])&&dashboardRows(screen).containsKey(candidates[3])&&dashboardRows(screen).size()==2,"unscheduled fixture path");
            paths.put(exerciseDashboardSelection(screen,new long[]{candidates[2],candidates[3]},"selectedScheduleIds",true,"dashboard-unscheduled"));
            tap(((DockNavigationLayout)field(screen,"navigation")).itemAt(2));await(()->dashboardReady(screen)&&(int)field(screen,"section")==DashboardActivity.INBOX,"inbox entered by real navigation tap");
            setDashboardQuery(screen,token);tapDashboardFilter((DockNavigationLayout)field(screen,"filterChips"),1,"待确认");
            await(()->dashboardReady(screen)&&(int)field(screen,"statusIndex")==1&&dashboardRows(screen).size()==4&&dashboardRows(screen).keySet().containsAll(owned),"pending review isolated to own inputs");
            paths.put(exerciseDashboardSelection(screen,new long[]{owned.get(0),owned.get(1)},"selectedInboxIds",true,"dashboard-inbox"));
            try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(app)){
                for(long task:owned){var record=store.getTask(task);require(record!=null&&record.source.equals("classroom_batch_ui_fixture")&&record.status.equals(com.donglan.chrona.data.TaskRecord.NEEDS_REVIEW),"UI-only selection preserves own task status and actions do not write calendar");}
            }
            return new JSONObject().put("assertions",assertions).put("paths",paths).put("theme",args.getString("theme","light")).put("font",args.getString("font","1")).put("synthetic",true).toString();
        }finally{
            if(dashboard!=null){Activity close=dashboard;runOnMainSync(close::finish);waitForIdleSync();}
            synchronized(AndroidSync.LOCK){try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(app)){for(long task:owned){var record=store.getTask(task);if(record!=null){if(!record.source.equals("classroom_batch_ui_fixture")||!record.rawText.startsWith(token))throw new SecurityException("Fixture cleanup refuses an unowned task");store.deleteTask(task);}}}}
            for(int i=0;i<keys.length;i++){var edit=preferences[i].edit();Object value=old[i];if(value instanceof Boolean flag)edit.putBoolean(keys[i],flag);else if(value instanceof Integer number)edit.putInt(keys[i],number);else edit.remove(keys[i]);edit.commit();}
        }
    }
    private JSONObject exerciseDashboardSelection(Activity screen,long[] ids,String selectionField,boolean publish,String name)throws Exception {
        Map<?,?> rows=dashboardRows(screen);Object first=rows.get(ids[0]),second=rows.get(ids[1]);View firstCard=(View)field(first,"card"),secondCard=(View)field(second,"card");
        TextView firstMarker=(TextView)field(first,"marker"),secondMarker=(TextView)field(second,"marker");View bar=(View)field(screen,"selectionBar"),heading=(View)bar.getParent();Object originalResults=field(screen,"results"),background=firstCard.getBackground();
        List<?> beforeOrder=new ArrayList<>(rows.keySet());Object leadingId=beforeOrder.get(0);int height=heading.getHeight(),leadingTop=((View)field(rows.get(leadingId),"card")).getTop();require(bar.getVisibility()==View.GONE&&firstMarker.getVisibility()==View.GONE,"normal count row hides batch actions and circles");
        JSONObject beforeMetrics=dashboardMetrics(screen,selectionField,firstCard,secondCard,heading);
        capture(name+"-before");longPress(dashboardTitle(liveDashboardCard(screen,ids[0])),()->lastResumed==screen&&((Set<?>)field(screen,selectionField)).contains(ids[0]));await(()->((Set<?>)field(screen,selectionField)).contains(ids[0])&&((View)field(screen,"selectionBar")).getVisibility()==View.VISIBLE&&((View)field(screen,"selectionBar")).getAlpha()>.99f,"real long press enters "+name);
        firstCard=liveDashboardCard(screen,ids[0]);secondCard=liveDashboardCard(screen,ids[1]);firstMarker=(TextView)field(dashboardRows(screen).get(ids[0]),"marker");secondMarker=(TextView)field(dashboardRows(screen).get(ids[1]),"marker");bar=(View)field(screen,"selectionBar");heading=(View)bar.getParent();
        JSONObject selectedMetrics=dashboardMetrics(screen,selectionField,firstCard,secondCard,heading);writeEvidence(name+"-layout",new JSONObject().put("before",beforeMetrics).put("selected",selectedMetrics));capture(name+"-after-enter");
        require(heading.getHeight()==height,"count-row height before="+height+" after="+heading.getHeight()+"; see "+name+"-layout");
        require(beforeOrder.equals(new ArrayList<>(dashboardRows(screen).keySet())),"selection changed actual row identity order; see "+name+"-layout");
        require(((View)field(dashboardRows(screen).get(leadingId),"card")).getTop()==leadingTop,"leading card layout top before="+leadingTop+" after="+((View)field(dashboardRows(screen).get(leadingId),"card")).getTop()+"; see "+name+"-layout");
        require(field(screen,"results")==originalResults,"results identity changed; see "+name+"-layout");
        require(UiSurfaces.isCardSurface(firstCard)&&firstCard.getBackground().getClass()==background.getClass(),"first-card acrylic surface type changed; see "+name+"-layout");
        TextView count=(TextView)field(screen,"selectionCount");Button all=(Button)field(screen,"selectAllButton"),write=(Button)field(screen,"publishSelectedButton"),delete=(Button)field(screen,"deleteSelectedButton");LinearLayout actionRow=(LinearLayout)bar;
        require(actionRow.getChildAt(0)==count&&actionRow.getChildAt(1)==all&&actionRow.getChildAt(2)==write&&actionRow.getChildAt(3)==delete,"existing count/all/publish/delete order unchanged");
        require(write.getVisibility()==(publish?View.VISIBLE:View.GONE)&&all.isEnabled()&&delete.isEnabled()&&all.hasOnClickListeners()&&write.hasOnClickListeners()&&delete.hasOnClickListeners(),"all original actions and conditional publish behavior remain wired");
        require(Math.abs(centerY(count)-centerY(all))<=1&&Math.abs(centerY(count)-centerY(delete))<=1&&(!publish||Math.abs(centerY(count)-centerY(write))<=1),"count and visible actions share the existing row center");
        require(markerMatches(firstMarker,R.drawable.ic_check,UiStyle.colors(screen).primary)&&markerMatches(secondMarker,R.drawable.ic_circle,UiStyle.colors(screen).muted),"real row markers draw original Lucide check/circle");
        int matching=((List<?>)field(screen,"matchingInboxIds")).size();require(count.getText().toString().equals("1/"+matching),"selection count includes complete filtered matches");
        capture(name+"-before-second-tap");tap(dashboardTitle(liveDashboardCard(screen,ids[1])));JSONObject tappedMetrics=dashboardMetrics(screen,selectionField,liveDashboardCard(screen,ids[0]),liveDashboardCard(screen,ids[1]),(View)((View)field(screen,"selectionBar")).getParent());writeEvidence(name+"-second-tap",new JSONObject().put("before",selectedMetrics).put("after",tappedMetrics));capture(name+"-after-second-tap");
        require(((Set<?>)field(screen,selectionField)).contains(ids[1]),"second row not selected; actualActivity="+lastResumed.getClass().getSimpleName()+" selected="+field(screen,selectionField)+"; see "+name+"-second-tap and gesture trace");
        require(!screen.isFinishing(),"caller finishing after selected-row tap; see "+name+"-second-tap");require(lastResumed==screen,"selected-row tap resumed "+lastResumed.getClass().getSimpleName()+" instead of Dashboard; see gesture trace");
        tap(dashboardTitle(liveDashboardCard(screen,ids[1])));require(!((Set<?>)field(screen,selectionField)).contains(ids[1]),"second selected-row tap clears only its own selection");
        tap((View)field(screen,"selectAllButton"));require(((Set<?>)field(screen,selectionField)).size()==matching&&((TextView)field(screen,"selectionCount")).getText().toString().equals(matching+"/"+matching),"existing select-all action and count still operate on complete result");
        capture(name+"-selected");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(()->((Set<?>)field(screen,selectionField)).isEmpty()&&((View)field(screen,"selectionBar")).getVisibility()==View.GONE,"real Back exits Dashboard selection before leaving section");
        View afterHeading=(View)((View)field(screen,"selectionBar")).getParent();require(!screen.isFinishing()&&lastResumed==screen&&afterHeading.getHeight()==height&&beforeOrder.equals(new ArrayList<>(dashboardRows(screen).keySet()))&&((View)field(dashboardRows(screen).get(leadingId),"card")).getTop()==leadingTop&&((View)field(dashboardRows(screen).get(ids[0]),"marker")).getVisibility()==View.GONE&&((View)field(dashboardRows(screen).get(ids[1]),"marker")).getVisibility()==View.GONE,"Back hides batch chrome without reordering cards or losing caller page");
        capture(name+"-after-back");return new JSONObject().put("path",name).put("matching",matching).put("headerHeight",height).put("publishVisible",publish).put("nativeBack",true);
    }
    private static boolean dashboardReady(Activity screen){return (boolean)field(screen,"initialDataReady")&&!(boolean)field(screen,"sectionLoadRunning")&&!(boolean)field(screen,"scheduleResultLoading")&&!(boolean)field(screen,"pageTransitionRunning");}
    private static Map<?,?> dashboardRows(Activity screen){return (Map<?,?>)field(screen,"inboxRows");}
    private View liveDashboardCard(Activity screen,long id){await(()->dashboardReady(screen)&&dashboardRows(screen).containsKey(id)&&((View)field(dashboardRows(screen).get(id),"card")).isAttachedToWindow(),"live Dashboard row "+id);return (View)field(dashboardRows(screen).get(id),"card");}
    private JSONObject dashboardMetrics(Activity screen,String selected,View first,View second,View heading){
        JSONObject[] result={null};runOnMainSync(()->{try{
            JSONArray rows=new JSONArray();for(Map.Entry<?,?> entry:dashboardRows(screen).entrySet())rows.put(new JSONObject().put("id",entry.getKey()).put("card",viewFacts((View)field(entry.getValue(),"card"))));
            result[0]=new JSONObject().put("activeActivity",activityFacts()).put("callerFinishing",screen.isFinishing()).put("section",field(screen,"section")).put("selected",new JSONArray((Collection<?>)field(screen,selected))).put("snapshot",field(screen,"snapshot")).put("sectionVersions",String.valueOf(field(screen,"sectionVersions"))).put("dirtySections",String.valueOf(field(screen,"dirtySections"))).put("wallMinute",System.currentTimeMillis()/60000L).put("scrollY",((ScrollView)field(screen,"scroll")).getScrollY()).put("header",viewFacts(heading)).put("firstRequestedCard",viewFacts(first)).put("secondRequestedCard",viewFacts(second)).put("results",viewFacts((View)field(screen,"results"))).put("rowsInMapOrder",rows);
        }catch(Exception failure){throw new IllegalStateException(failure);}});return result[0];
    }
    private void setDashboardQuery(Activity screen,String value){EditText search=findDashboardSearch(screen.getWindow().getDecorView());if(search==null)throw new AssertionError("Missing Dashboard search");runOnMainSync(()->search.setText(value));waitForIdleSync();}
    private static EditText findDashboardSearch(View view){if(view instanceof EditText text&&view.isShown()&&text.getHint()!=null&&text.getHint().toString().startsWith("搜索"))return text;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText found=findDashboardSearch(group.getChildAt(i));if(found!=null)return found;}return null;}
    private static TextView dashboardTitle(View view){if(view instanceof TextView text&&text.getText().toString().startsWith("批量调用者"))return text;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=dashboardTitle(group.getChildAt(i));if(found!=null)return found;}return null;}
    private void tapDashboardFilter(DockNavigationLayout dock,int index,String label){if(dock.isShown())tap(dock.itemAt(index));else{tap((View)dock.getTag());await(()->{var root=getUiAutomation().getRootInActiveWindow();if(root==null)return false;for(var node:root.findAccessibilityNodeInfosByText(label))if(node.getText()!=null&&node.getText().toString().startsWith(label)){Rect bounds=new Rect();node.getBoundsInScreen(bounds);touch(bounds.exactCenterX(),bounds.exactCenterY(),80);return true;}return false;},"compact Dashboard filter "+label);}}
    private static int centerY(View view){int[] point=new int[2];view.getLocationOnScreen(point);return point[1]+view.getHeight()/2;}
    private static boolean markerMatches(TextView marker,int resource,int color){
        if(marker.getVisibility()!=View.VISIBLE)return false;var actual=marker.getCompoundDrawables()[0];if(actual==null)return false;int side=Math.round(22*marker.getResources().getDisplayMetrics().density);if(actual.getBounds().width()!=side||actual.getBounds().height()!=side)return false;
        var expected=marker.getContext().getDrawable(resource).mutate();expected.setBounds(0,0,side,side);expected.setTint(color);Bitmap a=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);actual.draw(new Canvas(a));expected.draw(new Canvas(b));int[] left=new int[side*side],right=new int[side*side];a.getPixels(left,0,side,0,0,side,side);b.getPixels(right,0,side,0,0,side,side);a.recycle();b.recycle();return Arrays.equals(left,right);
    }
    private void fixture(File dir,ClassroomLesson lesson,boolean notes,boolean draft)throws Exception {
        dir.mkdirs();JSONArray segments=new JSONArray().put(new JSONObject().put("start",3).put("end",9).put("text","评分规则与课堂例题。"));
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",segments).put("images",new JSONArray().put(new JSONObject().put("cache_index",0))).put("raw_images",1));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("lesson",lesson.json()).put("images",new JSONArray().put(new JSONObject().put("raw","raw-0.img").put("second",4))));
        Bitmap image=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.rgb(235,241,248));Paint paint=new Paint(3);paint.setColor(Color.rgb(25,55,95));paint.setTextSize(44);canvas.drawText("Sparse image 17",36,110,paint);
        try(OutputStream out=new FileOutputStream(new File(dir,"raw-0.img"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        try(OutputStream out=new FileOutputStream(new File(dir,"image-17.jpg"))){image.compress(Bitmap.CompressFormat.JPEG,92,out);}image.recycle();
        if(notes)ClassroomStore.write(new File(dir,"summary.json"),new JSONObject().put("lesson",lesson.json()).put("run","batch-fixture").put("updated",lesson.startsAt+1000).put("complete",!draft).put("overview","课堂重点")
                .put("parts",new JSONArray().put(new JSONObject().put("notes",new JSONArray().put(new JSONObject().put("title","例题核对").put("text","正文中的实际第17张图片。\n![例图](images/image-17.jpg)").put("sources",new JSONArray().put(0)).put("images",new JSONArray().put("images/image-17.jpg")))).put("assignments",new JSONArray())))
                .put("images",new JSONArray().put(new JSONObject().put("file","image-17.jpg").put("second",17))).put("raw_images",20).put("candidate_images",19).put("planned_images",1));
    }
    private void errorRecovery(Context app,ClassroomLesson lesson,File dir)throws Exception {
        for(String mode:new String[]{"failed","cancelled","succeeded","other-run"}){
            String error="图片引用无效：image-999.jpg";JSONObject run=new JSONObject().put("run",mode.equals("other-run")?"different-run":"batch-fixture")
                    .put("final_validation",new JSONObject().put("status",mode.equals("other-run")?"failed":mode).put("error",error));ClassroomStore.write(new File(dir,"run.json"),run);
            Activity detail=startActivitySync(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->!((boolean)field(field(detail,"s"),"busy"))&&((boolean)field(field(detail,"s"),"localInitialized")),"local failure recovery "+mode);
            boolean expected=mode.equals("failed")||mode.equals("cancelled");String message=(String)field(field(detail,"s"),"message");require(expected?message.equals(error):message.isEmpty(),"reopen error contract "+mode);
            if(expected){await(()->error.contentEquals(((TextView)field(detail,"status")).getText()),"stored failure rendered in existing status");capture("reopen-"+mode);}
            runOnMainSync(detail::finish);waitForIdleSync();
        }
        new File(dir,"run.json").delete();
    }
    private void tapDelete()throws Exception {View button=(View)field(page(),"batchDelete");if(button.isShown())tap(button);else{tap((View)field(page(),"batchMenu"));tapText("删除本地资料");}tapText("删除");}
    private SchedulePage page(){return lastResumed instanceof ScheduleWorkspaceActivity workspace?workspace.currentPage():null;}
    private Object session(){return field(page(),"s");}
    private boolean busy(){return (boolean)field(session(),"busy")||(boolean)field(session(),"localWork");}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Object invoke(Object object,String name)throws Exception {Method method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);}
    private void require(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private void await(BooleanSupplier condition,String label){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){waitForIdleSync();try{if(condition.getAsBoolean())return;}catch(Exception ignored){}SystemClock.sleep(80);}throw new AssertionError(label);}
    private void tap(View view){nativeGesture(view,80,null);}
    private void longPress(View view,BooleanSupplier selected){nativeGesture(view,0,selected);}
    private void nativeGesture(View target,long duration,BooleanSupplier heldSignal){
        boolean hold=heldSignal!=null;
        if(target==null)throw new AssertionError("Missing native touch target");Activity owner=lastResumed;String name=String.format(java.util.Locale.ROOT,"gesture-%03d-%s",++gestureNumber,hold?"hold":"tap");JSONObject trace=new JSONObject();JSONArray events=new JSONArray();float[] point={-1,-1};Window.Callback original=owner.getWindow().getCallback();
        runOnMainSync(()->target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),true));waitForIdleSync();scrollAboveBottomDock(target,owner,trace);
        runOnMainSync(()->{try{
            View decor=owner.getWindow().getDecorView();int[] xy=new int[2];target.getLocationOnScreen(xy);float oldX=xy[0]+target.getWidth()/2f,oldY=xy[1]+target.getHeight()/2f;Rect visible=new Rect();boolean shown=target.getGlobalVisibleRect(visible);Rect window=new Rect();decor.getWindowVisibleDisplayFrame(window);shown&=visible.intersect(window);
            trace.put("beforeActivity",activityFacts()).put("target",viewFacts(target)).put("requestedPath",viewPath(target)).put("window",window.toShortString()).put("visibleIntersection",visible.toShortString()).put("legacyCenter",new JSONArray().put(oldX).put(oldY)).put("legacyGeometricResponder",viewPath(touchableAt(decor,oldX,oldY))).put("hitTestMethod","public visible bounds, descending Z and child index; Dock consumes its full padded rectangle; real dispatch recorded below").put("configuredLongPressMillis",ViewConfiguration.getLongPressTimeout());
            if(shown)outer:for(float fy:new float[]{.5f,.25f,.75f,.1f,.9f})for(float fx:new float[]{.5f,.25f,.75f}){float x=visible.left+visible.width()*fx,y=visible.top+visible.height()*fy;View responder=touchableAt(decor,x,y);if(relatedViews(target,responder)){point[0]=x;point[1]=y;trace.put("actualPoint",new JSONArray().put(x).put(y)).put("plannedGeometricResponder",viewPath(responder));break outer;}}
            trace.put("events",events);
        }catch(Exception failure){throw new IllegalStateException(failure);}});
        try{
            writeEvidence(name,trace);if(hold)capture(name+"-before");
            if(point[0]<0)throw new AssertionError("No visible, unoccluded native hit point for "+viewPath(target)+"; see "+name);
            Window.Callback observer=(Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,method,arguments)->{
                Object result;try{result=method.invoke(original,arguments);}catch(InvocationTargetException failure){throw failure.getCause();}
                if(method.getName().equals("dispatchTouchEvent")&&arguments!=null&&arguments[0] instanceof MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){events.put(touchEventFacts(action,event.getRawX(),event.getRawY(),target,owner));if(action==MotionEvent.ACTION_DOWN&&hold){Handler main=new Handler(Looper.getMainLooper());main.postDelayed(()->events.put(touchEventFacts(-100,point[0],point[1],target,owner)),ViewConfiguration.getTapTimeout()+20L);main.postDelayed(()->events.put(touchEventFacts(-200,point[0],point[1],target,owner)),ViewConfiguration.getLongPressTimeout()+20L);}}}return result;
            });
            runOnMainSync(()->owner.getWindow().setCallback(observer));if(hold)holdUntilSelection(point[0],point[1],heldSignal,owner,trace);else touch(point[0],point[1],duration);
        }catch(Exception failure){throw new IllegalStateException("Native gesture failed; see "+name,failure);}
        finally{
            runOnMainSync(()->{owner.getWindow().setCallback(original);try{trace.put("afterActivity",activityFacts()).put("afterTarget",viewFacts(target));}catch(Exception failure){throw new IllegalStateException(failure);}});
            try{writeEvidence(name,trace);if(hold||owner!=lastResumed)capture(name+"-after");}catch(Exception evidenceFailure){throw new IllegalStateException("Cannot save gesture evidence "+name,evidenceFailure);}
        }
        if(hold&&owner!=lastResumed)throw new AssertionError("Long press changed Activity from "+owner.getClass().getSimpleName()+" to "+lastResumed.getClass().getSimpleName()+"; see "+name+" real dispatch trace");
    }
    private void holdUntilSelection(float x,float y,BooleanSupplier selected,Activity owner,JSONObject trace)throws Exception {
        long down=SystemClock.uptimeMillis(),deadline=down+5000;MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);sendPointerSync(press);press.recycle();boolean[] observed={false};
        try{
            while(SystemClock.uptimeMillis()<deadline&&lastResumed==owner){runOnMainSync(()->observed[0]=selected.getAsBoolean());if(observed[0])break;SystemClock.sleep(16);}
            trace.put("heldUntilSelection",observed[0]).put("heldMillis",SystemClock.uptimeMillis()-down);
        }finally{MotionEvent release=MotionEvent.obtain(down,SystemClock.uptimeMillis(),observed[0]?MotionEvent.ACTION_UP:MotionEvent.ACTION_CANCEL,x,y,0);sendPointerSync(release);release.recycle();waitForIdleSync();}
        if(!observed[0])throw new AssertionError("Actual long-press selection did not arrive within 5 seconds; cancelled without converting the failed hold into a click");
    }
    private void scrollAboveBottomDock(View target,Activity owner,JSONObject trace){
        JSONArray changes=new JSONArray();runOnMainSync(()->{try{
            if(!target.isAttachedToWindow())return;
            ScrollView viewport=null;for(var parent=target.getParent();parent instanceof View view;parent=view.getParent())if(view instanceof ScrollView scroll){viewport=scroll;break;}
            if(viewport==null)return;Object navigation=null;if(owner instanceof DashboardActivity)navigation=field(owner,"navigation");else if(owner instanceof ScheduleWorkspaceActivity){Field field=ScheduleWorkspaceActivity.class.getDeclaredField("navigation");field.setAccessible(true);navigation=field.get(owner);}
            if(!(navigation instanceof DockNavigationLayout dock)||(int)field(dock,"orientation")!=LinearLayout.HORIZONTAL||!dock.isShown()||relatedViews(target,dock))return;
            Rect dockBounds=new Rect(),viewportBounds=new Rect();if(!dock.getGlobalVisibleRect(dockBounds)||!viewport.getGlobalVisibleRect(viewportBounds)||dockBounds.centerY()<viewportBounds.centerY())return;
            int safeBottom=Math.min(viewportBounds.bottom,dockBounds.top-Math.round(8*target.getResources().getDisplayMetrics().density));int[] location=new int[2];target.getLocationOnScreen(location);
            int required=location[1]+target.getHeight()-safeBottom;if(required>0){int before=viewport.getScrollY();viewport.scrollBy(0,required);changes.put(new JSONObject().put("dockBounds",dockBounds.toShortString()).put("safeBottom",safeBottom).put("requestedScroll",required).put("scrollBefore",before).put("scrollAfter",viewport.getScrollY()));}
            trace.put("bottomDockClearance",changes);
        }catch(Exception failure){throw new IllegalStateException(failure);}});waitForIdleSync();
    }
    private JSONObject touchEventFacts(int action,float x,float y,View target,Activity owner){try{JSONArray pressed=new JSONArray();pressedViews(owner.getWindow().getDecorView(),pressed);return new JSONObject().put("action",action).put("at",SystemClock.uptimeMillis()).put("rawPoint",new JSONArray().put(x).put(y)).put("targetPressed",target.isPressed()).put("target",viewFacts(target)).put("activity",activityFacts()).put("pressedBranches",pressed);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private void pressedViews(View view,JSONArray pressed){if(view.isPressed())pressed.put(viewPath(view));if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)pressedViews(group.getChildAt(i),pressed);}
    private JSONObject activityFacts()throws Exception {
        Activity screen=lastResumed;JSONObject facts=new JSONObject().put("class",screen==null?"none":screen.getClass().getSimpleName());if(screen==null)return facts;facts.put("identity",System.identityHashCode(screen)).put("focus",screen.hasWindowFocus()).put("finishing",screen.isFinishing());
        if(screen instanceof ScheduleWorkspaceActivity workspace){SchedulePage page=workspace.currentPage();facts.put("workspacePage",workspace.selectedPage()).put("pageClass",page==null?"none":page.getClass().getSimpleName());if(page instanceof ClassroomSummaryPage){Object session=field(page,"s");facts.put("mode",field(session,"mode")).put("selected",new JSONArray((Collection<?>)field(session,"selected")));}}
        else if(screen instanceof DashboardActivity)facts.put("section",field(screen,"section")).put("selectedSchedule",new JSONArray((Collection<?>)field(screen,"selectedScheduleIds"))).put("selectedInbox",new JSONArray((Collection<?>)field(screen,"selectedInboxIds")));
        else if(screen instanceof ClassroomLessonActivity){Object session=field(screen,"s");if(session!=null){ClassroomLesson lesson=(ClassroomLesson)field(session,"selected");facts.put("lesson",lesson==null?"none":lesson.courseId+"/"+lesson.id);}}return facts;
    }
    private static JSONObject viewFacts(View view)throws Exception {
        if(view==null)return new JSONObject().put("view","none");int[] xy=new int[2];view.getLocationOnScreen(xy);Rect visible=new Rect();boolean shown=view.getGlobalVisibleRect(visible);Object background=view.getBackground();return new JSONObject().put("view",view.getClass().getSimpleName()).put("identity",System.identityHashCode(view)).put("bounds",new JSONArray().put(xy[0]).put(xy[1]).put(view.getWidth()).put(view.getHeight())).put("visible",shown).put("visibleBounds",visible.toShortString()).put("layoutTop",view.getTop()).put("height",view.getHeight()).put("translationY",view.getTranslationY()).put("scaleX",view.getScaleX()).put("scaleY",view.getScaleY()).put("z",view.getZ()).put("clickable",view.isClickable()).put("longClickable",view.isLongClickable()).put("attached",view.isAttachedToWindow()).put("background",background==null?0:System.identityHashCode(background));
    }
    private static String viewPath(View view){if(view==null)return "none";List<String> path=new ArrayList<>();for(View item=view;item!=null;item=item.getParent() instanceof View parent?parent:null){String part=item.getClass().getSimpleName();if(item instanceof TextView text)part+="["+text.getText().toString().replace('\n',' ').substring(0,Math.min(70,text.length()))+"]";path.add(part);}Collections.reverse(path);return String.join("/",path);}
    private static boolean relatedViews(View target,View responder){if(responder==null)return false;for(View view=responder;view!=null;view=view.getParent() instanceof View parent?parent:null)if(view==target)return true;for(View view=target;view!=null;view=view.getParent() instanceof View parent?parent:null)if(view==responder)return true;return false;}
    private static View touchableAt(View view,float x,float y){
        if(!view.isShown()||view.getAlpha()<.01f)return null;Rect visible=new Rect();if(!view.getGlobalVisibleRect(visible)||!visible.contains(Math.round(x),Math.round(y)))return null;
        if(view instanceof ViewGroup group){List<Integer> children=new ArrayList<>();for(int i=0;i<group.getChildCount();i++)children.add(i);children.sort((a,b)->{int z=Float.compare(group.getChildAt(b).getZ(),group.getChildAt(a).getZ());return z==0?Integer.compare(b,a):z;});for(int index:children){View hit=touchableAt(group.getChildAt(index),x,y);if(hit!=null)return hit;}}
        return view.isClickable()||view.isLongClickable()||view instanceof DockNavigationLayout||view instanceof ScrollView||view instanceof TextView text&&text.isTextSelectable()?view:null;
    }
    private void writeEvidence(String name,JSONObject data)throws Exception {File target=new File(getTargetContext().getExternalFilesDir(null),"batch-"+name+"-"+args.getString("theme","light")+"-font"+args.getString("font","1")+".json");try(OutputStream out=new FileOutputStream(target)){out.write(data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
    private void touch(float x,float y,long duration){long down=SystemClock.uptimeMillis();MotionEvent press=MotionEvent.obtain(down,down,0,x,y,0);sendPointerSync(press);press.recycle();SystemClock.sleep(duration);MotionEvent up=MotionEvent.obtain(down,SystemClock.uptimeMillis(),1,x,y,0);sendPointerSync(up);up.recycle();waitForIdleSync();SystemClock.sleep(250);}
    private void tapText(String text){tapText(text,null);}
    private void tapText(String text,String captureBefore){
        boolean[] requestedScroll={false};await(()->{
            var root=getUiAutomation().getRootInActiveWindow();if(root==null)return false;
            for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText())){
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);Rect visible=new Rect(bounds);
                for(var parent=node.getParent();parent!=null;parent=parent.getParent()){Rect clip=new Rect();parent.getBoundsInScreen(clip);if(!visible.intersect(clip))visible.setEmpty();}
                if(!requestedScroll[0]&&(!node.isVisibleToUser()||!visible.equals(bounds))){requestedScroll[0]=true;node.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());return false;}
                if(!node.isVisibleToUser()||visible.isEmpty())return false;
                try{JSONObject trace=new JSONObject().put("text",text).put("nodeBounds",bounds.toShortString()).put("visibleBounds",visible.toShortString()).put("actualPoint",new JSONArray().put(visible.exactCenterX()).put(visible.exactCenterY())).put("beforeActivity",activityFacts());String name=String.format(java.util.Locale.ROOT,"gesture-%03d-menu",++gestureNumber);writeEvidence(name,trace);if(captureBefore!=null)capture(captureBefore);touch(visible.exactCenterX(),visible.exactCenterY(),80);trace.put("afterActivity",activityFacts());writeEvidence(name,trace);}catch(Exception failure){throw new IllegalStateException(failure);}return true;
            }return false;
        },"dialog option "+text);
    }
    private void capture(String name)throws Exception {waitForIdleSync();SystemClock.sleep(350);Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("No native render");float scale=Math.min(1f,1200f/Math.max(image.getWidth(),image.getHeight()));Bitmap small=Bitmap.createScaledBitmap(image,Math.round(image.getWidth()*scale),Math.round(image.getHeight()*scale),true);File target=new File(getTargetContext().getExternalFilesDir(null),"batch-"+name+"-"+args.getString("theme","light")+"-font"+args.getString("font","1")+".jpg");try(OutputStream out=new FileOutputStream(target)){small.compress(Bitmap.CompressFormat.JPEG,75,out);}if(small!=image)small.recycle();image.recycle();}
}
