package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.ClassroomAudit;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Account-bound, local-only view of saved classroom processing metadata. */
public final class ClassroomAuditActivity extends Activity {
    private String account;private ClassroomLesson lesson;private ZjuSettingsStore settings;
    private LinearLayout body;private ScrollView viewport;private ImageButton refresh;
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private int generation,restoreY;private boolean firstPresentation;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(p,k)->allowed();

    static void open(Activity activity,String account,ClassroomLesson lesson){
        try{activity.startActivity(new Intent(activity,ClassroomAuditActivity.class)
                .putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,account).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()));}
        catch(Exception invalid){Feedback.show(activity,"无法打开处理记录");}
    }
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);settings=new ZjuSettingsStore(this);
        account=getIntent().getStringExtra(ClassroomLessonActivity.EXTRA_ACCOUNT);
        if(!allowed())return;
        try{lesson=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra(ClassroomLessonActivity.EXTRA_LESSON)));}
        catch(Exception invalid){finish();return;}
        firstPresentation=state==null||state.getBoolean("first_presentation",false);restoreY=state==null?0:state.getInt("scroll_y");
        LinearLayout root=SettingsPageLayout.content(this);
        refresh=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"刷新处理记录",this::load);
        SettingsPageLayout.header(this,root,"处理记录",refresh);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body);
        SettingsPageLayout.show(this,root,false);viewport=(ScrollView)root.getParent();load();
    }
    private boolean allowed(){
        if(account!=null&&ClassroomMonitor.allowed(this,account))return true;
        if(body!=null)body.removeAllViews();finish();return false;
    }
    private void load(){
        if(!allowed())return;int requested=++generation;refresh.setEnabled(false);
        reader.execute(()->{
            JSONObject snapshot=null;String error=null;
            try{File dir=new ClassroomStore(getApplicationContext(),account).lesson(lesson);
                synchronized(ClassroomStore.LOCK){
                    if(ClassroomStore.active(dir))error="课堂正在处理，请稍后刷新";
                    else snapshot=ClassroomAudit.project(ClassroomStore.read(new File(dir,"run.json")),ClassroomStore.read(new File(dir,"summary.json")));
                }}
            catch(Exception unavailable){error="处理记录无法读取";}
            JSONObject result=snapshot;String failure=error;
            runOnUiThread(()->{
                if(requested!=generation||isFinishing()||isDestroyed()||!allowed())return;
                int scrollY=Math.max(restoreY,viewport.getScrollY());restoreY=0;body.removeAllViews();
                if(failure!=null)text(body,failure,16,false);else render(result);
                refresh.setEnabled(true);viewport.post(()->{if(requested==generation)viewport.scrollTo(0,scrollY);});
                if(firstPresentation)UiMotion.enterPage(body,()->firstPresentation=false);
            });
        });
    }
    private void render(JSONObject snapshot){
        text(body,lesson.course,18,true);text(body,lesson.date()+" "+Instant.ofEpochMilli(lesson.startsAt).atZone(ClassroomLesson.ZONE).format(DateTimeFormatter.ofPattern("HH:mm")),13,false);
        JSONArray decisions=snapshot.optJSONArray("decisions"),turns=snapshot.optJSONArray("turns");
        if(decisions!=null&&decisions.length()>0){
            text(body,"图片判断",16,true);
            text(body,"选中 "+value(snapshot,"selected")+" · 已分析 "+value(snapshot,"analysed"),13,false);
            for(int i=0;i<decisions.length();i++){
                JSONObject decision=decisions.optJSONObject(i);if(decision==null)continue;LinearLayout card=card();
                int block=decision.optInt("block");text(card,(block>0?"第 "+block+" 段 · ":"")+decision(decision.optString("decision")),16,true);
                text(card,decision.optString("reason"),15,false);JSONArray requests=decision.optJSONArray("requests");
                if(requests!=null)for(int n=0;n<requests.length();n++){
                    JSONObject request=requests.optJSONObject(n);if(request!=null)text(card,range(request)+" · "+request.optString("reason"),14,false);
                }
            }
        }
        if(turns==null||turns.length()==0){text(body,"暂无逐轮处理记录",15,false);return;}
        text(body,"请求记录（Token）· "+turns.length()+" 轮",16,true);
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.optJSONObject(i);if(turn==null)continue;LinearLayout card=card();
            text(card,(i+1)+" · "+stage(turn.optString("stage"))+" · "+status(turn.optString("status")),16,true);
            text(card,time(turn,"started")+" — "+time(turn,"finished"),13,false);
            text(card,"输入 "+value(turn,"input")+" · 输出 "+value(turn,"output"),15,false);
            text(card,"缓存读取 "+value(turn,"cached")+" · 总量 "+value(turn,"total"),15,false);
            text(card,"上下文图片 "+value(turn,"context_images"),13,false);
        }
    }
    private LinearLayout card(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        int x=dp(16),y=dp(12);card.setPadding(x,y,x,y);UiStyle.card(card);UiStyle.addSpaced(body,card,0,8);return card;}
    private void text(LinearLayout parent,String value,int size,boolean title){if(value.isBlank())return;
        TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextIsSelectable(true);
        if(title)UiStyle.title(label);else label.setTextColor(UiStyle.colors(this).text);UiStyle.addSpaced(parent,label,0,4);}
    private static String value(JSONObject row,String key){return row.has(key)&&!row.isNull(key)?String.valueOf(row.opt(key)):"未提供";}
    private static String time(JSONObject row,String key){return row.has(key)&&!row.isNull(key)?Instant.ofEpochMilli(row.optLong(key)).atZone(ClassroomLesson.ZONE).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")):"未提供";}
    private static String range(JSONObject row){return seconds(row,"start")+"–"+seconds(row,"end");}
    private static String seconds(JSONObject row,String key){if(row.isNull(key))return "未提供";long seconds=(long)row.optDouble(key);return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
    private static String decision(String value){return switch(value){case "request"->"请求图片";case "not_needed"->"无需图片";case "disabled"->"未授权图片";case "unavailable"->"没有可用截图";default->"判断未提供";};}
    private static String status(String value){return switch(value){case "running"->"未记录结果";case "received"->"已响应";case "failed"->"失败";default->"状态未提供";};}
    private static String stage(String value){
        String label=value.startsWith("correct-")?"更正文稿":value.startsWith("draft-")||value.startsWith("first")?"笔记与图片规划":value.startsWith("final")?"核图与笔记":"处理";
        java.util.regex.Matcher block=java.util.regex.Pattern.compile("^(?:correct|draft|final)-(\\d+)(?:-|$)").matcher(value);
        if(block.find())try{label+=" 第 "+(Integer.parseInt(block.group(1))+1)+" 段";}catch(NumberFormatException ignored){}
        if(value.contains("repair"))label+=" · 结构修复";else if(value.contains("retry"))label+=" · 手动续跑";else if(value.contains("continue"))label+=" · 续写";
        return label;
    }
    @Override protected void onResume(){super.onResume();if(settings!=null){settings.prefs.registerOnSharedPreferenceChangeListener(changes);allowed();}}
    @Override protected void onPause(){super.onPause();if(settings!=null)settings.prefs.unregisterOnSharedPreferenceChangeListener(changes);}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("first_presentation",firstPresentation);state.putInt("scroll_y",viewport==null?0:viewport.getScrollY());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){generation++;reader.shutdownNow();super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
