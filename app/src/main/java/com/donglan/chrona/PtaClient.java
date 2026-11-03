package com.donglan.chrona;

import com.donglan.chrona.zju.ZjuSnapshot;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Read-only PTA adapter. Credentials are attached only to these two exact HTTPS hosts. */
final class PtaClient implements AutoCloseable {
    static final String HOST = "https://pintia.cn";
    private final String session;
    private final OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build();
    PtaClient(String session) { this.session = session; }
    record User(String id, String nickname) { }
    static final class AuthExpired extends IOException { AuthExpired() { super("PTA 登录已过期，请重新登录"); } }
    User whoAmI() throws Exception {
        JSONObject body = get("https://passport.pintia.cn/api/u/current", false);
        JSONObject user = body.optJSONObject("user");
        if (user == null || user.optString("id", "").isBlank()) throw new AuthExpired();
        return new User(user.getString("id"), user.optString("nickname", "PTA 用户"));
    }
    List<ZjuSnapshot.Homework> fetch(PtaSettingsStore settings) throws Exception {
        List<ZjuSnapshot.Homework> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        // PTA uses offset pagination. Fail an incomplete snapshot instead of accepting a partial list.
        for (int page = 0; page < 100; page++) {
            HttpUrl url = HttpUrl.get(HOST + "/api/problem-sets").newBuilder()
                    .addQueryParameter("filter", new JSONObject().put("endAtAfter", java.time.Instant.now().toString()).toString())
                    .addQueryParameter("limit", "100").addQueryParameter("offset", Integer.toString(page * 100)).build();
            JSONObject body = get(url.toString(), false); JSONArray sets = body.optJSONArray("problemSets");
            if (sets == null) throw new IOException("PTA 未返回题集列表，已保留原作业");
            for (int i = 0; i < sets.length(); i++) {
                JSONObject set = sets.getJSONObject(i); String id = set.getString("id");
                if (!id.matches("[0-9]+") || !seen.add(id)) throw new IOException("PTA 分页或题集身份异常，已保留原作业");
                JSONObject detail = get(HOST + "/api/problem-sets/" + id + "/exams", true);
                ZjuSnapshot.Homework item = parse(set, detail.optJSONObject("exam"), settings.course(id));
                result.add(item);
            }
            if (sets.length() < 100) return result;
        }
        throw new IOException("PTA 题集分页超出范围，已保留原作业");
    }
    static ZjuSnapshot.Homework parse(JSONObject set, JSONObject exam, String course) throws Exception {
        String id = set.getString("id"), title = set.getString("name");
        if (!id.matches("[0-9]+") || title.isBlank()) throw new IOException("PTA 题集身份或标题缺失");
        String end = exam == null ? "" : exam.optString("endAt", "");
        if (end.isBlank()) end = set.optString("endAt", "");
        Long deadline = end.isBlank() ? null : OffsetDateTime.parse(end).toInstant().toEpochMilli();
        return new ZjuSnapshot.Homework("pta:" + id, "", course == null || course.isBlank() ? title : course + " · " + title,
                set.optString("description", ""), HOST + "/problem-sets/" + id, set.optString("updatedAt", ""), deadline, null);
    }
    private JSONObject get(String url, boolean optional) throws Exception {
        HttpUrl target = HttpUrl.get(url);
        if (!target.isHttps() || !(target.host().equals("pintia.cn") || target.host().equals("passport.pintia.cn")))
            throw new IOException("PTA 地址无效");
        Request request = new Request.Builder().url(target).header("Cookie", "PTASession=" + session)
                .header("Accept", "application/json;charset=UTF-8").header("Accept-Language", "zh-CN")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36").build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() == null) throw new IOException("PTA 返回空响应");
            String text = response.body().string();
            JSONObject decoded=null;
            try{decoded=new JSONObject(text);}catch(org.json.JSONException invalid){if(response.isSuccessful())throw new IOException("PTA 响应格式无效");}
            JSONObject error=decoded==null?null:decoded.optJSONObject("error");String code=error==null?"":error.optString("code","");
            if (response.code() == 401 || code.equals("USER_NOT_FOUND") || code.equals("LOGIN_REQUIRED")) throw new AuthExpired();
            if (optional && (response.code() == 403 || response.code() == 404)) {
                if(response.code()==403)whoAmI();
                return new JSONObject();
            }
            if (response.code() == 403) {
                if(target.host().equals("passport.pintia.cn"))throw new AuthExpired();
                whoAmI();throw new IOException("PTA 拒绝访问题集列表");
            }
            if (!response.isSuccessful()) throw new IOException("PTA 请求失败（HTTP " + response.code() + "）");
            return decoded;
        }
    }
    void cancel() { client.dispatcher().cancelAll(); }
    @Override public void close() { cancel(); client.connectionPool().evictAll(); }
}
