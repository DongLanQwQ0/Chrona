package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.image.ImageStore;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Isolated normal-screen attachment readiness and stable-preview checks; no AI or school calls. */
final class AttachmentPageMotionChecks {
    private final ClassroomMonitorInstrumentation test;
    private final Bundle args;
    private final Context app;
    private final ArrayList<String> names=new ArrayList<>();
    private final JSONArray report=new JSONArray();
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    private volatile Activity screen;
    private long task;
    private int checks;
    private CountDownLatch decodeGate=new CountDownLatch(1);
    private boolean holdViewer;
    private volatile int imageFrames;
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity activity,Bundle state){}
        public void onActivityPostCreated(Activity activity,Bundle state){
            if(!(activity instanceof AttachmentViewerActivity))return;
            ImageSlidePager pager=(ImageSlidePager)get(activity,"pager");
            if(holdViewer){
                // Replace the source before the main thread can deliver the initial decode.
                // This invalidates its generation, not the production presentation callback.
                CountDownLatch gate=decodeGate;
                pager.setImages(List.of(names.get(0)),0,new ImageSlidePager.Source(){
                    public File file(String name)throws Exception{gate.await();return new ImageStore(app).fileFor(name);}
                    public boolean allowed(){return true;}
                });
            }
            pager.getViewTreeObserver().addOnPreDrawListener(()->{
                if(pager.ready()&&pager.getVisibility()==View.VISIBLE&&pager.getAlpha()>0f
                        &&pager.getAlpha()<1f&&pager.getTranslationY()>0f)imageFrames++;
                return true;
            });
        }
        public void onActivityStarted(Activity activity){}
        public void onActivityResumed(Activity activity){screen=activity;}
        public void onActivityPaused(Activity activity){}
        public void onActivityStopped(Activity activity){}
        public void onActivitySaveInstanceState(Activity activity,Bundle state){}
        public void onActivityDestroyed(Activity activity){}
    };
    private AttachmentPageMotionChecks(ClassroomMonitorInstrumentation test,Bundle args){
        this.test=test;this.args=args;app=test.getTargetContext();
    }
    static int run(ClassroomMonitorInstrumentation test,Bundle args)throws Exception{
        AttachmentPageMotionChecks c=new AttachmentPageMotionChecks(test,args);
        if(!c.app.getPackageName().equals("com.donglan.chrona.monitor.validation")
                ||!Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned AVD fixture only");
        Application application=(Application)c.app.getApplicationContext();
        application.registerActivityLifecycleCallbacks(c.lifecycle);
        try{c.seed();c.viewer();c.captureList();c.detailGallery();c.reduced();return c.checks;}
        catch(Throwable failure){c.capture("failure");throw failure;}
        finally{
            c.decodeGate.countDown();
            test.runOnMainSync(()->{if(c.screen!=null&&!c.screen.isDestroyed())c.screen.finish();});
            test.waitForIdleSync();
            application.unregisterActivityLifecycleCallbacks(c.lifecycle);
            try(TaskStore store=new TaskStore(c.app)){if(c.task>0)store.deleteTask(c.task);}
            for(String name:c.names)new ImageStore(c.app).delete(name);
            for(var entry:c.saved.entrySet())restore(c.app.getSharedPreferences(entry.getKey(),0),entry.getValue());
            File dir=c.output();java.nio.file.Files.write(new File(dir,"report.json").toPath(),c.report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private void seed()throws Exception{
        for(String name:List.of("appearance","capture_draft","startup_permissions","release_updates"))saved.put(name,new HashMap<>(app.getSharedPreferences(name,0).getAll()));
        app.getSharedPreferences("appearance",0).edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();
        app.getSharedPreferences("startup_permissions",0).edit().putBoolean("attempted",true).commit();
        app.getSharedPreferences("release_updates",0).edit().putBoolean("automatic",false).commit();
        File source=new File(app.getCacheDir(),"attachment-page-fixture-"+System.nanoTime()+".jpg");
        Bitmap bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(224,173,79));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.rgb(35,42,47));paint.setTextSize(74);canvas.drawText("ATTACHMENT 01",90,170,paint);canvas.drawRect(90,230,1150,620,paint);
        try(FileOutputStream stream=new FileOutputStream(source)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream);}finally{bitmap.recycle();}
        try{for(int i=0;i<3;i++)names.add(new ImageStore(app).importImage(android.net.Uri.fromFile(source)));}finally{source.delete();}
    }
    private void viewer()throws Exception{
        holdViewer=true;imageFrames=0;
        open(new Intent(app,AttachmentViewerActivity.class).putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME,names.get(0)));
        SystemClock.sleep(750);ImageSlidePager pager=(ImageSlidePager)get(screen,"pager");
        check(!pager.ready()&&pager.getVisibility()==View.INVISIBLE&&(Boolean)get(screen,"firstImagePending"),"700ms late first bitmap stays hidden and unconsumed");capture("viewer-held");
        Activity beforeReady=screen;test.runOnMainSync(beforeReady::recreate);await(()->screen!=beforeReady&&screen instanceof AttachmentViewerActivity);SystemClock.sleep(750);
        pager=(ImageSlidePager)get(screen,"pager");
        check(!pager.ready()&&pager.getVisibility()==View.INVISIBLE&&(Boolean)get(screen,"firstImagePending"),"recreate before bitmap presentation preserves the pending first entrance");capture("viewer-held-recreated");
        decodeGate.countDown();await(()->imageFrames>0);capture("viewer-image-entered");
        await(()->!(Boolean)get(screen,"firstImagePending"));SystemClock.sleep(350);pager=(ImageSlidePager)get(screen,"pager");
        check(pager.ready()&&pager.getVisibility()==View.VISIBLE&&pager.getAlpha()==1f&&pager.getTranslationY()==0f,"ready first bitmap ends at the visible identity state");capture("viewer-settled");
        imageFrames=0;Activity old=screen;test.runOnMainSync(old::recreate);await(()->screen!=old&&screen instanceof AttachmentViewerActivity);await(()->((ImageSlidePager)get(screen,"pager")).ready());SystemClock.sleep(350);
        check(!(Boolean)get(screen,"firstImagePending")&&imageFrames==0,"already presented bitmap restores without another image entrance");
        holdViewer=false;close();
    }
    @SuppressWarnings("unchecked") private void captureList()throws Exception{
        new CaptureDraftStore(app).save("附件稳定节点验收",new ArrayList<>(names.subList(0,2)),List.of());
        open(new Intent(app,MainActivity.class));await(()->((LinearLayout)get(screen,"pendingImagesContainer")).getChildCount()==2);
        LinearLayout list=(LinearLayout)get(screen,"pendingImagesContainer");View first=list.getChildAt(0),second=list.getChildAt(1);
        ImageView preview=(ImageView)((ViewGroup)second).getChildAt(0);await(()->preview.getDrawable()!=null);Object drawable=preview.getDrawable();
        test.runOnMainSync(()->{preview.setFocusableInTouchMode(true);preview.requestFocus();});
        List<String> pending=(List<String>)get(screen,"pendingImages");
        test.runOnMainSync(()->{pending.add(names.get(2));invoke(screen,"showPendingImages");});
        check(list.getChildAt(0)==first&&list.getChildAt(1)==second&&preview.getDrawable()==drawable&&preview.hasFocus(),"append retains previous tile, bitmap and current focus");
        test.runOnMainSync(()->{pending.remove(names.get(0));invoke(screen,"showPendingImages");});
        check(list.getChildAt(0)==second&&preview.getDrawable()==drawable&&preview.hasFocus(),"remove retains the other tile, bitmap and current focus");
        test.runOnMainSync(()->invoke(screen,"showPendingImages"));
        check(list.getChildAt(0)==second&&preview.getDrawable()==drawable,"unchanged refresh retains exact view and drawable identities");
        SystemClock.sleep(450);capture("capture-stable-previews");
        for(int level=1;level<=5;level++){
            app.getSharedPreferences("appearance",0).edit().putInt("corner_level",level).commit();
            Outline outline=new Outline();test.runOnMainSync(()->second.getOutlineProvider().getOutline(second,outline));
            float wanted=UiStyle.radius(app,UiStyle.RADIUS_CARD)*second.getResources().getDisplayMetrics().density;
            check(Math.abs(outline.getRadius()-wanted)<=.6f,"actual tile outline follows public corner level "+level);
        }
        app.getSharedPreferences("appearance",0).edit().putInt("corner_level",4).commit();
        check(new CaptureDraftStore(app).load().images.equals(pending),"stable reconciliation still persists the changed draft");close();
    }
    private void detailGallery()throws Exception{
        try(TaskStore store=new TaskStore(app)){task=store.insertTask("附件圆角验收",names.get(0),"attachment-motion-fixture",System.currentTimeMillis());store.addImageAttachment(task,names.get(1));store.updateStatus(task,TaskRecord.READY,null);}
        open(new Intent(app,TaskDetailActivity.class).putExtra("task_id",task));await(()->get(screen,"galleryGesturePriorityChild")!=null);
        ViewGroup gallery=(ViewGroup)get(screen,"galleryGesturePriorityChild");ViewGroup viewport=(ViewGroup)gallery.getParent();
        check(!viewport.getClipToOutline()&&viewport.getClipChildren()&&viewport.getClipToPadding(),"detail gallery keeps viewport clipping without a second rounded outline");
        ViewGroup images=(ViewGroup)gallery.getChildAt(0);check(images.getChildCount()==2&&images.getChildAt(0).getClipToOutline(),"each image retains its one shared rounded clipping layer");
        SystemClock.sleep(500);capture("detail-gallery");close();
    }
    private void reduced()throws Exception{
        String scale=android.provider.Settings.Global.getString(app.getContentResolver(),"animator_duration_scale");
        try{
            test.getUiAutomation().executeShellCommand("settings put global animator_duration_scale 0").close();await(()->!UiMotion.isEnabled());
            decodeGate=new CountDownLatch(1);holdViewer=true;imageFrames=0;
            open(new Intent(app,AttachmentViewerActivity.class).putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME,names.get(0)));SystemClock.sleep(750);
            check(((ImageSlidePager)get(screen,"pager")).getVisibility()==View.INVISIBLE&&(Boolean)get(screen,"firstImagePending"),"reduced motion still waits for the actual first bitmap");
            decodeGate.countDown();await(()->!(Boolean)get(screen,"firstImagePending"));ImageSlidePager pager=(ImageSlidePager)get(screen,"pager");
            check(pager.ready()&&pager.getVisibility()==View.VISIBLE&&pager.getAlpha()==1f&&pager.getTranslationY()==0f&&imageFrames==0,"reduced motion presents the ready bitmap directly at the identity state");
            SystemClock.sleep(650);capture("viewer-reduced");holdViewer=false;close();
        }finally{test.getUiAutomation().executeShellCommand("settings put global animator_duration_scale "+(scale==null?"1":scale)).close();}
    }
    private void open(Intent intent)throws Exception{
        ComponentName expected=intent.getComponent();
        test.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->{
            Activity current=test.lastResumed;
            if(current==null||!expected.equals(current.getComponentName())||current.isDestroyed()
                    ||current.isFinishing()||!current.hasWindowFocus()||!current.getWindow().getDecorView().isShown())return false;
            screen=current;return true;
        });
    }
    private void close(){Activity closing=screen;test.runOnMainSync(closing::finish);SystemClock.sleep(400);}
    private void await(BooleanSupplier condition)throws Exception{
        long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};test.runOnMainSync(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(16);}throw new AssertionError("Timed out waiting for attachment fixture state");
    }
    private void check(boolean good,String message)throws Exception{if(!good)throw new AssertionError(message);checks++;report.put(new JSONObject().put("check",message).put("pass",true));}
    private static Object get(Object owner,String name){try{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void invoke(Object owner,String name){try{Method method=owner.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(owner);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private File output(){File dir=new File(app.getExternalFilesDir(null),"attachment-page-motion/"+args.getString("orientation","portrait")+"-"+args.getString("theme","light"));dir.mkdirs();return dir;}
    private void capture(String name)throws Exception{
        Bitmap original=test.getUiAutomation().takeScreenshot();if(original==null)return;
        float ratio=Math.min(1f,1000f/Math.max(original.getWidth(),original.getHeight()));Bitmap small=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*ratio)),Math.max(1,Math.round(original.getHeight()*ratio)),true);
        try(FileOutputStream stream=new FileOutputStream(new File(output(),name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,78,stream);}finally{if(small!=original)small.recycle();original.recycle();}
    }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs,Map<String,?> values){
        SharedPreferences.Editor edit=prefs.edit().clear();for(var entry:values.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String v)edit.putString(key,v);else if(value instanceof Boolean v)edit.putBoolean(key,v);else if(value instanceof Integer v)edit.putInt(key,v);else if(value instanceof Long v)edit.putLong(key,v);else if(value instanceof Float v)edit.putFloat(key,v);else if(value instanceof Set<?>)edit.putStringSet(key,(Set<String>)value);}edit.commit();
    }
}
