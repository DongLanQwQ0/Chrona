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
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);
        LinearLayout root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"课程详情");
        TextView content=new TextView(this);content.setText("正在读取");content.setTextSize(18);content.setTextColor(UiStyle.colors(this).text);root.addView(content);
        SettingsPageLayout.show(this,root,false);
        long id=getIntent().getLongExtra("event_id",0),start=getIntent().getLongExtra("begin",System.currentTimeMillis()),end=getIntent().getLongExtra("end",start+60_000L);
        ZjuSync.WORKER.execute(()->{
            CalendarOccurrence found=null;
            try{for(CalendarOccurrence c:ExternalCourseAgenda.merge(this,List.of(),start-86_400_000L,end+86_400_000L))if(c.eventId==id){found=c;break;}}
            catch(RuntimeException ignored){}
            CalendarOccurrence item=found;
            runOnUiThread(()->{if(isDestroyed())return;
                if(item==null){content.setText("课程已更新或该来源已关闭");return;}
                DateTimeFormatter clock=DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault());
                content.setText(item.title+"\n\n"+(item.allDay?"全天":clock.format(Instant.ofEpochMilli(item.startMillis))+" — "+clock.format(Instant.ofEpochMilli(item.endMillis)))
                        +(item.location.isBlank()?"":"\n\n"+item.location)+"\n\n"+item.calendarName);
                String key=AgendaAcknowledgement.key(item.eventId,item.startMillis);
                Button confirm=new Button(this);UiStyle.button(confirm,false);
                Runnable bind=()->{boolean done=AgendaAcknowledgement.isAcknowledged(this,key);confirm.setText(done?"取消确认":"确认活动");confirm.setEnabled(item.endMillis>System.currentTimeMillis());};
                bind.run();confirm.setOnClickListener(v->{if(item.endMillis<=System.currentTimeMillis()){bind.run();return;}AgendaAcknowledgement.set(this,key,item.endMillis,!AgendaAcknowledgement.isAcknowledged(this,key));bind.run();});UiStyle.addSpaced(root,confirm,16,8);
                Button source=new Button(this);source.setText("日程来源");UiStyle.button(source,false);source.setOnClickListener(v->startActivity(new Intent(this,ScheduleSourceSettingsActivity.class)));UiStyle.addSpaced(root,source,20,8);
                if("导入课表".equals(item.calendarName)){Button table=new Button(this);table.setText("查看课表");UiStyle.button(table,false);table.setOnClickListener(v->startActivity(new Intent(this,TimetableActivity.class)));root.addView(table);}
            });
        });
    }
}
