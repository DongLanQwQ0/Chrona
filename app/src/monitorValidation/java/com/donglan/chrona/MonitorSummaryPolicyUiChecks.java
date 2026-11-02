package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.donglan.chrona.classroom.ClassroomLesson;
import java.io.*;
import java.lang.reflect.Field;
import org.json.JSONObject;

/** Native-layout preference interaction checks; uses only the isolated fixture package. */
final class MonitorSummaryPolicyUiChecks {
    private final Instrumentation test;
    private final Bundle args;
    private final Context app;
    private final ClassroomMonitorStore store;
    private Activity screen;
    private int assertions;
    private MonitorSummaryPolicyUiChecks(Instrumentation test,Bundle args,String account){
        this.test=test;this.args=args;app=test.getTargetContext();store=new ClassroomMonitorStore(app,account);
    }
    static int run(Instrumentation test,Bundle args,String account,ClassroomLesson lesson)throws Exception{
        if(!"com.donglan.chrona.monitor.validation".equals(test.getTargetContext().getPackageName()))throw new SecurityException("Fixture required");
        MonitorSummaryPolicyUiChecks checks=new MonitorSummaryPolicyUiChecks(test,args,account);
        try{checks.run(account,lesson);return checks.assertions;}
        finally{if(checks.screen!=null)test.runOnMainSync(checks.screen::finish);}
    }
    private void run(String account,ClassroomLesson lesson)throws Exception{
        screen=test.startActivitySync(new Intent(app,ClassroomTranscriptActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("account",account).putExtra("lesson",lesson.json().toString()));
        idle(1000);
        if(test instanceof ClassroomMonitorInstrumentation runner&&runner.lastResumed instanceof ClassroomTranscriptActivity)screen=runner.lastResumed;
        check(!screen.isDestroyed()&&screen.hasWindowFocus(),"Checks use the actual resumed monitor reader");
        TextView policy=(TextView)field("summaryPolicy");CheckBox repeat=(CheckBox)field("repeat");
        ScrollView viewport=(ScrollView)field("scroll");LinearLayout rows=(LinearLayout)field("rows");
        check(policy.getText().toString().equals("跟随全局"),"Default course policy follows global");
        check(policy.getContentDescription().toString().contains("已开启"),"Missing global preference retains automatic summary");
        Rect p=bounds(policy),r=bounds(repeat),v=bounds(viewport);
        check(!Rect.intersects(p,r)&&p.left>=r.right,"Course field is beside the future-course checkbox without overlap");
        check(p.width()>=48*screen.getResources().getDisplayMetrics().density&&p.height()>=48*screen.getResources().getDisplayMetrics().density,"Course field keeps normal accessible touch size");
        check(policy.getLayout()!=null&&policy.getLayout().getEllipsisCount(0)==0,"Course field label remains complete");
        check(v.height()>screen.getResources().getDisplayMetrics().heightPixels*.35f,"Reading viewport retains substantial initial height");
        capture("initial");
        test.runOnMainSync(()->viewport.scrollTo(0,Math.max(0,rows.getHeight()/3)));idle(300);
        int offset=viewport.getScrollY();View paragraph=rows.getChildAt(0);String rules=store.read().getJSONArray("rules").toString();
        tap(policy);idle(250);capture("choices");tapText("不自动");idle(500);
        check(store.summaryPolicy(lesson)==ClassroomMonitorStore.SUMMARY_OFF&&policy.getText().toString().equals("不自动"),"Real menu selects and saves course off");
        tap(policy);idle(200);tapText("自动");idle(400);
        check(store.summaryPolicy(lesson)==ClassroomMonitorStore.SUMMARY_ON&&policy.getText().toString().equals("自动"),"Real menu selects and saves course on");
        tap(policy);idle(200);tapText("跟随全局");idle(400);
        check(store.summaryPolicy(lesson)==ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL,"Follow choice removes course override");
        check(rows.getChildAt(0)==paragraph&&Math.abs(viewport.getScrollY()-offset)<=1,"Policy selection preserves existing captions and exact reading offset");
        check(rules.equals(store.read().getJSONArray("rules").toString()),"Preference changes never register or restart monitoring");
        test.runOnMainSync(()->ClassroomMonitorUi.runtimeSettings(screen));idle(300);capture("global-on");
        tapText("上传图片");idle(150);check(store.summaryImages(),"Existing image preference saves through global settings");
        tapText("自动总结");idle(200);check(!store.autoSummary()&&store.summaryImages(),"Turning off global auto keeps the existing image permission");
        AccessibilityNodeInfo images=exact("上传图片");check(!images.isEnabled(),"Image upload is not editable while global auto is off");idle(1000);capture("global-off");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);idle(600);
        check(policy.getContentDescription().toString().contains("已关闭"),"Reader reflects external global change without a body reload");
        check(rows.getChildAt(0)==paragraph&&Math.abs(viewport.getScrollY()-offset)<=1,"Global preference refresh preserves paragraphs and offset");
        test.runOnMainSync(()->ClassroomMonitorUi.runtimeSettings(screen));idle(250);tapText("自动总结");idle(200);
        check(store.autoSummary()&&exact("上传图片").isEnabled()&&store.summaryImages(),"Re-enabling global auto restores editable saved image choice");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);idle(300);
        Rect target=bounds(policy);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,target.centerX(),target.centerY());idle(350);
        capture("field-hold");
        RectF projected=new RectF(0,0,policy.getWidth(),policy.getHeight());
        test.runOnMainSync(()->{Matrix matrix=new Matrix();policy.transformMatrixToGlobal(matrix);matrix.mapRect(projected);});
        ViewParent parent=policy.getParent();while(parent instanceof View&&! (parent instanceof ScrollView))parent=parent.getParent();
        Rect clip=bounds((View)parent);boolean envelope=projected.left>=clip.left&&projected.top>=clip.top&&projected.right<=clip.right&&projected.bottom<=clip.bottom;
        JSONObject press=new JSONObject().put("scale",policy.getScaleX()).put("projected",projected.toShortString()).put("viewport",clip.toShortString()).put("contained",envelope);
        try(FileOutputStream out=new FileOutputStream(new File(directory(),"press.json"))){out.write(press.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,target.centerX(),target.centerY());idle(500);
        check(envelope,"Field press envelope stays inside the real tools viewport");
        int before=viewport.getHeight();
        if(SettingsPageLayout.shortWindow(screen)){
            Rect area=bounds(viewport);swipe(area.centerX(),area.bottom-30,area.top+30);idle(400);
            check(viewport.getHeight()>before,"Landscape upward reading hides tools and increases the actual viewport");capture("tools-collapsed");
        }
        JSONObject geometry=new JSONObject().put("viewport",v.toShortString()).put("policy",p.toShortString()).put("repeat",r.toShortString()).put("initial_height",before).put("after_height",viewport.getHeight());
        try(FileOutputStream out=new FileOutputStream(new File(directory(),"geometry.json"))){out.write(geometry.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    private Object field(String name)throws Exception{Field f=ClassroomTranscriptActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    private void idle(long millis){SystemClock.sleep(millis);test.waitForIdleSync();}
    private Rect bounds(View view){Rect rect=new Rect();test.runOnMainSync(()->view.getGlobalVisibleRect(rect));return rect;}
    private void tap(View view){Rect rect=bounds(view);touch(rect.centerX(),rect.centerY());}
    private void tapText(String text){AccessibilityNodeInfo node=exact(text);Rect rect=new Rect();node.getBoundsInScreen(rect);touch(rect.centerX(),rect.centerY());}
    private AccessibilityNodeInfo exact(String text){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText()))return node;throw new AssertionError("Missing visible action: "+text);}
    private void touch(float x,float y){long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(40);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y);}
    private void swipe(float x,float start,float end){long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,x,start);for(int i=1;i<=12;i++){SystemClock.sleep(16);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,start+(end-start)*i/12);}pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,end);}
    private void pointer(long down,long at,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,at,action,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);try{test.sendPointerSync(event);}finally{event.recycle();}}
    private File directory(){File dir=new File(app.getExternalFilesDir(null),"monitor-policy100/"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+"-"+args.getString("font","1"));dir.mkdirs();return dir;}
    private void capture(String name)throws Exception{Bitmap original=test.getUiAutomation().takeScreenshot();if(original==null)throw new AssertionError("Screenshot unavailable");int width=Math.min(1200,original.getWidth());Bitmap image=Bitmap.createScaledBitmap(original,width,Math.round(original.getHeight()*(float)width/original.getWidth()),true);try(FileOutputStream out=new FileOutputStream(new File(directory(),name+".jpg"))){image.compress(Bitmap.CompressFormat.JPEG,78,out);}if(image!=original)image.recycle();original.recycle();}
}
