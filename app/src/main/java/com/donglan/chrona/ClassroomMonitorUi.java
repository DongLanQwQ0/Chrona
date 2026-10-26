package com.donglan.chrona;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.net.Uri;
import android.provider.Settings;
import com.donglan.chrona.classroom.ClassroomLesson;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Existing menu rows carry scope and stop actions; no second downloader or monitoring page. */
final class ClassroomMonitorUi {
    static boolean watching(Context context,ClassroomLesson lesson){
        try{ZjuSettingsStore s=new ZjuSettingsStore(context);return s.enabled()&&ClassroomMonitorStore.matches(new ClassroomMonitorStore(context,s.account()).read(),lesson);}catch(Exception ignored){return false;}
    }
    static void choose(Activity activity,ClassroomLesson lesson){
        ZjuSettingsStore settings=new ZjuSettingsStore(activity);
        if(!ClassroomMonitor.allowed(activity,settings.account()))return;
        try {
            String account=settings.account();
            ClassroomMonitorStore store=new ClassroomMonitorStore(activity,settings.account());
            JSONArray rules=store.read().getJSONArray("rules");List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
            labels.add("监视本节课堂");actions.add(()->start(activity,account,lesson,false));
            labels.add("监视该课程后续课堂");actions.add(()->start(activity,account,lesson,true));
            labels.add("查看字幕");actions.add(()->{try{if(ClassroomMonitor.allowed(activity,account))ClassroomTranscriptActivity.open(activity,account,lesson);}catch(Exception failure){Feedback.show(activity,"无法打开字幕");}});
            for(int i=0;i<rules.length();i++){
                JSONObject rule=rules.getJSONObject(i);ClassroomLesson selected=ClassroomLesson.fromJson(rule.getJSONObject("lesson"));boolean repeat=rule.getBoolean("repeat");
                if(repeat?selected.courseId.equals(lesson.courseId):ClassroomMonitorStore.key(selected).equals(ClassroomMonitorStore.key(lesson))){
                    final JSONObject captured=new JSONObject(rule.toString());
                    labels.add(repeat?"停止课程监视":"停止本节监视");actions.add(()->remove(activity,account,captured));
                }
            }
            if(rules.length()>0){labels.add("监视列表");actions.add(()->manage(activity));}
            UiDialogs.choiceDialog(activity,"课堂监视",labels.toArray(new String[0]),-1,index->actions.get(index).run());
        }catch(Exception failure){Feedback.show(activity,"无法读取课堂监视");}
    }
    private static void start(Activity activity,String account,ClassroomLesson lesson,boolean repeat){
        try{if(!ClassroomMonitor.allowed(activity,account))return;ClassroomNotifications.requestPermission(activity);ClassroomMonitor.start(activity,lesson,repeat);ClassroomTranscriptActivity.open(activity,account,lesson);}
        catch(Exception failure){Feedback.show(activity,"无法添加课堂监视");}
    }
    private static void remove(Activity activity,String account,JSONObject captured){
        try {
            if(!ClassroomMonitor.allowed(activity,account))return;
            ClassroomMonitorStore store=new ClassroomMonitorStore(activity,new ZjuSettingsStore(activity).account());
            // Resolve the identity again: background completion may have removed/reordered a rule.
            synchronized(ClassroomMonitorStore.LOCK){JSONArray rules=store.read().getJSONArray("rules");
                for(int i=0;i<rules.length();i++)if(rules.getJSONObject(i).toString().equals(captured.toString())){store.remove(i);break;}}
            ClassroomMonitor.changed(activity);Feedback.show(activity,"已停止监视");
        }catch(Exception failure){Feedback.show(activity,"无法停止课堂监视");}
    }
    static void manage(Activity activity){
        try {
            ClassroomMonitorStore store=new ClassroomMonitorStore(activity,new ZjuSettingsStore(activity).account());
            JSONObject state=store.read();JSONArray rules=state.getJSONArray("rules");
            if(rules.length()==0){Feedback.show(activity,state.optString("message","暂无监视"));return;}
            List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
            for(int i=0;i<rules.length();i++){
                JSONObject captured=new JSONObject(rules.getJSONObject(i).toString());ClassroomLesson lesson=ClassroomLesson.fromJson(captured.getJSONObject("lesson"));
                labels.add(lesson.course+" · "+(captured.getBoolean("repeat")?"后续每次":lesson.date()+" "+java.time.Instant.ofEpochMilli(lesson.startsAt).atZone(ClassroomLesson.ZONE).toLocalTime()));
                actions.add(()->showStatus(activity,lesson,captured));
            }
            AlarmManager alarms=activity.getSystemService(AlarmManager.class);
            if(Build.VERSION.SDK_INT>=31&&alarms!=null&&!alarms.canScheduleExactAlarms()){
                labels.add("允许准时启动");actions.add(()->activity.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+activity.getPackageName()))));
            }
            labels.add("后台运行设置");actions.add(()->activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName()))));
            UiDialogs.choiceDialog(activity,"监视列表",labels.toArray(new String[0]),-1,index->actions.get(index).run());
        }catch(Exception failure){Feedback.show(activity,"无法读取课堂监视");}
    }
    private static void showStatus(Activity activity,ClassroomLesson lesson,JSONObject rule){
        try {
            JSONObject state=new ClassroomMonitorStore(activity,new ZjuSettingsStore(activity).account()).read();
            JSONObject statuses=state.optJSONObject("statuses");String key=rule.getBoolean("repeat")?"course:"+lesson.courseId:"lesson:"+ClassroomMonitorStore.key(lesson);
            String message=statuses==null?"等待上课":statuses.optString(key,"等待上课");
            android.app.Dialog dialog=new android.app.Dialog(activity);android.widget.LinearLayout panel=UiDialogs.dialogPanel(activity,lesson.course);
            android.widget.TextView status=new android.widget.TextView(activity);status.setText(message);status.setTextSize(15);UiStyle.muted(status);
            UiStyle.addSpaced(panel,status,0,12);
            String account=new ZjuSettingsStore(activity).account();
            UiDialogs.addMenuItem(panel,"停止监视",false,()->{dialog.dismiss();remove(activity,account,rule);});
            UiDialogs.showFloatingDialog(dialog,panel);
        }catch(Exception failure){Feedback.show(activity,"无法读取监视状态");}
    }
}
