package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Theme tokens and semantic control styles for the native View interface. */
public final class UiStyle {
    public static final int PAGE_TOP_DP = 8;
    public static final int HEADER_HEIGHT_DP = 64;
    /** Content card inset and the gap between adjacent records, shared by every list page. */
    public static final int CARD_INSET_H_DP = 16;
    public static final int CARD_INSET_V_DP = 12;
    public static final int RECORD_GAP_DP = 8;

    /** 48dp controls share a centerline 40dp below the system safe area's top. */
    public static LinearLayout headerRow(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(row, HEADER_HEIGHT_DP));
        row.setClipChildren(false); row.setClipToPadding(false);
        return row;
    }

    public static ImageButton navigationIcon(Activity activity, int resource, String label, Runnable action) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(colors(activity).primary));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(button, 12), dp(button, 12), dp(button, 12), dp(button, 12));
        button.setContentDescription(label); button.setTooltipText(label);
        acrylicChoice(button, false, 999, false);
        button.setOnClickListener(view -> action.run()); pressable(button);
        return button;
    }
    private static final java.util.ArrayList<ChildSurfaceRecord> CHILD_SURFACES =
            new java.util.ArrayList<>();

    private static final class ChildSurfaceRecord {
        final java.lang.ref.WeakReference<View> view;
        int kind, radius;
        boolean primary;
        boolean selected;
        boolean outline = true;
        Runnable customRefresh;
        ChildSurfaceRecord(View view, int kind, boolean primary, int radius, boolean selected) {
            this(view, kind, primary, radius, selected, null);
        }
        ChildSurfaceRecord(View view, int kind, boolean primary, int radius, boolean selected,
                Runnable customRefresh) {
            this.view = new java.lang.ref.WeakReference<>(view);
            this.kind = kind;
            this.primary = primary;
            this.radius = radius;
            this.selected = selected;
            this.customRefresh = customRefresh;
        }
    }

    /** One radius scale for the whole app: fields and buttons, cards, sheets, and chips. */
    static final int RADIUS_FIELD = CornerScale.FIELD;
    static final int RADIUS_CARD = CornerScale.CARD;
    static final int RADIUS_PANEL = CornerScale.PANEL;
    static final int RADIUS_PILL = CornerScale.PILL;
    /** The animated Dock keeps its independently tuned round-ended geometry. */
    static final int RADIUS_DOCK = 28;
    /** Filter chips: a soft rectangle, deliberately much less round than a pill. */
    static final int RADIUS_CHIP = CornerScale.CHIP;

    static int radius(Context context, int token) {
        return CornerScale.radius(token, ThemeStore.cornerLevel(context));
    }

    static final class Palette {
        final int background, surface, surfaceAlt, text, muted, outline, primary, onPrimary;
        final int primaryContainer, onPrimaryContainer;
        Palette(int background, int surface, int surfaceAlt, int text, int muted, int outline,
                int primary, int onPrimary, int primaryContainer, int onPrimaryContainer) {
            this.background = background;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.text = text;
            this.muted = muted;
            this.outline = outline;
            this.primary = primary;
            this.onPrimary = onPrimary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
        }
    }

    private UiStyle() { }

    static Palette colors(Context context) {
        boolean dark = ThemeStore.dark(context);
        int background, surface, surfaceAlt, text, muted, outline;
        String scheme = ThemeStore.color(context);
        int primary, container, onContainer;
        int seed = ThemeStore.paletteSeed(context, scheme);
        if (seed != 0) return accentPalette(dark, seed);
        if (ThemeStore.WALLPAPER.equals(scheme) && ThemeStore.wallpaperAvailable()) {
            background = context.getColor(dark ? android.R.color.system_neutral1_900
                    : android.R.color.system_neutral1_10);
            surface = context.getColor(dark ? android.R.color.system_neutral1_800
                    : android.R.color.system_neutral1_50);
            surfaceAlt = context.getColor(dark ? android.R.color.system_neutral2_800
                    : android.R.color.system_neutral2_100);
            text = context.getColor(dark ? android.R.color.system_neutral1_50
                    : android.R.color.system_neutral1_900);
            muted = context.getColor(dark ? android.R.color.system_neutral2_200
                    : android.R.color.system_neutral2_700);
            outline = context.getColor(dark ? android.R.color.system_neutral2_700
                    : android.R.color.system_neutral2_200);
            primary = context.getColor(dark ? android.R.color.system_accent1_200
                    : android.R.color.system_accent1_700);
            container = context.getColor(dark ? android.R.color.system_accent1_800
                    : android.R.color.system_accent1_100);
            onContainer = context.getColor(dark ? android.R.color.system_accent1_100
                    : android.R.color.system_accent1_900);
        } else if (ThemeStore.BLUE.equals(scheme)) {
            background = dark ? 0xFF101722 : 0xFFF7F9FF;
            surface = dark ? 0xFF1C2636 : 0xFFFFFFFF;
            surfaceAlt = dark ? 0xFF27354B : 0xFFEAF0FB;
            text = dark ? 0xFFE4ECFA : 0xFF17263E;
            muted = dark ? 0xFFAFBED6 : 0xFF586B89;
            outline = dark ? 0xFF42536C : 0xFFD7E2F1;
            primary = dark ? 0xFFA9C7FF : 0xFF315CA7;
            container = dark ? 0xFF243F6A : 0xFFD8E5FF;
            onContainer = dark ? 0xFFD8E5FF : 0xFF17345F;
        } else if (ThemeStore.CORAL.equals(scheme)) {
            background = dark ? 0xFF201613 : 0xFFFFF8F5;
            surface = dark ? 0xFF30221D : 0xFFFFFFFF;
            surfaceAlt = dark ? 0xFF403029 : 0xFFFFEEE8;
            text = dark ? 0xFFF8E8E0 : 0xFF37231C;
            muted = dark ? 0xFFD6B9AB : 0xFF806457;
            outline = dark ? 0xFF644A3F : 0xFFF0DCD2;
            primary = dark ? 0xFFFFB4A1 : 0xFF9B4B32;
            container = dark ? 0xFF603426 : 0xFFFFDED4;
            onContainer = dark ? 0xFFFFDED4 : 0xFF622B1A;
        } else {
            background = dark ? 0xFF101B1B : 0xFFF6F8F5;
            surface = dark ? 0xFF1A2928 : Color.WHITE;
            surfaceAlt = dark ? 0xFF233431 : 0xFFEAF2EE;
            text = dark ? 0xFFE5F3EE : 0xFF172B2A;
            muted = dark ? 0xFFADC5BB : 0xFF566F67;
            outline = dark ? 0xFF40554D : 0xFFD9E6DE;
            primary = dark ? 0xFF80D8C9 : 0xFF006B60;
            container = dark ? 0xFF12483F : 0xFFC8F1E6;
            onContainer = dark ? 0xFFC8F1E6 : 0xFF004C44;
        }
        return new Palette(background, surface, surfaceAlt, text, muted, outline,
                primary, dark ? background : Color.WHITE, container, onContainer);
    }

    private static Palette lastAccentPalette;
    private static boolean lastAccentDark;
    private static int lastAccentSeed;

    private static synchronized Palette accentPalette(boolean dark, int seed) {
        // Every acrylic draw asks for this immutable palette. Reuse the pure
        // (mode, seed) result instead of allocating HSV arrays for each cell.
        if (lastAccentPalette != null && lastAccentDark == dark && lastAccentSeed == seed)
            return lastAccentPalette;
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float hue = hsv[0];
        float saturation = Math.max(.48f, Math.min(.92f, hsv[1]));
        int background, surface, surfaceAlt, text, muted, outline;
        int primary, onPrimary, container, onContainer;
        if (dark) {
            background = Color.HSVToColor(new float[]{hue, .18f, .09f});
            surface = Color.HSVToColor(new float[]{hue, .20f, .14f});
            surfaceAlt = Color.HSVToColor(new float[]{hue, .23f, .19f});
            text = 0xFFF0F1F5;
            muted = 0xFFBEC3CD;
            outline = Color.HSVToColor(new float[]{hue, .18f, .36f});
            primary = Color.HSVToColor(new float[]{hue, saturation, .91f});
            onPrimary = Color.luminance(primary) > .179f ? Color.BLACK : Color.WHITE;
            container = Color.HSVToColor(new float[]{hue, saturation * .72f, .38f});
            onContainer = Color.HSVToColor(new float[]{hue, .10f, .97f});
        } else {
            background = Color.HSVToColor(new float[]{hue, .08f, .985f});
            surface = Color.WHITE;
            surfaceAlt = Color.HSVToColor(new float[]{hue, .045f, .96f});
            text = Color.HSVToColor(new float[]{hue, .28f, .14f});
            muted = Color.HSVToColor(new float[]{hue, .24f, .43f});
            outline = Color.HSVToColor(new float[]{hue, .10f, .80f});
            primary = Color.HSVToColor(new float[]{hue, saturation, .48f});
            onPrimary = Color.luminance(primary) > .179f ? Color.BLACK : Color.WHITE;
            container = Color.HSVToColor(new float[]{hue, saturation * .20f, .98f});
            onContainer = Color.HSVToColor(new float[]{hue, saturation * .55f, .23f});
        }
        lastAccentDark = dark; lastAccentSeed = seed;
        lastAccentPalette = new Palette(background, surface, surfaceAlt, text, muted, outline,
                primary, onPrimary, container, onContainer);
        return lastAccentPalette;
    }

    /** Destructive labels ("删除") read as red; the shade follows the theme's light/dark mode. */
    static int danger(Context context) {
        return ThemeStore.dark(context) ? 0xFFFFB4AB : 0xFFB3261E;
    }

    public static void page(Activity activity, LinearLayout root) {
        Palette colors = colors(activity);
        root.setBackgroundColor(colors.background);
        activity.getWindow().setStatusBarColor(colors.background);
        activity.getWindow().setNavigationBarColor(colors.background);
        activity.getWindow().getDecorView().setSystemUiVisibility(ThemeStore.dark(activity) ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setFitsSystemWindows(true);
    }

    public static void title(TextView view) {
        view.setTextColor(colors(view.getContext()).text);
        view.setTypeface(null, Typeface.BOLD);
        view.setLetterSpacing(-0.025f);
    }
    public static void muted(TextView view) { view.setTextColor(colors(view.getContext()).muted); }

    static void input(EditText view) {
        Palette colors = colors(view.getContext());
        view.setTextColor(colors.text);
        view.setTextSize(16);
        view.setHintTextColor(colors.muted);
        view.setBackgroundTintList(null);
        UiSurfaces.acrylicSurface(view, RADIUS_FIELD);
        view.setElevation(0f);
        view.setPadding(dp(view, 16), dp(view, 14), dp(view, 16), dp(view, 14));
        rememberChildSurface(view, 0, false, 0, false);
    }

    /**
     * A dropdown trigger that reads as one of the form's fields, so a filled-in value lines up with
     * the fields around it. The marker is a compound drawable, which keeps it pinned to the right
     * edge however long the selected label is.
     */
    static void fieldTrigger(TextView view) {
        fieldTrigger(view, false);
    }
    static void fieldTrigger(TextView view, boolean multiline) {
        Palette colors = colors(view.getContext());
        view.setTextColor(colors.text);
        view.setTextSize(16);
        view.setTypeface(null, Typeface.NORMAL);
        view.setLetterSpacing(0f);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setSingleLine(!multiline);
        view.setMinHeight(dp(view, 52));
        view.setPadding(dp(view, 16), dp(view, 14), dp(view, 16), dp(view, 14));
        view.setCompoundDrawablePadding(dp(view, 8));
        view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down, 0);
        view.setCompoundDrawableTintList(ColorStateList.valueOf(colors.muted));
        view.setBackgroundTintList(null);
        acrylicChoice(view, false, RADIUS_FIELD, false);
        rememberChildSurface(view, 1, false, 0, false);
    }

    public static void button(Button view, boolean primary) {
        Palette colors = colors(view.getContext());
        view.setAllCaps(false);
        view.setTextColor(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled}, new int[]{}
        }, new int[]{alpha(colors.muted, 115), primary ? colors.primary : colors.text}));
        view.setTextSize(15);
        view.setTypeface(null, Typeface.BOLD);
        view.setBackgroundTintList(null);
        acrylicChoice(view, primary, RADIUS_FIELD, false);
        view.setPadding(dp(view, 18), dp(view, 10), dp(view, 18), dp(view, 10));
        view.setMinimumHeight(dp(view, 52));
        PressFeedback.apply(view);
        rememberChildSurface(view, 2, primary, RADIUS_FIELD, primary);
    }

    static void toggle(android.widget.CompoundButton view) {
        applyToggleColors(view);
        view.setMinHeight(dp(view, 48));
        registerDynamicSurface(view, () -> applyToggleColors(view));
    }

    private static void applyToggleColors(android.widget.CompoundButton view) {
        Palette palette = colors(view.getContext());
        int[][] states = {new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_checked}, new int[]{}};
        ColorStateList tint = new ColorStateList(states,
                new int[]{alpha(palette.muted, 100), palette.primary, palette.muted});
        view.setTextColor(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{alpha(palette.muted, 115), palette.text}));
        if (view instanceof android.widget.Switch toggle) {
            toggle.setThumbTintList(tint);
            toggle.setTrackTintList(new ColorStateList(states, new int[]{
                    alpha(palette.muted, 30), alpha(palette.primary, 70), alpha(palette.muted, 45)}));
        } else view.setButtonTintList(tint);
    }

    /**
     * The single record entry: the shared plus glyph on the app's translucent acrylic surface,
     * tinted with the theme colour. Phone (floating over the dock) and wide (corner) both build it
     * here, so the two layouts can never drift into different-looking controls.
     */
    public static ImageButton recordEntry(Activity activity, Runnable action) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(R.drawable.ic_add);
        button.setImageTintList(ColorStateList.valueOf(colors(activity).primary));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(button, 14), dp(button, 14), dp(button, 14), dp(button, 14));
        button.setBackgroundColor(Color.TRANSPARENT);
        glassPill(button);
        pressable(button);
        button.setContentDescription("记录一件事");
        button.setOnClickListener(view -> action.run());
        PressFeedback.apply(button, 1.20f);
        return button;
    }

    public static void card(View view) {
        UiSurfaces.acrylicSurface(view, RADIUS_CARD);
        view.setElevation(dp(view, 2));
    }

    /**
     * The shared empty state: a card that continues the list from the toolbar instead of centring
     * in the viewport, with one icon, one line of copy and an optional recovery action.
     */
    public static LinearLayout emptyState(LinearLayout parent, int iconRes, String label,
            String actionLabel, Runnable action) {
        Palette colors = colors(parent.getContext());
        LinearLayout box = new LinearLayout(parent.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        card(box);
        cardInset(box);
        ImageView glyph = new ImageView(parent.getContext());
        glyph.setImageResource(iconRes);
        glyph.setImageTintList(ColorStateList.valueOf(colors.muted));
        box.addView(glyph, new LinearLayout.LayoutParams(dp(box, 24), dp(box, 24)));
        TextView text = new TextView(parent.getContext());
        text.setText(label);
        text.setTextColor(colors.text);
        text.setTextSize(16);
        text.setLineSpacing(dp(text, 2), 1f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
        textParams.topMargin = dp(text, 10);
        box.addView(text, textParams);
        if (actionLabel != null && action != null) {
            TextView trigger = new TextView(parent.getContext());
            trigger.setText(actionLabel);
            trigger.setTextColor(colors.primary);
            trigger.setTextSize(13);
            trigger.setTypeface(null, Typeface.BOLD);
            trigger.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            trigger.setMinimumHeight(dp(trigger, 48));
            pressable(trigger);
            trigger.setOnClickListener(view -> action.run());
            box.addView(trigger, new LinearLayout.LayoutParams(-1, -2));
        }
        addSpaced(parent, box, RECORD_GAP_DP / 2, RECORD_GAP_DP / 2);
        return box;
    }

    /** The shared content-card inset: 16dp horizontal, 12dp vertical. */
    public static void cardInset(View view) {
        float density = view.getResources().getDisplayMetrics().density;
        int horizontal = Math.round(CARD_INSET_H_DP * density);
        int vertical = Math.round(CARD_INSET_V_DP * density);
        view.setPadding(horizontal, vertical, horizontal, vertical);
    }

    /** Translucent surface over the softly colored backdrop; text stays opaque. */
    public static void glass(View view) {
        UiSurfaces.acrylicSurface(view, RADIUS_PANEL);
    }

    /** Compact acrylic surface for controls and transient in-app messages. */
    public static void glassPill(View view) {
        UiSurfaces.acrylicSurface(view, CornerScale.ROUND);
    }

    static void dockGlass(View view) {
        UiSurfaces.acrylicSurface(view, RADIUS_DOCK);
    }

    public static int uncertaintyIcon(int level) {
        if (level == com.donglan.chrona.data.EventCandidate.INFERRED) return R.drawable.ic_help_outline;
        if (level == com.donglan.chrona.data.EventCandidate.DOUBTFUL) return R.drawable.ic_warning_outline;
        return R.drawable.ic_check;
    }

    public static String uncertaintyLabel(int level) {
        if (level == com.donglan.chrona.data.EventCandidate.INFERRED) return "推定";
        if (level == com.donglan.chrona.data.EventCandidate.DOUBTFUL) return "高存疑";
        return "明确";
    }

    /** Selectable action labels keep native selection while exposing presses to our shared feedback. */
    static TextView selectableActionText(Context context) {
        TextView text = new TextView(context) {
            @Override protected int[] onCreateDrawableState(int extraSpace) {
                int[] state = super.onCreateDrawableState(extraSpace + 1);
                if (isTextSelectable() && isPressed())
                    mergeDrawableStates(state, new int[]{android.R.attr.state_pressed});
                return state;
            }
        };
        text.setTextIsSelectable(true);
        pressable(text);
        return text;
    }
    static void pressable(View view) {
        PressFeedback.apply(view);
        UiSurfaces.installRipple(view);
    }

    /** Selectable surface: chips use the pill radius, dialog rows the field radius. */
    static void choice(View view, boolean selected, int radius) {
        acrylicChoice(view, selected, radius, true);
    }

    /** Theme-mixed acrylic selection surface; set outline=false for borderless navigation chips. */
    public static void acrylicChoice(View view, boolean selected, int radius, boolean outline) {
        UiSurfaces.acrylicChoice(view, selected, radius, outline);
        rememberChildSurface(view, 3, false, radius, selected, outline);
    }

    static void pill(View view, boolean selected) {
        choice(view, selected, RADIUS_PILL);
    }

    /** The single marker used by every list of choices: a trailing check on the selected row. */
    static void marked(TextView label, boolean selected) {
        Drawable check = selected ? label.getContext().getDrawable(R.drawable.ic_check).mutate() : null;
        if (check != null) { check.setTint(label.getCurrentTextColor()); check.setBounds(0, 0, dp(label, 20), dp(label, 20)); }
        label.setCompoundDrawablesRelative(null, null, check, null);
        label.setCompoundDrawablePadding(dp(label, 8));
    }

    public static void addSpaced(LinearLayout parent, View view, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(view, top), 0, dp(view, bottom));
        parent.addView(view, params);
    }

    /** Keep controls clear of Android 15+ system bars while the backdrop draws behind them. */
    public static void applyInsets(View stage, View safeContent) {
        applyInsets(stage, safeContent, null);
    }

    static void applyInsets(View stage, View safeContent, View floating) {
        if (Build.VERSION.SDK_INT < 35) return;
        enableEdgeToEdge(stage);
        safeContent.setFitsSystemWindows(false);
        int left = safeContent.getPaddingLeft();
        int top = safeContent.getPaddingTop();
        int right = safeContent.getPaddingRight();
        int bottom = safeContent.getPaddingBottom();
        int floatBottom = floating == null ? 0
                : ((FrameLayout.LayoutParams) floating.getLayoutParams()).bottomMargin;
        int floatLeft=floating==null?0:((FrameLayout.LayoutParams)floating.getLayoutParams()).leftMargin;
        int floatRight=floating==null?0:((FrameLayout.LayoutParams)floating.getLayoutParams()).rightMargin;
        stage.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            int safeBottom = Math.max(bars.bottom, keyboard);
            safeContent.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + safeBottom);
            if (floating != null) {
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) floating.getLayoutParams();
                params.bottomMargin = floatBottom + bars.bottom;
                params.leftMargin=floatLeft+bars.left;
                params.rightMargin=floatRight+bars.right;
                floating.setLayoutParams(params);
            }
            return insets;
        });
        stage.requestApplyInsets();
    }

    /** Insets outside the viewport so focus scrolling uses the actual visible height above IME. */
    static void applyScrollableInsets(View stage, View scrollContent) {
        if (Build.VERSION.SDK_INT < 35) return;
        enableEdgeToEdge(stage);
        ScrollView viewport = scrollContent.getParent() instanceof ScrollView scrollView
                ? scrollView : null;
        View insetTarget = viewport!=null&&viewport.getParent() instanceof View parent?parent:scrollContent;
        insetTarget.setFitsSystemWindows(false);
        scrollContent.setFitsSystemWindows(false);
        int left = insetTarget.getPaddingLeft();
        int top = insetTarget.getPaddingTop();
        int right = insetTarget.getPaddingRight();
        int bottom = insetTarget.getPaddingBottom();
        if (viewport != null) viewport.setClipToPadding(true);
        scrollContent.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            insetTarget.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + Math.max(bars.bottom, keyboard));
            return insets;
        });
        stage.requestApplyInsets();
    }

    private static void enableEdgeToEdge(View stage) {
        if (stage.getContext() instanceof Activity activity) {
            Window window = activity.getWindow();
            if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
            // Let the full-screen GlassBackdropView paint beneath the status bar. The safe
            // content below still receives the system inset, so controls keep their positions.
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    static int alpha(int color, int value) {
        return Color.argb(value, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void rememberChildSurface(View view, int kind, boolean primary, int radius,
            boolean selected) {
        rememberChildSurface(view, kind, primary, radius, selected, true);
    }

    private static void rememberChildSurface(View view, int kind, boolean primary, int radius,
            boolean selected, boolean outline) {
        synchronized (CHILD_SURFACES) {
            java.util.Iterator<ChildSurfaceRecord> iterator = CHILD_SURFACES.iterator();
            while (iterator.hasNext()) {
                ChildSurfaceRecord record = iterator.next();
                View existing = record.view.get();
                if (existing == null) iterator.remove();
                else if (existing == view) {
                    record.kind = kind;
                    record.primary = primary;
                    record.radius = radius;
                    record.selected = selected;
                    record.outline = outline;
                    record.customRefresh = null;
                    return;
                }
            }
            ChildSurfaceRecord record = new ChildSurfaceRecord(view, kind, primary, radius,
                    selected);
            record.outline = outline;
            CHILD_SURFACES.add(record);
        }
    }

    /** Repaints already-created controls and acrylic surfaces while appearance sliders move. */
    static void refreshAppearancePreview(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        UiSurfaces.invalidateAcrylicSurfaces(content);
        synchronized (CHILD_SURFACES) {
            java.util.Iterator<ChildSurfaceRecord> iterator = CHILD_SURFACES.iterator();
            while (iterator.hasNext()) {
                ChildSurfaceRecord record = iterator.next();
                View view = record.view.get();
                if (view == null) {
                    iterator.remove();
                } else if (view.isAttachedToWindow() && isDescendantOf(view, content)) {
                    switch (record.kind) {
                        case 0 -> input((EditText) view);
                        case 1 -> fieldTrigger((TextView) view);
                        case 2 -> button((Button) view, record.primary);
                        case 3 -> acrylicChoice(view, record.selected, record.radius,
                                record.outline);
                        case 4 -> { if (record.customRefresh != null) record.customRefresh.run(); }
                        default -> { }
                    }
                }
            }
        }
    }

    /** Registers custom themed controls so appearance sliders can repaint them in place. */
    public static void registerDynamicSurface(View view, Runnable refresh) {
        java.lang.ref.WeakReference<View> weakView = new java.lang.ref.WeakReference<>(view);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) { }
            @Override public void onViewDetachedFromWindow(View detached) {
                synchronized (CHILD_SURFACES) {
                    java.util.Iterator<ChildSurfaceRecord> iterator = CHILD_SURFACES.iterator();
                    while (iterator.hasNext()) {
                        ChildSurfaceRecord record = iterator.next();
                        if (record.view.get() == weakView.get()) iterator.remove();
                    }
                }
            }
        });
        synchronized (CHILD_SURFACES) {
            java.util.Iterator<ChildSurfaceRecord> iterator = CHILD_SURFACES.iterator();
            while (iterator.hasNext()) {
                ChildSurfaceRecord record = iterator.next();
                View existing = record.view.get();
                if (existing == null) iterator.remove();
                else if (existing == view) {
                    iterator.remove();
                    break;
                }
            }
            CHILD_SURFACES.add(new ChildSurfaceRecord(view, 4, false, 0, false, refresh));
        }
    }

    private static boolean isDescendantOf(View view, View root) {
        View current = view;
        while (current != null) {
            if (current == root) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View parentView ? parentView : null;
        }
        return false;
    }
    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
