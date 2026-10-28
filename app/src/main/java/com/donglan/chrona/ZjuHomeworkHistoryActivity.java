package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.json.JSONObject;

/** Read-only paginated history, with shared settings cards and modal surfaces. */
public final class ZjuHomeworkHistoryActivity extends Activity {
    private static final int PAGE_SIZE = 50;
    private LinearLayout root,content;
    private boolean firstDataPresentation;
    private Button more;
    private long beforeId = Long.MAX_VALUE;
    private boolean loading;
    private LocalDate lastDay;
    private ScrollView viewport;
    private int loadedCount,restoreCount,restoreScroll=-1;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        firstDataPresentation=state==null||state.getBoolean("first_data_presentation",false);
        restoreCount=state==null?0:state.getInt("loaded_count",0);
        restoreScroll=state==null?-1:state.getInt("scroll_y",0);
        root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "作业更新记录");
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        more = new Button(this); UiStyle.button(more, false);
        root.addView(more); more.setOnClickListener(v -> loadMore());
        viewport=SettingsPageLayout.viewport(this,root,state==null);
        viewport.setOnTouchListener((view,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)restoreScroll=-1;
            return false;
        });
        viewport.setOnScrollChangeListener((view,x,y,oldX,oldY)->{if(y!=oldY)restoreScroll=-1;});
        SettingsPageLayout.showFixed(this,viewport);loadMore();
    }

    @Override protected void onSaveInstanceState(Bundle state){
        state.putBoolean("first_data_presentation",firstDataPresentation);
        state.putInt("loaded_count",Math.max(loadedCount,restoreCount));
        state.putInt("scroll_y",restoreScroll>=0?restoreScroll:viewport.getScrollY());
        super.onSaveInstanceState(state);
    }

    private void loadMore() {
        if (loading) return;
        loading = true; more.setEnabled(false); more.setText("正在读取");
        long cursor = beforeId;
        ZjuSync.WORKER.execute(() -> {
            List<ZjuHomeworkStore.History> rows;
            try { rows = new ZjuHomeworkStore(this).history(PAGE_SIZE, cursor); }
            catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()||isFinishing()) return;
                    loading = false; more.setEnabled(true); more.setText("读取失败，重试");
                });
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed()||isFinishing()) return;
                root.removeView(more);
                if (rows.isEmpty() && cursor == Long.MAX_VALUE)
                    content.addView(text("暂无更新记录", 15, false));
                for (ZjuHomeworkStore.History row : rows) addRecord(row);
                loadedCount+=rows.size();
                if(firstDataPresentation)UiMotion.enterPage(content,()->firstDataPresentation=false);
                if (!rows.isEmpty()) beforeId = rows.get(rows.size() - 1).id;
                loading = false;
                if (rows.size() == PAGE_SIZE) {
                    more.setEnabled(true); more.setText("加载更多"); root.addView(more);
                }
                if(loadedCount<restoreCount&&rows.size()==PAGE_SIZE)loadMore();
                else restoreReadingPosition();
            });
        });
    }

    private void restoreReadingPosition(){
        restoreCount=0;
        if(restoreScroll<0)return;
        android.view.ViewTreeObserver.OnPreDrawListener listener=new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){
                viewport.getViewTreeObserver().removeOnPreDrawListener(this);
                if(restoreScroll>=0){int y=restoreScroll;restoreScroll=-1;viewport.scrollTo(0,y);}
                return true;
            }
        };
        viewport.getViewTreeObserver().addOnPreDrawListener(listener);
    }

    private void addRecord(ZjuHomeworkStore.History record) {
        LocalDate day = Instant.ofEpochMilli(record.at).atZone(ZoneId.systemDefault()).toLocalDate();
        if (!day.equals(lastDay)) {
            String date = DateTimeFormatter.ofPattern("M月d日").format(day);
            UiStyle.addSpaced(content, text(day.equals(LocalDate.now()) ? "今天 · " + date : date, 13, false),
                    lastDay == null ? 0 : 8, 8);
            lastDay = day;
        }
        LinearLayout card = new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL);
        UiStyle.card(card); card.setPadding(dp(16), dp(12), dp(12), dp(12));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(record.title, 16, true);
        title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); copy.addView(title);
        TextView meta = text(record.change + " · " + DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(record.at)), 13, false);
        UiStyle.addSpaced(copy, meta, 4, 0);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this); arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setColorFilter(UiStyle.colors(this).muted);
        arrow.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams icon = new LinearLayout.LayoutParams(dp(18), dp(18));
        icon.setMarginStart(dp(12)); card.addView(arrow, icon);
        card.setContentDescription(record.title + "，" + record.change + "，查看变更");
        card.setOnClickListener(v -> showRecord(record)); UiStyle.pressable(card);
        UiStyle.addSpaced(content, card, 0, 8);
    }

    private void showRecord(ZjuHomeworkStore.History record) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = UiDialogs.dialogPanel(this, record.title);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        UiStyle.addSpaced(body, text(record.change, 13, false), 0, 12);
        try {
            JSONObject after = new JSONObject(record.after);
            JSONObject before = record.before == null ? new JSONObject() : new JSONObject(record.before);
            boolean created = record.before == null;
            for (String key : new String[]{"title", "deadline", "submitted"}) {
                if (Objects.equals(before.opt(key), after.opt(key)) || (created && key.equals("title"))) continue;
                String label = key.equals("title") ? "标题" : key.equals("deadline") ? "截止时间" : "提交状态";
                UiStyle.addSpaced(body, text(label, 13, false), 0, 4);
                if (!created) UiStyle.addSpaced(body, text("原：" + value(before, key), 14, false), 0, 4);
                TextView next = text(value(after, key), 16, true);
                next.setTextIsSelectable(true); UiStyle.addSpaced(body, next, 0, 16);
            }
        } catch (Exception error) {
            UiStyle.addSpaced(body, text("变更详情无法读取", 15, false), 0, 12);
        }
        UiDialogs.dialogBody(panel, body);
        Button close = new Button(this); close.setText("关闭"); UiStyle.button(close, false);
        close.setOnClickListener(v -> dialog.dismiss()); panel.addView(close, new LinearLayout.LayoutParams(-1, -2));
        UiDialogs.showFloatingDialog(dialog, panel);
    }

    private TextView text(String value, int size, boolean primary) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        text.setIncludeFontPadding(false);
        if (primary) UiStyle.title(text); else UiStyle.muted(text);
        return text;
    }
    private String value(JSONObject json, String key) {
        if (json.isNull(key) || !json.has(key)) return key.equals("submitted") ? "状态未知" : "未设置";
        if (key.equals("submitted")) return json.optBoolean(key) ? "已提交" : "未提交";
        if (key.equals("deadline")) try {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"))
                    .format(Instant.ofEpochMilli(json.getLong(key)));
        } catch (Exception ignored) { }
        return json.optString(key);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
