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
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskFileAttachment;
import com.donglan.chrona.data.TaskFileStore;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.debug.DiagLog;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.processing.ProcessingJobService;
import com.donglan.chrona.processing.StreamingOutputStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

/** A focused capture page for text, images and Android's share sheet. */
public final class MainActivity extends Activity {
    private static final int PICK_IMAGE_REQUEST = 12;
    private static final int PICK_FILE_REQUEST = 14;
    private static final int FILE_STORAGE_REQUEST = 16;
    private static final String STATE_PENDING_IMAGES = "pending_images";
    private static final String STATE_PENDING_FILES = "pending_files";
    private static final String STATE_PENDING_FILE_NAMES = "pending_file_names";
    private static final String STATE_PENDING_FILE_TYPES = "pending_file_types";
    private static final String STATE_PENDING_FILE_SIZES = "pending_file_sizes";
    private static final String STATE_TEXT = "draft_text";

    private EditText input;
    private ImageButton pickImage;
    private LinearLayout pendingImagesContainer;
    private LinearLayout pendingFilesContainer;
    /** Retained across rotation so an import never completes against a stale draft snapshot. */
    private static final class DraftSession {
        final List<String> images = new ArrayList<>();
        final List<TaskFileAttachment> files = new ArrayList<>();
        final List<Uri> deferredUris = new ArrayList<>();
        String text = "";
        int importsInFlight;
        java.lang.ref.WeakReference<MainActivity> owner = new java.lang.ref.WeakReference<>(null);
    }
    private DraftSession draftSession = new DraftSession();
    private List<String> pendingImages = draftSession.images;
    private List<TaskFileAttachment> pendingFiles = draftSession.files;
    private List<Uri> deferredFileUris = draftSession.deferredUris;
    private boolean draftReady;
    private android.window.OnBackInvokedCallback captureBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeStore.apply(this);
        super.onCreate(savedInstanceState);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof DraftSession session) {
            draftSession = session;
            pendingImages = session.images;
            pendingFiles = session.files;
            deferredFileUris = session.deferredUris;
        }
        draftSession.owner = new java.lang.ref.WeakReference<>(this);
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(12));
        root.setFitsSystemWindows(true);
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root);
        // Header bar: back on the left, the confirm tick on the right, matching the detail page.
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        String backLabel = isTaskRoot() ? "收件箱" : null;
        if (backLabel == null) {
            topBar.addView(headerButton("←", 24, "返回", this::requestExit),
                    new LinearLayout.LayoutParams(dp(48), dp(48)));
        } else {
            // Launched from the system share sheet, so there is nothing behind this screen:
            // "Back" has to reach the inbox instead of closing the app on the shared content.
            TextView back = headerButton("←  " + backLabel, 15, backLabel, this::requestExit);
            topBar.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
        }
        topBar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        topBar.addView(headerButton("✓", 20, "保存并解析", this::submit),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        UiStyle.addSpaced(root, topBar, 0, 6);

        // The three quick inputs live on the title line so the page keeps one obvious title row.
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("记录一件事");
        title.setTextSize(26);
        UiStyle.title(title);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(iconTool(R.drawable.ic_content_paste, "读取剪贴板文字",
                view -> pasteClipboard()));
        pickImage = iconTool(R.drawable.ic_add_photo, "选择图片", view -> pickImage());
        titleRow.addView(pickImage);
        titleRow.addView(iconTool(R.drawable.ic_attach_file, "选择普通文件附件",
                view -> pickFile()));
        UiStyle.addSpaced(root, titleRow, 0, 4);

        TextView hint = new TextView(this);
        hint.setText("文字或图片都可以。保存后自动解析，你可以继续记录下一件事。");
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

        pendingImagesContainer = new LinearLayout(this);
        pendingImagesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(pendingImagesContainer);

        pendingFilesContainer = new LinearLayout(this);
        pendingFilesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(pendingFilesContainer);

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
        if (retained instanceof DraftSession) {
            input.setText(draftSession.text);
        } else if (savedInstanceState == null || new CaptureDraftStore(this).hasDraft()) {
            // Imports may finish after Android saves an older instance-state bundle.
            // Persisted content is authoritative when no live retained session exists.
            CaptureDraftStore.Draft draft = new CaptureDraftStore(this).load();
            input.setText(draft.text);
            pendingImages.addAll(draft.images);
            pendingFiles.addAll(draft.files);
        } else {
            ArrayList<String> images = savedInstanceState.getStringArrayList(STATE_PENDING_IMAGES);
            if (images != null) pendingImages.addAll(images);
            ArrayList<String> stored = savedInstanceState.getStringArrayList(STATE_PENDING_FILES);
            String[] names = savedInstanceState.getStringArray(STATE_PENDING_FILE_NAMES);
            String[] types = savedInstanceState.getStringArray(STATE_PENDING_FILE_TYPES);
            long[] sizes = savedInstanceState.getLongArray(STATE_PENDING_FILE_SIZES);
            if (stored != null && names != null && types != null && sizes != null
                    && stored.size() == names.length && names.length == types.length
                    && types.length == sizes.length) {
                for (int i = 0; i < stored.size(); i++) pendingFiles.add(
                        new TaskFileAttachment(0, 0, stored.get(i), names[i], types[i], sizes[i]));
            }
            input.setText(savedInstanceState.getString(STATE_TEXT, ""));
        }
        showPendingImages();
        showPendingFiles();
        draftReady = true;
        saveDraft();
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                saveDraft();
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        if (Build.VERSION.SDK_INT >= 33) {
            captureBack = this::requestExit;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, captureBack);
        }
        if (savedInstanceState == null) sweepImages();
        if (savedInstanceState == null) receiveShared(getIntent());
        // On Android 8/9, a shared file may already have opened the storage permission dialog.
        if (deferredFileUris.isEmpty()) StartupPermissions.requestFirstLaunch(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putStringArrayList(STATE_PENDING_IMAGES, new ArrayList<>(pendingImages));
        ArrayList<String> stored = new ArrayList<>();
        String[] names = new String[pendingFiles.size()];
        String[] types = new String[pendingFiles.size()];
        long[] sizes = new long[pendingFiles.size()];
        for (int i = 0; i < pendingFiles.size(); i++) {
            TaskFileAttachment file = pendingFiles.get(i);
            stored.add(file.storedName);
            names[i] = file.displayName;
            types[i] = file.mimeType;
            sizes[i] = file.sizeBytes;
        }
        state.putStringArrayList(STATE_PENDING_FILES, stored);
        state.putStringArray(STATE_PENDING_FILE_NAMES, names);
        state.putStringArray(STATE_PENDING_FILE_TYPES, types);
        state.putLongArray(STATE_PENDING_FILE_SIZES, sizes);
        state.putString(STATE_TEXT, input.getText().toString());
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
    }

    @Override protected void onStop() {
        saveDraft();
        super.onStop();
    }

    // Android 13+ uses captureBack above; this fallback is exclusively for Android 8–12.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { requestExit(); }

    @Override public Object onRetainNonConfigurationInstance() { return draftSession; }

    private void saveDraft() {
        if (draftReady && draftSession.owner.get() == this) {
            draftSession.text = input.getText().toString();
            new CaptureDraftStore(this).save(draftSession.text, pendingImages, pendingFiles);
        }
    }

    private void requestExit() {
        if (draftSession.importsInFlight > 0) {
            Feedback.show(this, "附件正在读取，请稍候再退出");
            return;
        }
        if (input.getText().length() == 0 && pendingImages.isEmpty() && pendingFiles.isEmpty()) {
            leaveCapture();
            return;
        }
        UiStyle.choiceDialog(this, "尚未提交这条记录", new String[]{"保留草稿", "放弃", "取消"},
                -1, choice -> {
                    if (choice == 0) {
                        saveDraft();
                        leaveCapture();
                    } else if (choice == 1) {
                        for (String image : pendingImages) new ImageStore(this).delete(image);
                        pendingImages.clear();
                        pendingFiles.clear();
                        input.setText("");
                        new CaptureDraftStore(this).clear();
                        leaveCapture();
                    }
                });
    }

    private void leaveCapture() {
        if (isTaskRoot()) startActivity(new Intent(this, DashboardActivity.class)
                .putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.INBOX)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && captureBack != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(captureBack);
        if (draftSession.owner.get() == this) draftSession.owner.clear();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<Uri> sources = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri source = clip.getItemAt(i).getUri();
                    if (source != null) sources.add(source);
                }
            } else if (data.getData() != null) sources.add(data.getData());
            attachImages(sources);
            return;
        }
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<Uri> sources = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri source = clip.getItemAt(i).getUri();
                    if (source != null) sources.add(source);
                }
            } else if (data.getData() != null) sources.add(data.getData());
            addPickedAttachments(data, sources);
            return;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != FILE_STORAGE_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ArrayList<Uri> files = new ArrayList<>(deferredFileUris);
            deferredFileUris.clear();
            attachFiles(files);
        } else {
            deferredFileUris.clear();
            Feedback.showLong(this, "需要允许写入公共 Downloads/Chrona 才能保存普通文件");
        }
        StartupPermissions.requestFirstLaunch(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void receiveShared(Intent intent) {
        if (intent == null || (!Intent.ACTION_SEND.equals(intent.getAction())
                && !Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()))) return;
        String type = intent.getType();
        if ("text/plain".equals(type) && Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) appendText(shared);
        }
        ArrayList<Uri> streams = sharedStreams(intent);
        if (!streams.isEmpty()) {
            ArrayList<Uri> images = new ArrayList<>();
            ArrayList<Uri> files = new ArrayList<>();
            for (Uri stream : streams) {
                String mime = getContentResolver().getType(stream);
                if (mime == null) mime = type;
                if (mime != null && mime.startsWith("image/")) images.add(stream);
                else files.add(stream);
            }
            if (!images.isEmpty()) {
                attachImages(images);
            }
            persistPickedUris(intent, files);
            attachFiles(files);
        } else if (type != null && !"text/plain".equals(type)) {
            Feedback.showLong(this, "分享内容里没有可读取的附件");
        }
    }

    private static boolean isShareIntent(Intent intent) {
        if (intent == null) return false;
        return Intent.ACTION_SEND.equals(intent.getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction());
    }

    @SuppressWarnings("deprecation")
    private static Uri sharedStream(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Uri> sharedStreams(Intent intent) {
        ArrayList<Uri> streams = new ArrayList<>();
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> values;
            if (Build.VERSION.SDK_INT >= 33) {
                values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }
            if (values != null) streams.addAll(values);
        } else {
            Uri one = sharedStream(intent);
            if (one != null) streams.add(one);
        }
        return streams;
    }

    private void persistPickedUris(Intent intent, List<Uri> uris) {
        if ((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return;
        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Share providers may offer only temporary grants; metadata remains visible.
            }
        }
    }

    private void addPickedAttachments(Intent result, List<Uri> sources) {
        ArrayList<Uri> images = new ArrayList<>();
        ArrayList<Uri> files = new ArrayList<>();
        for (Uri source : sources) {
            String mime = getContentResolver().getType(source);
            if (mime != null && mime.startsWith("image/")) images.add(source);
            else files.add(source);
        }
        if (!images.isEmpty()) {
            attachImages(images);
        }
        persistPickedUris(result, files);
        attachFiles(files);
    }

    private void submit() {
        if (draftSession.importsInFlight > 0) {
            Feedback.show(this, "附件正在读取，请稍候再提交");
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingImages.isEmpty() && pendingFiles.isEmpty()) {
            // The platform error bubble is a white system popup; the app speaks through Feedback.
            Feedback.show(this, "请输入内容或添加附件");
            input.requestFocus();
            return;
        }
        List<String> entries = Collections.singletonList(text);
        boolean withImage = !pendingImages.isEmpty();
        int attachedFileCount = pendingFiles.size();
        if (withImage && imagesUnsupported()) {
            Feedback.showLong(this, "当前模型不支持图片，请移除图片或切换模型");
            return;
        }
        String source = isShareIntent(getIntent()) ? "share" : "app";
        List<String> nonDuplicateEntries = new ArrayList<>();
        TaskRecord existingDuplicate = null;
        int duplicateCount = 0;
        Set<String> seenInBatch = new LinkedHashSet<>();
        try (TaskStore store = new TaskStore(this)) {
            if (!pendingImages.isEmpty() || !pendingFiles.isEmpty()) {
                TaskRecord duplicate = store.findDuplicateTask(text,
                        pendingImages.isEmpty() ? null : pendingImages.get(0), pendingFiles);
                if (duplicate != null) {
                    existingDuplicate = duplicate;
                    duplicateCount++;
                } else {
                    nonDuplicateEntries.add(text);
                }
            } else {
                for (String entry : entries) {
                    String normalized = TaskStore.normalizeInputText(entry);
                    TaskRecord duplicate = store.findDuplicateTask(entry, null, Collections.emptyList());
                    if (duplicate != null) {
                        if (existingDuplicate == null) existingDuplicate = duplicate;
                        duplicateCount++;
                    } else if (!seenInBatch.add(normalized)) {
                        duplicateCount++;
                    } else {
                        nonDuplicateEntries.add(entry);
                    }
                }
            }
        } catch (Exception exception) {
            Feedback.showLong(this, "检查重复内容失败：" + exception.getMessage());
            return;
        }
        if (duplicateCount > 0) {
            showDuplicateInputChoice(text, entries, nonDuplicateEntries, source,
                    withImage, attachedFileCount, existingDuplicate, duplicateCount);
            return;
        }
        saveSubmission(text, entries, source, withImage, attachedFileCount);
    }

    private void showDuplicateInputChoice(String text, List<String> allEntries,
            List<String> nonDuplicateEntries, String source, boolean withImage,
            int attachedFileCount, TaskRecord existing, int duplicateCount) {
        String[] options = existing == null
                ? new String[] {"只提交不重复项", "仍要新建全部", "取消"}
                : new String[] {"查看已有记录", "跳过重复项并继续", "仍要新建全部", "取消"};
        String title = "发现 " + duplicateCount + " 条重复内容 · 默认跳过";
        if (allEntries.size() > 1 && !nonDuplicateEntries.isEmpty())
            title += "（另有 " + nonDuplicateEntries.size() + " 条可提交）";
        UiStyle.choiceDialog(this, title, options, -1, choice -> {
            if (existing != null && choice == 0) {
                startActivity(new Intent(this, TaskDetailActivity.class)
                        .putExtra("task_id", existing.id));
                return;
            }
            if (existing == null && choice == 0 || existing != null && choice == 1) {
                if (nonDuplicateEntries.isEmpty()) {
                    Feedback.show(this, "没有新的内容需要提交");
                    requestExit();
                    return;
                }
                saveSubmission(text, nonDuplicateEntries, source, withImage, attachedFileCount);
                return;
            }
            if (existing == null && choice == 1 || existing != null && choice == 2)
                saveSubmission(text, allEntries, source, withImage, attachedFileCount);
        });
    }

    private void saveSubmission(String text, List<String> entries, String source,
            boolean withImage, int attachedFileCount) {
        List<Long> taskIds;
        try (TaskStore store = new TaskStore(this)) {
            boolean configured = new AiSettingsStore(this).load() != null;
            long now = System.currentTimeMillis();
            taskIds = pendingImages.isEmpty() && pendingFiles.isEmpty()
                    ? store.insertTasks(entries, source, now)
                    : Collections.singletonList(store.insertTask(text,
                            pendingImages.isEmpty() ? null : pendingImages.get(0),
                            pendingFiles, source, now));
            if (!pendingImages.isEmpty()) {
                long taskId = taskIds.get(0);
                for (int i = 1; i < pendingImages.size(); i++)
                    store.addImageAttachment(taskId, pendingImages.get(i));
            }
            for (long taskId : taskIds) {
                if (!configured) {
                    store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "请先配置 AI 服务");
                } else {
                    ProcessingJobService.enqueue(this, taskId);
                }
            }
        } catch (Exception exception) {
            Feedback.showLong(this, "保存失败：" + exception.getMessage());
            return;
        }
        input.setText("");
        setIntent(new Intent(this, MainActivity.class));
        // The image now belongs to a stored input, so the pending reference is simply dropped.
        pendingImages.clear();
        pendingFiles.clear();
        new CaptureDraftStore(this).clear();
        showPendingImages();
        showPendingFiles();
        DiagLog.add(this, "submitted entries=" + taskIds.size() + " source=" + source
                + " image=" + (withImage ? "yes" : "no")
                + " files=" + attachedFileCount);
        Feedback.show(this, taskIds.size() == 1 ? "已存入收件箱"
                : "已存入收件箱（" + taskIds.size() + " 条）");
        startActivity(new Intent(this, DashboardActivity.class)
                .putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.INBOX)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    /** Copies picked/shared images into app storage so parsing can read them later. */
    private void attachImages(List<Uri> sources) {
        if (sources == null || sources.isEmpty()) return;
        if (imagesUnsupported()) {
            Feedback.showLong(this, "当前模型不支持图片输入，可在「AI 服务设置」中重新启用");
            return;
        }
        Feedback.show(this, "正在读取 " + sources.size() + " 张图片…");
        draftSession.importsInFlight++;
        DraftSession importingSession = draftSession;
        android.content.Context importContext = getApplicationContext();
        new Thread(() -> {
            ArrayList<String> imported = new ArrayList<>();
            int failed = 0;
            for (Uri source : sources) {
                try {
                    String name = new ImageStore(importContext).importImage(source);
                    imported.add(name);
                    DiagLog.add(importContext, "image imported name=" + name + " bytes="
                            + new ImageStore(importContext).fileFor(name).length());
                } catch (Exception exception) {
                    failed++;
                    DiagLog.add(this, "image import failed " + exception);
                }
            }
            int failedCount = failed;
            runOnUiThread(() -> {
                importingSession.importsInFlight--;
                importingSession.images.addAll(imported);
                new CaptureDraftStore(importContext).save(importingSession.text,
                        importingSession.images, importingSession.files);
                MainActivity owner = importingSession.owner.get();
                if (owner != null && !owner.isDestroyed()) {
                    owner.showPendingImages();
                    Feedback.show(owner, failedCount == 0 ? "已添加 " + imported.size() + " 张图片"
                            : "已添加 " + imported.size() + " 张，" + failedCount + " 张失败");
                }
            });
        }, "chrona-image-import").start();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } catch (ActivityNotFoundException exception) {
            Feedback.showLong(this, "没有可用的图片选择器");
        }
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, PICK_FILE_REQUEST);
        } catch (ActivityNotFoundException exception) {
            Feedback.showLong(this, "没有可用的文件选择器");
        }
    }

    private void attachFiles(List<Uri> sources) {
        if (sources == null || sources.isEmpty()) return;
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            deferredFileUris.addAll(sources);
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    FILE_STORAGE_REQUEST);
            return;
        }
        Feedback.show(this, "正在保存到公共 Downloads/Chrona…");
        draftSession.importsInFlight++;
        DraftSession importingSession = draftSession;
        android.content.Context importContext = getApplicationContext();
        new Thread(() -> {
            ArrayList<TaskFileAttachment> imported = new ArrayList<>();
            int failed = 0;
            for (Uri source : sources) {
                try {
                    imported.add(new TaskFileStore(importContext).importFile(source));
                } catch (Exception exception) {
                    failed++;
                }
            }
            int failedCount = failed;
            runOnUiThread(() -> {
                importingSession.importsInFlight--;
                importingSession.files.addAll(imported);
                new CaptureDraftStore(importContext).save(importingSession.text,
                        importingSession.images, importingSession.files);
                MainActivity owner = importingSession.owner.get();
                if (owner != null && !owner.isDestroyed()) {
                    owner.showPendingFiles();
                    Feedback.show(owner, failedCount == 0 ? "已添加 " + imported.size() + " 个文件"
                            : "已添加 " + imported.size() + " 个，" + failedCount + " 个失败");
                }
            });
        }, "chrona-file-import").start();
    }

    private void showPendingFiles() {
        if (pendingFilesContainer == null) return;
        pendingFilesContainer.removeAllViews();
        for (int i = 0; i < pendingFiles.size(); i++) {
            final TaskFileAttachment file = pendingFiles.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView label = new TextView(this);
            label.setText(file.displayName + " · " + file.mimeType + " · "
                    + formatFileSize(file.sizeBytes));
            label.setTextSize(13);
            label.setMaxLines(2);
            UiStyle.muted(label);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView remove = new TextView(this);
            remove.setText("移除");
            remove.setTextSize(14);
            remove.setGravity(android.view.Gravity.CENTER);
            remove.setMinWidth(dp(52));
            remove.setMinHeight(dp(48));
            remove.setTextColor(UiStyle.colors(this).primary);
            remove.setOnClickListener(view -> {
                UiStyle.confirmDialog(this, "移除这个文件？", file.displayName
                        + " 将从本次记录中移除，公共 Downloads/Chrona 中的副本会保留。", "移除", () -> {
                    pendingFiles.remove(file);
                    showPendingFiles();
                });
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(56), dp(48)));
            UiStyle.addSpaced(pendingFilesContainer, row, 1, 1);
        }
        pickImage.setEnabled(pendingFiles.isEmpty() && pendingImages.isEmpty());
        saveDraft();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024f);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024f * 1024));
    }

    /** Rebuilds the visible image list for the next unsaved input. */
    private void showPendingImages() {
        if (pendingImagesContainer == null) return;
        pendingImagesContainer.removeAllViews();
        for (String imageName : new ArrayList<>(pendingImages)) {
            AttachmentImageTile tile = new AttachmentImageTile(this,
                    Uri.fromFile(new ImageStore(this).fileFor(imageName)), imageName,
                    () -> startActivity(new Intent(this, AttachmentViewerActivity.class)
                            .putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME, imageName)),
                    () -> UiStyle.confirmDialog(this, "移除图片？",
                            "将从本次记录中移除图片“" + imageName + "”。",
                            "移除图片", () -> {
                                pendingImages.remove(imageName);
                                new ImageStore(this).delete(imageName);
                                showPendingImages();
                            }));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(-1, dp(160));
            tileParams.setMargins(0, dp(4), 0, dp(4));
            pendingImagesContainer.addView(tile, tileParams);
        }
        pickImage.setEnabled(pendingImages.isEmpty() && pendingFiles.isEmpty());
        saveDraft();
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
        // The icon stays an icon; unavailability is carried by the alpha and the description.
        pickImage.setAlpha(unsupported ? 0.45f : 1f);
        pickImage.setContentDescription(unsupported ? "当前模型不支持图片" : "选择图片");
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

    /** Best-effort cleanup of attachments and streamed output left behind by a killed process. */
    private void sweepImages() {
        new Thread(() -> {
            try (TaskStore store = new TaskStore(this)) {
                java.util.Set<String> references = new java.util.HashSet<>(store.listImageNames());
                references.addAll(new CaptureDraftStore(this).load().images);
                int removed = new ImageStore(this).deleteUnreferenced(references);
                if (removed > 0) DiagLog.add(this, "swept orphan images=" + removed);
                java.util.Set<Long> live = new java.util.HashSet<>();
                for (TaskRecord task : store.listTasks()) live.add(task.id);
                int previews = new StreamingOutputStore(this).deleteUnreferenced(live);
                if (previews > 0) DiagLog.add(this, "swept orphan model output=" + previews);
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
            Feedback.show(this, "剪贴板没有可用文字");
            return;
        }
        ClipDescription description = clip.getDescription();
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
            Feedback.showLong(this, "剪贴板内容不是文字，图片支持尚未实现");
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        String pasted = text == null ? "" : text.toString().trim();
        if (pasted.isEmpty()) {
            Feedback.show(this, "剪贴板没有可用文字");
            return;
        }
        appendText(pasted);
        DiagLog.add(this, "clipboard pasted chars=" + pasted.length());
        Feedback.show(this, "已从剪贴板追加文字");
    }

    /** Appends pasted text so several snippets can be collected before one submit. */
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Glass square affordance shared by the page header: the back arrow and the confirm tick. */
    private TextView headerButton(String glyph, int textSize, String description,
            Runnable action) {
        TextView button = new TextView(this);
        button.setText(glyph);
        button.setTextSize(textSize);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(UiStyle.colors(this).primary);
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(description);
        UiStyle.glass(button);
        UiStyle.pressable(button);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    /** Compact icon affordance for the quick inputs, in the same style as the header buttons. */
    private ImageButton iconTool(int drawable, String description,
            android.view.View.OnClickListener action) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(
                UiStyle.colors(this).primary));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        UiStyle.glass(button);
        UiStyle.pressable(button);
        button.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }
}
