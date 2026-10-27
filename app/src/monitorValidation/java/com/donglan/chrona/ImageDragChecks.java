package com.donglan.chrona;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import com.donglan.chrona.image.ImageStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Real touchscreen pickup/drop against the separately installed androidTest application UID. */
final class ImageDragChecks {
    private static final String TILE_COMPONENT="attachment-tile-component";
    private final Instrumentation test;
    private final Bundle args;
    private final Context app;
    private final String account,receiverPackage;
    private final List<File> files=new ArrayList<>();
    private final JSONArray outcomes=new JSONArray();
    private final JSONArray inputTimings=new JSONArray();
    private final Lifecycle lifecycle=new Lifecycle();
    private Activity activity;
    private ClassroomLesson lesson;
    private String attachment,otherAttachment;
    private byte[] jpeg,png;
    private int assertions;
    private volatile int tileOpens,tileRemoves;
    private boolean pendingWindowTransition=true;
    private ImageDragChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;app=test.getTargetContext();account=new ZjuSettingsStore(app).account();receiverPackage=args.getString("receiver_package",app.getPackageName()+".test");((Application)app.getApplicationContext()).registerActivityLifecycleCallbacks(lifecycle);}
    static int run(Instrumentation test,Bundle args)throws Exception{
        if(!"com.donglan.chrona.monitor.validation".equals(test.getTargetContext().getPackageName())||!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Isolated emulator fixture only");
        ImageDragChecks checks=new ImageDragChecks(test,args);
        try{checks.seed();checks.runPaths();return checks.assertions;}
        catch(Throwable failure){checks.failureEvidence(failure);throw failure;}
        finally{checks.saveTimings();new ZjuSettingsStore(checks.app).prefs.edit().putBoolean("enabled",true).commit();checks.close();((Application)checks.app.getApplicationContext()).unregisterActivityLifecycleCallbacks(checks.lifecycle);for(File file:checks.files)file.delete();}
    }
    private void seed()throws Exception{
        check(ClassroomMonitor.allowed(app,account),"runner provides its enabled synthetic account");
        jpeg=picture(Bitmap.CompressFormat.JPEG,Color.rgb(35,115,155));png=picture(Bitmap.CompressFormat.PNG,Color.rgb(155,75,45));
        attachment=UUID.randomUUID()+".jpg";otherAttachment=UUID.randomUUID()+".jpg";
        File a=new ImageStore(app).fileFor(attachment),b=new ImageStore(app).fileFor(otherAttachment);a.getParentFile().mkdirs();write(a,jpeg);write(b,jpeg);
        lesson=new ClassroomLesson("8830","9200","拖放合成课堂","图片拖放","合成教室","2",System.currentTimeMillis()-600000);
        File dir=new ClassroomStore(app,account).localLesson(lesson);write(new File(dir,"raw-0.img"),png);write(new File(dir,"raw-1.img"),png);
        File candidates=new File(dir,"monitor-candidates.json");ClassroomStore.write(candidates,new JSONObject().put("updated",System.currentTimeMillis()).put("images",new JSONArray().put(new JSONObject().put("raw","raw-0.img").put("second",1)).put(new JSONObject().put("raw","raw-1.img").put("second",20))));files.add(candidates);
        File captions=new File(dir,"live-transcript.json");ClassroomStore.write(captions,new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("start",0).put("end",1).put("text","图片拖放合成字幕"))));files.add(captions);
    }
    private void runPaths()throws Exception{
        Uri attachmentUri=ImageTransferProvider.attachment(app,attachment),otherAttachmentUri=ImageTransferProvider.attachment(app,otherAttachment);
        if("tile".equals(args.getString("drag_scope"))){tileComponent(attachmentUri,otherAttachmentUri);writeReport();return;}
        activity=test.startActivitySync(new Intent(app,AttachmentViewerActivity.class).putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME,attachment).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ImageView image=readyImage("image");drop("attachment",image,attachmentUri,otherAttachmentUri,jpeg,"image/jpeg",false);close();
        tileComponent(attachmentUri,otherAttachmentUri);
        Uri classroomUri=ImageTransferProvider.classroom(app,account,lesson,"raw-0.img"),otherClassroomUri=ImageTransferProvider.classroom(app,account,lesson,"raw-1.img");
        openClassroom();image=readyImage("image");drop("classroom-viewer",image,classroomUri,otherClassroomUri,png,"image/png",false);close();
        activity=test.startActivitySync(new Intent(app,ClassroomTranscriptActivity.class).putExtra("account",account).putExtra("lesson",lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Method mode=ClassroomTranscriptActivity.class.getDeclaredMethod("setMode",int.class);mode.setAccessible(true);test.runOnMainSync(()->{try{mode.invoke(activity,1);}catch(Exception failure){throw new RuntimeException(failure);}});
        image=readyImage("slideImage");drop("classroom-inline",image,classroomUri,otherClassroomUri,png,"image/png",false);close();
        gestureSafeguards();
        openClassroom();image=readyImage("image");drop("classroom-disabled-after-pickup",image,classroomUri,otherClassroomUri,png,"image/png",true);
        new ZjuSettingsStore(app).prefs.edit().putBoolean("enabled",true).commit();
        writeReport();
    }
    private void writeReport()throws Exception{File output=new File(app.getExternalFilesDir(null),"monitor-validation/image-drag-report.json");ClassroomStore.write(output,new JSONObject().put("assertions",assertions).put("scope",args.getString("drag_scope","all")).put("paths",outcomes).put("receiverPackage",receiverPackage));}
    private void openClassroom()throws Exception{
        Bundle extras=new Bundle();extras.putString("classroom_image_account",account);extras.putString("classroom_image_lesson",lesson.json().toString());extras.putString("classroom_image_raw","raw-0.img");extras.putStringArrayList("classroom_image_list",new ArrayList<>(List.of("raw-0.img","raw-1.img")));
        activity=test.startActivitySync(new Intent(app,AttachmentViewerActivity.class).putExtras(extras).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private ImageView readyImage(String name)throws Exception{
        final ImageView[] result={null};waitFor(()->{try{result[0]=(ImageView)field(activity,name);return result[0]!=null&&result[0].getDrawable()!=null&&result[0].getWidth()>0&&result[0].getHeight()>0;}catch(Exception pending){return false;}},"decoded "+name);sourceReady("decoded "+name);return result[0];
    }
    private void drop(String label,ImageView image,Uri uri,Uri other,byte[] expected,String mime,boolean disableAfterPickup)throws Exception{
        Reports reports=new Reports();Intent receiver=new Intent().setComponent(new ComponentName(receiverPackage,"com.donglan.chrona.ImageDragReceiverActivity"))
                .putExtra("expected_uri",uri.toString()).putExtra("other_uri",other.toString()).putExtra("expected_bytes",expected).putExtra("reports",remoteReports(reports))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Start on the source first: a different UID's floating Activity legitimately blocks
        // a new DOWN through its task input sink. An already active system drag crosses apps.
        Rect source=bounds(image);check(!source.isEmpty(),label+": source is exposed before pickup");
        float x=source.exactCenterX(),y=source.exactCenterY();DragObserver sourceDrag=observe(image);
        long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,x,y);
        boolean released=false;
        try{
            waitFor(()->sourceDrag.starts>0,label+": real long press starts system drag in source window");
            if(!TILE_COMPONENT.equals(label))check(((Boolean)field(pager(),"outboundDrag")),label+": source enters outbound state before switching apps");
            capture(label+"-picked-up");
            // Intentionally no data/ClipData/URI grant: the new receiver still cannot read
            // merely because it becomes visible during an active cross-application drag.
            pendingWindowTransition=true;test.runOnMainSync(()->activity.startActivity(receiver));
            check(reports.ready.await(8,TimeUnit.SECONDS),label+": separate receiver window joins active drag");Bundle ready=reports.readyData;
            check(ready.getBoolean("beforeDenied"),label+": receiver cannot read before drop");check(ready.getInt("uid")!=android.os.Process.myUid(),label+": callback came from a different UID");
            check(reports.started.await(4,TimeUnit.SECONDS),label+": receiver gets real ongoing ACTION_DRAG_STARTED");
            check(reports.startData.getBoolean("image"),label+": platform drag description advertises image MIME");
            if(disableAfterPickup)test.runOnMainSync(()->new ZjuSettingsStore(app).prefs.edit().putBoolean("enabled",false).commit());
            float endX=ready.getInt("x"),endY=ready.getInt("y");
            for(int i=1;i<=18;i++){SystemClock.sleep(24);pointer(down,MotionEvent.ACTION_MOVE,x+(endX-x)*i/18f,y+(endY-y)*i/18f);}
            SystemClock.sleep(180);capture(label+"-held");pointer(down,MotionEvent.ACTION_UP,endX,endY);released=true;
            check(reports.dropped.await(8,TimeUnit.SECONDS),label+": real receiver ACTION_DROP");Bundle result=reports.dropData;
            check(result.getBoolean("permission")&&result.getBoolean("exact"),label+": exact URI temporary drop grant");
            if(disableAfterPickup)check(!result.getBoolean("read"),label+": disabled classroom account refuses a granted external read");
            else{
                check(result.getBoolean("read")&&result.getBoolean("complete")&&result.getInt("bytes")==expected.length,label+": different UID reads complete original bytes");
                check(mime.equals(result.getString("mime")),label+": precise MIME survives raw .img source");
                check(result.getBoolean("writeDenied"),label+": no external write permission");check(result.getBoolean("otherDenied"),label+": adjacent valid image URI remains private");
            }
            check(result.getBoolean("releasedDenied"),label+": release ends exact URI read access");
            JSONObject row=new JSONObject().put("path",label);for(String key:result.keySet())row.put(key,result.get(key));outcomes.put(row);
            check(reports.drawn.await(3,TimeUnit.SECONDS),label+": receiver result text has drawn a frame");capture(label+"-result");
            if(!disableAfterPickup)verifyBrowsingAfterDrop(label,image);
        }finally{if(!released)pointer(down,MotionEvent.ACTION_CANCEL,x,y);}
    }
    private static ResultReceiver remoteReports(ResultReceiver reports){
        // Only framework Parcelable names may cross to the test APK's unrelated classloader.
        Parcel parcel=Parcel.obtain();try{reports.writeToParcel(parcel,0);parcel.setDataPosition(0);return ResultReceiver.CREATOR.createFromParcel(parcel);}finally{parcel.recycle();}
    }
    private void verifyBrowsingAfterDrop(String label,ImageView image)throws Exception{
        sourceReady(label+": receiver transition finished");
        check(!activity.isFinishing(),label+": pickup UP does not close the source page");
        if(test instanceof ClassroomMonitorInstrumentation monitor)check(monitor.lastResumed==activity,label+": pickup UP does not open a second viewer");
        if(TILE_COMPONENT.equals(label)){
            check(tileOpens==0&&tileRemoves==0,"component pickup/drop UP does not invoke onOpen or onRemove");
            tap(image);waitFor(()->tileOpens==1,"component receives the next real preview tap");check(tileOpens==1&&tileRemoves==0,"component next real preview tap invokes only onOpen");
            return;
        }
        ImageSlidePager pager=pager();
        if("attachment".equals(label)){
            doubleTap(image);check(((Float)field(activity,"scale"))>1.1f,label+": next real double tap zooms after drop");
        }else{
            swipePage(image,true,false);awaitIndex(pager,1,label+": next real swipe browses after drop");
        }
        check(!((Boolean)field(pager,"outboundDrag")),label+": next DOWN exits outbound-drag state");
    }
    private void tileComponent(Uri uri,Uri other)throws Exception{
        // Only the shared component is under test. Reusing an empty viewer Activity as its
        // host does not exercise MainActivity's or TaskDetailActivity's business flow.
        activity=test.startActivitySync(new Intent(app,AttachmentViewerActivity.class).putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME,attachment).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));readyImage("image");
        final AttachmentImageTile[] tile={null};test.runOnMainSync(()->{
            FrameLayout host=new FrameLayout(activity);host.setBackgroundColor(Color.rgb(238,242,246));
            TextView label=new TextView(activity);label.setText("AttachmentImageTile · component fixture");label.setGravity(Gravity.CENTER);label.setTextColor(Color.rgb(16,44,62));
            FrameLayout.LayoutParams title=new FrameLayout.LayoutParams(-1,Math.round(64*activity.getResources().getDisplayMetrics().density),Gravity.TOP);host.addView(label,title);
            tile[0]=new AttachmentImageTile(activity,uri,attachment,()->tileOpens++,()->tileRemoves++);int side=Math.round(260*activity.getResources().getDisplayMetrics().density);
            host.addView(tile[0],new FrameLayout.LayoutParams(side,side,Gravity.CENTER));activity.setContentView(host);
        });
        ImageView preview=(ImageView)field(tile[0],"preview");waitFor(()->preview.getDrawable()!=null&&preview.getWidth()>0&&preview.getAlpha()>.99f,"shared tile decodes its real imported UUID attachment");sourceReady("shared tile component ready");
        tileTwoFingerHold(preview);tileOpens=tileRemoves=0;
        drop(TILE_COMPONENT,preview,uri,other,jpeg,"image/jpeg",false);
        outcomes.put(new JSONObject().put("path","attachment-tile-component-callbacks").put("scope","shared component real touch; not MainActivity/TaskDetailActivity workflow").put("dropOpenCallbacks",0).put("dropRemoveCallbacks",0).put("nextPreviewTap",tileOpens));
        close();
    }
    private void tileTwoFingerHold(ImageView preview)throws Exception{
        DragObserver observer=observe(preview);Rect r=bounds(preview);float cx=r.exactCenterX(),cy=r.exactCenterY(),span=r.width()*.16f;long down=SystemClock.uptimeMillis();boolean released=false;
        ownPointer(down,MotionEvent.ACTION_DOWN,cx-span,cy);
        try{
            multi(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),cx,cy,span);SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+180);capture("tile-two-finger-held");
            android.util.Log.i("ChronaImageDragCheck","tile two-finger held: platformDragStarts="+observer.starts);
            check(observer.starts==0,"tile component two held real fingers never start platform drag; starts="+observer.starts);
            multi(down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),cx,cy,span);SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+180);capture("tile-after-second-finger-up");
            android.util.Log.i("ChronaImageDragCheck","tile second finger lifted, first held: platformDragStarts="+observer.starts);
            check(observer.starts==0,"tile component lifting back to one held finger never rearms drag in the same gesture; starts="+observer.starts);
            ownPointer(down,MotionEvent.ACTION_UP,cx-span,cy);released=true;test.waitForIdleSync();
            outcomes.put(new JSONObject().put("path","attachment-tile-component-multitouch").put("twoFingerHeldNoDrag",true).put("secondFingerUpStillNoDrag",true));
        }finally{if(!released)ownPointer(down,MotionEvent.ACTION_CANCEL,cx-span,cy);}
    }
    private void tap(View view)throws Exception{
        Rect r=bounds(view);long down=SystemClock.uptimeMillis();ownPointer(down,MotionEvent.ACTION_DOWN,r.exactCenterX(),r.exactCenterY());long downReturned=SystemClock.uptimeMillis();sleepUntil(down+45);long up=SystemClock.uptimeMillis();ownPointer(down,MotionEvent.ACTION_UP,r.exactCenterX(),r.exactCenterY());long upReturned=SystemClock.uptimeMillis();
        JSONObject sample=new JSONObject().put("gesture","tap").put("injection","Instrumentation.sendPointerSync").put("downInjectionMillis",downReturned-down).put("upInjectionMillis",upReturned-up).put("actualHeldMillis",up-down);inputTimings.put(sample);android.util.Log.i("ChronaImageDragCheck",sample.toString());test.waitForIdleSync();saveTimings();SystemClock.sleep(100);
    }
    private void gestureSafeguards()throws Exception{
        openClassroom();ImageView image=readyImage("image");ImageSlidePager pager=pager();DragObserver observer=observe(image);
        swipePage(image,true,true);awaitIndex(pager,1,"real move and held finger still page normally");
        check(observer.starts==0,"movement beyond slop and a long hold never start an external drag");
        swipePage(image,false,false);awaitIndex(pager,0,"reverse swipe restores first page");
        pinch(image);check(observer.starts==0&&!((Boolean)field(pager,"outboundDrag")),"two real fingers and a long hold never start external drag");
        check(((Float)field(activity,"scale"))>1.05f,"two-finger safeguard exercised actual pinch zoom");
        check(((Integer)field(pager,"index"))==0,"two-finger zoom does not page");
        doubleTap(image);check(((Float)field(activity,"scale"))<1.01f,"real double tap returns to fit before cancel check");
        cancelPickup("viewer-cancel",image,observer,true);
        swipePage(image,true,false);awaitIndex(pager,1,"next real swipe restores browsing after cancelled drag");
        capture("gesture-safeguards");outcomes.put(new JSONObject().put("path","gesture-safeguards").put("movementNoDrag",true).put("pinchNoDrag",true).put("cancelRestoresPaging",true));close();
        activity=test.startActivitySync(new Intent(app,ClassroomTranscriptActivity.class).putExtra("account",account).putExtra("lesson",lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Method mode=ClassroomTranscriptActivity.class.getDeclaredMethod("setMode",int.class);mode.setAccessible(true);test.runOnMainSync(()->{try{mode.invoke(activity,1);}catch(Exception failure){throw new RuntimeException(failure);}});
        image=readyImage("slideImage");pager=pager();observer=observe(image);cancelPickup("inline-cancel",image,observer,false);
        swipePage(image,true,false);awaitIndex(pager,1,"inline next real swipe restores browsing after cancellation");
        check(!activity.isFinishing()&&(!(test instanceof ClassroomMonitorInstrumentation monitor)||monitor.lastResumed==activity),"inline pickup UP never opens enlargement");
        outcomes.put(new JSONObject().put("path","inline-cancel").put("noEnlargement",true).put("nextSwipe",true));close();
    }
    private void cancelPickup(String label,ImageView image,DragObserver observer,boolean releaseOnBlank)throws Exception{
        Rect rect=bounds(image);float x=rect.exactCenterX(),y=rect.exactCenterY();long down=SystemClock.uptimeMillis();int before=observer.starts;
        pointer(down,MotionEvent.ACTION_DOWN,x,y);boolean released=false;
        try{
            waitFor(()->observer.starts>before,label+": real long press starts platform drag");
            float finishY=releaseOnBlank?rect.top+rect.height()*.18f:y;
            for(int i=1;i<=8;i++){SystemClock.sleep(24);pointer(down,MotionEvent.ACTION_MOVE,x,y+(finishY-y)*i/8);}
            capture(label+"-held");pointer(down,MotionEvent.ACTION_UP,x,finishY);released=true;
            waitFor(()->observer.ends>0,label+": platform reports ended drag");
            check(!observer.result,label+": unaccepted source drop is cancelled");SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout()+120);
            check(!activity.isFinishing(),label+": pickup UP does not close viewer even over blank area");
            if(test instanceof ClassroomMonitorInstrumentation monitor)check(monitor.lastResumed==activity,label+": pickup UP never launches an enlargement");
            check(((Integer)field(pager(),"index"))==0,label+": external drag movement does not page");
        }finally{if(!released)pointer(down,MotionEvent.ACTION_CANCEL,x,y);}
    }
    private DragObserver observe(ImageView image){DragObserver observer=new DragObserver();test.runOnMainSync(()->image.setOnDragListener(observer));return observer;}
    private ImageSlidePager pager()throws Exception{return (ImageSlidePager)field(activity,activity instanceof AttachmentViewerActivity?"pager":"slidePager");}
    private Rect bounds(View view){Rect rect=new Rect();test.runOnMainSync(()->view.getGlobalVisibleRect(rect));return rect;}
    private void awaitIndex(ImageSlidePager pager,int index,String label)throws Exception{waitFor(()->{try{return ((Integer)field(pager,"index"))==index&&field(pager,"animator")==null;}catch(Exception failure){return false;}},label);check(pager.selection()==index,label);}
    private void swipePage(ImageView image,boolean forward,boolean hold)throws Exception{
        sourceReady("same-UID swipe before DOWN");Rect r=bounds(image);float from=r.left+r.width()*(forward?.76f:.24f),to=r.left+r.width()*(forward?.24f:.76f),y=r.exactCenterY();long down=SystemClock.uptimeMillis();
        ownPointer(down,MotionEvent.ACTION_DOWN,from,y);long downMillis=SystemClock.uptimeMillis()-down;
        if(downMillis>=ViewConfiguration.getLongPressTimeout()){
            JSONObject sample=new JSONObject().put("gesture","swipe").put("downInjectionMillis",downMillis).put("stoppedBeforeMove",true);inputTimings.put(sample);android.util.Log.e("ChronaImageDragCheck",sample.toString());saveTimings();throw new AssertionError("Own-UID swipe DOWN exceeded long-press threshold after stable window; remaining gesture not injected: "+downMillis+"ms");
        }
        for(int i=1;i<=12;i++){SystemClock.sleep(18);ownPointer(down,MotionEvent.ACTION_MOVE,from+(to-from)*i/12,y);}if(hold)SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+200);ownPointer(down,MotionEvent.ACTION_UP,to,y);
        JSONObject sample=new JSONObject().put("gesture","swipe").put("downInjectionMillis",downMillis).put("stoppedBeforeMove",false);inputTimings.put(sample);android.util.Log.i("ChronaImageDragCheck",sample.toString());saveTimings();SystemClock.sleep(400);
    }
    private void doubleTap(View view)throws Exception{
        sourceReady("double tap input window stable");
        // Same native stream and image bounds as ClassroomUiPolishChecks.doubleTap.
        Rect rect=imageTouchBounds(view);var active=test.getUiAutomation().getRootInActiveWindow();
        if(!view.isAttachedToWindow()||active==null||!app.getPackageName().contentEquals(active.getPackageName()))throw new AssertionError("Double tap requires the owned fixture window: attached="+view.isAttachedToWindow()+", finishing="+activity.isFinishing()+", package="+(active==null?"null":active.getPackageName()));
        long base=SystemClock.uptimeMillis();long[] submitted=new long[4],returned=new long[4];
        // Queue the native input stream without waiting for each frame; synchronous UP waits exceed the double-tap timeout.
        for(int i=0;i<4;i++){long at=base+new int[]{0,45,120,165}[i];long remaining=at-SystemClock.uptimeMillis();if(remaining>0)SystemClock.sleep(remaining);MotionEvent event=MotionEvent.obtain(base+(i>=2?120:0),at,i%2==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY(),0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);submitted[i]=SystemClock.uptimeMillis();try{if(!test.getUiAutomation().injectInputEvent(event,false))throw new AssertionError("Owned double tap rejected");}finally{returned[i]=SystemClock.uptimeMillis();event.recycle();}}
        test.waitForIdleSync();
        // Record after the complete stream, without adding work between its native events.
        for(int tap=0;tap<2;tap++){int down=tap*2,up=down+1;JSONObject sample=new JSONObject().put("gesture","doubleTap").put("injection","ClassroomUiPolishChecks fixed native stream").put("tap",tap+1).put("eventDownTime",base+tap*120).put("eventHeldMillis",45).put("eventDownIntervalMillis",tap==0?0:120).put("downInjectionMillis",returned[down]-submitted[down]).put("upInjectionMillis",returned[up]-submitted[up]).put("submittedHeldMillis",submitted[up]-submitted[down]).put("submittedDownIntervalMillis",tap==0?0:submitted[2]-submitted[0]);inputTimings.put(sample);android.util.Log.i("ChronaImageDragCheck",sample.toString());}
        saveTimings();frameBarrier(activity.findViewById(android.R.id.content),2);SystemClock.sleep(100);
    }
    private Rect imageTouchBounds(View view){
        if(!(view instanceof ImageView image)||image.getDrawable()==null)return bounds(view);
        android.graphics.RectF actual=new android.graphics.RectF(0,0,image.getDrawable().getIntrinsicWidth(),image.getDrawable().getIntrinsicHeight());image.getImageMatrix().mapRect(actual);
        if(!actual.intersect(0,0,image.getWidth(),image.getHeight()))throw new AssertionError("No visible image for a picture gesture");
        int[] location=new int[2];image.getLocationOnScreen(location);Rect result=new Rect((int)Math.ceil(actual.left)+location[0],(int)Math.ceil(actual.top)+location[1],(int)Math.floor(actual.right)+location[0],(int)Math.floor(actual.bottom)+location[1]);
        if(!result.intersect(bounds(image)))throw new AssertionError("Picture outside visible viewport");return result;
    }
    private static void sleepUntil(long uptime){long remaining=uptime-SystemClock.uptimeMillis();if(remaining>0)SystemClock.sleep(remaining);}
    private void sourceReady(String label)throws Exception{
        waitFor(()->sourceInteractive(),label+": source is resumed and owns window focus");test.waitForIdleSync();frameBarrier(activity.findViewById(android.R.id.content),2);test.waitForIdleSync();
        check(sourceInteractive(),label+": source remains resumed/focused after two rendered frames");
        if(pendingWindowTransition){
            // Focus returns before the platform's outgoing-Activity animation transaction.
            // Let its 600ms maximum finish before taking the next gesture's downTime.
            SystemClock.sleep(600);test.waitForIdleSync();frameBarrier(activity.findViewById(android.R.id.content),2);
            check(sourceInteractive(),label+": outgoing Activity transition settled before new input");pendingWindowTransition=false;
        }
    }
    private boolean sourceInteractive(){
        final boolean[] ready={false};test.runOnMainSync(()->ready[0]=activity!=null&&!activity.isFinishing()&&lifecycle.resumed==activity&&activity.hasWindowFocus()&&activity.getWindow().getDecorView().isShown());return ready[0];
    }
    private void frameBarrier(View view,int frames)throws Exception{
        CountDownLatch done=new CountDownLatch(1);test.runOnMainSync(()->view.postOnAnimation(new Runnable(){int left=frames;public void run(){if(--left<=0)done.countDown();else view.postOnAnimation(this);}}));
        check(done.await(3,TimeUnit.SECONDS),"requested display-frame barrier completed");
    }
    private void failureEvidence(Throwable failure){
        try{
            JSONObject state=new JSONObject().put("failure",failure.toString()).put("resumed",lifecycle.resumed==activity).put("activity",activity==null?"null":activity.getClass().getSimpleName()).put("inputTimings",inputTimings).put("completedPaths",outcomes);
            if(activity!=null){final boolean[] focus={false};test.runOnMainSync(()->focus[0]=activity.hasWindowFocus());state.put("windowFocus",focus[0]).put("finishing",activity.isFinishing());
                if(activity instanceof AttachmentViewerActivity)state.put("scale",field(activity,"scale"));ImageSlidePager p=pager();for(String name:List.of("outboundDrag","index","tracking","multiTouch","blocked","offset","animator"))state.put(name,String.valueOf(field(p,name)));}
            android.util.Log.e("ChronaImageDragCheck",state.toString());ClassroomStore.write(new File(app.getExternalFilesDir(null),"monitor-validation/image-drag-failure.json"),state);capture("failure");
        }catch(Exception evidenceFailure){android.util.Log.e("ChronaImageDragCheck","diagnostic failure",evidenceFailure);}
    }
    private void saveTimings(){try{ClassroomStore.write(new File(app.getExternalFilesDir(null),"monitor-validation/image-drag-input-timings.json"),new JSONObject().put("events",inputTimings));}catch(Exception failure){android.util.Log.e("ChronaImageDragCheck","timing write",failure);}}
    private void pinch(ImageView image)throws Exception{
        Rect r=bounds(image);float cx=r.exactCenterX(),cy=r.exactCenterY(),span=r.width()*.12f;long down=SystemClock.uptimeMillis();
        ownPointer(down,MotionEvent.ACTION_DOWN,cx-span,cy);multi(down,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),cx,cy,span);
        for(int i=1;i<=12;i++){SystemClock.sleep(22);multi(down,MotionEvent.ACTION_MOVE,cx,cy,span*(1+i/12f));}
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+180);multi(down,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),cx,cy,span*2);ownPointer(down,MotionEvent.ACTION_UP,cx-span*2,cy);SystemClock.sleep(180);
    }
    private void multi(long down,int action,float cx,float cy,float span){
        MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
        for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i].x=cx+(i==0?-span:span);coords[i].y=cy;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);try{test.sendPointerSync(event);}finally{event.recycle();}
    }
    private static final class DragObserver implements View.OnDragListener {
        volatile int starts,ends;volatile boolean result;
        @Override public boolean onDrag(View view,DragEvent event){if(event.getAction()==DragEvent.ACTION_DRAG_STARTED){starts++;return true;}if(event.getAction()==DragEvent.ACTION_DRAG_ENDED){ends++;result=event.getResult();}return event.getAction()!=DragEvent.ACTION_DROP;}
    }
    private void ownPointer(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{test.sendPointerSync(event);}finally{event.recycle();}}
    private void pointer(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{check(test.getUiAutomation().injectInputEvent(event,true),"cross-window system touchscreen event accepted");}finally{event.recycle();}}
    private void capture(String label)throws Exception{
        Bitmap original=test.getUiAutomation().takeScreenshot();if(original==null)throw new AssertionError("Screenshot unavailable");
        float ratio=Math.min(1f,900f/Math.max(original.getWidth(),original.getHeight()));Bitmap small=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*ratio)),Math.max(1,Math.round(original.getHeight()*ratio)),true);
        File dir=new File(app.getExternalFilesDir(null),"monitor-validation");dir.mkdirs();try(FileOutputStream output=new FileOutputStream(new File(dir,"image-drag-"+label+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,78,output);}finally{if(small!=original)small.recycle();original.recycle();}
    }
    private void close(){if(activity!=null){Activity old=activity;test.runOnMainSync(old::finish);activity=null;pendingWindowTransition=true;test.waitForIdleSync();}}
    private void write(File file,byte[] bytes)throws Exception{try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);}files.add(file);}
    private static byte[] picture(Bitmap.CompressFormat format,int color){Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(format,90,bytes);bitmap.recycle();return bytes.toByteArray();}
    private static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private void waitFor(BooleanSupplier ready,String label)throws Exception{long until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){test.waitForIdleSync();if(ready.getAsBoolean())return;SystemClock.sleep(50);}throw new AssertionError(label);}
    private void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    private static final class Reports extends ResultReceiver {
        final CountDownLatch ready=new CountDownLatch(1),started=new CountDownLatch(1),dropped=new CountDownLatch(1),drawn=new CountDownLatch(1);volatile Bundle readyData,startData,dropData;
        Reports(){super(new Handler(Looper.getMainLooper()));}
        @Override protected void onReceiveResult(int code,Bundle data){if(code==1){readyData=data;ready.countDown();}else if(code==2){startData=data;started.countDown();}else if(code==3){dropData=data;dropped.countDown();}else if(code==4)drawn.countDown();}
    }
    private static final class Lifecycle implements Application.ActivityLifecycleCallbacks {
        volatile Activity resumed;
        public void onActivityResumed(Activity activity){resumed=activity;}public void onActivityPaused(Activity activity){if(resumed==activity)resumed=null;}
        public void onActivityCreated(Activity activity,Bundle state){}public void onActivityStarted(Activity activity){}public void onActivityStopped(Activity activity){}public void onActivitySaveInstanceState(Activity activity,Bundle state){}public void onActivityDestroyed(Activity activity){}
    }
}
