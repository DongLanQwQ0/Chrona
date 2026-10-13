"""Execute production choice-sheet geometry, including a padding-free negative control.

Java doubles measure vertical LinearLayouts and a one-child ScrollView. They do
not simulate text rendering or Android touch dispatch; the held rectangle is
the control's measured bounds at the production pressed scale.
"""
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
STYLE = (ROOT / 'app/src/main/java/com/donglan/chrona/UiStyle.java').read_text(encoding='utf-8')
PRESS = (ROOT / 'app/src/main/java/com/donglan/chrona/PressFeedback.java').read_text(encoding='utf-8')


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


choice = method(STYLE, '    public static void choiceDialog(')
# This mutation intentionally recreates the missing direct-content inset.
negative = re.sub(r'choices\.setPadding\([^;]+;', 'choices.setPadding(0, 0, 0, 0);', choice)
negative = negative.replace('void choiceDialog(', 'void choiceDialogWithoutPadding(')
style_methods = '\n'.join([
    choice, negative,
    method(STYLE, '    private static LinearLayout dialogPanel('),
    method(STYLE, '    public static void addSpaced('),
    method(STYLE, '    private static int dp('),
])
press_methods = '\n'.join(method(PRESS, signature) for signature in [
    '    static int edgeSpace(', '    static void protectViewport(',
    '    private static void updateViewportBounds(',
]).replace('android.graphics.Rect', 'Rect')
constants = re.search(r'private static final int CHOICE_SHEET_WIDTH_DP = [^;]+;', STYLE).group()
scale = re.search(r'private static final float PRESSED_SCALE = [^;]+;', PRESS).group()

HARNESS = r'''
import java.util.*;
import java.util.function.IntConsumer;
public class ChoiceMotionCheck {
    static int checks, rejected;
    static void check(boolean result, String label) {
        checks++;
        if (!result) throw new AssertionError(label);
    }
    static class Metrics { float density; int widthPixels, heightPixels; }
    static class Resources {
        final Metrics metrics = new Metrics();
        Metrics getDisplayMetrics() { return metrics; }
    }
    static class Activity {
        final Resources resources = new Resources();
        Resources getResources() { return resources; }
    }
    static class Rect {
        int left, top, right, bottom;
        Rect(int l, int t, int r, int b) { left=l; top=t; right=r; bottom=b; }
    }
    static class View {
        final Activity activity;
        int left, top, width, height, pl, pt, pr, pb, minimum;
        LinearLayout.LayoutParams params;
        Rect clipBounds;
        interface Click { void click(View view); }
        View(Activity a) { activity=a; }
        Resources getResources() { return activity.resources; }
        void setPadding(int l,int t,int r,int b) { pl=l;pt=t;pr=r;pb=b; }
        void setOnClickListener(Click c) { }
        int getWidth() { return width; }
        int getHeight() { return height; }
        void setClipBounds(Rect r) { clipBounds=r; }
    }
    static class ViewGroup extends View {
        final List<View> children = new ArrayList<>();
        final List<LayoutListener> listeners = new ArrayList<>();
        interface LayoutListener {
            void layout(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);
        }
        ViewGroup(Activity a) { super(a); }
        void addOnLayoutChangeListener(LayoutListener l) { listeners.add(l); }
        void addView(View v) { addView(v,new LinearLayout.LayoutParams(-1,-2)); }
        void addView(View v,LinearLayout.LayoutParams p) { v.params=p;children.add(v); }
        void notifyLayout() { for(var l:listeners) l.layout(this,0,0,width,height,0,0,0,0); }
    }
    static class LinearLayout extends ViewGroup {
        static final int VERTICAL = 1;
        static class LayoutParams {
            int width,height,topMargin,bottomMargin,leftMargin,rightMargin;
            LayoutParams(int w,int h) { width=w;height=h; }
            void setMargins(int l,int t,int r,int b) {
                leftMargin=l;topMargin=t;rightMargin=r;bottomMargin=b;
            }
        }
        LinearLayout(Activity a) { super(a); }
        void setOrientation(int orientation) { }
        void measure(int availableWidth) {
            width=availableWidth;
            int y=pt;
            for(View v:children) {
                var p=v.params;
                int w=width-pl-pr-p.leftMargin-p.rightMargin;
                if(v instanceof ScrollView s) s.measure(w,p.height);
                else if(v instanceof LinearLayout row) row.measure(w);
                else { v.width=w;v.height=p.height>=0?p.height:v.minimum; }
                v.left=pl+p.leftMargin;v.top=y+p.topMargin;
                y=v.top+v.height+p.bottomMargin;
            }
            height=y+pb;
            notifyLayout();
        }
    }
    static class ScrollView extends ViewGroup {
        int scrollY;
        ScrollView(Activity a) { super(a); }
        void setVerticalScrollBarEnabled(boolean b) { }
        void setHorizontalScrollBarEnabled(boolean b) { }
        void measure(int w,int h) {
            width=w;height=h;
            ((LinearLayout)children.get(0)).measure(w);
            notifyLayout();
        }
    }
    static class TextView extends View {
        TextView(Activity a) { super(a); }
        void setText(String s) { }
        void setTextSize(int s) { }
        void setGravity(int g) { }
        void setMinHeight(int h) { minimum=h; }
        void setTextColor(int color) { }
    }
    static class Gravity { static final int CENTER=1; }
    static class Dialog { void dismiss() { } }
    static class Palette { int onPrimaryContainer,text; }
    static class PressFeedback {
        __SCALE__
        private static final WeakHashMap<ViewGroup,int[]> viewports = new WeakHashMap<>();
        __PRESS__
    }
    __CONSTANTS__
    static final int RADIUS_FIELD=16;
    static LinearLayout lastPanel;
    static Dialog dialog(Activity a) { return new Dialog(); }
    static void showDialog(Dialog d,LinearLayout panel,int width) {
        lastPanel=panel;panel.measure(width);
    }
    static void glass(View v) { }
    static void title(View v) { }
    static void choice(View v,boolean selected,int radius) { }
    static String marked(String text,boolean selected) { return text; }
    static Palette colors(Activity a) { return new Palette(); }
    __STYLE__
    static boolean contained(View row,int width,int height,int offsetY) {
        double dx=row.width*(PressFeedback.PRESSED_SCALE-1)/2;
        double dy=row.height*(PressFeedback.PRESSED_SCALE-1)/2;
        double y=row.top-offsetY;
        return row.left-dx>=0 && row.left+row.width+dx<=width
            && y-dy>=0 && y+row.height+dy<=height;
    }
    static void scenario(int density,int windowDp,int heightDp,int count,boolean bad) {
        Activity a=new Activity();
        var m=a.resources.metrics;
        m.density=density;m.widthPixels=windowDp*density;m.heightPixels=heightDp*density;
        String[] labels=new String[count];Arrays.fill(labels,"Choice");
        if(bad) choiceDialogWithoutPadding(a,"Title",labels,0,i->{});
        else choiceDialog(a,"Title",labels,0,i->{});
        ScrollView scroll=(ScrollView)lastPanel.children.get(1);
        LinearLayout choices=(LinearLayout)scroll.children.get(0);
        String label="density="+density+", width="+windowDp+", height="+heightDp+", rows="+count;
        check(scroll.clipBounds!=null && scroll.clipBounds.right==scroll.width
                && scroll.clipBounds.bottom==scroll.height,"Protected viewport: "+label);
        check(lastPanel.width==Math.min((int)(m.widthPixels*.72f),300*density),"Sheet constraint: "+label);
        for(int index:new int[]{0,count-1}) {
            View row=choices.children.get(index);
            scroll.scrollY=index==0?0:Math.max(0,choices.height-scroll.height);
            boolean direct=contained(row,choices.width,choices.height,0);
            boolean viewport=contained(row,scroll.width,scroll.height,scroll.scrollY);
            if(bad) {
                check(!direct && !viewport,"Padding-free regression must clip both bounds: "+label);
                rejected++;
            } else {
                check(direct,"Held rectangle in direct choices bounds: "+label);
                check(viewport,"Held rectangle in scrolled viewport: "+label);
            }
        }
    }
    public static void main(String[] args) {
        for(int density:new int[]{1,2,3})
            for(int width:new int[]{180,240,360,800})
                for(int height:new int[]{320,800})
                    for(int count:new int[]{1,4,12}) {
                        scenario(density,width,height,count,false);
                        scenario(density,width,height,count,true);
                    }
        System.out.println("Choice motion checks passed: "+checks+"; padding-free held rectangles rejected: "+rejected);
    }
}
'''
for key, value in {'__STYLE__': style_methods, '__PRESS__': press_methods,
                   '__CONSTANTS__': constants, '__SCALE__': scale}.items():
    HARNESS = HARNESS.replace(key, value)
out = ROOT / 'build/choice-motion-check'
out.mkdir(parents=True, exist_ok=True)
java_file = out / 'ChoiceMotionCheck.java'
java_file.write_text(HARNESS, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-d', str(out), str(java_file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'ChoiceMotionCheck'], check=True)
