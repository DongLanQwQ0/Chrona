package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.lang.reflect.Field;

/** Hardware-rendered synthetic surfaces. No task, account or calendar writes. */
final class AcrylicMotionDeviceCheck {
    static String runHistory(Instrumentation test) throws Exception {
        android.content.Context context=test.getTargetContext();
        if(!context.getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated followup validation package only");
        android.content.SharedPreferences guide=context.getSharedPreferences("usage_guide",0);
        int guideBefore=guide.getInt("version",0);
        Field version=GuideActivity.class.getDeclaredField("GUIDE_VERSION");version.setAccessible(true);
        guide.edit().putInt("version",version.getInt(null)).commit();
        long[] tasks=new long[3],ids=new long[3];Activity activity=null;
        try {
            long now=System.currentTimeMillis();
            try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(context)){
                for(int i=0;i<3;i++){
                    tasks[i]=store.insertTask("背景同步验收 "+i,null,"device_check",now);
                    ids[i]=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,tasks[i],"背景同步验收 "+i,
                            now+(i+1)*3600000L,now+(i+1)*3600000L+1800000,java.time.ZoneId.systemDefault().getId(),"","",
                            null,false,null,"task",false));
                    store.updateStatus(tasks[i],com.donglan.chrona.data.TaskRecord.READY,null);
                }
            }
            activity=test.startActivitySync(new Intent(context,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(DashboardActivity.EXTRA_SECTION,DashboardActivity.SCHEDULE));
            Activity screen=activity;
            await(test,()->find(screen.getWindow().getDecorView(),ids[0])!=null&&find(screen.getWindow().getDecorView(),ids[2])!=null,"fixture rows appear");
            SystemClock.sleep(400);
            View first=find(screen.getWindow().getDecorView(),ids[0]),second=find(screen.getWindow().getDecorView(),ids[1]);
            GlassBackdropView backdrop=GlassBackdropView.findFor(second);
            Bitmap wallpaper=Bitmap.createBitmap(backdrop.getWidth(),backdrop.getHeight(),Bitmap.Config.ARGB_8888);
            for(int y=0;y<wallpaper.getHeight();y++)for(int x=0;x<wallpaper.getWidth();x++)wallpaper.setPixel(x,y,Color.rgb(y*255/wallpaper.getHeight(),0,255-y*255/wallpaper.getHeight()));
            test.runOnMainSync(()->{try{set(backdrop,"blurredWallpaper",wallpaper);set(backdrop,"requestedBlurStrength",ThemeStore.blurStrength(context));set(backdrop,"requestedGaussian",ThemeStore.gaussianBlur(context));UiStyle.invalidateAcrylicSurfaces(screen.getWindow().getDecorView());}catch(Exception e){throw new AssertionError(e);}});
            SystemClock.sleep(200);int before=sample(test,second);int[] beforeXY=new int[2];second.getLocationOnScreen(beforeXY);
            test.runOnMainSync(()->{View button=completion(first);require(button!=null&&button.performClick(),"real completion click");});
            await(test,()->find(screen.getWindow().getDecorView(),ids[0])==null,"completed row removed");
            SystemClock.sleep(600);
            require(find(screen.getWindow().getDecorView(),ids[1])==second,"surviving row retained");
            int[] afterXY=new int[2];second.getLocationOnScreen(afterXY);require(afterXY[1]<beforeXY[1],"surviving row moved upward");
            int after=sample(test,second);require(distance(before,after)>5,"real row background follows new position");
            test.runOnMainSync(second::invalidate);SystemClock.sleep(120);require(distance(after,sample(test,second))<4,"real row final pixels match fresh draw");
            return "PASS: real schedule completion, retained row moves upward, wallpaper sample changes and matches fresh draw";
        } finally {
            Activity screen=activity;if(screen!=null)test.runOnMainSync(screen::finish);
            try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(context)){for(long id:tasks)if(id>0)store.deleteTask(id);}
            guide.edit().putInt("version",guideBefore).commit();
        }
    }
    private static View find(View view,long id){if(Long.valueOf(id).equals(view.getTag()))return view;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),id);if(found!=null)return found;}return null;}
    private static View completion(View view){if(String.valueOf(view.getContentDescription()).endsWith("标记待办完成"))return view;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=completion(group.getChildAt(i));if(found!=null)return found;}return null;}
    private static void await(Instrumentation test,java.util.function.BooleanSupplier condition,String label)throws Exception{long end=SystemClock.elapsedRealtime()+15000;boolean[] done={false};do{test.runOnMainSync(()->done[0]=condition.getAsBoolean());if(done[0])return;SystemClock.sleep(80);}while(SystemClock.elapsedRealtime()<end);throw new AssertionError(label);}
    static String run(Instrumentation test) throws Exception {
        if (!test.getTargetContext().getPackageName().endsWith(".validation"))
            throw new IllegalStateException("Validation package only");
        Activity activity = test.startActivitySync(new Intent(test.getTargetContext(), SettingsHubActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        FrameLayout[] root = new FrameLayout[1];
        LinearLayout[] list = new LinearLayout[1];
        View[] gap = new View[1];
        Surface[] card = new Surface[1];
        GlassBackdropView[] backdrop = new GlassBackdropView[1];
        try {
            test.runOnMainSync(() -> {
                if (!ThemeStore.acrylicEnabled(activity)) throw new AssertionError("Fixture requires acrylic enabled");
                root[0] = new FrameLayout(activity);
                backdrop[0] = new GlassBackdropView(activity);
                root[0].addView(backdrop[0], new FrameLayout.LayoutParams(-1,-1));
                list[0] = new LinearLayout(activity); list[0].setOrientation(LinearLayout.VERTICAL);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(500,-2);lp.leftMargin=80;lp.topMargin=80;
                root[0].addView(list[0],lp);
                gap[0] = new View(activity);list[0].addView(gap[0],new LinearLayout.LayoutParams(-1,500));
                card[0] = new Surface(activity);UiStyle.card(card[0]);
                list[0].addView(card[0],new LinearLayout.LayoutParams(-1,220));
                activity.setContentView(root[0]);
            });
            test.waitForIdleSync();SystemClock.sleep(200);
            Bitmap wallpaper=Bitmap.createBitmap(root[0].getWidth(),root[0].getHeight(),Bitmap.Config.ARGB_8888);
            for(int y=0;y<wallpaper.getHeight();y++)for(int x=0;x<wallpaper.getWidth();x++)
                wallpaper.setPixel(x,y,Color.rgb(Math.min(255,y*255/wallpaper.getHeight()),x*255/wallpaper.getWidth(),255-Math.min(255,y*255/wallpaper.getHeight())));
            test.runOnMainSync(() -> {
                try {
                    set(backdrop[0],"blurredWallpaper",wallpaper);
                    set(backdrop[0],"requestedBlurStrength",ThemeStore.blurStrength(activity));
                    set(backdrop[0],"requestedGaussian",ThemeStore.gaussianBlur(activity));
                    card[0].invalidate();
                } catch(Exception e){throw new AssertionError(e);}
            });
            SystemClock.sleep(200);
            int before=sample(test,card[0]); int draws=card[0].draws;
            test.runOnMainSync(() -> {
                android.transition.ChangeBounds transition=new android.transition.ChangeBounds();transition.setDuration(700);
                android.transition.TransitionManager.beginDelayedTransition(list[0],transition);
                list[0].removeView(gap[0]);
            });
            SystemClock.sleep(350);
            require(card[0].draws>draws+1,"ChangeBounds redraws sampled surface during motion");
            SystemClock.sleep(550);
            int after=sample(test,card[0]);require(distance(before,after)>15,"reordered card samples its new backdrop");
            int stableDraws=card[0].draws;SystemClock.sleep(180);
            require(card[0].draws<=stableDraws+1,"idle surfaces do not schedule continuous redraws");
            int retained=sample(test,card[0]);test.runOnMainSync(card[0]::invalidate);SystemClock.sleep(120);
            require(distance(retained,sample(test,card[0]))<4,"final retained pixels match fresh draw");
            draws=card[0].draws;
            test.runOnMainSync(() -> list[0].animate().translationY(450).scaleX(.7f).scaleY(.7f).setDuration(700).start());
            SystemClock.sleep(350);require(card[0].draws>draws+1,"ancestor translation and scale update child surface");
            SystemClock.sleep(550);
            retained=sample(test,card[0]);test.runOnMainSync(card[0]::invalidate);SystemClock.sleep(120);
            require(distance(retained,sample(test,card[0]))<4,"transformed retained pixels match fresh draw");
            Bitmap capture=test.getUiAutomation().takeScreenshot();
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(
                    test.getTargetContext().getExternalFilesDir(null),"acrylic-motion.png"))){capture.compress(Bitmap.CompressFormat.PNG,100,out);}
            finally{capture.recycle();}
            return "PASS: ChangeBounds intermediate/final sampling, ancestor translation/scale, fresh-draw equivalence, idle redraw stops";
        } finally {test.runOnMainSync(activity::finish);}
    }
    private static int sample(Instrumentation test,View view){
        int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));
        Bitmap image=test.getUiAutomation().takeScreenshot();
        try{return image.getPixel(xy[0]+80,xy[1]+70);}finally{image.recycle();}
    }
    private static int distance(int a,int b){return Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));}
    private static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    private static final class Surface extends View {
        int draws;
        Surface(android.content.Context context){super(context);setWillNotDraw(false);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);draws++;}
    }
}
