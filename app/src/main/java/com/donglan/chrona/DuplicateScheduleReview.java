package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import com.donglan.chrona.data.*;
import org.json.JSONArray;
import java.util.*;

/** One foreground owner for automatic discovery, across all candidate writers and screens. */
final class DuplicateScheduleReview {
    private static final long CHECK_INTERVAL_MS = 2000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Activity foreground;
    private static Dialog dialog;
    private static boolean checking;
    private static long scannedRevision = -1;
    private static final Runnable CHECK = DuplicateScheduleReview::check;

    static void resumed(Activity activity) {
        foreground = activity;
        MAIN.removeCallbacks(CHECK);
        MAIN.postDelayed(CHECK, 600);
    }

    static void paused(Activity activity) {
        if (foreground != activity) return;
        foreground = null;
        MAIN.removeCallbacks(CHECK);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    private static void check() {
        Activity owner = foreground;
        if (owner == null) return;
        if (!checking && !(owner instanceof CandidateMergeActivity)
                && (dialog == null || !dialog.isShowing())
                && owner.getWindow().getDecorView().hasWindowFocus()) {
            checking = true;
            Context app = owner.getApplicationContext();
            new Thread(() -> {
                CandidateMerges.Preview result = null;
                long revision = -1;
                try {
                    synchronized (AndroidSync.LOCK) {
                        try (TaskStore store = new TaskStore(app)) {
                            revision = store.dataRevision();
                        }
                        if (revision != scannedRevision) result = CandidateMerges.scan(app);
                    }
                } catch (RuntimeException error) {
                    // Keep the previous revision so a transient database failure is retried.
                    revision = -1;
                }
                CandidateMerges.Preview preview = result;
                long observed = revision;
                MAIN.post(() -> {
                    checking = false;
                    // Do not consume a result while backgrounded or while another modal owns focus.
                    if (foreground != owner || !owner.getWindow().getDecorView().hasWindowFocus()) return;
                    if (observed >= 0) scannedRevision = preview == null ? observed : preview.revision;
                    if (preview == null || preview.groups.isEmpty()) return;
                    android.content.SharedPreferences preferences = app.getSharedPreferences("duplicate_review", Context.MODE_PRIVATE);
                    Set<String> seen = new HashSet<>(preferences.getStringSet("shown", Collections.emptySet()));
                    List<List<EventCandidate>> fresh = new ArrayList<>();
                    for (List<EventCandidate> group : preview.groups) if (!seen.contains(signature(group, preview))) fresh.add(group);
                    if (fresh.isEmpty()) return;
                    // Persist only on actual presentation; back/outside/cancel all acknowledge this batch.
                    for (List<EventCandidate> group : fresh) seen.add(signature(group, preview));
                    preferences.edit().putStringSet("shown", seen).apply();
                    show(owner, new CandidateMerges.Preview(preview.revision, fresh, preview.tasks));
                });
            }, "chrona-duplicate-review").start();
        }
        MAIN.postDelayed(CHECK, CHECK_INTERVAL_MS);
    }

    private static String signature(List<EventCandidate> group, CandidateMerges.Preview preview) {
        JSONArray members = new JSONArray();
        for (EventCandidate candidate : group) {
            TaskRecord task = preview.tasks.get(candidate.taskId);
            members.put(new JSONArray().put(candidate.id).put(candidate.taskId).put(task.source)
                    .put(DuplicateCandidates.key(candidate)).put(candidate.title).put(candidate.location)
                    .put(candidate.description).put(candidate.timeZoneId).put(candidate.reminderMinutesBefore)
                    .put(candidate.needsConfirmation).put(candidate.repeatRule == null ? "" : candidate.repeatRule.toJson())
                    .put(candidate.repeatExceptions).put(candidate.repeatStoppedAtMillis));
        }
        return members.toString();
    }

    private static void show(Activity owner, CandidateMerges.Preview preview) {
        Dialog window = new Dialog(owner);
        dialog = window;
        LinearLayout panel = UiDialogs.dialogPanel(owner, "发现重复日程");
        LinearLayout body = new LinearLayout(owner);
        body.setOrientation(LinearLayout.VERTICAL);
        Set<Long> selected = new HashSet<>();
        boolean[] submitting = {false};
        Button confirm = new Button(owner);
        confirm.setText("合并所选");
        UiStyle.button(confirm, true);
        confirm.setEnabled(false);
        for (List<EventCandidate> group : preview.groups) {
            EventCandidate first = group.get(0);
            CheckBox choice = new CheckBox(owner);
            choice.setText(first.title + " · " + group.size() + " 份");
            choice.setTextColor(UiStyle.colors(owner).text);
            choice.setTextSize(16);
            choice.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(first.id); else selected.remove(first.id);
                int count = 0;
                for (List<EventCandidate> values : preview.groups) if (selected.contains(values.get(0).id)) count += values.size() - 1;
                confirm.setText(count == 0 ? "合并所选" : "确认合并 " + count + " 份");
                confirm.setEnabled(!submitting[0] && count > 0);
            });
            UiStyle.addSpaced(body, choice, 0, 8);
            TextView time = new TextView(owner);
            time.setText(CandidateMergeActivity.summary(first));
            time.setTextSize(13);
            time.setTextColor(UiStyle.colors(owner).muted);
            UiStyle.addSpaced(body, time, 0, 8);
            for (EventCandidate candidate : group) {
                TaskRecord task = preview.tasks.get(candidate.taskId);
                Button source = new Button(owner);
                source.setText((candidate == first ? "保留 · " : "合并 · ") + candidate.title + "\n"
                        + task.source + (candidate.location == null || candidate.location.isEmpty() ? "" : " · " + candidate.location));
                UiStyle.button(source, false);
                source.setOnClickListener(view -> {
                    window.dismiss();
                    owner.startActivity(new Intent(owner, TaskDetailActivity.class).putExtra("task_id", task.id)
                            .putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID, candidate.id));
                });
                UiStyle.addSpaced(body, source, 0, 8);
            }
        }
        UiDialogs.dialogBody(panel, body);
        LinearLayout actions = new LinearLayout(owner);
        Button cancel = new Button(owner);
        cancel.setText("暂不合并");
        UiStyle.button(cancel, false);
        cancel.setOnClickListener(view -> window.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams confirmLayout = new LinearLayout.LayoutParams(0, -2, 1);
        confirmLayout.leftMargin = Math.round(8 * owner.getResources().getDisplayMetrics().density);
        actions.addView(confirm, confirmLayout);
        confirm.setOnClickListener(view -> {
            if (submitting[0]) return;
            submitting[0] = true;
            Set<Long> chosen = new HashSet<>(selected);
            freeze(body);
            confirm.setEnabled(false);
            cancel.setEnabled(false);
            window.setCancelable(false);
            window.setCanceledOnTouchOutside(false);
            Context app = owner.getApplicationContext();
            new Thread(() -> {
                String message;
                boolean success;
                try { message = "已合并 " + CandidateMerges.merge(app, preview.revision, chosen) + " 份重复日程"; success = true; }
                catch (Exception error) { message = "日程已变化或合并失败，请重新扫描"; success = false; }
                String text = message;
                boolean merged = success;
                MAIN.post(() -> {
                    if (owner.isDestroyed() || foreground != owner) return;
                    Toast.makeText(owner, text, Toast.LENGTH_LONG).show();
                    window.dismiss();
                    if (!merged) owner.startActivity(new Intent(owner, CandidateMergeActivity.class));
                });
            }, "chrona-duplicate-confirm").start();
        });
        panel.addView(actions);
        UiDialogs.showFloatingDialog(window, panel);
    }

    private static void freeze(android.view.View view) {
        view.setEnabled(false);
        if (view instanceof android.view.ViewGroup group)
            for (int index = 0; index < group.getChildCount(); index++) freeze(group.getChildAt(index));
    }

    private DuplicateScheduleReview() { }
}
