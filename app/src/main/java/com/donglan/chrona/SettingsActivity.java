package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.ai.SchedulePreferences;

/** User-supplied HTTPS Chat Completions endpoint and model. */
public final class SettingsActivity extends Activity {
    private EditText baseUrl;
    private EditText model;
    private EditText apiKey;
    private TextView imageState;
    private Button allowImages;
    private static final String[] EFFORT_VALUES = {"auto", "none", "low", "medium", "high", "max"};
    private static final String[] EFFORT_LABELS = {"自动", "关闭", "低", "中", "高", "最高"};
    private String reasoningEffort = "auto";
    private TextView effortChoice;
    private EditText customPrompt;
    private EditText classroomPrompt;
    private Switch enhancedClassroom;
    private int classroomConcurrency;
    private TextView classroomConcurrencyChoice;
    private int classroomNoteStrength;
    private TextView classroomNoteStrengthLabel;
    private SeekBar classroomNoteStrengthSlider;
    private ImageButton classroomNoteStrengthInfo;
    private int workStartMinutes;
    private int workEndMinutes;
    private TextView workStart;
    private TextView workEnd;
    private String[] savedForm;
    private android.window.OnBackInvokedCallback settingsBack;

    @Override
    protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "AI 服务设置");
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(16), dp(16), dp(16));
        UiStyle.glass(form);
        TextView presetTitle = new TextView(this);
        presetTitle.setText("常用模型预设（选择后仍可编辑）");
        presetTitle.setTextSize(15);
        UiStyle.muted(presetTitle);
        form.addView(presetTitle);
        String[] presets = {"自定义服务", "DeepSeek Flash", "DeepSeek Pro"};
        int[] selectedPreset = {0};
        TextView preset = new TextView(this);
        preset.setText(presets[0]);
        UiStyle.fieldTrigger(preset);
        UiStyle.addSpaced(form, preset, 5, 10);
        baseUrl = field(form, "API 基础地址（含 /v1）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        model = field(form, "模型名称", InputType.TYPE_CLASS_TEXT);
        TextView effortTitle = new TextView(this);
        effortTitle.setText("思考强度");
        UiStyle.muted(effortTitle);
        UiStyle.addSpaced(form, effortTitle, 8, 4);
        effortChoice = new TextView(this);
        effortChoice.setText(EFFORT_LABELS[0]);
        UiStyle.fieldTrigger(effortChoice);
        UiStyle.addSpaced(form, effortChoice, 4, 8);
        TextView effortNote = new TextView(this);
        effortNote.setText("按模型能力映射；部分模型无法关闭思考，自定义接口可能忽略此选项。");
        effortNote.setTextSize(12);
        UiStyle.muted(effortNote);
        UiStyle.addSpaced(form, effortNote, 0, 4);
        effortChoice.setOnClickListener(view -> UiDialogs.choiceDialog(this, "思考强度",
                EFFORT_LABELS, effortIndex(), position -> setEffort(EFFORT_VALUES[position])));
        preset.setOnClickListener(view -> UiDialogs.choiceDialog(this, "常用模型预设",
                presets, selectedPreset[0], position -> {
                selectedPreset[0] = position;
                preset.setText(presets[position]);
                if (position == 0) return;
                baseUrl.setText("https://api.deepseek.com");
                model.setText(position == 1 ? "deepseek-flash" : "deepseek-v4-pro");
        }));
        apiKey = field(form, "API 密钥（留空表示不更改）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        SchedulePreferences preferences = SchedulePreferences.load(this);
        workStartMinutes = state == null ? preferences.workStartMinutes
                : state.getInt("workStartMinutes", preferences.workStartMinutes);
        workEndMinutes = state == null ? preferences.workEndMinutes
                : state.getInt("workEndMinutes", preferences.workEndMinutes);
        TextView scheduleTitle = new TextView(this);
        scheduleTitle.setText("解析与总结");
        scheduleTitle.setTextSize(18);
        UiStyle.title(scheduleTitle);
        UiStyle.addSpaced(form, scheduleTitle, 20, 8);
        workStart = new TextView(this);
        workEnd = new TextView(this);
        UiStyle.fieldTrigger(workStart);
        UiStyle.fieldTrigger(workEnd);
        workStart.setSingleLine(false);
        workEnd.setSingleLine(false);
        UiStyle.addSpaced(form, workStart, 4, 4);
        UiStyle.addSpaced(form, workEnd, 4, 8);
        refreshWorkTime();
        workStart.setOnClickListener(view -> UiDialogs.timeDialog(this, "工作开始时间", workStartMinutes,
                selected -> { workStartMinutes = selected; refreshWorkTime(); }));
        workEnd.setOnClickListener(view -> UiDialogs.timeDialog(this, "工作结束时间", workEndMinutes,
                selected -> { workEndMinutes = selected; refreshWorkTime(); }));
        TextView promptTitle = new TextView(this);
        promptTitle.setText("日程解析提示词 · 优先于默认规则");
        UiStyle.muted(promptTitle);
        UiStyle.addSpaced(form, promptTitle, 8, 4);
        customPrompt = field(form, "日程解析偏好", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        customPrompt.setSingleLine(false);
        customPrompt.setMinLines(4);
        customPrompt.setGravity(Gravity.TOP | Gravity.START);
        customPrompt.setText(state == null ? preferences.customPrompt
                : state.getString("customPrompt", preferences.customPrompt));
        TextView classroomTitle = new TextView(this);
        classroomTitle.setText("课堂总结提示词 · 优先于默认规则");
        UiStyle.muted(classroomTitle);
        UiStyle.addSpaced(form, classroomTitle, 12, 4);
        classroomPrompt = field(form, "课堂总结偏好", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        classroomPrompt.setSingleLine(false);
        classroomPrompt.setMinLines(4);
        classroomPrompt.setGravity(Gravity.TOP | Gravity.START);
        classroomPrompt.setText(state == null ? preferences.classroomPrompt
                : state.getString("classroomPrompt", preferences.classroomPrompt));
        classroomNoteStrength = state == null ? preferences.classroomNoteStrength
                : state.getInt("classroomNoteStrength", preferences.classroomNoteStrength);
        classroomNoteStrengthLabel = new TextView(this);
        UiStyle.muted(classroomNoteStrengthLabel);
        classroomNoteStrengthLabel.setTextSize(16);
        UiStyle.addSpaced(form, classroomNoteStrengthLabel, 8, 0);
        LinearLayout noteStrengthRow = new LinearLayout(this);
        noteStrengthRow.setOrientation(LinearLayout.HORIZONTAL);
        noteStrengthRow.setGravity(Gravity.CENTER_VERTICAL);
        noteStrengthRow.setClipChildren(false);
        noteStrengthRow.setClipToPadding(false);
        classroomNoteStrengthSlider = new SeekBar(this);
        classroomNoteStrengthSlider.setContentDescription("笔记整理强度");
        classroomNoteStrengthSlider.setMin(1);
        classroomNoteStrengthSlider.setMax(3);
        classroomNoteStrengthSlider.setProgress(classroomNoteStrength);
        int sliderColor = UiStyle.colors(this).primary;
        classroomNoteStrengthSlider.setThumbTintList(ColorStateList.valueOf(sliderColor));
        classroomNoteStrengthSlider.setProgressTintList(ColorStateList.valueOf(sliderColor));
        classroomNoteStrengthSlider.setProgressBackgroundTintList(ColorStateList.valueOf(
                Color.argb(72, Color.red(sliderColor), Color.green(sliderColor), Color.blue(sliderColor))));
        noteStrengthRow.addView(classroomNoteStrengthSlider, new LinearLayout.LayoutParams(0, dp(48), 1));
        classroomNoteStrengthInfo = UiStyle.navigationIcon(this, R.drawable.ic_info, "笔记整理强度介绍",
                () -> startActivity(new Intent(this, ClassroomNoteStrengthActivity.class)));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        infoParams.leftMargin = dp(8);
        noteStrengthRow.addView(classroomNoteStrengthInfo, infoParams);
        UiStyle.addSpaced(form, noteStrengthRow, 0, 4);
        refreshClassroomNoteStrength();
        classroomNoteStrengthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                classroomNoteStrength = progress;
                refreshClassroomNoteStrength();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        enhancedClassroom = new Switch(this);
        enhancedClassroom.setText("增强整理");
        enhancedClassroom.setTextSize(16);
        enhancedClassroom.setChecked(state == null ? preferences.enhancedClassroom
                : state.getBoolean("enhancedClassroom", preferences.enhancedClassroom));
        enhancedClassroom.setSwitchPadding(dp(12));
        UiStyle.toggle(enhancedClassroom);
        enhancedClassroom.setMinHeight(dp(54));
        UiStyle.addSpaced(form, enhancedClassroom, 8, 0);
        TextView enhancedNote = new TextView(this);
        enhancedNote.setText("更正字幕后分段总结，耗时和Token用量更高");
        enhancedNote.setTextSize(13);
        UiStyle.muted(enhancedNote);
        UiStyle.addSpaced(form, enhancedNote, 0, 4);
        classroomConcurrency = state == null ? preferences.classroomConcurrency
                : state.getInt("classroomConcurrency", preferences.classroomConcurrency);
        classroomConcurrencyChoice = new TextView(this);
        UiStyle.fieldTrigger(classroomConcurrencyChoice, true);
        refreshClassroomConcurrency();
        UiStyle.addSpaced(form, classroomConcurrencyChoice, 8, 4);
        classroomConcurrencyChoice.setOnClickListener(view -> UiDialogs.choiceDialog(this, "课堂总结并发数",
                new String[]{"1", "2", "3"}, classroomConcurrency - 1, position -> {
                    classroomConcurrency = position + 1;
                    refreshClassroomConcurrency();
                }));
        Button save = new Button(this);
        save.setText("保存设置");
        save.setOnClickListener(view -> save());
        UiStyle.button(save, true);
        UiStyle.addSpaced(form, save, 12, 4);
        UiStyle.addSpaced(root, form, 8, 18);

        LinearLayout imageCard = new LinearLayout(this);
        imageCard.setOrientation(LinearLayout.VERTICAL);
        imageCard.setPadding(dp(16), dp(13), dp(16), dp(13));
        UiStyle.glass(imageCard);
        TextView imageTitle = new TextView(this);
        imageTitle.setText("图片支持");
        imageTitle.setTextSize(18);
        UiStyle.title(imageTitle);
        imageCard.addView(imageTitle);
        imageState = new TextView(this);
        imageState.setTextSize(14);
        imageState.setPadding(0, dp(8), 0, dp(6));
        UiStyle.muted(imageState);
        imageCard.addView(imageState);
        allowImages = new Button(this);
        allowImages.setText("重新允许图片输入");
        allowImages.setOnClickListener(view -> {
            new AiSettingsStore(this).clearImageSupport();
            refreshImageState();
            Feedback.show(this, "图片输入已重新启用");
        });
        UiStyle.button(allowImages, false);
        UiStyle.addSpaced(imageCard, allowImages, 5, 5);
        UiStyle.addSpaced(root, imageCard, 0, 8);

        SettingsPageLayout.show(this, root, state == null);
        try {
            AiSettings existing = new AiSettingsStore(this).load();
            if (existing != null) {
                baseUrl.setText(existing.baseUrl);
                model.setText(existing.model);
                setEffort(existing.reasoningEffort);
                if ("https://api.deepseek.com".equals(existing.baseUrl)) {
                    if ("deepseek-flash".equals(existing.model)) selectedPreset[0] = 1;
                    else if ("deepseek-v4-pro".equals(existing.model)) selectedPreset[0] = 2;
                    preset.setText(presets[selectedPreset[0]]);
                }
            }
        } catch (Exception exception) {
            Feedback.showLong(this, "无法读取现有设置，请重新填写密钥");
        }
        ConfigBackup.Imported pending = ConfigBackup.pending(this);
        if (pending != null) {
            try {
                if (new AiSettingsStore(this).load() == null) {
                    baseUrl.setText(pending.baseUrl);
                    model.setText(pending.model);
                    setEffort(pending.reasoningEffort);
                }
            } catch (Exception ignored) { }
        }
        savedForm = state == null ? formValues() : state.getStringArray("savedForm");
        if (savedForm == null) savedForm = formValues();
        if (state != null) restoreForm(state.getStringArray("settingsForm"));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            settingsBack = this::requestExit;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, settingsBack);
        }
        refreshImageState();
    }

    /** The shared title back button calls finish; every exit uses the same guard. */
    @Override public void finish() { requestExit(); }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { requestExit(); }

    private void requestExit() {
        if (savedForm == null || java.util.Arrays.equals(savedForm, formValues())) {
            super.finish();
            return;
        }
        UiDialogs.choiceDialog(this, "保存修改？", new String[]{"保存", "取消", "放弃"}, -1,
                choice -> {
                    if (choice == 0) save();
                    else if (choice == 2) super.finish();
                });
    }

    private String[] formValues() {
        return new String[]{baseUrl.getText().toString(), model.getText().toString(),
                apiKey.getText().toString(), reasoningEffort, Integer.toString(workStartMinutes),
                Integer.toString(workEndMinutes), customPrompt.getText().toString(),
                classroomPrompt.getText().toString(), Boolean.toString(enhancedClassroom.isChecked()),
                Integer.toString(classroomConcurrency), Integer.toString(classroomNoteStrength)};
    }

    private void restoreForm(String[] values) {
        if (values == null) return;
        baseUrl.setText(values[0]);
        model.setText(values[1]);
        apiKey.setText(values[2]);
        setEffort(values[3]);
        workStartMinutes = Integer.parseInt(values[4]);
        workEndMinutes = Integer.parseInt(values[5]);
        customPrompt.setText(values[6]);
        classroomPrompt.setText(values[7]);
        enhancedClassroom.setChecked(Boolean.parseBoolean(values[8]));
        classroomConcurrency = Integer.parseInt(values[9]);
        classroomNoteStrength = Integer.parseInt(values[10]);
        classroomNoteStrengthSlider.setProgress(classroomNoteStrength);
        refreshClassroomNoteStrength();
        refreshClassroomConcurrency();
        refreshWorkTime();
    }

    @Override protected void onDestroy() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && settingsBack != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(settingsBack);
        super.onDestroy();
    }

    /** Shows whether this endpoint and model were already found to reject images. */
    private void refreshImageState() {
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            AiSettings settings = store.load();
            if (settings == null) {
                imageState.setText("尚未配置服务，无法判断是否支持图片。");
                allowImages.setVisibility(android.view.View.GONE);
            } else if (store.isKnownImageUnsupported(settings)) {
                imageState.setText("DeepSeek Pro 当前不支持视觉输入，图片入口已隐藏。"
                        + "如需识别图片，请改用支持视觉的模型。");
                allowImages.setVisibility(android.view.View.GONE);
            } else if (store.isImageUnsupported(settings)) {
                imageState.setText("该模型上次拒绝了图片输入，收件箱的图片入口已停用。"
                        + "确认模型支持视觉后可重新启用。");
                allowImages.setVisibility(android.view.View.VISIBLE);
            } else {
                imageState.setText("当前模型未发现图片输入问题。");
                allowImages.setVisibility(android.view.View.GONE);
            }
        } catch (Exception exception) {
            imageState.setText("无法读取现有设置：" + exception.getMessage());
            allowImages.setVisibility(android.view.View.GONE);
        }
    }

    private EditText field(LinearLayout root, String hint, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(type);
        edit.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        UiStyle.input(edit);
        UiStyle.addSpaced(root, edit, 5, 5);
        return edit;
    }

    /** Saves service settings; file transfer belongs to BackupRestoreActivity. */
    private void save() {
        try {
            SchedulePreferences preferences = new SchedulePreferences(workStartMinutes, workEndMinutes,
                    customPrompt.getText().toString(), classroomPrompt.getText().toString(),
                    enhancedClassroom.isChecked(), classroomConcurrency, classroomNoteStrength);
            AiSettingsStore store = new AiSettingsStore(this);
            String key = apiKey.getText().toString().trim();
            if (key.isEmpty()) {
                AiSettings existing = store.load();
                if (existing != null) key = existing.apiKey;
            }
            store.save(baseUrl.getText().toString().trim(), model.getText().toString().trim(), key,
                    reasoningEffort);
            preferences.save(this);
            ConfigBackup.clearDraft(this);
            Feedback.show(this, "设置已保存");
            super.finish();
        } catch (Exception exception) {
            Feedback.showLong(this, "保存失败：" + exception.getMessage());
        }
    }

    private int effortIndex() {
        for (int i = 0; i < EFFORT_VALUES.length; i++)
            if (EFFORT_VALUES[i].equals(reasoningEffort)) return i;
        return 0;
    }

    private void refreshWorkTime() {
        workStart.setText("工作开始  " + SchedulePreferences.time(workStartMinutes));
        workEnd.setText("工作结束  " + SchedulePreferences.time(workEndMinutes)
                + (workEndMinutes < workStartMinutes ? " · 次日" : ""));
    }

    private void refreshClassroomConcurrency() {
        classroomConcurrencyChoice.setText("课堂总结并发数  " + classroomConcurrency);
    }

    private void refreshClassroomNoteStrength() {
        classroomNoteStrengthLabel.setText("笔记整理强度 · " + classroomNoteStrength + "档");
        if (android.os.Build.VERSION.SDK_INT >= 30)
            classroomNoteStrengthSlider.setStateDescription(classroomNoteStrength + "档");
        else classroomNoteStrengthSlider.setContentDescription("笔记整理强度 · " + classroomNoteStrength + "档");
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putStringArray("savedForm", savedForm);
        state.putStringArray("settingsForm", formValues());
        state.putInt("workStartMinutes", workStartMinutes);
        state.putInt("workEndMinutes", workEndMinutes);
        state.putString("customPrompt", customPrompt.getText().toString());
        state.putString("classroomPrompt", classroomPrompt.getText().toString());
        state.putBoolean("enhancedClassroom", enhancedClassroom.isChecked());
        state.putInt("classroomConcurrency", classroomConcurrency);
        state.putInt("classroomNoteStrength", classroomNoteStrength);
        super.onSaveInstanceState(state);
    }

    private void setEffort(String value) {
        reasoningEffort = value;
        effortChoice.setText(EFFORT_LABELS[effortIndex()]);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
