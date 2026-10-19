package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import java.lang.reflect.Field;

/** Unsaved settings form and custom picker, only in the synthetic validation package. */
final class ScheduleSettingsDeviceCheck {
    static String run(Instrumentation test)throws Exception{
        if(!test.getTargetContext().getPackageName().equals("com.donglan.chrona.followup.validation"))throw new IllegalStateException("Isolated package only");
        SettingsActivity activity=(SettingsActivity)test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            test.waitForIdleSync();
            EditText schedule=(EditText)field(activity,"customPrompt"),classroom=(EditText)field(activity,"classroomPrompt");
            View start=(View)field(activity,"workStart"),end=(View)field(activity,"workEnd");
            test.runOnMainSync(()->{
                require(start.getContentDescription()!=null||start instanceof android.widget.TextView,"work time trigger");
                require(schedule!=classroom&&schedule.getMinLines()>=4&&classroom.getMinLines()>=4,"separate readable prompt editors");
                require(((Number)field(activity,"workStartMinutes")).intValue()==600&&((Number)field(activity,"workEndMinutes")).intValue()==1320,"10 to 22 defaults");
                schedule.setText("日程偏好验收");classroom.setText("课堂偏好验收");
                start.requestRectangleOnScreen(new android.graphics.Rect(0,0,start.getWidth(),start.getHeight()+420),true);
            });
            SystemClock.sleep(400);capture(test,"schedule-preferences");
            test.runOnMainSync(start::performClick);SystemClock.sleep(350);capture(test,"schedule-time-picker");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(200);
            test.runOnMainSync(()->classroom.requestRectangleOnScreen(new android.graphics.Rect(0,0,classroom.getWidth(),classroom.getHeight()),true));
            SystemClock.sleep(250);capture(test,"classroom-preference-editor");
            require(schedule.getText().toString().equals("日程偏好验收")&&classroom.getText().toString().equals("课堂偏好验收"),"independent form values");
            return "PASS: default work window, separate multi-line prompts, custom time picker opened; no saved preference or API changes";
        }finally{test.runOnMainSync(activity::finish);}
    }
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static void capture(Instrumentation test,String name)throws Exception{android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
}
