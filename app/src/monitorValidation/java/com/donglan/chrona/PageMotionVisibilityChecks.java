package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real existing split-window probe. No new task, school fixtures or model requests. */
final class PageMotionVisibilityChecks {
    private final ClassroomMonitorInstrumentation test;private final Bundle args;private final Context app;
    private final File output;private final JSONArray evidence=new JSONArray();
    private Activity screen;private FrameLayout host;private LinearLayout probe;private int checks;
    private String originalScale;private boolean scaleSaved;
    private final AtomicInteger presented=new AtomicInteger();private final AtomicBoolean unfocusedCallback=new AtomicBoolean();
    private PageMotionVisibilityChecks(ClassroomMonitorInstrumentation test,Bundle args){
        this.test=test;this.args=args;app=test.getTargetContext();String run=args.getString("runId","native");
        if(!run.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Safe runId required");
        output=new File(app.getFilesDir(),"page-motion-visibility/"+run);
    }
    static int run(ClassroomMonitorInstrumentation test,Bundle args)throws Exception{
        PageMotionVisibilityChecks c=new PageMotionVisibilityChecks(test,args);
        if(!c.app.getPackageName().equals("com.donglan.chrona.monitor.validation")||!Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned isolated AVD only");
        if(!"true".equals(args.getString("nativeScreen"))||!args.containsKey("focusX")||!args.containsKey("focusY"))throw new IllegalArgumentException("nativeScreen=true and verified Settings gap focusX/focusY required");
        Files.createDirectories(c.output.toPath());
        try{c.attach();c.focusSettings();c.exercise();return c.checks;}
        catch(Throwable failure){c.record("failure");c.capture("failure");throw failure;}
        finally{
            try{if(c.scaleSaved){c.scale(c.originalScale);c.await(()->UiMotion.isEnabled()==(c.originalScale==null||Float.parseFloat(c.originalScale)!=0),"restore animator scale");}}
            finally{
                test.runOnMainSync(()->{if(c.probe!=null)UiMotion.finishSwap(c.probe);if(c.host!=null&&c.host.getParent() instanceof ViewGroup parent)parent.removeView(c.host);});
                Files.write(new File(c.output,"report.json").toPath(),new JSONObject().put("checks",c.checks).put("evidence",c.evidence)
                        .put("animatorScaleSaved",c.scaleSaved).put("originalAnimatorScale",c.originalScale==null?JSONObject.NULL:c.originalScale)
                        .put("restoredAnimatorScale",Objects.requireNonNullElse(android.provider.Settings.Global.getString(c.app.getContentResolver(),"animator_duration_scale"),JSONObject.NULL))
                        .put("paid_calls",0).put("new_tasks",0).put("output",c.output.getAbsolutePath()).toString(2).getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    private void attach()throws Exception{
        test.runOnMainSync(()->{Activity current=test.lastResumed;if(current instanceof MainActivity&&!current.isDestroyed()&&!current.isFinishing()
                &&(!args.containsKey("taskId")||current.getTaskId()==Integer.parseInt(args.getString("taskId"))))screen=current;});
        int existingTask=screen==null?-1:screen.getTaskId();
        if(screen==null){
            List<ActivityManager.AppTask> existing=new ArrayList<>();int requested=args.containsKey("taskId")?Integer.parseInt(args.getString("taskId")):-1;
            for(ActivityManager.AppTask task:app.getSystemService(ActivityManager.class).getAppTasks()){
                ActivityManager.RecentTaskInfo info=task.getTaskInfo();
                if(requested<0||info.taskId==requested)existing.add(task);
            }
            if(existing.size()!=1)throw new AssertionError("Need exactly one existing validation task; found "+existing.size()+". No task or system mode created.");
            ActivityManager.RecentTaskInfo before=existing.get(0).getTaskInfo();int taskId=before.taskId;existingTask=taskId;
            Intent launch=new Intent(app,MainActivity.class);evidence.put(new JSONObject().put("phase","reuse-existing-task").put("taskId",taskId)
                    .put("visibleBefore",before.isVisible()).put("launchFlags",launch.getFlags()));
            existing.get(0).startActivity(app,launch,null); // Instrumentation may kill visibility, but never the existing task identity.
            await(()->test.lastResumed instanceof MainActivity&&!test.lastResumed.isDestroyed()&&!test.lastResumed.isFinishing()
                    &&test.lastResumed.getTaskId()==taskId,"existing-task MainActivity resume");screen=test.lastResumed;
        }
        final int taskId=existingTask;check(screen.getTaskId()==taskId,"MainActivity reuses the exact existing task ID without NEW_TASK");
        Files.write(new File(output,"ready-for-split.json").toPath(),new JSONObject().put("state","waiting-for-real-split")
                .put("taskId",taskId).put("readyAt",System.currentTimeMillis()).put("waitMillis",90000).toString(2).getBytes(StandardCharsets.UTF_8));
        record("ready-for-split");
        await(()->{
            Activity current=test.lastResumed;
            if(current instanceof MainActivity&&current.getTaskId()==taskId&&!current.isDestroyed()&&!current.isFinishing())screen=current;
            View decor=screen.getWindow().getDecorView();return !screen.isDestroyed()&&!screen.isFinishing()&&screen.isInMultiWindowMode()
                    &&decor.isAttachedToWindow()&&decor.getWidth()>0&&decor.getHeight()>0&&"com.android.settings".equals(focusedPackage());
        },"SystemUI must pair the existing task with focused Settings in real split screen within 90 seconds",90000);
        check(screen.getTaskId()==taskId,"SystemUI split retains the same task identity");
        check(screen.isInMultiWindowMode(),"activity reports real Android multi-window mode");
        test.runOnMainSync(()->{
            host=new FrameLayout(screen);probe=new LinearLayout(screen);probe.setOrientation(LinearLayout.VERTICAL);UiStyle.card(probe);
            int inset=dp(16);probe.setPadding(inset,inset,inset,inset);TextView title=new TextView(screen);title.setText("首次呈现 · 分屏无焦点");title.setTextSize(16);UiStyle.title(title);probe.addView(title);
            FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);position.setMargins(dp(20),dp(20),dp(20),dp(20));host.addView(probe,position);
            ((FrameLayout)screen.findViewById(android.R.id.content)).addView(host,new FrameLayout.LayoutParams(-1,-1));
        });
        await(()->probe.isAttachedToWindow()&&probe.isLaidOut()&&probe.getWidth()>0&&probe.getHeight()>0&&probe.isShown(),"real attached probe layout");
        record("attached");capture("attached");
    }
    private void focusSettings()throws Exception{
        int x=Integer.parseInt(args.getString("focusX")),y=Integer.parseInt(args.getString("focusY"));Rect bounds=new Rect();
        test.runOnMainSync(()->screen.getWindow().getDecorView().getGlobalVisibleRect(bounds));
        check(y>bounds.bottom&&!bounds.contains(x,y),"verified Settings gap lies below the target window");
        shell("input tap "+x+" "+y);
        await(()->!screen.hasWindowFocus()&&"com.android.settings".equals(focusedPackage()),"Settings focus without leaving split screen");
        check(screen.isInMultiWindowMode()&&probe.isShown(),"unfocused target remains genuinely visible in multi-window");record("settings-focused");capture("settings-focused");
    }
    private void exercise()throws Exception{
        originalScale=android.provider.Settings.Global.getString(app.getContentResolver(),"animator_duration_scale");scaleSaved=true;
        if(!UiMotion.isEnabled()){scale("1");await(UiMotion::isEnabled,"enabled animation control");}
        start();await(()->presented.get()==1&&identity(),"unfocused multi-window presentation");frames();
        check(presented.get()==1&&unfocusedCallback.get()&&!screen.hasWindowFocus(),"visible unfocused split invokes onPresented exactly once");
        check(identity(),"unfocused entrance settles to alpha 1 and translation 0");record("unfocused-presented");capture("unfocused-presented");

        pending();frames();check(presented.get()==0&&probe.getVisibility()==View.GONE,"GONE attached probe waits without consuming presentation");record("cancel-pending");
        test.runOnMainSync(()->{UiMotion.finishSwap(probe);probe.setVisibility(View.VISIBLE);});frames();SystemClock.sleep(400);frames();
        check(presented.get()==0&&identity(),"finishSwap cancels waiting GONE entrance without a late callback");record("cancelled-restored");capture("cancelled-restored");

        pending();frames();check(presented.get()==0,"second GONE entrance is still pending before detachment");record("detach-pending");
        test.runOnMainSync(()->host.removeView(probe));
        check(!probe.isAttachedToWindow()&&presented.get()==0&&identity(),"detach cancels pending entry and restores identity immediately");record("detached");
        test.runOnMainSync(()->{probe.setVisibility(View.VISIBLE);FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);position.setMargins(dp(20),dp(20),dp(20),dp(20));host.addView(probe,position);});
        frames();SystemClock.sleep(400);frames();check(presented.get()==0&&identity(),"reattachment never runs the detached entrance callback");record("detached-restored");capture("detached-restored");

        scale("0");await(()->!UiMotion.isEnabled(),"system reduced animations");pending();frames();
        check(presented.get()==0,"reduced animations still defer a GONE probe");record("reduced-pending");test.runOnMainSync(()->probe.setVisibility(View.VISIBLE));
        await(()->presented.get()==1&&identity(),"reduced visible presentation");frames();
        check(presented.get()==1&&unfocusedCallback.get()&&!screen.hasWindowFocus()&&screen.isInMultiWindowMode(),"reduced unfocused split presents once without residual transform");
        record("reduced-presented");capture("reduced-presented");
    }
    private void start(){test.runOnMainSync(()->{presented.set(0);unfocusedCallback.set(false);UiMotion.enterPage(probe,()->{presented.incrementAndGet();unfocusedCallback.set(screen.isInMultiWindowMode()&&!screen.hasWindowFocus());});});}
    private void pending(){test.runOnMainSync(()->{UiMotion.finishSwap(probe);probe.setVisibility(View.GONE);});start();}
    private boolean identity(){return probe.getAlpha()==1f&&probe.getTranslationX()==0f&&probe.getTranslationY()==0f;}
    private void frames()throws Exception{
        View decor=screen.getWindow().getDecorView();CountDownLatch drawn=new CountDownLatch(2);AtomicInteger count=new AtomicInteger();
        android.view.ViewTreeObserver.OnDrawListener observer=()->{int n=count.incrementAndGet();if(n<=2)decor.post(drawn::countDown);if(n<2)decor.postInvalidateOnAnimation();};
        test.runOnMainSync(()->{decor.getViewTreeObserver().addOnDrawListener(observer);decor.invalidate();});
        try{if(!drawn.await(5,TimeUnit.SECONDS))throw new AssertionError("actual unfocused-window frame timeout");}
        finally{test.runOnMainSync(()->{if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnDrawListener(observer);});}
        test.waitForIdleSync();
    }
    private void await(BooleanSupplier condition,String label){await(condition,label,10000);}
    private void await(BooleanSupplier condition,String label,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] value={false};test.runOnMainSync(()->value[0]=condition.getAsBoolean());if(value[0])return;SystemClock.sleep(25);}throw new AssertionError(label);}
    private void check(boolean value,String label)throws JSONException{checks++;evidence.put(new JSONObject().put("case",label).put("passed",value));if(!value)throw new AssertionError(label);}
    private void record(String phase)throws JSONException{
        test.runOnMainSync(()->{try{Rect visible=new Rect();boolean shown=probe!=null&&probe.getGlobalVisibleRect(visible);
            evidence.put(new JSONObject().put("phase",phase).put("taskId",screen==null?-1:screen.getTaskId()).put("multiWindow",screen!=null&&screen.isInMultiWindowMode())
                    .put("windowFocus",screen!=null&&screen.hasWindowFocus()).put("focusedPackage",focusedPackage()).put("attached",probe!=null&&probe.isAttachedToWindow())
                    .put("shown",probe!=null&&probe.isShown()).put("visibility",probe==null?-1:probe.getVisibility()).put("windowVisibility",probe==null?-1:probe.getWindowVisibility())
                    .put("width",probe==null?0:probe.getWidth()).put("height",probe==null?0:probe.getHeight())
                    .put("visibleBounds",shown?visible.toShortString():"").put("presented",presented.get()).put("unfocusedCallback",unfocusedCallback.get())
                    .put("alpha",probe==null?JSONObject.NULL:probe.getAlpha()).put("translationY",probe==null?JSONObject.NULL:probe.getTranslationY())
                    .put("animatorScale",android.provider.Settings.Global.getString(app.getContentResolver(),"animator_duration_scale"))
                    .put("densityDpi",app.getResources().getDisplayMetrics().densityDpi));
        }catch(JSONException failure){throw new AssertionError(failure);}});
    }
    private String focusedPackage(){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root==null)return "";try{return String.valueOf(root.getPackageName());}finally{root.recycle();}}
    private void scale(String value)throws Exception{if(value==null)shell("settings delete global animator_duration_scale");else{Float.parseFloat(value);if(!value.matches("[0-9]+(?:\\.[0-9]+)?"))throw new IllegalArgumentException("Unexpected animator scale");shell("settings put global animator_duration_scale "+value);}}
    private void shell(String command)throws IOException{try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand(command))){stream.readAllBytes();}}
    private int dp(int value){return Math.round(value*screen.getResources().getDisplayMetrics().density);}
    private void capture(String name)throws IOException{Bitmap raw=test.getUiAutomation().takeScreenshot();if(raw==null)throw new IOException("No native screenshot");float ratio=Math.min(1,1400f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*ratio),Math.round(raw.getHeight()*ratio),true);try(FileOutputStream stream=new FileOutputStream(new File(output,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,72,stream);}finally{if(small!=raw)small.recycle();raw.recycle();}}
}
