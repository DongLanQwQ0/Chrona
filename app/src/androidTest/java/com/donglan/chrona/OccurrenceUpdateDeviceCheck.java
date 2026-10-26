package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.calendar.*;
import com.donglan.chrona.data.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** Real SQLite/Provider and ordinary editor; only labelled fixtures in the shared isolated AVD app. */
final class OccurrenceUpdateDeviceCheck {
    private static int checks,windowHeight;
    private static final long WEEK=7*86400000L;
    static String run(Instrumentation test,Bundle args)throws Exception{
        Context context=test.getTargetContext();FixtureIsolation.require(context);checks=0;windowHeight=Integer.parseInt(args.getString("heightDp","0"));
        CalendarStore calendar=new CalendarStore(context);
        if(!calendar.hasReadPermission()||!calendar.hasWritePermission())throw new IllegalStateException("Calendar permissions required");
        String oldTheme=ThemeStore.mode(context),token="单次验收 "+System.currentTimeMillis();
        List<Long> tasks=new ArrayList<>();Activity page=null;long event=0;
        try{
            ThemeStore.setMode(context,args.getString("theme","light"));
            ZoneId zone=ZoneId.of("Asia/Shanghai");long first=LocalDate.now(zone).plusDays(3).atTime(19,0).atZone(zone).toInstant().toEpochMilli();
            long original=first+WEEK,seriesId,seriesTask;
            try(TaskStore store=new TaskStore(context)){
                seriesTask=store.insertTask(token,null,"occurrence-update-check",System.currentTimeMillis());tasks.add(seriesTask);
                seriesId=store.appendCandidate(new EventCandidate(0,seriesTask,token,first,first+3600000,"Asia/Shanghai","北四-212","常规占位",15,false,null,"event",false)
                        .withRepeat(new RepeatRule("weekly",1,List.of(Instant.ofEpochMilli(first).atZone(zone).getDayOfWeek().getValue()),null,4)));
                store.updateStatus(seriesTask,TaskRecord.READY,null);
            }
            EventCandidate series=saved(context,seriesId);event=calendar.insertEvent(RepeatActions.input(series));
            try(TaskStore store=new TaskStore(context)){require(store.setCalendarEventIdIfUnlinked(seriesId,seriesTask,event),"link master");}
            series=saved(context,seriesId);String stable;
            try(TaskStore store=new TaskStore(context)){stable=store.stableCandidateId(seriesId);}
            JSONArray sent=OccurrenceUpdates.context(context);require(sent.toString().contains(stable)&&sent.toString().contains("start_local"),"request includes saved placeholder and computed local time");
            EventCandidate notice=new EventCandidate(0,0,token+" · 机械设计与3D打印",original+1800000,original+7200000,"Asia/Shanghai","北四-111","请假请周五17点前联系负责人",null,false,null,"event",false);
            long sourceTask=notice(context,tasks,notice,sent,stable,original,List.of("title","start","end","location","description"));
            EventCandidate source=only(context,sourceTask);
            try(TaskStore store=new TaskStore(context)){require(OccurrenceUpdates.held(store,source.id)&&source.needsConfirmation,"parse commit holds proposal before projection");}
            OccurrenceUpdates.stage(context,sourceTask);source=only(context,sourceTask);
            if(!"ui".equals(args.getString("scope"))){
                var batch=com.donglan.chrona.processing.BulkCalendarPublisher.publish(context,List.of(source.id),false);
                var inbox=com.donglan.chrona.processing.BulkCalendarPublisher.publish(context,List.of(sourceTask),true);
                require(batch.published==0&&batch.skipped==1&&inbox.published==0&&inbox.skipped==1,"both bulk entries hold announcement instead of inserting");
                File snapshot=File.createTempFile("occurrence-snapshot-",".db",context.getCacheDir());
                try{
                    try(TaskStore store=new TaskStore(context)){store.createSnapshot(snapshot);}
                    try(TaskStore store=TaskStore.openSnapshot(context,snapshot)){require(store.occurrenceProposal(source.id)!=null&&store.latestParseEvidence(source.id)!=null,"backup snapshot preserves operational proposal and source audit");}
                }finally{snapshot.delete();}
            }
            try(TaskStore store=new TaskStore(context)){
                JSONObject proposal=store.occurrenceProposal(source.id);require("pending".equals(proposal.getString("state")),"proposal staged");
                require(proposal.getLong("original")==original&&source.reminderMinutesBefore==15&&source.dueAtMillis==null,"original identity, reminder inherited, leave deadline is not main due");
                require(store.latestParseEvidence(source.id).getJSONObject("evidence").getJSONObject("model").getJSONObject("occurrence_update").getString("series_id").equals(stable),"original model audit linked");
            }
            page=open(test,context,sourceTask,source.id,null);waitText(test,page,"更新本次");capture(test,context,args,"proposal");
            require(description(page.getWindow().getDecorView(),"更新本次：")!=null,"proposal header announces occurrence update");
            tap(test,description(page.getWindow().getDecorView(),"更新本次："));
            waitFor(test,()->only(context,sourceTask).isRecord,"actual source confirmation applies");
            series=saved(context,seriesId);EventCandidate actual=RepeatOccurrences.at(series,original);
            require(actual.title.equals(notice.title)&&actual.location.equals("北四-111")&&actual.startAtMillis.equals(notice.startAtMillis)&&actual.endAtMillis.equals(notice.endAtMillis),"only occurrence gets full supplied details");
            require(series.title.equals(token)&&series.location.equals("北四-212")&&RepeatOccurrences.at(series,first).location.equals("北四-212")&&RepeatOccurrences.at(series,first+2*WEEK).startAtMillis==first+2*WEEK,"template and adjacent weeks unchanged");
            EventCandidate applied=only(context,sourceTask);require(applied.isRecord&&applied.calendarEventId==null,"source outcome is not a master alias");
            try(TaskStore store=new TaskStore(context)){
                JSONObject proposal=store.occurrenceProposal(applied.id);require(proposal.getJSONArray("applications").length()==1&&proposal.getJSONArray("applications").getJSONObject(0).has("after"),"source application before/after and run reference retained");
                long revision=store.dataRevision();OccurrenceUpdates.apply(context,source,CandidateEditBaseline.of(source));require(store.dataRevision()==revision,"repeated confirmation idempotent");
            }
            List<CalendarOccurrence> provider=provider(calendar,token,first-86400000L,first+4*WEEK);
            require(provider.size()==4&&provider.stream().filter(item->item.title.equals(notice.title)&&item.location.equals("北四-111")&&item.startMillis==notice.startAtMillis).count()==1,"real provider contains four occurrences, one changed");
            Activity close=page;test.runOnMainSync(close::finish);page=null;
            long finalSeriesId=seriesId;page=open(test,context,seriesTask,seriesId,original);waitText(test,page,"本次日程");capture(test,context,args,"editor");
            Activity editor=page;EditText location=findInput(editor.getWindow().getDecorView(),"北四-111");require(location!=null,"ordinary location editor reused");
            if("true".equals(args.getString("ime"))){
                test.runOnMainSync(()->location.requestRectangleOnScreen(new android.graphics.Rect(0,0,location.getWidth(),location.getHeight()),true));
                tap(test,location);test.runOnMainSync(()->((android.view.inputmethod.InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(location,0));
                waitFor(test,()->editor.getWindow().getDecorView().getRootWindowInsets()!=null&&editor.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()),"IME visible");
                test.waitForIdleSync();SystemClock.sleep(400);
                test.runOnMainSync(()->location.requestRectangleOnScreen(new android.graphics.Rect(0,0,location.getWidth(),location.getHeight()),true));
                test.waitForIdleSync();capture(test,context,args,"ime");
                android.graphics.Rect visible=new android.graphics.Rect();require(location.getGlobalVisibleRect(visible)&&visible.height()>=location.getHeight(),"focused field fully reachable above IME; visible="+visible+", fieldHeight="+location.getHeight());
                test.runOnMainSync(()->((android.view.inputmethod.InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(location.getWindowToken(),0));
                waitFor(test,()->!editor.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()),"IME hidden");SystemClock.sleep(400);test.waitForIdleSync();
            }
            test.runOnMainSync(()->location.setText("北四-112"));tap(test,description(editor.getWindow().getDecorView(),"更新本次："));
            waitFor(test,()->RepeatOccurrences.at(saved(context,finalSeriesId),original).location.equals("北四-112"),"real editor save updates this occurrence");
            test.waitForIdleSync();capture(test,context,args,"saved");
            close=page;Activity finish=close;test.runOnMainSync(finish::finish);page=null;
            if(!"ui".equals(args.getString("scope"))){
                long otherOriginal=first+2*WEEK;
                long dayStart=Instant.ofEpochMilli(otherOriginal).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                series=saved(context,seriesId);
                series=RepeatActions.edit(context,seriesTask,seriesId,CandidateEditBaseline.of(series),otherOriginal,new JSONObject()
                        .put("title",token+" · 全天预习").put("all_day",true).put("time_zone_id","UTC").put("start",dayStart).put("end",dayStart+86400000L).put("category","task").put("due",dayStart+86400000L));
                EventCandidate other=RepeatOccurrences.at(series,otherOriginal);
                require(other.isTask()&&other.allDay&&other.timeZoneId.equals("UTC")&&other.startAtMillis==dayStart&&other.endAtMillis==dayStart+86400000L&&series.category.equals("event")&&!series.allDay,"full type/all-day/timezone/start/end/due override leaves template");
                series=RepeatActions.change(context,seriesTask,seriesId,CandidateEditBaseline.of(series),"complete",otherOriginal,null,null);
                require(RepeatOccurrences.at(series,otherOriginal).completed()&&!RepeatOccurrences.at(series,first).completed(),"completion follows occurrence type, not master category");
                series=saved(context,seriesId);String stale=CandidateEditBaseline.of(series);
                JSONObject clear=new JSONObject().put("location",JSONObject.NULL).put("description",JSONObject.NULL).put("reminder_minutes_before",JSONObject.NULL);
                series=RepeatActions.edit(context,seriesTask,seriesId,stale,original,clear);actual=RepeatOccurrences.at(series,original);
                require(actual.location==null&&actual.description==null&&actual.reminderMinutesBefore==null,"explicit field clearing");
                boolean rejected=false;try{RepeatActions.edit(context,seriesTask,seriesId,stale,original,new JSONObject().put("location","stale"));}catch(IllegalStateException expected){rejected=true;}require(rejected,"stale editor rejected");
                // Announcement without end keeps the existing duration rather than parser's default hour.
                EventCandidate movedNotice=new EventCandidate(0,0,token,original+3600000,original+7200000,"Asia/Shanghai","","",null,false,null,"event",false);
                long second=notice(context,tasks,movedNotice,OccurrenceUpdates.context(context),stable,original,List.of("start"));OccurrenceUpdates.stage(context,second);
                EventCandidate projected=only(context,second);require(projected.endAtMillis-projected.startAtMillis==5400000,"missing end inherits current occurrence duration");
                EventCandidate draft=OccurrenceUpdates.copy(new EventCandidate(projected.id,projected.taskId,projected.title,projected.startAtMillis,projected.endAtMillis,projected.timeZoneId,"北四-113",projected.description,projected.reminderMinutesBefore,true,null,projected.category,projected.allDay),projected.id,projected.taskId,null,true,2,false,null);
                try(TaskStore store=new TaskStore(context)){
                    store.getWritableDatabase().beginTransaction();try{store.saveOccurrenceDraft(projected.id,OccurrenceUpdates.diff(projected,draft));require(store.updateCandidate(draft),"save draft candidate");store.getWritableDatabase().setTransactionSuccessful();}finally{store.getWritableDatabase().endTransaction();}
                }
                projected=only(context,second);
                OccurrenceUpdates.apply(context,projected,CandidateEditBaseline.of(projected));
                require(RepeatOccurrences.at(saved(context,seriesId),original).location.equals("北四-113"),"saved draft survives reopening and confirmation");
                require(provider(calendar,token,first-86400000L,first+4*WEEK).size()==4,"another announcement updates same slot without duplicate");
                try(TaskStore store=new TaskStore(context)){store.deleteTask(sourceTask);}
                require(calendar.getEvent(event)!=null,"deleting source never removes target master");
                long invalid=notice(context,tasks,notice,new JSONArray(),stable,original,List.of("location"));OccurrenceUpdates.stage(context,invalid);
                EventCandidate invalidSource=only(context,invalid);
                try(TaskStore store=new TaskStore(context)){require("invalid".equals(store.occurrenceProposal(invalidSource.id).getString("state")),"unoffered target invalid and held");}
                rejected=false;try{OccurrenceUpdates.apply(context,invalidSource,CandidateEditBaseline.of(invalidSource));}catch(IllegalStateException expected){rejected=true;}require(rejected&&!only(context,invalid).isRecord,"invalid target cannot silently create");
                for(Object malformed:List.of("not-an-object",new JSONArray().put("bad"))){
                    long bad=notice(context,tasks,notice,new JSONArray(),malformed);EventCandidate held=only(context,bad);
                    try(TaskStore store=new TaskStore(context)){require(OccurrenceUpdates.held(store,held.id),"malformed model match atomically held");}
                    OccurrenceUpdates.stage(context,bad);
                    try(TaskStore store=new TaskStore(context)){require("invalid_match_type".equals(store.occurrenceProposal(held.id).optString("validation"))&&OccurrenceUpdates.held(store,held.id),"malformed match cannot become ordinary publication");}
                }
                long badProvider=notice(context,tasks,notice,OccurrenceUpdates.context(context),stable,original,List.of("location"));OccurrenceUpdates.stage(context,badProvider);
                EventCandidate pending=only(context,badProvider);String before=saved(context,seriesId).repeatExceptions;
                calendar.deleteEvent(event);rejected=false;try{OccurrenceUpdates.apply(context,pending,CandidateEditBaseline.of(pending));}catch(IllegalStateException expected){rejected=true;}
                require(rejected&&saved(context,seriesId).repeatExceptions.equals(before)&&!only(context,badProvider).isRecord,"provider failure rolls back target and source together");
                try(TaskStore store=new TaskStore(context);AndroidSyncData sync=new AndroidSyncData(context)){
                    JSONObject portable=sync.payload(store.getTask(badProvider));
                    require(portable.getJSONArray("candidates").getJSONObject(0).has("occurrenceUpdate")&&!portable.toString().contains("reminder_minutes_before"),"operational proposal sync retained without device reminders");
                }
            }
            return new JSONObject().put("assertions",checks).put("realProvider",true).put("screenshots",context.getExternalFilesDir(null).getAbsolutePath()).toString();
        }finally{
            if(page!=null){Activity close=page;test.runOnMainSync(close::finish);}
            if(event>0)calendar.deleteEvent(event);
            try(TaskStore store=new TaskStore(context)){for(long task:tasks)store.deleteTask(task);}
            ThemeStore.setMode(context,oldTheme);
        }
    }
    private static long notice(Context context,List<Long> tasks,EventCandidate incoming,JSONArray sent,String stable,long original,List<String> provided)throws Exception{
        try(TaskStore store=new TaskStore(context)){
            long task=store.insertTask("@所有人 内训通知：机械设计与3D打印，地点北四-111；有事请于周五17点前请假。",null,"occurrence-update-check",System.currentTimeMillis());tasks.add(task);
            EventCandidate source=OccurrenceUpdates.copy(incoming,0,task,null,false,0,false,null);
            JSONObject match=new JSONObject().put("series_id",stable).put("original_date",Instant.ofEpochMilli(original).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString())
                    .put("provided_fields",new JSONArray(provided)).put("reason","同一内训的本周通知");
              return parsedNotice(store,task,source,sent,match);
          }
      }
    private static long notice(Context context,List<Long> tasks,EventCandidate incoming,JSONArray sent,Object match)throws Exception{
        try(TaskStore store=new TaskStore(context)){
            long task=store.insertTask("异常匹配格式验证",null,"occurrence-update-check",System.currentTimeMillis());tasks.add(task);
            return parsedNotice(store,task,OccurrenceUpdates.copy(incoming,0,task,null,false,0,false,null),sent,match);
        }
    }
    private static long parsedNotice(TaskStore store,long task,EventCandidate source,JSONArray sent,Object match)throws Exception{
            JSONObject model=new JSONObject().put("occurrence_update",match).put("reminder_specified",false);
            String run=store.beginParseRun(task,System.currentTimeMillis(),new JSONObject().put("recurring_placeholders",sent));
            JSONObject evidence=new JSONObject().put("model",model).put("plan_group",JSONObject.NULL).put("resolved",OccurrenceUpdates.snapshot(source));
            store.replaceParsedCandidates(task,List.of(source),List.of(source),run,new JSONObject().put("entries",new JSONArray().put(evidence)).toString());
            store.updateStatus(task,TaskRecord.NEEDS_REVIEW,null);return task;
    }
    private static EventCandidate saved(Context c,long id){try(TaskStore s=new TaskStore(c)){return s.getCandidatesByIds(List.of(id)).get(0);}}
    private static EventCandidate only(Context c,long task){try(TaskStore s=new TaskStore(c)){return s.getCandidates(task).get(0);}}
    private static List<CalendarOccurrence> provider(CalendarStore c,String token,long from,long to){return c.listAllInstances(from,to).stream().filter(item->item.title.startsWith(token)).toList();}
    private static Activity open(Instrumentation t,Context c,long task,long candidate,Long original){Intent i=new Intent(c,TaskDetailActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("task_id",task).putExtra("candidate_id",candidate);if(original!=null)i.putExtra("occurrence_at",original.longValue());Activity result=t.startActivitySync(i);if(windowHeight>0)t.runOnMainSync(()->result.getWindow().setLayout(-1,Math.round(windowHeight*result.getResources().getDisplayMetrics().density)));return result;}
    private static void waitText(Instrumentation t,Activity a,String text)throws Exception{waitFor(t,()->findText(a.getWindow().getDecorView(),text)!=null,text);}
    private static void waitFor(Instrumentation t,java.util.function.BooleanSupplier condition,String label)throws Exception{long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){t.waitForIdleSync();if(condition.getAsBoolean())return;SystemClock.sleep(50);}throw new AssertionError("Timed out: "+label);}
    private static TextView findText(View v,String text){if(v instanceof TextView x&&x.getText().toString().contains(text))return x;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView x=findText(g.getChildAt(i),text);if(x!=null)return x;}return null;}
    private static EditText findInput(View v,String text){if(v instanceof EditText x&&x.getText().toString().equals(text))return x;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){EditText x=findInput(g.getChildAt(i),text);if(x!=null)return x;}return null;}
    private static View description(View v,String prefix){if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(prefix))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View x=description(g.getChildAt(i),prefix);if(x!=null)return x;}return null;}
    private static void tap(Instrumentation t,View v){if(v==null||!v.isAttachedToWindow())throw new AssertionError("Missing attached touch target");t.runOnMainSync(()->v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()),true));t.waitForIdleSync();android.graphics.Rect visible=new android.graphics.Rect();if(!v.getGlobalVisibleRect(visible))throw new AssertionError("Touch target outside viewport");int[] at=new int[2];v.getLocationOnScreen(at);long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,at[0]+v.getWidth()/2f,at[1]+v.getHeight()/2f,0);try{t.sendPointerSync(e);}finally{e.recycle();}}t.waitForIdleSync();}
    private static void capture(Instrumentation t,Context c,Bundle args,String name)throws Exception{t.waitForIdleSync();SystemClock.sleep(350);Bitmap image=t.getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("No render");try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"occurrence-"+args.getString("widthDp","400")+"-"+args.getString("font","1.0")+"-"+args.getString("theme","light")+"-"+name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}
