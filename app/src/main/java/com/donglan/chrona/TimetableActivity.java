package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.timetable.Timetable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A local, all-courses weekly overview. Imports never write to the system calendar or AI. */
public final class TimetableActivity extends Activity {
    private static final int PICK_ICS = 61;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.CHINA);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private static final class Session {
        WeakReference<TimetableActivity> owner = new WeakReference<>(null);
        TimetableStore.Document document, pending;
        boolean initialized, busy;
        String error, operation;
        int scrollX, scrollY;
    }

    private Session session;
    private LinearLayout shell;
    private FrameLayout body;
    private TextView source;
    private Button importButton;
    private ScrollView vertical;
    private HorizontalScrollView horizontal;
    private Dialog floating;
    private boolean resumed;
    private int gridWidth;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        session = getLastNonConfigurationInstance() instanceof Session retained ? retained : new Session();
        session.owner = new WeakReference<>(this);
        if (state != null) {
            session.scrollX = state.getInt("scroll_x");
            session.scrollY = state.getInt("scroll_y");
        }
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(20), dp(16), dp(20), dp(12));
        UiStyle.page(this, shell);
        shell.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_left);
        back.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setContentDescription("返回");
        UiStyle.acrylicChoice(back, false, UiStyle.RADIUS_PILL, false);
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        UiStyle.pressable(back);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label("课表", 26, true);
        title.setPadding(dp(12), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        importButton = new Button(this);
        importButton.setText("导入 ICS");
        UiStyle.button(importButton, false);
        importButton.setOnClickListener(view -> pickIcs());
        header.addView(importButton, new LinearLayout.LayoutParams(-2, dp(48)));
        UiStyle.addSpaced(shell, header, 0, 12);
        source = label("", 12, false);
        source.setMaxLines(2);
        source.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiStyle.addSpaced(shell, source, 0, 12);
        body = new FrameLayout(this);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, shell);
        setContentView(stage);
        body.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left > 0 && gridWidth != right - left && session.document != null) {
                rememberScroll();
                render();
            }
        });
        render();
        if (!session.initialized && !session.busy) load();
    }

    private void load() {
        Session target = session;
        Context app = getApplicationContext();
        begin("读取课表…");
        IO.execute(() -> {
            TimetableStore.Document loaded = null;
            String error = null;
            try { loaded = new TimetableStore(app).load(); }
            catch (Exception exception) { error = message(exception); }
            TimetableStore.Document result = loaded;
            String failure = error;
            MAIN.post(() -> {
                target.initialized = true;
                target.busy = false;
                target.document = result;
                target.error = failure;
                notifyOwner(target);
            });
        });
    }

    private void pickIcs() {
        if (session.busy) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        // Several campus portals label .ics as text/plain or application/octet-stream.
        picker.setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(picker, PICK_ICS); }
        catch (android.content.ActivityNotFoundException exception) { Feedback.show(this, "没有可用的文件选择器"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ICS || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (session.busy) return;
        Uri uri = data.getData();
        Session target = session;
        Context app = getApplicationContext();
        ZoneId zone = ZoneId.systemDefault();
        begin("正在读取 ICS…");
        IO.execute(() -> {
            TimetableStore.Document imported = null;
            String error = null;
            try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("无法读取所选文件");
                imported = TimetableStore.prepare(displayName(app, uri), TimetableStore.readIcs(input), zone);
            } catch (Exception exception) { error = message(exception); }
            TimetableStore.Document result = imported;
            String failure = error;
            MAIN.post(() -> {
                target.busy = false;
                target.pending = result;
                target.error = failure;
                notifyOwner(target);
            });
        });
    }

    private void confirmImport() {
        TimetableStore.Document pending = session.pending;
        if (pending == null || floating != null && floating.isShowing()) return;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = panel("导入课表");
        UiStyle.addSpaced(panel, label(pending.name, 14, false), 0, 8);
        UiStyle.addSpaced(panel, label(pending.table.courseCount + " 门课程 · "
                + pending.table.blocks.size() + " 个时段", 18, true), 0, 8);
        UiStyle.addSpaced(panel, label("汇总周一至周日 · " + pending.table.zone.getId(), 13, false), 0, 16);
        if (session.document != null)
            UiStyle.addSpaced(panel, label("导入后替换当前课表", 14, false), 0, 12);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button cancel = action("取消", false, () -> {
            session.pending = null;
            dialog.dismiss();
        });
        actions.addView(cancel);
        Button save = action(session.document == null ? "导入" : "替换课表", true, () -> {
            dialog.dismiss();
            save(pending);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMarginStart(dp(8));
        actions.addView(save, params);
        panel.addView(actions);
        dialog.setOnCancelListener(ignored -> session.pending = null);
        floating = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void save(TimetableStore.Document document) {
        Session target = session;
        Context app = getApplicationContext();
        session.pending = null;
        begin("保存课表…");
        IO.execute(() -> {
            String error = null;
            try { new TimetableStore(app).save(document); }
            catch (Exception exception) { error = message(exception); }
            String failure = error;
            MAIN.post(() -> {
                target.busy = false;
                target.error = failure;
                if (failure == null) {
                    target.document = document;
                    target.scrollX = target.scrollY = 0;
                }
                notifyOwner(target);
            });
        });
    }

    private void begin(String operation) {
        rememberScroll();
        session.busy = true;
        session.operation = operation;
        session.error = null;
        render();
    }

    private void render() {
        if (isFinishing() || isDestroyed()) return;
        importButton.setEnabled(!session.busy);
        importButton.setText(session.busy ? "读取中…" : "导入 ICS");
        body.removeAllViews();
        vertical = null;
        horizontal = null;
        gridWidth = body.getWidth();
        TimetableStore.Document document = session.document;
        if (document == null) {
            source.setVisibility(View.GONE);
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            UiStyle.glass(empty);
            empty.setPadding(dp(24), dp(28), dp(24), dp(28));
            TextView heading = label(session.busy ? session.operation
                    : session.error == null ? "导入你的课表" : "未能读取课表", 21, true);
            heading.setGravity(Gravity.CENTER);
            UiStyle.addSpaced(empty, heading, 0, 12);
            TextView hint = label(session.busy ? "" : session.error == null
                    ? "选择 ICS 文件，查看一周课程" : session.error, 14, false);
            hint.setGravity(Gravity.CENTER);
            empty.addView(hint);
            if (!session.busy) {
                Button open = action("选择 ICS 文件", true, this::pickIcs);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.topMargin = dp(20);
                empty.addView(open, params);
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
            body.addView(empty, params);
        } else {
            source.setVisibility(View.VISIBLE);
            source.setText(document.name + "\n" + document.table.courseCount + " 门课程 · 全部课程 · " + document.table.zone.getId());
            UiStyle.glass(body);
            body.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(UiStyle.RADIUS_PANEL));
                }
            });
            body.setClipToOutline(true);
            vertical = new ScrollView(this);
            vertical.setVerticalScrollBarEnabled(false);
            horizontal = new HorizontalScrollView(this);
            horizontal.setHorizontalScrollBarEnabled(false);
            horizontal.setFillViewport(true);
            int available = gridWidth > 0 ? gridWidth : getResources().getDisplayMetrics().widthPixels - dp(40);
            TimetableGrid grid = new TimetableGrid(this, document.table, available, this::showCourse);
            horizontal.addView(grid);
            vertical.addView(horizontal, new ScrollView.LayoutParams(-1, -2));
            body.addView(vertical, new FrameLayout.LayoutParams(-1, -1));
            ScrollView scroll = vertical;
            HorizontalScrollView sideways = horizontal;
            int x = session.scrollX, y = session.scrollY;
            scroll.post(() -> { scroll.scrollTo(0, y); sideways.scrollTo(x, 0); });
            if (session.error != null && resumed) {
                Feedback.showLong(this, session.error);
                session.error = null;
            }
        }
        if (session.pending != null && resumed) confirmImport();
    }

    private void showCourse(Timetable.Block block) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = panel(block.title);
        String when = TimetableGrid.DAYS[block.day] + " · " + (block.allDay ? "全天"
                : Timetable.time(block.startMinute) + "–" + Timetable.time(block.endMinute));
        TextView time = label(when, 16, true);
        time.setTextColor(UiStyle.colors(this).primary);
        UiStyle.addSpaced(panel, time, 0, 12);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (!block.location.isEmpty()) detail(details, "地点", block.location);
        LinkedHashSet<String> descriptions = new LinkedHashSet<>();
        for (Timetable.Occurrence entry : block.occurrences())
            if (!entry.description.isEmpty()) descriptions.add(entry.description);
        if (!descriptions.isEmpty()) detail(details, "课程信息", String.join("\n\n", descriptions));
        LinkedHashSet<String> rules = new LinkedHashSet<>();
        TreeSet<String> dates = new TreeSet<>();
        for (Timetable.Occurrence entry : block.occurrences()) {
            if (entry.openEnded) {
                rules.add(entry.recurrence);
            } else {
                String value = entry.allDay ? DATE.format(entry.start) : DATE.format(entry.start) + "  " + TIME.format(entry.start);
                value += entry.allDay ? (entry.end.toLocalDate().minusDays(1).isAfter(entry.start.toLocalDate())
                        ? "–" + DATE.format(entry.end.minusDays(1)) : "")
                        : "–" + (entry.start.toLocalDate().equals(entry.end.toLocalDate())
                                ? TIME.format(entry.end) : DATE_TIME.format(entry.end));
                dates.add(value);
            }
        }
        if (!rules.isEmpty()) {
            detail(details, "重复安排", String.join("\n", rules));
            detail(details, "开始日期", DATE.format(block.occurrences().get(0).start));
        }
        if (!dates.isEmpty()) {
            UiStyle.addSpaced(details, label("上课日期 · " + dates.size() + " 次", 12, false), 0, 5);
            java.util.ArrayList<String> rows = new java.util.ArrayList<>(dates);
            TextView dateList = label("", 15, false);
            dateList.setTextColor(UiStyle.colors(this).text);
            dateList.setTextIsSelectable(true);
            dateList.setLineSpacing(dp(4), 1);
            details.addView(dateList);
            final int pageSize = 64;
            int[] page = {0};
            Runnable updateDates = () -> {
                int first = page[0] * pageSize;
                dateList.setText(String.join("\n", rows.subList(first, Math.min(rows.size(), first + pageSize))));
            };
            updateDates.run();
            if (rows.size() > pageSize) {
                LinearLayout navigation = new LinearLayout(this);
                Button previous = action("上一页", false, () -> { });
                Button next = action("下一页", false, () -> { });
                Runnable updatePage = () -> {
                    updateDates.run();
                    previous.setEnabled(page[0] > 0);
                    next.setEnabled((page[0] + 1) * pageSize < rows.size());
                };
                previous.setOnClickListener(view -> { page[0]--; updatePage.run(); });
                next.setOnClickListener(view -> { page[0]++; updatePage.run(); });
                navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
                navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
                details.addView(navigation);
                updatePage.run();
            }
        }
        detail(details, "时区", session.document.table.zone.getId());
        scroll.addView(details);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * .48f);
        details.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(200), (int) (getResources().getDisplayMetrics().widthPixels * .9f) - dp(40)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(maxHeight, details.getMeasuredHeight())));
        Button close = action("关闭", false, dialog::dismiss);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-2, -2);
        closeParams.gravity = Gravity.END;
        closeParams.topMargin = dp(12);
        panel.addView(close, closeParams);
        floating = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void detail(LinearLayout parent, String heading, String value) {
        UiStyle.addSpaced(parent, label(heading, 12, false), 0, 5);
        TextView text = label(value, 15, false);
        text.setTextColor(UiStyle.colors(this).text);
        text.setTextIsSelectable(true);
        text.setLineSpacing(dp(4), 1);
        UiStyle.addSpaced(parent, text, 0, 16);
    }

    private LinearLayout panel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        UiStyle.glass(panel);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        TextView heading = label(title, 21, true);
        heading.setMaxLines(3);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiStyle.addSpaced(panel, heading, 0, 12);
        return panel;
    }
    private TextView label(String value, int size, boolean heading) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setIncludeFontPadding(false);
        if (heading) UiStyle.title(label); else UiStyle.muted(label);
        return label;
    }
    private Button action(String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        UiStyle.button(button, primary);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) { /* The display name is optional metadata. */ }
        return "导入的课表";
    }
    private static String message(Exception exception) {
        if (exception instanceof SecurityException) return "无法读取文件，请重新选择 ICS";
        return exception.getMessage() == null ? "课表读取失败，请检查 ICS 文件" : exception.getMessage();
    }
    private static void notifyOwner(Session target) {
        TimetableActivity owner = target.owner.get();
        if (owner != null) owner.render();
    }
    private void rememberScroll() {
        if (vertical != null) session.scrollY = vertical.getScrollY();
        if (horizontal != null) session.scrollX = horizontal.getScrollX();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (session.pending != null) confirmImport();
        if (session.error != null && session.document != null) {
            Feedback.showLong(this, session.error);
            session.error = null;
        }
    }
    @Override protected void onPause() { resumed = false; rememberScroll(); super.onPause(); }
    @Override public Object onRetainNonConfigurationInstance() { rememberScroll(); return session; }
    @Override protected void onSaveInstanceState(Bundle state) {
        rememberScroll();
        state.putInt("scroll_x", session.scrollX);
        state.putInt("scroll_y", session.scrollY);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (session.owner.get() == this) session.owner.clear();
        if (floating != null && floating.isShowing()) floating.dismiss();
        super.onDestroy();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
