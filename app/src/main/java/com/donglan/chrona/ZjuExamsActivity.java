package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.donglan.chrona.net.NetworkFailure;
import com.donglan.chrona.zju.ZjuClient;
import com.donglan.chrona.zju.ZjuExam;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only exam list, using the existing school identity and secondary-page vocabulary. */
public final class ZjuExamsActivity extends Activity {
    private Session session;
    private LinearLayout content;
    private TextView status;
    private ImageButton refresh;
    private List<ZjuExam> shown;
    private boolean firstDataPresentation;

    /** A rotation retains exactly one query; leaving the page cancels its owned request. */
    static final class Session {
        final Context app;
        final String account,credentialsRevision;
        final SharedPreferences preferences;
        final java.util.function.BiFunction<String,String,ZjuClient> connections;
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        final Handler main=new Handler(Looper.getMainLooper());
        final SharedPreferences.OnSharedPreferenceChangeListener changes;
        volatile ZjuClient client;
        volatile boolean disposed,busy;
        volatile List<ZjuExam> rows;
        volatile String message="";
        Runnable listener;
        Session(Context context){this(context,ZjuClient::new);}
        Session(Context context,java.util.function.BiFunction<String,String,ZjuClient> connections){
            this.connections=connections;
            app=context.getApplicationContext();ZjuSettingsStore settings=new ZjuSettingsStore(app);
            account=settings.account();preferences=settings.prefs;
            credentialsRevision=preferences.getString("credentials","");
            changes=(p,k)->{if(!allowed()){ZjuClient active=client;if(active!=null)active.cancel();publish();}};
            preferences.registerOnSharedPreferenceChangeListener(changes);
        }
        boolean allowed(){
            ZjuSettingsStore settings=new ZjuSettingsStore(app);
            return !disposed&&settings.enabled()&&settings.configured()&&account.equals(settings.account())
                    &&credentialsRevision.equals(preferences.getString("credentials",""));
        }
        void publish(){main.post(()->{if(listener!=null&&!disposed)listener.run();});}
        void load(){
            if(busy||!allowed())return;busy=true;message="正在刷新";publish();
            worker.execute(()->{
                try{
                    if(rows==null){
                        try{rows=new ZjuExamStore(app).load(account);publish();}
                        catch(Exception badCache){/* A failed local read must not prevent an official refresh. */}
                    }
                    ZjuClient active;
                    synchronized(AndroidSync.LOCK){
                        if(!allowed())return;
                        String[] pair=new ZjuSettingsStore(app).credentials();
                        try{active=connections.apply(pair[0],pair[1]);client=active;}
                        finally{Arrays.fill(pair,"");}
                    }
                    try(active){
                        if(!allowed()){active.cancel();return;}
                        List<ZjuExam> fresh=active.exams();
                        synchronized(AndroidSync.LOCK){
                            if(!allowed())return;
                            new ZjuExamStore(app).save(account,fresh);rows=fresh;message="";
                        }
                    }finally{client=null;}
                }catch(Exception failure){
                    message=NetworkFailure.hasDetails(failure)?NetworkFailure.describe(failure):
                            failure instanceof ZjuClient.SchoolException?failure.getMessage():"考试查询失败，请重试";
                    if(rows!=null)message+=" · 已保留上次数据";
                }finally{busy=false;publish();}
            });
        }
        void dispose(){
            disposed=true;listener=null;preferences.unregisterOnSharedPreferenceChangeListener(changes);
            ZjuClient active=client;if(active!=null)active.cancel();worker.shutdown();
        }
    }

    @Override protected void onCreate(Bundle saved){
        ThemeStore.apply(this);super.onCreate(saved);
        firstDataPresentation=saved==null||saved.getBoolean("first_data_presentation",false);
        Object retained=getLastNonConfigurationInstance();
        session=retained instanceof Session value?value:new Session(this);
        LinearLayout root=SettingsPageLayout.content(this);
        refresh=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"刷新考试安排",()->session.load());
        SettingsPageLayout.header(this,root,"考试安排",refresh);
        status=text("",13,false);status.setVisibility(View.GONE);UiStyle.addSpaced(root,status,0,8);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        SettingsPageLayout.show(this,root,saved==null);
        session.listener=this::render;render();
        if(retained==null)session.load();
    }
    @Override public Object onRetainNonConfigurationInstance(){return session;}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("first_data_presentation",firstDataPresentation);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();if(session!=null)render();}
    @Override protected void onDestroy(){
        session.listener=null;if(!isChangingConfigurations())session.dispose();
        super.onDestroy();
    }
    private void render(){
        if(isDestroyed()||isFinishing())return;
        boolean allowed=session.allowed();
        refresh.setEnabled(allowed&&!session.busy);
        String note=allowed?session.message:"请重新进入已启用的学校账号";
        status.setText(note);status.setVisibility(note.isEmpty()?View.GONE:View.VISIBLE);
        List<ZjuExam> rows=allowed?session.rows:null;
        if(shown==rows&&content.getChildCount()>0)return;
        shown=rows;content.removeAllViews();
        if(rows==null){if(!session.busy&&allowed)content.addView(text("尚未获取考试安排",15,false));return;}
        if(rows.isEmpty()){content.addView(text("暂无已公布考试",15,false));presentData();return;}
        long now=System.currentTimeMillis();boolean past=false;
        for(ZjuExam exam:ZjuExam.ordered(rows,now)){
            if(exam.ended(now)&&!past){past=true;UiStyle.addSpaced(content,text("已结束",13,false),0,8);}
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            UiStyle.card(card);card.setPadding(dp(16),dp(12),dp(16),dp(12));
            TextView title=text(exam.title,16,true);title.setTextIsSelectable(true);card.addView(title);
            TextView time=text(exam.kind+" · "+exam.displayTime(),14,true);time.setTextIsSelectable(true);
            UiStyle.addSpaced(card,time,4,0);
            String place=exam.room.isEmpty()?"考场待公布":exam.room;
            String seat=exam.seat.isEmpty()?"座位待公布":"座位 "+exam.seat;
            TextView location=text(place+" · "+seat,13,false);location.setTextIsSelectable(true);
            UiStyle.addSpaced(card,location,4,0);UiStyle.addSpaced(content,card,0,8);
        }
        presentData();
    }
    private void presentData(){if(firstDataPresentation)UiMotion.enterPage(content,()->firstDataPresentation=false);}
    private TextView text(String value,int size,boolean primary){
        TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setIncludeFontPadding(false);
        if(primary)UiStyle.title(text);else UiStyle.muted(text);return text;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
