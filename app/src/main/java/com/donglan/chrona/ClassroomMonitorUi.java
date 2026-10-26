package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.timetable.Timetable;
import org.json.*;
import java.util.*;
import java.util.concurrent.Executors;

/** All entry points lead to the dedicated monitor screen; no nested scope menus. */
final class ClassroomMonitorUi {
    private static final java.util.concurrent.ExecutorService IO=Executors.newSingleThreadExecutor();
    static boolean watching(Context context,ClassroomLesson lesson){
        try{ZjuSettingsStore s=new ZjuSettingsStore(context);return s.enabled()&&ClassroomMonitorStore.matches(new ClassroomMonitorStore(context,s.account()).read(),lesson);}catch(Exception ignored){return false;}
    }
    static void open(Activity activity,ClassroomLesson lesson){
        String account=new ZjuSettingsStore(activity).account();
        if(!ClassroomMonitor.allowed(activity,account))return;
        try{ClassroomTranscriptActivity.open(activity,account,lesson);}catch(Exception failure){Feedback.show(activity,"无法打开课堂监视");}
    }
    static void fromTimetable(Activity activity,Timetable.Block block){
        String account=new ZjuSettingsStore(activity).account();if(!ClassroomMonitor.allowed(activity,account))return;
        IO.execute(()->{
            List<ClassroomLesson> found;String error="";
            try{
                ClassroomStore store=new ClassroomStore(activity.getApplicationContext(),account);found=matching(store.catalog(),block);
                if(found.isEmpty()&&ClassroomMonitor.allowed(activity,account)){
                    activity.runOnUiThread(()->{if(!activity.isFinishing())Feedback.show(activity,"正在匹配课堂");});
                    String[] pair=new ZjuSettingsStore(activity).credentials();ClassroomClient client;
                    try{client=ClassroomMonitor.clients.apply(pair[0],pair[1]);}finally{Arrays.fill(pair,"");}
                    try(client){List<ClassroomLesson> catalog=client.lessons();if(!ClassroomMonitor.allowed(activity,account))return;store.catalog(catalog);found=matching(catalog,block);}
                }
            }catch(Exception failure){found=new ArrayList<>();error=com.donglan.chrona.net.NetworkFailure.hasDetails(failure)?com.donglan.chrona.net.NetworkFailure.describe(failure):"课堂匹配失败，请稍后重试";}
            List<ClassroomLesson> matches=found;String failure=error;
            matches.sort(java.util.Comparator.comparingLong(l->l.startsAt));
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||activity.isDestroyed()||!ClassroomMonitor.allowed(activity,account))return;
                if(matches.isEmpty()){Feedback.show(activity,failure.isEmpty()?"尚无对应课堂":failure);return;}
                if(matches.size()==1){open(activity,matches.get(0));return;}
                String[] labels=new String[matches.size()];int chosen=0;long now=System.currentTimeMillis();
                for(int i=0;i<matches.size();i++){ClassroomLesson l=matches.get(i);labels[i]=l.date()+" "+clock(l)+" · "+l.title+(l.room.isEmpty()?"":" · "+l.room);if(l.startsAt<=now)chosen=i;}
                UiDialogs.choiceDialog(activity,"选择课堂",labels,chosen,i->open(activity,matches.get(i)));
            });
        });
    }
    private static List<ClassroomLesson> matching(List<ClassroomLesson> catalog,Timetable.Block block){
        List<ClassroomLesson> matches=new ArrayList<>();for(ClassroomLesson lesson:catalog){if(!name(lesson.course).equals(name(block.title)))continue;
            for(Timetable.Occurrence entry:block.occurrences())if(entry.start.atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli()==lesson.startsAt){matches.add(lesson);break;}}
        return matches;
    }
    private static String name(String text){return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).replaceAll("\\s+","");}
    static String clock(ClassroomLesson lesson){return java.time.Instant.ofEpochMilli(lesson.startsAt).atZone(ClassroomLesson.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));}
    static void overview(Activity activity,LinearLayout parent,JSONObject state){
        try{
            JSONArray rules=state.getJSONArray("rules");List<ClassroomLesson> pending=ClassroomMonitorStore.pending(state);
            for(int i=0;i<rules.length();i++){
                JSONObject rule=rules.getJSONObject(i);ClassroomLesson anchor=ClassroomLesson.fromJson(rule.getJSONObject("lesson")),selected=anchor;
                if(rule.getBoolean("repeat")){
                    List<ClassroomLesson> course=new ArrayList<>();for(ClassroomLesson lesson:pending)if(lesson.courseId.equals(anchor.courseId))course.add(lesson);
                    if(!course.isEmpty()){selected=course.get(0);for(ClassroomLesson lesson:course)if(lesson.startsAt<=System.currentTimeMillis())selected=lesson;}
                }
                ClassroomLesson target=selected;LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(activity,16),dp(activity,12),dp(activity,16),dp(activity,12));UiStyle.card(card);
                TextView title=new TextView(activity);title.setText(target.course);title.setTextSize(16);UiStyle.title(title);card.addView(title);
                TextView detail=new TextView(activity);detail.setText(target.date()+" "+clock(target)+" · "+(rule.getBoolean("repeat")?"含后续课堂":"单次"));detail.setTextSize(13);UiStyle.muted(detail);UiStyle.addSpaced(card,detail,4,0);
                JSONObject statuses=state.optJSONObject("statuses");String phase=statuses==null?"等待上课":statuses.optString((rule.getBoolean("repeat")?"course:"+target.courseId:"lesson:"+ClassroomMonitorStore.key(target)),"等待上课");
                if(phase.startsWith(target.course+" · "))phase=phase.substring(target.course.length()+3);
                TextView status=new TextView(activity);status.setText(phase);status.setTextSize(13);UiStyle.muted(status);UiStyle.addSpaced(card,status,4,0);
                card.setContentDescription(target.course+"，"+detail.getText()+"，"+phase+"，打开课堂监视");card.setOnClickListener(v->open(activity,target));UiStyle.pressable(card);UiStyle.addSpaced(parent,card,0,8);
            }
            if(rules.length()==0){TextView empty=new TextView(activity);empty.setText("暂无监视");empty.setTextSize(16);UiStyle.muted(empty);parent.addView(empty);}
        }catch(Exception failure){Feedback.show(activity,"无法读取课堂监视");}
    }
    static void runtimeSettings(Activity activity){
        String account=new ZjuSettingsStore(activity).account();if(!ClassroomMonitor.allowed(activity,account))return;
        ClassroomMonitorStore store=new ClassroomMonitorStore(activity,account);
        android.app.Dialog dialog=new android.app.Dialog(activity);LinearLayout panel=UiDialogs.dialogPanel(activity,"监视运行设置");
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        CheckBox images=new CheckBox(activity);images.setText("自动总结上传图片");images.setTextSize(15);images.setMinHeight(dp(activity,48));UiStyle.toggle(images);
        try{images.setChecked(store.summaryImages());}catch(Exception failure){Feedback.show(activity,"无法读取监视运行设置");return;}
        images.setOnClickListener(view->{try{if(!ClassroomMonitor.allowed(activity,account))throw new IllegalStateException("学校账号已停用");store.summaryImages(images.isChecked());}catch(Exception failure){images.setChecked(!images.isChecked());Feedback.show(activity,"无法保存监视运行设置");}});
        UiStyle.addSpaced(body,images,0,8);
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        android.app.AlarmManager alarms=activity.getSystemService(android.app.AlarmManager.class);
        if(android.os.Build.VERSION.SDK_INT>=31&&alarms!=null&&!alarms.canScheduleExactAlarms()){labels.add("允许准时启动");actions.add(()->activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+activity.getPackageName()))));}
        labels.add("后台运行设置");actions.add(()->activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+activity.getPackageName()))));
        LinearLayout choices=new LinearLayout(activity);choices.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<labels.size();i++){final int index=i;UiDialogs.addMenuItem(choices,labels.get(i),false,()->{dialog.dismiss();actions.get(index).run();});}
        body.addView(choices);UiDialogs.dialogBody(panel,body);UiDialogs.showFloatingDialog(dialog,panel);
    }
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
}
