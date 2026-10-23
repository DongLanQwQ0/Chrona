package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/** Disposable emulator only: real page creation, navigation, lifecycle and rendering. */
final class ClassroomVisibilityDeviceCheck {
    private static int assertions;
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context=test.getTargetContext();FixtureIsolation.require(context);assertions=0;
        boolean supplement=Boolean.parseBoolean(args.getString("supplement","false"));
        ZjuSettingsStore school=new ZjuSettingsStore(context);
        java.util.Map<String,?> before=school.prefs.getAll();
        String oldTheme=ThemeStore.mode(context);
        Activity[] opened={null};
        try {
            school.prefs.edit().putString("account","00000000-0000-0000-0000-000000000036")
                    .putString("credentials","synthetic-not-a-login").putBoolean("homework",false).putBoolean("timetable",false).commit();
            ThemeStore.setMode(context,args.getString("theme","light"));
            opened[0]=open(test,context,ClassroomSummaryActivity.class);
            ScheduleWorkspaceActivity workspace=(ScheduleWorkspaceActivity)opened[0];
            require(workspace.selectedPage()==0,"disabled direct classroom route returns timetable");
            checkTabs(workspace,2);require(pages(workspace)[1]==null,"hidden classroom is never created");
            capture(test,context,"off-timetable",args);
            DockNavigationLayout navigation=(DockNavigationLayout)field(workspace,"navigation");
            if(supplement) {
                CompactUiDeviceCheck.dockMotion(test,navigation,"classroom-two-tabs-"+args.getString("theme","light"),false);
                test.runOnMainSync(()->workspace.selectPage(0));
                await(test,()->workspace.selectedPage()==0&&!navigation.isAnimating(),"dock stress settles on timetable");
                if(Boolean.parseBoolean(args.getString("motionOnly","false")))return "PASS: two-tab first/last hold, drag-out release and bidirectional fling; every sampled highlight stays inside screen";
            }
            tap(test,navigation.itemAt(1));
            await(test,()->workspace.selectedPage()==2,"second visible tab opens calendar, not classroom");
            require(pages(workspace)[1]==null,"navigation never preloads hidden classroom");
            capture(test,context,"off-calendar",args);
            // Title-area swipe crosses one visible page; the calendar grid keeps its own gestures.
            swipe(test,(View)field(workspace,"pager"));
            await(test,()->workspace.selectedPage()==0,"title swipe skips hidden classroom");
            test.runOnMainSync(()->workspace.selectPage(1));
            require(workspace.selectedPage()==0&&pages(workspace)[1]==null,"programmatic hidden selection ignored");
            ScheduleWorkspaceActivity enabled=changed(test,workspace,()->school.prefs.edit().putBoolean("enabled",true).commit());
            opened[0]=enabled;checkTabs(enabled,3);
            test.runOnMainSync(()->enabled.selectPage(1));
            await(test,()->enabled.selectedPage()==1,"enabled classroom available");
            await(test,()->!((DockNavigationLayout)field(enabled,"navigation")).isAnimating(),"enabled dock settles");
            capture(test,context,"on-classroom",args);
            ScheduleWorkspaceActivity disabled=supplement?settingsToggle(test,enabled,false)
                    :changed(test,enabled,()->school.prefs.edit().putBoolean("enabled",false).commit());
            opened[0]=disabled;checkTabs(disabled,2);
            require(disabled.selectedPage()==0&&pages(disabled)[1]==null,"disable selected classroom returns timetable");
            ScheduleWorkspaceActivity firstDisabled=disabled;
            test.runOnMainSync(()->firstDisabled.selectPage(2));
            await(test,()->firstDisabled.selectedPage()==2,"calendar reachable after disable");
            if(supplement) {
                ScheduleWorkspaceActivity again=settingsToggle(test,disabled,true);opened[0]=again;
                require(again.selectedPage()==2,"real settings enable preserves selected calendar identity");checkTabs(again,3);
                disabled=settingsToggle(test,again,false);opened[0]=disabled;
                require(disabled.selectedPage()==2,"real settings disable preserves selected calendar identity");checkTabs(disabled,2);
            }
            ScheduleWorkspaceActivity recreated=changed(test,disabled,disabled::recreate);opened[0]=recreated;
            require(recreated.selectedPage()==2,"calendar identity survives recreation with only two tabs");
            checkTabs(recreated,2);close(test,opened);
            opened[0]=open(test,context,ZjuSettingsActivity.class);
            View classroom=(View)field(opened[0],"classroom");
            require(classroom.getVisibility()==View.GONE,"settings classroom entry hidden while off");
            TextView description=find(opened[0].getWindow().getDecorView(),"浙江大学专属功能");
            require(description!=null&&description.isShown(),"school-specific description visible");
            require(description.getLayout()!=null&&description.getLayout().getEllipsisCount(0)==0,"description not truncated");
            capture(test,context,"settings-off",args);
            // Toggle preference without synthetic credentials: rendering may change, network remains gated.
            test.runOnMainSync(()->school.prefs.edit().putBoolean("enabled",true).commit());
            Activity settings=opened[0];test.runOnMainSync(()->invokeBind(settings));
            require(classroom.getVisibility()==View.VISIBLE,"settings classroom entry returns when enabled");
            capture(test,context,"settings-on",args);
            return "PASS: "+assertions+" assertions; direct route, real tab tap/title swipe, enable/disable, recreation, settings description";
        } finally {
            close(test,opened);
            var edit=school.prefs.edit().clear();
            for(var entry:before.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof Boolean v)edit.putBoolean(key,v);else if(value instanceof String v)edit.putString(key,v);else if(value instanceof Long v)edit.putLong(key,v);else if(value instanceof Integer v)edit.putInt(key,v);else if(value instanceof Float v)edit.putFloat(key,v);}
            edit.commit();
            ThemeStore.setMode(context,oldTheme);
        }
    }
    private static void invokeBind(Activity a){try{var m=a.getClass().getDeclaredMethod("bind");m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new AssertionError(e);}}
    private static ScheduleWorkspaceActivity settingsToggle(Instrumentation test,ScheduleWorkspaceActivity workspace,boolean enabled)throws Exception{
        var settingsMonitor=test.addMonitor(ZjuSettingsActivity.class.getName(),null,false);
        var workspaceMonitor=test.addMonitor(workspace.getClass().getName(),null,false);Activity settings=null;
        try{
            test.runOnMainSync(()->workspace.startActivity(new Intent(workspace,ZjuSettingsActivity.class)));
            settings=settingsMonitor.waitForActivityWithTimeout(10000);require(settings!=null,"real school settings opens");
            Activity opened=settings;await(test,opened::hasWindowFocus,"settings focused");
            android.widget.Switch toggle=(android.widget.Switch)field(settings,"enabled");
            require(toggle.isChecked()!=enabled,"toggle starts at opposite state");tap(test,toggle);
            await(test,()->new ZjuSettingsStore(opened).enabled()==enabled&&!Boolean.TRUE.equals(field(opened,"busy")),"real setting persisted");
            test.runOnMainSync(opened::finish);settings=null;
            Activity next=workspaceMonitor.waitForActivityWithTimeout(10000);require(next!=null&&next!=workspace,"return rebuilds paused workspace");
            await(test,next::hasWindowFocus,"returned workspace focus");SystemClock.sleep(400);return (ScheduleWorkspaceActivity)next;
        }finally{if(settings!=null){Activity close=settings;test.runOnMainSync(close::finish);}test.removeMonitor(settingsMonitor);test.removeMonitor(workspaceMonitor);}
    }
    private static SchedulePage[] pages(Activity a){return (SchedulePage[])field(a,"pages");}
    private static void checkTabs(Activity a,int expected){
        DockNavigationLayout dock=(DockNavigationLayout)field(a,"navigation");
        require(dock.itemCount()==expected,"visible tab count "+expected);
        require("课表".contentEquals(((TextView)dock.itemAt(0)).getText()),"first tab timetable");
        require("日程".contentEquals(((TextView)dock.itemAt(expected-1)).getText()),"last tab calendar");
    }
    private static Activity open(Instrumentation test,Context context,Class<? extends Activity> type)throws Exception{
        Activity a=test.startActivitySync(new Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(test,a::hasWindowFocus,"window focus");SystemClock.sleep(500);return a;
    }
    private static ScheduleWorkspaceActivity changed(Instrumentation test,ScheduleWorkspaceActivity old,Runnable change)throws Exception{
        Instrumentation.ActivityMonitor monitor=test.addMonitor(old.getClass().getName(),null,false);
        try{test.runOnMainSync(change);Activity next=monitor.waitForActivityWithTimeout(10000);
            require(next!=null&&next!=old,"availability recreates workspace");
            await(test,next::hasWindowFocus,"rebuilt window focus");SystemClock.sleep(450);return (ScheduleWorkspaceActivity)next;
        }finally{test.removeMonitor(monitor);}
    }
    private static void close(Instrumentation test,Activity[] ref){if(ref[0]!=null){Activity a=ref[0];test.runOnMainSync(a::finish);test.waitForIdleSync();ref[0]=null;}}
    private static Object field(Object object,String name){try{Class<?> type=object.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}catch(Exception e){throw new AssertionError(e);}}
    private static TextView find(View view,String text){if(view instanceof TextView v&&text.contentEquals(v.getText()))return v;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView v=find(group.getChildAt(i),text);if(v!=null)return v;}return null;}
    private static void tap(Instrumentation test,View v){int[] p=new int[2];test.runOnMainSync(()->v.getLocationOnScreen(p));long at=SystemClock.uptimeMillis();event(test,at,at,MotionEvent.ACTION_DOWN,p[0]+v.getWidth()/2f,p[1]+v.getHeight()/2f);event(test,at,at+70,MotionEvent.ACTION_UP,p[0]+v.getWidth()/2f,p[1]+v.getHeight()/2f);}
    private static void swipe(Instrumentation test,View v){int[] p=new int[2];test.runOnMainSync(()->v.getLocationOnScreen(p));long at=SystemClock.uptimeMillis();float x=p[0]+v.getWidth()*.25f,y=p[1]+24*v.getResources().getDisplayMetrics().density;event(test,at,at,MotionEvent.ACTION_DOWN,x,y);for(int i=1;i<=12;i++)event(test,at,at+i*20,MotionEvent.ACTION_MOVE,x+v.getWidth()*.5f*i/12,y);event(test,at,at+260,MotionEvent.ACTION_UP,x+v.getWidth()*.5f,y);}
    private static void event(Instrumentation t,long down,long at,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,at,action,x,y,0);try{t.sendPointerSync(e);}finally{e.recycle();}}
    private static void await(Instrumentation test,BooleanSupplier condition,String label)throws Exception{long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){test.waitForIdleSync();if(condition.getAsBoolean())return;SystemClock.sleep(40);}throw new AssertionError(label);}
    private static void capture(Instrumentation test,Context context,String name,Bundle args)throws Exception{test.waitForIdleSync();Bitmap image=test.getUiAutomation().takeScreenshot();File file=new File(context.getExternalFilesDir(null),"classroom-visibility-"+args.getString("theme","light")+"-"+name+".png");try(FileOutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private static void require(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
