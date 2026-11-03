package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.widget.*;

public final class PtaSettingsActivity extends Activity {
    private PtaSettingsStore settings;
    private TextView account,status;
    private Switch enabled,plan;
    private boolean binding;
    private ImageButton refresh;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){updateStatus();handler.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle saved){
        ThemeStore.apply(this);super.onCreate(saved);settings=new PtaSettingsStore(this);
        LinearLayout root=SettingsPageLayout.content(this);
        refresh=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"刷新 PTA 作业",this::sync);
        SettingsPageLayout.header(this,root,"PTA 作业",refresh);
        LinearLayout identity=SettingsPageLayout.group(this,root,"账号");
        account=SettingsPageLayout.row(this,identity,"PTA 账号","未登录",()->startActivity(new Intent(this,PtaLoginActivity.class)));
        SettingsPageLayout.row(this,identity,"退出登录",null,()->UiDialogs.confirmDialog(this,"退出 PTA？","保留已有作业，停止 PTA 同步。","退出",()->{
            ZjuSync.cancelPta();synchronized(AndroidSync.LOCK){new ZjuHomeworkStore(this,2).configure(settings.account(),false);settings.clear();}
            // Expire only PTA's session cookie; never clear the shared WebView cookie store.
            PtaLoginActivity.clearBrowserSession();HomeworkNotice.refresh(this);ZjuSyncJobService.schedule(this);bind();
        }));
        LinearLayout sync=SettingsPageLayout.group(this,root,"作业同步");
        enabled=SettingsPageLayout.toggle(this,sync,"作业同步与提醒",false);
        plan=SettingsPageLayout.toggle(this,sync,"新作业自动排程",false);
        enabled.setOnCheckedChangeListener((v,on)->{if(binding)return;
            if(on&&!settings.configured()){bind();startActivity(new Intent(this,PtaLoginActivity.class));return;}
            ZjuSync.cancelPta();synchronized(AndroidSync.LOCK){new ZjuHomeworkStore(this,2).configure(settings.account(),on);settings.prefs.edit().putBoolean("enabled",on).putLong("attempt",0).apply();}
            HomeworkNotice.refresh(this);ZjuSyncJobService.schedule(this);bind();if(on)sync();
        });
        plan.setOnCheckedChangeListener((v,on)->{if(binding)return;bind();
            if(on)UiDialogs.confirmDialog(this,"自动安排 PTA 新作业？","题集标题、内容和截止时间将发送至当前AI服务，执行时间直接应用，可随时修改。已有安排不变。","开启",()->{settings.prefs.edit().putBoolean("auto_plan",true).apply();bind();});
            else{settings.prefs.edit().putBoolean("auto_plan",false).apply();ZjuHomeworkPlanner.cancelPending(this,2);bind();}
        });
        SettingsPageLayout.row(this,sync,"同步题集",null,()->scope(false));
        SettingsPageLayout.row(this,sync,"关联课程",null,()->scope(true));
        SettingsPageLayout.row(this,sync,"作业更新记录",null,()->startActivity(new Intent(this,ZjuHomeworkHistoryActivity.class).putExtra("source_id",2)));
        var calendar=new com.donglan.chrona.calendar.CalendarStore(this);
        if(!calendar.hasReadPermission()||!calendar.hasWritePermission())SettingsPageLayout.row(this,sync,"允许写入系统日历",null,
                ()->requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},73113));
        status=new TextView(this);status.setTextSize(14);UiStyle.muted(status);UiStyle.addSpaced(root,status,8,0);
        SettingsPageLayout.show(this,root,saved==null);bind();
    }
    private void scope(boolean courses){startActivity(new Intent(this,PtaScopeActivity.class).putExtra("courses",courses));}
    private void bind(){binding=true;account.setText(settings.configured()?settings.nickname():"未登录");
        enabled.setChecked(settings.enabled()&&new ZjuHomeworkStore(this,2).state().enabled);plan.setChecked(settings.autoPlan());
        plan.setEnabled(enabled.isChecked());updateStatus();binding=false;}
    private void sync(){ZjuSync.requestPta(this,r->{if(!isDestroyed())bind();});updateStatus();}
    private void updateStatus(){
        if(status==null)return;
        boolean busy=ZjuSync.ptaRefreshing();String text=busy?"正在同步":settings.status();
        if(!text.contentEquals(status.getText()))status.setText(text);
        refresh.setEnabled(!busy&&settings.enabled());refresh.setContentDescription(busy?"正在同步 PTA 作业":"刷新 PTA 作业");
    }
    @Override protected void onResume(){super.onResume();if(account!=null)bind();handler.post(poll);}
    @Override protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==73113)ZjuSync.WORKER.execute(()->ZjuHomeworkCalendar.retryPending(this));}
}
