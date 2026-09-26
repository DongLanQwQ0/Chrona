package com.donglan.chrona;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.processing.StreamingOutputStore;

/** Reads long model output one bounded page at a time. */
public final class ModelOutputActivity extends Activity {
    private long taskId;
    private int index;
    private TextView pageLabel;
    private TextView output;
    private Button previous;
    private Button next;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        taskId = getIntent().getLongExtra("task_id", -1);
        if (state != null) index = state.getInt("page_index");
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setFitsSystemWindows(false);
        UiStyle.back(this, root);
        TextView title = new TextView(this);
        title.setText("模型完整输出");
        title.setTextSize(26);
        UiStyle.title(title);
        root.addView(title);
        TextView note = new TextView(this);
        note.setText("按页浏览长输出。解析过程中点刷新可查看新内容。");
        note.setTextSize(14);
        UiStyle.muted(note);
        UiStyle.addSpaced(root, note, 6, 12);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        UiStyle.glass(card);
        pageLabel = new TextView(this);
        pageLabel.setTextSize(14);
        UiStyle.title(pageLabel);
        card.addView(pageLabel);
        scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextSize(12);
        output.setTextIsSelectable(true);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        UiStyle.muted(output);
        scroll.addView(output);
        LinearLayout.LayoutParams body = new LinearLayout.LayoutParams(-1, 0, 1);
        body.setMargins(0, dp(10), 0, dp(12));
        card.addView(scroll, body);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        previous = action("上一页", () -> { index--; showPage(); });
        next = action("下一页", () -> { index++; showPage(); });
        Button reload = action("刷新", this::showPage);
        actions.addView(previous, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(next, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(reload, new LinearLayout.LayoutParams(0, dp(50), 1));
        card.addView(actions);
        root.addView(card, new LinearLayout.LayoutParams(-1, 0, 1));
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, root);
        setContentView(stage);
        showPage();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("page_index", index);
    }

    private Button action(String text, Runnable onClick) {
        Button button = new Button(this);
        button.setText(text);
        UiStyle.button(button, false);
        button.setOnClickListener(view -> onClick.run());
        return button;
    }

    private void showPage() {
        StreamingOutputStore store = new StreamingOutputStore(this);
        long bytes = store.length(taskId);
        int pages = Math.max(1, (int) ((bytes + StreamingOutputStore.PAGE_BYTES - 1)
                / StreamingOutputStore.PAGE_BYTES));
        index = Math.max(0, Math.min(index, pages - 1));
        pageLabel.setText("第 " + (index + 1) + " / " + pages + " 页 · " + bytes + " 字节");
        previous.setEnabled(index > 0);
        next.setEnabled(index + 1 < pages);
        try {
            String part = store.page(taskId, index);
            String text = part.isEmpty() ? "尚无模型输出。" : part;
            boolean first = output.getText().length() == 0;
            output.setText(text);
            scroll.scrollTo(0, 0);
            if (!first) UiStyle.pop(output);
        } catch (Exception exception) {
            output.setText("无法读取输出：" + exception.getMessage());
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
