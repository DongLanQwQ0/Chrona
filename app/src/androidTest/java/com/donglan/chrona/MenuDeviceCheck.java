package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.donglan.chrona.calendar.CalendarOccurrence;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Actual shared menu rendering and callbacks; isolated validation package only. */
final class MenuDeviceCheck {
    private static int checks;
    static String run(Instrumentation test,Activity owner,Bundle args)throws Exception {
        if(!owner.getPackageName().equals("com.donglan.chrona.followup.validation"))throw new IllegalStateException("Isolated package only");
        checks=0;String theme=args.getString("theme","light");int[] chosen={-1};
        onMain(test,()->UiDialogs.choiceDialog(owner,"外观菜单",new String[]{"跟随系统","浅色","深色"},1,i->chosen[0]=i));
        waitFor(test,()->rows().size()==3,"short menu");
        inspect(test,3,1);capture(test,"menu-short-"+theme);
        View first=rows().get(0);int[] xy=new int[2];onMain(test,()->first.getLocationOnScreen(xy));
        long down=SystemClock.uptimeMillis();pointer(test,down,0,xy[0]+first.getWidth()/2f,xy[1]+first.getHeight()/2f);SystemClock.sleep(650);
        onMain(test,()->require(first.getScaleX()==1f&&first.getScaleY()==1f,"held menu row does not scale"));
        capture(test,"menu-held-"+theme);pointer(test,down,3,xy[0]+first.getWidth()/2f,xy[1]+first.getHeight()/2f);
        tap(test,rows().get(2));waitFor(test,()->chosen[0]==2&&rows().isEmpty(),"short menu callback");
        String[] labels=new String[12];for(int i=0;i<labels.length;i++)labels[i]="课程 "+(i+1)+" · 智能机器人系统与自主导航实验安排\n紫金港校区东教学区";
        onMain(test,()->UiDialogs.choiceDialog(owner,"选择课程",labels,0,i->chosen[0]=i));
        waitFor(test,()->rows().size()==12,"long menu");inspect(test,12,0);capture(test,"menu-long-top-"+theme);
        TextView last=rows().get(11);
        onMain(test,()->last.requestRectangleOnScreen(new Rect(0,0,last.getWidth(),last.getHeight()),true));SystemClock.sleep(300);
        onMain(test,()->{Rect visible=new Rect();require(last.getGlobalVisibleRect(visible)&&visible.height()==last.getHeight(),"last long menu row fully reachable");require(last.getLayout().getEllipsisCount(last.getLineCount()-1)==0,"long label not ellipsized");});
        capture(test,"menu-long-bottom-"+theme);tap(test,last);waitFor(test,()->chosen[0]==11&&rows().isEmpty(),"last long option callback");
        Activity courses=test.startActivitySync(new Intent(owner,CourseSourceMatchingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            waitFor(test,()->text(courses.getWindow().getDecorView(),"手动关联课程名字")!=null,"course matching loaded");
            long when=System.currentTimeMillis()+86400000L;
            CalendarOccurrence a=new CalendarOccurrence(11,when,when+3600000,"微积分",false,"东1-201","课表来源",null,11,"fixture");
            CalendarOccurrence b=new CalendarOccurrence(12,when,when+3600000,"微积分",false,"东1-201","系统来源",null,12,"fixture");
            Field data=CourseSourceMatchingActivity.class.getDeclaredField("rows");data.setAccessible(true);
            Method preview=CourseSourceMatchingActivity.class.getDeclaredMethod("preview",CalendarOccurrence.class,CalendarOccurrence.class);preview.setAccessible(true);
            var before=ScheduleSources.courseRules(owner);
            onMain(test,()->{try{data.set(courses,new ArrayList<>(List.of(a,b)));preview.invoke(courses,a,b);}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
            waitFor(test,()->rows().size()==3,"actual course action menu");inspect(test,3,-1);capture(test,"menu-course-actions-"+theme);
            tap(test,rows().get(0));waitFor(test,()->rows().size()==2,"actual source submenu");inspect(test,2,-1);capture(test,"menu-course-source-"+theme);
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitFor(test,()->rows().isEmpty(),"source menu cancelled");
            require(before.equals(ScheduleSources.courseRules(owner)),"cancelled course menu does not change source rules");
        } finally {onMain(test,courses::finish);}
        return "PASS: "+checks+" menu assertions; shared choices, long-label scrolling, held ripple, real course actions and cancellation";
    }
    static void inspect(Instrumentation test,int count,int selected) {
        onMain(test,()-> {
            List<TextView> items=rows();require(items.size()==count,"menu item count");
            ViewGroup parent=(ViewGroup)items.get(0).getParent();int separators=0;
            for(int i=0;i<parent.getChildCount();i++)if("menu_separator".equals(parent.getChildAt(i).getTag())) {
                View line=parent.getChildAt(i);separators++;require(Math.abs(line.getHeight()-line.getResources().getDisplayMetrics().density)<=1,"one-dp separator");
                require(i>0&&i<parent.getChildCount()-1,"no outer menu separators");
            }
            require(separators==count-1,"one separator between adjacent items");
            for(int i=0;i<items.size();i++) {
                TextView row=items.get(i);require(row.getBackground()==null,"menu row has no background or border");
                require(row.getForeground() instanceof RippleDrawable,"menu row retains ripple feedback");
                require((row.getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)==Gravity.START,"menu text left aligned");
                require(row.isSelected()==(i==selected),"selected row state");
                require((row.getCompoundDrawablesRelative()[2]!=null)==(i==selected),"selected trailing check only");
                require(row.getHeight()>=52*row.getResources().getDisplayMetrics().density-1,"full menu row target");
            }
        });
    }
    static List<TextView> rows(){List<TextView> rows=new ArrayList<>();for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())collect(root,rows);return rows;}
    private static void collect(View view,List<TextView> rows){if(view instanceof TextView label&&"menu_item".equals(view.getTag()))rows.add(label);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),rows);}
    private static TextView text(View view,String value){if(view instanceof TextView label&&value.contentEquals(label.getText()))return label;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static void tap(Instrumentation test,View view){int[] p=new int[2];onMain(test,()->view.getLocationOnScreen(p));long down=SystemClock.uptimeMillis();pointer(test,down,0,p[0]+view.getWidth()/2f,p[1]+view.getHeight()/2f);pointer(test,down,1,p[0]+view.getWidth()/2f,p[1]+view.getHeight()/2f);}
    private static void pointer(Instrumentation test,long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{test.sendPointerSync(event);}finally{event.recycle();}}
    private static void waitFor(Instrumentation test,BooleanSupplier ready,String label)throws Exception{long until=SystemClock.uptimeMillis()+10000;boolean[] ok={false};do{onMain(test,()->ok[0]=ready.getAsBoolean());if(ok[0])return;SystemClock.sleep(80);}while(SystemClock.uptimeMillis()<until);throw new AssertionError(label);}
    private static void onMain(Instrumentation test,Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private static void require(boolean valid,String label){checks++;if(!valid)throw new AssertionError(label);}
    private static void capture(Instrumentation test,String name)throws Exception{test.waitForIdleSync();SystemClock.sleep(350);Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
}
