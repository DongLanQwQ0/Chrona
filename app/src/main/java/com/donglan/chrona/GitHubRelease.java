package com.donglan.chrona;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Public stable releases only; no credentials or user data are sent. */
final class GitHubRelease {
    static final String REPOSITORY = "DongLanQwQ0/Chrona";
    static final String RELEASES_URL = "https://github.com/" + REPOSITORY + "/releases";
    static final String API_URL = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    final String version, notes, pageUrl, apkUrl;

    private GitHubRelease(String version, String notes, String pageUrl, String apkUrl) {
        this.version = version;
        this.notes = notes;
        this.pageUrl = pageUrl;
        this.apkUrl = apkUrl;
    }

    static String version(String value) {
        String normalized = value == null ? "" : value.trim().replaceFirst("^[vV]", "");
        if (!normalized.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
            throw new IllegalArgumentException("版本标签应为 v主版本.次版本.修订号");
        return normalized;
    }

    static boolean newer(String latest, String installed) {
        String[] left = version(latest).split("\\.");
        String[] right = version(installed).split("\\.");
        for (int i = 0; i < left.length; i++) {
            int result = new BigInteger(left[i]).compareTo(new BigInteger(right[i]));
            if (result != 0) return result > 0;
        }
        return false;
    }

    private static boolean repositoryUrl(String value, String prefix) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1
                    && uri.getRawPath().startsWith("/" + REPOSITORY + prefix);
        } catch (IllegalArgumentException exception) { return false; }
    }

    static GitHubRelease parse(String json) throws Exception {
        JSONObject release = new JSONObject(json);
        if (release.optBoolean("draft") || release.optBoolean("prerelease"))
            throw new IOException("此版本尚未正式发布");
        String version = version(release.getString("tag_name"));
        String page = release.getString("html_url");
        if (!repositoryUrl(page, "/releases/tag/")) throw new IOException("版本页面地址无效");
        String apk = "";
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String link = asset.optString("browser_download_url");
            if ("uploaded".equals(asset.optString("state")) && asset.optLong("size", 0) > 0
                    && asset.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")
                    && repositoryUrl(link, "/releases/download/")) {
                apk = link;
                break;
            }
        }
        return new GitHubRelease(version, release.isNull("body") ? "" : release.optString("body"), page, apk);
    }

    static GitHubRelease fetch() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
        connection.setRequestProperty("User-Agent", "Chrona-Android");
        try {
            int status = connection.getResponseCode();
            if (status == 404) throw new IOException("暂未找到正式版本，仓库可能还未发布 Release");
            if (status == 403 || status == 429) throw new IOException("GitHub 暂时限制请求，请稍后重试");
            if (status != 200) throw new IOException("GitHub 返回 HTTP " + status);
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("检查已取消");
                    if (bytes.size() + count > MAX_RESPONSE_BYTES) throw new IOException("版本信息过大");
                    bytes.write(buffer, 0, count);
                }
                return parse(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }
}
