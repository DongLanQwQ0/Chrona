package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Visits every production Activity in the isolated package; it never confirms business actions. */
final class LandscapePageAuditChecks {
    static int run(Instrumentation test,Bundle args,String account,ClassroomLesson lesson)throws Exception {
        Context app=test.getTargetContext();
        if(!app.getPackageName().equals("com.donglan.chrona.monitor.validation")||!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned fixture required");
        File directory=new File(app.getExternalFilesDir(null),"landscape-page-audit");if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create audit directory");
        long task,candidate,now=System.currentTimeMillis();
        try(TaskStore store=new TaskStore(app)){
            task=store.insertTask("横屏验收日程，时间地点与完整原始消息。\n".repeat(12),null,"height-audit",now);
            candidate=store.appendCandidate(new EventCandidate(0,task,"横屏验收日程",now+3600000,now+7200000,"Asia/Shanghai","验收教室","完整日程描述。\n".repeat(20),10,false));
            store.updateStatus(task,TaskRecord.READY,null);
        }
        try(var writer=new com.donglan.chrona.processing.StreamingOutputStore(app).begin(task)){writer.append("模型输出正文，用于连续阅读及高度验收。\n".repeat(180));}
        JSONObject report=new JSONObject();JSONArray entries=new JSONArray();report.put("window",args.toString()).put("pages",entries);
        int rendered=0;
        var info=app.getPackageManager().getPackageInfo(app.getPackageName(),PackageManager.GET_ACTIVITIES);
        for(var declared:info.activities){
            String name=declared.name,simple=name.substring(name.lastIndexOf('.')+1);JSONObject entry=new JSONObject().put("activity",simple);entries.put(entry);
            if(args.containsKey("pages")&&!java.util.Arrays.asList(args.getString("pages").split(",")).contains(simple)){entry.put("state","outside-focused-recheck");continue;}
            if(name.contains(".debug.")){entry.put("state","debug-only");continue;}
            if(simple.equals("LauncherShortcutActivity")||simple.equals("WidgetLaunchActivity")){entry.put("state","NoDisplay-routing");continue;}
            Activity activity=null;
            try{
                Intent intent=new Intent().setClassName(app,name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("task_id",task).putExtra("candidate_id",candidate);
                if(simple.equals("SystemAgendaDetailActivity"))intent.putExtra("event_id",0L).putExtra("beginTime",now+3600000).putExtra("endTime",now+7200000).putExtra("title","横屏日程详情").putExtra("location","验收教室").putExtra("description","日程信息正文。\n".repeat(30)).putExtra("calendar","验收来源");
                if(simple.equals("ExternalCourseDetailActivity"))intent.putExtra("source","ics").putExtra("source_key","height-audit").putExtra("begin",now).putExtra("end",now+3600000);
                if(simple.equals("ClassroomTranscriptActivity"))intent.putExtra("account",account).putExtra("lesson",lesson.json().toString());
                if(simple.equals("ClassroomLessonActivity"))intent.putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,account).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString());
                if(simple.equals("ClassroomSummaryActivity"))intent.putExtra("classroom_mode",1);
                if(simple.equals("AttachmentViewerActivity"))intent.putExtra("classroom_image_account",account).putExtra("classroom_image_lesson",lesson.json().toString()).putExtra("classroom_image_raw","raw-0.img").putStringArrayListExtra("classroom_image_list",new ArrayList<>(java.util.List.of("raw-0.img","raw-1.img","raw-2.img")));
                activity=test.startActivitySync(intent);SystemClock.sleep(500);test.waitForIdleSync();
                if(activity.isFinishing()||activity.isDestroyed()){entry.put("state",simple.equals("LanPairingActivity")?"requires-active-consent-request":"closed-with-fixture-input");continue;}
                View root=activity.findViewById(android.R.id.content);ScrollView scroll=primaryScroll(activity,root);Rect window=visible(root);var insets=root.getRootWindowInsets();var bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());Rect readable=new Rect(window.left+bars.left,window.top+bars.top,window.right-bars.right,window.bottom-bars.bottom);
                entry.put("state","rendered").put("usable_height_px",window.height()-bars.top-bars.bottom);
                capture(test,new File(directory,simple+"-before.jpg"));
                if(scroll!=null){
                    Rect before=visible(scroll);before.intersect(readable);int y=scroll.getScrollY();entry.put("body_initial_px",before.height()).put("fixed_top_px",Math.max(0,before.top-readable.top)).put("content_px",scroll.getChildCount()==0?0:scroll.getChildAt(0).getHeight());
                    for(int move=0;move<3;move++)swipe(test,scroll,true);
                    Rect after=visible(scroll);after.intersect(readable);entry.put("body_after_up_px",after.height()).put("scroll_before_px",y).put("scroll_after_px",scroll.getScrollY());
                    capture(test,new File(directory,simple+"-after.jpg"));
                }else entry.put("layout",simple.equals("AttachmentViewerActivity")?"full-window-media":"non-ScrollView-content");
                rendered++;
            }catch(Throwable failure){entry.put("state","fixture-failure").put("reason",failure.toString());var active=test.getUiAutomation().getRootInActiveWindow();if(active!=null){entry.put("active_package",String.valueOf(active.getPackageName()));if(app.getPackageName().contentEquals(active.getPackageName()))capture(test,new File(directory,simple+"-failure.jpg"));}}
            finally{if(activity!=null&&!activity.isDestroyed()){Activity closing=activity;test.runOnMainSync(closing::finish);}ClassroomStore.write(new File(directory,"report.json"),report);}
        }
        report.put("rendered",rendered).put("manifest_activities",info.activities.length);ClassroomStore.write(new File(directory,"report.json"),report);
        return rendered;
    }
    private static Rect visible(View view){Rect rect=new Rect();view.getGlobalVisibleRect(rect);return rect;}
    private static Object field(Object value,String name)throws Exception{var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);}
    private static ScrollView primaryScroll(Activity activity,View root)throws Exception{
        if(activity instanceof ScheduleWorkspaceActivity host){Object page=host.currentPage();
            if(page instanceof ClassroomSummaryPage){return (Integer)field(field(page,"s"),"mode")==0?((ClassroomLessonPickerView)field(page,"weekPicker")).verticalViewport():(ScrollView)field(page,"listScroll");}
            if(page instanceof CalendarOverviewPage)return (ScrollView)field(page,"verticalViewport");
            if(page instanceof TimetablePage)return (ScrollView)field(page,"vertical");
        }
        return largestScroll(root);
    }
    private static ScrollView largestScroll(View root){
        ScrollView best=root instanceof ScrollView scroll?scroll:null;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){ScrollView candidate=largestScroll(group.getChildAt(i));if(candidate!=null&&(best==null||visible(candidate).width()*visible(candidate).height()>visible(best).width()*visible(best).height()))best=candidate;}
        return best;
    }
    private static void swipe(Instrumentation test,View view,boolean up){
        Rect r=visible(view);var active=test.getUiAutomation().getRootInActiveWindow();if(active==null||!test.getTargetContext().getPackageName().contentEquals(active.getPackageName()))throw new IllegalStateException("Owned audit window is not active");Rect window=new Rect();active.getBoundsInScreen(window);var bars=view.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());window.set(window.left+bars.left,window.top+bars.top,window.right-bars.right,window.bottom-bars.bottom);r.intersect(window);if(r.height()<32)return;float x=r.centerX(),start=r.top+r.height()*(up?.85f:.2f),end=r.top+r.height()*(up?.2f:.85f);long down=SystemClock.uptimeMillis();
        for(int step=0;step<=13;step++){int action=step==0?MotionEvent.ACTION_DOWN:step==13?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;float y=start+(end-start)*Math.min(step,12)/12;MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(event);event.recycle();SystemClock.sleep(step==12?100:20);}
        SystemClock.sleep(100);
    }
    private static void capture(Instrumentation test,File target)throws Exception{
        Bitmap original=test.getUiAutomation().takeScreenshot();Bitmap small=Bitmap.createScaledBitmap(original,Math.min(1280,original.getWidth()),Math.max(1,Math.round(original.getHeight()*Math.min(1f,1280f/original.getWidth()))),true);
        try(FileOutputStream out=new FileOutputStream(target)){small.compress(Bitmap.CompressFormat.JPEG,74,out);}finally{if(small!=original)small.recycle();original.recycle();}
    }
}
