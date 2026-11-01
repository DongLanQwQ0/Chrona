package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Existing course affairs, grouped by lesson. Reading never invokes a model or a school client. */
public final class ClassroomAffairsActivity extends Activity {
    private static final String EXTRA_COURSE="classroom_course";
    private String account,course="",shown="";
    private ZjuSettingsStore settings;
    private LinearLayout body;
    private ScrollView viewport;
    private ImageButton refresh;
    private TextView courseField;
    private ClassroomMarkdownView document;
    private LinkedHashMap<String,String> courses=new LinkedHashMap<>();
    private Map<String,ClassroomLesson> sources=Map.of();
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private int generation,restoreY,scrollGesture,readingAnchor=-1,navigationY=-1;
    private boolean firstPresentation,firstResume=true,restorePosition;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(p,k)->allowed();

    static void open(Activity activity,String account,String courseId){
        activity.startActivity(new Intent(activity,ClassroomAffairsActivity.class)
                .putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,account).putExtra(EXTRA_COURSE,courseId));
    }
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);settings=new ZjuSettingsStore(this);
        account=getIntent().getStringExtra(ClassroomLessonActivity.EXTRA_ACCOUNT);
        if(!allowed())return;
        course=state==null?getIntent().getStringExtra(EXTRA_COURSE):state.getString("course","");if(course==null)course="";
        restoreY=state==null?0:state.getInt("scroll_y");restorePosition=state!=null;firstPresentation=state==null||state.getBoolean("first_presentation",false);
        LinearLayout root=SettingsPageLayout.content(this);
        refresh=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"刷新课程事务",this::load);
        SettingsPageLayout.header(this,root,"课程事务",refresh);
        courseField=new TextView(this);UiStyle.fieldTrigger(courseField,true);courseField.setText("选择课程");
        courseField.setOnClickListener(v->chooseCourse());UiStyle.addSpaced(root,courseField,0,8);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body);
        SettingsPageLayout.show(this,root,false);viewport=(ScrollView)root.getParent();
        viewport.setOnTouchListener((view,event)->{if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)readingGesture();return false;});load();
    }
    private boolean allowed(){
        if(account!=null&&ClassroomMonitor.allowed(this,account))return true;
        disposeDocument();if(body!=null)body.removeAllViews();sources=Map.of();finish();return false;
    }
    private void chooseCourse(){
        if(courses.isEmpty())return;
        List<String> ids=new ArrayList<>(courses.keySet());
        UiDialogs.choiceDialog(this,"课程",courses.values().toArray(new String[0]),ids.indexOf(course),index->{
            if(!course.equals(ids.get(index))){course=ids.get(index);restoreY=0;restorePosition=false;viewport.scrollTo(0,0);load();}
        });
    }
    private void load(){
        if(!allowed())return;
        int request=++generation;String selected=course;refresh.setEnabled(false);courseField.setEnabled(false);
        reader.execute(()->{
            JSONArray projected=null;LinkedHashMap<String,String> labels=new LinkedHashMap<>();
            Map<String,File> directories=new LinkedHashMap<>();Map<String,ClassroomLesson> lessons=new LinkedHashMap<>();
            String choice=selected,error=null;
            try{
                ClassroomStore store=new ClassroomStore(getApplicationContext(),account);
                synchronized(ClassroomStore.LOCK){
                    List<JSONObject> history=store.history();labels=ClassroomSummaryPage.historyCourseLabels(history);labels.remove("");
                    if(!labels.containsKey(choice))choice=labels.isEmpty()?"":labels.keySet().iterator().next();
                    for(JSONObject record:history){JSONObject identity=record.optJSONObject("lesson");
                        if(identity!=null&&choice.equals(identity.optString("course_id")))store.existingSummary(ClassroomLesson.fromJson(identity));}
                    projected=ClassroomAffairs.project(history,choice);
                    for(int i=0;i<projected.length();i++){
                        JSONObject row=projected.getJSONObject(i);ClassroomLesson lesson=ClassroomLesson.fromJson(row.getJSONObject("summary").getJSONObject("lesson"));
                        File dir=store.existingSummary(lesson);if(dir==null)throw new java.io.IOException("课堂资料已删除，请刷新");
                        directories.put(row.getString("key"),dir);lessons.put(row.getString("key"),lesson);
                    }
                }
            }catch(Exception unavailable){error=unavailable instanceof java.io.IOException?unavailable.getMessage():"课程事务无法读取";}
            JSONArray result=projected;LinkedHashMap<String,String> options=labels;String chosen=choice,failure=error;
            runOnUiThread(()->{
                if(request!=generation||isFinishing()||isDestroyed()||!allowed())return;
                refresh.setEnabled(true);courses=options;courseField.setEnabled(!options.isEmpty());
                if(failure!=null&&!shown.startsWith(chosen+"\n")){
                    disposeDocument();sources=Map.of();shown="";course=chosen;courseField.setText(courses.getOrDefault(course,"选择课程"));
                }
                if(failure!=null){if(document==null){body.removeAllViews();message(failure);present(body,request,0,scrollGesture);}else Feedback.show(this,failure);return;}
                course=chosen;courseField.setText(courses.getOrDefault(course,"选择课程"));
                String next=course+"\n"+result;int y=restorePosition?restoreY:viewport.getScrollY(),gesture=scrollGesture;restoreY=0;restorePosition=false;
                if(next.equals(shown)){restoreScroll(request,y,gesture);return;}
                shown=next;sources=Map.copyOf(lessons);disposeDocument();body.removeAllViews();
                if(result.length()==0){message("暂无课程事务");present(body,request,y,gesture);return;}
                try{
                    document=new ClassroomMarkdownView(this,result,directories,new ClassroomMarkdownView.Actions(){
                        @Override public void assignment(int index) { }
                        @Override public void image(String name) { }
                        @Override public Uri imageUri(String name){return null;}
                        @Override public void readingGesture(){ClassroomAffairsActivity.this.readingGesture();}
                        @Override public void lesson(String key){ClassroomLesson lesson=sources.get(key);if(allowed()&&lesson!=null){rememberNavigation();startActivity(new Intent(ClassroomAffairsActivity.this,ClassroomLessonActivity.class)
                                .putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,account).putExtra(ClassroomLessonActivity.EXTRA_LESSON,json(lesson)));}}
                        @Override public void image(String key,String name){ClassroomLesson lesson=sources.get(key);if(allowed()&&lesson!=null){rememberNavigation();AttachmentViewerActivity.openClassroomSummary(ClassroomAffairsActivity.this,account,lesson,name);}}
                        @Override public Uri imageUri(String key,String name){ClassroomLesson lesson=sources.get(key);return allowed()&&lesson!=null?ImageTransferProvider.classroomSummary(ClassroomAffairsActivity.this,account,lesson,name):null;}
                    });
                    ClassroomMarkdownView current=document;if(firstPresentation)current.setVisibility(View.INVISIBLE);body.addView(current);
                    current.onReady(()->{if(current==document&&request==generation)present(current,request,y,gesture);});
                }catch(Exception invalid){shown="";disposeDocument();body.removeAllViews();message("课程事务无法显示");}
            });
        });
    }
    private static String json(ClassroomLesson lesson){try{return lesson.json().toString();}catch(Exception invalid){throw new IllegalArgumentException(invalid);}}
    private void present(View content,int request,int y,int gesture){
        restoreScroll(request,y,gesture);
        if(firstPresentation){content.setVisibility(View.VISIBLE);UiMotion.enterPage(content,()->firstPresentation=false);}
    }
    private void restoreScroll(int request,int y,int gesture){viewport.post(()->{if(request==generation&&gesture==scrollGesture&&!isFinishing())viewport.scrollTo(0,y);});}
    private void readingGesture(){scrollGesture++;restorePosition=false;readingAnchor=viewport.getScrollY();}
    private void rememberNavigation(){navigationY=readingAnchor>=0?readingAnchor:viewport.getScrollY();readingAnchor=-1;}
    private void message(String value){TextView text=new TextView(this);text.setText(value);text.setTextSize(16);UiStyle.muted(text);body.addView(text);}
    private void disposeDocument(){if(document!=null){document.dispose();document=null;}}
    @Override protected void onResume(){super.onResume();if(settings!=null){settings.prefs.registerOnSharedPreferenceChangeListener(changes);if(allowed()){if(firstResume)firstResume=false;else load();}}}
    @Override protected void onPause(){if(viewport!=null){restoreY=navigationY>=0?navigationY:viewport.getScrollY();restorePosition=true;navigationY=-1;}if(settings!=null)settings.prefs.unregisterOnSharedPreferenceChangeListener(changes);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("course",course);state.putInt("scroll_y",viewport==null?0:viewport.getScrollY());state.putBoolean("first_presentation",firstPresentation);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){generation++;disposeDocument();reader.shutdownNow();super.onDestroy();}
}
