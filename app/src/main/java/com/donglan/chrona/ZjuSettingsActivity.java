package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact campus feature hub; source accounts and preferences have dedicated pages. */
public final class ZjuSettingsActivity extends Activity {
    private TextView account,school,pta;
    @Override protected void onCreate(Bundle saved){
        ThemeStore.apply(this);super.onCreate(saved);
        LinearLayout root=SettingsPageLayout.content(this);
        SettingsPageLayout.header(this,root,"折姜星辰",UiStyle.navigationIcon(this,R.drawable.ic_refresh,"立即同步",()->
                ZjuSync.request(this,result->{if(!isDestroyed()){bind();Feedback.show(this,result);}})));
        LinearLayout identity=SettingsPageLayout.group(this,root,"账号");
        account=SettingsPageLayout.row(this,identity,"账号与同步","",()->SchoolSettingsActivity.open(this,"account"));
        LinearLayout work=SettingsPageLayout.group(this,root,"作业");
        school=SettingsPageLayout.row(this,work,"学在浙大","",()->SchoolSettingsActivity.open(this,"homework"));
        pta=SettingsPageLayout.row(this,work,"PTA","",()->startActivity(new Intent(this,PtaSettingsActivity.class)));
        LinearLayout courses=SettingsPageLayout.group(this,root,"课程");
        SettingsPageLayout.row(this,courses,"课表与校历",null,()->SchoolSettingsActivity.open(this,"timetable"));
        SettingsPageLayout.row(this,courses,"考试安排",null,()->startActivity(new Intent(this,ZjuExamsActivity.class)));
        SettingsPageLayout.row(this,courses,"课堂总结",null,()->startActivity(new Intent(this,ClassroomSummaryActivity.class)));
        SettingsPageLayout.row(this,courses,"课程事务",null,()->ClassroomAffairsActivity.open(this,new ZjuSettingsStore(this).account(),""));
        SettingsPageLayout.show(this,root,saved==null);bind();
    }
    private void bind(){
        ZjuSettingsStore zju=new ZjuSettingsStore(this);PtaSettingsStore pt=new PtaSettingsStore(this);
        account.setText(zju.configured()?zju.enabled()?"已开启":"已登录":"未登录");
        school.setText(zju.enabled()&&new ZjuHomeworkStore(this).state().enabled?"已开启":"未开启");
        pta.setText(pt.prefs.getBoolean("auth_expired",false)?"需登录":pt.enabled()&&new ZjuHomeworkStore(this,2).state().enabled?"已开启":pt.configured()?"未开启":"未登录");
    }
    @Override protected void onResume(){super.onResume();if(account!=null)bind();}
}
