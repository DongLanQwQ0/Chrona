package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** Native incremental reader; opening/closing this screen never owns the live connection. */
public final class ClassroomTranscriptActivity extends Activity {
    private ClassroomLesson lesson;private String account;
    private LinearLayout rows;private ScrollView scroll;private ImageButton latest;private TextView status;
    private final List<TextView> paragraphs=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService reader=Executors.newSingleThreadExecutor();
    private boolean active,reading,follow=true,touching;private int generation,pendingRestore=-1;private String signature="";
    private ViewTreeObserver.OnPreDrawListener restore;
    private final Runnable poll=new Runnable(){public void run(){if(!active)return;read();main.postDelayed(this,250);}};
    static void open(Activity activity,String account,ClassroomLesson lesson)throws Exception {
        if(activity instanceof ClassroomTranscriptActivity&&((ClassroomTranscriptActivity)activity).showing(account,lesson)){((ClassroomTranscriptActivity)activity).signature="";return;}
        activity.startActivity(new Intent(activity,ClassroomTranscriptActivity.class).putExtra("account",account).putExtra("lesson",lesson.json().toString()));
    }
    private boolean showing(String owner,ClassroomLesson selected){return owner.equals(account)&&ClassroomMonitorStore.key(selected).equals(ClassroomMonitorStore.key(lesson));}
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);
        try{account=getIntent().getStringExtra("account");lesson=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra("lesson")));if(!ClassroomMonitor.allowed(this,account)){finish();return;}}
        catch(Exception failure){finish();return;}
        if(state!=null)follow=state.getBoolean("follow",true);
        LinearLayout root=SettingsPageLayout.content(this);
        SettingsPageLayout.header(this,root,"实时字幕",UiStyle.navigationIcon(this,R.drawable.ic_eye,"课堂监视",()->ClassroomMonitorUi.choose(this,lesson)));
        TextView title=new TextView(this);title.setText(lesson.course+" · "+lesson.date());title.setTextSize(16);UiStyle.title(title);UiStyle.addSpaced(root,title,0,8);
        status=new TextView(this);status.setTextSize(13);UiStyle.muted(status);status.setText("连接中");UiStyle.addSpaced(root,status,0,12);
        FrameLayout body=new FrameLayout(this);scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(0,0,0,dp(64));scroll.addView(rows);
        body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        latest=UiStyle.navigationIcon(this,R.drawable.ic_arrow_down,"回到最新",()->{follow=true;scroll.fullScroll(View.FOCUS_DOWN);updateFollow();});
        FrameLayout.LayoutParams jump=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.END);body.addView(latest,jump);
        InteractionLayers.pin(latest);
        scroll.setOnTouchListener((view,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){touching=true;follow=false;}if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){touching=false;follow=atBottom();updateFollow();}return false;});
        scroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->{if(touching){follow=atBottom();updateFollow();}});
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));SettingsPageLayout.showFixed(this,root);updateFollow();
        if(state!=null&&!follow)pendingRestore=state.getInt("scroll");
    }
    private boolean atBottom(){return scroll.getScrollY()>=Math.max(0,rows.getHeight()-scroll.getHeight()-dp(48));}
    private void updateFollow(){if(latest!=null)latest.setVisibility(follow?View.GONE:View.VISIBLE);}
    private void read(){
        if(reading)return;if(!ClassroomMonitor.allowed(this,account)){finish();return;}
        reading=true;int token=generation;
        reader.execute(()->{
            try{
                ClassroomStore store=new ClassroomStore(getApplicationContext(),account);File local=store.localLesson(lesson),saved=store.lesson(lesson);
                StringBuilder stamp=new StringBuilder();for(File file:new File[]{new File(local,"live-transcript.json"),new File(local,"live-current.json"),new File(local,"live-status.json"),new File(saved,"materials.json"),new File(local,"monitor-materials.json")})stamp.append(file.lastModified()).append(':').append(file.length()).append('/');
                boolean watching=ClassroomMonitorUi.watching(getApplicationContext(),lesson);
                String version=stamp.toString()+watching+":"+(lesson.startsAt<=System.currentTimeMillis());
                if(version.equals(signature))return;
                JSONObject complete=ClassroomStore.read(new File(saved,"materials.json"));JSONArray segments;
                boolean finalTranscript=complete!=null&&!complete.optBoolean("monitoring")&&complete.optJSONArray("segments")!=null&&complete.getJSONArray("segments").length()>0;
                if(finalTranscript)segments=complete.getJSONArray("segments");
                else{segments=ClassroomTranscript.snapshot(ClassroomLiveTranscripts.read(local));if(segments.length()==0){JSONObject observed=ClassroomStore.read(new File(local,"monitor-materials.json"));if(observed!=null)segments=observed.getJSONArray("segments");}}
                JSONObject connection=ClassroomStore.read(new File(local,"live-status.json"));
                String phase=finalTranscript?"完整字幕":connection==null?"连接中":connection.optString("state","连接中");
                if(!finalTranscript&&!watching)phase=segments.length()>0?"已保存":"未开始监视";
                else if(lesson.startsAt>System.currentTimeMillis())phase="等待上课";
                if(!finalTranscript&&connection!=null&&"实时连接".equals(phase)&&System.currentTimeMillis()-connection.optLong("at")>70_000)phase="正在恢复连接";
                JSONArray incoming=segments;String label=phase+" · "+segments.length()+" 条";
                main.post(()->{if(!active||generation!=token)return;signature=version;render(incoming,label);});
            }catch(Exception failure){main.post(()->{if(active&&generation==token)status.setText("读取失败，正在重试");});}
            finally{main.post(()->reading=false);}
        });
    }
    private void render(JSONArray segments,String label){
        status.setText(label);
        try{
            for(int i=0;i<segments.length();i++){
                if(i==paragraphs.size()){TextView text=new TextView(this);text.setTextSize(16);text.setTextIsSelectable(true);text.setTextColor(UiStyle.colors(this).text);text.setLineSpacing(dp(3),1f);UiStyle.addSpaced(rows,text,0,12);paragraphs.add(text);}
                TextView text=paragraphs.get(i);String value=segments.getJSONObject(i).getString("text");if(!value.contentEquals(text.getText()))text.setText(value);
            }
            while(paragraphs.size()>segments.length())rows.removeView(paragraphs.remove(paragraphs.size()-1));
            if(pendingRestore>=0&&segments.length()>0&&restore==null){
                restore=()->{
                    if(rows.isLayoutRequested())return true;
                    scroll.getViewTreeObserver().removeOnPreDrawListener(restore);restore=null;
                    scroll.scrollTo(0,pendingRestore);pendingRestore=-1;return true;
                };scroll.getViewTreeObserver().addOnPreDrawListener(restore);
            }else if(follow)scroll.post(()->{if(follow)scroll.fullScroll(View.FOCUS_DOWN);});updateFollow();
        }catch(Exception failure){status.setText("字幕格式无法读取");}
    }
    @Override protected void onResume(){super.onResume();if(lesson==null)return;if(!ClassroomMonitor.allowed(this,account)){finish();return;}active=true;generation++;signature="";if(ClassroomMonitorUi.watching(this,lesson))ClassroomMonitor.promote(this);main.post(poll);}
    @Override protected void onPause(){active=false;generation++;main.removeCallbacks(poll);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("follow",follow);if(scroll!=null)state.putInt("scroll",pendingRestore>=0?pendingRestore:scroll.getScrollY());}
    @Override protected void onDestroy(){if(restore!=null&&scroll!=null)scroll.getViewTreeObserver().removeOnPreDrawListener(restore);reader.shutdownNow();super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
