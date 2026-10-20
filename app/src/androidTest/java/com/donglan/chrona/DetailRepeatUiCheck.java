package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatRule;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;
import java.time.YearMonth;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Own synthetic drafts only; no Calendar Provider or network writes. */
final class DetailRepeatUiCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context=test.getTargetContext();
        if(!context.getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated package required");
        ThemeStore.setMode(context,args.getString("theme","light"));
        boolean reminders=AgendaReminderPreferences.enabled(context);
        AgendaReminderPreferences.setEnabled(context,false);
        context.getSystemService(android.app.NotificationManager.class).cancelAll();
        long task=0; Activity detail=null, repeat=null;
        try {
            long first=System.currentTimeMillis()+7*86400000L;
            String title="true".equals(args.getString("long_title"))?"机器人与无人机基础：室外飞行安全与自主导航实验":"机器人与无人机基础";
            long repeated, empty, fresh;
            try(TaskStore store=new TaskStore(context)) {
                task=store.insertTask("详情操作布局验收"+("hints".equals(args.getString("scope"))?" https://example.invalid/menu":""),null,"detail-repeat-ui",System.currentTimeMillis());
                repeated=store.appendCandidate(new EventCandidate(0,task,title,first,first+5400000,
                        "Asia/Shanghai","北四-212","每周课堂",null,true,null,"event",false)
                        .withRepeat(new RepeatRule("daily",1,List.of(),null,40)));
                empty=store.appendCandidate(new EventCandidate(0,task,"未安排待办",null,null,"Asia/Shanghai","","",null,true,null,"task",false));
                fresh=store.appendCandidate(new EventCandidate(0,task,"新的重复事项",first,first+3600000,
                        "Asia/Shanghai","","",null,true,null,"event",false));
                store.appendCandidate(new EventCandidate(0,task,"退役记录标题",null,null,"Asia/Shanghai","","保留正文",
                        null,false,null,"event",false,false,0,null,null,true));
                store.updateStatus(task,TaskRecord.READY,null);
            }
            boolean hintScope="hints".equals(args.getString("scope"));
            detail=open(test,context,TaskDetailActivity.class,task,hintScope?empty:"values".equals(args.getString("scope"))?fresh:repeated);
            final Activity editor=detail;
            waitFor(test,()->description(editor.getWindow().getDecorView(),hintScope?"执行开始时间，点按选择日期和时间":"开始时间，点按选择日期和时间")!=null,"time value");
            if(hintScope) {
                tap(test,description(editor.getWindow().getDecorView(),"打开模型输出、解析用量、来源与任务操作"));
                waitFor(test,()->MenuDeviceCheck.rows().size()==2,"detail header menu");
                MenuDeviceCheck.inspect(test,2,-1);capture(test,"menu-detail-"+args.getString("theme","light"));
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
                waitFor(test,()->MenuDeviceCheck.rows().isEmpty(),"detail menu cancelled");
                EditText start=(EditText)description(editor.getWindow().getDecorView(),"执行开始时间，点按选择日期和时间");
                EditText end=(EditText)description(editor.getWindow().getDecorView(),"执行结束时间，点按选择日期和时间");
                require(timeHints(start,end)&&start.length()==0&&end.length()==0,"exact requested empty-field hints");
                test.runOnMainSync(()-> {View range=(View)start.getParent().getParent();range.requestRectangleOnScreen(new Rect(0,0,range.getWidth(),range.getHeight()),true);});
                capture(test,"detail-time-hints-"+args.getString("theme","light"));
                tap(test,start);waitFor(test,()->windowText("执行开始时间")!=null,"hint field opens picker");tap(test,windowAction("取消"));
                require(timeHints(start,end)&&start.length()==0&&end.length()==0,"cancel preserves hints and empty values");
                View allDay=type(editor.getWindow().getDecorView(),Class.forName("com.donglan.chrona.TaskDetailActivity$AllDayToggle"));
                tap(test,allDay);require(timeHints(start,end),"all-day toggle retains exact hints");
                test.runOnMainSync(()->start.setText("2026-11-01"));
                require("2026-11-01".contentEquals(start.getText())&&timeHints(start,end),"hint does not replace entered value");
                return "PASS: literal start/end hints, normal/large wrapping render, picker/cancel, all-day retention, filled value, and borderless detail menu";
            }
            require(text(editor.getWindow().getDecorView(),"收件箱记录")==null
                    &&text(editor.getWindow().getDecorView(),"退役记录标题")==null,"retired independent record UI removed");
            if ("values".equals(args.getString("scope"))) {
                TextView begin=(TextView)description(editor.getWindow().getDecorView(),"开始时间，点按选择日期和时间");
                TextView finish=(TextView)description(editor.getWindow().getDecorView(),"结束时间，点按选择日期和时间");
                test.runOnMainSync(()->begin.requestRectangleOnScreen(new Rect(0,0,begin.getWidth(),begin.getHeight()),true));
                capture(test,"detail-values-"+args.getString("theme","light"));
                test.runOnMainSync(()->{fullText(begin);fullText(finish);});
                tap(test,begin);
                waitFor(test,()->windowText("开始时间")!=null,"wrapped value opens picker");
                tap(test,windowAction("取消"));
                return "PASS: full date/time text and picker action; independent record hidden, source preserved";
            }
            View rule=description(editor.getWindow().getDecorView(),"重复日程");
            View remove=description(editor.getWindow().getDecorView(),"删除日程 "+title);
            require(remove!=null&&rule.getParent()==remove.getParent(),"repeat and delete share a row");
            ViewGroup row=(ViewGroup)rule.getParent(),card=(ViewGroup)row.getParent();
            require(card.getChildAt(card.getChildCount()-1)==row,"actions are at card bottom");
            test.runOnMainSync(()->row.requestRectangleOnScreen(new Rect(0,0,row.getWidth(),row.getHeight()),true));
            test.waitForIdleSync();SystemClock.sleep(500);
            test.runOnMainSync(()->{
                ScrollView page=(ScrollView)type(editor.getWindow().getDecorView(),ScrollView.class);
                int[] position=new int[2];row.getLocationOnScreen(position);
                page.scrollBy(0,position[1]-editor.getResources().getDisplayMetrics().heightPixels/2);
            });
            capture(test,"detail-actions-"+args.getString("theme","light"));
            require(rule.getWidth()<card.getWidth()&&Math.abs(rule.getTop()+rule.getHeight()/2f-remove.getTop()-remove.getHeight()/2f)<=2,
                    "actions fit and align on one line");
            tap(test,remove);
            waitFor(test,()->windowText("确认删除")!=null||windowText("删除日程 1？")!=null,"delete confirmation");
            tap(test,windowText("取消"));
            Instrumentation.ActivityMonitor monitor=test.addMonitor(RepeatScheduleActivity.class.getName(),null,false);
            tap(test,rule);
            repeat=monitor.waitForActivityWithTimeout(5000);test.removeMonitor(monitor);
            require(repeat!=null,"repeat action opens series");
            final Activity series=repeat;
            waitFor(test,()->type(series.getWindow().getDecorView(),DateIntervalBar.class)!=null,"shared month rail");
            DateIntervalBar rail=(DateIntervalBar)type(series.getWindow().getDecorView(),DateIntervalBar.class);
            View stop=description(series.getWindow().getDecorView(),"停止重复");
            require(stop!=null&&stop.getParent()==description(series.getWindow().getDecorView(),"返回").getParent(),"stop is a header action");
            capture(test,"repeat-series-"+args.getString("theme","light"));
            String month=rail.text();
            WorkspaceDeviceCheck.swipe(test,rail,.7f,.3f,24);
            waitFor(test,()->!month.equals(rail.text()),"month swipe advances");
            SystemClock.sleep(400);
            require(rail.isAttachedToWindow()&&rail==type(series.getWindow().getDecorView(),DateIntervalBar.class),"month rail retained during load");
            ScrollView viewport=(ScrollView)type(series.getWindow().getDecorView(),ScrollView.class);
            java.lang.reflect.Field bodyField=RepeatScheduleActivity.class.getDeclaredField("occurrenceBody");bodyField.setAccessible(true);
            ViewGroup body=(ViewGroup)bodyField.get(series);
            View last=body.getChildAt(body.getChildCount()-1);
            tap(test,last);
            waitFor(test,()->windowText("改单次时间")!=null,"later occurrence actions");
            int position=viewport.getScrollY();
            require(position>0,"later occurrence is scrolled into view");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
            require(viewport==type(series.getWindow().getDecorView(),ScrollView.class)&&viewport.getScrollY()==position,
                    "cancel preserves scroll viewport and position");
            require(rail==type(series.getWindow().getDecorView(),DateIntervalBar.class),"opening occurrence retains date rail");
            capture(test,"repeat-later-cancel-"+args.getString("theme","light"));
            test.runOnMainSync(()->viewport.scrollTo(0,0));SystemClock.sleep(300);
            WorkspaceDeviceCheck.swipe(test,rail,.3f,.7f,24);
            waitFor(test,()->month.equals(rail.text()),"month reverse swipe returns");
            tap(test,stop);waitFor(test,()->windowText("过往日程将保留，停止今后的重复。")!=null,"stop confirmation");
            tap(test,windowAction("停止重复"));
            waitFor(test,()->text(series.getWindow().getDecorView(),"每周 · 已停止")!=null
                    || stopped(context,repeated),"stop persisted");
            try(TaskStore store=new TaskStore(context)){require(store.getCandidates(task).stream().anyMatch(c->c.id==repeated&&c.repeatStoppedAtMillis!=null),"stop persisted");}
            test.runOnMainSync(series::finish);repeat=null;
            test.runOnMainSync(editor::finish);detail=null;

            detail=open(test,context,TaskDetailActivity.class,task,empty);final Activity blank=detail;
            waitFor(test,()->description(blank.getWindow().getDecorView(),"执行开始时间，点按选择日期和时间")!=null,"blank execution fields");
            EditText start=(EditText)description(blank.getWindow().getDecorView(),"执行开始时间，点按选择日期和时间");
            EditText end=(EditText)description(blank.getWindow().getDecorView(),"执行结束时间，点按选择日期和时间");
            require(start!=null&&end!=null&&timeHints(start,end),"empty time fields show requested hints");
            test.runOnMainSync(()->start.requestRectangleOnScreen(new Rect(0,0,start.getWidth(),start.getHeight()),true));
            capture(test,"detail-empty-time-"+args.getString("theme","light"));
            tap(test,start);waitFor(test,()->windowText("执行开始时间")!=null,"empty time still opens picker");
            tap(test,windowText("取消"));
            View allDay=type(blank.getWindow().getDecorView(),Class.forName("com.donglan.chrona.TaskDetailActivity$AllDayToggle"));
            tap(test,allDay);require(timeHints(start,end),"all-day toggle retains requested hints");
            test.runOnMainSync(blank::finish);detail=null;
            repeat=open(test,context,RepeatScheduleActivity.class,task,fresh);final Activity form=repeat;
            waitFor(test,()->description(form.getWindow().getDecorView(),"建立重复日程")!=null,"create header action");
            capture(test,"repeat-form-"+args.getString("theme","light"));
            tap(test,description(form.getWindow().getDecorView(),"重复频率，每天"));
            waitFor(test,()->windowAction("每周")!=null,"frequency choices");tap(test,windowAction("每周"));
            waitFor(test,()->text(form.getWindow().getDecorView(),"周日")!=null,"weekly days");
            capture(test,"repeat-weekly-"+args.getString("theme","light"));
            tap(test,text(form.getWindow().getDecorView(),"持续重复"));
            waitFor(test,()->windowAction("结束日期")!=null,"end choices");tap(test,windowAction("结束日期"));
            waitFor(test,()->windowAction("确定")!=null,"end date picker");tap(test,windowAction("确定"));
            waitFor(test,()->buttonStarts(form.getWindow().getDecorView(),"结束日期")!=null,"end date retained");
            tap(test,buttonStarts(form.getWindow().getDecorView(),"结束日期"));
            waitFor(test,()->windowAction("重复次数")!=null,"count choice");tap(test,windowAction("重复次数"));
            waitFor(test,()->text(form.getWindow().getDecorView(),"次数（包含首次）")!=null,"count field");
            java.lang.reflect.Field countField=RepeatScheduleActivity.class.getDeclaredField("countInput");countField.setAccessible(true);
            EditText countInput=(EditText)countField.get(form);tap(test,countInput);
            SystemClock.sleep(600);
            test.runOnMainSync(()->countInput.requestRectangleOnScreen(new Rect(0,0,countInput.getWidth(),countInput.getHeight()),true));
            test.waitForIdleSync();SystemClock.sleep(300);
            capture(test,"repeat-count-keyboard-"+args.getString("theme","light"));
            Rect inputRect=new Rect();boolean visible=countInput.getGlobalVisibleRect(inputRect);
            ScrollView formScroll=(ScrollView)type(form.getWindow().getDecorView(),ScrollView.class);
            int keyboard=form.getWindow().getDecorView().getRootWindowInsets().getInsets(android.view.WindowInsets.Type.ime()).bottom;
            require(visible&&inputRect.height()>=countInput.getHeight()-1&&inputRect.bottom<=form.getWindow().getDecorView().getHeight()-keyboard,"count input scrolls fully above keyboard: rect="+inputRect+" fieldHeight="+countInput.getHeight()+" scrollY="+formScroll.getScrollY()+" viewportHeight="+formScroll.getHeight()+" bottomPadding="+formScroll.getPaddingBottom()+" ime="+keyboard);
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            waitFor(test,()->!form.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()),"keyboard dismissed");
            test.runOnMainSync(()->((ScrollView)type(form.getWindow().getDecorView(),ScrollView.class)).scrollTo(0,0));SystemClock.sleep(400);
            tap(test,description(form.getWindow().getDecorView(),"建立重复日程"));
            waitFor(test,()->type(form.getWindow().getDecorView(),DateIntervalBar.class)!=null,"create opens series view");
            try(TaskStore store=new TaskStore(context)){require(store.getCandidates(task).stream().anyMatch(c->c.id==fresh&&c.repeatRule!=null),"create persisted");}
            return "PASS: bottom action row, delete confirmation, repeat navigation/create/stop, retained month swipes and later-instance scroll after cancel, empty hints and picker";
        } finally {
            Activity a=detail,b=repeat;test.runOnMainSync(()->{if(a!=null)a.finish();if(b!=null)b.finish();});
            if(task>0)try(TaskStore store=new TaskStore(context)){store.deleteTask(task);}
            AgendaReminderPreferences.setEnabled(context,reminders);
        }
    }
    private static boolean timeHints(TextView start,TextView end){return "欸~这好像还没有时间".contentEquals(start.getHint())&&"遥遥路途，没有终点~".contentEquals(end.getHint());}
    private static void fullText(TextView view){
        require(view!=null&&view.getLayout()!=null,"time value layout exists");
        android.text.Layout layout=view.getLayout();
        int width=view.getWidth()-view.getCompoundPaddingLeft()-view.getCompoundPaddingRight();
        for(int i=0;i<layout.getLineCount();i++)require(layout.getLineWidth(i)<=width+1&&layout.getEllipsisCount(i)==0,"time line fits without truncation");
        require(layout.getLineEnd(layout.getLineCount()-1)==view.length()
                &&layout.getHeight()<=view.getHeight()-view.getCompoundPaddingTop()-view.getCompoundPaddingBottom(),"all time text remains visible");
    }
    private static View buttonStarts(View root,String prefix){if(root instanceof android.widget.Button t&&t.getText().toString().startsWith(prefix))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=buttonStarts(g.getChildAt(i),prefix);if(found!=null)return found;}return null;}
    private static boolean stopped(Context context,long id){try(TaskStore store=new TaskStore(context)){return store.getCandidatesByIds(List.of(id)).stream().anyMatch(c->c.repeatStoppedAtMillis!=null);}}
    private static View action(View root,String label){if(root instanceof TextView t&&label.contentEquals(t.getText())&&root.isClickable())return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=action(g.getChildAt(i),label);if(found!=null)return found;}return null;}
    private static View windowAction(String label){View found=null;for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){View item=action(root,label);if(item!=null)found=item;}return found;}
    private static Activity open(Instrumentation test,Context context,Class<?> type,long task,long candidate){return test.startActivitySync(new Intent(context,type).putExtra("task_id",task).putExtra("candidate_id",candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private static View description(View root,String text){if(text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=description(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static View type(View root,Class<?> type){if(type.isInstance(root))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=type(g.getChildAt(i),type);if(found!=null)return found;}return null;}
    private static TextView text(View root,String text){if(root instanceof TextView t&&text.contentEquals(t.getText()))return t;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView found=text(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static TextView windowText(String text){TextView found=null;for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){TextView item=text(root,text);if(item!=null)found=item;}return found;}
    private static void tap(Instrumentation test,View view)throws Exception{require(view!=null,"tap target exists");test.runOnMainSync(()->view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true));SystemClock.sleep(400);int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));long time=SystemClock.uptimeMillis();float x=xy[0]+view.getWidth()/2f,y=xy[1]+view.getHeight()/2f;MotionEvent down=MotionEvent.obtain(time,time,0,x,y,0),up=MotionEvent.obtain(time,time+80,1,x,y,0);test.sendPointerSync(down);test.sendPointerSync(up);down.recycle();up.recycle();}
    private static void waitFor(Instrumentation test,BooleanSupplier condition,String label)throws Exception{long until=SystemClock.uptimeMillis()+10000;boolean[] result={false};do{test.runOnMainSync(()->result[0]=condition.getAsBoolean());if(result[0])return;SystemClock.sleep(80);}while(SystemClock.uptimeMillis()<until);throw new AssertionError(label);}
    private static void capture(Instrumentation test,String name)throws Exception{test.waitForIdleSync();SystemClock.sleep(500);Bitmap bitmap=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
}
