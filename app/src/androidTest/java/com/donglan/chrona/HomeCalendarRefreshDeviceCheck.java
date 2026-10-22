package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.data.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** Force the real first-home reconciliation/late-course callback ordering, not a random loop. */
final class HomeCalendarRefreshDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context=test.getTargetContext(); FixtureIsolation.require(context);
        var guide=context.getSharedPreferences("usage_guide",0);
        int oldGuide=guide.getInt("version",0);
        Field guideVersion=GuideActivity.class.getDeclaredField("GUIDE_VERSION");guideVersion.setAccessible(true);
        boolean oldReminders=AgendaReminderPreferences.enabled(context);
        String oldTheme=ThemeStore.mode(context);
        guide.edit().putInt("version",guideVersion.getInt(null)).commit();
        AgendaReminderPreferences.setEnabled(context,false);
        ThemeStore.setMode(context,args.getString("theme","light"));
        Activity screen=null;
        try {
            screen=test.startActivitySync(new Intent(context,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity owner=screen;
            long until=SystemClock.uptimeMillis()+12000;
            while(SystemClock.uptimeMillis()<until){
                boolean[] ready={false};test.runOnMainSync(()->{try{ready[0]=(boolean)get(owner,"initialDataReady")&&!(boolean)get(owner,"calendarLoading")&&!(boolean)get(owner,"completionRefreshRunning")&&!(boolean)get(owner,"pageTransitionRunning");}catch(Exception e){throw new RuntimeException(e);}});
                if(ready[0])break;SystemClock.sleep(40);
            }
            DashboardSectionData data=DashboardSectionData.load(context,DashboardActivity.HOME,null,null,false,0,false,0);
            String title="课表回调竞态验收 · 不写入数据库";
            test.runOnMainSync(()->{
                try {
                    if(!(boolean)get(owner,"initialDataReady")||(boolean)get(owner,"calendarLoading"))throw new AssertionError("Home did not finish initial read");
                    Handler handler=(Handler)get(owner,"refresh");handler.removeCallbacks((Runnable)get(owner,"poll"));
                    set(owner,"calendarLoading",true); // Hold periodic reads; only the late callback may restore the row.
                    set(owner,"systemCalendarEvents",Collections.emptyList());
                    set(owner,"calendarResultWindow","");
                    set(owner,"completionRefreshRunning",true);
                    call(owner,"applyCompletionRows",new Class<?>[]{DashboardSectionData.class,LinearLayout.class,ScrollView.class,int.class},data,get(owner,"content"),get(owner,"scroll"),DashboardActivity.HOME);
                    if(hasText((View)get(owner,"content"),title))throw new AssertionError("Late course must not exist in the first render");
                    LocalDate today=LocalDate.now();
                    String key=(String)call(owner,"calendarWindowKey",new Class<?>[]{LocalDate[].class},(Object)new LocalDate[]{today,today.plusMonths(1)});
                    long now=System.currentTimeMillis();
                    set(owner,"systemCalendarEvents",List.of(new CalendarOccurrence(-991100,now+1000,now+3600000,title,false,"隔离教室","测试课表")));
                    set(owner,"calendarResultWindow",key);
                    call(owner,"refreshHomeContent",new Class<?>[]{});
                }catch(Exception e){throw new RuntimeException(e);}
            });
            until=SystemClock.uptimeMillis()+2500;boolean[] visible={false};
            while(SystemClock.uptimeMillis()<until){test.runOnMainSync(()->{try{visible[0]=hasText((View)get(owner,"content"),title)&&!(boolean)get(owner,"completionRefreshRunning");}catch(Exception e){throw new RuntimeException(e);}});if(visible[0])break;SystemClock.sleep(40);}
            if(!visible[0])throw new AssertionError("Late course callback during completion animation was dropped; home requires re-entry or a later unrelated refresh");
            test.runOnMainSync(()->{try{if((boolean)get(owner,"homeRefreshPending")||(boolean)get(owner,"completionRefreshPending"))throw new AssertionError("Deferred flags did not drain");}catch(Exception e){throw new RuntimeException(e);}});
            String recoveredTitle="课表读取失败后下一轮恢复";
            test.runOnMainSync(()->{
                try {
                    long now=System.currentTimeMillis();
                    set(owner,"systemCalendarEvents",List.of(new CalendarOccurrence(-991101,now+1000,now+3600000,recoveredTitle,false,"隔离教室","测试课表")));
                    ((Set<Integer>)get(owner,"dirtySections")).add(DashboardActivity.HOME);
                    set(owner,"snapshot",call(owner,"dataSnapshot",new Class<?>[]{}));
                    set(owner,"completionRefreshRunning",true);set(owner,"completionRefreshPending",true);
                    call(owner,"finishCompletionReadFailure",new Class<?>[]{});
                    if((boolean)get(owner,"completionRefreshRunning")||(boolean)get(owner,"completionRefreshPending"))throw new AssertionError("Failed read keeps global refresh locked");
                    if(!((Set<?>)get(owner,"dirtySections")).contains(DashboardActivity.HOME))throw new AssertionError("Failure must not mark the page refreshed");
                    if(hasText((View)get(owner,"content"),recoveredTitle))throw new AssertionError("Failure must wait for ordinary retry, not spin immediately");
                    call(owner,"pollChanges",new Class<?>[]{});
                }catch(Exception e){throw new RuntimeException(e);}
            });
            until=SystemClock.uptimeMillis()+2500;visible[0]=false;
            while(SystemClock.uptimeMillis()<until){test.runOnMainSync(()->{try{visible[0]=hasText((View)get(owner,"content"),recoveredTitle)&&!(boolean)get(owner,"completionRefreshRunning");}catch(Exception e){throw new RuntimeException(e);}});if(visible[0])break;SystemClock.sleep(40);}
            if(!visible[0])throw new AssertionError("Next poll with the same snapshot must retry a dirty home after read failure");
            boolean[] focused={false};test.runOnMainSync(()->focused[0]=owner.hasWindowFocus());
            if(!focused[0])throw new AssertionError("Native screenshot blocked by another system window");
            Bitmap image=test.getUiAutomation().takeScreenshot();
            if(image==null)throw new AssertionError("No real native screenshot");
            try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"home-calendar-race.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            return "PASS: late course callback during real completion motion drained without re-entry; failed-read callback unlocks both flags, keeps dirty, next same-snapshot poll recovers; no database/calendar writes";
        }finally{
            Activity owner=screen;if(owner!=null)test.runOnMainSync(owner::finish);
            guide.edit().putInt("version",oldGuide).commit();AgendaReminderPreferences.setEnabled(context,oldReminders);ThemeStore.setMode(context,oldTheme);
        }
    }
    private static boolean hasText(View view,String title){if(view instanceof TextView text&&text.getText().toString().contains(title))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),title))return true;return false;}
    private static Object get(Object target,String name)throws Exception{Field f=DashboardActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{Field f=DashboardActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static Object call(Object target,String name,Class<?>[] types,Object... values)throws Exception{Method m=DashboardActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,values);}
}
