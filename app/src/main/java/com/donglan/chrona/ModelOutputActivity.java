package com.donglan.chrona;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.processing.StreamingOutputStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads long model output one bounded page at a time. */
public final class ModelOutputActivity extends Activity {
    private long taskId;
    private int index;
    private TextView pageLabel;
    private TextView output;
    private Button previous;
    private Button next;
    private Button reload;
    private ScrollView scroll;
    private boolean pageLoaded;
    private boolean firstDataPresentation;
    private int shownIndex;
    private StreamingOutputStore outputStore;
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private int readGeneration;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        firstDataPresentation = state == null || state.getBoolean("first_data_presentation", false);
        taskId = getIntent().getLongExtra("task_id", -1);
        outputStore = new StreamingOutputStore(this);
        if (state != null) index = state.getInt("page_index");
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        CollapsingPageLayout root = SettingsPageLayout.workspaceContent(this);
        SettingsPageLayout.header(this, root, "模型完整输出");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        UiStyle.glass(card);
        pageLabel = new TextView(this);
        pageLabel.setTextSize(14);
        UiStyle.title(pageLabel);
        card.addView(pageLabel);
        scroll = new ScrollView(this);
        PressFeedback.protectViewport(scroll);
        output = new TextView(this);
        output.setTextSize(12);
        output.setTextIsSelectable(true);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        UiStyle.muted(output);
        scroll.addView(output);
        LinearLayout.LayoutParams body = new LinearLayout.LayoutParams(-1, 0, 1);
        body.setMargins(0, dp(10), 0, dp(12));
        FrameLayout outputHost = new FrameLayout(this);
        outputHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        card.addView(outputHost, body);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, dp(8));
        previous = action("上一页", () -> { index--; showPage(); });
        next = action("下一页", () -> { index++; showPage(); });
        reload = action("刷新", this::showPage);
        LinearLayout.LayoutParams previousParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        previousParams.setMarginEnd(dp(8));
        actions.addView(previous, previousParams);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        nextParams.setMarginEnd(dp(8));
        actions.addView(next, nextParams);
        actions.addView(reload, new LinearLayout.LayoutParams(0, dp(50), 1));
        card.addView(actions);
        root.addView(card, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setScrollingBody(card);
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, root);
        setContentView(stage);
        showPage();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("page_index", index);
        state.putBoolean("first_data_presentation", firstDataPresentation);
    }

    private Button action(String text, Runnable onClick) {
        Button button = new Button(this);
        button.setText(text);
        UiStyle.button(button, false);
        button.setOnClickListener(view -> onClick.run());
        return button;
    }

    private void showPage() {
        int generation = ++readGeneration;
        int requestedIndex = index;
        previous.setEnabled(false);
        next.setEnabled(false);
        reload.setEnabled(false);
        pageLabel.setText("正在加载输出…");
        reader.execute(() -> loadPage(generation, requestedIndex));
    }

    private void loadPage(int generation, int requestedIndex) {
        try {
            long bytes = outputStore.renderedLength(taskId);
            int pages = Math.max(1, (int) ((bytes + StreamingOutputStore.PAGE_BYTES - 1)
                    / StreamingOutputStore.PAGE_BYTES));
            int loadedIndex = Math.max(0, Math.min(requestedIndex, pages - 1));
            String part = outputStore.readPage(taskId, loadedIndex);
            String text = part.isEmpty() ? "尚无模型输出。" : part;
            runOnUiThread(() -> {
                if (generation != readGeneration || isFinishing() || isDestroyed()) return;
                index = loadedIndex;
                pageLabel.setText("第 " + (index + 1) + " / " + pages + " 页 · " + bytes + " 字节");
                previous.setEnabled(index > 0);
                next.setEnabled(index + 1 < pages);
                reload.setEnabled(true);
                Runnable bind = () -> { output.setText(text); scroll.scrollTo(0, 0); };
                if (pageLoaded) UiMotion.swap(scroll, scroll, bind, Integer.compare(loadedIndex, shownIndex));
                else {
                    bind.run();
                    if (firstDataPresentation) { firstDataPresentation = false; UiMotion.enterPage(scroll); }
                }
                pageLoaded = true; shownIndex = loadedIndex;
            });
        } catch (Exception exception) {
            runOnUiThread(() -> {
                if (generation != readGeneration || isFinishing() || isDestroyed()) return;
                pageLabel.setText("读取失败");
                output.setText("无法读取输出：" + exception.getMessage());
                reload.setEnabled(true);
            });
        }
    }

    @Override protected void onDestroy() {
        ++readGeneration;
        reader.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onPause() {
        if (scroll != null) UiMotion.finishSwap(scroll);
        super.onPause();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
