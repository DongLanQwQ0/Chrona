package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Gravity;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.EventTimeDefaults;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskFileAttachment;
import com.donglan.chrona.data.TaskFileStore;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.debug.DiagLog;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.processing.ProcessingJobService;
import com.donglan.chrona.processing.StreamingOutputStore;
import com.donglan.chrona.web.LinkFetcher;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Review parsed events before committing them to the device calendar. */
public final class TaskDetailActivity extends Activity {
    private static final class CompactCheckBoxDrawable extends Drawable {
        private final int size;
        private final int checkedColor;
        private final int checkColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean checked;

        CompactCheckBoxDrawable(int size, int checkedColor, int checkColor) {
            this.size = size;
            this.checkedColor = checkedColor;
            this.checkColor = checkColor;
        }

        @Override public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float unit = size / 18f;
            float inset = 1.5f * unit;
            bounds.inset(inset, inset);
            paint.setStrokeWidth(1f * unit);
            paint.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setColor(checkedColor);
            canvas.drawRoundRect(bounds, 3f * unit, 3f * unit, paint);
            if (checked) {
                Path tick = new Path();
                tick.moveTo(bounds.left + 3.2f * unit, bounds.top + 6.8f * unit);
                tick.lineTo(bounds.left + 6.3f * unit, bounds.top + 10.1f * unit);
                tick.lineTo(bounds.left + 12.8f * unit, bounds.top + 3.8f * unit);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(1.8f * unit);
                paint.setColor(checkColor);
                canvas.drawPath(tick, paint);
            }
        }

        void setChecked(boolean value) {
            if (checked == value) return;
            checked = value;
            invalidateSelf();
        }

        // The owning ImageView forwards pressed/focused states without state_checked. Keep the
        // toggle value explicit so those transient drawable states cannot clear the checkmark.
        @Override protected boolean onStateChange(int[] state) { return false; }

        @Override public boolean isStateful() { return false; }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class AllDayToggle extends LinearLayout {
        interface OnCheckedChangeListener {
            void onCheckedChanged(AllDayToggle view, boolean checked);
        }

        private final CompactCheckBoxDrawable checkbox;
        private final TextView label;
        private boolean checked;
        private OnCheckedChangeListener listener;

        AllDayToggle(Activity activity, int size, int checkedColor, int checkColor) {
            super(activity);
            setOrientation(HORIZONTAL);
            setGravity(android.view.Gravity.CENTER_VERTICAL);
            float density = activity.getResources().getDisplayMetrics().density;
            setMinimumHeight(Math.round(48 * density));
            setClickable(true);
            setFocusable(true);
            setContentDescription("全天日程");
            checkbox = new CompactCheckBoxDrawable(size, checkedColor, checkColor);
            checkbox.setBounds(0, 0, size, size);
            ImageView check = new ImageView(activity);
            check.setImageDrawable(checkbox);
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(check, new LinearLayout.LayoutParams(size, size));
            label = new TextView(activity);
            label.setText("全天");
            label.setTextSize(DETAIL_LABEL_TEXT_SP);
            UiStyle.muted(label);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
            labelParams.setMargins(Math.round(4 * density), 0, 0, 0);
            addView(label, labelParams);
        }

        void setOnCheckedChangeListener(OnCheckedChangeListener value) { listener = value; }

        boolean isChecked() { return checked; }

        void setChecked(boolean value) {
            if (checked == value) return;
            checked = value;
            checkbox.setChecked(value);
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent
                    .TYPE_WINDOW_CONTENT_CHANGED);
            if (listener != null) listener.onCheckedChanged(this, value);
        }

        @Override public boolean performClick() {
            setChecked(!checked);
            super.performClick();
            return true;
        }

        @Override public void onInitializeAccessibilityNodeInfo(
                android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(android.widget.CompoundButton.class.getName());
            info.setCheckable(true);
            info.setChecked(checked);
            info.setContentDescription("全天日程");
        }
    }

    private static final int DETAIL_FIELD_LABEL_WIDTH_DP = 78;
    private static final int DETAIL_FIELD_ICON_SIZE_DP = 18;
    private static final int DETAIL_LABEL_TEXT_SP = 13;
    private static final int DETAIL_VALUE_TEXT_SP = 15;
    private static final int CALENDAR_PERMISSION_REQUEST = 11;
    private static final int PICK_CAPTURE_IMAGES = 13;
    private static final int PICK_CAPTURE_FILES = 14;
    private static final int CREATE_FILE_DOCUMENT = 15;
    private static final int FILE_STORAGE_REQUEST = 16;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private long taskId;
    private String exportFileName;
    private LinearLayout content;
    private LinearLayout shell;
    private Button completionAction;
    private ScrollView page;
    private ScrollView previewScroll;
    private Button previewToggleButton;
    private View advancedDetailsView;
    private View usageDetailsView;
    private View linkBodyView;
    private SwipePagerLayout detailPager;
    private TextView previewText;
    private long previewLength = -1;
    private boolean deleting;
    private boolean pageTransitionRunning;
    private boolean saveInFlight;
    private boolean resetScrollForNextRender;
    private boolean dragTargetLoaded;
    private int activeDragDirection;
    private PagerPage adjacentPage;
    private View gesturePriorityChild;
    private View galleryGesturePriorityChild;
    private final Map<Long, Integer> taskScrollPositions = new HashMap<>();
    /** Candidate selection is session-local and follows the inbox task while paging. */
    private final Map<Long, Integer> taskCandidatePagePositions = new HashMap<>();
    private int candidatePageIndex;
    private float dragStartOffset;
    private final Set<Long> dirtyCandidateIds = new HashSet<>();
    private final List<Uri> deferredFileUris = new ArrayList<>();
    /** Cards stagger in only for a freshly opened screen, not on every rebuild. */
    private boolean animateEntrances;
    private Boolean previewExpanded;
    private boolean detailsExpanded;
    private boolean usageExpanded;
    private boolean linksExpanded;
    private boolean hasUnsavedEdits;
    private String observedStatus;
    private int observedCandidates = -1;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshPoll = this::pollForResult;

    @Override
    protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        taskId = state == null ? getIntent().getLongExtra("task_id", -1)
                : state.getLong("task_id", getIntent().getLongExtra("task_id", -1));
        if (state != null) exportFileName = state.getString("export_file_uri");
        page = new ScrollView(this);
        // The detail page reads as a stack of cards; a scrollbar down the right edge just cuts
        // into the field column.
        page.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(24));
        UiStyle.page(this, content);
        content.setBackgroundColor(Color.TRANSPARENT);
        page.addView(content);
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));
        SwipePagerLayout stage = new SwipePagerLayout(this,
                new SwipePagerLayout.Listener() {
                    @Override public void onStart() {
                        if (pageTransitionRunning) return;
                        shell.animate().cancel();
                        dragStartOffset = shell.getTranslationX();
                        dragTargetLoaded = false;
                        activeDragDirection = 0;
                        taskScrollPositions.put(taskId, page.getScrollY());
                    }

                    @Override public void onDrag(float distanceX) {
                        if (pageTransitionRunning || hasUnsavedEdits) return;
                        float width = Math.max(page.getWidth(),
                                getResources().getDisplayMetrics().widthPixels);
                        float offset = Math.max(-width, Math.min(width, dragStartOffset + distanceX));
                        int direction = offset < 0 ? 1 : -1;
                        if (adjacentPage == null && Math.abs(offset) >= dp(8)
                                && hasTaskPage(direction)) {
                            long targetId = taskIdInDirection(direction);
                            if (targetId > 0) {
                                activeDragDirection = direction;
                                adjacentPage = buildAdjacentPage(targetId);
                                if (adjacentPage != null) {
                                    FrameLayout stage = detailPager;
                                    stage.addView(adjacentPage.shell, 1,
                                            new FrameLayout.LayoutParams(-1, -1));
                                    stage.requestApplyInsets();
                                    adjacentPage.shell.setTranslationX(direction > 0 ? width : -width);
                                    long restoredY = taskScrollPositions.getOrDefault(targetId, 0);
                                    PagerPage targetPage = adjacentPage;
                                    targetPage.page.post(() -> targetPage.page.scrollTo(0, (int) restoredY));
                                    dragTargetLoaded = true;
                                }
                            }
                        }
                        if (dragTargetLoaded && activeDragDirection != 0) {
                            int travel = activeDragDirection;
                            if (travel > 0 && offset > 0 || travel < 0 && offset < 0)
                                offset = 0;
                            shell.setTranslationX(offset);
                            adjacentPage.shell.setTranslationX((travel > 0 ? width : -width) + offset);
                            invalidateVisibleAcrylicSurfaces();
                        } else {
                            shell.setTranslationX(offset);
                            invalidateVisibleAcrylicSurfaces();
                        }
                    }

                    @Override public void onRelease(float distanceX, float velocityX) {
                        if (dragTargetLoaded && adjacentPage != null) {
                            float width = Math.max(page.getWidth(), getResources().getDisplayMetrics().widthPixels);
                            float travel = activeDragDirection > 0 ? -1f : 1f;
                            float total = dragStartOffset + distanceX;
                            boolean quick = Math.abs(velocityX) > dp(900) && Math.abs(total) > dp(24);
                            boolean commit = (Math.abs(total) >= Math.max(dp(64), width * .18f) || quick)
                                    && travel * total > 0;
                            finishAdjacentPageGesture(commit, width);
                            return;
                        }
                        finishTaskPageGesture(dragStartOffset + distanceX, velocityX);
                    }

                    @Override public void onCancel() {
                        if (adjacentPage != null) finishAdjacentPageGesture(false,
                                Math.max(page.getWidth(), getResources().getDisplayMetrics().widthPixels));
                        else settleTaskPageAtCurrent();
                    }
                });
        detailPager = stage;
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        content.setFitsSystemWindows(false);
        UiStyle.applyScrollableInsets(stage, content);
        setContentView(stage);
        if (state != null) {
            int scrollY = state.getInt("scroll_y");
            candidatePageIndex = state.getInt("candidate_page_index");
            taskCandidatePagePositions.put(taskId, candidatePageIndex);
            if (state.containsKey("preview_expanded"))
                previewExpanded = state.getBoolean("preview_expanded");
            detailsExpanded = state.getBoolean("details_expanded");
            usageExpanded = state.getBoolean("usage_expanded");
            linksExpanded = state.getBoolean("links_expanded");
            long[] savedIds = state.getLongArray("task_scroll_ids");
            int[] savedValues = state.getIntArray("task_scroll_values");
            if (savedIds != null && savedValues != null) {
                for (int i = 0; i < Math.min(savedIds.length, savedValues.length); i++)
                    taskScrollPositions.put(savedIds[i], savedValues[i]);
            }
            long[] candidateTaskIds = state.getLongArray("candidate_task_ids");
            int[] candidateIndexes = state.getIntArray("candidate_task_indexes");
            if (candidateTaskIds != null && candidateIndexes != null) {
                for (int i = 0; i < Math.min(candidateTaskIds.length, candidateIndexes.length); i++)
                    taskCandidatePagePositions.put(candidateTaskIds[i], Math.max(0, candidateIndexes[i]));
            }
            page.post(() -> page.scrollTo(0, scrollY));
        } else {
            animateEntrances = true;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", page.getScrollY());
        taskScrollPositions.put(taskId, page.getScrollY());
        long[] savedIds = new long[taskScrollPositions.size()];
        int[] savedValues = new int[taskScrollPositions.size()];
        int savedIndex = 0;
        for (Map.Entry<Long, Integer> entry : taskScrollPositions.entrySet()) {
            savedIds[savedIndex] = entry.getKey();
            savedValues[savedIndex++] = entry.getValue();
        }
        state.putLongArray("task_scroll_ids", savedIds);
        state.putIntArray("task_scroll_values", savedValues);
        taskCandidatePagePositions.put(taskId, candidatePageIndex);
        long[] candidateTaskIds = new long[taskCandidatePagePositions.size()];
        int[] candidateIndexes = new int[taskCandidatePagePositions.size()];
        int candidateSavedIndex = 0;
        for (Map.Entry<Long, Integer> entry : taskCandidatePagePositions.entrySet()) {
            candidateTaskIds[candidateSavedIndex] = entry.getKey();
            candidateIndexes[candidateSavedIndex++] = entry.getValue();
        }
        state.putLongArray("candidate_task_ids", candidateTaskIds);
        state.putIntArray("candidate_task_indexes", candidateIndexes);
        state.putInt("candidate_page_index", candidatePageIndex);
        state.putLong("task_id", taskId);
        state.putBoolean("details_expanded", detailsExpanded);
        state.putBoolean("usage_expanded", usageExpanded);
        state.putBoolean("links_expanded", linksExpanded);
        if (previewExpanded != null) state.putBoolean("preview_expanded", previewExpanded);
        if (exportFileName != null) state.putString("export_file_uri", exportFileName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasUnsavedEdits) render();
        refreshHandler.postDelayed(refreshPoll, 1500L);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshPoll);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != FILE_STORAGE_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            List<Uri> files = new ArrayList<>(deferredFileUris);
            deferredFileUris.clear();
            addOrdinaryFiles(files);
        } else {
            deferredFileUris.clear();
            Feedback.showLong(this, "需要允许写入公共 Downloads/Chrona 才能保存普通文件");
        }
    }

    private void pollForResult() {
        if (deleting) return;
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            int count = store.getCandidates(taskId).size();
            if (task != null && (!task.status.equals(observedStatus)
                    || count != observedCandidates) && !hasUnsavedEdits) render();
            else if (task != null) refreshPreview();
        } catch (Exception exception) {
            // A transient database failure should not close a draft being edited.
            android.util.Log.w("Chrona", "Could not refresh task detail", exception);
        }
        refreshHandler.postDelayed(refreshPoll, 1500L);
    }

    private void render() {
        if (deleting) return;
        final long renderedTaskId = taskId;
        candidatePageIndex = taskCandidatePagePositions.getOrDefault(taskId, candidatePageIndex);
        if (detailPager != null) detailPager.setGesturePriorityChild(null);
        gesturePriorityChild = null;
        galleryGesturePriorityChild = null;
        int previousScroll = resetScrollForNextRender ? taskScrollPositions.getOrDefault(taskId, 0) : page.getScrollY();
        resetScrollForNextRender = false;
        final ScrollView renderedPage = page;
        content.removeAllViews();
        completionAction = null;
        previewText = null;
        previewScroll = null;
        previewToggleButton = null;
        advancedDetailsView = null;
        usageDetailsView = null;
        linkBodyView = null;
        previewLength = -1;
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) {
                label("任务不存在", 20);
                return;
            }
            addDetailHeader();
            List<EventCandidate> candidates = store.getCandidates(taskId);
            candidatePageIndex = candidates.isEmpty() ? 0
                    : Math.max(0, Math.min(candidatePageIndex, candidates.size() - 1));
            taskCandidatePagePositions.put(taskId, candidatePageIndex);
            addTaskPagerIndicator(store.listTasks(), task.status);
            observedStatus = task.status;
            observedCandidates = candidates.size();
            int publishedCount = countPublished(candidates);
            List<String> links = LinkFetcher.extractUrls(task.rawText);
            addStatusSection(task, links, publishedCount, candidates.isEmpty());
            boolean processing = TaskRecord.PROCESSING.equals(task.status);
            long outputBytes = new StreamingOutputStore(this).length(taskId);
            if (previewExpanded == null) previewExpanded = false;
            if (candidates.isEmpty()) {
                label("没有日程", 19);
                label("还没有识别出日程。你可以重新解析，或手动添加。", 14);
                button("手动添加日程", () -> addManualCandidate());
            } else {
                if (candidates.size() == 1) {
                    EventCandidate candidate = candidates.get(0);
                    Button save = addCandidateEditor(candidate, 1, true);
                    showCompletionAction(save, candidate);
                } else {
                    addCandidateCarousel(candidates);
                }
            }

            addAttachmentSection(task.rawText, store.getImagePaths(taskId),
                    store.getFileAttachments(taskId));
        } catch (Exception exception) {
            showError(exception);
        }
        renderedPage.post(() -> renderedPage.scrollTo(0, previousScroll));
        page.setOnScrollChangeListener((View view, int x, int y, int oldX, int oldY) ->
                taskScrollPositions.put(renderedTaskId, y));
        if (animateEntrances) {
            animateEntrances = false;
            enterDetailChildren(content);
        }
    }

    /** Fade detail cards in without translating them while their acrylic samples are drawn. */
    private void enterDetailChildren(LinearLayout parent) {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.animate().cancel();
            child.setTranslationY(0f);
            child.setAlpha(0f);
            child.animate().alpha(1f).setStartDelay(Math.min(i, 6) * 28L)
                    .setDuration(180L).start();
        }
    }

    private void addTaskPagerIndicator(List<TaskRecord> tasks, String status) {
        int current = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id == taskId) {
                current = i;
                break;
            }
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView position = new TextView(this);
        position.setText(current < 0 ? "收件箱" : "" + (current + 1) + " / " + tasks.size());
        position.setTextSize(16);
        position.setGravity(android.view.Gravity.CENTER_VERTICAL);
        position.setMinHeight(dp(48));
        UiStyle.muted(position);
        Drawable inboxIcon = getDrawable(R.drawable.ic_inbox_outline);
        if (inboxIcon != null) {
            inboxIcon.setTint(UiStyle.colors(this).primary);
            inboxIcon.setBounds(0, 0, dp(20), dp(20));
            position.setCompoundDrawablesRelative(inboxIcon, null, null, null);
            position.setCompoundDrawablePadding(dp(6));
        }
        position.setContentDescription(current < 0 ? "收件箱" : "收件箱，第" + (current + 1)
                + "条，共" + tasks.size() + "条" + (tasks.size() > 1 ? "，可左右滑动切换" : ""));
        row.addView(position, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView badge = new TextView(this);
        badge.setText(statusLabel(status));
        badge.setTextSize(12);
        badge.setPadding(dp(9), dp(4), dp(9), dp(4));
        badge.setContentDescription("任务状态：" + statusLabel(status));
        UiStyle.pill(badge, true);
        row.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        UiStyle.addSpaced(content, row, 2, 5);
    }

    private void addCandidateCarousel(List<EventCandidate> candidates) {
        candidatePageIndex = Math.max(0, Math.min(candidatePageIndex, candidates.size() - 1));
        taskCandidatePagePositions.put(taskId, candidatePageIndex);
        TextView indicator = new TextView(this);
        indicator.setText("日程 " + (candidatePageIndex + 1) + " / " + candidates.size());
        indicator.setContentDescription("第" + (candidatePageIndex + 1) + "项日程，共"
                + candidates.size() + "项，可左右滑动切换");
        indicator.setTextSize(14);
        UiStyle.muted(indicator);
        UiStyle.addSpaced(content, indicator, 2, 5);

        CandidatePagerScrollView carousel = new CandidatePagerScrollView(this);
        gesturePriorityChild = candidates.size() > 1 ? carousel : null;
        detailPager.setGesturePriorityChild(gesturePriorityChild);
        LinearLayout pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.HORIZONTAL);
        carousel.addView(pages, new HorizontalScrollView.LayoutParams(-2, -2));
        int pageWidth = candidatePageWidth();
        carousel.setPages(pageWidth, candidates.size());
        Button[] saves = new Button[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            saves[i] = addCandidateEditor(candidates.get(i), i + 1, true, pages, pageWidth);
        }
        carousel.setOnScrollChangeListener((view, scrollX, scrollY, oldX, oldY) -> {
            int index = Math.max(0, Math.min(candidates.size() - 1,
                    Math.round(scrollX / (float) pageWidth)));
            if (index != candidatePageIndex) {
                candidatePageIndex = index;
                taskCandidatePagePositions.put(taskId, index);
                indicator.setText("日程 " + (index + 1) + " / " + candidates.size());
                indicator.setContentDescription("第" + (index + 1) + "项日程，共"
                        + candidates.size() + "项，可左右滑动切换");
                setCompletionAction(() -> requestCandidateSave(candidates.get(index).id,
                        saves[index]), candidates.get(index), candidates);
            }
        });
        UiStyle.addSpaced(content, carousel, 0, 8);
        setCompletionAction(() -> requestCandidateSave(
                candidates.get(candidatePageIndex).id, saves[candidatePageIndex]),
                candidates.get(candidatePageIndex), candidates);
        carousel.post(() -> carousel.scrollTo(candidatePageIndex * pageWidth, 0));
    }

    private int candidatePageWidth() {
        int width = content.getWidth() - content.getPaddingLeft() - content.getPaddingRight();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels - dp(40);
        return Math.max(dp(280), width);
    }

    private void showCompletionAction(Button action, EventCandidate candidate) {
        ViewGroup parent = (ViewGroup) action.getParent();
        if (parent != null) parent.removeView(action);
        setCompletionAction(action::performClick, candidate,
                Collections.singletonList(candidate));
    }

    private void requestCandidateSave(long candidateId, Button action) {
        if (saveInFlight) return;
        boolean otherDirty = false;
        for (long dirtyId : dirtyCandidateIds) {
            if (dirtyId != candidateId) {
                otherDirty = true;
                break;
            }
        }
        if (otherDirty) {
            UiStyle.confirmDialog(this, "保存当前日程？",
                    "其他日程有未保存修改。继续后会放弃那些修改。", "继续保存", () -> {
                        dirtyCandidateIds.removeIf(id -> id != candidateId);
                        updateDirtyState();
                        action.performClick();
                    });
            return;
        }
        action.performClick();
    }

    private void addDetailHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setGravity(android.view.Gravity.CENTER);
        back.setContentDescription("返回");
        back.setFocusable(true);
        back.setClickable(true);
        back.setMinHeight(dp(48));
        back.setMinimumHeight(dp(48));
        back.setMinWidth(dp(48));
        back.setMinimumWidth(dp(48));
        back.setTextColor(UiStyle.colors(this).primary);
        UiStyle.glass(back);
        UiStyle.pressable(back);
        back.setOnClickListener(view -> {
            if (saveInFlight) {
                Feedback.show(this, "正在保存日程，请稍候");
            } else if (hasUnsavedEdits) {
                showUnsavedEditsDialog();
            } else {
                finish();
            }
        });
        row.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("任务详情");
        title.setTextSize(20);
        UiStyle.title(title);
        title.setPadding(dp(8), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout headerActions = new LinearLayout(this);
        headerActions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        UiStyle.glassPill(headerActions);
        completionAction = new Button(this);
        completionAction.setText("✓");
        completionAction.setAllCaps(false);
        completionAction.setContentDescription("保存日程");
        completionAction.setMinWidth(dp(48));
        completionAction.setMinimumWidth(dp(48));
        completionAction.setMinHeight(dp(48));
        completionAction.setMinimumHeight(dp(48));
        completionAction.setPadding(0, 0, 0, 0);
        completionAction.setTextColor(UiStyle.colors(this).primary);
        completionAction.setTextSize(20);
        completionAction.setTypeface(null, android.graphics.Typeface.BOLD);
        completionAction.setBackgroundColor(Color.TRANSPARENT);
        UiStyle.pressable(completionAction);
        completionAction.setVisibility(View.GONE);
        headerActions.addView(completionAction,
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        View actionDivider = new View(this);
        actionDivider.setBackgroundColor(UiStyle.colors(this).outline);
        actionDivider.setVisibility(View.GONE);
        completionAction.setTag(actionDivider);
        headerActions.addView(actionDivider,
                new LinearLayout.LayoutParams(dp(1), dp(24)));
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextSize(24);
        more.setGravity(android.view.Gravity.CENTER);
        more.setTextColor(UiStyle.colors(this).primary);
        more.setContentDescription("打开模型输出、解析用量、来源与任务操作");
        more.setMinWidth(dp(48));
        more.setMinHeight(dp(48));
        more.setMinimumWidth(dp(48));
        more.setMinimumHeight(dp(48));
        UiStyle.pressable(more);
        more.setOnClickListener(view -> showHeaderMenu());
        headerActions.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.addView(headerActions, new LinearLayout.LayoutParams(-2, dp(48)));
        UiStyle.addSpaced(content, row, 0, 2);
    }

    private void showUnsavedEditsDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = floatingDialogPanel("未保存的修改");
        TextView message = new TextView(this);
        message.setText("保存会确认并提交当前日程；放弃会丢弃修改。点击外部可继续编辑。 ");
        message.setTextSize(14);
        UiStyle.muted(message);
        UiStyle.addSpaced(panel, message, 0, 14);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button abandon = new Button(this);
        abandon.setText("放弃");
        UiStyle.button(abandon, false);
        abandon.setMinimumHeight(dp(48));
        abandon.setOnClickListener(view -> {
            dialog.dismiss();
            finish();
        });
        LinearLayout.LayoutParams abandonParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        abandonParams.setMargins(0, 0, dp(6), 0);
        actions.addView(abandon, abandonParams);
        Button save = new Button(this);
        save.setText("保存");
        UiStyle.button(save, true);
        save.setMinimumHeight(dp(48));
        save.setOnClickListener(view -> {
            dialog.dismiss();
            if (completionAction != null && completionAction.isEnabled()) {
                completionAction.performClick();
            } else {
                Feedback.show(this, "当前日程暂时无法保存");
            }
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        saveParams.setMargins(dp(6), 0, 0, 0);
        actions.addView(save, saveParams);
        panel.addView(actions);
        dialog.setCanceledOnTouchOutside(true);
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void setCompletionAction(Runnable action, EventCandidate candidate,
            List<EventCandidate> candidates) {
        if (completionAction == null) return;
        completionAction.setVisibility(View.VISIBLE);
        if (completionAction.getTag() instanceof View divider)
            divider.setVisibility(View.VISIBLE);
        completionAction.setEnabled(true);
        int remaining = Math.max(0, candidates.size() - countPublished(candidates));
        completionAction.setContentDescription("确认当前日程：" + candidate.title
                + "。本任务还有 " + remaining + " 项待确认；保存后会自动跳到下一项。");
        completionAction.setOnClickListener(view -> action.run());
    }

    private void showHeaderMenu() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        UiStyle.card(panel);
        TaskRecord task = null;
        List<EventCandidate> candidates = Collections.emptyList();
        try (TaskStore store = new TaskStore(this)) {
            task = store.getTask(taskId);
            candidates = store.getCandidates(taskId);
        } catch (Exception exception) {
            showError(exception);
        }
        boolean hasOutput = TaskRecord.PROCESSING.equals(observedStatus)
                || new StreamingOutputStore(this).length(taskId) > 0;
        boolean hasUsage = task != null && (task.totalTokens != null || task.cachedTokens != null);
        List<String> links = task == null ? Collections.emptyList() : LinkFetcher.extractUrls(task.rawText);
        TaskRecord menuTask = task;
        if (hasOutput) {
            addHeaderMenuRow(panel, dialog, "输出预览", this::showModelOutputPreviewDialog);
            addHeaderMenuRow(panel, dialog, "完整输出", () -> startActivity(
                    new Intent(this, ModelOutputActivity.class).putExtra("task_id", taskId)));
        }
        if (hasUsage) addHeaderMenuRow(panel, dialog, "解析用量", () -> showUsageDialog(menuTask));
        if (!links.isEmpty())
            addHeaderMenuRow(panel, dialog, "来源链接", () -> showLinksDialog(menuTask, links));
        int publishedCount = countPublished(candidates);
        addHeaderMenuRow(panel, dialog, "删除任务", true, () -> confirmDelete(publishedCount));
        UiStyle.showFloatingDialog(dialog, panel);
        android.view.Window window = dialog.getWindow();
        if (dialog.getWindow() != null) {
            window.setLayout(dp(240), -2);
            window.setGravity(android.view.Gravity.TOP | android.view.Gravity.RIGHT);
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.y = dp(58);
            params.x = dp(8);
            window.setAttributes(params);
        }
    }

    private void addHeaderMenuRow(LinearLayout panel, Dialog dialog, String title, Runnable action) {
        addHeaderMenuRow(panel, dialog, title, false, action);
    }

    private void addHeaderMenuRow(LinearLayout panel, Dialog dialog, String title,
            boolean destructive, Runnable action) {
        TextView row = headerMenuAction(title, destructive);
        row.setOnClickListener(view -> {
            dialog.dismiss();
            action.run();
        });
        UiStyle.addSpaced(panel, row, 2, 2);
    }

    private TextView headerMenuAction(String text, boolean destructive) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextSize(15);
        action.setGravity(android.view.Gravity.CENTER);
        action.setPadding(dp(12), 0, dp(12), 0);
        action.setMinHeight(dp(48));
        action.setTextColor(destructive ? UiStyle.danger(this) : UiStyle.colors(this).primary);
        UiStyle.pill(action, false);
        return action;
    }

    private void showModelOutputPreviewDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = floatingDialogPanel("模型输出预览");
        TextView text = new TextView(this);
        text.setTextSize(13);
        text.setLineSpacing(dp(2), 1.05f);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        UiStyle.muted(text);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(text);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(320)));
        UiStyle.showFloatingDialog(dialog, panel);
        long[] lastLength = {-1};
        StreamingOutputStore previewStore = new StreamingOutputStore(this);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            if (!dialog.isShowing()) return;
            try {
                StreamingOutputStore output = previewStore;
                long length = output.length(taskId);
                if (length != lastLength[0]) {
                    boolean follow = scroll.getScrollY() + scroll.getHeight()
                            >= text.getHeight() - dp(24);
                    lastLength[0] = length;
                    text.setText(length == 0 ? "等待模型开始输出…"
                            : output.readPreview(taskId));
                    if (follow) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                }
            } catch (Exception exception) {
                text.setText("预览暂时不可用：" + exception.getMessage());
            }
            refreshHandler.postDelayed(refresh[0], 1500L);
        };
        refreshHandler.post(refresh[0]);
        sizeFloatingDialog(dialog, dp(420), Gravity.CENTER);
    }

    private void showUsageDialog(TaskRecord task) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        if (task.totalTokens != null) label(body, "本次解析 · " + task.totalTokens + " token", 16);
        if (task.promptTokens != null) label(body, "输入 · " + task.promptTokens + " token", 14);
        if (task.completionTokens != null)
            label(body, "输出 · " + task.completionTokens + " token", 14);
        if (task.cachedTokens != null && task.promptTokens != null)
            label(body, "缓存命中 · " + task.cachedTokens + " / " + task.promptTokens
                    + " 输入 token", 14);
        Dialog dialog = new Dialog(this);
        LinearLayout panel = floatingDialogPanel("解析用量");
        addScrollableDialogBody(panel, body, 180);
        UiStyle.showFloatingDialog(dialog, panel);
        sizeFloatingDialog(dialog, dp(420), Gravity.CENTER);
    }

    private void showLinksDialog(TaskRecord task, List<String> links) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView summary = new TextView(this);
        summary.setText(linkState(task, links));
        summary.setTextSize(14);
        UiStyle.muted(summary);
        body.addView(summary);
        if (task.linkText != null && !task.linkText.trim().isEmpty()) {
            TextView contentText = new TextView(this);
            contentText.setText(task.linkText);
            contentText.setTextSize(14);
            contentText.setLineSpacing(dp(2), 1.06f);
            UiStyle.muted(contentText);
            UiStyle.addSpaced(body, contentText, 8, 0);
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = floatingDialogPanel("来源与检索");
        addScrollableDialogBody(panel, body, 320);
        UiStyle.showFloatingDialog(dialog, panel);
        sizeFloatingDialog(dialog, dp(460), Gravity.CENTER);
    }

    private LinearLayout floatingDialogPanel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(16));
        UiStyle.card(panel);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(18);
        heading.setGravity(Gravity.CENTER);
        UiStyle.title(heading);
        UiStyle.addSpaced(panel, heading, 0, 10);
        return panel;
    }

    private void addScrollableDialogBody(LinearLayout panel, View body, int heightDp) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(heightDp)));
    }

    private void sizeFloatingDialog(Dialog dialog, int maxWidth, int gravity) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        int available = getResources().getDisplayMetrics().widthPixels - dp(40);
        window.setLayout(Math.min(maxWidth, available), -2);
        window.setGravity(gravity);
    }

    private void markCandidateDirty(long candidateId) {
        dirtyCandidateIds.add(candidateId);
        updateDirtyState();
    }

    private void updateDirtyState() {
        hasUnsavedEdits = !dirtyCandidateIds.isEmpty();
        if (detailPager != null) detailPager.setTaskPagingEnabled(!hasUnsavedEdits);
    }

    /** direction +1 moves to an older inbox item; -1 moves to a newer one. */
    private void finishTaskPageGesture(float distanceX, float velocityX) {
        if (deleting || pageTransitionRunning) return;
        if (hasUnsavedEdits) {
            settleTaskPageAtCurrent();
            Feedback.show(this, "请先保存当前修改，再切换收件");
            return;
        }
        int direction = distanceX < 0 ? 1 : -1;
        float threshold = Math.max(dp(64), page.getWidth() * .18f);
        boolean quickSwipe = Math.abs(velocityX) > dp(900)
                && Math.abs(distanceX) > dp(24);
        if ((Math.abs(distanceX) < threshold && !quickSwipe) || !hasTaskPage(direction)) {
            settleTaskPageAtCurrent();
            return;
        }
        changeTaskPage(direction);
    }

    private void settleTaskPageAtCurrent() {
        if (pageTransitionRunning) return;
        shell.animate().cancel();
        float width = Math.max(page.getWidth(), getResources().getDisplayMetrics().widthPixels);
        shell.animate().translationX(0f)
                .setDuration(SwipePagerLayout.recoilDuration(
                        Math.abs(shell.getTranslationX()), width))
                .setInterpolator(SwipePagerLayout.RECOIL_INTERPOLATOR)
                .setUpdateListener(animation -> invalidateVisibleAcrylicSurfaces())
                .withEndAction(this::invalidateVisibleAcrylicSurfaces).start();
    }

    private boolean hasTaskPage(int direction) {
        try (TaskStore store = new TaskStore(this)) {
            List<TaskRecord> tasks = store.listTasks();
            int current = -1;
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).id == taskId) {
                    current = i;
                    break;
                }
            }
            int next = current + direction;
            return current >= 0 && next >= 0 && next < tasks.size();
        } catch (Exception exception) {
            showError(exception);
            return false;
        }
    }

    private long taskIdInDirection(int direction) {
        try (TaskStore store = new TaskStore(this)) {
            List<TaskRecord> tasks = store.listTasks();
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).id == taskId) {
                    int next = i + direction;
                    return next >= 0 && next < tasks.size() ? tasks.get(next).id : -1;
                }
            }
        } catch (Exception exception) {
            showError(exception);
        }
        return -1;
    }

    private void changeTaskPage(int direction) {
        if (deleting || pageTransitionRunning || direction == 0) return;
        long nextTaskId;
        try (TaskStore store = new TaskStore(this)) {
            List<TaskRecord> tasks = store.listTasks();
            int current = -1;
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).id == taskId) {
                    current = i;
                    break;
                }
            }
            int next = current + direction;
            if (current < 0 || next < 0 || next >= tasks.size()) return;
            nextTaskId = tasks.get(next).id;
        } catch (Exception exception) {
            showError(exception);
            return;
        }
        transitionToTask(nextTaskId, direction);
    }

    private void transitionToTask(long nextTaskId, int direction) {
        int outgoingDirection = direction > 0 ? -1 : 1;
        float distance = Math.max(page.getWidth(), getResources().getDisplayMetrics().widthPixels);
        if (!android.animation.ValueAnimator.areAnimatorsEnabled() || distance <= 0) {
            shell.setTranslationX(0f);
            renderTaskPage(nextTaskId);
            invalidateVisibleAcrylicSurfaces();
            return;
        }
        pageTransitionRunning = true;
        shell.animate().cancel();
        float startX = shell.getTranslationX();
        float remaining = Math.max(0f, distance - Math.abs(startX));
        long duration = Math.max(110L, Math.min(260L, Math.round(260f * remaining / distance)));
        shell.animate().translationX(outgoingDirection * distance)
                .setDuration(duration).setInterpolator(new AccelerateInterpolator(1.25f))
                .setUpdateListener(animation -> invalidateVisibleAcrylicSurfaces())
                .withEndAction(() -> {
                    renderTaskPage(nextTaskId);
                    shell.setTranslationX(-outgoingDirection * distance);
                    shell.animate().translationX(0f).setDuration(285L)
                            .setInterpolator(new DecelerateInterpolator(1.35f))
                            .setUpdateListener(animation -> invalidateVisibleAcrylicSurfaces())
                            .withEndAction(() -> {
                                pageTransitionRunning = false;
                                invalidateVisibleAcrylicSurfaces();
                            }).start();
                    invalidateVisibleAcrylicSurfaces();
                }).start();
    }

    private void renderTaskPage(long nextTaskId) {
        taskId = nextTaskId;
        candidatePageIndex = taskCandidatePagePositions.getOrDefault(nextTaskId, 0);
        resetScrollForNextRender = true;
        setIntent(new android.content.Intent(getIntent()).putExtra("task_id", taskId));
        render();
        invalidateVisibleAcrylicSurfaces();
    }

    private final class PagerPage {
        final long id;
        final ScrollView page;
        final LinearLayout content;
        final LinearLayout shell;
        final Button completionAction;
        final TextView previewText;
        final ScrollView previewScroll;
        final Button previewToggleButton;
        final View advancedDetailsView;
        final View usageDetailsView;
        final View linkBodyView;
        final long previewLength;
        final int candidatePageIndex;
        final String observedStatus;
        final int observedCandidates;
        final View gestureChild;
        final View galleryGestureChild;
        final boolean resetScroll;

        PagerPage(long id) {
            this.id = id;
            this.page = TaskDetailActivity.this.page;
            this.content = TaskDetailActivity.this.content;
            this.shell = TaskDetailActivity.this.shell;
            this.completionAction = TaskDetailActivity.this.completionAction;
            this.previewText = TaskDetailActivity.this.previewText;
            this.previewScroll = TaskDetailActivity.this.previewScroll;
            this.previewToggleButton = TaskDetailActivity.this.previewToggleButton;
            this.advancedDetailsView = TaskDetailActivity.this.advancedDetailsView;
            this.usageDetailsView = TaskDetailActivity.this.usageDetailsView;
            this.linkBodyView = TaskDetailActivity.this.linkBodyView;
            this.previewLength = TaskDetailActivity.this.previewLength;
            this.candidatePageIndex = TaskDetailActivity.this.candidatePageIndex;
            this.observedStatus = TaskDetailActivity.this.observedStatus;
            this.observedCandidates = TaskDetailActivity.this.observedCandidates;
            this.gestureChild = gesturePriorityChild;
            this.galleryGestureChild = galleryGesturePriorityChild;
            this.resetScroll = resetScrollForNextRender;
        }
    }

    private PagerPage capturePagerPage() {
        return new PagerPage(taskId);
    }

    private void activatePagerPage(PagerPage saved) {
        taskId = saved.id;
        page = saved.page;
        content = saved.content;
        shell = saved.shell;
        completionAction = saved.completionAction;
        previewText = saved.previewText;
        previewScroll = saved.previewScroll;
        previewToggleButton = saved.previewToggleButton;
        advancedDetailsView = saved.advancedDetailsView;
        usageDetailsView = saved.usageDetailsView;
        linkBodyView = saved.linkBodyView;
        previewLength = saved.previewLength;
        candidatePageIndex = saved.candidatePageIndex;
        taskCandidatePagePositions.put(saved.id, saved.candidatePageIndex);
        observedStatus = saved.observedStatus;
        observedCandidates = saved.observedCandidates;
        gesturePriorityChild = saved.gestureChild;
        galleryGesturePriorityChild = saved.galleryGestureChild;
        resetScrollForNextRender = saved.resetScroll;
        detailPager.setGesturePriorityChild(gesturePriorityChild);
        detailPager.addGesturePriorityChild(galleryGesturePriorityChild);
    }

    private PagerPage buildAdjacentPage(long targetId) {
        PagerPage current = capturePagerPage();
        ScrollView targetScroll = new ScrollView(this);
        targetScroll.setVerticalScrollBarEnabled(false);
        LinearLayout targetContent = new LinearLayout(this);
        targetContent.setOrientation(LinearLayout.VERTICAL);
        targetContent.setPadding(dp(20), dp(8), dp(20), dp(24));
        UiStyle.page(this, targetContent);
        targetContent.setBackgroundColor(Color.TRANSPARENT);
        targetScroll.addView(targetContent);
        UiStyle.applyScrollableInsets(detailPager, targetContent);
        LinearLayout targetShell = new LinearLayout(this);
        targetShell.setOrientation(LinearLayout.VERTICAL);
        targetShell.addView(targetScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        targetShell.setPadding(shell.getPaddingLeft(), shell.getPaddingTop(),
                shell.getPaddingRight(), shell.getPaddingBottom());

        page = targetScroll;
        content = targetContent;
        shell = targetShell;
        taskId = targetId;
        completionAction = null;
        previewText = null;
        previewScroll = null;
        previewLength = -1;
        candidatePageIndex = taskCandidatePagePositions.getOrDefault(targetId, 0);
        resetScrollForNextRender = true;
        render();
        PagerPage adjacent = capturePagerPage();
        activatePagerPage(current);
        adjacent.page.post(() -> adjacent.page.scrollTo(0,
                taskScrollPositions.getOrDefault(targetId, 0)));
        return adjacent;
    }

    private void finishAdjacentPageGesture(boolean commit, float width) {
        if (adjacentPage == null) return;
        float direction = activeDragDirection > 0 ? -1f : 1f;
        float currentTarget = commit ? direction * width : 0f;
        float adjacentTarget = commit ? 0f : -direction * width;
        pageTransitionRunning = true;
        shell.animate().cancel();
        adjacentPage.shell.animate().cancel();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled() || width <= 0) {
            shell.setTranslationX(currentTarget);
            adjacentPage.shell.setTranslationX(adjacentTarget);
            invalidateVisibleAcrylicSurfaces();
            completeAdjacentPageGesture(commit);
            return;
        }
        float remaining = Math.abs(currentTarget - shell.getTranslationX());
        long duration = commit
                ? Math.max(140L, Math.min(280L, Math.round(280f * remaining / width)))
                : SwipePagerLayout.recoilDuration(remaining, width);
        android.view.animation.Interpolator interpolator = commit
                ? new DecelerateInterpolator(1.35f) : SwipePagerLayout.RECOIL_INTERPOLATOR;
        shell.animate().translationX(currentTarget).setDuration(duration)
                .setInterpolator(interpolator)
                .setUpdateListener(animation -> invalidateVisibleAcrylicSurfaces()).start();
        adjacentPage.shell.animate().translationX(adjacentTarget).setDuration(duration)
                .setInterpolator(interpolator)
                .setUpdateListener(animation -> invalidateVisibleAcrylicSurfaces())
                .withEndAction(() -> completeAdjacentPageGesture(commit)).start();
    }

    private void completeAdjacentPageGesture(boolean commit) {
        PagerPage target = adjacentPage;
        if (target == null) {
            pageTransitionRunning = false;
            return;
        }
        FrameLayout stage = detailPager;
        if (commit) {
            stage.removeView(shell);
            target.shell.setTranslationX(0f);
            activatePagerPage(target);
            setIntent(new Intent(getIntent()).putExtra("task_id", taskId));
        } else {
            stage.removeView(target.shell);
            shell.setTranslationX(0f);
            activatePagerPage(capturePagerPage());
        }
        adjacentPage = null;
        dragTargetLoaded = false;
        activeDragDirection = 0;
        pageTransitionRunning = false;
        invalidateVisibleAcrylicSurfaces();
    }

    /** Re-samples acrylic cards after horizontal translations; vertical scroll alone is insufficient. */
    private void invalidateVisibleAcrylicSurfaces() {
        if (shell != null && shell.isAttachedToWindow()
                && shell.getGlobalVisibleRect(new android.graphics.Rect())) {
            UiStyle.invalidateAcrylicSurfaces(shell);
        }
        PagerPage adjacent = adjacentPage;
        if (adjacent != null && adjacent.shell.isAttachedToWindow()
                && adjacent.shell.getGlobalVisibleRect(new android.graphics.Rect())) {
            UiStyle.invalidateAcrylicSurfaces(adjacent.shell);
        }
    }

    private void addSourceSection(String rawText, LinearLayout card) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView headingLabel = new TextView(this);
        headingLabel.setText("原始内容");
        headingLabel.setTextSize(15);
        UiStyle.title(headingLabel);
        heading.addView(headingLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = new TextView(this);
        edit.setText("编辑");
        edit.setTextSize(14);
        edit.setMinHeight(dp(44));
        edit.setGravity(android.view.Gravity.CENTER);
        edit.setTextColor(UiStyle.colors(this).primary);
        edit.setOnClickListener(view -> editCaptureContent(rawText));
        heading.addView(edit, new LinearLayout.LayoutParams(dp(52), dp(44)));
        TextView body = new TextView(this);
        body.setTextSize(15);
        body.setLineSpacing(dp(2), 1.08f);
        UiStyle.muted(body);
        String source = rawText == null || rawText.trim().isEmpty()
                ? "仅图片输入" : rawText;
        body.setText(source);
        body.setMaxLines(1);
        body.setEllipsize(TextUtils.TruncateAt.END);
        UiStyle.addSpaced(card, heading, 0, 4);
        card.addView(body);
    }

    private void editCaptureContent(String rawText) {
        UiStyle.textEditorDialog(this, "编辑捕获内容", rawText, "保存", updated -> {
            try (TaskStore store = new TaskStore(this)) {
                if (!store.updateRawText(taskId, updated))
                    throw new IllegalStateException("任务不存在或已删除");
                Feedback.show(this, "捕获内容已更新");
                render();
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }

    private void addAttachmentSection(String rawText, List<String> imagePaths,
            List<TaskFileAttachment> files) {
        LinearLayout attachment = new LinearLayout(this);
        attachment.setOrientation(LinearLayout.VERTICAL);
        LinearLayout sourceCard = sectionCard();
        addSourceSection(rawText, sourceCard);
        UiStyle.addSpaced(attachment, sourceCard, 0, 4);
        label(attachment, "图片与文件", 16);
        if (!imagePaths.isEmpty()) {
            FrameLayout galleryViewport = new FrameLayout(this);
            galleryViewport.setClipChildren(true);
            galleryViewport.setClipToPadding(true);
            galleryViewport.setClipToOutline(true);
            galleryViewport.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                }
            });
            HorizontalScrollView gallery = new HorizontalScrollView(this);
            gallery.setHorizontalScrollBarEnabled(false);
            gallery.setClipChildren(true);
            gallery.setClipToPadding(true);
            gallery.setOverScrollMode(View.OVER_SCROLL_NEVER);
            galleryGesturePriorityChild = gallery;
            detailPager.addGesturePriorityChild(gallery);
            LinearLayout images = new LinearLayout(this);
            images.setOrientation(LinearLayout.HORIZONTAL);
            images.setClipChildren(true);
            for (String imagePath : imagePaths) {
                AttachmentImageTile tile = new AttachmentImageTile(this,
                        Uri.fromFile(new ImageStore(this).fileFor(imagePath)), imagePath,
                        () -> startActivity(new Intent(this, AttachmentViewerActivity.class)
                                .putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME, imagePath)),
                        () -> confirmRemoveImage(imagePath));
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(236), dp(172));
                tileParams.setMargins(0, dp(3), dp(10), dp(3));
                images.addView(tile, tileParams);
            }
            gallery.addView(images, new HorizontalScrollView.LayoutParams(-2, -2));
            galleryViewport.addView(gallery, new FrameLayout.LayoutParams(-1, -2));
            UiStyle.addSpaced(attachment, galleryViewport, 5, 2);
        } else {
            LinearLayout emptyCard = sectionCard();
            TextView empty = new TextView(this);
            empty.setText("还没有图片附件");
            empty.setTextSize(14);
            UiStyle.muted(empty);
            emptyCard.addView(empty);
            UiStyle.addSpaced(attachment, emptyCard, 4, 2);
        }
        LinearLayout addActions = new LinearLayout(this);
        addActions.setOrientation(LinearLayout.HORIZONTAL);
        addActions.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        Button addImage = compactActionButton("添加图片", R.drawable.ic_add_photo,
                this::pickCaptureImages);
        Button addFile = compactActionButton("添加文件", R.drawable.ic_attach_file,
                this::pickCaptureFiles);
        addImage.setContentDescription("添加图片附件");
        addFile.setContentDescription("添加普通文件附件");
        LinearLayout.LayoutParams addImageParams = new LinearLayout.LayoutParams(-2, -2);
        addImageParams.setMargins(0, dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams addFileParams = new LinearLayout.LayoutParams(-2, -2);
        addFileParams.setMargins(0, dp(3), 0, dp(3));
        addActions.addView(addImage, addImageParams);
        addActions.addView(addFile, addFileParams);
        attachment.addView(addActions);
        if (!files.isEmpty()) {
            label(attachment, "普通文件 · " + files.size() + "（仅发送文件名、类型和大小给 AI）", 14);
            for (TaskFileAttachment file : files) addOrdinaryFileRow(attachment, file);
        }
        LinearLayout reparseCard = sectionCard();
        LinearLayout reparseRow = new LinearLayout(this);
        reparseRow.setOrientation(LinearLayout.HORIZONTAL);
        reparseRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView hint = new TextView(this);
        hint.setText("重新解析将使用文字和全部图片；普通文件只发送名称、类型与大小。");
        hint.setTextSize(13);
        UiStyle.muted(hint);
        Button reparse = compactActionButton("重新解析", R.drawable.ic_refresh,
                this::confirmReparseCapture);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, -2, 1f);
        hintParams.setMargins(0, 0, dp(8), 0);
        reparseRow.addView(hint, hintParams);
        reparseRow.addView(reparse, new LinearLayout.LayoutParams(-2, -2));
        reparseCard.addView(reparseRow);
        UiStyle.addSpaced(attachment, reparseCard, 4, 0);
        UiStyle.addSpaced(content, attachment, 8, 8);
    }

    private Button compactActionButton(String text, int iconResource, Runnable action) {
        Button button = new Button(this);
        button.setOnClickListener(view -> action.run());
        UiStyle.glass(button);
        UiStyle.pressable(button);
        button.setTextColor(UiStyle.colors(this).primary);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        button.setMinimumHeight(dp(48));
        button.setMinHeight(dp(48));
        setCompactActionPresentation(button, text, iconResource);
        return button;
    }

    private void setCompactActionPresentation(Button button, String text, int iconResource) {
        button.setText(text);
        button.setTextSize(DETAIL_LABEL_TEXT_SP);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(9), 0, dp(9), 0);
        Drawable icon = iconResource == 0 ? null : getDrawable(iconResource);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(UiStyle.colors(this).primary);
            icon.setBounds(0, 0, dp(DETAIL_FIELD_ICON_SIZE_DP), dp(DETAIL_FIELD_ICON_SIZE_DP));
        }
        button.setCompoundDrawablesRelative(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(6));
    }

    private void confirmReparseCapture() {
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) throw new IllegalStateException("任务不存在或已删除");
            if (TaskRecord.QUEUED.equals(task.status) || TaskRecord.PROCESSING.equals(task.status)) {
                Feedback.show(this, "这条内容正在解析");
                return;
            }
            List<EventCandidate> candidates = store.getCandidates(taskId);
            for (EventCandidate candidate : candidates) {
                if (candidate.calendarEventId != null) {
                    Feedback.showLong(this, "已有日程写入系统日历，请先编辑或移除日程");
                    return;
                }
            }
            UiStyle.confirmDialog(this, "重新解析这条捕获？",
                    "将使用当前文字和全部图片重新识别；普通文件只会提供文件名、类型和大小，不会上传文件内容。现有日程草稿会被替换。",
                    "重新解析", this::retry);
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void pickCaptureImages() {
        Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
        picker.setType("image/*");
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(picker, "添加图片"), PICK_CAPTURE_IMAGES);
    }

    private void pickCaptureFiles() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(picker, PICK_CAPTURE_FILES);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_FILE_DOCUMENT && resultCode == RESULT_OK && data != null
                && data.getData() != null && exportFileName != null) {
            String name = exportFileName;
            exportFileName = null;
            new Thread(() -> {
                try {
                    new TaskFileStore(this).exportTo(name, data.getData());
                    runOnUiThread(() -> Feedback.show(this, "文件已保存"));
                } catch (Exception exception) {
                    runOnUiThread(() -> Feedback.showLong(this,
                            "保存文件失败：" + exception.getMessage()));
                }
            }, "chrona-export-file").start();
            return;
        }
        if (requestCode == PICK_CAPTURE_FILES && resultCode == RESULT_OK && data != null) {
            List<Uri> sources = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri source = clip.getItemAt(i).getUri();
                    if (source != null) sources.add(source);
                }
            } else if (data.getData() != null) {
                sources.add(data.getData());
            }
            List<Uri> images = new ArrayList<>();
            List<Uri> files = new ArrayList<>();
            for (Uri source : sources) {
                String mime = getContentResolver().getType(source);
                if (mime != null && mime.startsWith("image/")) images.add(source);
                else files.add(source);
            }
            persistPickedUris(data, files);
            addCaptureImages(images);
            addOrdinaryFiles(files);
            return;
        }
        if (requestCode != PICK_CAPTURE_IMAGES || resultCode != RESULT_OK || data == null) return;
        List<Uri> sources = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri source = clip.getItemAt(i).getUri();
                if (source != null) sources.add(source);
            }
        } else if (data.getData() != null) {
            sources.add(data.getData());
        }
        addCaptureImages(sources);
    }

    private void addCaptureImages(List<Uri> sources) {
        if (sources.isEmpty()) return;
        Feedback.show(this, "正在添加图片…");
        new Thread(() -> {
            int added = 0;
            int failed = 0;
            ImageStore images = new ImageStore(this);
            try (TaskStore store = new TaskStore(this)) {
                for (Uri source : sources) {
                    String stored = null;
                    try {
                        stored = images.importImage(source);
                        if (store.addImageAttachment(taskId, stored)) added++;
                        else {
                            images.delete(stored);
                            failed++;
                        }
                    } catch (Exception exception) {
                        if (stored != null) images.delete(stored);
                        failed++;
                    }
                }
            }
            int addedCount = added;
            int failedCount = failed;
            runOnUiThread(() -> {
                render();
                Feedback.show(this, failedCount == 0 ? "已添加 " + addedCount + " 张图片"
                        : "已添加 " + addedCount + " 张，" + failedCount + " 张失败");
            });
        }, "chrona-add-capture-images").start();
    }

    private void addOrdinaryFiles(List<Uri> sources) {
        if (sources.isEmpty()) return;
        if (android.os.Build.VERSION.SDK_INT < 29 && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            deferredFileUris.addAll(sources);
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    FILE_STORAGE_REQUEST);
            return;
        }
        Feedback.show(this, "正在添加文件…");
        new Thread(() -> {
            int added = 0;
            int failed = 0;
            TaskFileStore fileStore = new TaskFileStore(this);
            try (TaskStore store = new TaskStore(this)) {
                for (Uri source : sources) {
                    TaskFileAttachment file = null;
                    try {
                        file = fileStore.importFile(source);
                        if (store.addFileAttachment(taskId, file)) added++;
                        else {
                            failed++;
                        }
                    } catch (Exception exception) {
                        failed++;
                    }
                }
            }
            int addedCount = added;
            int failedCount = failed;
            runOnUiThread(() -> {
                render();
                Feedback.show(this, failedCount == 0 ? "已添加 " + addedCount + " 个文件"
                        : "已添加 " + addedCount + " 个，" + failedCount + " 个失败");
            });
        }, "chrona-add-task-files").start();
    }

    private void addOrdinaryFileRow(LinearLayout parent, TaskFileAttachment file) {
        LinearLayout card = sectionCard();
        TextView name = new TextView(this);
        name.setText(file.displayName);
        name.setTextSize(14);
        name.setMaxLines(2);
        UiStyle.title(name);
        card.addView(name);
        TextView metadata = new TextView(this);
        metadata.setText(file.mimeType + " · " + formatFileSize(file.sizeBytes));
        metadata.setTextSize(12);
        UiStyle.muted(metadata);
        UiStyle.addSpaced(card, metadata, 2, 2);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button open = compactActionButton("↗ 打开", 0, () -> openOrdinaryFile(file));
        Button save = compactActionButton("↓ 保存副本", 0, () -> exportFile(file));
        Button remove = compactActionButton("× 移除", 0,
                () -> confirmRemoveOrdinaryFile(file));
        for (Button action : new Button[] {open, save, remove}) {
            action.setTextSize(12);
            action.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            actions.addView(action, params);
        }
        UiStyle.addSpaced(card, actions, 2, 0);
        UiStyle.addSpaced(parent, card, 3, 3);
    }

    private void exportFile(TaskFileAttachment file) {
        exportFileName = file.storedName;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(file.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, file.displayName);
        try {
            startActivityForResult(intent, CREATE_FILE_DOCUMENT);
        } catch (Exception exception) {
            exportFileName = null;
            Feedback.showLong(this, "无法打开保存位置：" + exception.getMessage());
        }
    }

    private void openOrdinaryFile(TaskFileAttachment file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(file.storedName), file.mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "打开附件"));
        } catch (Exception exception) {
            Feedback.showLong(this, "没有可用的应用打开此文件，可选择“保存副本”");
        }
    }

    private void persistPickedUris(Intent intent, List<Uri> sources) {
        if ((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return;
        for (Uri source : sources) {
            try {
                getContentResolver().takePersistableUriPermission(source,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The Android 10+ path copies immediately; legacy providers may grant only briefly.
            }
        }
    }

    private void removeOrdinaryFile(TaskFileAttachment file) {
        try (TaskStore store = new TaskStore(this)) {
            store.removeFileAttachment(taskId, file.id);
            render();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void confirmRemoveOrdinaryFile(TaskFileAttachment file) {
        UiStyle.confirmDialog(this, "移除普通文件？",
                "将从此任务移除“" + file.displayName
                        + "”的附件关联。公共 Downloads 文件不会被删除。",
                "移除关联", () -> removeOrdinaryFile(file));
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(java.util.Locale.ROOT,
                "%.1f KB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.ROOT,
                "%.1f MB", bytes / (1024f * 1024));
        return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024f * 1024 * 1024));
    }

    private void addStatusSection(TaskRecord task, List<String> links, int publishedCount,
            boolean noCandidates) {
        boolean showRetry = noCandidates || TaskRecord.FAILED.equals(task.status);
        boolean hasError = task.errorMessage != null && !task.errorMessage.trim().isEmpty();
        if (!hasError) return;
        LinearLayout card = sectionCard();
        if (hasError) {
            TextView error = new TextView(this);
            error.setText(task.errorMessage);
            error.setTextSize(14);
            UiStyle.muted(error);
            UiStyle.addSpaced(card, error, 8, 0);
        }
        UiStyle.addSpaced(content, card, 3, 5);
    }

    private void addAdvancedDetails(TaskRecord task, List<String> links, int publishedCount,
            boolean hasCandidates, boolean processing, long outputBytes) {
        LinearLayout card = sectionCard();
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (!links.isEmpty()) addLinkSection(task, links, details);
        if (task.totalTokens != null || task.cachedTokens != null)
            addUsageSection(task, details);
        if (details.getChildCount() == 0) return;
        card.addView(details);
        card.setVisibility(detailsExpanded ? View.VISIBLE : View.GONE);
        advancedDetailsView = card;
        UiStyle.addSpaced(content, card, 8, 14);
    }

    private void addPreview(boolean processing, LinearLayout destination) {
        LinearLayout card = sectionCard();
        label(card, processing ? "模型实时输出" : "模型输出", 16);
        TextView hint = new TextView(this);
        hint.setText("实时预览只保留最新片段；完整输出可按页查看。");
        hint.setTextSize(14);
        UiStyle.muted(hint);
        UiStyle.addSpaced(card, hint, 4, 8);
        previewScroll = new ScrollView(this);
        final ScrollView previewOwnerPage = page;
        previewScroll.setFillViewport(false);
        previewScroll.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN
                    || action == android.view.MotionEvent.ACTION_MOVE) {
                previewOwnerPage.requestDisallowInterceptTouchEvent(true);
            } else if (action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL) {
                previewOwnerPage.requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        previewText = new TextView(this);
        previewText.setTextSize(13);
        previewText.setLineSpacing(dp(2), 1.05f);
        previewText.setTypeface(android.graphics.Typeface.MONOSPACE);
        previewText.setTextIsSelectable(true);
        UiStyle.muted(previewText);
        previewScroll.addView(previewText);
        card.addView(previewScroll, new LinearLayout.LayoutParams(-1, dp(156)));
        previewScroll.setVisibility(previewExpanded ? View.VISIBLE : View.GONE);
        previewToggleButton = compactActionButton(
                previewExpanded ? "收起预览" : "展开预览", R.drawable.ic_expand_more, () -> { });
        previewToggleButton.setOnClickListener(view -> {
            previewExpanded = !previewExpanded;
            setCompactActionPresentation(previewToggleButton,
                    previewExpanded ? "收起预览" : "展开预览",
                    previewExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            animateSection(previewScroll, previewExpanded);
        });
        if (previewExpanded) previewScroll.setVisibility(View.VISIBLE);
        LinearLayout previewActions = new LinearLayout(this);
        previewActions.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        previewActions.addView(previewToggleButton, new LinearLayout.LayoutParams(-2, -2));
        UiStyle.addSpaced(card, previewActions, 5, 0);
        UiStyle.addSpaced(destination, card, 8, 8);
        refreshPreview();
    }

    private LinearLayout sectionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        UiStyle.card(card);
        return card;
    }

    private void animateSection(View view, boolean showing) {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setVisibility(showing ? View.VISIBLE : View.GONE);
            return;
        }
        if (showing) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationY(dp(6));
            view.animate().alpha(1f).translationY(0f).setDuration(180).start();
        } else {
            view.animate().alpha(0f).translationY(-dp(4)).setDuration(130)
                    .withEndAction(() -> {
                        view.setVisibility(View.GONE);
                        view.setAlpha(1f);
                        view.setTranslationY(0f);
                    }).start();
        }
    }

    private void addLinkSection(TaskRecord task, List<String> links, LinearLayout destination) {
        LinearLayout card = sectionCard();
        label(card, "链接与检索", 16);
        TextView summary = new TextView(this);
        summary.setText(linkState(task, links));
        summary.setTextSize(14);
        UiStyle.muted(summary);
        card.addView(summary);
        if (task.linkText != null && !task.linkText.trim().isEmpty()) {
            TextView body = new TextView(this);
            body.setTextSize(14);
            UiStyle.muted(body);
            body.setVisibility(linksExpanded ? View.VISIBLE : View.GONE);
            if (linksExpanded) body.setText(task.linkText);
            card.addView(body);
            linkBodyView = body;
        }
        UiStyle.addSpaced(destination, card, 4, 4);
    }

    private void addUsageSection(TaskRecord task, LinearLayout destination) {
        LinearLayout card = sectionCard();
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(usageExpanded ? View.VISIBLE : View.GONE);
        usageDetailsView = details;
        if (task.totalTokens != null) label(details, "本次解析 · " + task.totalTokens + " token", 14);
        if (task.promptTokens != null) label(details, "输入 · " + task.promptTokens + " token", 14);
        if (task.completionTokens != null)
            label(details, "输出 · " + task.completionTokens + " token", 14);
        if (task.cachedTokens != null && task.promptTokens != null)
            label(details, "缓存命中 · " + task.cachedTokens + " / " + task.promptTokens
                    + " 输入 token", 14);
        card.addView(details);
        UiStyle.addSpaced(destination, card, 4, 4);
    }

    private static String statusLabel(String status) {
        if (TaskRecord.QUEUED.equals(status)) return "排队中";
        if (TaskRecord.PROCESSING.equals(status)) return "解析中";
        if (TaskRecord.NEEDS_REVIEW.equals(status)) return "待确认";
        if (TaskRecord.READY.equals(status)) return "已写入日历";
        if (TaskRecord.FAILED.equals(status)) return "解析失败";
        return "任务状态";
    }

    private StreamingOutputStore previewOutputStore;

    private void refreshPreview() {
        if (previewText == null) return;
        if (previewOutputStore == null) previewOutputStore = new StreamingOutputStore(this);
        StreamingOutputStore output = previewOutputStore;
        long length = output.length(taskId);
        if (length == previewLength) return;
        previewLength = length;
        boolean follow = previewScroll.getVisibility() == View.VISIBLE
                && previewScroll.getScrollY() + previewScroll.getHeight()
                >= previewText.getHeight() - dp(24);
        try {
            previewText.setText(length == 0 ? "等待模型开始输出…"
                    : output.readPreview(taskId));
            if (follow) previewScroll.post(() -> previewScroll.fullScroll(View.FOCUS_DOWN));
        } catch (Exception exception) {
            previewText.setText("预览暂时不可用：" + exception.getMessage());
        }
    }

    private void confirmDelete(int publishedCount) {
        if (publishedCount > 0 && !requestCalendarPermission()) return;
        String message = publishedCount == 0
                ? "删除这条输入及其日程草稿，无法撤销。"
                : "删除这条输入、日程草稿，以及已写入日历的 " + publishedCount + " 条日程，无法撤销。";
        UiStyle.confirmDialog(this, "确认删除任务？", message, "删除", () -> {
                    deleting = true;
                    content.removeAllViews();
                    label("正在删除任务与关联日程…", 18);
                    new Thread(this::deleteTask, "chrona-task-delete").start();
                });
    }

    private void deleteTask() {
        try (TaskStore store = new TaskStore(this)) {
            ProcessingJobService.cancel(this, taskId);
            List<String> imagePaths = store.getImagePaths(taskId);
            int ordinaryFileCount = store.getFileAttachments(taskId).size();
            CalendarStore calendar = new CalendarStore(this);
            for (EventCandidate candidate : store.getCandidates(taskId)) {
                if (candidate.calendarEventId != null) {
                    // A missing provider row is already deleted; provider errors stop the removal.
                    calendar.deleteEvent(candidate.calendarEventId);
                }
            }
            if (!store.deleteTask(taskId)) throw new IllegalStateException("任务不存在或已删除");
            new StreamingOutputStore(this).delete(taskId);
            ImageStore images = new ImageStore(this);
            for (String imagePath : imagePaths) images.delete(imagePath);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel((int) taskId);
            DiagLog.add(this, "task deleted id=" + taskId + " images=" + imagePaths.size()
                    + " publicFiles=" + ordinaryFileCount);
            runOnUiThread(() -> {
                Feedback.show(this, "任务已删除");
                finish();
            });
        } catch (Exception exception) {
            runOnUiThread(() -> {
                deleting = false;
                render();
                showError(exception);
            });
        }
    }

    /** Drops the attachment so a text-only retry stays possible when images are rejected. */
    private void confirmRemoveImage(String imagePath) {
        UiStyle.confirmDialog(this, "移除图片？",
                "将从任务捕获内容中删除图片“" + imagePath
                        + "”；重新解析将不再使用它。",
                "移除图片", () -> removeImage(imagePath));
    }

    private void removeImage(String imagePath) {
        try (TaskStore store = new TaskStore(this)) {
            String removed = store.removeImageAttachment(taskId, imagePath);
            if (removed != null) new ImageStore(this).delete(removed);
            render();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void confirmRemoveCandidate(EventCandidate candidate, int number) {
        boolean published = candidate.calendarEventId != null;
        if (published && !requestCalendarPermission()) return;
        UiStyle.confirmDialog(this, "删除日程 " + number + "？", published
                        ? "将从系统日历中删除这条日程，并移除对应草稿。此操作无法撤销。"
                        : "将移除这条日程草稿。此操作无法撤销。", "删除",
                () -> removeCandidate(candidate, published));
    }

    private void removeCandidate(EventCandidate candidate, boolean published) {
        deleting = true;
        content.removeAllViews();
        label("正在删除日程…", 18);
        new Thread(() -> {
            try (TaskStore store = new TaskStore(this)) {
                if (published) {
                    // A missing provider row is already deleted; provider errors stop the removal.
                    new CalendarStore(this).deleteEvent(candidate.calendarEventId);
                }
                if (!store.deleteCandidate(candidate.id, taskId)) {
                    throw new IllegalStateException("日程草稿不存在或已被删除");
                }
                store.updateStatus(taskId, reviewStatus(store.getCandidates(taskId)), null);
                runOnUiThread(() -> {
                    deleting = false;
                    render();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    deleting = false;
                    render();
                    showError(exception);
                });
            }
        }, "chrona-candidate-delete").start();
    }

    private Button addCandidateEditor(EventCandidate candidate, int number,
            boolean dockSaveAction) {
        return addCandidateEditor(candidate, number, dockSaveAction, content, -1);
    }

    private Button addCandidateEditor(EventCandidate candidate, int number,
            boolean dockSaveAction, LinearLayout destination, int pageWidth) {
        EventCandidate defaulted = EventTimeDefaults.completeInterval(candidate);
        boolean[] autoEnd = {candidate.endAtMillis == null || candidate.endAutoGenerated};
        boolean[] syncingRange = {false};
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        UiStyle.glass(card);
        if (destination == content) {
            UiStyle.addSpaced(destination, card, 12, 6);
        } else {
            int pageGutter = dp(8);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Math.max(dp(240), pageWidth - pageGutter * 2), -2);
            params.setMargins(pageGutter, dp(6), pageGutter, dp(6));
            destination.addView(card, params);
        }
        LinearLayout cardHeader = new LinearLayout(this);
        cardHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView cardTitle = new TextView(this);
        cardTitle.setText("日程 " + number);
        cardTitle.setTextSize(17);
        UiStyle.title(cardTitle);
        cardHeader.addView(cardTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton removeCandidate = new ImageButton(this);
        removeCandidate.setContentDescription("删除日程 " + candidate.title);
        removeCandidate.setOnClickListener(view -> confirmRemoveCandidate(candidate, number));
        removeCandidate.setPadding(0, 0, 0, 0);
        Drawable deleteIcon = getDrawable(R.drawable.ic_delete);
        if (deleteIcon != null) deleteIcon.setTint(UiStyle.colors(this).primary);
        removeCandidate.setImageDrawable(deleteIcon);
        removeCandidate.setScaleType(ImageView.ScaleType.CENTER);
        UiStyle.glass(removeCandidate);
        UiStyle.pressable(removeCandidate);
        removeCandidate.setMinimumWidth(dp(48));
        removeCandidate.setMinimumHeight(dp(48));
        cardHeader.addView(removeCandidate, new LinearLayout.LayoutParams(dp(48), dp(48)));
        UiStyle.addSpaced(card, cardHeader, 0, 2);
        EditText title = inlineField(formRow(card, "标题", R.drawable.ic_title),
                "添加标题", candidate.title, 1f);
        trackUnsavedChanges(title, candidate.id);
        AllDayToggle allDay = new AllDayToggle(this, dp(DETAIL_FIELD_ICON_SIZE_DP),
                UiStyle.colors(this).primary, UiStyle.colors(this).onPrimaryContainer);
        int[] selectedCategory = {EventCategory.indexOf(candidate.category)};
        TextView category = new TextView(this);
        category.setText(EventCategory.LABELS[selectedCategory[0]]);
        UiStyle.fieldTrigger(category);
        category.setTextSize(DETAIL_VALUE_TEXT_SP);
        category.setMinHeight(dp(40));
        category.setPadding(dp(4), dp(5), dp(4), dp(5));
        category.setCompoundDrawablePadding(dp(4));
        category.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout categoryRow = formRow(card, "类型", R.drawable.ic_event);
        categoryRow.addView(category, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout timeRows = new LinearLayout(this);
        timeRows.setOrientation(LinearLayout.VERTICAL);
        UiStyle.addSpaced(card, timeRows, 1, 1);
        LinearLayout startRow = new LinearLayout(this);
        startRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        startRow.setPadding(0, dp(3), 0, dp(3));
        startRow.addView(fieldLabel("时间", R.drawable.ic_schedule),
                new LinearLayout.LayoutParams(dp(DETAIL_FIELD_LABEL_WIDTH_DP), -2));
        LinearLayout endRow = new LinearLayout(this);
        endRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        endRow.setPadding(0, dp(3), 0, dp(3));
        String startHint = candidate.allDay ? "开始日期：yyyy-MM-dd"
                : EventCategory.DEADLINE.equals(candidate.category)
                        ? "截止前开始：yyyy-MM-dd HH:mm" : "开始：yyyy-MM-dd HH:mm";
        String endHint = candidate.allDay ? "结束日期（含当天）：yyyy-MM-dd"
                : EventCategory.DEADLINE.equals(candidate.category)
                        ? "截止时间：yyyy-MM-dd HH:mm" : "结束：yyyy-MM-dd HH:mm";
        EditText start = inlineField(startRow, startHint,
                candidate.allDay ? dayStart(defaulted.startAtMillis)
                        : format(defaulted.startAtMillis), 1f);
        start.setPadding(dp(4), dp(5), dp(4), dp(5));
        start.setMinHeight(dp(48));
        allDay.setChecked(candidate.allDay);
        endRow.addView(allDay, new LinearLayout.LayoutParams(
                dp(DETAIL_FIELD_LABEL_WIDTH_DP), dp(48)));
        EditText end = inlineField(endRow, endHint,
                candidate.allDay ? dayEnd(defaulted.endAtMillis)
                        : format(defaulted.endAtMillis), 1f);
        end.setPadding(dp(4), dp(5), dp(4), dp(5));
        end.setMinHeight(dp(48));
        timeRows.addView(startRow, new LinearLayout.LayoutParams(-1, -2));
        timeRows.addView(endRow, new LinearLayout.LayoutParams(-1, -2));
        configureTimePicker(start, allDay, "开始时间");
        configureTimePicker(end, allDay, "结束 / 截止时间");
        String[] timedRange = {start.getText().toString(), end.getText().toString()};
        trackUnsavedChanges(start, candidate.id);
        trackUnsavedChanges(end, candidate.id);
        start.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { }
                @Override public void afterTextChanged(Editable editable) {
                markCandidateDirty(candidate.id);
                if (!autoEnd[0] || syncingRange[0] || allDay.isChecked()) return;
                try {
                    Long startAt = parse(editable.toString(), ZoneId.systemDefault());
                    if (startAt == null) return;
                    syncingRange[0] = true;
                    end.setText(format(startAt + EventTimeDefaults.durationMillis(
                            EventCategory.VALUES[selectedCategory[0]])));
                    syncingRange[0] = false;
                } catch (DateTimeParseException ignored) { }
            }
        });
        end.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                markCandidateDirty(candidate.id);
                if (!syncingRange[0]) autoEnd[0] = false;
            }
        });
        category.setOnClickListener(view -> UiStyle.choiceDialog(this, "日程类型",
                EventCategory.LABELS, selectedCategory[0], index -> {
                    if (index == selectedCategory[0]) return;
                    markCandidateDirty(candidate.id);
                    int previousCategory = selectedCategory[0];
                    selectedCategory[0] = index;
                    category.setText(EventCategory.LABELS[index]);
                    boolean deadline = EventCategory.DEADLINE.equals(
                            EventCategory.VALUES[index]);
                    start.setHint(allDay.isChecked() ? "开始日期：yyyy-MM-dd" : deadline ? "截止前开始：yyyy-MM-dd HH:mm"
                            : "开始：yyyy-MM-dd HH:mm");
                    end.setHint(allDay.isChecked() ? "结束日期（含当天）：yyyy-MM-dd" : deadline ? "截止时间：yyyy-MM-dd HH:mm"
                            : "结束：yyyy-MM-dd HH:mm");
                    if (!autoEnd[0] || allDay.isChecked()) return;
                    try {
                        ZoneId zone = ZoneId.systemDefault();
                        Long startAt = parse(start.getText().toString(), zone);
                        Long endAt = parse(end.getText().toString(), zone);
                        Long anchor = EventCategory.DEADLINE.equals(
                                EventCategory.VALUES[previousCategory]) ? endAt : startAt;
                        if (anchor == null) return;
                        long nextStart;
                        long nextEnd;
                        if (deadline) {
                            long due = EventCategory.DEADLINE.equals(
                                    EventCategory.VALUES[previousCategory]) ? anchor : startAt;
                            nextStart = due - EventTimeDefaults.durationMillis(
                                    EventCategory.DEADLINE);
                            nextEnd = due;
                        } else {
                            nextStart = EventCategory.DEADLINE.equals(
                                    EventCategory.VALUES[previousCategory]) ? anchor : startAt;
                            nextEnd = nextStart + EventTimeDefaults.durationMillis(
                                    EventCategory.VALUES[index]);
                        }
                        syncingRange[0] = true;
                        start.setText(format(nextStart));
                        end.setText(format(nextEnd));
                        syncingRange[0] = false;
                    } catch (DateTimeParseException ignored) {
                        // The user can correct an incomplete time without losing the new category.
                    }
                }));
        allDay.setOnCheckedChangeListener((view, checked) -> {
            markCandidateDirty(candidate.id);
            boolean deadline = EventCategory.DEADLINE.equals(
                    EventCategory.VALUES[selectedCategory[0]]);
            start.setHint(checked ? "开始日期：yyyy-MM-dd" : deadline
                    ? "截止前开始：yyyy-MM-dd HH:mm" : "开始：yyyy-MM-dd HH:mm");
            end.setHint(checked ? "结束日期（含当天）：yyyy-MM-dd" : deadline
                    ? "截止时间：yyyy-MM-dd HH:mm" : "结束：yyyy-MM-dd HH:mm");
            syncingRange[0] = true;
            if (checked) {
                timedRange[0] = start.getText().toString();
                timedRange[1] = end.getText().toString();
                start.setText(datePart(start.getText().toString()));
                end.setText(datePart(end.getText().toString()));
            } else {
                start.setText(withTime(start.getText().toString(), clockPart(timedRange[0], "09:00")));
                try {
                    Long startAt = parse(start.getText().toString(), ZoneId.systemDefault());
                    if (autoEnd[0]) end.setText(startAt == null ? "" : format(startAt
                            + EventTimeDefaults.durationMillis(
                                    EventCategory.VALUES[selectedCategory[0]])));
                    else end.setText(withTime(end.getText().toString(), clockPart(timedRange[1], "09:00")));
                } catch (DateTimeParseException ignored) {
                    end.setText("");
                }
            }
            syncingRange[0] = false;
        });
        EditText location = inlineField(formRow(card, "地点", R.drawable.ic_place),
                "不填写", candidate.location, 1f);
        String[] currentDescription = {candidate.description == null ? "" : candidate.description};
        LinearLayout noteRow = formRow(card, "备注", R.drawable.ic_notes);
        TextView notePreview = new TextView(this);
        notePreview.setText(shortNote(currentDescription[0]));
        notePreview.setTextSize(DETAIL_VALUE_TEXT_SP);
        notePreview.setMaxLines(2);
        notePreview.setEllipsize(TextUtils.TruncateAt.END);
        notePreview.setGravity(android.view.Gravity.CENTER_VERTICAL);
        UiStyle.muted(notePreview);
        notePreview.setPadding(dp(4), 0, 0, 0);
        noteRow.addView(notePreview, new LinearLayout.LayoutParams(0, -2, 1f));
        Button editNote = compactActionButton("展开", R.drawable.ic_expand_more,
                () -> showNoteEditorDialog(candidate, currentDescription, notePreview));
        editNote.setContentDescription("展开并编辑备注");
        noteRow.addView(editNote, new LinearLayout.LayoutParams(-2, -2));
        notePreview.setOnClickListener(view -> showNoteEditorDialog(candidate,
                currentDescription, notePreview));
        notePreview.setContentDescription("备注，点按展开并编辑；" + currentDescription[0]);
        LinearLayout reminderRow = formRow(card, "提醒", R.drawable.ic_notifications);
        EditText reminder = inlineField(reminderRow, "不提醒",
                candidate.reminderMinutesBefore == null ? ""
                        : candidate.reminderMinutesBefore.toString(), .5f);
        TextView reminderSuffix = new TextView(this);
        reminderSuffix.setText("分钟");
        reminderSuffix.setTextSize(DETAIL_VALUE_TEXT_SP);
        UiStyle.muted(reminderSuffix);
        reminderRow.addView(reminderSuffix);
        trackUnsavedChanges(location, candidate.id);
        trackUnsavedChanges(reminder, candidate.id);
        Button[] publish = new Button[1];
        publish[0] = button(card, candidate.calendarEventId == null
                ? "确认并写入日历" : "保存并更新日历", () -> {
            if (!requestCalendarPermission()) return;
            try {
                String titleText = title.getText().toString().trim();
                if (titleText.isEmpty()) throw new IllegalArgumentException("请填写标题");
                ZoneId zone = ZoneId.systemDefault();
                Long startAt = allDay.isChecked()
                        ? parseDayStart(start.getText().toString())
                        : parse(start.getText().toString(), zone);
                Long endAt = allDay.isChecked()
                        ? parseDayEnd(end.getText().toString())
                        : parse(end.getText().toString(), zone);
                if (startAt == null || endAt == null || endAt <= startAt) {
                    throw new IllegalArgumentException("请填写有效的开始和结束日期，结束不能早于开始");
                }
                String reminderText = reminder.getText().toString().trim();
                Integer minutes = reminderText.isEmpty() ? null : Integer.parseInt(reminderText);
                if (minutes != null && minutes < 0) throw new IllegalArgumentException("提醒分钟数不能为负数");
                EventCandidate edited = new EventCandidate(candidate.id, taskId, titleText,
                        startAt, endAt, allDay.isChecked() ? "UTC" : zone.getId(),
                        location.getText().toString().trim(),
                        currentDescription[0].trim(), minutes, false,
                        candidate.calendarEventId,
                        EventCategory.VALUES[selectedCategory[0]], allDay.isChecked(), autoEnd[0]);
                saveInFlight = true;
                if (completionAction != null) completionAction.setEnabled(false);
                new Thread(() -> checkCalendarDuplicateThenPublish(edited),
                        "chrona-calendar-write").start();
            } catch (DateTimeParseException | NumberFormatException exception) {
                Feedback.showLong(this, "请按提示格式填写日期或时间，提醒填写数字");
            } catch (Exception exception) {
                showError(exception);
            }
        });
        if (dockSaveAction) {
            ViewGroup parent = (ViewGroup) publish[0].getParent();
            if (parent != null) parent.removeView(publish[0]);
        }
        return publish[0];
    }

    private void publish(EventCandidate edited) {
        try (TaskStore store = new TaskStore(this)) {
            CalendarStore calendar = new CalendarStore(this);
            List<Integer> reminders = edited.reminderMinutesBefore == null
                    ? Collections.emptyList() : Collections.singletonList(edited.reminderMinutesBefore);
            CalendarStore.EventInput input = new CalendarStore.EventInput(edited.title,
                    edited.startAtMillis, edited.endAtMillis, edited.timeZoneId, edited.allDay,
                    edited.description, edited.location, reminders);
            if (edited.calendarEventId != null) {
                if (!store.updateCandidate(edited))
                    throw new IllegalStateException("无法保存日程草稿");
                if (!calendar.updateEvent(edited.calendarEventId, input)) {
                    throw new IllegalStateException("日历中的原日程已被删除，请重新解析或手动添加");
                }
            } else {
                if (!store.updateCandidate(edited))
                    throw new IllegalStateException("无法保存日程草稿");
                long eventId = calendar.insertEvent(input);
                if (!store.setCalendarEventId(edited.id, taskId, eventId)) {
                    calendar.deleteEvent(eventId);
                    throw new IllegalStateException("无法保存日程关联");
                }
            }
            List<EventCandidate> savedCandidates = store.getCandidates(taskId);
            store.updateStatus(taskId, reviewStatus(savedCandidates), null);
            savedCandidates = store.getCandidates(taskId);
            DiagLog.add(this, "calendar written task=" + taskId + " candidate=" + edited.id
                    + " event=" + edited.calendarEventId);
            List<EventCandidate> result = savedCandidates;
            runOnUiThread(() -> {
                continueCandidateConfirmation(edited.id, result);
            });
        } catch (Exception exception) {
            runOnUiThread(() -> {
                saveInFlight = false;
                if (completionAction != null) completionAction.setEnabled(true);
                showCandidateError(edited, exception);
            });
        }
    }

    private void checkCalendarDuplicateThenPublish(EventCandidate edited) {
        if (edited.calendarEventId != null) {
            publish(edited);
            return;
        }
        try {
            List<Integer> reminders = edited.reminderMinutesBefore == null
                    ? Collections.emptyList() : Collections.singletonList(edited.reminderMinutesBefore);
            CalendarStore.EventInput input = new CalendarStore.EventInput(edited.title,
                    edited.startAtMillis, edited.endAtMillis, edited.timeZoneId, edited.allDay,
                    edited.description, edited.location, reminders);
            List<Long> matches = new CalendarStore(this).findMatchingEvents(input);
            if (matches.isEmpty()) {
                publish(edited);
                return;
            }
            runOnUiThread(() -> {
                saveInFlight = false;
                if (completionAction != null) completionAction.setEnabled(true);
                String[] options = new String[matches.size() + 2];
                for (int i = 0; i < matches.size(); i++)
                    options[i] = "关联已有日程（" + matches.get(i) + "）";
                options[matches.size()] = "仍然新建";
                options[matches.size() + 1] = "取消";
                UiStyle.choiceDialog(this, "发现相同日程 · 选择关联或新建",
                        options, -1, choice -> {
                            if (choice < 0 || choice >= options.length - 1) return;
                            saveInFlight = true;
                            if (completionAction != null) completionAction.setEnabled(false);
                            if (choice < matches.size()) {
                                long eventId = matches.get(choice);
                                new Thread(() -> associateExistingEvent(edited, eventId),
                                        "chrona-calendar-link").start();
                            } else {
                                new Thread(() -> publish(edited), "chrona-calendar-write").start();
                            }
                        });
            });
        } catch (Exception exception) {
            runOnUiThread(() -> {
                saveInFlight = false;
                if (completionAction != null) completionAction.setEnabled(true);
                showCandidateError(edited, exception);
            });
        }
    }

    private void associateExistingEvent(EventCandidate edited, long eventId) {
        try (TaskStore store = new TaskStore(this)) {
            EventCandidate linked = new EventCandidate(edited.id, edited.taskId, edited.title,
                    edited.startAtMillis, edited.endAtMillis, edited.timeZoneId, edited.location,
                    edited.description, edited.reminderMinutesBefore, edited.needsConfirmation,
                    eventId, edited.category, edited.allDay, edited.endAutoGenerated);
            if (!store.updateCandidate(linked)
                    || !store.setCalendarEventId(edited.id, taskId, eventId))
                throw new IllegalStateException("无法保存日程关联");
            List<EventCandidate> savedCandidates = store.getCandidates(taskId);
            store.updateStatus(taskId, reviewStatus(savedCandidates), null);
            savedCandidates = store.getCandidates(taskId);
            List<EventCandidate> result = savedCandidates;
            runOnUiThread(() -> {
                continueCandidateConfirmation(edited.id, result);
            });
        } catch (Exception exception) {
            runOnUiThread(() -> {
                saveInFlight = false;
                if (completionAction != null) completionAction.setEnabled(true);
                showCandidateError(edited, exception);
            });
        }
    }

    private void continueCandidateConfirmation(long savedCandidateId,
            List<EventCandidate> candidates) {
        dirtyCandidateIds.remove(savedCandidateId);
        updateDirtyState();
        saveInFlight = false;
        int nextPending = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).calendarEventId == null) {
                nextPending = i;
                break;
            }
        }
        if (nextPending < 0) {
            Feedback.show(this, "整条任务已确认，日程均已写入日历");
            finish();
            return;
        }
        int remaining = candidates.size() - countPublished(candidates);
        candidatePageIndex = nextPending;
        taskCandidatePagePositions.put(taskId, candidatePageIndex);
        render();
        Feedback.show(this, "当前日程已保存；还有 " + remaining + " 项待确认，已切换到下一项");
    }

    private void showCandidateError(EventCandidate candidate, Exception exception) {
        String detail = exception.getMessage();
        Feedback.showLong(this, "日程“" + candidate.title + "”尚未完成，仍待确认："
                + (detail == null || detail.trim().isEmpty() ? "保存失败，请重试" : detail));
    }

    private void retry() {
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) throw new IllegalStateException("任务不存在或已删除");
            if (task.rawText.trim().isEmpty() && store.getImagePaths(taskId).isEmpty()
                    && store.getFileAttachments(taskId).isEmpty()) {
                Feedback.showLong(this, "没有可解析的内容，请补充文字或附件");
                return;
            }
            for (EventCandidate candidate : store.getCandidates(taskId)) {
                if (candidate.calendarEventId != null) {
                    throw new IllegalStateException("已有日程写入日历，请直接编辑已有日程");
                }
            }
            if (new AiSettingsStore(this).load() == null) {
                Feedback.showLong(this, "请先在设置中配置 AI 服务");
                return;
            }
            store.replaceCandidates(taskId, Collections.emptyList());
            new StreamingOutputStore(this).delete(taskId);
            store.updateStatus(taskId, TaskRecord.QUEUED, null);
            ProcessingJobService.enqueue(this, taskId);
            DiagLog.add(this, "retry task=" + taskId);
            Feedback.show(this, "已加入解析队列");
            render();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void addManualCandidate() {
        ArrayList<EventCandidate> entries = new ArrayList<>();
        entries.add(new EventCandidate(0, taskId, "新日程", null, null,
                ZoneId.systemDefault().getId(), "", "", null, true));
        try (TaskStore store = new TaskStore(this)) {
            store.replaceCandidates(taskId, entries);
            store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, null);
            render();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private EditText field(String hint, String value) {
        return field(content, hint, value);
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        if (value != null) edit.setText(value);
        UiStyle.input(edit);
        UiStyle.addSpaced(parent, edit, 5, 5);
        return edit;
    }

    private LinearLayout formRow(LinearLayout card, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        row.addView(fieldLabel(label, 0),
                new LinearLayout.LayoutParams(dp(DETAIL_FIELD_LABEL_WIDTH_DP), -2));
        UiStyle.addSpaced(card, row, 1, 1);
        return row;
    }

    private LinearLayout formRow(LinearLayout card, String label, int iconResource) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        row.addView(fieldLabel(label, iconResource),
                new LinearLayout.LayoutParams(dp(DETAIL_FIELD_LABEL_WIDTH_DP), -2));
        UiStyle.addSpaced(card, row, 1, 1);
        return row;
    }

    private LinearLayout fieldLabel(String label, int iconResource) {
        LinearLayout key = new LinearLayout(this);
        key.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (iconResource != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResource);
            icon.setColorFilter(UiStyle.colors(this).primary);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            key.addView(icon, new LinearLayout.LayoutParams(
                    dp(DETAIL_FIELD_ICON_SIZE_DP), dp(DETAIL_FIELD_ICON_SIZE_DP)));
        }
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(DETAIL_LABEL_TEXT_SP);
        text.setContentDescription(label);
        UiStyle.muted(text);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        if (iconResource != 0) textParams.setMargins(dp(4), 0, 0, 0);
        key.addView(text, textParams);
        return key;
    }

    private String shortNote(String note) {
        String value = note == null ? "" : note.trim();
        return value.isEmpty() ? "添加备注" : value;
    }

    private void showNoteEditorDialog(EventCandidate candidate, String[] currentDescription,
            TextView preview) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = floatingDialogPanel("备注");
        EditText editor = new EditText(this);
        editor.setText(currentDescription[0]);
        editor.setSingleLine(false);
        editor.setMinLines(5);
        editor.setMaxLines(Integer.MAX_VALUE);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setHint("补充日程说明");
        editor.setTextSize(15);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        UiStyle.input(editor);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(editor, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(280)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setOnClickListener(view -> dialog.dismiss());
        UiStyle.button(cancel, false);
        cancel.setMinimumHeight(dp(48));
        Button save = new Button(this);
        save.setText("保存备注");
        save.setOnClickListener(view -> {
            String value = editor.getText().toString().trim();
            try (TaskStore store = new TaskStore(this)) {
                EventCandidate updated = new EventCandidate(candidate.id, taskId, candidate.title,
                        candidate.startAtMillis, candidate.endAtMillis, candidate.timeZoneId,
                        candidate.location, value, candidate.reminderMinutesBefore,
                        candidate.needsConfirmation, candidate.calendarEventId,
                        candidate.category, candidate.allDay, candidate.endAutoGenerated);
                if (!store.updateCandidate(updated))
                    throw new IllegalStateException("无法保存备注");
                currentDescription[0] = value;
                preview.setText(shortNote(value));
                preview.setContentDescription("备注，点按展开并编辑；" + value);
                dialog.dismiss();
                Feedback.show(this, "备注已保存");
            } catch (Exception exception) {
                showError(exception);
            }
        });
        UiStyle.button(save, true);
        save.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        cancelParams.setMargins(0, 0, dp(6), 0);
        actions.addView(cancel, cancelParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, -2, 1f);
        saveParams.setMargins(dp(6), 0, 0, 0);
        actions.addView(save, saveParams);
        panel.addView(actions);
        UiStyle.showFloatingDialog(dialog, panel);
        editor.requestFocus();
    }

    /** Date/time values are chosen atomically; cancelling either step leaves the field intact. */
    private void configureTimePicker(EditText field, AllDayToggle allDay, String title) {
        field.setKeyListener(null);
        field.setFocusable(false);
        field.setClickable(true);
        field.setContentDescription(title + "，点按选择日期和时间");
        field.setOnClickListener(view -> {
            LocalDateTime initial = LocalDateTime.now().withSecond(0).withNano(0);
            try {
                String value = field.getText().toString().trim();
                if (!value.isEmpty()) initial = allDay.isChecked()
                        ? java.time.LocalDate.parse(value).atStartOfDay()
                        : LocalDateTime.parse(value, DATE_TIME);
            } catch (DateTimeParseException ignored) { }
            final LocalDateTime seed = initial;
            final boolean dateOnly = allDay.isChecked();
            Dialog dialog = new Dialog(this);
            LinearLayout panel = floatingDialogPanel(title);
            GlassDateTimePickerView picker = new GlassDateTimePickerView(this, seed);
            TextView selected = new TextView(this);
            selected.setTextSize(15);
            selected.setGravity(Gravity.CENTER);
            UiStyle.title(selected);
            Runnable displaySelection = () -> selected.setText(dateOnly
                    ? picker.value().toLocalDate().toString() : picker.value().format(DATE_TIME));
            picker.onChanged(displaySelection);
            displaySelection.run();
            UiStyle.addSpaced(panel, selected, 0, 8);
            android.graphics.Rect visible = new android.graphics.Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            int availableHeight = visible.height() > 0 ? visible.height()
                    : getResources().getDisplayMetrics().heightPixels;
            int pickerHeight = Math.max(dp(64), Math.min(dp(240), availableHeight - dp(170)));
            panel.addView(picker, new LinearLayout.LayoutParams(-1, pickerHeight));
            LinearLayout actions = new LinearLayout(this);
            Button cancel = new Button(this);
            cancel.setText("取消");
            UiStyle.button(cancel, false);
            cancel.setOnClickListener(v -> dialog.dismiss());
            Button choose = new Button(this);
            choose.setText(dateOnly ? "确定" : "选择时间");
            UiStyle.button(choose, false);
            boolean[] timeStep = {false};
            selected.setContentDescription("当前选择，点按返回日期选择");
            selected.setOnClickListener(v -> {
                picker.finishSelection();
                timeStep[0] = false;
                picker.showTime(false);
                choose.setText(dateOnly ? "确定" : "选择时间");
            });
            choose.setOnClickListener(v -> {
                picker.finishSelection();
                if (!dateOnly && !timeStep[0]) {
                    timeStep[0] = true;
                    picker.showTime(true);
                    choose.setText("确定");
                    return;
                }
                String value = dateOnly ? picker.value().toLocalDate().toString()
                        : picker.value().format(DATE_TIME);
                field.setText(value);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1f);
            left.setMargins(0, 0, dp(6), 0);
            actions.addView(cancel, left);
            actions.addView(choose, new LinearLayout.LayoutParams(0, dp(48), 1f));
            panel.addView(actions);
            // Account for actual title/button heights, including the user's font scale.
            int panelWidth = Math.min(dp(420), getResources().getDisplayMetrics().widthPixels - dp(40));
            panel.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, panelWidth), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int overhead = panel.getMeasuredHeight() - picker.getMeasuredHeight();
            picker.getLayoutParams().height = Math.max(1, Math.min(dp(240), availableHeight - overhead - dp(32)));
            picker.requestLayout();
            UiStyle.showFloatingDialog(dialog, panel);
            dialog.setCanceledOnTouchOutside(true);
            sizeFloatingDialog(dialog, dp(420), Gravity.CENTER);
        });
    }

    private static String clockPart(String value, String fallback) {
        return value != null && value.length() >= 16 ? value.substring(11, 16) : fallback;
    }

    private EditText inlineField(LinearLayout row, String hint, String value, float weight) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        if (value != null) edit.setText(value);
        UiStyle.input(edit);
        edit.setTextSize(DETAIL_VALUE_TEXT_SP);
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setPadding(dp(4), dp(9), dp(4), dp(9));
        edit.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        edit.setMinWidth(0);
        edit.setMinimumWidth(0);
        LinearLayout.LayoutParams params = row.getOrientation() == LinearLayout.VERTICAL
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, weight);
        row.addView(edit, params);
        return edit;
    }

    private void trackUnsavedChanges(EditText edit, long candidateId) {
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before,
                    int count) { markCandidateDirty(candidateId); }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private void label(String value, int size) {
        label(content, value, size);
    }

    private void label(LinearLayout parent, String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(0, dp(5), 0, dp(5));
        if (size >= 19) UiStyle.title(text);
        else UiStyle.muted(text);
        parent.addView(text);
    }

    private Button button(String value, Runnable action) {
        return button(content, value, action);
    }

    private Button button(LinearLayout parent, String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setOnClickListener(view -> action.run());
        UiStyle.button(button, value.startsWith("确认并") || value.startsWith("保存并"));
        UiStyle.addSpaced(parent, button, 5, 5);
        return button;
    }

    private static String format(Long millis) {
        return millis == null ? "" : DATE_TIME.format(
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
    }

    private static Long parse(String value, ZoneId zone) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : LocalDateTime.parse(trimmed, DATE_TIME)
                .atZone(zone).toInstant().toEpochMilli();
    }

    private static Long parseDayStart(String value) {
        return value.trim().isEmpty() ? null : AllDayDates.utcStart(value);
    }

    private static Long parseDayEnd(String value) {
        return value.trim().isEmpty() ? null : AllDayDates.utcExclusiveEnd(value);
    }

    private static String dayStart(Long millis) {
        return millis == null ? "" : AllDayDates.displayStart(millis);
    }

    private static String dayEnd(Long millis) {
        return millis == null ? "" : AllDayDates.displayEnd(millis);
    }

    private static String datePart(String value) {
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    private static String withTime(String value, String time) {
        return value.length() == 10 ? value + " " + time : value;
    }

    private void showError(Exception exception) {
        Feedback.showLong(this, "操作失败：" + exception.getMessage());
    }

    /** Returns true when both calendar permissions are already granted. */
    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Requests the calendar permissions when missing; false means the caller must wait for a retry. */
    private boolean requestCalendarPermission() {
        if (hasCalendarPermission()) return true;
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION_REQUEST);
        Feedback.show(this, "授权日历权限后请再次点击");
        return false;
    }

    private static int countPublished(List<EventCandidate> candidates) {
        int count = 0;
        for (EventCandidate candidate : candidates) {
            if (candidate.calendarEventId != null) count++;
        }
        return count;
    }

    /** Describes what the last on-demand retrieval managed to read from the input's links. */
    private static String linkState(TaskRecord task, List<String> links) {
        String found = "联网检索：输入里有 " + links.size() + " 个链接。";
        if (task.linkFetchedAtMillis == null) {
            return found + "解析时会自动抓取正文。";
        }
        String when = DateFormat.getDateTimeInstance().format(new Date(task.linkFetchedAtMillis));
        if (task.linkText == null) {
            return found + when + " 抓取失败或没有正文，已按原文解析。";
        }
        return found + when + " 已抓取 " + task.linkText.length() + " 字。";
    }

    private static String excerpt(String text) {
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 220 ? flat : flat.substring(0, 220) + "…";
    }

    /** The input is ready only when at least one draft exists and every draft is in the calendar. */
    private static String reviewStatus(List<EventCandidate> candidates) {
        if (candidates.isEmpty() || countPublished(candidates) < candidates.size()) {
            return TaskRecord.NEEDS_REVIEW;
        }
        return TaskRecord.READY;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
