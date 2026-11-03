package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import org.json.*;

/** Real-window interactions; synthetic long catalog is restored and never becomes homework. */
final class PtaUiInteractionCheck {
    static void recover(Context context)throws Exception{
        var file=new java.io.File(context.getCacheDir(),"pta-ui-fixture.json");if(!file.isFile())return;
        JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));PtaSettingsStore settings=new PtaSettingsStore(context);
        if(!settings.account().equals(saved.getString("account")))throw new AssertionError("Fixture account changed");
        var edit=settings.prefs.edit().putString("catalog:"+settings.account(),saved.getString("catalog")).putBoolean("enabled",saved.getBoolean("enabled"))
                .putLong("options_revision",saved.getLong("revision")).putLong("attempt",saved.getLong("attempt"));
        String key="course:"+settings.account()+":9000000000000";if(saved.isNull("course"))edit.remove(key);else edit.putString(key,saved.getString("course"));
        if(!edit.commit()||!file.delete())throw new AssertionError("Fixture restore failed");
    }
    static void touch(Instrumentation test,long down,long at,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(down,at,action,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        if(!test.getUiAutomation().injectInputEvent(event,true))throw new AssertionError("Input rejected");event.recycle();
    }
    static void tap(Instrumentation test,String text){
        AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
        for(var n:root.findAccessibilityNodeInfosByText(text))if(n.isVisibleToUser()){
            Rect r=new Rect();n.getBoundsInScreen(r);long now=SystemClock.uptimeMillis();touch(test,now,now,0,r.exactCenterX(),r.exactCenterY());touch(test,now,now+80,1,r.exactCenterX(),r.exactCenterY());return;
        }
        throw new AssertionError("Missing visible action");
    }
    private static TextView text(View view,String value){
        if(view instanceof TextView t&&value.contentEquals(t.getText()))return t;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){var found=text(group.getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    private static AccessibilityNodeInfo editor(AccessibilityNodeInfo node){
        if(node==null)return null;if("android.widget.EditText".contentEquals(node.getClassName()))return node;
        for(int i=0;i<node.getChildCount();i++){var e=editor(node.getChild(i));if(e!=null)return e;}return null;
    }
    static Rect scrollBounds(AccessibilityNodeInfo node){
        if(node==null)return null;
        if(node.isVisibleToUser()&&"android.widget.ScrollView".contentEquals(node.getClassName())){Rect r=new Rect();node.getBoundsInScreen(r);return r;}
        for(int i=0;i<node.getChildCount();i++){Rect found=scrollBounds(node.getChild(i));if(found!=null)return found;}return null;
    }
    private static <T extends View>T find(View view,Class<T> type){
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup group)for(int i=group.getChildCount()-1;i>=0;i--){T found=find(group.getChildAt(i),type);if(found!=null)return found;}
        return null;
    }
    private static void shot(Instrumentation test,String name)throws Exception{
        Bitmap image=test.getUiAutomation().takeScreenshot();float f=Math.min(1f,1200f/Math.max(image.getWidth(),image.getHeight()));
        Bitmap small=Bitmap.createScaledBitmap(image,Math.round(image.getWidth()*f),Math.round(image.getHeight()*f),true);
        var dir=new java.io.File(test.getTargetContext().getExternalCacheDir(),"pta-ui");dir.mkdirs();
        try(var out=new java.io.FileOutputStream(new java.io.File(dir,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,78,out);}
        if(small!=image)small.recycle();image.recycle();
    }
    static String run(Instrumentation test)throws Exception{
        Context context=test.getTargetContext();recover(context);PtaSettingsStore settings=new PtaSettingsStore(context);
        test.getUiAutomation();
        String key="catalog:"+settings.account(),old=settings.prefs.getString(key,"[]");boolean enabled=settings.prefs.getBoolean("enabled",false);
        String courseKey="course:"+settings.account()+":9000000000000",oldCourse=settings.prefs.getString(courseKey,null);
        long revision=settings.prefs.getLong("options_revision",0),attempt=settings.prefs.getLong("attempt",0);
        var checkpoint=new java.io.File(context.getCacheDir(),"pta-ui-fixture.json");
        java.nio.file.Files.write(checkpoint.toPath(),new JSONObject().put("account",settings.account()).put("catalog",old).put("enabled",enabled)
                .put("course",oldCourse==null?JSONObject.NULL:oldCourse).put("revision",revision).put("attempt",attempt).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JSONObject report=new JSONObject();JSONArray catalog=new JSONArray();
        for(int i=0;i<30;i++)catalog.put(new JSONObject().put("id","900000000000"+i).put("title","long "+i+" · 课程程序设计循环与数组练习题集长名称"));
        settings.prefs.edit().putBoolean("enabled",false).putString(key,catalog.toString()).commit();
        try{
            Activity activity=test.startActivitySync(new Intent(context,PtaScopeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
            ScrollView scroll=find(activity.getWindow().getDecorView(),ScrollView.class);EditText search=find(activity.getWindow().getDecorView(),EditText.class);
            test.runOnMainSync(()->{search.setText("long");scroll.scrollTo(0,850);});Thread.sleep(500);
            int offset=scroll.getScrollY();if(offset<=0)throw new AssertionError("No scroll offset");
            var monitor=test.addMonitor(PtaScopeActivity.class.getName(),null,false);
            test.runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Activity rotated=monitor.waitForActivityWithTimeout(6000);test.removeMonitor(monitor);
            if(rotated==null)throw new AssertionError("No recreation");test.waitForIdleSync();Thread.sleep(800);
            EditText restored=find(rotated.getWindow().getDecorView(),EditText.class);ScrollView restoredScroll=find(rotated.getWindow().getDecorView(),ScrollView.class);
            if(!"long".contentEquals(restored.getText())||restoredScroll.getScrollY()!=offset)throw new AssertionError("Reading state lost");
            report.put("query_and_scroll_restored",true);shot(test,"scope-rotated-long-fixture");
            test.runOnMainSync(()->rotated.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(1000);
            // NEW_TASK may reuse the same top component; close it before startActivitySync waits for a fresh instance.
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(400);
            Activity courses=test.startActivitySync(new Intent(context,PtaScopeActivity.class).putExtra("courses",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
            String title=catalog.getJSONObject(0).getString("title"),value="程序设计与计算思维长课程名称";
            View originalRow=text(courses.getWindow().getDecorView(),title).getParent() instanceof View v?v:null;
            tap(test,title);Thread.sleep(400);var inputNode=editor(test.getUiAutomation().getRootInActiveWindow());
            Bundle content=new Bundle();content.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
            if(inputNode==null||!inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,content))throw new AssertionError("Editor text not set");
            tap(test,"保存");Thread.sleep(600);
            if(text(courses.getWindow().getDecorView(),value)==null||text(courses.getWindow().getDecorView(),title).getParent()!=originalRow)
                throw new AssertionError("Course row rebuilt or not updated");
            report.put("course_updated_in_place",true);Thread.sleep(5000);shot(test,"course-long-fixture");
            Activity account=test.startActivitySync(new Intent(context,SchoolSettingsActivity.class).putExtra("page","account").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
            var editor=find(account.getWindow().getDecorView(),EditText.class);
            test.runOnMainSync(()->{editor.requestFocus();((android.view.inputmethod.InputMethodManager)account.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editor,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
            Thread.sleep(1200);Rect frame=new Rect(),input=new Rect();
            test.runOnMainSync(()->{account.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);editor.getGlobalVisibleRect(input);});
            if(!editor.hasFocus()||input.bottom>frame.bottom||input.height()==0)throw new AssertionError("Keyboard hides editor");
            report.put("keyboard_field_visible",true);shot(test,"account-keyboard");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            // The login page starts without importing the supplied file into WebView cookies.
            Activity login=test.startActivitySync(new Intent(context,PtaLoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
            test.runOnMainSync(()->{
                ViewGroup decor=(ViewGroup)login.getWindow().getDecorView();
                java.util.ArrayList<View> targets=new java.util.ArrayList<>();decor.findViewsWithText(targets,"完成登录",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                if(targets.isEmpty())throw new AssertionError("Missing login action");targets.get(0).performClick();
            });Thread.sleep(600);
            boolean[] feedback={false};test.runOnMainSync(()->{
                java.util.ArrayList<View> found=new java.util.ArrayList<>();login.getWindow().getDecorView().findViewsWithText(found,"登录",View.FIND_VIEWS_WITH_TEXT);
                for(var v:found)if(v instanceof TextView t&&(t.getText().toString().contains("请先")||t.getText().toString().contains("正在验证")))feedback[0]=true;
            });
            if(!feedback[0])throw new AssertionError("No explicit login feedback");report.put("explicit_login_feedback",true);shot(test,"login-feedback");
            test.runOnMainSync(login::finish);
        }finally{var edit=settings.prefs.edit().putString(key,old).putBoolean("enabled",enabled).putLong("options_revision",revision).putLong("attempt",attempt);
            if(oldCourse==null)edit.remove(courseKey);else edit.putString(courseKey,oldCourse);if(!edit.commit()||!checkpoint.delete())throw new AssertionError("Fixture cleanup failed");
            test.getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);ZjuSyncJobService.schedule(context);}
        return report.put("synthetic_catalog_restored",true).toString();
    }
}
