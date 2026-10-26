package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import com.donglan.chrona.data.TaskStore;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Device-only integration check: real server + real Android SQLite, isolated from user data. */
public final class LanAccessInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) {
        this.arguments = arguments == null ? new Bundle() : new Bundle(arguments);
        super.onCreate(arguments); start();
    }
    @Override public void callActivityOnCreate(android.app.Activity activity,Bundle state){
        super.callActivityOnCreate(activity,state);
        if("responsive_workspace".equals(arguments.getString("check"))&&arguments.containsKey("windowWidthDp")){
            float density=activity.getResources().getDisplayMetrics().density;
            activity.getWindow().setLayout(Math.round(Integer.parseInt(arguments.getString("windowWidthDp"))*density),Math.round(Integer.parseInt(arguments.getString("windowHeightDp"))*density));
        }
    }
    @Override public android.app.Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        android.app.Activity activity = super.newActivity(loader, name, intent);
        if("responsive_workspace".equals(arguments.getString("check"))){
            var config=new android.content.res.Configuration();config.fontScale=Float.parseFloat(arguments.getString("font","1.0"));
            if(arguments.containsKey("windowWidthDp")){config.screenWidthDp=Integer.parseInt(arguments.getString("windowWidthDp"));config.screenHeightDp=Integer.parseInt(arguments.getString("windowHeightDp"));config.smallestScreenWidthDp=Math.min(config.screenWidthDp,config.screenHeightDp);}
            activity.applyOverrideConfiguration(config);
        }
        if (("capsule_settings".equals(arguments.getString("check"))
                && NotificationSettingsActivity.class.getName().equals(name))
                || ("occurrence_updates".equals(arguments.getString("check")) && TaskDetailActivity.class.getName().equals(name))
                || ("compact_detail_atomic".equals(arguments.getString("check"))
                && (TaskDetailActivity.class.getName().equals(name) || CandidateMergeActivity.class.getName().equals(name)))) {
            FixtureIsolation.require(getTargetContext());
            // Per-activity resources only: do not change the user's emulator display/font settings.
            var metrics = getTargetContext().getResources().getDisplayMetrics();
            int width = Integer.parseInt(arguments.getString("widthDp", "400"));
            float font = Float.parseFloat(arguments.getString("font", "1.0"));
            if (width < 320 || width > 600 || font < 1 || font > 2)
                throw new IllegalArgumentException("Invalid settings render configuration");
            var config = new android.content.res.Configuration();
            config.densityDpi = Math.round(metrics.widthPixels * 160f / width);
            config.screenWidthDp = width;
            config.screenHeightDp = Math.round(metrics.heightPixels * 160f / config.densityDpi);
            config.smallestScreenWidthDp = width;
            config.fontScale = font;
            activity.applyOverrideConfiguration(config);
        }
        return activity;
    }
    @Override public void onStart() {
        android.util.Log.i("ChronaDeviceCheck", "onStart phase=" + arguments.getString("phase"));
        if("responsive_workspace".equals(arguments.getString("check"))){
            Bundle report=new Bundle();try{report.putString("responsive_report",ResponsiveWorkspaceDeviceCheck.run(this,arguments));finish(-1,report);}
            catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));finish(0,report);}return;
        }
        if("occurrence_updates".equals(arguments.getString("check"))){
            Bundle report=new Bundle();
            try{report.putString("occurrence_report",OccurrenceUpdateDeviceCheck.run(this,arguments));finish(-1,report);}
            catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));finish(0,report);}
            return;
        }
        if ("compact_detail_atomic".equals(arguments.getString("check"))) {
            Bundle report = new Bundle();
            try { report.putString("compact_report", CompactDetailAtomicCheck.run(this, arguments)); finish(-1, report); }
            catch (Throwable error) { report.putString("error", android.util.Log.getStackTraceString(error)); finish(0, report); }
            return;
        }
        if ("capsule_settings".equals(arguments.getString("check"))) {
            Bundle report = new Bundle();
            try { report.putString("capsule_settings_report", CapsuleSettingsDeviceCheck.run(this, arguments)); finish(-1, report); }
            catch (Throwable error) { report.putString("error", android.util.Log.getStackTraceString(error)); finish(0, report); }
            return;
        }
        if ("classroom_visibility".equals(arguments.getString("check"))) {
            Bundle report=new Bundle();
            try { report.putString("visibility_report",ClassroomVisibilityDeviceCheck.run(this,arguments));finish(-1,report); }
            catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));finish(0,report);}
            return;
        }
        if ("backup_restore".equals(arguments.getString("check"))) {
            Bundle report = new Bundle();
            try { report.putString("backup_report", BackupRestoreDeviceCheck.run(this)); finish(-1, report); }
            catch (Throwable error) { report.putString("error", android.util.Log.getStackTraceString(error)); finish(0, report); }
            return;
        }
        if ("home_calendar_race".equals(arguments.getString("check"))) {
            Bundle report=new Bundle();
            try { report.putString("home_calendar_report",HomeCalendarRefreshDeviceCheck.run(this,arguments)); finish(-1,report); }
            catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));finish(0,report);}
            return;
        }
        if ("homework_diagnostics".equals(arguments.getString("check"))) {
            Bundle report = new Bundle();
            try { report.putString("homework_report", HomeworkPhoneDiagnostics.run(this, arguments)); finish(-1, report); }
            catch (Throwable error) { report.putString("error", android.util.Log.getStackTraceString(error)); finish(0, report); }
            return;
        }
        if ("release".equals(arguments.getString("check"))) {
            Bundle report = new Bundle();
            try { report.putString("release_report", ReleaseDeviceCheck.run(this,arguments)); finish(-1,report); }
            catch(Throwable error) { report.putString("error",android.util.Log.getStackTraceString(error)); finish(0,report); }
            return;
        }
        if ("ui".equals(arguments.getString("check"))) {
            Bundle report = new Bundle();
            try { report.putString("ui_report", DeviceUiChecks.run(this, arguments)); finish(-1, report); }
            catch (Throwable failure) { report.putString("error", android.util.Log.getStackTraceString(failure)); finish(0, report); }
            return;
        }
        Bundle result = new Bundle();
        try { check(); result.putString("stream", "LAN integration: PASS (real Android SQLite and HTTP)\n"); finish(-1, result); }
        catch (Throwable error) { result.putString("stream", "LAN integration: FAIL " + error.getClass().getSimpleName() + ": " + error.getMessage()+"\n"); finish(0, result); }
    }
    private String cookie, csrf, lastSetCookie;
    private int port;
    private boolean providerCompletion;
    private void check() throws Exception {
        Context target = getTargetContext();
        if ("linked_calendar".equals(arguments.getString("check"))) FixtureIsolation.require(target);
        File directory = new File(target.getCacheDir(), "lan-integration-" + UUID.randomUUID());
        if (!directory.mkdirs()) throw new IOException("Cannot create isolated test directory");
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return directory; }
            @Override public File getDatabasePath(String name) { return new File(directory,name); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(),factory,handler); }
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode) { return super.getSharedPreferences("lan-test-"+directory.getName()+"-"+name,mode); }
        };
        try(ServerSocket reserve = new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { port=reserve.getLocalPort(); }
        java.util.concurrent.atomic.AtomicInteger approvalChanges = new java.util.concurrent.atomic.AtomicInteger();
        LanWebServer server = new LanWebServer(isolated,"127.0.0.1",port,approvalChanges::incrementAndGet);
        try {
            server.start(3000,false);
            expect(request("GET","/api/inbox",null,null),401);
            expect(request("GET","/api/attachment/1/image-0",null,null),401);
            checkApprovalProtocol(server,approvalChanges);
            expect(request("POST","/api/pair",new JSONObject().put("code",server.security.pairing()),"http://evil.invalid"),403);
            expect(request("POST","/api/pair",new JSONObject().put("code",server.security.pairing()),origin()),200);
            Reply session=request("GET","/api/session",null,null);expect(session,200);csrf=session.body.getString("csrf");
            Reply created=request("POST","/api/task/create",new JSONObject().put("text","LAN test original").put("process",false),origin());expect(created,200);long id=created.body.getLong("id");
            try(TaskStore store=new TaskStore(isolated)){require(store.getTask(id).rawText.equals("LAN test original"),"HTTP must persist into TaskStore");}
            Reply detail=request("GET","/api/task?id="+id,null,null);long revision=detail.body.getLong("revision");
            JSONObject edit=new JSONObject().put("id",Long.toString(id)).put("revision",revision).put("text","edited");
            String saved=csrf;csrf="wrong";expect(request("POST","/api/task/edit",edit,origin()),403);csrf=saved;
            expect(request("POST","/api/task/edit",edit,"http://evil.invalid"),403);
            expect(request("POST","/api/task/edit",edit,origin()),200);
            expect(request("POST","/api/task/edit",edit,origin()),409);
            detail=request("GET","/api/task?id="+id,null,null);revision=detail.body.getLong("revision");require(detail.body.getJSONObject("task").getString("text").equals("edited"),"read after write");
            JSONObject candidate=new JSONObject().put("id",Long.toString(id)).put("revision",revision).put("title","Meeting").put("start",1791072000000L).put("end",1791075600000L).put("zone","Asia/Shanghai").put("allDay",false).put("category","event").put("location","").put("description","").put("reminder",JSONObject.NULL).put("needsConfirmation",false);
            expect(request("POST","/api/candidate/create",candidate,origin()),200);
            detail=request("GET","/api/task?id="+id,null,null);revision=detail.body.getLong("revision");long candidateId=detail.body.getJSONArray("candidates").getJSONObject(0).getLong("id");candidate.put("candidateId",Long.toString(candidateId)).put("revision",revision).put("end",1);
            expect(request("POST","/api/candidate/save",candidate,origin()),400);
            try(TaskStore store=new TaskStore(isolated)){require(store.getCandidates(id).get(0).endAtMillis==1791075600000L,"invalid edit leaves row unchanged");}
            long memoId, todoId;
            try(TaskStore store=new TaskStore(isolated)){
                memoId=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,id,"原记录",null,null,"Asia/Shanghai","","原正文",null,false,null,"event",false,false,0,null,null,true));
                todoId=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,id,"未安排待办",null,null,"Asia/Shanghai","","",null,false,null,"task",false,false,0,null,null,false));
            }
            revision=request("GET","/api/task?id="+id,null,null).body.getLong("revision");
            JSONObject memo=new JSONObject().put("id",Long.toString(id)).put("candidateId",Long.toString(memoId))
                    .put("revision",revision).put("title","独立可见标题").put("description","修改后的正文").put("record",true);
            expect(request("POST","/api/candidate/save",memo,origin()),400);
            require(request("GET","/api/task?id="+id,null,null).body.getJSONArray("candidates").length()==2,
                    "retired records omitted from detail results");
            try(TaskStore store=new TaskStore(isolated)){
                var row=store.getCandidatesByIds(java.util.List.of(memoId)).get(0);
                require(row.isRecord&&row.title.equals("原记录")&&row.description.equals("原正文"),"retired record remains untouched");
                require(store.getTask(id).rawText.equals("edited"),"original capture remains available");
                revision=store.dataRevision();
            }
            JSONObject completion=new JSONObject().put("id",Long.toString(id)).put("candidateId",Long.toString(todoId))
                    .put("revision",revision).put("completed",true);
            expect(request("POST","/api/candidate/complete",completion,origin()),200);
            expect(request("POST","/api/candidate/complete",completion,origin()),409);
            try(TaskStore store=new TaskStore(isolated)){
                require(store.getCandidatesByIds(java.util.List.of(todoId)).get(0).completed(),"HTTP completion persisted");
                revision=store.dataRevision();
            }
            completion.put("completed",false).put("revision",revision);
            expect(request("POST","/api/candidate/complete",completion,origin()),200);
            try(TaskStore store=new TaskStore(isolated)){require(!store.getCandidatesByIds(java.util.List.of(todoId)).get(0).completed(),"HTTP restore persisted");revision=store.dataRevision();}
            JSONObject extra=new JSONObject(candidate.toString()).put("revision",revision).put("title","Another event")
                    .put("start",System.currentTimeMillis()+3_600_000L).put("end",System.currentTimeMillis()+7_200_000L);
            expect(request("POST","/api/candidate/create",extra,origin()),200);
            detail=request("GET","/api/task?id="+id,null,null);revision=detail.body.getLong("revision");
            require(detail.body.getJSONArray("candidates").length()==3,"append retains active schedule results");
            long extraId=detail.body.getJSONArray("candidates").getJSONObject(2).getLong("id");
            JSONObject acknowledgement=new JSONObject().put("id",Long.toString(id)).put("candidateId",Long.toString(extraId))
                    .put("revision",revision).put("acknowledged",true);
            expect(request("POST","/api/candidate/acknowledge",acknowledgement,origin()),200);
            detail=request("GET","/api/task?id="+id,null,null);
            require(detail.body.getJSONArray("candidates").getJSONObject(2).getBoolean("acknowledged"),"event acknowledgement visible through HTTP");
            expect(request("POST","/api/candidate/acknowledge",acknowledgement.put("acknowledged",false),origin()),200);
            expect(request("GET","/api/schedule?tab=1&category=task&publication=3&todo=3",null,null),200);
            Reply unscheduled=request("GET","/api/schedule?tab=1&category=task&publication=3&todo=3",null,null);
            require(unscheduled.body.getJSONArray("items").length()==1&&unscheduled.body.getJSONArray("items").getJSONObject(0).getLong("id")==todoId,"shared pending task filters");
            expect(request("GET","/api/schedule?tab=3&from=2026-10-02&until=2026-10-01",null,null),400);
            expect(request("GET","/api/inbox?category=record",null,null),400);
            expect(request("GET","/api/agenda?from=2026-10-01&until=2026-11-01",null,null),200);
            expect(request("GET","/api/agenda?from=2026-10-01&until=2028-11-01",null,null),400);
            expect(request("POST","/api/calendar/publish-selected",new JSONObject().put("revision",revision-1)
                    .put("ids",new org.json.JSONArray().put(Long.toString(todoId))).put("inbox",false),origin()),409);
            com.donglan.chrona.calendar.CalendarStore calendar = new com.donglan.chrona.calendar.CalendarStore(isolated);
            if (calendar.hasReadPermission() && calendar.hasWritePermission()) {
                long start=System.currentTimeMillis()+7_200_000L;
                long event=calendar.insertEvent(new com.donglan.chrona.calendar.CalendarStore.EventInput(
                        "Chrona 回归检查 · 临时事项",start,start+60000,"Asia/Shanghai",false,"自动验收后移除","",java.util.List.of(15,30)));
                try {
                    long linked;
                    try(TaskStore store=new TaskStore(isolated)){
                        linked=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,id,"临时待办",start,null,"Asia/Shanghai","","",15,false,event,"task",false,false,0,start,null,false));
                    }
                    require(TaskCompletion.set(isolated,linked,id,true)==null,"provider completion sync");
                    require(calendar.getEvent(event)!=null&&calendar.getEvent(event).reminderMinutes.isEmpty(),"completed event history retained and alerts cleared");
                    require(TaskCompletion.set(isolated,linked,id,false)==null,"provider restore sync");
                    require(calendar.getEvent(event).reminderMinutes.equals(java.util.List.of(15,30)),"future original alerts restored");
                    providerCompletion=true;
                } finally { require(calendar.deleteEvent(event),"temporary provider fixture removed"); }
                try(TaskStore store=new TaskStore(isolated)){revision=store.dataRevision();}
            }
            expect(request("GET","/../chrona.db",null,null),404);
            expect(request("POST","/api/ics",new JSONObject(),origin()),405);
            expect(request("POST","/api/task/delete",new JSONObject().put("id",Long.toString(id)).put("revision",revision),origin()),200);
            try(TaskStore store=new TaskStore(isolated)){require(store.getTask(id)==null,"delete must remove real row");require(store.getCandidates(id).isEmpty(),"candidate cascade");}
            checkRepeatAndOrder(isolated);
            checkDefaultExecution(isolated);
            if ("linked_calendar".equals(arguments.getString("check"))) checkLinkedCalendar(isolated);
            expect(request("POST","/api/logout",new JSONObject(),origin()),200);expect(request("GET","/api/inbox",null,null),401);
            server.closeAccess();require(!server.security.authenticated(cookie),"closing revokes connections");
        } finally { server.closeAccess(); delete(directory); }
    }
    private void checkApprovalProtocol(LanWebServer server,java.util.concurrent.atomic.AtomicInteger changes) throws Exception {
        expect(request("POST","/api/approval/request",new JSONObject(),null),403);
        expect(request("POST","/api/approval/request",new JSONObject(),"http://evil.invalid"),403);
        Reply created=request("POST","/api/approval/request",new JSONObject(),origin());expect(created,200);
        String id=created.body.getString("id");JSONObject handle=new JSONObject().put("id",id);
        require(id.matches("[0-9a-f]{64}")&&created.body.getString("verification").matches("[0-9]{4}"),"HTTP returns one pending handle and comparison digits");
        require(lastSetCookie==null&&cookie==null&&changes.get()==1,"request alone grants no session and notifies once");
        expect(request("GET","/api/session",null,null),401);
        require(request("POST","/api/approval/poll",handle,origin()).body.getString("state").equals("pending"),"HTTP poll waits for native decision");
        JSONObject missing=new JSONObject().put("id","0".repeat(64));
        for(int i=0;i<10;i++) {
            expect(request("POST","/api/approval/cancel",missing,origin()),200);
            require(request("POST","/api/approval/poll",missing,origin()).body.getString("state").equals("expired"),"unknown handle is unavailable");
        }
        require(changes.get()==1,"invalid poll/cancel cannot flood the main-thread notifier");
        require(server.security.decideApproval(id,true),"native decision accepts pending HTTP request");
        Reply accepted=request("POST","/api/approval/poll",handle,origin());expect(accepted,200);
        require(accepted.body.getString("state").equals("approved")&&lastSetCookie.contains("HttpOnly")&&lastSetCookie.contains("SameSite=Strict"),"approval claims protected cookie");
        require(changes.get()==2,"consumed approval notifies exactly once");
        Reply session=request("GET","/api/session",null,null);expect(session,200);csrf=session.body.getString("csrf");
        require(request("POST","/api/approval/poll",handle,origin()).body.getString("state").equals("expired")&&lastSetCookie==null&&changes.get()==2,"claim replay issues no cookie or callback");
        expect(request("POST","/api/logout",new JSONObject(),origin()),200);cookie=null;csrf=null;
        created=request("POST","/api/approval/request",new JSONObject(),origin());expect(created,200);
        handle=new JSONObject().put("id",created.body.getString("id"));
        expect(request("POST","/api/approval/cancel",handle,origin()),200);int cancelled=changes.get();
        expect(request("POST","/api/approval/cancel",handle,origin()),200);
        require(changes.get()==cancelled&&server.security.pendingApproval()==null,"real cancel updates once, repeat is inert");
        created=request("POST","/api/approval/request",new JSONObject(),origin());expect(created,200);
        id=created.body.getString("id");handle=new JSONObject().put("id",id);require(server.security.decideApproval(id,false),"native rejection");
        require(request("POST","/api/approval/poll",handle,origin()).body.getString("state").equals("denied")&&cookie==null,"rejection never grants cookie");
        expect(request("GET","/api/session",null,null),401);
    }
    private void checkLinkedCalendar(Context context) throws Exception {
        var calendar=new com.donglan.chrona.calendar.CalendarStore(context);
        require(calendar.hasReadPermission()&&calendar.hasWritePermission(),"linked-calendar check needs provider permission");
        String title="shared-check-"+UUID.randomUUID();long at=System.currentTimeMillis()+7_200_000L;
        java.util.List<com.donglan.chrona.calendar.CalendarStore.EventInput> fixtures=new java.util.ArrayList<>();
        var input=new com.donglan.chrona.calendar.CalendarStore.EventInput(title,at,at+60000,"UTC",false,"provider notes","room",java.util.List.of(20));
        fixtures.add(input);long event=calendar.insertEvent(input);long capture,other,a,b,c,d;
        try {
            try(TaskStore store=new TaskStore(context)) {
                capture=store.insertTask(title,null,"linked-calendar-check",System.currentTimeMillis());
                other=store.insertTask(title+" other",null,"linked-calendar-check",System.currentTimeMillis());
                store.updateStatus(capture,com.donglan.chrona.data.TaskRecord.READY,null);store.updateStatus(other,com.donglan.chrona.data.TaskRecord.READY,null);
                a=linkedCandidate(store,capture,title,event,at);b=linkedCandidate(store,capture,title,event,at);
                c=linkedCandidate(store,capture,title,event,at);d=linkedCandidate(store,other,title,event,at);
            }
            JSONObject edit=new JSONObject().put("id",Long.toString(capture)).put("candidateId",Long.toString(a))
                    .put("title",title+" edited").put("start",at).put("end",at+60000).put("zone","UTC")
                    .put("allDay",false).put("category","task").put("location","room").put("description","changed")
                    .put("reminder",20).put("needsConfirmation",false).put("due",JSONObject.NULL);
            var editedInput=new com.donglan.chrona.calendar.CalendarStore.EventInput(title+" edited",at,at+60000,"UTC",false,"changed","room",java.util.List.of(20));fixtures.add(editedInput);
            expect(request("POST","/api/candidate/save",revision(context,edit),origin()),200);
            long split;
            try(TaskStore store=new TaskStore(context)) {
                split=store.getCandidatesByIds(java.util.List.of(a)).get(0).calendarEventId;
                require(split!=event&&store.getCandidatesByIds(java.util.List.of(b)).get(0).calendarEventId==event,"HTTP edit isolates shared event");
            }
            require(calendar.getEvent(event).title.equals(title)&&calendar.getEvent(split).title.equals(title+" edited"),"real provider untouched sibling title");
            JSONObject remove=new JSONObject().put("id",Long.toString(capture)).put("candidateId",Long.toString(b));
            expect(request("POST","/api/candidate/delete",revision(context,remove),origin()),200);
            require(calendar.getEvent(event)!=null,"deleting one shared candidate keeps provider event");
            JSONObject complete=new JSONObject().put("id",Long.toString(capture)).put("candidateId",Long.toString(c)).put("completed",true);
            expect(request("POST","/api/candidate/complete",revision(context,complete),origin()),200);
            try(TaskStore store=new TaskStore(context)) {
                long completed=store.getCandidatesByIds(java.util.List.of(c)).get(0).calendarEventId;
                require(completed!=event&&calendar.getEvent(completed).reminderMinutes.isEmpty(),"completion owns independent silent event");
            }
            require(calendar.getEvent(event).reminderMinutes.equals(java.util.List.of(20)),"other task retains provider reminder");
            edit.put("start",JSONObject.NULL).put("end",JSONObject.NULL);
            expect(request("POST","/api/candidate/save",revision(context,edit),origin()),200);
            try(TaskStore store=new TaskStore(context)){require(store.getCandidatesByIds(java.util.List.of(a)).get(0).calendarEventId==null,"undated edit detaches only owner");}
            require(calendar.getEvent(split)==null&&calendar.getEvent(event)!=null,"detached unique event removed; shared sibling retained");
            expect(request("POST","/api/task/delete",revision(context,new JSONObject().put("id",Long.toString(capture))),origin()),200);
            require(calendar.getEvent(event)!=null,"task deletion preserves another task owner");
            expect(request("POST","/api/task/delete",revision(context,new JSONObject().put("id",Long.toString(other))),origin()),200);
            require(calendar.getEvent(event)==null,"last owner deletion removes provider event");

            // Fail at the outer SQL COMMIT, after provider insertion and status/link writes succeeded.
            long failureTask,failureCandidate;
            try(TaskStore store=new TaskStore(context)) {
                failureTask=store.insertTask(title+" failure",null,"linked-calendar-check",System.currentTimeMillis());
                store.updateStatus(failureTask,com.donglan.chrona.data.TaskRecord.READY,null);
                failureCandidate=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,failureTask,title+" failure",at,at+60000,"UTC","","",null,false,null,"event",false));
                var db=store.getWritableDatabase();db.execSQL("CREATE TABLE linked_guard_parent(id INTEGER PRIMARY KEY)");
                db.execSQL("CREATE TABLE linked_guard_child(id INTEGER REFERENCES linked_guard_parent(id) DEFERRABLE INITIALLY DEFERRED)");
                db.execSQL("CREATE TRIGGER linked_commit_failure AFTER UPDATE OF status ON tasks WHEN NEW.id="+failureTask+" BEGIN INSERT INTO linked_guard_child VALUES(1); END");
            }
            var failureInput=new com.donglan.chrona.calendar.CalendarStore.EventInput(title+" failure",at,at+60000,"UTC",false,"","",java.util.List.of());fixtures.add(failureInput);
            JSONObject publish=new JSONObject().put("id",Long.toString(failureTask)).put("candidateId",Long.toString(failureCandidate))
                    .put("title",title+" failure").put("start",at).put("end",at+60000).put("zone","UTC").put("allDay",false)
                    .put("category","event").put("location","").put("description","").put("reminder",JSONObject.NULL).put("needsConfirmation",false);
            expect(request("POST","/api/candidate/publish",revision(context,publish),origin()),500);
            require(calendar.findMatchingEvents(failureInput).isEmpty(),"outer commit failure removes inserted provider event");
            try(TaskStore store=new TaskStore(context)) {
                require(store.getCandidatesByIds(java.util.List.of(failureCandidate)).get(0).calendarEventId==null,"failed commit leaves draft unlinked");
                store.getWritableDatabase().execSQL("DROP TRIGGER linked_commit_failure");
            }
            expect(request("POST","/api/candidate/publish",revision(context,publish),origin()),200);
            require(calendar.findMatchingEvents(failureInput).size()==1,"same request retries without orphan duplicate");
            expect(request("POST","/api/task/delete",revision(context,new JSONObject().put("id",Long.toString(failureTask))),origin()),200);
        } finally {
            java.util.Set<Long> removeEvents=new java.util.HashSet<>();
            try(TaskStore store=new TaskStore(context)) {
                store.getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS linked_commit_failure");
                try(android.database.Cursor rows=store.getReadableDatabase().query("tasks",new String[]{"id"},"source=?",new String[]{"linked-calendar-check"},null,null,null)) {
                    while(rows.moveToNext()){long id=rows.getLong(0);for(var item:store.getCandidates(id))if(item.calendarEventId!=null)removeEvents.add(item.calendarEventId);store.deleteTask(id);}
                }
            }
            for(var fixture:fixtures)removeEvents.addAll(calendar.findMatchingEvents(fixture));
            for(long id:removeEvents)calendar.deleteEvent(id);
        }
    }
    private JSONObject revision(Context context,JSONObject body)throws Exception {try(TaskStore store=new TaskStore(context)){return body.put("revision",store.dataRevision());}}
    private static long linkedCandidate(TaskStore store,long task,String title,long event,long at){return store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,task,title,at,at+60000,"UTC","room","provider notes",20,false,event,"task",false));}

    private void checkDefaultExecution(Context context) throws Exception {
        long capture,candidate;
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();java.time.LocalDate day=java.time.LocalDate.now(zone).plusDays(1);
        long due=day.atTime(0,10).atZone(zone).toInstant().toEpochMilli();
        try(TaskStore store=new TaskStore(context)) {
            capture=store.insertTask("due-only integration",null,"integration",System.currentTimeMillis());
            store.updateStatus(capture,com.donglan.chrona.data.TaskRecord.READY,null);
            candidate=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,capture,"默认半小时",null,null,zone.getId(),"","",null,false,null,"task",false,false,0,due,null,false));
        }
        JSONObject detail=request("GET","/api/task?id="+capture,null,null).body.getJSONArray("candidates").getJSONObject(0);
        require(detail.isNull("start")&&detail.isNull("end")&&detail.getLong("executionStart")==due-1800000L&&detail.getLong("executionEnd")==due&&!detail.getBoolean("executionAllDay"),"HTTP separates raw editor values from default display interval");
        Reply previous=request("GET","/api/schedule?tab=3&from="+day.minusDays(1)+"&until="+day,null,null);expect(previous,200);
        boolean found=false;for(int i=0;i<previous.body.getJSONArray("items").length();i++)if(previous.body.getJSONArray("items").getJSONObject(i).getLong("id")==candidate)found=true;
        require(found,"HTTP date filter includes cross-midnight default start");
        try(TaskStore store=new TaskStore(context)) { require(store.getCandidates(capture).get(0).startAtMillis==null&&store.getCandidates(capture).get(0).endAtMillis==null,"HTTP reads never persist display defaults");store.deleteTask(capture); }
    }
    private void checkRepeatAndOrder(Context context) throws Exception {
        long taskId, candidateId;
        java.time.ZoneId zone=java.time.ZoneId.of("Asia/Shanghai");
        java.time.LocalDate date=java.time.LocalDate.now(zone).plusDays(1);
        long first=date.atTime(9,0).atZone(zone).toInstant().toEpochMilli();
        try(TaskStore store=new TaskStore(context)) {
            taskId=store.insertTask("LAN repeat fixture",null,"integration",System.currentTimeMillis());
            store.updateStatus(taskId,com.donglan.chrona.data.TaskRecord.READY,null);
            candidateId=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,taskId,"Repeat fixture",first,first+3600000,
                    zone.getId(),"","",null,false,null,"event",false));
            store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,taskId,"Later fixture",first+86400000,first+90000000,
                    zone.getId(),"","",null,false,null,"event",false));
        }
        Reply ascending=request("GET","/api/schedule?tab=3&oldestFirst=true",null,null);
        Reply descending=request("GET","/api/schedule?tab=3&oldestFirst=false",null,null);
        expect(ascending,200);expect(descending,200);
        require(ascending.body.getJSONArray("items").getJSONObject(0).getLong("id")==candidateId,"real SQL ascending order");
        require(descending.body.getJSONArray("items").getJSONObject(1).getLong("id")==candidateId,"real SQL descending order");
        JSONObject single=request("GET","/api/task?id="+taskId,null,null).body.getJSONArray("candidates").getJSONObject(0);
        JSONObject change=new JSONObject().put("id",Long.toString(taskId)).put("candidateId",Long.toString(candidateId))
                .put("revision",ascending.body.getLong("revision")).put("baseline",single.getString("baseline")).put("action","rule")
                .put("rule",new com.donglan.chrona.data.RepeatRule("daily",1,java.util.List.of(),null,3).toJson());
        Reply changed=request("POST","/api/candidate/repeat",change,origin());expect(changed,200);
        expect(request("POST","/api/candidate/repeat",change,origin()),409);
        String path="/api/candidate/occurrences?id="+taskId+"&candidateId="+candidateId+"&from="+date+"&until="+date.plusDays(8);
        Reply occurrences=request("GET",path,null,null);expect(occurrences,200);
        require(occurrences.body.getJSONArray("items").length()==3,"HTTP expands three instances");
        change.remove("rule");change.put("revision",changed.body.getLong("revision"))
                .put("baseline",changed.body.getJSONObject("candidate").getString("baseline")).put("action","skip").put("original",first);
        changed=request("POST","/api/candidate/repeat",change,origin());expect(changed,200);
        require(request("GET",path,null,null).body.getJSONArray("items").length()==2,"HTTP skip persists");
        change.put("revision",changed.body.getLong("revision")).put("baseline",changed.body.getJSONObject("candidate").getString("baseline"))
                .put("action","move").put("original",first+86400000).put("moved",first+5*86400000L);
        changed=request("POST","/api/candidate/repeat",change,origin());expect(changed,200);
        JSONObject moved=request("GET","/api/task?id="+taskId+"&candidate_id="+candidateId+"&occurrenceAt="+(first+86400000),null,null)
                .body.getJSONArray("candidates").getJSONObject(0);
        require(moved.getLong("occurrenceAt")==first+86400000 && moved.getLong("start")==first+5*86400000L,"HTTP moved identity");
        change.put("revision",changed.body.getLong("revision")).put("baseline",changed.body.getJSONObject("candidate").getString("baseline")).put("action","stop");
        changed=request("POST","/api/candidate/repeat",change,origin());expect(changed,200);
        require(request("GET",path,null,null).body.getJSONArray("items").length()==0,"HTTP stop removes future instances");
        expect(request("POST","/api/selection/delete",new JSONObject().put("revision",changed.body.getLong("revision"))
                .put("inbox",true).put("ids",new org.json.JSONArray().put(Long.toString(taskId))),origin()),200);
        try(TaskStore store=new TaskStore(context)){require(store.getTask(taskId)==null,"HTTP batch delete persists");}
    }
    private String origin(){return "http://127.0.0.1:"+port;}
    private Reply request(String method,String path,JSONObject body,String origin)throws Exception{
        lastSetCookie=null;
        // Raw loopback sockets avoid changing the release APK's outbound cleartext policy.
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress("127.0.0.1",port),5000);socket.setSoTimeout(5000);
            byte[] bytes=body==null?new byte[0]:body.toString().getBytes(StandardCharsets.UTF_8);
            StringBuilder headers=new StringBuilder(method+" "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nConnection: close\r\n");
            if(cookie!=null)headers.append("Cookie: ").append(cookie).append("\r\n");
            if(csrf!=null)headers.append("X-Chrona-CSRF: ").append(csrf).append("\r\n");
            if(origin!=null)headers.append("Origin: ").append(origin).append("\r\n");
            if(body!=null)headers.append("Content-Type: application/json\r\nContent-Length: ").append(bytes.length).append("\r\n");
            headers.append("\r\n");OutputStream output=socket.getOutputStream();output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));output.write(bytes);output.flush();
            BufferedInputStream input=new BufferedInputStream(socket.getInputStream());int status=Integer.parseInt(line(input).split(" ")[1]);int length=-1;String header;
            while(!(header=line(input)).isEmpty()){
                if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());
                if(header.toLowerCase(java.util.Locale.ROOT).startsWith("set-cookie:")){lastSetCookie=header;cookie=header.substring(11).trim().split(";",2)[0];}
            }
            if(length<0||length>1024*1024)throw new IOException("Invalid test response length");
            byte[] response=new byte[length];int offset=0;while(offset<length){int count=input.read(response,offset,length-offset);if(count<0)throw new IOException("Truncated response");offset+=count;}
            return new Reply(status,new JSONObject(new String(response,StandardCharsets.UTF_8)));
        }
    }
    private static String line(InputStream input)throws IOException{ByteArrayOutputStream value=new ByteArrayOutputStream();int next;while((next=input.read())!=-1&&next!='\n'){if(next!='\r')value.write(next);if(value.size()>8192)throw new IOException("Header too long");}if(next==-1)throw new EOFException();return value.toString("US-ASCII");}
    private static final class Reply{final int status;final JSONObject body;Reply(int status,JSONObject body){this.status=status;this.body=body;}}
    private static void expect(Reply reply,int code){require(reply.status==code,"expected HTTP "+code+" got "+reply.status);}
    private static void require(boolean result,String label){if(!result)throw new AssertionError(label);}
    private static void delete(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);}if(!file.delete())file.deleteOnExit();}
}
