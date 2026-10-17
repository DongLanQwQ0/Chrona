package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.donglan.chrona.calendar.CalendarOccurrence;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ExternalCourseDetailActivity extends Activity {
    private boolean schoolSource;
    private boolean resumed;
    private long acknowledgementUntil;
    private Runnable bindAcknowledgement;
    private final android.os.Handler expiryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable expiryRefresh = this::refreshAcknowledgement;
    static Intent sourceIntent(android.content.Context context,String source,String key,long start,long end){
        return new Intent(context,ExternalCourseDetailActivity.class).putExtra("source",source)
                .putExtra("source_key",key).putExtra("begin",start).putExtra("end",end);
    }
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);
        schoolSource="zju".equals(getIntent().getStringExtra("source"));
        if(schoolSource&&!new ZjuSettingsStore(this).enabled()){finish();return;}
        LinearLayout root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"课程详情");
        TextView content=new TextView(this);content.setText("正在读取");content.setTextSize(18);content.setTextColor(UiStyle.colors(this).text);root.addView(content);
        SettingsPageLayout.show(this,root,false);
        long id=getIntent().getLongExtra("event_id",0),start=getIntent().getLongExtra("begin",System.currentTimeMillis()),end=getIntent().getLongExtra("end",start+60_000L);
        new Thread(()->{
            CalendarOccurrence found=null;
            boolean failed=false;
            try{
                if("zju".equals(getIntent().getStringExtra("source"))){
                    ZjuSettingsStore settings=new ZjuSettingsStore(this);
                    if(settings.enabled())for(com.donglan.chrona.zju.ZjuSnapshot.Lesson c:new ZjuTimetableStore(this).load(settings.account()))
                        if(c.key.equals(getIntent().getStringExtra("source_key"))){
                            found=new CalendarOccurrence(ExternalCourseAgenda.id("zju:"+settings.account()+":"+c.key),c.start,c.end,c.title,false,c.location,"折姜星辰",null,CourseSourceMatching.ZJU,"",c.notes);break;
                        }
                }else for(CalendarOccurrence c:ExternalCourseAgenda.merge(this,List.of(),start-86_400_000L,end+86_400_000L))if(c.eventId==id){found=c;break;}
            }
            catch(Exception error){failed=true;}
            CalendarOccurrence item=found;
            boolean readFailed=failed;
            runOnUiThread(()->{if(isDestroyed())return;
                if(item==null||item.calendarId==CourseSourceMatching.ZJU&&!new ZjuSettingsStore(this).enabled()){
                    content.setText(readFailed?"读取课程失败，请返回后重试":"zju".equals(getIntent().getStringExtra("source"))?"来源不可用":"课程已更新或该来源已关闭");return;
                }
                schoolSource=item.calendarId==CourseSourceMatching.ZJU;
                DateTimeFormatter clock=DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault());
                root.removeView(content);
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);UiStyle.card(card);
                card.setPadding(dp(16),dp(12),dp(16),dp(12));root.addView(card,new LinearLayout.LayoutParams(-1,-2));
                TextView title=new TextView(this);title.setText(item.title);title.setTextSize(17);UiStyle.title(title);card.addView(title);
                detail(card,item.allDay?"全天":clock.format(Instant.ofEpochMilli(item.startMillis))+" — "+clock.format(Instant.ofEpochMilli(item.endMillis)));
                if(!item.location.isBlank())detail(card,item.location);
                detail(card,item.calendarName);
                if(!item.description.isBlank())detail(card,item.description);
                String key=AgendaAcknowledgement.key(item.eventId,item.startMillis);
                Button confirm=new Button(this);UiStyle.button(confirm,false);
                acknowledgementUntil=item.endMillis;
                bindAcknowledgement=()->{boolean done=AgendaAcknowledgement.isAcknowledged(this,key);confirm.setText(done?"取消确认":"确认活动");confirm.setVisibility(item.endMillis>System.currentTimeMillis()?android.view.View.VISIBLE:android.view.View.GONE);confirm.setEnabled(item.endMillis>System.currentTimeMillis());};
                refreshAcknowledgement();confirm.setOnClickListener(v->{if(item.endMillis<=System.currentTimeMillis()){refreshAcknowledgement();return;}AgendaAcknowledgement.set(this,key,item.endMillis,!AgendaAcknowledgement.isAcknowledged(this,key));refreshAcknowledgement();});UiStyle.addSpaced(root,confirm,16,8);
                Button source=new Button(this);source.setText("日程来源");UiStyle.button(source,false);source.setOnClickListener(v->startActivity(new Intent(this,ScheduleSourceSettingsActivity.class)));UiStyle.addSpaced(root,source,20,8);
                if("导入课表".equals(item.calendarName)){Button table=new Button(this);table.setText("查看课表");UiStyle.button(table,false);table.setOnClickListener(v->startActivity(new Intent(this,TimetableActivity.class)));root.addView(table);}
            });
        },"course-detail-local-reader").start();
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(schoolSource&&!new ZjuSettingsStore(this).enabled())finish();refreshAcknowledgement();}
    @Override protected void onPause(){resumed=false;expiryHandler.removeCallbacks(expiryRefresh);super.onPause();}
    private void refreshAcknowledgement(){
        expiryHandler.removeCallbacks(expiryRefresh);
        if(bindAcknowledgement==null)return;
        bindAcknowledgement.run();
        long remaining=acknowledgementUntil-System.currentTimeMillis();
        if(resumed&&remaining>0)expiryHandler.postDelayed(expiryRefresh,remaining);
    }
    private void detail(LinearLayout parent,String value){TextView label=new TextView(this);label.setText(value);label.setTextSize(14);UiStyle.muted(label);UiStyle.addSpaced(parent,label,8,0);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
