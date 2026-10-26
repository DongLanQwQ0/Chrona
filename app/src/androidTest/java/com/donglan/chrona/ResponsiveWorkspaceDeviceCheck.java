package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;

/** Owned isolated app; real landscape window, no user calendar or settings mutations. */
final class ResponsiveWorkspaceDeviceCheck {
    static String run(Instrumentation test,Bundle args)throws Exception{
        Context context=test.getTargetContext();FixtureIsolation.require(context);Activity page=null;
        String theme=ThemeStore.mode(context);JSONArray results=new JSONArray();
        TimetableStore timetable=new TimetableStore(context);String priorTable=timetable.snapshot();
        java.util.List<Long> fixtures=new java.util.ArrayList<>();
        int rotation=context.getSystemService(android.view.WindowManager.class).getDefaultDisplay().getRotation();
        try{
            ThemeStore.setMode(context,args.getString("theme","light"));
            java.time.LocalDate monday=java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            StringBuilder ics=new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Chrona responsive fixture//EN\r\n");
            for(int i=0;i<3;i++){String date=monday.plusDays(i*2).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                ics.append("BEGIN:VEVENT\r\nUID:responsive-").append(i).append("\r\nSUMMARY:课程").append(i+1).append(" · 线性代数与课堂讨论\r\nLOCATION:北四-111\r\nDTSTART;TZID=Asia/Shanghai:").append(date).append("T090000\r\nDTEND;TZID=Asia/Shanghai:").append(date).append("T103000\r\nRRULE:FREQ=WEEKLY;COUNT=8\r\nEND:VEVENT\r\n");}
            ics.append("END:VCALENDAR\r\n");
            var document=TimetableStore.prepare("横屏验证课表",ics.toString(),java.time.ZoneId.of("Asia/Shanghai"));
            timetable.restore(TimetableStore.encode(new TimetableStore.Library(java.util.List.of(document),"")));
            try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(context)){
                for(int i=0;i<5;i++){long task=store.insertTask("分屏验收任务 "+i,null,"responsive-check",System.currentTimeMillis());fixtures.add(task);
                    long start=System.currentTimeMillis()+(i+1)*3600000L;
                    store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,task,"分屏验收任务 "+i+" · 课程作业整理与提交",start,start+1800000L,"Asia/Shanghai","北四-111","保留完整说明与编辑入口",15,true,null,"task",false));store.updateStatus(task,com.donglan.chrona.data.TaskRecord.NEEDS_REVIEW,null);}
            }
            test.getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_FREEZE_90);SystemClock.sleep(700);
            for(Class<?> type:new Class<?>[]{DashboardActivity.class,CalendarOverviewActivity.class,TimetableActivity.class}){
                Intent launch=new Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if(type==DashboardActivity.class)launch.putExtra(DashboardActivity.EXTRA_SECTION,Integer.parseInt(args.getString("dashboardSection","2")));
                page=test.startActivitySync(launch);test.waitForIdleSync();
                Instrumentation.ActivityMonitor orientation=test.addMonitor(type.getName(),null,false);Activity opening=page;
                test.runOnMainSync(()->opening.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                Activity rotated=test.waitForMonitorWithTimeout(orientation,2500);test.removeMonitor(orientation);if(rotated!=null)page=rotated;
                test.waitForIdleSync();SystemClock.sleep(600);
                if(args.containsKey("windowWidthDp")){Activity bounded=page;test.runOnMainSync(()->bounded.getWindow().setLayout(Math.round(Float.parseFloat(args.getString("windowWidthDp"))*bounded.getResources().getDisplayMetrics().density),Math.round(Float.parseFloat(args.getString("windowHeightDp"))*bounded.getResources().getDisplayMetrics().density)));test.waitForIdleSync();SystemClock.sleep(300);}
                if(!args.containsKey("windowWidthDp")&&page.getResources().getConfiguration().screenWidthDp<=page.getResources().getConfiguration().screenHeightDp)throw new AssertionError("Landscape was not established");
                View decor=page.getWindow().getDecorView();JSONObject row=new JSONObject().put("page",type.getSimpleName()).put("width",decor.getWidth()).put("height",decor.getHeight()).put("widthDp",page.getResources().getConfiguration().screenWidthDp).put("heightDp",page.getResources().getConfiguration().screenHeightDp);
                if(page instanceof ScheduleWorkspaceActivity workspace){
                    java.lang.reflect.Field pages=ScheduleWorkspaceActivity.class.getDeclaredField("pages");pages.setAccessible(true);Object[] loaded=(Object[])pages.get(workspace);
                    for(Object child:loaded)if(child!=null){String field=child instanceof CalendarOverviewPage?"bodyStage":child instanceof TimetablePage?"body":null;
                        if(field!=null){java.lang.reflect.Field body=child.getClass().getDeclaredField(field);body.setAccessible(true);View view=(View)body.get(child);row.put("bodyWidth",view.getWidth()).put("bodyHeight",view.getHeight());if(view.getHeight()<dp(page,80))throw new AssertionError("Body squeezed: "+row);}
                        if(child instanceof TimetablePage){
                            long deadline=SystemClock.uptimeMillis()+6000;while(find(decor,TimetableGrid.class)==null&&SystemClock.uptimeMillis()<deadline){test.waitForIdleSync();SystemClock.sleep(80);}
                            if(find(decor,TimetableGrid.class)==null)throw new AssertionError("Populated timetable absent");
                        }
                    }
                }
                results.put(row);Bitmap image=test.getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Missing landscape render");
                try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),"responsive-"+args.getString("label","candidate")+"-"+type.getSimpleName()+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}image.recycle();
                // Exercise the actual scroll viewport and keep its position over recreation.
                ScrollView scroll=largestScroll(decor);int wanted=0;
                if(scroll!=null&&scroll.getChildCount()>0&&scroll.getChildAt(0).getHeight()>scroll.getHeight()+dp(page,32)){
                    android.graphics.Rect visible=new android.graphics.Rect();scroll.getGlobalVisibleRect(visible);
                    int[] screen=new int[2],window=new int[2];scroll.getLocationOnScreen(screen);scroll.getLocationInWindow(window);visible.offset(screen[0]-window[0],screen[1]-window[1]);
                    // Keep the gesture above the bottom floating navigation's actual hit region.
                    float x=visible.centerX(),from=visible.top+visible.height()*.62f,to=visible.top+visible.height()*.18f;long down=SystemClock.uptimeMillis();
                    for(int frame=0;frame<=12;frame++){int action=frame==0?MotionEvent.ACTION_DOWN:frame==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent motion=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,from+(to-from)*frame/12,0);test.sendPointerSync(motion);motion.recycle();SystemClock.sleep(25);}
                    test.waitForIdleSync();SystemClock.sleep(500);wanted=scroll.getScrollY();if(wanted<=0)throw new AssertionError("Vertical gesture did not scroll "+type.getSimpleName());row.put("gestureScrollY",wanted);
                    int retainY=wanted;ScrollView stableScroll=scroll;test.runOnMainSync(()->stableScroll.scrollTo(0,retainY));
                }
                Activity previous=page;Instrumentation.ActivityMonitor recreate=test.addMonitor(type.getName(),null,false);test.runOnMainSync(previous::recreate);Activity replacement=test.waitForMonitorWithTimeout(recreate,5000);test.removeMonitor(recreate);
                if(replacement==null)throw new AssertionError("Recreation missing");page=replacement;
                if(args.containsKey("windowWidthDp")){Activity bounded=page;test.runOnMainSync(()->bounded.getWindow().setLayout(dp(bounded,Integer.parseInt(args.getString("windowWidthDp"))),dp(bounded,Integer.parseInt(args.getString("windowHeightDp")))));}
                test.waitForIdleSync();SystemClock.sleep(700);
                if(type==TimetableActivity.class){ScrollView restored=largestScroll(page.getWindow().getDecorView());if(restored==null||Math.abs(restored.getScrollY()-wanted)>dp(page,2))throw new AssertionError("Timetable scroll not restored: "+wanted+" -> "+(restored==null?-1:restored.getScrollY()));row.put("recreatedScrollY",restored.getScrollY());}
                Activity closing=page;test.runOnMainSync(closing::finish);page=null;
            }
            return results.toString();
        }finally{
            if(page!=null){Activity closing=page;test.runOnMainSync(closing::finish);}
            test.getUiAutomation().setRotation(rotation);ThemeStore.setMode(context,theme);timetable.restore(priorTable);
            try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(context)){for(long task:fixtures)store.deleteTask(task);}
        }
    }
    private static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    private static View find(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),type);if(result!=null)return result;}return null;}
    private static ScrollView largestScroll(View view){ScrollView result=view instanceof ScrollView scroll&&!SettingsPageLayout.isToolsViewport(view)?scroll:null;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){ScrollView next=largestScroll(group.getChildAt(i));if(next!=null&&(result==null||next.getHeight()>result.getHeight()))result=next;}return result;}
}
