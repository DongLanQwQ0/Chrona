package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import com.donglan.chrona.data.*;
import java.time.*;
import java.util.*;

/** Explicit scan, concrete preview and one atomic confirmation. */
public final class CandidateMergeActivity extends Activity {
    private static final int PREVIEW_PAGE_SIZE = 12;
    private static final class Session {
        volatile CandidateMergeActivity owner;
        boolean busy;
        CandidateMerges.Preview preview;
        String message="";
        int page;
    }
    private Session session;
    @Override protected void onCreate(Bundle state){ThemeStore.apply(this);super.onCreate(state);Object retained=getLastNonConfigurationInstance();session=retained instanceof Session?(Session)retained:new Session();session.owner=this;draw();if(session.preview==null&&!session.busy&&session.message.isEmpty())scan();}
    @Override public Object onRetainNonConfigurationInstance(){return session;}
    @Override protected void onDestroy(){if(session.owner==this)session.owner=null;super.onDestroy();}
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(UiStyle.colors(this).text);return view;}
    private Button button(String label,boolean primary,Runnable action){Button button=new Button(this);button.setText(label);UiStyle.button(button,primary);button.setOnClickListener(view->action.run());button.setEnabled(!session.busy);return button;}
    private void draw(){
        LinearLayout root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"合并完全相同日程");
        if(session.busy)UiStyle.addSpaced(root,text("正在处理…",14),0,16);
        if(!session.message.isEmpty())UiStyle.addSpaced(root,text(session.message,14),0,16);
        UiStyle.addSpaced(root,button("重新扫描",false,this::scan),0,16);
        CandidateMerges.Preview preview=session.preview;
        if(preview!=null){
            UiStyle.addSpaced(root,text(preview.groups.isEmpty()?"没有完全相同的日程":preview.groups.size()+" 组 · 合并 "+preview.removed()+" 份重复日程",17),0,16);
            int begin=session.page*PREVIEW_PAGE_SIZE,end=Math.min(preview.groups.size(),begin+PREVIEW_PAGE_SIZE);
            for(List<EventCandidate> group:preview.groups.subList(begin,end)){
                EventCandidate value=group.get(0);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);UiStyle.glass(card);card.setPadding(dp(16),dp(16),dp(16),dp(16));
                card.addView(text(value.title,18));UiStyle.addSpaced(card,text(summary(value),13),0,8);
                for(EventCandidate candidate:group){TaskRecord task=preview.tasks.get(candidate.taskId);Button source=button((candidate==value?"保留 · ":"来源 · ")+task.source,false,()->startActivity(new Intent(this,TaskDetailActivity.class).putExtra("task_id",task.id).putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID,candidate.id)));UiStyle.addSpaced(card,source,0,8);}
                UiStyle.addSpaced(root,card,0,16);
            }
            if(preview.groups.size()>PREVIEW_PAGE_SIZE){
                LinearLayout pages=new LinearLayout(this);pages.setOrientation(LinearLayout.HORIZONTAL);
                if(session.page>0)pages.addView(button("上一页",false,()->{session.page--;draw();}),new LinearLayout.LayoutParams(0,-2,1));
                if(end<preview.groups.size())pages.addView(button("下一页",false,()->{session.page++;draw();}),new LinearLayout.LayoutParams(0,-2,1));
                UiStyle.addSpaced(root,pages,0,12);
            }
            if(!preview.groups.isEmpty())UiStyle.addSpaced(root,button("确认合并 "+preview.removed()+" 份",true,this::merge),0,8);
        }
        SettingsPageLayout.show(this,root);
    }
    private static String summary(EventCandidate candidate){
        ZoneId zone;
        try{zone=ZoneId.of(candidate.timeZoneId==null?"UTC":candidate.timeZoneId);}catch(DateTimeException invalid){zone=ZoneOffset.UTC;}
        String start=candidate.startAtMillis==null?"未定":Instant.ofEpochMilli(candidate.startAtMillis).atZone(zone).toLocalDateTime().toString();
        String end=candidate.endAtMillis==null?"未定":Instant.ofEpochMilli(candidate.endAtMillis).atZone(zone).toLocalDateTime().toString();
        return start+" — "+end+"\n"+zone+(candidate.allDay?" · 全天":"")+" · "+EventCategory.label(candidate.category)
                +"\n"+(candidate.location==null?"":candidate.location)+"\n"+(candidate.description==null?"":candidate.description)
                +"\n提醒："+(candidate.reminderMinutesBefore==null?"关闭":candidate.reminderMinutesBefore+" 分钟")+(candidate.needsConfirmation?" · 待确认":" · 已确认");
    }
    private void scan(){if(session.busy)return;Session current=session;current.busy=true;current.message="";draw();Context app=getApplicationContext();new Thread(()->{CandidateMerges.Preview preview=null;String error="";try{preview=CandidateMerges.scan(app);}catch(Exception exception){error="扫描失败，请重试";}CandidateMerges.Preview result=preview;String message=error;new Handler(Looper.getMainLooper()).post(()->{current.preview=result;current.page=0;current.message=message;current.busy=false;if(current.owner!=null)current.owner.draw();});},"chrona-merge-scan").start();}
    private void merge(){if(session.busy||session.preview==null)return;Session current=session;long revision=current.preview.revision;current.busy=true;draw();Context app=getApplicationContext();new Thread(()->{String message;try{message="已合并 "+CandidateMerges.merge(app,revision)+" 份重复日程";}catch(Exception exception){message="日程已变化或合并失败，请重新扫描";}String result=message;new Handler(Looper.getMainLooper()).post(()->{current.busy=false;current.preview=null;current.message=result;if(current.owner!=null)current.owner.draw();});},"chrona-merge-confirm").start();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
