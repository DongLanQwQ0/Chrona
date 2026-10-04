package com.donglan.chrona;

import android.content.Context;
import android.net.Uri;
import com.donglan.chrona.calendar.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.processing.ProcessingJobService;
import com.donglan.chrona.processing.StreamingOutputStore;
import com.donglan.chrona.timetable.Timetable;
import fi.iki.elonen.NanoHTTPD;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** HTTP framing belongs to NanoHTTPD; authorization precedes every data/file lookup. */
final class LanWebServer extends NanoHTTPD {
    private static final int MAX_BODY = 1024 * 1024;
    private static final long MAX_DATE_MILLIS = 4102444800000L; // 2100-01-01 UTC
    final LanSecurity security;
    private final Context context;
    private volatile boolean closed;
    LanWebServer(Context context, String address, int port) {
        super(address, port); this.context = context.getApplicationContext(); security = new LanSecurity(address + ":" + port);
        setAsyncRunner(new BoundedRunner());
    }
    void closeAccess() { closed = true; security.close(); stop(); }
    @Override public Response serve(IHTTPSession request) {
        Response response;
        try { response = dispatch(request); }
        catch (Conflict error) { response = json(Response.Status.CONFLICT, "数据已变化，请刷新后重试"); }
        catch (IllegalArgumentException | JSONException | java.time.DateTimeException | java.nio.charset.CharacterCodingException error) { response = json(Response.Status.BAD_REQUEST, "输入格式或范围无效"); }
        catch (SecurityException error) { response = json(Response.Status.FORBIDDEN, "请先在手机授予日历或附件权限"); }
        catch (Exception error) { response = json(Response.Status.INTERNAL_ERROR, "操作未完成，请检查手机状态后重试"); }
        response.addHeader("Cache-Control", "no-store"); response.addHeader("X-Content-Type-Options", "nosniff");
        // Rejected POST bodies are intentionally not consumed. Never reuse their connection.
        response.closeConnection(true);
        response.addHeader("Referrer-Policy", "no-referrer"); response.addHeader("X-Frame-Options", "DENY");
        response.addHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        return response;
    }
    private Response dispatch(IHTTPSession request) throws Exception {
        if (closed) return json(Response.Status.SERVICE_UNAVAILABLE, "局域网访问已关闭");
        Map<String,String> headers = request.getHeaders();
        if (!security.host(headers.get("host"))) return json(Response.Status.FORBIDDEN, "访问地址无效");
        String origin = headers.get("origin");
        if (origin != null && !security.origin(origin)) return json(Response.Status.FORBIDDEN, "来源无效");
        String fetchSite = headers.get("sec-fetch-site");
        if (fetchSite != null && !(fetchSite.equals("same-origin") || fetchSite.equals("none"))) return json(Response.Status.FORBIDDEN, "来源无效");
        String path = request.getUri(); Method method = request.getMethod();
        if (method != Method.GET && method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, "请求方法无效");
        if (method == Method.GET && (path.equals("/") || path.equals("/app.js") || path.equals("/style.css"))) {
            String name = path.equals("/") ? "index.html" : path.substring(1);
            return newChunkedResponse(Response.Status.OK, path.endsWith(".js") ? "text/javascript; charset=utf-8" : path.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8", context.getAssets().open("lan/" + name));
        }
        if (method == Method.POST && path.equals("/api/pair")) {
            if (!security.origin(origin)) return json(Response.Status.FORBIDDEN, "来源无效");
            JSONObject body = body(request, 256);
            String token = security.pair(request.getRemoteIpAddress(), string(body, "code", 64, true));
            if (token == null) return json(Response.Status.TOO_MANY_REQUESTS, "配对失败，请核对配对码；连续尝试后请等待一分钟");
            Response result = data(new JSONObject().put("ok", true));
            result.addHeader("Set-Cookie", "chrona_session=" + token + "; HttpOnly; SameSite=Strict; Path=/"); return result;
        }
        if (!security.authenticated(headers.get("cookie"))) return json(Response.Status.UNAUTHORIZED, "请在手机开启局域网访问并配对");
        if (method == Method.POST && !security.write(origin, headers.get("x-chrona-csrf"))) return json(Response.Status.FORBIDDEN, "连接验证失败，请重新配对");
        if (method == Method.GET && path.equals("/api/session")) {
            UiStyle.Palette colors = UiStyle.colors(context);
            return data(new JSONObject().put("csrf", security.csrf()).put("zone", ZoneId.systemDefault().getId())
                    .put("palette", new JSONObject().put("background", hex(colors.background)).put("text", hex(colors.text)).put("muted", hex(colors.muted)).put("primary", hex(colors.primary)).put("surface", hex(colors.surface)).put("outline", hex(colors.outline)))
                    .put("calendarAllowed", new CalendarStore(context).hasWritePermission()).put("calendarReadable",new CalendarStore(context).hasReadPermission())
                    .put("systemCalendarIncluded",HomeTimelinePreferences.includesSystemCalendar(context)).put("syncStatus", new WebDavSettingsStore(context).status()));
        }
        if (method == Method.POST && path.equals("/api/logout")) { security.logout(headers.get("cookie")); Response result = data(new JSONObject().put("ok",true)); result.addHeader("Set-Cookie", "chrona_session=; Max-Age=0; HttpOnly; SameSite=Strict; Path=/"); return result; }
        // Sync apply and browser mutations must not interleave their database/file snapshots.
        synchronized (AndroidSync.LOCK) {
            if (closed) return json(Response.Status.SERVICE_UNAVAILABLE, "局域网访问已关闭");
            if(method==Method.POST&&path.equals("/api/preferences")){
                JSONObject body=body(request,256);boolean included=bool(body,"systemCalendarIncluded");
                if(included&&!new CalendarStore(context).hasReadPermission())return json(Response.Status.FORBIDDEN,"系统日历读取权限需在手机授予");
                HomeTimelinePreferences.setIncludesSystemCalendar(context,included);return data(new JSONObject().put("ok",true).put("systemCalendarIncluded",included));
            }
            if (path.startsWith("/api/attachment/")) return method == Method.GET ? attachment(request) : json(Response.Status.METHOD_NOT_ALLOWED,"请求方法无效");
            if (path.equals("/api/ics") && method != Method.GET) return json(Response.Status.METHOD_NOT_ALLOWED,"请求方法无效");
            if (path.equals("/api/timetable") || path.equals("/api/ics")) return timetable(request);
            try (TaskStore store = new TaskStore(context)) {
                if (method == Method.GET) return read(request, store);
                JSONObject body = body(request, MAX_BODY);
                JSONObject result;
                store.getWritableDatabase().beginTransaction();
                try {
                    if (!path.equals("/api/task/create") && whole(body,"revision",0,Long.MAX_VALUE) != store.dataRevision()) throw new Conflict();
                    result = mutate(path, body, store);
                    store.getWritableDatabase().setTransactionSuccessful();
                } finally { store.getWritableDatabase().endTransaction(); }
                if (new WebDavSettingsStore(context).automatic()) { SyncJobService.schedule(context); AndroidSync.request(context, null); }
                return data(result.put("revision", store.dataRevision()));
            }
        }
    }
    private Response read(IHTTPSession request, TaskStore store) throws Exception {
        String path = request.getUri(); JSONObject result = new JSONObject().put("revision", store.dataRevision());
        if(path.equals("/api/merge/preview")){
            store.getWritableDatabase().beginTransaction();try{
                CandidateMerges.Preview preview=CandidateMerges.scan(store);JSONArray groups=new JSONArray();
                for(List<EventCandidate> group:preview.groups){JSONArray sources=new JSONArray();for(EventCandidate item:group){TaskRecord task=preview.tasks.get(item.taskId);sources.put(new JSONObject().put("taskId",task.id).put("candidateId",item.id).put("source",task.source));}
                    groups.put(new JSONObject().put("candidate",candidate(group.get(0))).put("sources",sources));}
                store.getWritableDatabase().setTransactionSuccessful();
                return data(new JSONObject().put("revision",preview.revision).put("removed",preview.removed()).put("groups",groups));
            }finally{store.getWritableDatabase().endTransaction();}
        }
        if (path.equals("/api/task")) {
            long id = positive(request.getParms().get("id")); TaskRecord record = store.getTask(id);
            if (record == null) return json(Response.Status.NOT_FOUND,"记录已不存在");
            JSONArray candidates = new JSONArray(), images = new JSONArray(), files = new JSONArray();
            for (EventCandidate item : store.getCandidates(id)) candidates.put(candidate(item).put("mergedSources",CandidateMerges.sources(store,item.id)));
            List<String> paths = store.getImagePaths(id); for (int i=0;i<paths.size();i++) images.put(new JSONObject().put("index",i));
            for (TaskFileAttachment item : store.getFileAttachments(id)) files.put(new JSONObject().put("id",item.id).put("name",item.displayName).put("size",item.sizeBytes));
            return data(result.put("task", task(record)).put("candidates",candidates).put("images",images).put("files",files).put("mergedTargets",CandidateMerges.targets(store,id)));
        }
        if (path.equals("/api/inbox")) {
            int page = integer(request.getParms().get("page"), 0, 0, 100000), status = integer(request.getParms().get("status"),0,0,4);
            String search = request.getParms().getOrDefault("search",""); if (search.length()>256) throw new IllegalArgumentException();
            InboxQuery query = new InboxQuery(status,null,search); List<Long> ids=store.queryInboxIds(query,false); JSONArray tasks=new JSONArray();
            for(TaskRecord item:store.queryInboxPage(query,false,page,ids)) tasks.put(task(item));
            return data(result.put("items",tasks).put("total",ids.size()).put("page",page).put("pageSize",InboxQuery.PAGE_SIZE));
        }
        if (path.equals("/api/schedule")) {
            int page=integer(request.getParms().get("page"),0,0,100000); String search=request.getParms().getOrDefault("search",""); if(search.length()>256) throw new IllegalArgumentException();
            String category=request.getParms().get("category"); if(category!=null&&!Arrays.asList(EventCategory.VALUES).contains(category))throw new IllegalArgumentException();
            ScheduleQuery query=new ScheduleQuery(0,category,0,search,null,null,System.currentTimeMillis(),ZoneId.systemDefault());
            JSONArray entries=new JSONArray(); for(EventCandidate item:store.querySchedulePage(query,false,page))entries.put(candidate(item));
            return data(result.put("items",entries).put("total",store.queryScheduleIds(query).size()).put("page",page).put("pageSize",ScheduleQuery.PAGE_SIZE));
        }
        if (path.equals("/api/home")) {
            ZoneId zone=ZoneId.systemDefault(); LocalDate date=LocalDate.now(zone); long start=date.atStartOfDay(zone).toInstant().toEpochMilli(),end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            JSONArray entries=new JSONArray(), calendar=new JSONArray();
            for(EventCandidate item:store.widgetCandidates(start,end,date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))entries.put(candidate(item));
            String calendarState="系统日历需在手机授权"; CalendarStore provider=new CalendarStore(context);
            if(HomeTimelinePreferences.includesSystemCalendar(context)&&provider.hasReadPermission()) {
                try { Set<Long> linked=new HashSet<>(store.linkedCalendarIds()); for(CalendarOccurrence item:provider.listInstances(start,end,null)) if(!linked.contains(item.eventId)) calendar.put(new JSONObject().put("title",item.title).put("location",item.location).put("start",item.displayStart(zone)).put("end",item.displayEnd(zone))); calendarState=""; }
                catch(RuntimeException error){calendarState="系统日历暂时无法读取";}
            } else if(!HomeTimelinePreferences.includesSystemCalendar(context)) calendarState="手机已关闭系统日历时间线";
            return data(result.put("items",entries).put("calendar",calendar).put("calendarState",calendarState).put("date",date.toString()).put("review",store.taskCountByStatus(TaskRecord.NEEDS_REVIEW)).put("failed",store.taskCountByStatus(TaskRecord.FAILED)));
        }
        return json(Response.Status.NOT_FOUND,"页面不存在");
    }
    private JSONObject mutate(String path,JSONObject body,TaskStore store)throws Exception {
        if(path.equals("/api/candidate/merge"))return new JSONObject().put("removed",CandidateMerges.mergeRows(store,whole(body,"revision",0,Long.MAX_VALUE)));
        if(path.equals("/api/task/create")) {
            String raw=string(body,"text",100000,true); long id=store.insertTask(raw,null,"局域网浏览器",System.currentTimeMillis());
            if(bool(body,"process")) enqueue(store,id); else store.updateStatus(id,TaskRecord.READY,null); return new JSONObject().put("id",id);
        }
        long id=positive(body.getString("id")); TaskRecord record=store.getTask(id); if(record==null)throw new Conflict();
        if(path.equals("/api/task/delete")) {
            ProcessingJobService.cancel(context,id); CalendarStore calendar=new CalendarStore(context);
            for(EventCandidate item:store.getCandidates(id)) if(item.calendarEventId!=null)calendar.deleteEvent(item.calendarEventId);
            List<String> images=store.getImagePaths(id); if(!store.deleteTask(id))throw new Conflict();
            new StreamingOutputStore(context).delete(id); for(String name:images)new ImageStore(context).delete(name);
            android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class); if(manager!=null)manager.cancel((int)id);
        } else if(path.equals("/api/task/edit")) {
            editable(record); if(!store.updateRawText(id,string(body,"text",100000,true)))throw new Conflict();
        } else if(path.equals("/api/task/process")) {
            editable(record); for(EventCandidate item:store.getCandidates(id))if(item.calendarEventId!=null)throw new IllegalArgumentException();
            // Schedule first; if Android rejects background work, retain existing results and status.
            store.getWritableDatabase().beginTransaction();
            try { store.replaceCandidates(id,Collections.emptyList()); store.updateStatus(id,TaskRecord.QUEUED,null);
                ProcessingJobService.enqueueRemote(context,id); store.getWritableDatabase().setTransactionSuccessful();
            } finally { store.getWritableDatabase().endTransaction(); }
        } else if(path.equals("/api/candidate/create")) {
            editable(record); if(!store.getCandidates(id).isEmpty())throw new Conflict();
            EventCandidate initial=new EventCandidate(0,id,"",null,null,ZoneId.systemDefault().getId(),"","",null,true);
            EventCandidate entry=edited(body,initial);store.replaceCandidates(id,Collections.singletonList(entry));store.updateStatus(id,entry.needsConfirmation?TaskRecord.NEEDS_REVIEW:TaskRecord.READY,null);
        } else if(path.equals("/api/candidate/save")||path.equals("/api/candidate/publish")||path.equals("/api/candidate/delete")) {
            editable(record); long candidateId=positive(body.getString("candidateId")); EventCandidate old=null;
            for(EventCandidate item:store.getCandidates(id))if(item.id==candidateId)old=item;
            if(old==null)throw new Conflict(); CalendarStore calendar=new CalendarStore(context);
            if(path.endsWith("delete")){if(old.calendarEventId!=null)calendar.deleteEvent(old.calendarEventId); if(!store.deleteCandidate(candidateId,id))throw new Conflict();}
            else {
                EventCandidate edited=edited(body,old); boolean publish=path.endsWith("publish");
                if(publish||old.calendarEventId!=null){
                    CalendarStore.EventInput input=input(edited);
                    if(old.calendarEventId!=null){if(!calendar.updateEvent(old.calendarEventId,input))throw new Conflict();}
                    else { if(!calendar.findMatchingEvents(input).isEmpty())throw new IllegalArgumentException(); long event=calendar.insertEvent(input); if(!store.setCalendarEventIdIfUnlinked(candidateId,id,event)){calendar.deleteEvent(event);throw new Conflict();} edited=new EventCandidate(edited.id,id,edited.title,edited.startAtMillis,edited.endAtMillis,edited.timeZoneId,edited.location,edited.description,edited.reminderMinutesBefore,false,event,edited.category,edited.allDay,false,EventCandidate.CERTAIN); }
                }
                if(!store.updateCandidate(edited))throw new Conflict();
            }
            boolean review=false; for(EventCandidate item:store.getCandidates(id))review|=item.needsConfirmation; store.updateStatus(id,review?TaskRecord.NEEDS_REVIEW:TaskRecord.READY,null);
        } else throw new IllegalArgumentException();
        return new JSONObject().put("ok",true).put("id",id);
    }
    private void enqueue(TaskStore store,long id) {
        try { ProcessingJobService.enqueueRemote(context,id); }
        catch(RuntimeException error){store.updateStatus(id,TaskRecord.FAILED,"手机暂时无法安排 AI 处理，请在手机检查后台运行限制后重试");}
    }
    private static void editable(TaskRecord record){if(TaskRecord.PROCESSING.equals(record.status)||TaskRecord.QUEUED.equals(record.status))throw new Conflict();}
    private static EventCandidate edited(JSONObject body,EventCandidate old)throws Exception {
        String title=string(body,"title",500,true),category=string(body,"category",20,true);
        if(!Arrays.asList(EventCategory.VALUES).contains(category))throw new IllegalArgumentException();
        Long start=nullableLong(body,"start"),end=nullableLong(body,"end"); boolean allDay=bool(body,"allDay");
        if(start==null&&end!=null||start!=null&&end!=null&&end<=start)throw new IllegalArgumentException();
        String zone=allDay?"UTC":string(body,"zone",100,true);ZoneId.of(zone);
        if(allDay&&(start!=null&&start%86400000L!=0||end!=null&&end%86400000L!=0))throw new IllegalArgumentException();
        if(!body.has("reminder"))throw new IllegalArgumentException();
        Integer reminder=body.isNull("reminder")?null:(int)whole(body,"reminder",0,525600);
        boolean needs=start==null||end==null||bool(body,"needsConfirmation");
        return new EventCandidate(old.id,old.taskId,title,start,end,zone,string(body,"location",2000,false),string(body,"description",100000,false),reminder,needs,old.calendarEventId,category,allDay,false,needs?EventCandidate.DOUBTFUL:EventCandidate.CERTAIN);
    }
    private static CalendarStore.EventInput input(EventCandidate item){if(item.startAtMillis==null||item.endAtMillis==null||item.needsConfirmation)throw new IllegalArgumentException();return new CalendarStore.EventInput(item.title,item.startAtMillis,item.endAtMillis,item.timeZoneId,item.allDay,item.description,item.location,item.reminderMinutesBefore==null?Collections.emptyList():Collections.singletonList(item.reminderMinutesBefore));}
    private Response timetable(IHTTPSession request)throws Exception {
        TimetableStore store=new TimetableStore(context);long capturedRevision=TimetableStore.revision();TimetableStore.Library library=store.load();
        if(request.getMethod()==Method.POST){
            String expected=store.snapshot();if(capturedRevision!=TimetableStore.revision())throw new Conflict();
            JSONObject body=body(request,MAX_BODY);if(whole(body,"revision",0,Long.MAX_VALUE)!=capturedRevision)throw new Conflict();
            String action=string(body,"action",20,true);
            if(action.equals("import")||action.equals("preview")){
                TimetableStore.Document incoming=TimetableStore.prepare(string(body,"name",160,true),string(body,"source",MAX_BODY,true),ZoneId.systemDefault());
                if(action.equals("preview")){
                    JSONArray replaced=new JSONArray();
                    if(library!=null)for(TimetableStore.Semester term:library.semesters)for(int index:incoming.enabled)if(term.id().equals(incoming.plan.id(index)))replaced.put(term.id());
                    if(capturedRevision!=TimetableStore.revision())throw new Conflict();
                    return data(new JSONObject().put("replaced",replaced).put("revision",capturedRevision));
                }
                if(library==null)library=new TimetableStore.Library(Collections.emptyList(),""); library=library.merge(incoming);
            }
            else {String id=string(body,"term",160,true);TimetableStore.Semester selected=null;if(library!=null)for(TimetableStore.Semester term:library.semesters)if(term.id().equals(id))selected=term;if(selected==null)throw new Conflict();if(action.equals("select"))library=library.select(id);else if(action.equals("delete"))library=library.remove(selected);else throw new IllegalArgumentException();}
            if(!store.compareAndRestore(expected,TimetableStore.encode(library)))throw new Conflict();
            if(new WebDavSettingsStore(context).automatic()){SyncJobService.schedule(context);AndroidSync.request(context,null);}return data(new JSONObject().put("ok",true));
        }
        if(request.getUri().equals("/api/ics")){
            String id=request.getParms().get("term");if(library!=null)for(TimetableStore.Semester term:library.semesters)if(term.id().equals(id)){Response result=newFixedLengthResponse(Response.Status.OK,"text/calendar; charset=utf-8",term.document.source);result.addHeader("Content-Disposition","attachment; filename=chrona-timetable.ics");return result;}return json(Response.Status.NOT_FOUND,"课表已不存在");
        }
        JSONArray terms=new JSONArray(),entries=new JSONArray();String chosen=""; LocalDate day=LocalDate.parse(request.getParms().getOrDefault("date",LocalDate.now().toString()));
        if(library!=null){TimetableStore.Semester selected=library.select("").current(day);for(TimetableStore.Semester term:library.semesters){terms.put(new JSONObject().put("id",term.id()).put("name",term.document.plan.groupLabel()+" · "+term.document.plan.label(term.index)));}
            if(selected!=null){LocalDate monday=day.minusDays(day.getDayOfWeek().getValue()-1);for(Timetable.Occurrence item:selected.view.all)if(!item.start.toLocalDate().isBefore(monday)&&item.start.toLocalDate().isBefore(monday.plusDays(7)))entries.put(new JSONObject().put("title",item.title).put("location",item.location).put("description",item.description).put("start",item.start.toString()).put("end",item.end.toString()).put("allDay",item.allDay));chosen=selected.id();}}
        return data(new JSONObject().put("terms",terms).put("items",entries).put("selected",chosen==null?"":chosen).put("date",day.toString()).put("revision",TimetableStore.revision()));
    }
    private Response attachment(IHTTPSession request)throws Exception {
        String[] parts=request.getUri().split("/");if(parts.length!=5)throw new IllegalArgumentException();long id=positive(parts[3]);String selector=parts[4];
        try(TaskStore store=new TaskStore(context)){
            if(store.getTask(id)==null)return json(Response.Status.NOT_FOUND,"记录已不存在");
            if(selector.startsWith("image-")){int index=integer(selector.substring(6),-1,0,10000);List<String> names=store.getImagePaths(id);if(index>=names.size())throw new IllegalArgumentException();File image=new ImageStore(context).fileFor(names.get(index));return newFixedLengthResponse(Response.Status.OK,"image/jpeg",new FileInputStream(image),image.length());}
            long fileId=positive(selector);for(TaskFileAttachment file:store.getFileAttachments(id))if(file.id==fileId){InputStream input=context.getContentResolver().openInputStream(Uri.parse(file.storedName));if(input==null)throw new IOException();Response result=newChunkedResponse(Response.Status.OK,"application/octet-stream",input);result.addHeader("Content-Disposition","attachment; filename*=UTF-8''"+Uri.encode(file.displayName));return result;}
            return json(Response.Status.NOT_FOUND,"附件已不存在");
        }
    }
    private static JSONObject task(TaskRecord item)throws JSONException{return new JSONObject().put("id",item.id).put("text",item.rawText).put("linkText",item.linkText==null?"":item.linkText).put("status",item.status).put("error",item.errorMessage==null?"":item.errorMessage).put("created",item.createdAtMillis).put("source",item.source);}
    private static JSONObject candidate(EventCandidate item)throws JSONException{return new JSONObject().put("id",item.id).put("taskId",item.taskId).put("title",item.title).put("start",item.startAtMillis==null?JSONObject.NULL:item.startAtMillis).put("end",item.endAtMillis==null?JSONObject.NULL:item.endAtMillis).put("zone",item.timeZoneId==null?ZoneId.systemDefault().getId():item.timeZoneId).put("location",item.location==null?"":item.location).put("description",item.description==null?"":item.description).put("category",item.category).put("allDay",item.allDay).put("reminder",item.reminderMinutesBefore==null?JSONObject.NULL:item.reminderMinutesBefore).put("needsConfirmation",item.needsConfirmation).put("calendarLinked",item.calendarEventId!=null);}
    private static JSONObject body(IHTTPSession request,int limit)throws Exception {
        if(request.getHeaders().containsKey("transfer-encoding")||!request.getHeaders().getOrDefault("content-type","").split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw new IllegalArgumentException();
        int length=integer(request.getHeaders().get("content-length"),-1,1,limit);byte[] bytes=new byte[length];int offset=0;
        while(offset<length){int count=request.getInputStream().read(bytes,offset,length-offset);if(count<0)throw new IOException();offset+=count;}
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        return new JSONObject(text);
    }
    private static Long nullableLong(JSONObject json,String key)throws JSONException{if(!json.has(key))throw new IllegalArgumentException();if(json.isNull(key))return null;return whole(json,key,0,MAX_DATE_MILLIS);}
    private static long whole(JSONObject json,String key,long minimum,long maximum)throws JSONException{Object raw=json.get(key);if(!(raw instanceof Integer)&&!(raw instanceof Long))throw new IllegalArgumentException();long value=((Number)raw).longValue();if(value<minimum||value>maximum)throw new IllegalArgumentException();return value;}
    private static boolean bool(JSONObject json,String key)throws JSONException{Object value=json.get(key);if(!(value instanceof Boolean))throw new IllegalArgumentException();return (Boolean)value;}
    private static String string(JSONObject json,String key,int maximum,boolean required)throws JSONException{Object raw=json.get(key);if(!(raw instanceof String))throw new IllegalArgumentException();String value=(String)raw;if(value.length()>maximum||required&&value.trim().isEmpty())throw new IllegalArgumentException();return value;}
    private static long positive(String raw){long id=Long.parseLong(raw);if(id<=0)throw new IllegalArgumentException();return id;}
    private static int integer(String raw,int fallback,int min,int max){int value=raw==null?fallback:Integer.parseInt(raw);if(value<min||value>max)throw new IllegalArgumentException();return value;}
    private static String hex(int color){return String.format(Locale.ROOT,"#%06x",color&0xffffff);}
    private static Response data(JSONObject value){return newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8",value.toString());}
    private static Response json(Response.Status status,String message){try{return newFixedLengthResponse(status,"application/json; charset=utf-8",new JSONObject().put("error",message).toString());}catch(JSONException error){throw new IllegalStateException(error);}}
    private static final class Conflict extends IllegalStateException { }
    private static final class BoundedRunner implements AsyncRunner {
        private final Set<ClientHandler> clients = new HashSet<>();
        private final java.util.concurrent.ThreadPoolExecutor workers = new java.util.concurrent.ThreadPoolExecutor(0,8,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<>());
        @Override public synchronized void exec(ClientHandler handler) {
            if(clients.size()>=8){handler.close();return;}clients.add(handler);
            try{workers.execute(handler);}catch(java.util.concurrent.RejectedExecutionException error){clients.remove(handler);handler.close();}
        }
        @Override public synchronized void closed(ClientHandler handler){clients.remove(handler);}
        @Override public synchronized void closeAll(){for(ClientHandler handler:new ArrayList<>(clients))handler.close();clients.clear();workers.shutdownNow();}
    }
}
