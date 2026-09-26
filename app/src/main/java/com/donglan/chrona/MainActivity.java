package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.debug.DiagLog;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.processing.ProcessingJobService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A focused capture page for text, voice, images and Android's share sheet. */
public final class MainActivity extends Activity {
    /** Guards against a pasted block turning into hundreds of paid parsing requests. */
    private static final int MAX_BATCH_TASKS = 20;
    private static final int PICK_IMAGE_REQUEST = 12;
    private static final int VOICE_REQUEST = 13;
    private static final String STATE_PENDING_IMAGE = "pending_image";
    private static final String STATE_TEXT = "draft_text";
    private static final String STATE_SPLIT = "draft_split";

    private EditText input;
    private CheckBox splitInput;
    private Button voiceInput;
    private Button pickImage;
    private ImageView attachment;
    private Button removeImage;
    /** Stored name of an image attached to the next submit, or null. */
    private String pendingImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeStore.apply(this);
        super.onCreate(savedInstanceState);
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(12));
        root.setFitsSystemWindows(true);
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root);
        UiStyle.back(this, root);

        TextView title = new TextView(this);
        title.setText("记录一件事");
        title.setTextSize(26);
        UiStyle.title(title);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("文字、图片或语音都可以。保存后自动解析，你可以继续记录下一件事。");
        hint.setPadding(0, dp(6), 0, dp(12));
        UiStyle.muted(hint);
        root.addView(hint);

        input = new EditText(this);
        input.setHint("粘贴消息、链接或描述一件事…");
        input.setMinLines(4);
        input.setGravity(android.view.Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        UiStyle.input(input);
        UiStyle.glass(input);
        UiStyle.addSpaced(root, input, 0, 12);

        TextView toolsTitle = new TextView(this);
        toolsTitle.setText("快速添加");
        toolsTitle.setTextSize(16);
        UiStyle.title(toolsTitle);
        UiStyle.addSpaced(root, toolsTitle, 8, 5);
        LinearLayout quickTools = new LinearLayout(this);
        quickTools.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = new Button(this);
        paste.setText("剪贴板");
        paste.setContentDescription("读取剪贴板文字");
        paste.setOnClickListener(view -> pasteClipboard());
        UiStyle.button(paste, false);
        addTool(quickTools, paste);

        voiceInput = new Button(this);
        voiceInput.setText("语音");
        voiceInput.setContentDescription("语音输入");
        voiceInput.setOnClickListener(view -> startVoiceInput());
        UiStyle.button(voiceInput, false);
        addTool(quickTools, voiceInput);

        pickImage = new Button(this);
        pickImage.setText("图片");
        pickImage.setContentDescription("选择图片");
        pickImage.setOnClickListener(view -> pickImage());
        UiStyle.button(pickImage, false);
        addTool(quickTools, pickImage);
        UiStyle.addSpaced(root, quickTools, 0, 14);

        attachment = new ImageView(this);
        attachment.setAdjustViewBounds(true);
        attachment.setMaxHeight(dp(180));
        attachment.setVisibility(View.GONE);
        root.addView(attachment);

        removeImage = new Button(this);
        removeImage.setText("移除图片");
        removeImage.setVisibility(View.GONE);
        removeImage.setOnClickListener(view -> clearPendingImage());
        UiStyle.button(removeImage, false);
        UiStyle.addSpaced(root, removeImage, 4, 4);

        splitInput = new CheckBox(this);
        splitInput.setText("按行拆分为多条");
        root.addView(splitInput);

        Button save = new Button(this);
        save.setText("保存并解析");
        save.setOnClickListener(view -> submit());
        UiStyle.button(save, true);
        UiStyle.addSpaced(root, save, 6, 10);

        TextView footnote = new TextView(this);
        footnote.setText("保存后可在「收件箱」查看处理进度，在「日程」查看已识别的事项。");
        UiStyle.muted(footnote);
        UiStyle.addSpaced(root, footnote, 12, 8);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        if (savedInstanceState != null) {
            pendingImage = savedInstanceState.getString(STATE_PENDING_IMAGE);
            input.setText(savedInstanceState.getString(STATE_TEXT, ""));
            splitInput.setChecked(savedInstanceState.getBoolean(STATE_SPLIT));
        }
        showPendingImage();
        if (savedInstanceState == null) sweepImages();
        if (savedInstanceState == null) receiveShared(getIntent());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(STATE_PENDING_IMAGE, pendingImage);
        state.putString(STATE_TEXT, input.getText().toString());
        state.putBoolean(STATE_SPLIT, splitInput.isChecked());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveShared(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshImageEntry();
        refreshVoiceEntry();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // A pending attachment that was never submitted does not belong to any input.
        if (isFinishing() && pendingImage != null) {
            new ImageStore(this).delete(pendingImage);
            pendingImage = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            attachImage(data.getData());
            return;
        }
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            if (results == null || results.isEmpty()) {
                Toast.makeText(this, "没有识别到内容", Toast.LENGTH_SHORT).show();
            } else {
                appendText(results.get(0));
                DiagLog.add(this, "voice appended chars=" + results.get(0).length());
            }
        }
    }

    private void receiveShared(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type = intent.getType();
        if ("text/plain".equals(type)) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) input.setText(shared);
            return;
        }
        if (type != null && type.startsWith("image/")) {
            Uri stream = sharedStream(intent);
            if (stream == null) {
                Toast.makeText(this, "分享内容里没有图片", Toast.LENGTH_LONG).show();
            } else {
                attachImage(stream);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static Uri sharedStream(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    private void submit() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingImage == null) {
            input.setError("请输入内容或选择图片");
            return;
        }
        List<String> entries = pendingImage == null && splitInput.isChecked()
                ? splitLines(text) : Collections.singletonList(text);
        if (entries.isEmpty()) {
            input.setError("请输入内容");
            return;
        }
        if (entries.size() > MAX_BATCH_TASKS) {
            Toast.makeText(this, "一次最多拆分 " + MAX_BATCH_TASKS + " 条，请分批发入",
                    Toast.LENGTH_LONG).show();
            return;
        }
        boolean withImage = pendingImage != null;
        if (withImage && imagesUnsupported()) {
            Toast.makeText(this, "当前模型不支持图片，请移除图片或切换模型",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String source = Intent.ACTION_SEND.equals(getIntent().getAction()) ? "share" : "app";
        List<Long> taskIds;
        try (TaskStore store = new TaskStore(this)) {
            boolean configured = new AiSettingsStore(this).load() != null;
            long now = System.currentTimeMillis();
            taskIds = pendingImage == null
                    ? store.insertTasks(entries, source, now)
                    : Collections.singletonList(store.insertTask(text, pendingImage, source, now));
            for (long taskId : taskIds) {
                if (!configured) {
                    store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "请先配置 AI 服务");
                } else {
                    ProcessingJobService.enqueue(this, taskId);
                }
            }
        } catch (Exception exception) {
            Toast.makeText(this, "保存失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        input.setText("");
        setIntent(new Intent(this, MainActivity.class));
        // The image now belongs to a stored input, so the pending reference is simply dropped.
        pendingImage = null;
        showPendingImage();
        DiagLog.add(this, "submitted entries=" + taskIds.size() + " source=" + source
                + " image=" + (withImage ? "yes" : "no"));
        Toast.makeText(this, taskIds.size() == 1 ? "已存入收件箱"
                : "已存入收件箱（" + taskIds.size() + " 条）", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, DashboardActivity.class)
                .putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.INBOX)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    /** Copies a picked or shared image into app storage before anything else reads it. */
    private void attachImage(Uri source) {
        if (imagesUnsupported()) {
            Toast.makeText(this, "当前模型不支持图片输入，可在「AI 服务设置」中重新启用",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在读取图片…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String name = new ImageStore(this).importImage(source);
                DiagLog.add(this, "image imported name=" + name + " bytes="
                        + new ImageStore(this).fileFor(name).length());
                runOnUiThread(() -> {
                    clearPendingImage();
                    pendingImage = name;
                    showPendingImage();
                });
            } catch (Exception exception) {
                DiagLog.add(this, "image import failed " + exception);
                runOnUiThread(() -> Toast.makeText(this, "读取图片失败：" + exception.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }, "chrona-image-import").start();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        try {
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_LONG).show();
        }
    }

    /** Drops the pending attachment and deletes its file; the file is not in any input yet. */
    private void clearPendingImage() {
        if (pendingImage == null) return;
        new ImageStore(this).delete(pendingImage);
        pendingImage = null;
        showPendingImage();
    }

    private void showPendingImage() {
        boolean attached = pendingImage != null;
        attachment.setVisibility(attached ? View.VISIBLE : View.GONE);
        removeImage.setVisibility(attached ? View.VISIBLE : View.GONE);
        // One image belongs to one input, so line splitting does not apply while it is attached.
        splitInput.setEnabled(!attached);
        if (attached) {
            attachment.setImageURI(Uri.fromFile(new ImageStore(this).fileFor(pendingImage)));
        } else {
            attachment.setImageDrawable(null);
        }
    }

    /** Keeps the image entry in step with what the configured model was just found to accept. */
    private void refreshImageEntry() {
        boolean unsupported = imagesUnsupported();
        boolean knownUnsupported = false;
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            knownUnsupported = store.isKnownImageUnsupported(store.load());
        } catch (Exception ignored) {
            // Submission still checks the effective model before sending an attachment.
        }
        pickImage.setVisibility(knownUnsupported ? View.GONE : View.VISIBLE);
        pickImage.setEnabled(!unsupported);
        pickImage.setText(unsupported ? "图片不可用" : "图片");
    }

    /** True when the configured endpoint and model already rejected an image request. */
    private boolean imagesUnsupported() {
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            AiSettings settings = store.load();
            return settings != null && store.isImageUnsupported(settings);
        } catch (Exception exception) {
            // Unreadable settings already surface when the user submits or opens settings.
            return false;
        }
    }

    /** Best-effort cleanup of attachments left behind by a killed process. */
    private void sweepImages() {
        new Thread(() -> {
            try (TaskStore store = new TaskStore(this)) {
                int removed = new ImageStore(this).deleteUnreferenced(store.listImageNames());
                if (removed > 0) DiagLog.add(this, "swept orphan images=" + removed);
            } catch (Exception ignored) {
                // Storage cleanup must never block the inbox.
            }
        }, "chrona-image-sweep").start();
    }

    /** Appends the clipboard text so several snippets can be collected before one submit. */
    private void pasteClipboard() {
        ClipboardManager manager = getSystemService(ClipboardManager.class);
        ClipData clip = manager == null ? null : manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, "剪贴板没有可用文字", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipDescription description = clip.getDescription();
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
            Toast.makeText(this, "剪贴板内容不是文字，图片支持尚未实现", Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        String pasted = text == null ? "" : text.toString().trim();
        if (pasted.isEmpty()) {
            Toast.makeText(this, "剪贴板没有可用文字", Toast.LENGTH_SHORT).show();
            return;
        }
        appendText(pasted);
        DiagLog.add(this, "clipboard pasted chars=" + pasted.length());
        Toast.makeText(this, "已从剪贴板追加文字", Toast.LENGTH_SHORT).show();
    }

    /** Speaks one input through the system recognizer and appends whatever it returns. */
    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "说出一件事，最好带上时间和地点");
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "没有可用的语音识别服务", Toast.LENGTH_LONG).show();
        }
    }

    /** Voice capture is a system capability; with no recognizer installed the entry is hidden. */
    private void refreshVoiceEntry() {
        voiceInput.setVisibility(SpeechRecognizer.isRecognitionAvailable(this)
                ? View.VISIBLE : View.GONE);
    }

    /** Appends pasted or recognized text so several snippets can be collected before one submit. */
    private void appendText(String addition) {
        String trimmed = addition == null ? "" : addition.trim();
        if (trimmed.isEmpty()) return;
        String current = input.getText().toString();
        if (current.trim().isEmpty()) {
            input.setText(trimmed);
        } else {
            input.setText((current.endsWith("\n") ? current : current + "\n") + trimmed);
        }
        input.setSelection(input.getText().length());
    }

    /** One entry per non-empty line; the caller decides when splitting is wanted. */
    private static List<String> splitLines(String text) {
        List<String> entries = new ArrayList<>();
        for (String line : text.split("\r\n|\r|\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) entries.add(trimmed);
        }
        return entries;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void addTool(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
    }
}
