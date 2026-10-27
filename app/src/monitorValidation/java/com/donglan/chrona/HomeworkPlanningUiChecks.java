package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.zju.ZjuSnapshot;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Actual touch UI in the isolated emulator package, with unusable school/AI connection settings. */
final class HomeworkPlanningUiChecks {
    private final Instrumentation test;
    private final Bundle args;
    private final Context app;
    private final String account="homework-ui-"+UUID.randomUUID();
    private final Map<String,Map<String,?>> preferences=new HashMap<>();
    private final JSONArray outcomes=new JSONArray();
    private final long now=System.currentTimeMillis();
    private long capture,candidate;
    private String captureStable;
    private ZjuHomeworkStore.State previous;
    private Set<Long> previousTasks;
    private Activity activity;
    private int assertions;

    private HomeworkPlanningUiChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;app=test.getTargetContext();}
    static int run(Instrumentation test,Bundle args)throws Exception{
        if(!"com.donglan.chrona.monitor.validation".equals(test.getTargetContext().getPackageName())
                ||!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Isolated emulator fixture required");
        HomeworkPlanningUiChecks checks=new HomeworkPlanningUiChecks(test,args);
        try{checks.seed();checks.settings();checks.detail();checks.writeReport(null);return checks.assertions;}
        catch(Throwable failure){checks.screenshot("failure");checks.writeReport(failure.toString());throw failure;}
        finally{checks.cleanup();}
    }
    private void seed()throws Exception{
        ZjuSync.cancel();barrier();
        previous=new ZjuHomeworkStore(app).state();previousTasks=taskIds();
        for(String name:List.of("chrona_zju","chrona_ai_settings","appearance"))preferences.put(name,new HashMap<>(app.getSharedPreferences(name,0).getAll()));
        // configured() stays true so the real UI is enabled. Decryption fails BEFORE new ZjuClient,
        // even if the production settings callback requests a refresh. No usable endpoint exists.
        app.getSharedPreferences("chrona_zju",0).edit().clear().putString("account",account)
                .putString("credentials","unusable-fixture-ciphertext").putString("iv","unusable-fixture-iv")
                .putBoolean("enabled",true).putBoolean("homework",true).putBoolean("timetable",false)
                .putLong("homework_attempt",now).putLong("timetable_attempt",now).commit();
        new AiSettingsStore(app).clear();
        app.getSharedPreferences("appearance",0).edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();
        boolean refused=false;try{new ZjuSettingsStore(app).credentials();}catch(Exception expected){refused=true;}
        check(refused&&new AiSettingsStore(app).load()==null,"school decryption gate and absent AI settings prevent all provider traffic");
        synchronized(AndroidSync.LOCK){new ZjuHomeworkStore(app).configure(account,true);capture=new ZjuHomeworkStore(app).state().captureId;}
        try(TaskStore store=new TaskStore(app)){captureStable=store.stableTaskId(capture);}
    }
    private void settings()throws Exception{
        open(ZjuSettingsActivity.class,null);
        Switch auto=toggle("新作业自动排程"),school=toggle("折姜星辰");
        waitFor(()->auto.isEnabled(),"real automatic planning switch enabled for active homework source");
        check(!auto.isChecked()&&!new ZjuSettingsStore(app).homeworkAutoPlan(),"automatic planning defaults off");screenshot("settings-default");
        tap(auto);waitFor(()->node("自动安排新作业？",false)!=null,"consent dialog opens after real switch touch");
        check(!new ZjuSettingsStore(app).homeworkAutoPlan(),"dialog opening alone does not authorize uploading");screenshot("consent");
        tapNode("取消",true);test.waitForIdleSync();
        check(!auto.isChecked()&&!new ZjuSettingsStore(app).homeworkAutoPlan(),"cancel preserves off state");
        tap(auto);tapNode("开启",true);barrier();
        waitFor(()->auto.isChecked()&&auto.isEnabled()&&new ZjuSettingsStore(app).homeworkAutoPlan(),"confirm authorizes and persists switch");
        screenshot("settings-enabled");
        tap(school);barrier();waitFor(()->!school.isChecked()&&!auto.isEnabled(),"school disabled disables automatic planning control");
        screenshot("settings-school-off");
        tap(school);barrier();waitFor(()->school.isChecked()&&auto.isEnabled(),"re-enabling school restores authorized control");
        tap(auto);barrier();waitFor(()->!auto.isChecked()&&!new ZjuSettingsStore(app).homeworkAutoPlan(),"turning automatic planning off revokes authorization directly");
        check(node("自动安排新作业？",false)==null,"revoking authorization does not show an enable consent dialog");
        screenshot("settings-revoked");close();
    }
    private void detail()throws Exception{
        ZoneId zone=ZoneId.systemDefault();
        long start=LocalDate.now().plusDays(1).atTime(12,0).atZone(zone).toInstant().toEpochMilli();
        long end=start+90*60_000L,due=LocalDate.now().plusDays(4).atTime(23,59).atZone(zone).toInstant().toEpochMilli();
        ZjuHomeworkStore school=new ZjuHomeworkStore(app);ZjuHomeworkStore.State state=school.state();
        ZjuSnapshot.Homework source=new ZjuSnapshot.Homework("ui-fixture","course-ui","合成课程 · 报告","用于隔离UI验收","","",due,false);
        candidate=school.apply(account,state.generation,List.of(source)).newHomeworkIds.get(0);
        try(TaskStore store=new TaskStore(app)){
            var db=store.getWritableDatabase();db.beginTransaction();
            try{
                String run=store.beginParseRun(capture,now,new JSONObject().put("source","zju_homework_schedule").put("fixture",true));
                ContentValues times=new ContentValues();times.put("start_at_millis",start);times.put("end_at_millis",end);times.put("uncertainty_level",2);
                db.update("event_candidates",times,"id=?",new String[]{Long.toString(candidate)});
                JSONObject entry=new JSONObject().put("position",0).put("plan_group",JSONObject.NULL)
                        .put("model",new JSONObject().put("time_assessment",new JSONObject().put("suggested_start",new JSONObject()
                                .put("certainty","uncertain").put("basis","合成截止与工作量").put("rule",JSONObject.NULL).put("reason","可修改执行建议"))))
                        .put("resolved",new JSONObject().put("start",start).put("end",end).put("due",due).put("uncertainty_level",2));
                store.saveHomeworkPlanAudit(capture,candidate,run,new JSONObject().put("entries",new JSONArray().put(entry)).toString(),true,null);
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        Bundle input=new Bundle();input.putLong("task_id",capture);input.putLong("candidate_id",candidate);open(TaskDetailActivity.class,input);
        waitFor(()->find(activity.getWindow().getDecorView(),v->v instanceof TextView t&&t.getText().toString().contains("AI建议"))!=null,"real detail shows authorized AI suggestion marker");
        EditText startField=(EditText)find(activity.getWindow().getDecorView(),v->v instanceof EditText&&description(v).startsWith("执行开始时间"));
        check(startField!=null,"personal execution start retains real native picker entry");
        View hint=find(activity.getWindow().getDecorView(),v->v instanceof TextView t&&t.getText().toString().contains("AI建议"));reveal(hint);
        check(visible(hint),"AI suggestion marker is actually visible after scrolling");
        String dueText=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(due).atZone(zone));
        check(find(activity.getWindow().getDecorView(),v->v instanceof EditText e&&e.getText().toString().equals(dueText))!=null,"official deadline remains in actual detail form");
        screenshot("detail-ai");String before=startField.getText().toString();
        tap(startField);tapNode("选择时间",true);
        AccessibilityNodeInfo wheel=node("当前调整时",false);check(wheel!=null,"native execution picker time wheel exposed");
        Rect bounds=new Rect();wheel.getBoundsInScreen(bounds);wheel.recycle();
        float x=bounds.left+bounds.width()*.25f,y=bounds.exactCenterY(),distance=bounds.height()*.22f;
        long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,x,y);
        for(int i=1;i<=12;i++){SystemClock.sleep(35);pointer(down,MotionEvent.ACTION_MOVE,x,y-distance*i/12);}
        SystemClock.sleep(160);pointer(down,MotionEvent.ACTION_UP,x,y-distance);SystemClock.sleep(450);
        tapNode("确定",true);test.waitForIdleSync();
        check(!startField.getText().toString().equals(before),"real wheel gesture changes editable execution start");
        check(find(activity.getWindow().getDecorView(),v->v instanceof TextView t&&t.getText().toString().contains("AI建议"))==null,"unsaved manual execution edit stops describing current fields as AI suggestion");
        check(read().dueAtMillis==due,"manual execution edit leaves official stored deadline intact");screenshot("detail-manual");
        View save=find(activity.getWindow().getDecorView(),v->description(v).startsWith("保存待办："));
        check(save!=null,"detail exposes real save action");tap(save);
        waitFor(()->!format(read().startAtMillis).equals(before),"manual time edit persists through real save action");
        check(read().dueAtMillis==due,"saving manual time preserves official deadline");close();
        open(TaskDetailActivity.class,input);waitFor(()->find(activity.getWindow().getDecorView(),v->v instanceof EditText&&description(v).startsWith("执行开始时间"))!=null,"saved detail reloads");
        check(find(activity.getWindow().getDecorView(),v->v instanceof TextView t&&t.getText().toString().contains("AI建议"))==null,"reopened manual time no longer matches historical AI audit");screenshot("detail-manual-saved");close();
    }
    private EventCandidate read(){try(TaskStore store=new TaskStore(app)){return store.getCandidatesByIds(List.of(candidate)).get(0);}}
    private static String format(Long time){return time==null?"":DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));}
    private void open(Class<? extends Activity> type,Bundle extras){Intent intent=new Intent(app,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(extras!=null)intent.putExtras(extras);activity=test.startActivitySync(intent);test.waitForIdleSync();SystemClock.sleep(700);}
    private Switch toggle(String label)throws Exception{final Switch[] result={null};waitFor(()->{result[0]=(Switch)find(activity.getWindow().getDecorView(),v->v instanceof Switch s&&s.getText().toString().equals(label));return result[0]!=null;},"find switch "+label);return result[0];}
    private static String description(View view){return view.getContentDescription()==null?"":view.getContentDescription().toString();}
    private static View find(View view,Predicate<View> match){if(match.test(view))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),match);if(found!=null)return found;}return null;}
    private static boolean visible(View view){Rect rect=new Rect();return view!=null&&view.isShown()&&view.getGlobalVisibleRect(rect)&&!rect.isEmpty();}
    private void reveal(View view){test.runOnMainSync(()->view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true));test.waitForIdleSync();SystemClock.sleep(200);}
    private void tap(View view){reveal(view);Rect bounds=new Rect();test.runOnMainSync(()->view.getGlobalVisibleRect(bounds));check(!bounds.isEmpty(),"tap target has visible screen bounds");long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY());SystemClock.sleep(60);pointer(down,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY());test.waitForIdleSync();SystemClock.sleep(250);}
    private AccessibilityNodeInfo node(String text,boolean exact){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root==null)return null;try{return node(root,text,exact);}finally{root.recycle();}}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,String text,boolean exact){String value=root.getText()==null?"":root.getText().toString(),description=root.getContentDescription()==null?"":root.getContentDescription().toString();if(root.isVisibleToUser()&&(exact?value.equals(text)||description.equals(text):value.contains(text)||description.contains(text)))return AccessibilityNodeInfo.obtain(root);for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo child=root.getChild(i);if(child==null)continue;try{AccessibilityNodeInfo found=node(child,text,exact);if(found!=null)return found;}finally{child.recycle();}}return null;}
    private void tapNode(String text,boolean exact)throws Exception{final AccessibilityNodeInfo[] target={null};waitFor(()->{target[0]=node(text,exact);return target[0]!=null;},"find dialog target "+text);Rect bounds=new Rect();target[0].getBoundsInScreen(bounds);target[0].recycle();check(!bounds.isEmpty(),"dialog tap target visible "+text);long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY());SystemClock.sleep(60);pointer(down,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY());test.waitForIdleSync();SystemClock.sleep(250);}
    private void pointer(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{if(!test.getUiAutomation().injectInputEvent(event,true))throw new AssertionError("Touch injection rejected");}finally{event.recycle();}}
    private void waitFor(BooleanSupplier predicate,String label)throws Exception{long end=SystemClock.uptimeMillis()+12_000;while(!predicate.getAsBoolean()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(80);check(predicate.getAsBoolean(),label);}
    private void barrier()throws Exception{ZjuSync.WORKER.submit(()->{}).get(12,TimeUnit.SECONDS);test.waitForIdleSync();SystemClock.sleep(250);ZjuSync.WORKER.submit(()->{}).get(12,TimeUnit.SECONDS);test.waitForIdleSync();}
    private void close(){if(activity!=null){Activity closing=activity;test.runOnMainSync(closing::finish);activity=null;test.waitForIdleSync();}}
    private void check(boolean ok,String label){assertions++;try{outcomes.put(new JSONObject().put("check",label).put("passed",ok));}catch(org.json.JSONException invalid){throw new IllegalStateException(invalid);}if(!ok)throw new AssertionError(label);}
    private File directory(){File dir=new File(app.getExternalFilesDir(null),"monitor-validation");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Cannot create fixture evidence directory");return dir;}
    private void screenshot(String stage)throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();if(raw==null)return;float ratio=Math.min(1f,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.max(1,Math.round(raw.getWidth()*ratio)),Math.max(1,Math.round(raw.getHeight()*ratio)),true);try(FileOutputStream out=new FileOutputStream(new File(directory(),"homework-planning-"+stage+"-"+args.getString("widthDp","400")+"-"+args.getString("font","1")+"-"+args.getString("theme","light")+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,75,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
    private void writeReport(String failure)throws Exception{JSONObject value=new JSONObject().put("assertions",assertions).put("checks",outcomes).put("failure",failure==null?JSONObject.NULL:failure).put("network_policy","unusable school credentials; absent AI settings; no provider call");try(FileOutputStream out=new FileOutputStream(new File(directory(),"homework-planning-ui-report.json"))){out.write(value.toString(2).getBytes(StandardCharsets.UTF_8));}}
    private Set<Long> taskIds(){Set<Long> ids=new HashSet<>();try(TaskStore store=new TaskStore(app);var rows=store.getReadableDatabase().rawQuery("SELECT id FROM tasks",null)){while(rows.moveToNext())ids.add(rows.getLong(0));}return ids;}
    private void cleanup()throws Exception{
        close();ZjuSync.cancel();barrier();
        if(capture>0)try(TaskStore store=new TaskStore(app)){
            for(EventCandidate item:store.getCandidates(capture))if(item.calendarEventId!=null)new CalendarStore(app).deleteEvent(item.calendarEventId);
            store.deleteTask(capture);var db=store.getWritableDatabase();
            db.delete("zju_homework_map","account=?",new String[]{account});db.delete("zju_homework_history","account=?",new String[]{account});
            if(captureStable!=null)db.delete("zju_homework_deleted_tasks","sync_id=?",new String[]{captureStable});
            if(previous!=null)db.execSQL("UPDATE managed_zju_state SET enabled=?,account=?,generation=?,capture_id=? WHERE id=1",new Object[]{previous.enabled?1:0,previous.account,previous.generation,previous.captureId==0?null:previous.captureId});
        }
        for(var entry:preferences.entrySet())restore(app.getSharedPreferences(entry.getKey(),0),entry.getValue());
        ZjuSyncJobService.schedule(app);
        if(previousTasks!=null&& !taskIds().equals(previousTasks))throw new AssertionError("Fixture changed pre-existing task IDs");
    }
    private static void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor edit=prefs.edit().clear();for(var entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String s)edit.putString(key,s);else if(value instanceof Boolean b)edit.putBoolean(key,b);else if(value instanceof Long l)edit.putLong(key,l);else if(value instanceof Integer i)edit.putInt(key,i);else if(value instanceof Float f)edit.putFloat(key,f);else if(value instanceof Set<?> set){Set<String> strings=new HashSet<>();for(Object item:set)strings.add((String)item);edit.putStringSet(key,strings);}}if(!edit.commit())throw new IllegalStateException("Cannot restore fixture preferences");}
}
