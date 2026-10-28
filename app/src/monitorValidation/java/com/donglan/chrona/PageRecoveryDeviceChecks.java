package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.zju.ZjuSnapshot;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Only the actual source/history recovery counterexamples; no general page matrix or network. */
final class PageRecoveryDeviceChecks {
    private final ClassroomMonitorInstrumentation test;
    private final Bundle args;
    private final Context app;
    private final String account="page-recovery-fixture-"+Long.toUnsignedString(System.nanoTime());
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    private final JSONArray report=new JSONArray();
    private final Set<Activity> opened=Collections.newSetFromMap(new IdentityHashMap<>());
    private Activity anchor,screen;
    private JSONObject previousTables;
    private ContentValues previousHomeworkState;
    private int checks;
    private PageRecoveryDeviceChecks(ClassroomMonitorInstrumentation test,Bundle args){this.test=test;this.args=args;app=test.getTargetContext();}

    static int run(ClassroomMonitorInstrumentation test,Bundle args)throws Exception{
        PageRecoveryDeviceChecks c=new PageRecoveryDeviceChecks(test,args);c.guard();
        try{c.seed();c.bootstrap();c.source();c.history();c.firstFrame();return c.checks;}
        catch(Throwable failure){try{c.capture("failure");}catch(Exception capture){failure.addSuppressed(capture);}throw failure;}
        finally{
            test.runOnMainSync(()->{for(Activity activity:c.opened)if(!activity.isDestroyed())activity.finish();});
            test.waitForIdleSync();
            try(TaskStore store=new TaskStore(c.app)){
                var db=store.getWritableDatabase();db.delete("zju_homework_history","account=?",new String[]{c.account});
                if(c.previousHomeworkState!=null)db.update("managed_zju_state",c.previousHomeworkState,"id=1",null);
            }
            if(c.saved.containsKey("chrona_zju"))new ZjuTimetableStore(c.app).restore(c.previousTables);
            for(var entry:c.saved.entrySet())restore(c.app.getSharedPreferences(entry.getKey(),0),entry.getValue());
            java.nio.file.Files.write(new File(c.output(),"report.json").toPath(),c.report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
    private void guard()throws Exception{
        if(!"com.donglan.chrona.monitor.validation".equals(app.getPackageName())||!Build.HARDWARE.contains("ranchu"))throw new SecurityException("monitor.validation emulator only");
        String avd=property("ro.boot.qemu.avd_name");if(avd.isEmpty())avd=property("ro.kernel.qemu.avd_name");
        if(!"Chrona_Classroom_36".equals(avd))throw new SecurityException("Shared Chrona_Classroom_36 AVD required");
        ZjuSettingsStore school=new ZjuSettingsStore(app);
        String salt=school.prefs.getString("identity_salt","");
        boolean monitorFixture=!salt.isEmpty()&&!school.account().isEmpty()
                &&school.account().equals(school.prefs.getString(ZjuSettingsStore.identityKey(salt,"fixture"),""));
        boolean knownFake="page-motion".equals(school.account())||school.account().startsWith("page-recovery-fixture-")
                ||"00000000-0000-0000-0000-000000000036".equals(school.account())||monitorFixture;
        if((school.configured()||!school.account().isEmpty())&&!knownFake)throw new SecurityException("Refusing an existing school identity");
        if(school.prefs.getBoolean("homework",false)||school.prefs.getBoolean("timetable",false)||school.homeworkAutoPlan())throw new SecurityException("Refusing active school/AI synchronization");
        WebDavSettingsStore sync=new WebDavSettingsStore(app);
        if(sync.configured()||sync.automatic()||LanAccessService.running()||LanAccessService.starting())throw new SecurityException("Refusing external synchronization or LAN service");
    }
    private String property(String name)throws Exception{
        try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand("getprop "+name))){
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8).trim();
        }
    }
    private void seed()throws Exception{
        previousTables=new ZjuTimetableStore(app).snapshot();
        for(String name:List.of("chrona_zju","appearance","release_updates"))saved.put(name,new HashMap<>(app.getSharedPreferences(name,0).getAll()));
        app.getSharedPreferences("chrona_zju",0).edit().clear().putString("account",account).putBoolean("enabled",true)
                .putBoolean("homework",false).putBoolean("timetable",false).commit();
        app.getSharedPreferences("appearance",0).edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();
        app.getSharedPreferences("release_updates",0).edit().putBoolean("automatic",false).commit();
        long start=LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(8,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        List<ZjuSnapshot.Lesson> courses=new ArrayList<>();
        for(int i=1;i<=120;i++)courses.add(new ZjuSnapshot.Lesson("recovery-"+i,String.format(Locale.ROOT,"恢复来源 %03d",i),"隔离验收教室","恢复验收",start+i*3600000L,start+(i+1)*3600000L,"本机合成课程"));
        new ZjuTimetableStore(app).save(account,courses,List.of("恢复验收"));
        try(TaskStore store=new TaskStore(app)){
            var db=store.getWritableDatabase();
            try(Cursor row=db.query("managed_zju_state",null,"id=1",null,null,null,null)){
                if(!row.moveToFirst())throw new AssertionError("No managed homework state");
                previousHomeworkState=new ContentValues();DatabaseUtils.cursorRowToContentValues(row,previousHomeworkState);
            }
            if(previousHomeworkState.getAsInteger("enabled")!=0)throw new SecurityException("Refusing an active homework binding");
            ContentValues fixture=new ContentValues();fixture.put("account",account);fixture.put("enabled",0);db.update("managed_zju_state",fixture,"id=1",null);
            for(int i=1;i<=120;i++){
                ContentValues item=new ContentValues();item.put("account",account);item.put("title",String.format(Locale.ROOT,"恢复作业 %03d",i));
                item.put("change","验收更新");item.put("at_millis",start+i);item.put("before_json","{\"submitted\":false}");item.put("after_json","{\"submitted\":true}");
                item.put("remote_updated_at","");db.insertOrThrow("zju_homework_history",null,item);
            }
        }
    }
    private void bootstrap()throws Exception{
        screen=test.startActivitySync(new Intent(app,SettingsHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));opened.add(screen);
        await(()->test.lastResumed instanceof SettingsHubActivity&&test.lastResumed.hasWindowFocus());anchor=test.lastResumed;screen=anchor;opened.add(anchor);
        check(anchor.getResources().getConfiguration().orientation==("landscape".equals(args.getString("orientation"))?2:1),"requested orientation is active");
    }
    private void source()throws Exception{
        open(ScheduleSourceContentsActivity.intent(app,"zju","来源分页恢复验收"));
        await(()->integer(screen,"offset")==50&&!bool(screen,"loading")&&!bool(screen,"firstDataPresentation"));
        ScrollView viewport=(ScrollView)get(screen,"viewport");revealMore();tap("加载更多");
        await(()->integer(screen,"offset")==100&&!bool(screen,"loading"));
        LinearLayout rows=(LinearLayout)get(screen,"rows");View target=rows.getChildAt(60);readAt(viewport,target);
        int y=viewport.getScrollY();check(y>0&&rows.getChildCount()==100,"source really loaded and reads the second 50-row page");capture("source-second-page");
        Activity original=screen;tap("恢复来源 061");await(()->test.lastResumed instanceof ExternalCourseDetailActivity);screen=test.lastResumed;opened.add(screen);
        await(()->!bool(screen,"firstDataPresentation"));tap("返回");await(()->test.lastResumed==original);screen=original;
        settleWindow(screen);
        check(get(screen,"rows")==rows&&rows.getChildAt(60)==target&&integer(screen,"offset")==100,"detail return retains actual source row identities and all pages");
        check(Math.abs(viewport.getScrollY()-y)<=2,"detail return retains source reading position");capture("source-returned");
        Activity before=screen;test.runOnMainSync(before::recreate);await(()->test.lastResumed!=before&&test.lastResumed instanceof ScheduleSourceContentsActivity);screen=test.lastResumed;opened.add(screen);
        await(()->integer(screen,"offset")==100&&!bool(screen,"loading")&&integer(screen,"restoreScroll")==-1);
        settleWindow(screen);
        ScrollView restored=(ScrollView)get(screen,"viewport");
        report.put(new JSONObject().put("phase","source-recreated-steady").put("savedScrollY",y).put("actualScrollY",restored.getScrollY()).put("maxScrollY",maxScroll(restored)));
        check(((LinearLayout)get(screen,"rows")).getChildCount()==100,"source recreate restores the complete loaded row count");
        check(Math.abs(restored.getScrollY()-Math.min(y,maxScroll(restored)))<=2,"source recreate restores only reachable reading position");
        check(!bool(screen,"firstDataPresentation")&&test.entranceFrames.getOrDefault(screen,0)==0,"source recreate does not replay first data entrance");capture("source-recreated");close();
    }
    private void history()throws Exception{
        open(new Intent(app,ZjuHomeworkHistoryActivity.class));
        await(()->integer(screen,"loadedCount")==50&&!bool(screen,"loading")&&!bool(screen,"firstDataPresentation"));
        ScrollView viewport=(ScrollView)get(screen,"viewport");revealMore();tap("加载更多");
        await(()->integer(screen,"loadedCount")==100&&!bool(screen,"loading"));
        LinearLayout content=(LinearLayout)get(screen,"content");readAt(viewport,content.getChildAt(70));int y=viewport.getScrollY();
        check(y>0&&content.getChildCount()>=100,"history really appended its original 50-row page");capture("history-second-page");
        Activity before=screen;test.runOnMainSync(before::recreate);await(()->test.lastResumed!=before&&test.lastResumed instanceof ZjuHomeworkHistoryActivity);screen=test.lastResumed;opened.add(screen);
        await(()->integer(screen,"loadedCount")==100&&!bool(screen,"loading")&&integer(screen,"restoreScroll")==-1);
        settleWindow(screen);
        ScrollView restored=(ScrollView)get(screen,"viewport");
        report.put(new JSONObject().put("phase","history-recreated-steady").put("savedScrollY",y).put("actualScrollY",restored.getScrollY()).put("maxScrollY",maxScroll(restored)));
        check(((LinearLayout)get(screen,"content")).getChildCount()>=100,"history recreate restores all appended records");
        check(Math.abs(restored.getScrollY()-Math.min(y,maxScroll(restored)))<=2,"history recreate restores reachable reading position");
        check(!bool(screen,"firstDataPresentation")&&test.entranceFrames.getOrDefault(screen,0)==0,"history recreate does not replay first data entrance");capture("history-recreated");close();
    }
    private void firstFrame()throws Exception{
        FrameLayout[] host={null};View[] data={null};AtomicInteger presented=new AtomicInteger();
        test.runOnMainSync(()->{
            host[0]=new FrameLayout(anchor);data[0]=new View(anchor);host[0].addView(data[0],new FrameLayout.LayoutParams(200,100));
            ((FrameLayout)anchor.findViewById(android.R.id.content)).addView(host[0],new FrameLayout.LayoutParams(-1,-1));
            UiMotion.enterPage(data[0],presented::incrementAndGet);UiMotion.finishSwap(data[0]);
        });
        test.waitForIdleSync();SystemClock.sleep(50);check(presented.get()==0,"native cancel before pre-draw preserves first presentation");
        test.runOnMainSync(()->{UiMotion.enterPage(data[0],presented::incrementAndGet);host[0].removeView(data[0]);});
        test.waitForIdleSync();SystemClock.sleep(50);check(presented.get()==0,"native detach before start preserves first presentation");
        test.runOnMainSync(()->{host[0].addView(data[0],new FrameLayout.LayoutParams(200,100));UiMotion.enterPage(data[0],presented::incrementAndGet);});
        await(()->presented.get()==1);SystemClock.sleep(300);check(presented.get()==1,"native drawable frame confirms exactly once");
        check(data[0].getAlpha()==1f&&data[0].getTranslationY()==0f,"native first presentation reaches the exact final pixels");
        test.runOnMainSync(()->((FrameLayout)host[0].getParent()).removeView(host[0]));
    }
    private void revealMore()throws Exception{
        View more=(View)get(screen,"more");
        await(()->more.isShown()&&more.isLaidOut()&&!more.isLayoutRequested()&&more.getWidth()>0&&more.getHeight()>0);
        test.runOnMainSync(()->more.requestRectangleOnScreen(new Rect(0,0,more.getWidth(),more.getHeight()),true));
        await(()->{
            Rect visible=new Rect();int[] location=new int[2];more.getLocationOnScreen(location);
            Rect whole=new Rect(location[0],location[1],location[0]+more.getWidth(),location[1]+more.getHeight());
            return more.hasWindowFocus()&&more.getGlobalVisibleRect(visible)&&visible.contains(whole);
        });
        long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){
            test.waitForIdleSync();
            if(find(test.getUiAutomation().getRootInActiveWindow(),"加载更多")!=null)return;
            SystemClock.sleep(16);
        }
        throw new AssertionError("Laid-out load-more button never reached the visible accessibility tree");
    }
    private void readAt(ScrollView viewport,View row)throws Exception{
        test.runOnMainSync(()->{Rect rect=new Rect();row.getDrawingRect(rect);viewport.offsetDescendantRectToMyCoords(row,rect);viewport.scrollTo(0,rect.top-viewport.getHeight()/3);});
        test.waitForIdleSync();SystemClock.sleep(100);
    }
    private static int maxScroll(ScrollView view){return Math.max(0,view.getChildAt(0).getHeight()-view.getHeight()+view.getPaddingTop()+view.getPaddingBottom());}
    private void settleWindow(Activity owner)throws Exception{
        View decor=owner.getWindow().getDecorView();
        await(()->test.lastResumed==owner&&!owner.isDestroyed()&&owner.hasWindowFocus()&&decor.isShown());
        AtomicInteger frames=new AtomicInteger();
        ViewTreeObserver.OnPreDrawListener[] observer={null};
        observer[0]=()->{
            if(frames.incrementAndGet()>=2)decor.getViewTreeObserver().removeOnPreDrawListener(observer[0]);
            else decor.postInvalidateOnAnimation();
            return true;
        };
        test.runOnMainSync(()->{decor.getViewTreeObserver().addOnPreDrawListener(observer[0]);decor.invalidate();});
        try{await(()->frames.get()>=2);}
        finally{test.runOnMainSync(()->{if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnPreDrawListener(observer[0]);});}
        SystemClock.sleep(650);
        await(()->test.lastResumed==owner&&!owner.isDestroyed()&&owner.hasWindowFocus()&&decor.isShown());
        test.waitForIdleSync();
    }
    private void open(Intent intent)throws Exception{
        Activity previous=test.lastResumed;test.runOnMainSync(()->anchor.startActivity(intent.setFlags(0)));
        await(()->{
            Activity current=test.lastResumed;
            if(current==null||current==previous||!current.getComponentName().equals(intent.getComponent())
                    ||current.isDestroyed()||current.isFinishing())return false;
            // A requested orientation can replace the first resumed instance before it gains focus.
            screen=current;opened.add(current);
            return current.hasWindowFocus()&&current.getWindow().getDecorView().isShown();
        });
    }
    private void close(){Activity previous=screen;test.runOnMainSync(previous::finish);SystemClock.sleep(350);screen=anchor;}
    private void tap(String text)throws Exception{
        test.waitForIdleSync();AccessibilityNodeInfo node=find(test.getUiAutomation().getRootInActiveWindow(),text);
        if(node==null)throw new AssertionError("Missing visible action: "+text);Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        long down=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(down,down+(action==MotionEvent.ACTION_UP?60:0),action,bounds.centerX(),bounds.centerY(),0);
            try{test.sendPointerSync(event);}finally{event.recycle();}
        }
    }
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text){
        if(node==null)return null;if(node.isVisibleToUser()&&(text.equals(String.valueOf(node.getText()))||text.equals(String.valueOf(node.getContentDescription()))))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),text);if(found!=null)return found;}return null;
    }
    private interface Condition{boolean ready()throws Exception;}
    private void await(Condition condition)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<deadline){boolean[] ready={false};Throwable[] failure={null};
            test.runOnMainSync(()->{try{ready[0]=condition.ready();}catch(Throwable problem){failure[0]=problem;}});
            if(failure[0]!=null)throw new AssertionError("Recovery condition failed",failure[0]);if(ready[0])return;SystemClock.sleep(16);
        }throw new AssertionError("Timed out waiting for native recovery; resumed="+test.lastResumed);
    }
    private void check(boolean valid,String label)throws Exception{if(!valid)throw new AssertionError(label);checks++;report.put(new JSONObject().put("check",label).put("pass",true));}
    private static Object get(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static int integer(Object owner,String name)throws Exception{return (Integer)get(owner,name);}
    private static boolean bool(Object owner,String name)throws Exception{return (Boolean)get(owner,name);}
    private File output(){File dir=new File(app.getExternalFilesDir(null),"page-recovery/"+args.getString("orientation","portrait")+"-"+args.getString("theme","light"));dir.mkdirs();return dir;}
    private void capture(String name)throws Exception{
        Bitmap raw=test.getUiAutomation().takeScreenshot();if(raw==null)throw new AssertionError("No native recovery screenshot");
        float ratio=Math.min(1f,1000f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.max(1,Math.round(raw.getWidth()*ratio)),Math.max(1,Math.round(raw.getHeight()*ratio)),true);
        try(FileOutputStream stream=new FileOutputStream(new File(output(),name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,76,stream);}finally{if(small!=raw)small.recycle();raw.recycle();}
    }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs,Map<String,?> values){
        SharedPreferences.Editor edit=prefs.edit().clear();for(var entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String v)edit.putString(key,v);else if(value instanceof Boolean v)edit.putBoolean(key,v);else if(value instanceof Integer v)edit.putInt(key,v);else if(value instanceof Long v)edit.putLong(key,v);else if(value instanceof Float v)edit.putFloat(key,v);else if(value instanceof Set<?>)edit.putStringSet(key,(Set<String>)value);}edit.commit();
    }
}
