"""Run the production detail disclosure controller against interruptible animator doubles."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/donglan/chrona/UiMotion.java').read_text(encoding='utf-8')
registry_start = source.index('    private static final WeakHashMap<View, java.lang.ref.WeakReference<SectionMotion>>')
registry_end = source.index(';', registry_start) + 1
start = source.index('    public static void section(View view, boolean showing)')
end = source.index('    /** Idempotent; observes', start)
controller = (source[registry_start:registry_end] + '\n' + source[start:end]).replace('android.animation.', '').replace('android.view.animation.', '')
harness = r'''
import java.util.*;
public class SectionMotionCheck {
    static int checks;
    static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    static class UiMotion {
        static boolean enabled = true;
        static final long ENTER = 240, EXIT = 180;
        static boolean isEnabled() { return enabled; }
        CONTROLLER
    }
    static class Animator { }
    static class AnimatorListenerAdapter { public void onAnimationEnd(Animator a) { } }
    static class ValueAnimator extends Animator {
        interface Update { void onAnimationUpdate(ValueAnimator a); }
        final List<Update> updates = new ArrayList<>();
        final List<AnimatorListenerAdapter> listeners = new ArrayList<>();
        float fraction;
        boolean canceled;
        static ValueAnimator ofFloat(float a, float b) { return new ValueAnimator(); }
        void setDuration(long duration) { }
        void setInterpolator(Object i) { }
        void addUpdateListener(Update update) { updates.add(update); }
        void addListener(AnimatorListenerAdapter listener) { listeners.add(listener); }
        void removeAllUpdateListeners() { updates.clear(); }
        void removeAllListeners() { listeners.clear(); }
        void cancel() { canceled = true; end(); }
        void start() { }
        Object getAnimatedValue() { return fraction; }
        void step(float value) {
            fraction = value;
            for (Update update : new ArrayList<>(updates)) update.onAnimationUpdate(this);
            if (value == 1) end();
        }
        void end() {
            for (AnimatorListenerAdapter listener : new ArrayList<>(listeners)) listener.onAnimationEnd(this);
        }
    }
    static class DecelerateInterpolator { DecelerateInterpolator(float factor) { } }
    static class View {
        static final int VISIBLE = 0, GONE = 8;
        static class MeasureSpec {
            static final int EXACTLY = 1, UNSPECIFIED = 0;
            static int makeMeasureSpec(int value, int mode) { return value; }
        }
        interface OnAttachStateChangeListener {
            void onViewAttachedToWindow(View v);
            void onViewDetachedFromWindow(View v);
        }
        int visibility = VISIBLE, laidOutHeight, measuredHeight, desiredHeight;
        float alpha = 1, translation;
        boolean attached = true;
        ViewGroup parent;
        ViewGroup.LayoutParams params;
        final List<OnAttachStateChangeListener> attachments = new ArrayList<>();
        View(int height) { params = new ViewGroup.LayoutParams(height); laidOutHeight = height; }
        ViewGroup.LayoutParams getLayoutParams() { return params; }
        Object getParent() { return parent; }
        int getHeight() { return laidOutHeight; }
        int getMeasuredHeight() { return measuredHeight; }
        int getVisibility() { return visibility; }
        void setVisibility(int value) { visibility = value; }
        float getAlpha() { return alpha; }
        void setAlpha(float value) { alpha = value; }
        void setTranslationY(float value) { translation = value; }
        void requestLayout() { }
        void measure(int width, int height) { measuredHeight = desiredHeight; }
        boolean isAttachedToWindow() { return attached; }
        ValueAnimator animate() { return new ValueAnimator(); }
        void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) { attachments.add(listener); }
        void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) { attachments.remove(listener); }
        void detach() {
            attached = false;
            for (OnAttachStateChangeListener listener : new ArrayList<>(attachments)) listener.onViewDetachedFromWindow(this);
        }
    }
    static class ViewGroup extends View {
        static class LayoutParams { int height; LayoutParams(int height) { this.height = height; } }
        static class MarginLayoutParams extends LayoutParams {
            int leftMargin, rightMargin;
            MarginLayoutParams(int h) { super(h); }
        }
        ViewGroup() { super(0); }
        int getWidth() { return 300; }
        int getPaddingLeft() { return 10; }
        int getPaddingRight() { return 10; }
    }
    public static void main(String[] args) {
        View v = new View(156);
        v.parent = new ViewGroup();
        v.visibility = View.GONE;
        UiMotion.section(v, true);
        UiMotion.SectionMotion motion = UiMotion.SECTION_MOTIONS.get(v).get();
        check(v.params.height == 0 && v.alpha == 0, "Expansion begins collapsed");
        ValueAnimator first = motion.animator;
        first.step(.4f);
        int currentHeight = v.params.height;
        float currentAlpha = v.alpha;
        UiMotion.section(v, false);
        check(first.canceled, "Old animator canceled");
        check(v.params.height == currentHeight && v.alpha == currentAlpha, "Reversal retains geometry and alpha");
        ValueAnimator second = motion.animator;
        second.step(.3f);
        currentHeight = v.params.height;
        UiMotion.section(v, true);
        check(v.params.height == currentHeight, "Second reversal retains current height");
        second.end();
        check(v.visibility == View.VISIBLE, "Canceled collapse cannot hide new expansion");
        motion.animator.step(1);
        check(v.params.height == 156 && v.alpha == 1 && motion.animator == null, "Expanded terminal state");
        UiMotion.section(v, false);
        motion.animator.step(.5f);
        UiMotion.enabled = false;
        motion.animator.step(.6f);
        check(v.visibility == View.GONE && v.params.height == 156 && v.alpha == 1, "Disabling midflight reaches requested state");
        UiMotion.section(v, true);
        check(v.visibility == View.VISIBLE && motion.animator == null, "Disabled expansion is immediate");
        UiMotion.enabled = true;
        UiMotion.section(v, false);
        v.detach();
        check(v.visibility == View.GONE && v.alpha == 1, "Detach settles collapse");
        check(UiMotion.SECTION_MOTIONS.isEmpty() && v.attachments.isEmpty(), "Detach drops controller and listener");
        View dynamic = new View(-2);
        dynamic.parent = new ViewGroup();
        dynamic.visibility = View.GONE;
        dynamic.desiredHeight = 200;
        UiMotion.section(dynamic, true);
        UiMotion.SectionMotion flexible = UiMotion.SECTION_MOTIONS.get(dynamic).get();
        flexible.animator.step(.5f);
        check(dynamic.params.height == 100, "Wrap content measures full natural size");
        dynamic.desiredHeight = 300;
        UiMotion.section(dynamic, false);
        UiMotion.section(dynamic, true);
        flexible.animator.step(.5f);
        check(dynamic.params.height == 200, "Reversal remeasures changed content");
        flexible.animator.step(1);
        check(dynamic.params.height == -2, "Finish restores wrap content constraint");
        UiMotion.section(dynamic, false, false);
        check(dynamic.visibility == View.GONE && dynamic.params.height == -2 && dynamic.alpha == 1 && flexible.animator == null, "Restored collapse is immediate with natural height and opacity");
        UiMotion.section(dynamic, true, false);
        check(dynamic.visibility == View.VISIBLE && dynamic.params.height == -2 && flexible.animator == null, "Initial expanded content is restored without animation");
        check(UiMotion.SECTION_MOTIONS.get(dynamic).get() == flexible && dynamic.attachments.size() == 1, "Animated and restored calls share one controller owner");
        System.out.println("Section motion checks passed: " + checks + " (production controller with animator doubles)");
    }
}
'''.replace('CONTROLLER', controller)
out = ROOT / 'build/section-motion-check'
out.mkdir(parents=True, exist_ok=True)
java_file = out / 'SectionMotionCheck.java'
java_file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(java_file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'SectionMotionCheck'], check=True)
