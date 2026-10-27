package com.donglan.chrona.zju;

import android.text.Html;
import com.donglan.chrona.net.NetworkFailure;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Synchronous, cancellable reader. Run off the UI thread and close after use. */
public final class ZjuClient implements Closeable {
    private static final String COURSES = "https://courses.zju.edu.cn";
    private static final String ZDBK = "https://zdbk.zju.edu.cn/jwglxt";
    private static final int MAX_REDIRECTS = 16;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_CLASSROOM_IMAGE_BYTES = 16 * 1024 * 1024;
    private final ZjuCalendarConfig calendar;
    private final MemoryCookies cookies = new MemoryCookies();
    private final OkHttpClient http;
    private String user, password;
    private volatile boolean cancelled, closed;
    private boolean coursesLoggedIn, timetableLoggedIn;
    private boolean classroomLoggedIn, casAuthenticated;
    private final java.util.Set<okhttp3.WebSocket> classroomSockets=new java.util.concurrent.CopyOnWriteArraySet<>();
    private java.util.function.BiFunction<Request,okhttp3.WebSocketListener,okhttp3.WebSocket> classroomSocketFactory;

    /** Read-only classroom session; shares the verified CAS transport, never persists cookies. */
    public synchronized JSONObject classroomJson(String url) throws IOException {
        HttpUrl target = safeUrl(url);
        if (!(target.host().equals("education.cmc.zju.edu.cn") || target.host().equals("yjapi.cmc.zju.edu.cn")
                || target.host().equals("classroom.zju.edu.cn")))
            throw new SchoolException("数据地址不属于智云课堂服务");
        ensureClassroom();
        try { return json(target.toString(), null); }
        catch (JSONException e) { throw new SchoolException("智云返回的数据格式无法识别"); }
    }

    public synchronized byte[] classroomImage(String url) throws IOException {
        HttpUrl target = safeUrl(url);
        if (!target.host().equals("video.cmc.zju.edu.cn"))
            throw new SchoolException("截图地址不属于智云媒体服务");
        ensureClassroom();
        try (Response response = execute(new Request.Builder().url(target).get().build())) {
            if (response.code() == 401 || response.code() == 403) throw new AuthenticationException(NetworkFailure.http("下载课堂截图",target.host(),response.code()));
            if (!response.isSuccessful()) throw NetworkFailure.http("下载课堂截图",target.host(),response.code());
            if (response.body() == null) throw new SchoolException("课堂截图返回空响应");
            String type = response.header("Content-Type", "").toLowerCase(java.util.Locale.ROOT);
            if (!type.startsWith("image/")) throw new SchoolException("课堂截图未返回图像");
            if (response.body().contentLength() > MAX_CLASSROOM_IMAGE_BYTES) throw new SchoolException("课堂截图过大");
            try (java.io.InputStream input = response.body().byteStream();
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    checkOpen();
                    if (output.size() + count > MAX_CLASSROOM_IMAGE_BYTES) throw new SchoolException("课堂截图过大");
                    output.write(buffer, 0, count);
                }
                checkOpen();
                if (output.size() == 0) throw new SchoolException("课堂截图为空");
                return output.toByteArray();
            } catch (SchoolException e) { throw e; }
            catch (IOException e) { checkOpen(); throw NetworkFailure.transport("读取课堂截图", target.host(), e); }
        }
    }
    /** The live endpoint is supplied by the authenticated official classroom metadata. HTTPS only. */
    public synchronized okhttp3.WebSocket classroomTranscriptSocket(String source,okhttp3.WebSocketListener listener)throws IOException {
        HttpUrl raw=HttpUrl.parse(source);
        if(raw==null||!raw.host().matches("qlite[0-9]*\\.cmc\\.zju\\.edu\\.cn")||!raw.username().isEmpty()||!raw.password().isEmpty()
                ||!(raw.isHttps()&&raw.port()==443||raw.scheme().equals("http")&&raw.port()==80)||raw.query()!=null||raw.fragment()!=null)
            throw new SchoolException("实时字幕地址不受支持");
        HttpUrl target=safeUrl(raw.newBuilder().scheme("https").port(443)
                .encodedPath(raw.encodedPath().replaceAll("/+$","")+"/glue/ws").build().toString());
        ensureClassroom();
        okhttp3.WebSocket socket=classroomSocketFactory.apply(new Request.Builder().url(target).header("Origin","https://classroom.zju.edu.cn").build(),listener);
        classroomSockets.add(socket);return socket;
    }

    private void ensureClassroom() throws IOException {
        checkOpen();
        if (classroomLoggedIn) return;
        try { if (!casAuthenticated) loginService(null); }
        catch (JSONException e) { throw new AuthenticationException(); }
        follow(new Request.Builder().url("https://tgmedia.cmc.zju.edu.cn/index.php?r=auth%2Flogin&forward=https%3A%2F%2Fclassroom.zju.edu.cn%2F").get().build(), true, true);
        classroomLoggedIn = true;
    }

    public ZjuClient(String user, String password) { this(user, password, ZjuCalendarConfig.ordinary()); }

    public ZjuClient(String user, String password, ZjuCalendarConfig calendar) {
        calendar.validate(); this.calendar = ZjuCalendarConfig.parse(calendar.json().toString());
        if (user == null || user.trim().isEmpty() || password == null || password.isEmpty())
            throw new IllegalArgumentException("请填写学号和密码");
        this.user = user.trim(); this.password = password;
        http = new OkHttpClient.Builder().cookieJar(cookies).followRedirects(false)
                .followSslRedirects(false).connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build();
        classroomSocketFactory=(request,listener)->this.http.newWebSocket(request,listener);
    }

    public synchronized ZjuSnapshot fetch(boolean homework, boolean timetable, Set<String> trackedCourses) throws IOException {
        checkOpen();
        List<ZjuSnapshot.Homework> work = new ArrayList<>();
        List<ZjuSnapshot.Course> courses = new ArrayList<>();
        List<ZjuSnapshot.Lesson> lessons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> completeTerms = new ArrayList<>();
        boolean domainSuccess = false;
        AuthenticationException authenticationFailure = null;
        if (homework) {
            try {
                readHomework(work, courses, warnings, trackedCourses);
                domainSuccess = true;
            } catch (AuthenticationException e) {
                checkOpen(); authenticationFailure = e;
                warnings.add("作业认证失败，请检查学号、密码或完成学校登录验证，已保留上次数据");
            } catch (Exception e) {
                checkOpen();
                warnings.add("作业同步失败，已保留上次数据");
            }
        }
        if (timetable) {
            try {
                readTimetable(lessons, warnings, completeTerms);
                domainSuccess = true;
            } catch (AuthenticationException e) {
                checkOpen(); authenticationFailure = e;
                warnings.add("课表认证失败，请检查学号、密码或完成学校登录验证，已保留未读学期的上次数据");
                if (!completeTerms.isEmpty() || !lessons.isEmpty()) domainSuccess = true;
            } catch (Exception e) {
                checkOpen();
                warnings.add("课表同步未完整完成，已保留未读学期的上次数据");
                // A later quarter failing must not invalidate an earlier complete quarter.
                if (!completeTerms.isEmpty() || !lessons.isEmpty()) domainSuccess = true;
            }
        }
        checkOpen();
        if ((homework || timetable) && !domainSuccess) {
            if (authenticationFailure != null) throw authenticationFailure;
            throw new SchoolException("本次同步未能读取学校数据，请检查网络后重试");
        }
        return new ZjuSnapshot(work, courses, lessons, warnings, completeTerms);
    }

    public void cancel() { cancelled = true;for(okhttp3.WebSocket socket:classroomSockets)socket.cancel();classroomSockets.clear();http.dispatcher().cancelAll(); }

    @Override public void close() {
        closed = true; cancel(); cookies.clear(); user = ""; password = "";
        http.connectionPool().evictAll(); http.dispatcher().executorService().shutdown();
    }

    private void checkOpen() throws IOException {
        if (cancelled || closed) throw new SchoolException("同步已取消");
    }

    private void readHomework(List<ZjuSnapshot.Homework> output, List<ZjuSnapshot.Course> courses,
                              List<String> warnings, Set<String> trackedCourses) throws IOException, JSONException {
        if (!coursesLoggedIn) {
            follow(new Request.Builder().url(COURSES + "/user/index").get().build(), true);
            coursesLoggedIn = true;
        }
        JSONArray semesters = requiredArray(json(COURSES + "/api/my-semesters?fields=id,name,sort,is_active,code", null), "semesters");
        Set<String> active = new HashSet<>();
        for (int i = 0; i < semesters.length(); i++) {
            JSONObject s = semesters.getJSONObject(i);
            if (s.optBoolean("is_active")) active.add(required(s, "id"));
        }
        if (active.isEmpty()) {
            if (trackedCourses.isEmpty()) throw new SchoolException("学在浙大未返回当前学期，未更新作业");
            warnings.add("学在浙大未返回当前学期，仅更新已追踪课程的作业");
        }
        Set<String> seenCourses = new HashSet<>();
        int page = 1, loaded = 0;
        while (true) {
            checkOpen();
            JSONObject query = new JSONObject().put("fields", "id,name,semester_id")
                    .put("page", page).put("page_size", PAGE_SIZE)
                    .put("conditions", new JSONObject().put("status", new JSONArray().put("ongoing").put("notStarted").put("closed"))
                            .put("keyword", "").put("classify_type", "recently_started").put("display_studio_list", false))
                    .put("showScorePassedStatus", false);
            JSONObject data = json(COURSES + "/api/my-courses", RequestBody.create(query.toString(), MediaType.get("application/json")));
            JSONArray rows = requiredArray(data, "courses");
            int newCount = 0;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject c = rows.getJSONObject(i);
                String id = required(c, "id");
                if (!seenCourses.add(id)) continue;
                newCount++; loaded++;
                if (trackedCourses.contains(id) || active.contains(required(c, "semester_id"))) readCourse(c, output, courses, warnings);
            }
            int pages = data.optInt("pages", -1), total = data.optInt("total", -1);
            if ((pages >= 0 && page >= pages) || (total >= 0 && loaded >= total)) break;
            if (rows.length() < PAGE_SIZE && pages < 0 && total < 0) break;
            if (newCount == 0 || page >= 200) throw new SchoolException("学在浙大课程分页不完整，未覆盖全部课程");
            page++;
        }
        for (String id : trackedCourses) {
            if (seenCourses.contains(id)) continue;
            warnings.add("学校课程列表未返回部分已追踪课程，已保留这些课程的上次作业数据");
            break;
        }
    }

    private void readCourse(JSONObject c, List<ZjuSnapshot.Homework> output,
                            List<ZjuSnapshot.Course> courses, List<String> warnings) throws IOException, JSONException {
        String id = required(c, "id"), title = required(c, "name");
        JSONArray activities;
        try { activities = requiredArray(json(COURSES + "/api/courses/" + segment(id) + "/activities", null), "activities"); }
        catch (AuthenticationException e) { throw e; }
        catch (IOException | JSONException e) {
            checkOpen(); courses.add(new ZjuSnapshot.Course(id, title, false, false));
            warnings.add(title + "：作业列表读取失败，保留上次数据"); return;
        }
        boolean publishedHomework=false;
        for(int i=0;i<activities.length();i++){JSONObject a=activities.getJSONObject(i);if("homework".equals(a.optString("type"))&&a.optBoolean("published"))publishedHomework=true;}
        Map<String, Boolean> status = new HashMap<>();
        boolean statusComplete = true;
        try {
            JSONArray states = !publishedHomework ? new JSONArray() : requiredArray(json(COURSES + "/api/course/" + segment(id) + "/homework/submission-status?no-intercept=true", null), "homework_activities");
            for (int i = 0; i < states.length(); i++) {
                JSONObject s = states.getJSONObject(i);
                String code = s.optString("status_code", "");
                Boolean value = "submitted".equals(code) || "graded".equals(code) ? Boolean.TRUE
                        : "unsubmitted".equals(code) || "not_submitted".equals(code) || "absent".equals(code) ? Boolean.FALSE : null;
                status.put(required(s, "id"), value);
            }
        } catch (AuthenticationException e) { throw e;
        } catch (IOException | JSONException e) { checkOpen(); statusComplete = false; }
        boolean complete = true;
        for (int i = 0; i < activities.length(); i++) {
            JSONObject a = activities.getJSONObject(i);
            if (!"homework".equals(a.optString("type"))) continue;
            if (!a.has("published")) { complete = false; continue; }
            if (!a.optBoolean("published")) continue;
            String activityId = a.optString("id", ""), name = a.optString("title", "");
            if (activityId.isEmpty() || name.isEmpty()) { complete = false; continue; }
            Boolean submitted = status.get(activityId);
            if (submitted == null) statusComplete = false;
            Long deadline = null;
            String rawDeadline = a.isNull("deadline") ? "" : a.optString("deadline", "");
            if (!rawDeadline.isEmpty()) {
                try { deadline = OffsetDateTime.parse(rawDeadline).toInstant().toEpochMilli(); }
                catch (RuntimeException e) { complete = false; warnings.add(title + "：有作业截止时间无法识别，已保留上次数据"); continue; }
            }
            JSONObject detail = a.optJSONObject("data");
            String description = detail == null ? "" : plain(detail.optString("description", detail.optString("content", "")));
            output.add(new ZjuSnapshot.Homework(id + "/homework/" + activityId, id, homeworkTitle(title, name),
                    description, COURSES + "/course/" + segment(id) + "/learning-activity#/" + segment(activityId),
                    a.optString("updated_at", ""), deadline, submitted));
        }
        if (!complete) warnings.add(title + "：部分作业缺少必要字段，保留上次数据");
        if (!statusComplete) warnings.add(title + "：部分作业提交状态未知");
        courses.add(new ZjuSnapshot.Course(id, title, complete, statusComplete));
    }

    static String homeworkTitle(String course, String homework) {
        return course == null || course.isBlank() ? homework : course.trim() + " · " + homework;
    }

    private void readTimetable(List<ZjuSnapshot.Lesson> lessons, List<String> warnings,
                               List<String> completeTerms) throws IOException, JSONException {
        ensureAcademic();
        readAcademicTimetable(lessons, warnings, completeTerms);
    }

    private void ensureAcademic() throws IOException, JSONException {
        checkOpen();
        if (!timetableLoggedIn) {
            follow(new Request.Builder().url(ZDBK + "/xtgl/login_cxSsoLoginUrl.html")
                    .post(new FormBody.Builder().build()).build(), false);
            follow(new Request.Builder().url(loginService(ZDBK + "/xtgl/login_ssologin.html")).get().build(), false);
            timetableLoggedIn = true;
        }
    }

    /** Official personal exam query; independent of course-calendar expansion and AI. */
    public synchronized List<ZjuExam> exams() throws IOException {
        checkOpen();
        try {
            ensureAcademic();
            HttpUrl endpoint = safeUrl(ZDBK + "/xskscx/kscx_cxXsgrksIndex.html").newBuilder()
                    .addQueryParameter("gnmkdm", "N509070").addQueryParameter("layout", "default")
                    .addQueryParameter("su", user).build();
            follow(new Request.Builder().url(endpoint).get().build(), false);
            String query = endpoint.newBuilder().addQueryParameter("doType", "query").build().toString();
            JSONArray rows = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (int page = 1; ; page++) {
                checkOpen();
                JSONObject data = json(query, new FormBody.Builder().add("_search", "false")
                        .add("queryModel.showCount", Integer.toString(PAGE_SIZE))
                        .add("queryModel.currentPage", Integer.toString(page))
                        .add("queryModel.sortName", "xkkh").add("queryModel.sortOrder", "desc")
                        .add("time", "0").build());
                JSONArray items = requiredArray(data, "items");
                int added = 0;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (seen.add(item.toString())) { rows.put(item); added++; }
                }
                int pages = data.optInt("totalPage", -1), total = data.optInt("totalResult", -1);
                if (pages >= 0 && page >= pages) {
                    if(total >= 0 && rows.length() < total)
                        throw new SchoolException("考试分页数量不完整");
                    break;
                }
                if (total >= 0 && rows.length() >= total) break;
                if (pages < 0 && total < 0 && items.length() < PAGE_SIZE) break;
                if (added == 0 || page >= 200) throw new SchoolException("考试分页未完整返回");
            }
            checkOpen();
            return ZjuExam.parse(rows);
        } catch (JSONException e) { throw new SchoolException("教务考试数据格式无法识别"); }
    }

    private void readAcademicTimetable(List<ZjuSnapshot.Lesson> lessons, List<String> warnings,
                               List<String> completeTerms) throws IOException, JSONException {
        if(calendar.campus.equals("ordinary")) {
            try {
                String official=follow(new Request.Builder().url(ZDBK+"/xtgl/index_cxXiaoLi.html").get().build(),false);
                Matcher boundaries=Pattern.compile("(\\d{4}-\\d{4})学年(秋|冬|春|夏|短|暑)学期\\((\\d{4}-\\d{2}-\\d{2})至(\\d{4}-\\d{2}-\\d{2})\\)").matcher(plain(official));
                while(boundaries.find()){ZjuCalendarConfig.Term configured=calendar.term(boundaries.group(1),boundaries.group(2));
                    if(configured==null)warnings.add(boundaries.group(1)+" "+boundaries.group(2)+"：学校起止 "+boundaries.group(3)+" 至 "+boundaries.group(4)+"，请在校历页补充授课截止与调课");
                    else if(!configured.start.toString().equals(boundaries.group(3)))warnings.add("校历开始日期与教务系统不符，当前采用已保存设置，请核对校历");}
            } catch(AuthenticationException e){throw e;} catch(Exception e){checkOpen();warnings.add("校历在线核对未完成，采用已保存配置");}
        }
        JSONObject menu = json(ZDBK + "/xtgl/index_cxFilterGnmkdm.html", new FormBody.Builder().add("gnmkmc", "课表").build());
        JSONObject entry = findMenu(menu);
        if (entry == null) throw new SchoolException("教务系统未返回个人课表菜单");
        String code = entry.optString("gnmkdm", entry.optString("gnmkdmKey", ""));
        HttpUrl pageUrl = safeUrl(ZDBK + required(entry, "dyym")).newBuilder().addQueryParameter("gnmkdm", code).build();
        String page = follow(new Request.Builder().url(pageUrl).get().build(), false);
        String year = calendar.selectedYear.isEmpty() ? selectedOption(page, "xnm") : calendar.selectedYear;
        String term = selectedOption(page, "xqm");
        if (year.isEmpty() || term.isEmpty()) throw new SchoolException("教务课表缺少学年或学期信息");
        List<String> terms = quarterOptions(page, year);
        if (terms.isEmpty()) terms.add(term);
        for (String queryTerm : terms) readQuarter(year, queryTerm, code, lessons, warnings, completeTerms);
        if (year.equals("2026-2027") && calendar.campus.equals("ordinary")) warnings.add("2027年节假日调休待学校公布，请在校历页核对更新");
    }

    private void readQuarter(String year, String term, String code, List<ZjuSnapshot.Lesson> lessons,
                             List<String> warnings, List<String> completeTerms) throws IOException, JSONException {
        String termName = term.contains("|") ? term.substring(term.indexOf('|') + 1) : "";
        JSONObject data = json(safeUrl(ZDBK + "/kbcx/xskbcx_cxXsKb.html").newBuilder().addQueryParameter("gnmkdm", code).build().toString(),
                new FormBody.Builder().add("xnm", year).add("xqm", term).add("xqmmc", termName).add("xxqf", "0").add("xsfs", "0").build());
        JSONArray rows = requiredArray(data, "kbList");
        List<String> parsingWarnings = new ArrayList<>();
        lessons.addAll(ZjuAcademicCalendar.expand(calendar, year, termName, rows, parsingWarnings));
        warnings.addAll(parsingWarnings);
        if (parsingWarnings.isEmpty()) completeTerms.add(year + " " + termName);
        for (String key : new String[]{"sjkList", "jxkList", "xskList", "ywkList", "fKbList"}) {
            JSONArray extra = data.optJSONArray(key);
            if (extra != null && extra.length() > 0) warnings.add("教务课表含 " + extra.length() + " 项其他教学安排，未返回可确认的授课日期");
        }
    }

    private List<String> quarterOptions(String page, String year) {
        List<String> values = new ArrayList<>();
        Matcher select = Pattern.compile("<select\\b[^>]*\\bid\\s*=\\s*['\"]xqm['\"][^>]*>([\\s\\S]*?)</select>", Pattern.CASE_INSENSITIVE).matcher(page);
        if (!select.find()) return values;
        Matcher options = Pattern.compile("<option\\b([^>]*)>([\\s\\S]*?)</option>", Pattern.CASE_INSENSITIVE).matcher(select.group(1));
        while (options.find()) {
            String value = attribute(options.group(1), "value");
            String name = value.contains("|") ? value.substring(value.indexOf('|') + 1) : "";
            if (calendar.term(year, name) != null && (calendar.selectedTerm.isEmpty() || calendar.selectedTerm.equals(name)) && !values.contains(value)) values.add(value);
        }
        return values;
    }

    private static JSONObject findMenu(Object value) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            if ("/kbcx/xskbcx_cxXskbcxIndex.html".equals(o.optString("dyym"))) return o;
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) { JSONObject found = findMenu(o.get(keys.next())); if (found != null) return found; }
        } else if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) { JSONObject found = findMenu(a.get(i)); if (found != null) return found; }
        }
        return null;
    }

    private static String selectedOption(String page, String id) {
        Matcher select = Pattern.compile("<select\\b[^>]*\\bid\\s*=\\s*['\"]" + id + "['\"][^>]*>([\\s\\S]*?)</select>", Pattern.CASE_INSENSITIVE).matcher(page);
        if (!select.find()) return "";
        Matcher options = Pattern.compile("<option\\b([^>]*)>([\\s\\S]*?)</option>", Pattern.CASE_INSENSITIVE).matcher(select.group(1));
        String first = "";
        while (options.find()) {
            String value = attribute(options.group(1), "value");
            if (first.isEmpty()) first = value;
            if (Pattern.compile("\\bselected\\b", Pattern.CASE_INSENSITIVE).matcher(options.group(1)).find()) return value;
        }
        return first;
    }

    private JSONObject json(String url, RequestBody body) throws IOException, JSONException {
        Request.Builder request = new Request.Builder().url(safeUrl(url));
        if (body != null) request.post(body); else request.get();
        String text = follow(request.build(), false);
        if (!text.trim().startsWith("{")) throw new SchoolException("学校未返回预期的数据，请重新登录后重试");
        return new JSONObject(text);
    }

    private String loginService(String service) throws IOException, JSONException {
        if (service != null) safeUrl(service);
        HttpUrl.Builder login = safeUrl("https://zjuam.zju.edu.cn/cas/login").newBuilder();
        if (service != null) login.addQueryParameter("service", service);
        String url = login.build().toString();
        try (Response r = execute(new Request.Builder().url(url).get().build())) {
            if (isRedirect(r)) { String target = redirect(r); casAuthenticated = true; return target; }
            if (r.code() != 200) throw new AuthenticationException(NetworkFailure.http("学校登录",r.request().url().host(),r.code()));
            String html = body(r);
            Matcher inputs = Pattern.compile("<input\\b([^>]*)>", Pattern.CASE_INSENSITIVE).matcher(html);
            String execution = "";
            while (inputs.find()) if ("execution".equals(attribute(inputs.group(1), "name"))) execution = attribute(inputs.group(1), "value");
            if (execution.isEmpty()) throw new AuthenticationException();
            JSONObject key = json("https://zjuam.zju.edu.cn/cas/v2/getPubKey", null);
            String modulus = required(key, "modulus"), exponent = required(key, "exponent");
            String encrypted;
            try {
                BigInteger passwordNumber = BigInteger.ZERO;
                for (int i = 0; i < password.length(); i++) passwordNumber = passwordNumber.shiftLeft(8).add(BigInteger.valueOf(password.charAt(i)));
                encrypted = passwordNumber.modPow(new BigInteger(exponent, 16), new BigInteger(modulus, 16)).toString(16);
                while (encrypted.length() < modulus.length()) encrypted = "0" + encrypted;
            } catch (RuntimeException e) { throw new AuthenticationException(); }
            Request post = new Request.Builder().url(url).post(new FormBody.Builder().add("username", user)
                    .add("password", encrypted).add("execution", execution).add("_eventId", "submit").add("authcode", "").build()).build();
            try (Response result = execute(post)) {
                if (!isRedirect(result)) {
                    if (!result.isSuccessful()) throw new AuthenticationException(NetworkFailure.http("学校登录",result.request().url().host(),result.code()));
                    throw new AuthenticationException();
                }
                String target = redirect(result); casAuthenticated = true; return target;
            }
        }
    }

    private String follow(Request request, boolean allowLogin) throws IOException {
        return follow(request, allowLogin, false);
    }

    private String follow(Request request, boolean allowLogin, boolean classroomOauth) throws IOException {
        int authCount = 0;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            checkOpen();
            HttpUrl url = request.url();
            // ZJU's OAuth server advertises its own callback using http in the opaque CAS service.
            // Keep that ticket service unchanged, but only send the callback request over HTTPS.
            boolean oauthService = classroomOauth && legacyOauthCallback(HttpUrl.parse(url.queryParameter("service") == null ? "" : url.queryParameter("service")));
            if (url.host().equals("zjuam.zju.edu.cn") && url.encodedPath().equals("/cas/login") && !oauthService) {
                if (!allowLogin || ++authCount > 2) throw new AuthenticationException();
                String service = url.queryParameter("service");
                if (service == null) throw new AuthenticationException();
                try { request = new Request.Builder().url(loginService(service)).get().build(); }
                catch (JSONException e) { throw new AuthenticationException(); }
                continue;
            }
            try (Response response = execute(request)) {
                if (response.code() == 401 || response.code() == 403) throw new AuthenticationException(NetworkFailure.http("请求学校数据",url.host(),response.code()));
                if (isRedirect(response)) {
                    String target;
                    HttpUrl next = url.resolve(response.header("Location", ""));
                    if (classroomOauth && legacyOauthCallback(next)) target = safeUrl(next.newBuilder().scheme("https").port(443).build().toString()).toString();
                    else target = redirect(response);
                    request = new Request.Builder().url(target).get().build(); continue;
                }
                if (!response.isSuccessful()) throw NetworkFailure.http("请求学校数据",url.host(),response.code());
                String text = body(response);
                if (Pattern.compile("<input[^>]*type\\s*=\\s*['\"]?password|name\\s*=\\s*['\"]execution['\"]", Pattern.CASE_INSENSITIVE).matcher(text).find()) throw new AuthenticationException();
                Matcher refresh = Pattern.compile("<meta[^>]*http-equiv\\s*=\\s*['\"]refresh['\"][^>]*content\\s*=\\s*['\"]\\s*0;\\s*URL=([^'\"]+)", Pattern.CASE_INSENSITIVE).matcher(text);
                if (refresh.find()) {
                    HttpUrl next = url.resolve(Html.fromHtml(refresh.group(1), Html.FROM_HTML_MODE_LEGACY).toString());
                    if (next == null) throw new SchoolException("学校登录跳转无效");
                    request = new Request.Builder().url(safeUrl(next.toString())).get().build(); continue;
                }
                return text;
            }
        }
        throw new SchoolException("学校登录跳转过多，请稍后重试");
    }

    private static boolean legacyOauthCallback(HttpUrl url) {
        return url != null && url.scheme().equals("http") && url.host().equals("zjuam.zju.edu.cn")
                && url.port() == 80 && url.encodedPath().equals("/cas/oauth2.0/callbackAuthorize")
                && url.username().isEmpty() && url.password().isEmpty();
    }

    private Response execute(Request request) throws IOException {
        checkOpen(); safeUrl(request.url().toString());
        Call call = http.newCall(request.newBuilder().header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0")
                .header("Accept", "*/*").build());
        try { return call.execute(); }
        catch (IOException e) { checkOpen(); throw NetworkFailure.transport("建立连接或等待响应", request.url().host(), e); }
    }

    private static HttpUrl safeUrl(String raw) throws IOException {
        HttpUrl url = HttpUrl.parse(raw);
        if (url == null || !url.isHttps() || !(url.host().equals("zju.edu.cn") || url.host().endsWith(".zju.edu.cn"))
                || !url.username().isEmpty() || !url.password().isEmpty() || url.port() != 443)
            throw new SchoolException("已阻止学校服务跳转到不安全的地址");
        return url;
    }
    private static boolean isRedirect(Response r) { return r.code() == 301 || r.code() == 302 || r.code() == 303 || r.code() == 307 || r.code() == 308; }
    private static String redirect(Response r) throws IOException {
        String location = r.header("Location");
        HttpUrl resolved = location == null ? null : r.request().url().resolve(location);
        if (resolved == null) throw new SchoolException("学校登录跳转缺少地址");
        return safeUrl(resolved.toString()).toString();
    }
    private static String body(Response r) throws IOException {
        if (r.body() == null) throw new SchoolException("学校服务返回空响应");
        try { return r.body().string(); }
        catch (IOException e) { throw NetworkFailure.transport("读取学校响应",r.request().url().host(),e); }
    }
    private static String required(JSONObject o, String key) throws JSONException {
        if (!o.has(key) || o.isNull(key)) throw new JSONException("Missing required field");
        Object value = o.get(key);
        if (!(value instanceof String || value instanceof Number) || value.toString().isEmpty()) throw new JSONException("Invalid required field");
        return value.toString();
    }
    private static JSONArray requiredArray(JSONObject o, String key) throws JSONException { return o.getJSONArray(key); }
    private static String segment(String value) { return new HttpUrl.Builder().scheme("https").host("zju.edu.cn").addPathSegment(value).build().encodedPath().substring(1); }
    private static String attribute(String attributes, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(['\"])([\\s\\S]*?)\\1", Pattern.CASE_INSENSITIVE).matcher(attributes);
        return m.find() ? Html.fromHtml(m.group(2), Html.FROM_HTML_MODE_LEGACY).toString() : "";
    }
    private static String plain(String html) { return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim(); }

    public static class SchoolException extends IOException {
        SchoolException(String message) { super(message); }
    }
    public static final class AuthenticationException extends SchoolException {
        AuthenticationException() { super("浙大统一身份认证失败或登录已失效，请检查学号、密码或校园网络后重试"); }
        AuthenticationException(IOException cause) { super(NetworkFailure.describe(cause)); initCause(cause); }
    }
    private static final class MemoryCookies implements CookieJar {
        private final List<Cookie> values = new ArrayList<>();
        private boolean closed;
        @Override public synchronized void saveFromResponse(HttpUrl url, List<Cookie> incoming) {
            if (closed) return;
            for (Cookie cookie : incoming) {
                values.removeIf(old -> old.name().equals(cookie.name()) && old.domain().equals(cookie.domain()) && old.path().equals(cookie.path()));
                if (cookie.expiresAt() > System.currentTimeMillis()) values.add(cookie);
            }
        }
        @Override public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            values.removeIf(c -> c.expiresAt() <= System.currentTimeMillis());
            List<Cookie> result = new ArrayList<>();
            for (Cookie c : values) if (c.matches(url)) result.add(c);
            return result;
        }
        synchronized void clear() { closed = true; values.clear(); }
    }
}
