package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** School integration is opt-in; account fields never enter instance state or exports. */
public final class ZjuSettingsActivity extends Activity {
    private ZjuSettingsStore settings;
    private Switch enabled, homework, autoPlan, timetable;
    private EditText user,password;
    private TextView status;
    private android.widget.ImageButton sync;
    private Button save,logout,calendarPermission,classroom;
    private boolean binding,busy;
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);settings=new ZjuSettingsStore(this);
        LinearLayout root=SettingsPageLayout.content(this);
        sync=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"立即同步",()->{
            sync.setEnabled(false);status.setText("正在同步");
            ZjuSync.request(this,result->{if(isDestroyed())return;status.setText(result);bind();});
        });
        SettingsPageLayout.header(this,root,"折姜星辰",sync);
        TextView description=new TextView(this);description.setText("浙江大学专属功能");
        description.setTextSize(13);UiStyle.muted(description);UiStyle.addSpaced(root,description,0,8);
        enabled=toggle(root,"折姜星辰");
        LinearLayout account=new LinearLayout(this);account.setOrientation(LinearLayout.VERTICAL);
        account.setPadding(dp(16),dp(12),dp(16),dp(12));UiStyle.glass(account);
        user=field(account,settings.configured()?"学号（留空保留已保存账号）":"学号",false);
        password=field(account,settings.configured()?"密码（留空保留）":"密码",true);
        LinearLayout accountActions=new LinearLayout(this);
        save=accountAction(accountActions,"保存账号",this::saveAccount);
        logout=accountAction(accountActions,"退出账号",()->UiDialogs.choiceDialog(this,"退出账号",new String[]{"保留账号","退出并停止同步"},0,choice->{
            if(choice==1){ZjuSync.cancel();change(()->{new ZjuHomeworkStore(this).configure(settings.account(),false);settings.clear();new ZjuTimetableStore(this).clear();});}
        }));
        account.addView(accountActions);root.addView(account);
        homework=toggle(root,"作业同步与提醒");
        autoPlan=toggle(root,"新作业自动排程");
        calendarPermission=button(root,"允许写入系统日历",()->requestPermissions(new String[]{
                android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},73113));
        timetable=toggle(root,"课表同步");
        button(root,"校历",()->startActivity(new Intent(this,ZjuCalendarSettingsActivity.class)));
        button(root,"日程来源",()->startActivity(new Intent(this,ScheduleSourceSettingsActivity.class)));
        button(root,"作业更新记录",()->startActivity(new Intent(this,ZjuHomeworkHistoryActivity.class)));
        classroom=button(root,"课堂总结",()->startActivity(new Intent(this,ClassroomSummaryActivity.class)));
        Button cleanup=button(root,"清理未引用的课堂图片",()->{});
        cleanup.setOnClickListener(v->{cleanup.setEnabled(false);ClassroomCleanupService.WORKER.execute(()->{
            String message;
            try{long bytes=com.donglan.chrona.classroom.ClassroomStore.cleanUnused(this,true,System.currentTimeMillis());message="已清理 "+android.text.format.Formatter.formatFileSize(this,bytes);}
            catch(Exception error){message=error.getMessage()==null?"清理失败，请稍后重试":error.getMessage();}
            String result=message;runOnUiThread(()->{if(isDestroyed())return;cleanup.setEnabled(true);Feedback.show(this,result);});
        });});
        status=new TextView(this);status.setTextSize(14);UiStyle.muted(status);UiStyle.addSpaced(root,status,12,4);
        enabled.setOnCheckedChangeListener((v,on)->{
            if(binding)return;
            if(on&&!settings.configured()){Feedback.show(this,"请先保存学号和密码");bind();return;}
            if(!on)ZjuSync.cancel();
            change(()->{new ZjuHomeworkStore(this).configure(settings.account(),on&&settings.prefs.getBoolean("homework",true));
                settings.options(on,settings.prefs.getBoolean("timetable",false));});
        });
        homework.setOnCheckedChangeListener((v,on)->{if(!binding){if(!on)ZjuSync.cancel();change(()->{new ZjuHomeworkStore(this).configure(settings.account(),on&&settings.enabled());settings.prefs.edit().putBoolean("homework",on).apply();});}});
        autoPlan.setOnCheckedChangeListener((v,on)->{
            if(binding)return;
            bind();
            if(!on){settings.revokeHomeworkAutoPlan();change(()->settings.homeworkAutoPlan(false));return;}
            UiDialogs.confirmDialog(this,"自动安排新作业？","作业标题、内容和截止时间将发送至当前AI服务，建议的执行时间会直接应用，可随时修改。已有安排不变。","开启",()->change(()->settings.homeworkAutoPlan(true)));
        });
        timetable.setOnCheckedChangeListener((v,on)->{if(!binding){if(!on)ZjuSync.cancel();change(()->settings.options(settings.enabled(),on));}});
        SettingsPageLayout.show(this,root,state==null);bind();
    }
    private void saveAccount(){
        String name=user.getText().toString(),secret=password.getText().toString();
        boolean retainHomework=homework.isChecked();
        ZjuSync.cancel();
        change(()->{
            String actual=name;
            if(actual.trim().isEmpty()&&settings.configured()){
                String[] current=settings.credentials();actual=current[0];java.util.Arrays.fill(current,"");
            }
            settings.save(actual,secret);
            new ZjuHomeworkStore(this).configure(settings.account(),settings.enabled()&&retainHomework);
        });
        password.setText("");user.setText("");
    }
    private interface Work{void run()throws Exception;}
    private void change(Work work){
        if(busy)return;busy=true;controls(false);
        ZjuSync.WORKER.execute(()->{
            String error=null;boolean refresh=false;
            try{synchronized(AndroidSync.LOCK){work.run();refresh=settings.enabled()&&settings.configured()
                        &&(new ZjuHomeworkStore(this).state().enabled||settings.timetable());}
                ZjuSyncJobService.schedule(this);AgendaWidgetProvider.requestRefresh(this);AgendaReminder.requestRefresh(this);}
            catch(Exception e){error="无法保存设置，请检查账号或重新填写";}
            String message=error;boolean refreshNow=refresh;
            runOnUiThread(()->{busy=false;if(isDestroyed())return;if(message!=null)Feedback.show(this,message);bind();
                if(refreshNow&&message==null){status.setText("正在同步");ZjuSync.request(this,result->{if(!isDestroyed()){status.setText(result);bind();}});}
            });
        });
    }
    private void controls(boolean on){for(View v:new View[]{enabled,homework,autoPlan,timetable,save,logout,sync,user,password})v.setEnabled(on);}
    private void bind(){
        binding=true;enabled.setChecked(settings.enabled());autoPlan.setChecked(settings.homeworkAutoPlan());timetable.setChecked(settings.prefs.getBoolean("timetable",false));binding=false;
        controls(!busy);homework.setEnabled(!busy&&settings.enabled());autoPlan.setEnabled(!busy&&settings.enabled()&&homework.isChecked());timetable.setEnabled(!busy&&settings.enabled());
        com.donglan.chrona.calendar.CalendarStore calendar=new com.donglan.chrona.calendar.CalendarStore(this);
        calendarPermission.setVisibility(calendar.hasReadPermission()&&calendar.hasWritePermission()?View.GONE:View.VISIBLE);
        sync.setEnabled(!busy&&settings.enabled()&&settings.configured());status.setText(settings.status());
        classroom.setVisibility(settings.enabled()?View.VISIBLE:View.GONE);
        classroom.setEnabled(!busy&&settings.enabled()&&settings.configured());
        ZjuSync.WORKER.execute(()->{
            boolean on; synchronized(AndroidSync.LOCK){on=new ZjuHomeworkStore(this).state().enabled;}
            runOnUiThread(()->{if(isDestroyed()||busy)return;binding=true;homework.setChecked(on);autoPlan.setEnabled(settings.enabled()&&on);binding=false;});
        });
    }
    @Override protected void onResume(){super.onResume();if(settings!=null&&status!=null)bind();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==73113){bind();ZjuSync.WORKER.execute(()->{
            ZjuHomeworkCalendar.retryPending(this);AgendaReminder.requestRefresh(this);AgendaWidgetProvider.requestRefresh(this);
        });}
    }
    private Switch toggle(LinearLayout root,String title){Switch s=new Switch(this);s.setText(title);s.setTextSize(16);s.setMinHeight(dp(54));s.setPadding(dp(12),dp(5),dp(12),dp(5));UiStyle.toggle(s);UiStyle.addSpaced(root,s,4,8);return s;}
    private EditText field(LinearLayout root,String hint,boolean secret){EditText f=new EditText(this);f.setHint(hint);f.setSingleLine();f.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));f.setSaveEnabled(false);f.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);UiStyle.input(f);UiStyle.addSpaced(root,f,0,10);return f;}
    private Button button(LinearLayout root,String title,Runnable run){Button b=new Button(this);b.setText(title);UiStyle.button(b,false);b.setOnClickListener(v->run.run());UiStyle.addSpaced(root,b,6,6);return b;}
    private Button accountAction(LinearLayout row,String title,Runnable run){
        Button button=new Button(this);button.setText(title);UiStyle.button(button,false);button.setOnClickListener(v->run.run());
        button.setPadding(dp(12),button.getPaddingTop(),dp(12),button.getPaddingBottom());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
        params.setMargins(row.getChildCount()==0?0:dp(8),dp(6),0,dp(6));row.addView(button,params);return button;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
