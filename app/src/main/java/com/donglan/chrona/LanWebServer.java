package com.donglan.chrona;

import android.content.Context;
import android.net.Uri;
import com.donglan.chrona.calendar.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.processing.ProcessingJobService;
import com.donglan.chrona.processing.CaptureReparser;
import com.donglan.chrona.processing.StreamingOutputStore;
import com.donglan.chrona.processing.BulkCalendarPublisher;
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
    private final Runnable approvalsChanged;
    private volatile boolean closed;
    LanWebServer(Context context, String address, int port) {
        this(context, address, port, () -> { });
    }
    LanWebServer(Context context, String address, int port, Runnable approvalsChanged) {
        super(address, port); this.context = context.getApplicationContext(); this.approvalsChanged = approvalsChanged;
        security = new LanSecurity(address + ":" + port);
        setAsyncRunner(new BoundedRunner());
    }
    void closeAccess() { closed = true; security.close(); stop(); }
    @Override public Response serve(IHTTPSession request) {
        Response response;
        try { response = dispatch(request); }
        catch (Conflict | ConcurrentModificationException error) { response = json(Response.Status.CONFLICT, "数据已变化或操作正在进行，请刷新后重试"); }
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
        if (method == Method.POST && path.startsWith("/api/approval/")) {
            if (!security.origin(origin)) return json(Response.Status.FORBIDDEN, "来源无效");
            JSONObject body = body(request, 256);
            if (path.equals("/api/approval/request")) {
                LanSecurity.Approval approval = security.requestApproval(request.getRemoteIpAddress(), headers.get("user-agent"));
                if (approval == null) return json(Response.Status.TOO_MANY_REQUESTS, "暂时无法请求连接，请稍后重试");
                approvalsChanged.run();
                return data(new JSONObject().put("id", approval.id).put("verification", approval.verification)
                        .put("expiresIn", LanSecurity.APPROVAL_LIFETIME / 1000));
            }
            String id = string(body, "id", 64, true);
            if (!id.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
            if (path.equals("/api/approval/cancel")) {
                if (security.cancelApproval(request.getRemoteIpAddress(), id)) approvalsChanged.run();
                return data(new JSONObject().put("ok", true));
            }
            if (path.equals("/api/approval/poll")) {
                LanSecurity.ApprovalResult approval = security.pollApproval(request.getRemoteIpAddress(), id);
                Response result = data(new JSONObject().put("state", approval.state));
                if (approval.token != null) result.addHeader("Set-Cookie", "chrona_session=" + approval.token + "; HttpOnly; SameSite=Strict; Path=/");
                if (!approval.state.equals("pending") && !approval.state.equals("expired")) approvalsChanged.run();
                return result;
            }
            return json(Response.Status.NOT_FOUND, "连接请求不存在");
        }
        if (!security.authenticated(headers.get("cookie"))) return json(Response.Status.UNAUTHORIZED, "请在手机开启局域网访问并配对");
        if (method == Method.POST && !security.write(origin, headers.get("x-chrona-csrf"))) return json(Response.Status.FORBIDDEN, "连接验证失败，请重新配对");
        if (method == Method.GET && path.equals("/api/sync"))
            return data(LanSyncApi.read(context, integer(request.getParms().get("page"), 0, 0, 100000)));
        if (method == Method.POST && path.equals("/api/sync/preferences"))
            return data(LanSyncApi.preferences(context, body(request, 256)));
        if (method == Method.POST && path.equals("/api/sync/run")) {
            body(request, 256);
            return data(LanSyncApi.run(context));
        }
        if (method == Method.POST && path.equals("/api/sync/resolve"))
            return data(LanSyncApi.resolve(context, body(request, MAX_BODY)));
        if (method == Method.POST && path.equals("/api/attachment-upload")) {
            JSONObject result = LanAttachmentUpload.receive(context, request);
            AgendaWidgetProvider.requestRefresh(context);
            if (new WebDavSettingsStore(context).automatic()) {
                SyncJobService.schedule(context); AndroidSync.request(context, null);
            }
            return data(result);
        }
        // Request acceptance is immediate. Never hold AndroidSync.LOCK waiting for the fetch/apply callback.
        if (method == Method.POST && path.equals("/api/homework/refresh")) {
            body(request, 256);
            if (!homeworkRefreshAvailable()) return json(Response.Status.CONFLICT, "请先在手机启用并配置作业同步");
            boolean accepted = ZjuSync.requestHomework(context, null);
            return data(new JSONObject().put("accepted", accepted).put("message", accepted ? "正在刷新作业" : "请检查手机作业同步设置"));
        }
        if (method == Method.GET && path.equals("/api/session")) {
            UiStyle.Palette colors = UiStyle.colors(context);
            boolean homeworkAvailable=homeworkRefreshAvailable();
            return data(new JSONObject().put("csrf", security.csrf()).put("zone", ZoneId.systemDefault().getId())
                    .put("palette", new JSONObject().put("background", hex(colors.background)).put("text", hex(colors.text)).put("muted", hex(colors.muted)).put("primary", hex(colors.primary)).put("surface", hex(colors.surface)).put("outline", hex(colors.outline)))
                    .put("calendarAllowed", new CalendarStore(context).hasWritePermission()).put("calendarReadable",new CalendarStore(context).hasReadPermission())
                    .put("systemCalendarIncluded",HomeTimelinePreferences.includesSystemCalendar(context)).put("syncStatus", new WebDavSettingsStore(context).status())
                    .put("homeworkRefreshAvailable",homeworkAvailable).put("homeworkRefreshing",ZjuSync.homeworkRefreshing())
                    .put("homeworkStatus",homeworkAvailable?new ZjuSettingsStore(context).status():"请先在手机启用并配置作业同步")
                    .put("acknowledgementRevision",AgendaReminderPreferences.prefs(context).getLong("acknowledgement_revision",0L)));
        }
        if (method == Method.POST && path.equals("/api/logout")) { security.logout(headers.get("cookie")); Response result = data(new JSONObject().put("ok",true)); result.addHeader("Set-Cookie", "chrona_session=; Max-Age=0; HttpOnly; SameSite=Strict; Path=/"); return result; }
        if (method == Method.POST && path.equals("/api/candidate/complete")) {
            JSONObject body = body(request, MAX_BODY);
            long taskId = positive(body.getString("id")), candidateId = positive(body.getString("candidateId"));
            String warning;
            try {
                warning = TaskCompletion.set(context, candidateId, taskId, bool(body, "completed"),
                        whole(body, "revision", 0, Long.MAX_VALUE));
            } catch (ConcurrentModificationException | IllegalStateException error) { throw new Conflict(); }
            if (new WebDavSettingsStore(context).automatic()) { SyncJobService.schedule(context); AndroidSync.request(context, null); }
            try (TaskStore store = new TaskStore(context)) {
                return data(new JSONObject().put("ok", true).put("id", taskId)
                        .put("warning", warning == null ? JSONObject.NULL : warning).put("revision", store.dataRevision()));
            }
        }
        // Sync apply and browser mutations must not interleave their database/file snapshots.
        synchronized (AndroidSync.LOCK) {
            if (closed) return json(Response.Status.SERVICE_UNAVAILABLE, "局域网访问已关闭");
            if (method == Method.POST && path.equals("/api/homework/seen")) {
                JSONObject body = body(request, 256);
                try (TaskStore store = new TaskStore(context)) {
                    if (whole(body,"revision",0,Long.MAX_VALUE) != store.dataRevision()) throw new Conflict();
                    long id = positive(body.getString("candidateId"));
                    EventCandidate item = store.getCandidatesByIds(Collections.singleton(id)).stream().findFirst().orElseThrow(Conflict::new);
                    ZjuHomeworkStore homework = new ZjuHomeworkStore(context);
                    ZjuHomeworkStore.State managed = homework.state();
                    if (!new ZjuSettingsStore(context).enabled() || !managed.enabled || managed.captureId != item.taskId
                            || !EventCategory.HOMEWORK.equals(item.category) || item.completed()) throw new Conflict();
                    homework.markSeen(id);
                    return data(new JSONObject().put("ok",true).put("revision",store.dataRevision()));
                }
            }
            if (method == Method.POST && path.equals("/api/agenda/acknowledge")) {
                JSONObject body = body(request, 256);
                String identity = string(body,"id",24,true);long id=Long.parseLong(identity);
                if (id==0 || !Long.toString(id).equals(identity)) throw new IllegalArgumentException();
                long start = whole(body,"start",0,MAX_DATE_MILLIS);
                ZoneId zone = ZoneId.systemDefault();LocalDate date=Instant.ofEpochMilli(start).atZone(zone).toLocalDate();
                long begin=date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                long end=date.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli();
                CalendarStore provider=new CalendarStore(context);List<CalendarOccurrence> system=Collections.emptyList();
                if(HomeTimelinePreferences.includesSystemCalendar(context)&&provider.hasReadPermission())
                    system=provider.listInstances(begin-86_400_000L,end+86_400_000L,null);
                try(TaskStore store=new TaskStore(context)) {
                    CalendarOccurrence occurrence=ExternalCourseAgenda.merge(context,system,begin,end,new HashSet<>(store.linkedCalendarIds())).stream()
                            .filter(item->item.eventId==id&&item.displayStart(zone)==start).findFirst().orElseThrow(Conflict::new);
                    long until=occurrence.displayEnd(zone);
                    if(until<=System.currentTimeMillis())throw new Conflict();
                    AgendaAcknowledgement.set(context,AgendaAcknowledgement.key(id,start),until,bool(body,"acknowledged"));
                    return data(new JSONObject().put("ok",true).put("revision",store.dataRevision()));
                }
            }
            if (method == Method.POST && path.equals("/api/task/process")) {
                JSONObject body = body(request, MAX_BODY);
                long id = positive(body.getString("id"));
                try {
                    CaptureReparser.enqueue(context, id, true, whole(body, "revision", 0, Long.MAX_VALUE));
                } catch (IllegalStateException error) {
                    return json(Response.Status.CONFLICT, error.getMessage());
                }
                if (new WebDavSettingsStore(context).automatic()) {
                    SyncJobService.schedule(context); AndroidSync.request(context, null);
                }
                try (TaskStore store = new TaskStore(context)) {
                    return data(new JSONObject().put("ok", true).put("revision", store.dataRevision()));
                }
            }
            if (path.equals("/api/sources")) return data(method == Method.GET
                    ? LanSourcesApi.read(context, request.getParms())
                    : LanSourcesApi.update(context, body(request, 4096)));
            if (method == Method.POST && path.equals("/api/calendar/publish-selected")) {
                JSONObject body = body(request, MAX_BODY);
                List<Long> ids = new ArrayList<>();
                JSONArray selected = body.getJSONArray("ids");
                if (selected.length() == 0) throw new IllegalArgumentException();
                for (int i = 0; i < selected.length(); i++) ids.add(positive(selected.getString(i)));
                try (TaskStore store = new TaskStore(context)) {
                    if (whole(body,"revision",0,Long.MAX_VALUE) != store.dataRevision()) throw new Conflict();
                }
                if (!new CalendarStore(context).hasWritePermission()) throw new SecurityException();
                BulkCalendarPublisher.Result published = BulkCalendarPublisher.publish(context, ids, bool(body,"inbox"));
                if (new WebDavSettingsStore(context).automatic()) { SyncJobService.schedule(context); AndroidSync.request(context,null); }
                try (TaskStore store = new TaskStore(context)) {
                    return data(new JSONObject().put("summary",published.summary()).put("published",published.published)
                            .put("skipped",published.skipped).put("failed",published.failed).put("revision",store.dataRevision()));
                }
            }
            if (method == Method.POST && path.equals("/api/candidate/acknowledge")) {
                JSONObject body = body(request, MAX_BODY);
                try (TaskStore store = new TaskStore(context)) {
                    if (whole(body,"revision",0,Long.MAX_VALUE) != store.dataRevision()) throw new Conflict();
                    long id = positive(body.getString("candidateId")), taskId = positive(body.getString("id"));
                    EventCandidate item = store.getCandidatesByIds(Collections.singleton(id)).stream()
                            .filter(value -> value.taskId == taskId).findFirst().orElseThrow(Conflict::new);
                    if(item.repeatRule!=null){if(!body.has("occurrenceAt"))throw new IllegalArgumentException();item=RepeatOccurrences.at(item,nullableLong(body,"occurrenceAt"));if(item==null)throw new Conflict();}
                    Long anchor = item.reminderAnchor();
                    long until = anchor == null ? 0 : item.endAtMillis != null && item.endAtMillis > anchor ? item.endAtMillis : anchor + 60_000L;
                    if (item.isRecord || item.isTask() || until <= System.currentTimeMillis()) throw new IllegalArgumentException();
                    AgendaAcknowledgement.set(context,AgendaAcknowledgement.key(item),until,bool(body,"acknowledged"));
                    return data(new JSONObject().put("ok",true).put("revision",store.dataRevision()));
                }
            }
            if (method == Method.POST && path.equals("/api/candidate/repeat")) {
                JSONObject body=body(request,MAX_BODY);
                try(TaskStore store=new TaskStore(context)) {
                    if(whole(body,"revision",0,Long.MAX_VALUE)!=store.dataRevision())throw new Conflict();
                }
                EventCandidate changed;
                if("edit".equals(body.optString("action"))){
                    EventCandidate current;long taskId=positive(body.getString("id"));
                    try(TaskStore store=new TaskStore(context)){current=store.getCandidatesByIds(Collections.singleton(positive(body.getString("candidateId")))).stream()
                            .filter(item->item.taskId==taskId).findFirst().orElseThrow(Conflict::new);}
                    long original=whole(body,"original",0,MAX_DATE_MILLIS);EventCandidate before=RepeatOccurrences.at(current,original);
                    if(before==null)throw new Conflict();
                    EventCandidate detached=OccurrenceUpdates.copy(before,before.id,before.taskId,before.calendarEventId,before.needsConfirmation,before.uncertaintyLevel,false,original);
                    changed=RepeatActions.edit(context,current.taskId,current.id,string(body,"baseline",10000,true),original,OccurrenceUpdates.diff(before,edited(body,detached)));
                }else changed=RepeatActions.change(context,positive(body.getString("id")),positive(body.getString("candidateId")),
                        string(body,"baseline",10000,true),string(body,"action",20,true),
                        body.has("original")?nullableLong(body,"original"):null,body.has("moved")?nullableLong(body,"moved"):null,
                        body.has("rule")?RepeatRule.fromJson(body.getJSONObject("rule")):null);
                AgendaWidgetProvider.requestRefresh(context);
                if(new WebDavSettingsStore(context).automatic()){SyncJobService.schedule(context);AndroidSync.request(context,null);}
                try(TaskStore store=new TaskStore(context)){return data(new JSONObject().put("candidate",candidate(changed)).put("revision",store.dataRevision()));}
            }
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
                try (LinkedCalendarEvents calendarWrites = new LinkedCalendarEvents(store, new CalendarStore(context))) {
                  store.getWritableDatabase().beginTransaction();
                  try {
                    if (!path.equals("/api/task/create") && whole(body,"revision",0,Long.MAX_VALUE) != store.dataRevision()) throw new Conflict();
                    result = mutate(path, body, store, calendarWrites);
                    store.getWritableDatabase().setTransactionSuccessful();
                  } finally { store.getWritableDatabase().endTransaction(); }
                  calendarWrites.committed();
                }
                if (new WebDavSettingsStore(context).automatic()) { SyncJobService.schedule(context); AndroidSync.request(context, null); }
                return data(result.put("revision", store.dataRevision()));
            }
        }
    }
    private Response read(IHTTPSession request, TaskStore store) throws Exception {
        String path = request.getUri(); JSONObject result = new JSONObject().put("revision", store.dataRevision());
        if (path.equals("/api/parse-audit")) {
            long taskId = positive(request.getParms().get("id"));
            if (store.getTask(taskId) == null) return json(Response.Status.NOT_FOUND,"记录已不存在");
            String candidate = request.getParms().get("candidateId");
            Long candidateId = candidate == null ? null : positive(candidate);
            String runId = request.getParms().get("runId"), group = request.getParms().get("planGroup");
            if (runId != null) java.util.UUID.fromString(runId);
            if (group != null && (group.trim().isEmpty() || group.length() > 256 || runId == null))
                throw new IllegalArgumentException("方案查询需同时提供解析ID");
            int offset = integer(request.getParms().get("offset"), 0, 0, 100000);
            int limit = integer(request.getParms().get("limit"), 10, 1, 20);
            return data(result.put("audits", store.getParseAudits(taskId, candidateId, runId, group, offset, limit))
                    .put("offset", offset).put("limit", limit));
        }
        if(path.equals("/api/candidate/occurrences")) {
            long id=positive(request.getParms().get("candidateId")),taskId=positive(request.getParms().get("id"));
            EventCandidate series=store.getCandidatesByIds(Collections.singleton(id)).stream().filter(item->item.taskId==taskId).findFirst().orElseThrow(Conflict::new);
            LocalDate from=LocalDate.parse(request.getParms().get("from")),until=LocalDate.parse(request.getParms().get("until"));
            if(!until.isAfter(from)||java.time.temporal.ChronoUnit.DAYS.between(from,until)>366)throw new IllegalArgumentException();
            ZoneId zone=ZoneId.of(series.timeZoneId);JSONArray items=new JSONArray();
            for(EventCandidate item:RepeatOccurrences.between(series,from.atStartOfDay(zone).toInstant().toEpochMilli(),until.atStartOfDay(zone).toInstant().toEpochMilli(),1000))
                items.put(candidate(item).put("baseline",CandidateEditBaseline.of(series)));
            return data(result.put("items",items).put("series",candidate(series)));
        }
        if(path.equals("/api/merge/preview")){
            store.getWritableDatabase().beginTransaction();try{
                CandidateMerges.Preview preview=CandidateMerges.scan(store);JSONArray groups=new JSONArray();
                for(List<EventCandidate> group:preview.groups){JSONArray sources=new JSONArray();for(EventCandidate item:group){TaskRecord task=preview.tasks.get(item.taskId);sources.put(new JSONObject().put("taskId",task.id).put("candidateId",item.id).put("source",task.source).put("title",item.title).put("location",item.location==null?"":item.location));}
                    groups.put(new JSONObject().put("candidate",candidate(group.get(0))).put("sources",sources));}
                store.getWritableDatabase().setTransactionSuccessful();
                return data(new JSONObject().put("revision",preview.revision).put("removed",preview.removed()).put("groups",groups));
            }finally{store.getWritableDatabase().endTransaction();}
        }
        if (path.equals("/api/task")) {
            long id = positive(request.getParms().get("id")); TaskRecord record = store.getTask(id);
            if (record == null) return json(Response.Status.NOT_FOUND,"记录已不存在");
            JSONArray candidates = new JSONArray(), images = new JSONArray(), files = new JSONArray();
            for (EventCandidate saved : store.getCandidates(id)) {
                if (saved.isRecord) continue;
                EventCandidate item=saved;
                if(saved.repeatRule!=null&&Long.toString(saved.id).equals(request.getParms().get("candidate_id"))&&request.getParms().containsKey("occurrenceAt")) {
                    EventCandidate occurrence=RepeatOccurrences.at(saved,Long.parseLong(request.getParms().get("occurrenceAt")));if(occurrence!=null)item=occurrence;
                }
                JSONObject value = candidate(item).put("managed",store.isManagedHomework(item.id,item.taskId))
                        .put("submitted",store.isSubmittedHomework(item.id,item.taskId))
                        .put("mergedSources",CandidateMerges.sources(store,item.id));
                if (item.isHomework() && new ZjuSettingsStore(context).enabled()) {
                    HomeworkLinks links = new ZjuHomeworkStore(context).links(item);
                    value.put("mobileUrl",links.mobile == null ? JSONObject.NULL : links.mobile)
                            .put("desktopUrl",links.desktop == null ? JSONObject.NULL : links.desktop);
                }
                candidates.put(value);
            }
            List<String> paths = store.getImagePaths(id); for (int i=0;i<paths.size();i++) images.put(new JSONObject().put("index",i));
            for (TaskFileAttachment item : store.getFileAttachments(id)) files.put(new JSONObject().put("id",item.id).put("name",item.displayName).put("size",item.sizeBytes));
            return data(result.put("task", task(record)).put("candidates",candidates).put("images",images).put("files",files)
                    .put("managedHomeworkCapture",store.isHomeworkCapture(id)).put("mergedTargets",CandidateMerges.targets(store,id)));
        }
        if (path.equals("/api/inbox")) {
            int page = integer(request.getParms().get("page"), 0, 0, 100000), status = integer(request.getParms().get("status"),0,0,4);
            String search = request.getParms().getOrDefault("search",""); if (search.length()>256) throw new IllegalArgumentException();
            String category = request.getParms().get("category");
            if(category!=null&&!Arrays.asList(EventCategory.VALUES).contains(category))throw new IllegalArgumentException();
            boolean oldestFirst=Boolean.parseBoolean(request.getParms().getOrDefault("oldestFirst","false"));
            InboxQuery query = new InboxQuery(status,category,search); List<Long> ids=store.queryInboxIds(query,oldestFirst); JSONArray tasks=new JSONArray();
            page=Math.min(page,Math.max(0,(ids.size()-1)/InboxQuery.PAGE_SIZE));
            for(TaskRecord item:store.queryInboxPage(query,oldestFirst,page,ids)) tasks.put(task(item));
            if("true".equals(request.getParms().get("includeIds")))result.put("ids",new JSONArray(ids));
            return data(result.put("items",tasks).put("total",ids.size()).put("page",page).put("pageSize",InboxQuery.PAGE_SIZE).put("oldestFirst",oldestFirst));
        }
        if (path.equals("/api/schedule")) {
            int page=integer(request.getParms().get("page"),0,0,100000); String search=request.getParms().getOrDefault("search",""); if(search.length()>256) throw new IllegalArgumentException();
            String category=request.getParms().get("category"); if(category!=null&&!Arrays.asList(EventCategory.VALUES).contains(category)&&!com.donglan.chrona.data.ScheduleFilterState.EXAM.equals(category))throw new IllegalArgumentException();
            int tab=integer(request.getParms().get("tab"),0,0,3), publication=integer(request.getParms().get("publication"),0,0,3), todo=integer(request.getParms().get("todo"),0,0,3);
            LocalDate from=request.getParms().containsKey("from")?LocalDate.parse(request.getParms().get("from")):null;
            LocalDate until=request.getParms().containsKey("until")?LocalDate.parse(request.getParms().get("until")):null;
            if((from==null)!=(until==null)||from!=null&&!until.isAfter(from))throw new IllegalArgumentException();
            if("1".equals(request.getParms().get("source"))) {
                ZoneId zone=ZoneId.systemDefault();if(from==null){from=LocalDate.now(zone).withDayOfMonth(1);until=from.plusMonths(1);}
                if(until.isAfter(from.plusYears(1)))throw new IllegalArgumentException();
                long begin=from.atStartOfDay(zone).toInstant().toEpochMilli(),end=until.atStartOfDay(zone).toInstant().toEpochMilli(),now=System.currentTimeMillis();
                CalendarStore provider=new CalendarStore(context);List<CalendarOccurrence> providerItems=Collections.emptyList();
                if(HomeTimelinePreferences.includesSystemCalendar(context)&&provider.hasReadPermission())providerItems=provider.listInstances(begin-86_400_000L,end+86_400_000L,null);
                List<CalendarOccurrence> visible=new ArrayList<>();String keyword=search.toLowerCase(Locale.ROOT).trim();
                for(CalendarOccurrence event:ExternalCourseAgenda.merge(context,providerItems,begin,end,new HashSet<>(store.linkedCalendarIds()))) {
                    boolean future=event.displayEnd(zone)>now;
                    if(event.displayStart(zone)>=end||event.displayEnd(zone)<=begin||tab==1||tab==0&&!future||tab==2&&future||publication==1||publication==3||category!=null&&!EventCategory.EVENT.equals(category))continue;
                    if(!keyword.isEmpty()&&!event.title.toLowerCase(Locale.ROOT).contains(keyword)&&!(event.location==null?"":event.location).toLowerCase(Locale.ROOT).contains(keyword))continue;
                    visible.add(event);
                }
                boolean oldestFirst=request.getParms().containsKey("oldestFirst")?Boolean.parseBoolean(request.getParms().get("oldestFirst")):ScheduleOrderPreferences.oldestFirst(context,tab);
                visible.sort(Comparator.comparingLong((CalendarOccurrence event)->event.displayStart(zone)).thenComparingLong(event->event.eventId));if(!oldestFirst)Collections.reverse(visible);
                page=Math.min(page,Math.max(0,(visible.size()-1)/ScheduleQuery.PAGE_SIZE));JSONArray items=new JSONArray();
                for(int i=page*ScheduleQuery.PAGE_SIZE;i<Math.min(visible.size(),(page+1)*ScheduleQuery.PAGE_SIZE);i++)items.put(externalCandidate(visible.get(i),zone));
                return data(result.put("items",items).put("total",visible.size()).put("page",page).put("pageSize",ScheduleQuery.PAGE_SIZE).put("oldestFirst",oldestFirst).put("system",true).put("from",from.toString()).put("until",until.toString()));
            }
            ScheduleQuery query=new ScheduleQuery(tab,category,publication,search,from,until,System.currentTimeMillis(),ZoneId.systemDefault(),todo);
            boolean oldestFirst=request.getParms().containsKey("oldestFirst")?Boolean.parseBoolean(request.getParms().get("oldestFirst")):ScheduleOrderPreferences.oldestFirst(context,tab);
            long focus=request.getParms().containsKey("locate")?positive(request.getParms().get("locate")):0;
            ScheduleListing.Page listing=ScheduleListing.load(context,store,query,oldestFirst,page,focus);
            if(focus>0)result.put("located",listing.ids.contains(focus)?Long.toString(focus):JSONObject.NULL);
            JSONArray entries=new JSONArray();
            for(ScheduleListing.Row row:listing.rows)entries.put(row.exam==null?candidate(row.candidate):examCandidate(row));
            if("true".equals(request.getParms().get("includeIds")))result.put("ids",new JSONArray(listing.ids));
            return data(result.put("items",entries).put("total",listing.total).put("selectableTotal",listing.ids.size())
                    .put("examWarning",listing.warning).put("examsLoaded",listing.examsLoaded).put("page",listing.page).put("pageSize",ScheduleQuery.PAGE_SIZE).put("oldestFirst",oldestFirst));
        }
        if (path.equals("/api/agenda")) {
            ZoneId zone=ZoneId.systemDefault();
            LocalDate from=LocalDate.parse(request.getParms().get("from")),until=LocalDate.parse(request.getParms().get("until"));
            if(!until.isAfter(from)||java.time.temporal.ChronoUnit.DAYS.between(from,until)>366)throw new IllegalArgumentException();
            AgendaRepository.Snapshot snapshot=AgendaRepository.load(context,from.atStartOfDay(zone).toInstant().toEpochMilli(),
                    until.atStartOfDay(zone).toInstant().toEpochMilli(),HomeTimelinePreferences.includesSystemCalendar(context),zone,new android.os.CancellationSignal());
            JSONArray entries=new JSONArray();
            for(AgendaEntry item:snapshot.entries)entries.put(new JSONObject().put("id",item.id).put("taskId",item.taskId)
                    .put("title",item.title).put("start",item.start).put("end",item.end).put("allDay",item.allDay)
                    .put("location",item.location).put("description",item.description).put("source",item.source)
                    .put("category",item.category).put("system",item.system).put("occurrenceAt",item.occurrenceAtMillis==null?JSONObject.NULL:item.occurrenceAtMillis).put("due",item.dueAtMillis==null?JSONObject.NULL:item.dueAtMillis));
            return data(result.put("items",entries).put("truncated",snapshot.truncated).put("systemUnavailable",snapshot.systemUnavailable));
        }
        if (path.equals("/api/home")) {
            HomeData home=new HomeData(context,store);ZoneId zone=home.zone;LocalDate date=home.today;
            long start=date.atStartOfDay(zone).toInstant().toEpochMilli(),end=home.endDate.atStartOfDay(zone).toInstant().toEpochMilli();
            JSONArray entries=new JSONArray(), calendar=new JSONArray(),recent=new JSONArray();
            for(EventCandidate item:home.candidates)if(HomeTimelineEntry.hasUpcomingTime(item,home.now,zone)&&new HomeTimelineEntry(item).timestampMillis<end)entries.put(candidate(item));
            for(EventCandidate item:home.upcoming)recent.put(candidate(item));
            String calendarState="系统日历需在手机授权"; CalendarStore provider=new CalendarStore(context);
            List<CalendarOccurrence> systemEntries=java.util.Collections.emptyList();
            if(HomeTimelinePreferences.includesSystemCalendar(context)&&provider.hasReadPermission()) {
                try { systemEntries=provider.listInstances(start-86_400_000L,end+86_400_000L,null); calendarState=""; }
                catch(RuntimeException error){calendarState="系统日历暂时无法读取";}
            } else if(!HomeTimelinePreferences.includesSystemCalendar(context)) calendarState="手机已关闭系统日历时间线";
            for(CalendarOccurrence item:ExternalCourseAgenda.merge(context,systemEntries,start,end,new HashSet<>(store.linkedCalendarIds())))if(item.displayEnd(zone)>home.now)
                calendar.put(externalCandidate(item,zone));
            HomeTodoSummary todos=home.todos;
            JSONArray focus=new JSONArray();for(EventCandidate item:todos.focus)focus.put(candidate(item));
            return data(result.put("items",entries).put("recent",recent).put("limit",home.limit).put("calendar",calendar).put("calendarState",calendarState).put("date",date.toString()).put("review",home.review).put("failed",home.failed)
                    .put("unreadHomework",home.unreadHomework>0?Long.toString(home.unreadHomework):JSONObject.NULL)
                    .put("todos",new JSONObject().put("total",todos.total).put("todayDue",todos.todayDue).put("overdue",todos.overdue).put("unscheduled",todos.unscheduled).put("items",focus)));
        }
        return json(Response.Status.NOT_FOUND,"页面不存在");
    }
    private JSONObject mutate(String path,JSONObject body,TaskStore store,LinkedCalendarEvents calendarWrites)throws Exception {
        if(path.equals("/api/selection/delete")) {
            JSONArray selected=body.getJSONArray("ids");if(selected.length()==0)throw new IllegalArgumentException();
            Set<Long> ids=new LinkedHashSet<>();for(int i=0;i<selected.length();i++)ids.add(positive(selected.getString(i)));
            boolean inbox=bool(body,"inbox");CalendarStore calendar=new CalendarStore(context);
            List<EventCandidate> candidates=new ArrayList<>();
            if(inbox){for(long taskId:ids){if(store.getTask(taskId)==null)throw new Conflict();candidates.addAll(store.getCandidates(taskId));}}
            else {candidates=store.getCandidatesByIds(ids);if(candidates.size()!=ids.size())throw new Conflict();}
            // All provider work succeeds before removing any local record or attachment.
            calendarWrites.delete(candidates);
            if(inbox)for(long taskId:ids){
                ProcessingJobService.cancel(context,taskId);List<String> images=store.getImagePaths(taskId);
                if(!store.deleteTask(taskId))throw new Conflict();new StreamingOutputStore(context).delete(taskId);
                for(String image:images)new ImageStore(context).delete(image);
                android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class);if(manager!=null)manager.cancel((int)taskId);
            }else for(EventCandidate item:candidates){if(!store.deleteCandidate(item.id,item.taskId))throw new Conflict();store.updateReviewStatus(item.taskId);}
            AgendaWidgetProvider.requestRefresh(context);return new JSONObject().put("ok",true).put("removed",ids.size());
        }
        if(path.equals("/api/candidate/merge")){
            Set<Long> groups=null;
            if(body.has("groups")){
                groups=new HashSet<>();JSONArray selected=body.getJSONArray("groups");
                for(int index=0;index<selected.length();index++)if(!groups.add(positive(selected.getString(index))))throw new IllegalArgumentException();
                if(groups.isEmpty())throw new IllegalArgumentException();
            }
            return new JSONObject().put("removed",CandidateMerges.mergeRows(store,whole(body,"revision",0,Long.MAX_VALUE),groups));
        }
        if(path.equals("/api/task/create")) {
            String raw=string(body,"text",100000,true); long id=store.insertTask(raw,null,"局域网浏览器",System.currentTimeMillis());
            if(bool(body,"process")) enqueue(store,id); else store.updateStatus(id,TaskRecord.READY,null); return new JSONObject().put("id",id);
        }
        long id=positive(body.getString("id")); TaskRecord record=store.getTask(id); if(record==null)throw new Conflict();
        if(path.equals("/api/task/delete")) {
            ProcessingJobService.cancel(context,id); CalendarStore calendar=new CalendarStore(context);
            calendarWrites.delete(store.getCandidates(id));
            List<String> images=store.getImagePaths(id); if(!store.deleteTask(id))throw new Conflict();
            new StreamingOutputStore(context).delete(id); for(String name:images)new ImageStore(context).delete(name);
            android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class); if(manager!=null)manager.cancel((int)id);
        } else if(path.equals("/api/task/edit")) {
            editable(record); if(!store.updateRawText(id,string(body,"text",100000,true)))throw new Conflict();
        } else if(path.equals("/api/candidate/choose-new")) {
            editable(record);long candidateId=positive(body.getString("candidateId"));
            if(store.getCandidates(id).stream().noneMatch(item->item.id==candidateId))throw new Conflict();
            OccurrenceUpdates.chooseNewLocked(store,candidateId,string(body,"baseline",10000,true));
        } else if(path.equals("/api/candidate/create")) {
            editable(record); if(store.isHomeworkCapture(id))throw new IllegalArgumentException();
            EventCandidate initial=new EventCandidate(0,id,"",null,null,ZoneId.systemDefault().getId(),"","",null,true);
            EventCandidate entry=edited(body,initial);store.appendCandidate(entry);
            store.updateReviewStatus(id);
        } else if(path.equals("/api/candidate/save")||path.equals("/api/candidate/publish")||path.equals("/api/candidate/delete")) {
            editable(record); long candidateId=positive(body.getString("candidateId")); EventCandidate old=null;
            for(EventCandidate item:store.getCandidates(id))if(item.id==candidateId)old=item;
            if(old==null)throw new Conflict(); CalendarStore calendar=new CalendarStore(context);
            if(old.repeatRule!=null&&!path.endsWith("publish"))throw new IllegalArgumentException("请选择具体一次重复日程");
            if(path.endsWith("delete")){calendarWrites.delete(Collections.singletonList(old)); if(!store.deleteCandidate(candidateId,id))throw new Conflict();}
            else {
                EventCandidate edited=edited(body,old); boolean publish=path.endsWith("publish");
                store.requireCandidateEditAllowed(edited);
                if(OccurrenceUpdates.held(store,old.id)){
                    CandidateEditBaseline.require(old,string(body,"baseline",10000,true));
                    if(publish)OccurrenceUpdates.applyLocked(store,calendarWrites,edited,body.getString("baseline"));
                    else {
                        store.saveOccurrenceDraft(old.id,OccurrenceUpdates.diff(old,edited));
                        if(!store.updateCandidate(OccurrenceUpdates.copy(edited,edited.id,edited.taskId,null,true,edited.uncertaintyLevel,false,null)))throw new Conflict();
                    }
                    store.updateReviewStatus(id);return new JSONObject().put("ok",true).put("id",id);
                }
                if (publish && edited.isRecord) throw new IllegalArgumentException();
                if(old.calendarEventId!=null&&edited.calendarStart()==null&&!edited.isRecord)calendarWrites.detach(old);
                if((publish||old.calendarEventId!=null) && edited.calendarStart()!=null && !edited.isRecord){
                    CalendarStore.EventInput input=input(edited);
                    if(old.calendarEventId!=null){if(!calendarWrites.update(old,input))throw new Conflict();}
                    else { if(!calendar.findMatchingEvents(input).isEmpty())throw new IllegalArgumentException(); long event=calendarWrites.insert(old,input); edited=new EventCandidate(edited.id,id,edited.title,edited.startAtMillis,edited.endAtMillis,edited.timeZoneId,edited.location,edited.description,edited.reminderMinutesBefore,false,event,edited.category,edited.allDay,false,EventCandidate.CERTAIN,edited.dueAtMillis,edited.completedAtMillis,edited.isRecord).withRepeatFrom(edited); }
                }
                if(!store.updateCandidate(edited))throw new Conflict();
            }
            store.updateReviewStatus(id);
        } else throw new IllegalArgumentException();
        return new JSONObject().put("ok",true).put("id",id);
    }
    private void enqueue(TaskStore store,long id) {
        try { ProcessingJobService.enqueueRemote(context,id); }
        catch(RuntimeException error){store.updateStatus(id,TaskRecord.FAILED,"手机暂时无法安排 AI 处理，请在手机检查后台运行限制后重试");}
    }
    private static void editable(TaskRecord record){if(TaskRecord.PROCESSING.equals(record.status)||TaskRecord.QUEUED.equals(record.status))throw new Conflict();}
    private static EventCandidate edited(JSONObject body,EventCandidate old)throws Exception {
        if (old.isRecord || body.optBoolean("record", false)) throw new IllegalArgumentException();
        String title=string(body,"title",500,true),category=string(body,"category",20,true);
        if(!Arrays.asList(EventCategory.VALUES).contains(category))throw new IllegalArgumentException();
        Long start=nullableLong(body,"start"),end=nullableLong(body,"end"); boolean allDay=bool(body,"allDay");
        if(start==null&&end!=null||start!=null&&end!=null&&end<=start)throw new IllegalArgumentException();
        String zone=allDay?"UTC":string(body,"zone",100,true);ZoneId.of(zone);
        if(allDay&&(start!=null&&start%86400000L!=0||end!=null&&end%86400000L!=0))throw new IllegalArgumentException();
        if(!body.has("reminder"))throw new IllegalArgumentException();
        Integer reminder=body.isNull("reminder")?null:(int)whole(body,"reminder",0,525600);
        Long due=EventCategory.isTaskLike(category)?body.has("due")?nullableLong(body,"due"):old.dueAtMillis:null;
        boolean needs=((EventCategory.EVENT.equals(category)&&(start==null||end==null))||bool(body,"needsConfirmation"));
        return new EventCandidate(old.id,old.taskId,title,start,end,zone,string(body,"location",2000,false),string(body,"description",100000,false),reminder,needs,old.calendarEventId,category,allDay,false,needs?EventCandidate.DOUBTFUL:EventCandidate.CERTAIN,due,EventCategory.isTaskLike(category)?old.completedAtMillis:null,false).withRepeatFrom(old);
    }
    private static CalendarStore.EventInput input(EventCandidate item){if(item.isRecord||item.calendarStart()==null||item.calendarEnd()==null||item.needsConfirmation)throw new IllegalArgumentException();return new CalendarStore.EventInput(item.title,item.calendarStart(),item.calendarEnd(),item.timeZoneId,item.allDay,item.description,item.location,item.completed()||item.reminderMinutesBefore==null?Collections.emptyList():Collections.singletonList(item.reminderMinutesBefore)).withRepeat(item);}
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
    private static JSONObject candidateBase(EventCandidate item)throws JSONException {
        return new JSONObject().put("id",item.id).put("taskId",item.taskId).put("title",item.title)
                .put("start",item.startAtMillis==null?JSONObject.NULL:item.startAtMillis)
                .put("end",item.endAtMillis==null?JSONObject.NULL:item.endAtMillis)
                .put("executionStart",item.executionStartAtMillis()==null?JSONObject.NULL:item.executionStartAtMillis())
                .put("executionEnd",item.executionEndAtMillis()==null?JSONObject.NULL:item.executionEndAtMillis())
                .put("executionAllDay",item.executionAllDay())
                .put("zone",item.timeZoneId==null?ZoneId.systemDefault().getId():item.timeZoneId)
                .put("location",item.location==null?"":item.location).put("description",item.description==null?"":item.description)
                .put("category",item.category).put("allDay",item.allDay)
                .put("reminder",item.reminderMinutesBefore==null?JSONObject.NULL:item.reminderMinutesBefore)
                .put("needsConfirmation",item.needsConfirmation).put("calendarLinked",item.calendarEventId!=null)
                .put("due",item.dueAtMillis==null?JSONObject.NULL:item.dueAtMillis)
                .put("effectiveDue",item.effectiveDueAtMillis()==null?JSONObject.NULL:item.effectiveDueAtMillis())
                .put("overdueAt",item.overdueAtMillis()==null?JSONObject.NULL:item.overdueAtMillis())
                .put("completed",item.completed()).put("record",item.isRecord);
    }
    private boolean homeworkRefreshAvailable() {
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        return settings.enabled()&&settings.configured()&&new ZjuHomeworkStore(context).state().enabled;
    }
    private JSONObject externalCandidate(CalendarOccurrence item,ZoneId zone) throws JSONException {
        long start=item.displayStart(zone),end=item.displayEnd(zone);
        return new JSONObject().put("id",item.eventId).put("externalId",Long.toString(item.eventId))
                .put("title",item.title).put("location",item.location).put("description",item.description)
                .put("allDay",item.allDay).put("start",start).put("end",end).put("source",item.calendarName)
                .put("system",true).put("category","event").put("canAcknowledge",end>System.currentTimeMillis())
                .put("acknowledged",AgendaAcknowledgement.isAcknowledged(context,AgendaAcknowledgement.key(item.eventId,start)));
    }
    private JSONObject examCandidate(ScheduleListing.Row row)throws JSONException{
        EventCandidate item=row.candidate;var exam=row.exam;
        return new JSONObject().put("id","exam:"+item.id).put("title",exam.title).put("category","exam")
                .put("start",item.startAtMillis==null?JSONObject.NULL:item.startAtMillis).put("end",item.endAtMillis==null?JSONObject.NULL:item.endAtMillis)
                .put("zone","Asia/Shanghai").put("allDay",false).put("readOnly",true).put("canAcknowledge",false)
                .put("source","折姜星辰 · 教务").put("location",ScheduleListing.location(exam))
                .put("description",exam.kind+" · "+exam.displayTime()).put("officialTime",exam.displayTime());
    }
    private JSONObject candidate(EventCandidate item) throws JSONException {
        Long anchor=item.reminderAnchor();
        long until=anchor==null?0:item.endAtMillis!=null&&item.endAtMillis>anchor?item.endAtMillis:anchor+60_000L;
        EventCandidate series=item;
        if(item.occurrenceAtMillis!=null)try(TaskStore store=new TaskStore(context)){List<EventCandidate> rows=store.getCandidatesByIds(Collections.singleton(item.id));if(!rows.isEmpty())series=rows.get(0);}
        JSONObject proposal;
        try(TaskStore store=new TaskStore(context)){
            proposal=store.occurrenceProposal(item.id);
            if(proposal!=null&&"applied".equals(proposal.optString("state")))try{
                EventCandidate target=OccurrenceUpdates.series(store,proposal.getString("series_id"));
                proposal.put("targetTaskId",target.taskId).put("targetCandidateId",target.id);
            }catch(IllegalArgumentException missing){/* The saved outcome remains readable after target deletion. */}
        }
        JSONArray history=null;
        if(item.occurrenceAtMillis!=null){JSONObject edit=RepeatOccurrences.exceptions(series).optJSONObject(Long.toString(item.occurrenceAtMillis));if(edit!=null)history=edit.optJSONArray("history");}
        return candidateBase(item).put("occurrenceHistory",history==null?new JSONArray():history).put("occurrenceUpdate",proposal==null?JSONObject.NULL:proposal).put("recurrence",item.repeatRule==null?JSONObject.NULL:item.repeatRule.toJson()).put("repeatLabel",item.repeatRule==null?"":item.repeatRule.label())
                .put("repeatStoppedAt",item.repeatStoppedAtMillis==null?JSONObject.NULL:item.repeatStoppedAtMillis).put("occurrenceAt",item.occurrenceAtMillis==null?JSONObject.NULL:item.occurrenceAtMillis)
                .put("baseline",CandidateEditBaseline.of(series)).put("canAcknowledge",!item.isRecord&&!item.isTask()&&until>System.currentTimeMillis())
                .put("acknowledged",AgendaAcknowledgement.isAcknowledged(context,AgendaAcknowledgement.key(item)));
    }
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
