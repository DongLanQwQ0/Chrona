package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** First-frame selection and real slow-loader layout checks on the disposable emulator. */
final class WorkspaceInitialStateDeviceCheck {
    private static int assertions;
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context=test.getTargetContext();FixtureIsolation.require(context);assertions=0;
        ZjuSettingsStore school=new ZjuSettingsStore(context);Map<String,?> before=school.prefs.getAll();
        ZjuTimetableStore timetable=new ZjuTimetableStore(context);JSONObject oldTimetable=timetable.snapshot();
        String account="00000000-0000-0000-0000-000000000036";
        ClassroomStore classroom=new ClassroomStore(context,account);List<ClassroomLesson> oldCatalog=classroom.catalog();
        Activity[] opened={null};CountDownLatch[] blocked={null};
        try {
            school.prefs.edit().putString("account",account).putString("credentials","synthetic-not-a-login")
                    .putBoolean("enabled",true).putBoolean("timetable",true).commit();
            ThemeStore.setMode(context,args.getString("theme","light"));
            LocalDate monday=LocalDate.now(ZjuTimetableViewZone()).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            List<ZjuSnapshot.Lesson> rows=new ArrayList<>();
            for(int i=0;i<3;i++){long at=monday.plusWeeks(i).atTime(8,0).atZone(ZjuTimetableViewZone()).toInstant().toEpochMilli();
                rows.add(new ZjuSnapshot.Lesson("initial-"+i,"高等数学","东1-201","2026-2027 秋",at,at+90*60000,"合成验收"));}
            timetable.save(account,rows,List.of("2026-2027 秋"));
            for(int attempt=0;attempt<2;attempt++) {
                opened[0]=openTimetable(test,context,false,0);
                SchedulePage page=((ScheduleWorkspaceActivity)opened[0]).currentPage();
                DockNavigationLayout dock=(DockNavigationLayout)field(page,"sourceDock");
                await(test,()->!dock.isAnimating(),"initial highlight settled");
                require(Math.abs(position(dock))<.001f,"school highlight remains at its initial source");
                if(attempt==0) {
                    capture(test,context,"timetable-initial-"+args.getString("theme","light"));
                    tap(test,dock.itemAt(1));
                    await(test,()->position(dock)>.05f&&position(dock)<.999f,"real source touch still animates");
                    await(test,()->!dock.isAnimating()&&Math.abs(position(dock)-1)<.001f,"import source settles");
                    require(field(page,"sourceDock")==dock,"source switch retains selector instance");
                    tap(test,dock.itemAt(0));await(test,()->!dock.isAnimating()&&position(dock)<.001f,"school source settles");
                }
                Activity close=opened[0];test.runOnMainSync(close::finish);opened[0]=null;
            }
            long specialAt=monday.atTime(14,0).atZone(ZjuTimetableViewZone()).toInstant().toEpochMilli();
            timetable.save(account,List.of(new ZjuSnapshot.Lesson("initial-special","单次调课","东1-201","2026-2027 秋",specialAt,specialAt+60*60000,"合成特殊安排")),List.of("2026-2027 秋"));
            opened[0]=openTimetable(test,context,false,0);
            SchedulePage specialPage=((ScheduleWorkspaceActivity)opened[0]).currentPage();
            await(test,()->field(specialPage,"horizontal")!=null,"special-only timetable loaded");
            require(((View)field(specialPage,"horizontal")).getParent()==null,"special-only timetable has no mounted horizontal grid");
            capture(test,context,"timetable-special-only-"+args.getString("theme","light"));
            Activity specialClose=opened[0];test.runOnMainSync(specialClose::finish);opened[0]=null;
            opened[0]=openTimetable(test,context,true,1);
            Activity close=opened[0];test.runOnMainSync(close::finish);opened[0]=null;
            school.prefs.edit().putBoolean("enabled",false).commit();
            opened[0]=openTimetable(test,context,false,1);
            close=opened[0];test.runOnMainSync(close::finish);opened[0]=null;
            school.prefs.edit().putBoolean("enabled",true).commit();
            classroom.catalog(List.of(new ClassroomLesson("initial-state","1","高等数学","导数与变化率","东1-201","6",
                    monday.atTime(8,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli())));
            Instrumentation.ActivityMonitor classroomMonitor=test.addMonitor(ClassroomSummaryActivity.class.getName(),null,false);
            try {
                test.runOnMainSync(()->context.startActivity(new Intent(context,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                opened[0]=classroomMonitor.waitForActivityWithTimeout(10000);
                if(opened[0]==null)throw new AssertionError("Classroom Activity not opened");
                await(test,()->opened[0].hasWindowFocus(),"classroom window focused");
                SystemClock.sleep(650);
            } finally {test.removeMonitor(classroomMonitor);}
            SchedulePage page=((ScheduleWorkspaceActivity)opened[0]).currentPage();Object session=field(page,"s");
            await(test,()->!(boolean)field(session,"busy"),"cached classroom ready");
            View date=(View)field(page,"weekBar");int baseline=top(test,date);
            Object navigation=field(page,"navigation");ExecutorService worker=(ExecutorService)field(session,"worker");
            for(String kind:List.of("CACHE","REFRESH")) {
                CountDownLatch release=new CountDownLatch(1),entered=new CountDownLatch(1);blocked[0]=release;
                worker.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
                if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("loader barrier");
                test.runOnMainSync(()->load(session,kind));test.runOnMainSync(()->{});
                require((boolean)field(session,"busy"),"slow "+kind+" really remains busy");
                require("".equals(field(session,"message")),"slow "+kind+" has no transient loading text");
                require(((View)field(page,"status")).getVisibility()==View.GONE,"loading row is absent");
                require(top(test,date)==baseline,"slow "+kind+" preserves date top");
                View refresh=(View)field(page,"refresh");require("停止刷新".contentEquals(refresh.getContentDescription()),"busy cancel action remains available");
                capture(test,context,"classroom-loading-"+kind.toLowerCase(Locale.ROOT)+"-"+args.getString("theme","light"));
                tap(test,refresh);await(test,()->(boolean)field(session,"cancelled"),"real cancel touch reaches loader");
                require((boolean)field(session,"cancelled"),"cancel state retained while loader is blocked");
                release.countDown();blocked[0]=null;await(test,()->!(boolean)field(session,"busy"),"loader completes after cancel");
                require(top(test,date)==baseline&&field(page,"navigation")==navigation,"completion preserves header geometry and selector");
            }
            test.runOnMainSync(()->{setField(session,"message","合成读取失败，请重试");invoke(page,"update");});
            require(((TextView)field(page,"status")).getText().toString().contains("读取失败")
                    &&((View)field(page,"status")).getVisibility()==View.VISIBLE,"actionable failure feedback retained");
            return new JSONObject().put("assertions",assertions).put("firstFrame",true).put("dateTop",baseline).toString();
        } finally {
            if(blocked[0]!=null)blocked[0].countDown();
            if(opened[0]!=null){Activity close=opened[0];test.runOnMainSync(close::finish);}
            classroom.catalog(oldCatalog);timetable.restore(oldTimetable);
            var edit=school.prefs.edit();for(String key:List.of("account","credentials","enabled","timetable")){
                Object value=before.get(key);if(value instanceof String text)edit.putString(key,text);
                else if(value instanceof Boolean flag)edit.putBoolean(key,flag);else edit.remove(key);
            }edit.commit();
        }
    }
    private static ZoneId ZjuTimetableViewZone(){return ZoneId.of("Asia/Shanghai");}
    private static Activity openTimetable(Instrumentation test,Context context,boolean imported,int expected) {
        float[] created={Float.NaN},firstDraw={Float.NaN};Activity[] launched={null};
        Application app=(Application)context.getApplicationContext();
        Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity activity,Bundle state){}
            @Override public void onActivityPostCreated(Activity activity,Bundle state){
                if(!(activity instanceof TimetableActivity))return;
                launched[0]=activity;
                DockNavigationLayout dock=(DockNavigationLayout)field(((ScheduleWorkspaceActivity)activity).currentPage(),"sourceDock");
                created[0]=position(dock);
                android.util.Log.i("ChronaFirstFrame","created="+created[0]);
                dock.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                    public boolean onPreDraw(){firstDraw[0]=position(dock);dock.getViewTreeObserver().removeOnPreDrawListener(this);return true;}
                });
            }
            public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };
        app.registerActivityLifecycleCallbacks(observer);Activity activity=null;
        try {
            Intent intent=new Intent(context,TimetableActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(imported)intent.putExtra(CourseAgenda.EXTRA_TERM,"synthetic-import");
            android.util.Log.i("ChronaFirstFrame","launch expected="+expected);
            test.runOnMainSync(()->context.startActivity(intent));
            await(test,()->launched[0]!=null,"Activity created");activity=launched[0];
            android.util.Log.i("ChronaFirstFrame","launch returned="+created[0]);
            require(Math.abs(created[0]-expected)<.001f,"resolved source before first layout: expected "+expected+", got "+created[0]);
            await(test,()->!Float.isNaN(firstDraw[0]),"first draw observed");
            require(Math.abs(firstDraw[0]-expected)<.001f,"first drawn highlight uses resolved source");
            Activity focused=activity;await(test,focused::hasWindowFocus,"timetable window focused");
            return activity;
        }catch(RuntimeException|Error failure){if(activity!=null){Activity close=activity;test.runOnMainSync(close::finish);}throw failure;}
        finally{app.unregisterActivityLifecycleCallbacks(observer);}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void load(Object session,String name){
        try {Class kind=Class.forName("com.donglan.chrona.ClassroomSummaryPage$Load");Method m=session.getClass().getDeclaredMethod("load",kind);m.setAccessible(true);m.invoke(session,Enum.valueOf(kind,name));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void invoke(Object target,String name){try{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void setField(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static float position(DockNavigationLayout dock){return ((Number)field(field(dock,"spring"),"position")).floatValue();}
    private static int top(Instrumentation test,View view){int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));return xy[1];}
    private static void await(Instrumentation test,BooleanSupplier ready,String label){long end=SystemClock.uptimeMillis()+10000;do{test.runOnMainSync(()->{});if(ready.getAsBoolean())return;SystemClock.sleep(20);}while(SystemClock.uptimeMillis()<end);throw new AssertionError(label);}
    private static void tap(Instrumentation test,View view){await(test,()->view.hasWindowFocus()&&view.isShown()&&view.getWidth()>0,"touch target focused");int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));long t=SystemClock.uptimeMillis();float x=xy[0]+view.getWidth()/2f,y=xy[1]+view.getHeight()/2f;MotionEvent down=MotionEvent.obtain(t,t,0,x,y,0),up=MotionEvent.obtain(t,t+60,1,x,y,0);test.sendPointerSync(down);test.sendPointerSync(up);down.recycle();up.recycle();}
    private static void capture(Instrumentation test,Context context,String name)throws Exception{test.runOnMainSync(()->{});SystemClock.sleep(650);Bitmap image=test.getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private static void require(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
}
