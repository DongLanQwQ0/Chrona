package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

/** The school's account, homework and timetable settings, separated by responsibility. */
public final class SchoolSettingsActivity extends Activity {
    private ZjuSettingsStore settings;
    private TextView status;
    private EditText user,password;
    private boolean busy,binding;
    private ImageButton refresh;
    private final java.util.List<android.view.View> controls=new java.util.ArrayList<>();
    private final java.util.Map<Switch,java.util.function.BooleanSupplier> toggles=new java.util.LinkedHashMap<>();
    static void open(Activity a,String page){a.startActivity(new Intent(a,SchoolSettingsActivity.class).putExtra("page",page));}
    @Override protected void onCreate(Bundle saved){
        ThemeStore.apply(this);super.onCreate(saved);settings=new ZjuSettingsStore(this);
        String page=getIntent().getStringExtra("page");LinearLayout root=SettingsPageLayout.content(this);
        refresh=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"立即同步",this::sync);
        SettingsPageLayout.header(this,root,"homework".equals(page)?"学在浙大":"timetable".equals(page)?"课表与校历":"账号与同步",refresh);
        if("homework".equals(page))homework(root);else if("timetable".equals(page))timetable(root);else account(root);
        status=new TextView(this);status.setTextSize(14);UiStyle.muted(status);UiStyle.addSpaced(root,status,8,0);status.setText(settings.status());
        SettingsPageLayout.show(this,root,saved==null);
    }
    private void toggle(LinearLayout group,String label,java.util.function.BooleanSupplier value,java.util.function.Consumer<Boolean> action){
        Switch s=SettingsPageLayout.toggle(this,group,label,value.getAsBoolean());controls.add(s);toggles.put(s,value);
        s.setOnCheckedChangeListener((v,on)->{if(!busy&&!binding){bind();action.accept(on);}});
    }
    private void account(LinearLayout root){
        LinearLayout group=SettingsPageLayout.group(this,root,"学校账号");
        user=field(group,settings.configured()?"学号（留空保留）":"学号",false);
        password=field(group,settings.configured()?"密码（留空保留）":"密码",true);
        SettingsPageLayout.row(this,group,"保存账号",null,()->{
            String name=user.getText().toString(),secret=password.getText().toString();
            change(()->{
                String actual=name;
                if(actual.isBlank()&&settings.configured()){String[] c=settings.credentials();actual=c[0];java.util.Arrays.fill(c,"");}
                settings.save(actual,secret);new ZjuHomeworkStore(this).configure(settings.account(),settings.enabled()&&settings.prefs.getBoolean("homework",true));
            },()->{user.setText("");password.setText("");});
        });
        toggle(group,"折姜星辰",settings::enabled,on->change(()->{
            if(on&&!settings.configured())throw new java.io.IOException("请先保存学号和密码");
            new ZjuHomeworkStore(this).configure(settings.account(),on&&settings.prefs.getBoolean("homework",true));
            settings.options(on,settings.prefs.getBoolean("timetable",false));
        },null));
        SettingsPageLayout.row(this,group,"退出账号",null,()->UiDialogs.confirmDialog(this,"退出学校账号？","保留已有作业，停止学校同步。","退出",()->change(()->{
            new ZjuHomeworkStore(this).configure(settings.account(),false);settings.clear();new ZjuTimetableStore(this).clear();
        },this::finish)));
    }
    private void homework(LinearLayout root){
        LinearLayout group=SettingsPageLayout.group(this,root,"作业同步");
        toggle(group,"作业同步与提醒",()->new ZjuHomeworkStore(this).state().enabled,on->change(()->{
            if(on&&(!settings.enabled()||!settings.configured()))throw new java.io.IOException("请先启用学校账号");
            new ZjuHomeworkStore(this).configure(settings.account(),on);settings.prefs.edit().putBoolean("homework",on).apply();
        },null));
        toggle(group,"新作业自动排程",settings::homeworkAutoPlan,on->{
            if(!on){settings.revokeHomeworkAutoPlan();change(()->settings.homeworkAutoPlan(false),null);return;}
            UiDialogs.confirmDialog(this,"自动安排新作业？","作业标题、内容和截止时间将发送至当前AI服务，执行时间直接应用，可随时修改。已有安排不变。","开启",()->change(()->settings.homeworkAutoPlan(true),null));
        });
        SettingsPageLayout.row(this,group,"作业更新记录",null,()->startActivity(new Intent(this,ZjuHomeworkHistoryActivity.class)));
        SettingsPageLayout.row(this,group,"账号与同步",null,()->open(this,"account"));
        var calendar=new com.donglan.chrona.calendar.CalendarStore(this);
        if(!calendar.hasReadPermission()||!calendar.hasWritePermission())SettingsPageLayout.row(this,SettingsPageLayout.group(this,root,"日历"),"允许写入系统日历",null,
                ()->requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},73113));
    }
    private void timetable(LinearLayout root){
        LinearLayout group=SettingsPageLayout.group(this,root,"课表");
        toggle(group,"课表同步",settings::timetable,on->change(()->{
            if(on&&(!settings.enabled()||!settings.configured()))throw new java.io.IOException("请先启用学校账号");settings.options(settings.enabled(),on);
        },null));
        SettingsPageLayout.row(this,group,"校历",null,()->startActivity(new Intent(this,ZjuCalendarSettingsActivity.class)));
        SettingsPageLayout.row(this,group,"日程来源",null,()->startActivity(new Intent(this,ScheduleSourceSettingsActivity.class)));
        SettingsPageLayout.row(this,group,"账号与同步",null,()->open(this,"account"));
    }
    private EditText field(LinearLayout group,String hint,boolean secret){
        EditText f=new EditText(this);f.setHint(hint);f.setSingleLine();f.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        f.setSaveEnabled(false);f.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);UiStyle.input(f);UiStyle.addSpaced(group,f,4,6);controls.add(f);return f;
    }
    private void bind(){binding=true;for(var e:toggles.entrySet()){
        e.getKey().setChecked(e.getValue().getAsBoolean());
        if("新作业自动排程".contentEquals(e.getKey().getText()))e.getKey().setEnabled(!busy&&settings.enabled()&&new ZjuHomeworkStore(this).state().enabled);
    }refresh.setEnabled(!busy&&settings.enabled()&&settings.configured());binding=false;}
    private void sync(){if(!settings.enabled()||!settings.configured()){bind();return;}refresh.setEnabled(false);status.setText("正在同步");ZjuSync.requestSchool(this,r->{if(!isDestroyed()){status.setText(settings.status());bind();}});}
    private interface Work{void run()throws Exception;}
    private void change(Work work,Runnable done){
        if(busy)return;busy=true;for(var v:controls)v.setEnabled(false);ZjuSync.cancelSchool();
        ZjuSync.WORKER.execute(()->{
            String error=null;
            try{synchronized(AndroidSync.LOCK){work.run();}}catch(Exception e){error=e.getMessage()==null?"无法保存设置":e.getMessage();}
            String failure=error;ZjuSyncJobService.schedule(this);AgendaReminder.requestRefresh(this);AgendaWidgetProvider.requestRefresh(this);
            runOnUiThread(()->{if(isDestroyed())return;busy=false;for(var v:controls)v.setEnabled(true);bind();
                if(failure!=null)Feedback.show(this,failure);else{if(done!=null)done.run();if(!isFinishing())sync();}
            });
        });
    }
    @Override protected void onResume(){super.onResume();if(settings!=null)bind();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==73113)ZjuSync.WORKER.execute(()->ZjuHomeworkCalendar.retryPending(this));}
}
