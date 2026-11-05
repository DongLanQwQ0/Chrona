"""Execute the production page-restore callback and scroll ownership guard with layout doubles."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/TaskDetailActivity.java').read_text(encoding='utf-8')
begin = source.index('        Runnable restorePosition = () -> {')
end = source.index('\n        };', begin) + len('\n        };')
restore = source[begin:end]
begin = source.index('            if (!positionRestored[0]')
end = source.index('            int firstVisible', begin)
guard = source[begin:end]
pager_source = (root / 'app/src/main/java/com/donglan/chrona/CandidatePagerScrollView.java').read_text(encoding='utf-8')
measure = pager_source.split('    @Override protected void onMeasure', 1)[1].split('    void addGesturePriorityChild', 1)[0]
out = root / 'build/detail-carousel-restore-check'
out.mkdir(parents=True, exist_ok=True)
harness = r'''
public class DetailCarouselRestoreCheck {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static class Box {int width;int getWidth(){return width;}}
    static class Params {int width;}
    static class View {final Params params=new Params(); boolean forced; Params getLayoutParams(){return params;} void forceLayout(){forced=true;}}
    static class LinearLayout extends View {View[] slots={new View(),new View(),new View()}; int getChildCount(){return slots.length;} View getChildAt(int i){return slots[i];}}
    static class MeasureSpec {static int getSize(int width){return width;}}
    static class BasePager {
        int x; int getScrollX(){return x;}
        protected void onMeasure(int width,int height){}
        protected void onLayout(boolean changed,int l,int t,int r,int b){}
    }
    static class Pager extends BasePager {
        int pageWidth=300,resizingPage=-1;
        final LinearLayout pages=new LinearLayout();
        final Animation settleAnimator=new Animation();
        static class Animation {void cancel(){}}
        int getPaddingLeft(){return 10;} int getPaddingRight(){return 10;}
        View getChildAt(int i){return pages;}
        void finishTracking(){}
        int clamp(int i){return Math.max(0,Math.min(2,i));}
        void scrollTo(int x,int y){this.x=x;}
        protected void onMeasure__MEASURE__
    }
    static class Carousel {
        Object parent;int x;java.util.function.IntConsumer listener;
        Object getParent(){return parent;}
        int pageWidth(){return 300;} boolean isResizing(){return false;}
        void scrollTo(int x,int y){this.x=x;if(listener!=null)listener.accept(x);}
    }
    static class Fixture {
        final Object ownerContent=new Object(); final long ownerTaskId=2;
        Object content=new Object();long taskId=1;int selected;boolean destroyed;
        final boolean[] positionRestored={false};final Carousel carousel=new Carousel();final Box pages=new Box();
        final int initialPageIndex=2,pageWidth=300;
        Fixture(){carousel.parent=ownerContent;carousel.listener=x->{
            __GUARD__
            selected=x/pageWidth;
        };}
        boolean isDestroyed(){return destroyed;}
        Runnable callback(){__RESTORE__ return restorePosition;}
        Runnable broken(){__BROKEN__ return restorePosition;}
    }
    public static void main(String[] ignored){
        Fixture f=new Fixture();Runnable restore=f.callback();restore.run();
        check(f.carousel.x==0&&!f.positionRestored[0],"unmeasured page defers restoration");
        f.pages.width=900;restore.run();
        check(f.carousel.x==600&&f.positionRestored[0],"background page restores its own selected position");
        check(f.taskId==1&&f.selected==0,"background layout cannot mutate active task selection");
        f.content=f.ownerContent;f.taskId=2;f.carousel.scrollTo(300,0);restore.run();
        check(f.selected==1&&f.carousel.x==300,"restoration runs once and preserves later user scrolling");
        f.content=new Object();f.carousel.scrollTo(0,0);
        check(f.selected==1,"detached render cannot mutate replacement content");
        f.content=f.ownerContent;f.taskId=9;f.carousel.scrollTo(600,0);
        check(f.selected==1,"same container with another task cannot receive stale scroll updates");
        Fixture detached=new Fixture();detached.pages.width=900;detached.carousel.parent=null;detached.callback().run();
        check(!detached.positionRestored[0],"removed carousel is not restored");
        Fixture dead=new Fixture();dead.pages.width=900;dead.destroyed=true;dead.callback().run();
        check(!dead.positionRestored[0],"destroyed Activity is not restored");
        Fixture negative=new Fixture();negative.pages.width=900;negative.broken().run();
        check(negative.carousel.x==0,"negative control reproduces old global-container blank page");
        Pager pager=new Pager();pager.x=600;
        pager.onMeasure(250,150);pager.onMeasure(240,150);
        check(pager.pageWidth==220&&pager.resizingPage==2,"two measures retain original selected page");
        check(pager.pages.slots[2].params.width==220&&pager.pages.forced,"slots use viewport after horizontal padding");
        pager.onLayout(true,0,0,240,150);
        check(pager.x==440&&pager.resizingPage==-1,"same-instance narrowing realigns the selected slot");
        pager.onMeasure(540,150);pager.onLayout(true,0,0,540,150);
        check(pager.x==1040&&pager.pages.slots[2].params.width==520,"widening retains selection");
        System.out.println("PASS: "+checks+" production restore/ownership checks including negative control; not Android rendering");
    }
}
'''
broken = restore.replace('carousel.getParent() != ownerContent', 'carousel.getParent() != content')
harness = harness.replace('__GUARD__', guard).replace('__RESTORE__', restore).replace('__BROKEN__', broken).replace('__MEASURE__', measure.replace('@Override protected void onLayout', 'protected void onLayout'))
file = out / 'DetailCarouselRestoreCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'DetailCarouselRestoreCheck'], check=True)
