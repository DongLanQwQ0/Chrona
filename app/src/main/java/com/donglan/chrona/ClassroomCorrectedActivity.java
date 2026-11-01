package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.*;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only published corrected text; uses the same local document renderer as notes. */
public final class ClassroomCorrectedActivity extends Activity {
    private String account;
    private ClassroomLesson lesson;
    private ZjuSettingsStore settings;
    private LinearLayout body;
    private ScrollView viewport;
    private ClassroomMarkdownView document;
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private int generation,restoreY,gesture,readingAnchor=-1,navigationY=-1;
    private boolean firstPresentation,restorePosition,documentReady;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(p,k)->allowed();

    static void open(Activity activity,String account,ClassroomLesson lesson){
        try{activity.startActivity(new Intent(activity,ClassroomCorrectedActivity.class)
                .putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,account)
                .putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()));}
        catch(Exception invalid){Feedback.show(activity,"无法打开更正文稿");}
    }
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);settings=new ZjuSettingsStore(this);
        account=getIntent().getStringExtra(ClassroomLessonActivity.EXTRA_ACCOUNT);
        if(!allowed())return;
        try{lesson=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra(ClassroomLessonActivity.EXTRA_LESSON)));}
        catch(Exception invalid){finish();return;}
        firstPresentation=state==null||state.getBoolean("first_presentation",false);
        restoreY=state==null?0:state.getInt("scroll_y");restorePosition=state!=null;
        LinearLayout root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"更正文稿");
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body);
        SettingsPageLayout.show(this,root,false);viewport=(ScrollView)root.getParent();
        viewport.setOnTouchListener((view,event)->{if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)readingGesture();return false;});
        load();
    }
    private boolean allowed(){
        if(account!=null&&ClassroomMonitor.allowed(this,account))return true;
        if(document!=null){document.dispose();document=null;}
        if(body!=null)body.removeAllViews();finish();return false;
    }
    private void load(){
        int request=++generation;
        reader.execute(()->{
            File source=null;JSONObject snapshot=null;String failure=null;
            try{
                synchronized(ClassroomStore.LOCK){
                    source=new ClassroomStore(getApplicationContext(),account).publishedSummary(lesson);
                    if(source==null)throw new IOException("更正文稿已不存在");
                    snapshot=ClassroomStore.read(new File(source,"summary.json"));
                    if(snapshot==null||!ClassroomDocument.key(snapshot.getJSONObject("lesson")).equals(ClassroomDocument.key(lesson.json())))
                        throw new IOException("课堂来源不匹配");
                    if(snapshot.optString("corrected_transcript").isBlank())throw new IOException("暂无更正文稿");
                }
            }catch(Exception unavailable){failure=unavailable instanceof IOException?unavailable.getMessage():"更正文稿无法读取";}
            File directory=source;JSONObject summary=snapshot;String error=failure;
            runOnUiThread(()->{
                if(request!=generation||isFinishing()||isDestroyed()||!allowed())return;
                if(error!=null){message(error);return;}
                try{
                    document=ClassroomMarkdownView.correctedTranscript(this,directory,summary,new ClassroomMarkdownView.Actions(){
                        @Override public void assignment(int index) { }
                        @Override public void readingGesture(){ClassroomCorrectedActivity.this.readingGesture();}
                        @Override public void image(String name){if(allowed()){rememberNavigation();AttachmentViewerActivity.openClassroomSummary(ClassroomCorrectedActivity.this,account,lesson,name);}}
                        @Override public Uri imageUri(String name){return allowed()?ImageTransferProvider.classroomSummary(ClassroomCorrectedActivity.this,account,lesson,name):null;}
                    });
                    ClassroomMarkdownView current=document;current.setVisibility(View.INVISIBLE);body.addView(current);
                    current.onReady(()->{
                        if(document!=current||request!=generation||isFinishing()||isDestroyed()||!allowed())return;
                        documentReady=true;current.setVisibility(View.VISIBLE);restoreScroll();
                        if(firstPresentation)UiMotion.enterPage(current,()->firstPresentation=false);
                    });
                }catch(Exception invalid){if(document!=null){document.dispose();document=null;}message("更正文稿无法显示");}
            });
        });
    }
    private void message(String value){
        TextView text=new TextView(this);text.setText(value);text.setTextSize(16);UiStyle.muted(text);body.addView(text);
        restoreScroll();if(firstPresentation)UiMotion.enterPage(text,()->firstPresentation=false);
    }
    private void restoreScroll(){
        if(!restorePosition)return;int y=restoreY,stamp=gesture;restorePosition=false;
        viewport.post(()->{if(stamp==gesture&&!isFinishing()&&!isDestroyed())viewport.scrollTo(0,y);});
    }
    private void readingGesture(){gesture++;restorePosition=false;readingAnchor=viewport.getScrollY();}
    private void rememberNavigation(){navigationY=readingAnchor>=0?readingAnchor:viewport.getScrollY();readingAnchor=-1;}
    @Override protected void onResume(){super.onResume();if(settings!=null){settings.prefs.registerOnSharedPreferenceChangeListener(changes);if(allowed()&&documentReady)restoreScroll();}}
    @Override protected void onPause(){if(viewport!=null){restoreY=navigationY>=0?navigationY:viewport.getScrollY();restorePosition=true;navigationY=-1;}if(settings!=null)settings.prefs.unregisterOnSharedPreferenceChangeListener(changes);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("first_presentation",firstPresentation);state.putInt("scroll_y",viewport==null?0:viewport.getScrollY());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){generation++;if(document!=null)document.dispose();reader.shutdownNow();super.onDestroy();}
}
