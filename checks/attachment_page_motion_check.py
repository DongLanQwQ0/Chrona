"""Execute capture's production image reconciliation with narrow view/lifecycle doubles.

Checks identity, focus, confirmation and draft calls; Android rendering is covered
separately by AttachmentPageMotionChecks. No device, Gradle or API calls.
"""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT/'app/src/main/java/com/donglan/chrona/MainActivity.java').read_text(encoding='utf-8')
start = source.index('    private void showPendingImages()')
end = source.index('\n    /**', start)
method = source[start:end]
broken = method.replace('showPendingImages()', 'brokenShowPendingImages()', 1).replace(
    'if (pendingImagesContainer == null) return;',
    'if (pendingImagesContainer == null) return; pendingImagesContainer.removeAllViews();', 1)

harness = r'''
import java.util.*;
import java.io.File;
public class AttachmentPageMotionCheck {
    static int checks,created,opens,deletes,drafts,transitions,ended;
    final List<String> pendingImages=new ArrayList<>(),pendingFiles=new ArrayList<>();
    final LinearLayout pendingImagesContainer=new LinearLayout();
    final View pickImage=new View();
    void startActivity(Intent intent){opens++;}
    void saveDraft(){drafts++;}
    int dp(int n){return n;}
    static class View {
        Object tag,drawable=new Object();boolean focused,enabled;int attaches=1,detaches;
        Object getTag(){return tag;}void setTag(Object tag){this.tag=tag;}
        void setEnabled(boolean value){enabled=value;}
    }
    static class LinearLayout extends View {
        final List<View> children=new ArrayList<>();
        int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
        boolean isLaidOut(){return true;}
        void removeViewAt(int i){children.remove(i).detaches++;}
        void removeAllViews(){while(!children.isEmpty())removeViewAt(children.size()-1);}
        void addView(View v,int index,LayoutParams p){children.add(index,v);}
        void bringChildToFront(View v){if(v==null)throw new AssertionError("null identity");children.remove(v);children.add(v);}
        static class LayoutParams {LayoutParams(int w,int h){}void setMargins(int a,int b,int c,int d){}}
    }
    static class AttachmentImageTile extends View {
        final Runnable open,remove;
        AttachmentImageTile(AttachmentPageMotionCheck owner,Uri uri,String name,Runnable open,Runnable remove){created++;this.open=open;this.remove=remove;}
    }
    static class Uri {static Uri fromFile(File f){return new Uri();}}
    static class ImageStore {
        ImageStore(Object context){}File fileFor(String name){return new File(name);}
        void delete(String name){deletes++;}
    }
    static class Intent {Intent(Object context,Class<?> type){}Intent putExtra(String k,String v){return this;}}
    static class AttachmentViewerActivity {static final String EXTRA_IMAGE_NAME="image_name";}
    static class UiDialogs {
        static Runnable confirm;
        static void confirmDialog(Object context,String title,String body,String action,Runnable callback){confirm=callback;}
    }
    static class UiMotion {static boolean enabled=true;static final long ENTER=240;static final Object SETTLE=new Object();static boolean isEnabled(){return enabled;}}
    static class android {static class transition {
        static class TransitionSet {
            static final int ORDERING_TOGETHER=1;void setOrdering(int value){}
            void addTransition(Object transition){}void setDuration(long value){check(value==240,"existing local duration");}
            void setInterpolator(Object curve){check(curve==UiMotion.SETTLE,"existing local curve");}
        }
        static class Fade {static final int OUT=1,IN=2;Fade(int mode){}}
        static class ChangeBounds {}
        static class TransitionManager {
            static void beginDelayedTransition(LinearLayout list,TransitionSet motion){transitions++;}
            static void endTransitions(LinearLayout list){ended++;}
        }
    }}
    __METHOD__
    __BROKEN__
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    void exercise(boolean broken){
        pendingImages.addAll(List.of("a","b"));if(broken)brokenShowPendingImages();else showPendingImages();
        View a=pendingImagesContainer.getChildAt(0),b=pendingImagesContainer.getChildAt(1);b.focused=true;
        int before=created,beforeMotion=transitions;
        if(broken)brokenShowPendingImages();else showPendingImages();
        check(pendingImagesContainer.getChildAt(0)==a&&pendingImagesContainer.getChildAt(1)==b,"unchanged identities");
        check(created==before&&transitions==beforeMotion,"unchanged no creation/transition");
        pendingImages.add("c");showPendingImages();View c=pendingImagesContainer.getChildAt(2);
        check(created==before+1&&pendingImagesContainer.getChildAt(0)==a&&pendingImagesContainer.getChildAt(1)==b,"append creates only one new tile");
        check(b.focused&&b.detaches==0,"append retains focus and attachment");
        pendingImages.remove("a");showPendingImages();
        check(a.detaches==1&&b.detaches==0&&c.detaches==0&&pendingImagesContainer.getChildAt(0)==b,"remove detaches only removed image");
        Collections.reverse(pendingImages);showPendingImages();
        check(pendingImagesContainer.getChildAt(0)==c&&pendingImagesContainer.getChildAt(1)==b&&b.detaches==0&&c.detaches==0,"reorder preserves exact attached nodes");
        ((AttachmentImageTile)b).open.run();check(opens==1,"existing preview still opens");
        ((AttachmentImageTile)b).remove.run();check(pendingImages.contains("b")&&UiDialogs.confirm!=null,"remove still requires confirmation");
        UiDialogs.confirm.run();
        check(!pendingImages.contains("b")&&b.detaches==1&&c.detaches==0&&deletes==1,"confirmed remove keeps original image delete semantics");
        beforeMotion=transitions;UiMotion.enabled=false;pendingImages.add("d");showPendingImages();
        check(transitions==beforeMotion&&ended>0,"reduced motion stops transitions and updates directly");
        check(!pickImage.enabled&&drafts>0,"entry availability and draft persistence remain active");
        UiMotion.enabled=true;
    }
    public static void main(String[] args){
        new AttachmentPageMotionCheck().exercise(false);
        try{new AttachmentPageMotionCheck().exercise(true);throw new AssertionError("rebuild mutant escaped");}
        catch(AssertionError failure){check("unchanged identities".equals(failure.getMessage()),"negative control fails for lost identities");}
        System.out.println("Attachment image reconciliation passed: "+checks+" assertions; production method, identity/focus/confirmation/draft/reduced-motion doubles");
        System.out.println("Rejected removeAllViews negative control; no Android rendering assertion");
    }
}
'''.replace('__METHOD__', method).replace('__BROKEN__', broken)
out = ROOT/'build/page-audit-next/attachment/host'
out.mkdir(parents=True, exist_ok=True)
java_file = out/'AttachmentPageMotionCheck.java'
java_file.write_text(harness, encoding='utf-8')
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21'))/'bin'
subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(java_file)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out), 'AttachmentPageMotionCheck'], check=True)
