package com.donglan.chrona;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.WeakHashMap;

/** Semantic colors, shapes and motion for the native View interface. */
public final class UiStyle {
    private static final long DIALOG_BACKGROUND_SCALE_IN_DURATION_MS = 180L * 2L;
    private static final WeakHashMap<Activity, DialogScaleState> DIALOG_SCALES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> DIALOG_ACRYLIC_ROOTS = new WeakHashMap<>();
    private static final WeakHashMap<View, AcrylicScrollWatcher> ACRYLIC_SCROLL_WATCHERS =
            new WeakHashMap<>();
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

    /** Invalidates visible translucent surfaces when a parent scroll changes their backdrop. */
    private static final class AcrylicScrollWatcher
            implements ViewTreeObserver.OnScrollChangedListener {
        private final java.lang.ref.WeakReference<ViewTreeObserver> observer;
        private final java.util.ArrayList<java.lang.ref.WeakReference<View>> hosts =
                new java.util.ArrayList<>();
        private final android.graphics.Rect visible = new android.graphics.Rect();
        AcrylicScrollWatcher(ViewTreeObserver observer) {
            this.observer = new java.lang.ref.WeakReference<>(observer);
        }
        void addHost(View host) {
            java.util.Iterator<java.lang.ref.WeakReference<View>> iterator = hosts.iterator();
            while (iterator.hasNext()) {
                View existing = iterator.next().get();
                if (existing == null) iterator.remove();
                else if (existing == host) return;
            }
            hosts.add(new java.lang.ref.WeakReference<>(host));
        }
        @Override public void onScrollChanged() {
            java.util.Iterator<java.lang.ref.WeakReference<View>> iterator = hosts.iterator();
            while (iterator.hasNext()) {
                View host = iterator.next().get();
                if (host == null) {
                    iterator.remove();
                } else if (host.isAttachedToWindow()
                        && host.getGlobalVisibleRect(visible)) {
                    host.invalidate();
                }
            }
        }
    }

    private static final class DialogScaleState {
        final View content;
        final float scaleX, scaleY;
        final Object originalRenderEffect;
        final java.util.List<View> dialogRoots = new java.util.ArrayList<>();
        int count;
        DialogScaleState(View content, Object originalRenderEffect) {
            this.content = content;
            scaleX = content.getScaleX();
            scaleY = content.getScaleY();
            this.originalRenderEffect = originalRenderEffect;
            count = 1;
        }
    }
    /** One radius scale for the whole app: fields and buttons, cards, sheets, and chips. */
    static final int RADIUS_FIELD = 18;
    static final int RADIUS_CARD = 22;
    static final int RADIUS_PANEL = 28;
    static final int RADIUS_PILL = 24;
    /** Filter chips: a soft rectangle, deliberately much less round than a pill. */
    static final int RADIUS_CHIP = 12;
    /** Choice sheets carry short labels only, so they stay well inside the default sheet width. */
    private static final int CHOICE_SHEET_WIDTH_DP = 300;
    /** Length of the in-place cross-fade used when a screen rebuilds itself. */
    static final long SWAP_MILLIS = 220L;
    /** Marks the temporary snapshot view a swap leaves over the container while it fades. */
    private static final Object SWAP_GHOST = new Object();

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

    private static void watchAcrylicScroll(View host) {
        View root = host.getRootView();
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return;
        synchronized (ACRYLIC_SCROLL_WATCHERS) {
            AcrylicScrollWatcher watcher = ACRYLIC_SCROLL_WATCHERS.get(root);
            if (watcher == null || watcher.observer.get() != observer) {
                if (watcher != null && watcher.observer.get() != null
                        && watcher.observer.get().isAlive()) {
                    watcher.observer.get().removeOnScrollChangedListener(watcher);
                }
                watcher = new AcrylicScrollWatcher(observer);
                ACRYLIC_SCROLL_WATCHERS.put(root, watcher);
                observer.addOnScrollChangedListener(watcher);
            }
            watcher.addHost(host);
        }
    }

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

    private static Palette accentPalette(boolean dark, int seed) {
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
        return new Palette(background, surface, surfaceAlt, text, muted, outline,
                primary, onPrimary, container, onContainer);
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
        acrylicSurface(view, RADIUS_FIELD);
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
        Palette colors = colors(view.getContext());
        view.setTextColor(colors.text);
        view.setTextSize(16);
        view.setTypeface(null, Typeface.NORMAL);
        view.setLetterSpacing(0f);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
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
        StateListAnimator press = new StateListAnimator();
        press.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed},
                scale(view, 0.97f, 90));
        press.addState(new int[]{}, scale(view, 1f, 160));
        view.setStateListAnimator(press);
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

    static void timeDialog(Activity activity, String title, int minutes, IntConsumer onSave) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        GlassDateTimePickerView picker = new GlassDateTimePickerView(activity,
                java.time.LocalDate.now().atTime(minutes / 60, minutes % 60));
        picker.showTime(true);
        panel.addView(picker, new LinearLayout.LayoutParams(-1, dp(panel, 220)));
        LinearLayout actions = new LinearLayout(activity);
        Button cancel = new Button(activity);
        cancel.setText("取消");
        button(cancel, false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(panel, 52), 1));
        Button save = new Button(activity);
        save.setText("保存");
        button(save, true);
        save.setOnClickListener(view -> {
            java.time.LocalDateTime value = picker.value();
            dialog.dismiss();
            onSave.accept(value.getHour() * 60 + value.getMinute());
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(panel, 52), 1);
        params.leftMargin = dp(panel, 8);
        actions.addView(save, params);
        addSpaced(panel, actions, 12, 0);
        showDialog(dialog, panel);
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
        return button;
    }

    public static void card(View view) {
        acrylicSurface(view, RADIUS_CARD);
        view.setElevation(dp(view, 2));
    }

    /** Translucent surface over the softly colored backdrop; text stays opaque. */
    public static void glass(View view) {
        acrylicSurface(view, RADIUS_PANEL);
    }

    /** Pill-shaped acrylic surface for transient in-app messages. */
    public static void glassPill(View view) {
        acrylicSurface(view, 999);
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

    private static void acrylicSurface(View view, int radius) {
        Palette colors = colors(view.getContext());
        boolean dark = ThemeStore.dark(view.getContext());
        GradientDrawable blurred = surfaceDrawable(view, colors, radius,
                ThemeStore.surfaceMix(view.getContext()) * 255 / 100,
                ThemeStore.surfaceMix(view.getContext()) * 225 / 100);
        GradientDrawable fallback = surfaceDrawable(view, colors, radius,
                dark ? 246 : 248, dark ? 238 : 242);
        view.setBackground(new AcrylicSurfaceDrawable(view, blurred, fallback, radius,
                false, true));
        view.setElevation(dp(view, radius == RADIUS_CARD ? 2 : 8));
    }

    private static GradientDrawable surfaceDrawable(View view, Palette colors, int radius,
            int firstAlpha, int secondAlpha) {
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(colors.surface, firstAlpha), alpha(colors.surfaceAlt, secondAlpha)});
        boolean dark = ThemeStore.dark(view.getContext());
        surface.setCornerRadius(dp(view, radius));
        surface.setStroke(dp(view, 1), alpha(dark ? colors.outline : Color.WHITE,
                dark ? 210 : 225));
        return surface;
    }

    /** Blurs only the wallpaper sampled behind this surface; labels remain separate and sharp. */
    private static final class AcrylicSurfaceDrawable extends Drawable {
        private final GradientDrawable blurredSurface;
        private final GradientDrawable fallbackSurface;
        private final int radius;
        private GlassBackdropView backdrop;
        private int lastMix = -1;
        private int lastSurface, lastAlt, lastOutline;
        private int lastText;
        private boolean lastDark;
        private boolean lastSelected;
        private int drawableAlpha = 255;
        private final View host;
        private boolean selected;
        private boolean outline;

        AcrylicSurfaceDrawable(View host, GradientDrawable blurredSurface,
                GradientDrawable fallbackSurface, int radius, boolean selected, boolean outline) {
            this.host = host;
            this.blurredSurface = blurredSurface;
            this.fallbackSurface = fallbackSurface;
            this.radius = radius;
            this.selected = selected;
            this.outline = outline;
            applyAcrylicStroke(host, selected, outline, blurredSurface, fallbackSurface);
        }

        boolean belongsTo(View candidate, int candidateRadius) {
            return host == candidate && radius == candidateRadius;
        }

        void updateSelection(boolean selected, boolean outline) {
            if (this.selected == selected && this.outline == outline) return;
            this.selected = selected;
            this.outline = outline;
            applyAcrylicStroke(host, selected, outline, blurredSurface, fallbackSurface);
            invalidateSelf();
        }

        @Override public void draw(android.graphics.Canvas canvas) {
            boolean backdropReady = false;
            boolean acrylic = ThemeStore.acrylicEnabled(host.getContext());
            if (acrylic && (insideAcrylicSurface(host) || insideAcrylicDialog(host))) {
                // The ancestor already drew a sampled surface or owns a blurred dialog window.
                backdropReady = true;
            } else if (acrylic) {
                watchAcrylicScroll(host);
                if (backdrop == null) {
                    backdrop = GlassBackdropView.findFor(host);
                }
                backdropReady = backdrop != null && backdrop.drawBlurredWallpaper(canvas, host,
                        getBounds(), dp(host, radius), colors(host.getContext()));
            }
            updateBlurredSurface(host);
            // A translucent mix is only safe when the matching blurred wallpaper was actually
            // drawn underneath. Until then, use the opaque fallback; cache completion invalidates
            // acrylic hosts so they switch to the mixed surface on their next frame.
            blurredSurface.setBounds(getBounds());
            fallbackSurface.setBounds(getBounds());
            if (!backdropReady) {
                fallbackSurface.setAlpha(drawableAlpha);
                fallbackSurface.draw(canvas);
                return;
            }
            fallbackSurface.setAlpha(drawableAlpha);
            blurredSurface.draw(canvas);
        }

        private void updateBlurredSurface(View host) {
            int mix = ThemeStore.surfaceMix(host.getContext());
            Palette palette = colors(host.getContext());
            boolean dark = ThemeStore.dark(host.getContext());
            int outlineColor = selected ? palette.primary
                    : dark ? palette.outline : Color.WHITE;
            if (lastMix == mix && lastSurface == palette.surface && lastAlt == palette.surfaceAlt
                    && lastOutline == outlineColor && lastText == palette.text && lastDark == dark
                    && lastSelected == selected)
                return;
            lastMix = mix;
            lastSurface = palette.surface;
            lastAlt = palette.surfaceAlt;
            lastOutline = outlineColor;
            lastText = palette.text;
            lastDark = dark;
            lastSelected = selected;
            // A gentle theme-aware RGB shift makes the wallpaper blur read through the tint:
            // dark surfaces lift toward the text color; light surfaces settle toward black.
            float toneShift = dark ? .20f : .12f;
            int toneTarget = dark ? palette.text : Color.BLACK;
            int mixedSurface = blendRgb(palette.surface, toneTarget, toneShift);
            int mixedAlt = blendRgb(palette.surfaceAlt, toneTarget, toneShift);
            if (selected) {
                mixedSurface = blendRgb(mixedSurface, palette.primaryContainer, .34f);
                mixedAlt = blendRgb(mixedAlt, palette.primaryContainer, .34f);
            }
            blurredSurface.setColors(new int[]{alpha(mixedSurface, mix * 255 / 100),
                    alpha(mixedAlt, mix * 225 / 100)});
            applyAcrylicStroke(host, selected, outline, blurredSurface, fallbackSurface);
        }

        @Override public void setAlpha(int alpha) {
            drawableAlpha = alpha;
            blurredSurface.setAlpha(alpha);
            fallbackSurface.setAlpha(alpha);
        }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            blurredSurface.setColorFilter(filter);
            fallbackSurface.setColorFilter(filter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void applyAcrylicStroke(View host, boolean selected, boolean outline,
            GradientDrawable blurredSurface, GradientDrawable fallbackSurface) {
        Palette palette = colors(host.getContext());
        boolean dark = ThemeStore.dark(host.getContext());
        int width = outline ? dp(host, selected ? 2 : 1) : 0;
        int strokeColor = selected ? palette.primary : dark ? palette.outline : Color.WHITE;
        int strokeAlpha = selected ? 240 : dark ? 210 : 225;
        int color = outline ? alpha(strokeColor, strokeAlpha) : Color.TRANSPARENT;
        blurredSurface.setStroke(width, color);
        fallbackSurface.setStroke(width, color);
    }

    private static boolean insideAcrylicSurface(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View ancestor) {
            if (ancestor.getBackground() instanceof AcrylicSurfaceDrawable) return true;
            parent = ancestor.getParent();
        }
        return false;
    }

    private static boolean insideAcrylicDialog(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View ancestor) {
            synchronized (DIALOG_ACRYLIC_ROOTS) {
                if (DIALOG_ACRYLIC_ROOTS.containsKey(ancestor)) return true;
            }
            parent = ancestor.getParent();
        }
        return false;
    }

    static void invalidateAcrylicSurfaces(View root) {
        invalidateAcrylicSurfaces(root, new android.graphics.Rect());
    }

    /** Collect once for a page gesture; ordinary text and icons need no per-frame traversal. */
    static void collectAcrylicSurfaces(View root, java.util.List<View> surfaces) {
        if (root.getBackground() instanceof AcrylicSurfaceDrawable) surfaces.add(root);
        if (root instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++)
                collectAcrylicSurfaces(group.getChildAt(i), surfaces);
        }
    }

    private static void invalidateAcrylicSurfaces(View root, android.graphics.Rect visible) {
        if (!root.getGlobalVisibleRect(visible)) return;
        if (root.getBackground() instanceof AcrylicSurfaceDrawable) root.invalidate();
        if (root instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++)
                invalidateAcrylicSurfaces(group.getChildAt(i), visible);
        }
    }

    static void pressable(View view) {
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(
                alpha(colors(view.getContext()).primary, 32)), null,
                shape(view, Color.WHITE, RADIUS_CARD, Color.TRANSPARENT)));
    }

    /** Selectable surface: chips use the pill radius, dialog rows the field radius. */
    static void choice(View view, boolean selected, int radius) {
        acrylicChoice(view, selected, radius, true);
    }

    /** Theme-mixed acrylic selection surface; set outline=false for borderless navigation chips. */
    public static void acrylicChoice(View view, boolean selected, int radius, boolean outline) {
        Palette colors = colors(view.getContext());
        Drawable existing = view.getBackground();
        if (existing instanceof AcrylicSurfaceDrawable acrylic
                && acrylic.belongsTo(view, radius)) {
            acrylic.updateSelection(selected, outline);
        } else {
            boolean dark = ThemeStore.dark(view.getContext());
            GradientDrawable blurred = surfaceDrawable(view, colors, radius,
                    ThemeStore.surfaceMix(view.getContext()) * 255 / 100,
                    ThemeStore.surfaceMix(view.getContext()) * 225 / 100);
            GradientDrawable fallback = surfaceDrawable(view, colors, radius,
                    dark ? 246 : 248, dark ? 238 : 242);
            view.setBackground(new AcrylicSurfaceDrawable(view, blurred, fallback, radius,
                    selected, outline));
        }
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(alpha(colors.primary, 34)),
                null, shape(view, Color.WHITE, radius, Color.TRANSPARENT)));
        rememberChildSurface(view, 3, false, radius, selected, outline);
    }

    static void pill(View view, boolean selected) {
        choice(view, selected, RADIUS_PILL);
    }

    /** The single marker used by every list of choices: a trailing check on the selected row. */
    static String marked(String label, boolean selected) {
        return selected ? label + "   ✓" : label;
    }

    static void enter(View view, int index) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 8));
        view.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(index, 4) * 18L)
                .setDuration(UiMotion.ENTER).setInterpolator(UiMotion.SETTLE).start();
    }

    /**
     * Staggers a card list into view. Only for a screen that is being opened: rebuilding a screen
     * for another reason (theme change, returning from another page) must not look like a reload.
     */
    public static void enterChildren(LinearLayout parent) {
        for (int i = 0; i < parent.getChildCount(); i++) enter(parent.getChildAt(i), i);
    }

    /**
     * Rebuilds a container's contents as a cross-fade: a snapshot of the outgoing content fades
     * out while the rebuilt content fades in. The earlier version dipped the container towards
     * transparent first and only rebuilt afterwards, so the new content appeared once the old one
     * was already gone.
     *
     * <p>The snapshot is placed in the container's own parent, at the container's layout position,
     * so the parent's scroll offset applies to it exactly as it does to the rebuilt content.
     * Pinning it to window coordinates instead left it behind whenever the rebuild scrolled the
     * page (a section switch resets the scroll), which only lined up at zero offset.
     */
    public static void swap(View container, Runnable rebuild) {
        swap(container, container, rebuild);
    }

    /**
     * Cross-fades a rebuild where the outgoing look is taken from {@code snapshotOf} but
     * {@code container} is the view that fades back in. Use this when the rebuilt content sits on
     * a transparent background — for example an activity page over the shared backdrop — so the
     * ghost covers the whole visual, background included.
     */
    public static void swap(View snapshotOf, View container, Runnable rebuild) {
        container.animate().cancel();
        container.setAlpha(1f);
        if (snapshotOf.getParent() instanceof FrameLayout previousHost
                && hostsSeveralChildren(previousHost)) dropSwapGhosts(previousHost);
        if (!ValueAnimator.areAnimatorsEnabled()) {
            rebuild.run();
            return;
        }
        if (snapshotOf.getWidth() <= 0
                || !(snapshotOf.getParent() instanceof FrameLayout host)
                || !hostsSeveralChildren(host)) {
            rebuild.run();
            return;
        }
        dropSwapGhosts(host);
        Bitmap outgoing = snapshot(snapshotOf);
        if (outgoing == null) {
            rebuild.run();
            return;
        }
        ImageView ghost = new ImageView(snapshotOf.getContext());
        ghost.setImageBitmap(outgoing);
        ghost.setScaleType(ImageView.ScaleType.FIT_XY);
        ghost.setTag(SWAP_GHOST);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                snapshotOf.getWidth(), snapshotOf.getHeight());
        params.leftMargin = snapshotOf.getLeft();
        params.topMargin = snapshotOf.getTop();
        // Added after the snapshot source so it draws on top of it.
        host.addView(ghost, params);

        rebuild.run();
        container.animate().cancel();
        container.setAlpha(0f);
        container.animate().alpha(1f).setStartDelay(0).setDuration(SWAP_MILLIS)
                .setInterpolator(UiMotion.SETTLE)
                .start();
        ghost.animate().alpha(0f).setDuration(SWAP_MILLIS)
                .setInterpolator(UiMotion.SETTLE)
                .withEndAction(() -> host.removeView(ghost)).start();
    }

    /**
     * The ghost is a second child, so the host has to accept one. Scrolling containers extend
     * FrameLayout but raise {@code IllegalStateException} on a second child, which crashed the
     * section switch while its body still lived directly in a ScrollView.
     */
    private static boolean hostsSeveralChildren(ViewGroup host) {
        return !(host instanceof ScrollView)
                && !(host instanceof android.widget.HorizontalScrollView);
    }

    /** Half-scale copy of what the container shows now; null when it has nothing to copy yet. */
    private static Bitmap snapshot(View container) {
        int width = container.getWidth();
        int height = container.getHeight();
        if (width <= 0 || height <= 0) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width / 2), Math.max(1, height / 2),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(0.5f, 0.5f);
            container.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError error) {
            return null;
        }
    }

    /** A second swap replaces the previous cross-fade instead of stacking ghosts. */
    private static void dropSwapGhosts(ViewGroup host) {
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View child = host.getChildAt(i);
            if (child.getTag() != SWAP_GHOST) continue;
            child.animate().cancel();
            host.removeView(child);
        }
    }

    public static void pop(View view) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.setScaleX(0.94f);
        view.setScaleY(0.94f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(230).start();
    }

    /** A quiet breathing cue for something that is still running; stops itself when detached. */
    public static void pulse(View view) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.6f, 1f);
        pulse.setDuration(1600);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) { }

            @Override public void onViewDetachedFromWindow(View detached) {
                pulse.cancel();
            }
        });
    }

    public static void addSpaced(LinearLayout parent, View view, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(view, top), 0, dp(view, bottom));
        parent.addView(view, params);
    }

    public static void back(Activity activity, LinearLayout parent) {
        TextView back = new TextView(activity);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(colors(activity).primary);
        back.setTypeface(null, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        glass(back);
        pressable(back);
        back.setOnClickListener(view -> activity.finish());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(back, 48), dp(back, 48));
        params.setMargins(0, 0, 0, dp(back, 8));
        parent.addView(back, params);
    }

    /** Same affordance with a different destination, for a screen that is also an app entry point. */
    public static void backTo(Activity activity, LinearLayout parent, String label, Runnable action) {
        TextView back = new TextView(activity);
        back.setText(label);
        back.setTextSize(15);
        back.setTextColor(colors(activity).primary);
        back.setTypeface(null, Typeface.BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinimumHeight(dp(back, 48));
        back.setContentDescription(label.replace("←", "").trim());
        back.setOnClickListener(view -> action.run());
        addSpaced(parent, back, 0, 8);
    }

    /** A themed, scrollable choice sheet shared by settings and filters. */
    public static void choiceDialog(Activity activity, String title, String[] options,
            int selected, IntConsumer onChoice) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView item = new TextView(activity);
            item.setText(marked(options[i], i == selected));
            item.setTextSize(16);
            // A short list of short labels reads better as a centred column than as rows of text
            // pinned to the left edge of a full-width sheet.
            item.setGravity(Gravity.CENTER);
            item.setMinHeight(dp(item, 52));
            item.setPadding(dp(item, 14), 0, dp(item, 14), 0);
            item.setTextColor(i == selected ? colors(activity).onPrimaryContainer
                    : colors(activity).text);
            choice(item, i == selected, RADIUS_FIELD);
            item.setOnClickListener(view -> {
                dialog.dismiss();
                onChoice.accept(index);
            });
            addSpaced(choices, item, 2, 5);
        }
        scroll.addView(choices);
        int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * .58f);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(maxHeight, options.length * dp(scroll, 60))));
        // Narrower than the default sheet: these are one-word choices, and the full width left the
        // rows looking like empty bars.
        showDialog(dialog, panel, Math.min(
                (int) (activity.getResources().getDisplayMetrics().widthPixels * .72f),
                dp(panel, CHOICE_SHEET_WIDTH_DP)));
    }

    public static void confirmDialog(Activity activity, String title, String message,
            String positive, Runnable onConfirm) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        TextView explanation = new TextView(activity);
        explanation.setText(message);
        explanation.setTextSize(15);
        muted(explanation);
        addSpaced(panel, explanation, 4, 20);
        LinearLayout actions = new LinearLayout(activity);
        Button cancel = new Button(activity);
        cancel.setText("取消");
        button(cancel, false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(cancel, 52), 1));
        Button confirm = new Button(activity);
        confirm.setText(positive);
        button(confirm, true);
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            onConfirm.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(confirm, 52), 1);
        params.setMargins(dp(confirm, 8), 0, 0, 0);
        actions.addView(confirm, params);
        panel.addView(actions);
        showDialog(dialog, panel);
    }

    static void textEditorDialog(Activity activity, String title, String initialValue,
            String positive, Consumer<String> onSave) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        EditText editor = new EditText(activity);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(4);
        editor.setMaxLines(9);
        editor.setText(initialValue == null ? "" : initialValue);
        editor.setSelection(editor.length());
        input(editor);
        addSpaced(panel, editor, 0, 16);
        LinearLayout actions = new LinearLayout(activity);
        Button cancel = new Button(activity);
        cancel.setText("取消");
        button(cancel, false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(cancel, 52), 1));
        Button save = new Button(activity);
        save.setText(positive);
        button(save, true);
        save.setOnClickListener(view -> {
            String value = editor.getText().toString();
            dialog.dismiss();
            onSave.accept(value);
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(save, 52), 1);
        saveParams.setMargins(dp(save, 8), 0, 0, 0);
        actions.addView(save, saveParams);
        panel.addView(actions);
        showDialog(dialog, panel);
        editor.requestFocus();
    }

    private static Dialog dialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.44f;
            window.setAttributes(params);
        }
        return dialog;
    }

    private static LinearLayout dialogPanel(Activity activity, String title) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(panel, 20), dp(panel, 20), dp(panel, 20), dp(panel, 20));
        glass(panel);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(21);
        title(heading);
        addSpaced(panel, heading, 0, 12);
        return panel;
    }

    private static void showDialog(Dialog dialog, View content) {
        showDialog(dialog, content, Math.min(
                (int) (content.getResources().getDisplayMetrics().widthPixels * .90f),
                dp(content, 460)));
    }

    private static void showDialog(Dialog dialog, View content, int widthPx) {
        dialog.setContentView(content);
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            // Some callers create Dialog directly instead of using dialog(Activity). Clear the
            // framework's rectangular window surface so the rounded panel is the outer edge.
            dialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
            dialogWindow.setWindowAnimations(ValueAnimator.areAnimatorsEnabled()
                    ? R.style.Animation_Chrona_Dialog : 0);
        }
        Activity owner = content.getContext() instanceof Activity activity ? activity : null;
        if (owner != null) {
            beginDialogScale(owner);
            boolean nested = registerDialogLayer(owner, content);
            applyDialogSurface(content, owner, nested);
            boolean dialogAcrylic = Build.VERSION.SDK_INT >= 31
                    && ThemeStore.acrylicEnabled(owner);
            if (dialogAcrylic) synchronized (DIALOG_ACRYLIC_ROOTS) {
                DIALOG_ACRYLIC_ROOTS.put(content, Boolean.TRUE);
            }
            dialog.setOnDismissListener(ignored -> {
                if (dialogAcrylic) synchronized (DIALOG_ACRYLIC_ROOTS) {
                    DIALOG_ACRYLIC_ROOTS.remove(content);
                }
                removeDialogLayer(owner, content);
                endDialogScale(owner);
            });
        }
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(widthPx, -2);
        // The window owns both enter and exit, including outside-tap and system-back dismissals.
        // No delayed dismiss callback can outlive the Activity or block a confirmation action.
    }

    /** Applies the shared backdrop blur and page scale to a custom floating dialog. */
    static void showFloatingDialog(Dialog dialog, View content) {
        showDialog(dialog, content);
    }

    private static void applyDialogSurface(View content, Activity owner, boolean nested) {
        if (Build.VERSION.SDK_INT >= 31 && ThemeStore.acrylicEnabled(owner)) {
            Palette palette = colors(content.getContext());
            boolean dark = ThemeStore.dark(content.getContext());
            content.setBackground(surfaceDrawable(content, palette, RADIUS_PANEL,
                    nested ? 220 : dark ? 112 : 124, nested ? 212 : dark ? 100 : 112));
            content.setElevation(dp(content, 8));
        } else {
            if (nested) content.setBackground(surfaceDrawable(content, colors(owner), RADIUS_PANEL, 244, 240));
            else glass(content);
        }
    }

    /** A child modal obscures the previous dialog window as well as the Activity behind it. */
    private static boolean registerDialogLayer(Activity owner, View root) {
        synchronized (DIALOG_SCALES) {
            DialogScaleState state = DIALOG_SCALES.get(owner);
            if (state == null) return false;
            boolean nested = !state.dialogRoots.isEmpty();
            for (View parent : state.dialogRoots) {
                parent.animate().cancel();
                parent.setScaleX(1f);
                parent.setScaleY(1f);
                parent.setAlpha(.4f);
                if (Build.VERSION.SDK_INT >= 31)
                    parent.setRenderEffect(RenderEffect.createBlurEffect(dp(parent, 12), dp(parent, 12),
                            android.graphics.Shader.TileMode.CLAMP));
            }
            state.dialogRoots.add(root);
            return nested;
        }
    }

    private static void removeDialogLayer(Activity owner, View root) {
        synchronized (DIALOG_SCALES) {
            DialogScaleState state = DIALOG_SCALES.get(owner);
            if (state == null) return;
            state.dialogRoots.remove(root);
            // These sheet roots have no persistent effects; only the top layer is interactive.
            if (!state.dialogRoots.isEmpty()) {
                View parent = state.dialogRoots.get(state.dialogRoots.size() - 1);
                if (Build.VERSION.SDK_INT >= 31) parent.setRenderEffect(null);
                parent.setAlpha(1f);
            }
        }
    }

    private static void beginDialogScale(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed())
            return;
        DialogScaleState state;
        synchronized (DIALOG_SCALES) {
            state = DIALOG_SCALES.get(activity);
            if (state != null) {
                state.count++;
                return;
            }
            View content = activity.findViewById(android.R.id.content);
            if (content == null) return;
            // This app does not install a persistent effect on the Activity content root.
            state = new DialogScaleState(content, null);
            DIALOG_SCALES.put(activity, state);
        }
        if (Build.VERSION.SDK_INT >= 31 && ThemeStore.acrylicEnabled(activity)) {
            state.content.setRenderEffect(RenderEffect.createBlurEffect(dp(state.content, 13),
                    dp(state.content, 13), android.graphics.Shader.TileMode.CLAMP));
        }
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        state.content.animate().cancel();
        state.content.animate().scaleX(state.scaleX * 1.04f).scaleY(state.scaleY * 1.04f)
                .setDuration(DIALOG_BACKGROUND_SCALE_IN_DURATION_MS)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private static void endDialogScale(Activity activity) {
        DialogScaleState state;
        synchronized (DIALOG_SCALES) {
            state = DIALOG_SCALES.get(activity);
            if (state == null) return;
            if (--state.count > 0) return;
            DIALOG_SCALES.remove(activity);
        }
        View content = state.content;
        content.animate().cancel();
        if (Build.VERSION.SDK_INT >= 31) content.setRenderEffect(
                (RenderEffect) state.originalRenderEffect);
        if (!ValueAnimator.areAnimatorsEnabled() || activity.isFinishing() || activity.isDestroyed()) {
            content.setScaleX(state.scaleX);
            content.setScaleY(state.scaleY);
        } else {
            content.animate().scaleX(state.scaleX).scaleY(state.scaleY).setDuration(160)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
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
                params.rightMargin = Math.max(params.rightMargin, bars.right);
                floating.setLayoutParams(params);
            }
            return insets;
        });
        stage.requestApplyInsets();
    }

    /** Insets the viewport, clipping scrolling text and controls safely away from system bars. */
    static void applyScrollableInsets(View stage, View scrollContent) {
        if (Build.VERSION.SDK_INT < 35) return;
        enableEdgeToEdge(stage);
        ScrollView viewport = scrollContent.getParent() instanceof ScrollView scrollView
                ? scrollView : null;
        View insetTarget = viewport == null ? scrollContent : viewport;
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

    private static GradientDrawable shape(View view, int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(view, radius));
        drawable.setStroke(dp(view, 1), stroke);
        return drawable;
    }

    private static int alpha(int color, int value) {
        return Color.argb(value, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int blendRgb(int color, int target, float amount) {
        float inverse = 1f - amount;
        return Color.argb(Color.alpha(color),
                Math.round(Color.red(color) * inverse + Color.red(target) * amount),
                Math.round(Color.green(color) * inverse + Color.green(target) * amount),
                Math.round(Color.blue(color) * inverse + Color.blue(target) * amount));
    }

    private static int childSurface(int color) {
        int baseAlpha = Color.alpha(color);
        // Preserve the former default surface strength without a nonfunctional preference.
        return alpha(color, baseAlpha * 194 / 255);
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
        invalidateAcrylicSurfaces(content);
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
    private static AnimatorSet scale(View view, float value, long duration) {
        AnimatorSet pair = new AnimatorSet();
        pair.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, value),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, value));
        pair.setDuration(duration);
        return pair;
    }
    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
