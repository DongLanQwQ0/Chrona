package com.donglan.chrona;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.os.PowerManager;
import com.donglan.chrona.data.TaskStore;
import org.json.JSONArray;
import org.json.JSONObject;

/** Authorized diagnosis/recovery export; never edits homework, calendar data or credentials. */
final class HomeworkPhoneDiagnostics {
    static String run(Instrumentation test, android.os.Bundle args) throws Exception {
        Context context = test.getTargetContext();
        if (!"com.donglan.chrona".equals(context.getPackageName()) || !VivoAtomicNotification.supportedManufacturer())
            throw new IllegalStateException("Official vivo app only");
        if ("backup".equals(args.getString("scope"))) {
            java.io.File archive = new java.io.File(context.getExternalFilesDir(null), "before-homework-attention-upgrade.zip");
            ChronaDataBackup.write(context, android.net.Uri.fromFile(archive), false);
            if (!archive.isFile() || archive.length() == 0) throw new java.io.IOException("Upgrade recovery backup missing");
            return "PASS: local recovery archive without API key; bytes=" + archive.length();
        }
        JSONObject report = new JSONObject().put("at", System.currentTimeMillis());
        var power = context.getSystemService(PowerManager.class);
        report.put("interactive", power != null && power.isInteractive());
        var settings = new ZjuSettingsStore(context);
        report.put("schoolEnabled", settings.enabled()).put("schoolStatus", settings.status());
        for (String key : new String[]{"homework_attempt", "homework_success", "last_success"})
            report.put(key, settings.prefs.getLong(key, 0));
        var state = new ZjuHomeworkStore(context).state();
        report.put("homeworkEnabled", state.enabled).put("capture", state.captureId);
        var notice = context.getSharedPreferences("homework_notice", 0);
        report.put("noticeExpiry", notice.getLong("expiry", 0)).put("noticeCapture", notice.getLong("capture", 0));
        JSONArray pending = new JSONArray(notice.getString("items", "[]")), identity = new JSONArray();
        for (int i = 0; i < pending.length(); i++) {
            JSONObject item = pending.getJSONObject(i);
            identity.put(new JSONObject().put("id", item.optLong("id")).put("title", item.optString("title")));
        }
        report.put("noticeItems", identity);
        JSONArray active = new JSONArray();
        for (var row : context.getSystemService(NotificationManager.class).getActiveNotifications())
            if (row.getId() == HomeworkNotice.ID) active.put(new JSONObject().put("id", row.getId())
                    .put("postTime", row.getPostTime()).put("timeout", row.getNotification().getTimeoutAfter()));
        report.put("activeHomeworkNotices", active);
        long begin = java.time.LocalDate.now().atTime(18, 45).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (TaskStore store = new TaskStore(context)) {
            JSONArray history = new JSONArray();
            try (var rows = store.getReadableDatabase().rawQuery("SELECT title,change,at_millis,remote_updated_at FROM zju_homework_history WHERE at_millis>=? OR title LIKE ? ORDER BY id DESC LIMIT 40", new String[]{Long.toString(begin), "%微信小程序%"})) {
                while (rows.moveToNext()) history.put(new JSONObject().put("title", rows.getString(0)).put("change", rows.getString(1))
                        .put("at", rows.getLong(2)).put("remoteUpdated", rows.getString(3)));
            }
            report.put("recentHistory", history);
            JSONArray work = new JSONArray();
            try (var rows = store.getReadableDatabase().rawQuery("SELECT m.candidate_id,m.ignored,m.snapshot,c.title,c.completed_at_millis FROM zju_homework_map m LEFT JOIN event_candidates c ON c.id=m.candidate_id WHERE m.capture_id=? ORDER BY m.candidate_id DESC LIMIT 12", new String[]{Long.toString(state.captureId)})) {
                while (rows.moveToNext()) {
                    JSONObject snapshot = new JSONObject(rows.getString(2));
                    work.put(new JSONObject().put("id", rows.getLong(0)).put("ignored", rows.getInt(1)).put("title", rows.getString(3))
                            .put("completedAt", rows.isNull(4) ? JSONObject.NULL : rows.getLong(4))
                            .put("remoteUpdated", snapshot.optString("updatedAt")).put("submitted", snapshot.optBoolean("submitted")));
                }
            }
            report.put("recentHomework", work);
        }
        return report.toString();
    }
}
