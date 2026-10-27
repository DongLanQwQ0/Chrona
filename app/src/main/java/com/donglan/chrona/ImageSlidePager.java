package com.donglan.chrona;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Three-image viewport: the current image and its neighbours share one finger-following track. */
final class ImageSlidePager extends FrameLayout {
    interface Source { File file(String name) throws Exception; boolean allowed(); }
    interface Listener { void selected(int index, ImageView image); }
    /** Consume image panning first; return only horizontal travel left at its edge. */
    interface PanHandler { float pan(float dx, float dy); }
    private final ImageView previous, current, next;
    private final android.app.Activity activity;
    private final int slop;
    private final boolean fitHeight;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> cache = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final List<String> names = new ArrayList<>();
    private Source source;
    private Listener listener;
    private java.util.function.BooleanSupplier pagingAllowed = () -> true;
    private PanHandler panHandler;
    private java.util.function.BooleanSupplier dragAction;
    private boolean outboundDrag;
    private int index, requested = -1, settleTarget = -1, settleDirection;
    private volatile int generation;
    private boolean closed, tracking, blocked;
    private boolean multiTouch;
    private float panX, panY, gestureDownX;
    private float downX, downY, originX, offset, startOffset;
    private float ratio = 16f / 9f;
    private ValueAnimator animator;

    ImageSlidePager(android.app.Activity context, boolean fitHeight) {
        super(context);
        activity=context;
        this.fitHeight = fitHeight;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(true);
        previous = page(context); current = page(context); next = page(context);
        addView(previous, new LayoutParams(-1, -1));
        addView(next, new LayoutParams(-1, -1));
        addView(current, new LayoutParams(-1, -1));
        previous.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        next.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private ImageView page(Context context) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return image;
    }

    ImageView image() { return current; }
    void setListener(Listener listener) { this.listener = listener; }
    void setPagingAllowed(java.util.function.BooleanSupplier allowed) { pagingAllowed = allowed; }
    void setPanHandler(PanHandler handler) { panHandler = handler; }
    void setDragAction(java.util.function.BooleanSupplier action){dragAction=action;current.setOnLongClickListener(view->beginDrag());}
    private boolean beginDrag(){
        if(outboundDrag||multiTouch||tracking||!ready()||animator!=null||Math.abs(offset)>.5f||dragAction==null||!dragAction.getAsBoolean())return false;
        outboundDrag=true;blocked=true;tracking=false;
        long now=android.os.SystemClock.uptimeMillis();MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);
        super.dispatchTouchEvent(cancel);cancel.recycle();return true;
    }
    boolean ready() { return index < names.size() && current.getDrawable() != null && cache.get(names.get(index)) != null; }
    int selection() { return requested >= 0 ? requested : settleTarget >= 0 ? settleTarget : index; }

    void setImages(List<String> images, int selected, Source source) {
        String previousName=index<names.size()?names.get(index):null;
        stop(); generation++; pending.clear();
        this.source = source;
        names.clear(); names.addAll(images);
        int desired=Math.max(0,Math.min(selected,names.size()-1));
        index=desired;requested=-1;
        if(!names.isEmpty()&&bitmap(desired)==null&&previousName!=null&&names.contains(previousName)&&cache.get(previousName)!=null){
            index=names.indexOf(previousName);requested=desired;
        }
        prune(); bind(); loadWindow();
        if(requested>=0)load(requested);
    }

    boolean select(int selected) {
        if (selected < 0 || selected >= names.size() || !source.allowed()) return false;
        if (selected == selection()) return true;
        if (cache.containsKey(names.get(selected))&&cache.get(names.get(selected))==null){Feedback.show(activity,"图片缓存暂不可用");return false;}
        if (cache.get(names.get(selected))!=null) {
            requested = -1; animateTo(selected);
        } else {
            requested = selected;
            load(selected);
        }
        return true;
    }

    private void animateTo(int selected) {
        interruptMotion();
        if (selected == index) { settle(0); return; }
        int direction = selected > index ? 1 : -1;
        (direction > 0 ? next : previous).setImageBitmap(bitmap(selected));
        settle(direction, selected);
    }

    /** A new gesture owns the already committed destination, without snapping its pixels. */
    private void interruptMotion() {
        if (animator == null) return;
        ValueAnimator running = animator; animator = null; running.cancel();
        int target = settleTarget, direction = settleDirection;
        settleTarget = -1; settleDirection = 0;
        if (target >= 0 && target != index) {
            Bitmap departing = bitmap(index);
            float distance = offset + direction * getWidth();
            index = target; bind();
            (direction > 0 ? previous : next).setImageBitmap(departing);
            transform(distance); prune(); loadWindow();
        }
    }

    private void bind() {
        Bitmap bitmap = bitmap(index);
        // Keep the last complete frame while a replacement list's selected image is decoded.
        if(bitmap==null&&current.getDrawable()!=null){transform(0);return;}
        current.setImageBitmap(bitmap);
        previous.setImageBitmap(bitmap(index - 1)); next.setImageBitmap(bitmap(index + 1));
        if (bitmap != null && fitHeight) {
            float nextRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            if (Math.abs(ratio - nextRatio) > .0001f) { ratio = nextRatio; requestLayout(); }
        }
        transform(0);
        if (listener != null) listener.selected(index, current);
    }

    private Bitmap bitmap(int position) { return position < 0 || position >= names.size() ? null : cache.get(names.get(position)); }
    private void loadWindow() { load(index); load(index - 1); load(index + 1); }
    private void load(int position) {
        if (closed || source == null || position < 0 || position >= names.size()) return;
        String name = names.get(position);
        if (cache.containsKey(name) || !pending.add(name)) return;
        int token = generation;
        Source loading = source;
        decoder.execute(() -> {
            Bitmap decoded = null;
            try {
                if (closed || token != generation || !loading.allowed()) return;
                File file = loading.file(name);
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), options);
                if (options.outWidth <= 0 || options.outHeight <= 0) return;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                decoded = BitmapFactory.decodeFile(file.getPath(), options);
            } catch (Exception | OutOfMemoryError ignored) {
                // A removed or invalid local frame remains an unavailable page, never a stale image.
            } finally {
                Bitmap result = decoded;
                post(() -> {
                    if (closed || token != generation || !loading.allowed()) { if (result != null) result.recycle(); return; }
                    pending.remove(name);
                    boolean wanted = Math.abs(names.indexOf(name) - index) <= 1
                            || requested >= 0 && names.get(requested).equals(name);
                    if (!wanted) { if (result != null) result.recycle(); return; }
                    cache.put(name, result);
                    if(result==null){if(names.get(index).equals(name))current.setContentDescription("图片缓存暂不可用");if(requested>=0&&names.get(requested).equals(name)){requested=-1;Feedback.show(activity,"图片缓存暂不可用");}return;}
                    if (requested >= 0 && names.get(requested).equals(name)) {
                        int selected = requested; requested = -1; animateTo(selected);
                    } else if (names.get(index).equals(name)) bind();
                    else { previous.setImageBitmap(bitmap(index - 1)); next.setImageBitmap(bitmap(index + 1)); }
                });
            }
        });
    }

    private void prune() {
        // Detached bitmap drawables may still be referenced by the render thread: let GC reclaim them.
        cache.keySet().removeIf(name -> Math.abs(names.indexOf(name) - index) > 1 || !names.contains(name));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (fitHeight) {
            int height = Math.max(1, Math.round(MeasureSpec.getSize(widthSpec) / ratio));
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        } else super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { super.onSizeChanged(w, h, oldW, oldH); transform(offset); }
    private void transform(float distance) {
        offset = distance;
        current.setTranslationX(distance);
        previous.setTranslationX(distance - getWidth()); next.setTranslationX(distance + getWidth());
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            outboundDrag=false;
            interruptMotion(); requested = -1;
            downX = event.getX(); downY = event.getY(); originX = downX; startOffset = offset;
            panX=gestureDownX=downX;panY=downY;multiTouch=false;
            tracking = false; blocked = !ready()||!pagingAllowed.getAsBoolean();
        }
        if(outboundDrag)return true;
        if (action == MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() > 1) {
            multiTouch=true;
            blocked = true;
            if (tracking) { settle(0); tracking = false; return true; }
        }
        if(action==MotionEvent.ACTION_MOVE&&!tracking&&!multiTouch&&ready()&&panHandler!=null){
            float dx=event.getX()-panX,dy=event.getY()-panY;
            downX+=dx-panHandler.pan(dx,dy);
            panX=event.getX();panY=event.getY();
        }
        if (action == MotionEvent.ACTION_MOVE && !blocked && !tracking && names.size() > 1) {
            float dx = event.getX() - downX, dy = event.getY() - downY;
            // Image panning discounts page travel, not the user's actual gesture direction.
            float gestureDx=event.getX()-gestureDownX;
            if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(gestureDx)) blocked = true;
            else if (Math.abs(dx) > slop && Math.abs(gestureDx) > Math.abs(dy) * 1.25f) {
                tracking = true; originX = downX + Math.copySign(slop, dx);
                MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel); cancel.recycle();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        if (tracking) {
            if (action == MotionEvent.ACTION_MOVE) {
                float distance = startOffset + event.getX() - originX;
                boolean edge = distance > 0 ? index == 0 : index == names.size() - 1;
                transform(edge ? distance * .22f : Math.max(-getWidth(), Math.min(getWidth(), distance)));
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float travel = event.getX() - originX;
                int direction = travel < 0 ? 1 : -1;
                boolean commit = action == MotionEvent.ACTION_UP && Math.abs(travel) >= Math.max(dp(48), getWidth() * .15f)
                        && index + direction >= 0 && index + direction < names.size();
                tracking = false;
                if (commit && bitmap(index + direction) == null) { settle(0); select(index + direction); }
                else settle(commit ? direction : 0);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return true;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (offset != 0 && animator == null) settle(0);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        // The moving current ImageView can leave a non-clickable neighbour under DOWN.
        // Keep the viewport in the touch stream so the next MOVE can still start paging.
        return handled || !blocked;
    }

    private void settle(int direction) {
        settle(direction, direction == 0 ? -1 : index + direction);
    }
    private void settle(int direction, int selected) {
        if (animator != null) { ValueAnimator running = animator; animator = null; running.cancel(); }
        settleTarget = selected; settleDirection = direction;
        float target = -direction * getWidth();
        if (!ValueAnimator.areAnimatorsEnabled() || offset == target) { finishSettle(direction); return; }
        ValueAnimator animation = ValueAnimator.ofFloat(offset, target); animator = animation;
        animation.setDuration(SwipePagerLayout.recoilDuration(Math.abs(target - offset), getWidth()));
        animation.setInterpolator(SwipePagerLayout.RECOIL_INTERPOLATOR);
        animation.addUpdateListener(frame -> transform((Float) frame.getAnimatedValue()));
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ignored) { if (animator == animation) { animator = null; finishSettle(direction); } }
        });
        animation.start();
    }
    private void finishSettle(int direction) {
        int selected = settleTarget; settleTarget = -1; settleDirection = 0;
        if (direction != 0) { index = selected; prune(); bind(); loadWindow(); }
        else transform(0);
    }
    void stop() { interruptMotion(); settleTarget = -1; settleDirection = 0; tracking = false; transform(0); }
    void close() { closed = true; generation++; stop(); decoder.shutdownNow(); cache.clear(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
