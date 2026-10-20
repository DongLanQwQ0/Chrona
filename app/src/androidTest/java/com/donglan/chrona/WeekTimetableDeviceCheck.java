package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.timetable.*;
import com.donglan.chrona.zju.ZjuSnapshot;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Called only inside ClassroomDeviceCheck's isolated, emulator-only fixture. */
final class WeekTimetableDeviceCheck {
    private static int assertions;
    static String run(Instrumentation test, Bundle args, ClassroomStore store,
            List<ClassroomLesson> lessons, LocalDate monday) throws Exception {
        assertions=0;
        for(int week=-1;week<=1;week++)for(int hour=6;hour<21;hour++)
            lessons.add(new ClassroomLesson("400","w"+week+"h"+hour,"周切换课程","第 "+hour+" 讲","东1","6",
                    monday.plusWeeks(week).atTime(hour,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli()));
        store.catalog(lessons);
        Activity activity=test.startActivitySync(new Intent(test.getTargetContext(),ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ScheduleWorkspaceActivity host=(ScheduleWorkspaceActivity)activity;
            waitFor(()->host.currentPage() instanceof ClassroomSummaryPage,"classroom page");
            Object page=host.currentPage();Object session=field(page,"s");
            waitFor(()->Boolean.TRUE.equals(field(session,"initialized"))&&!Boolean.TRUE.equals(field(session,"busy"))
                    &&field(page,"shownCatalog")==field(session,"catalog")
                    &&field(page,"weekPicker")!=null&&((View)field(page,"weekPicker")).getWidth()>0,"catalogue loaded");
            View bar=(View)field(page,"weekBar");
            HorizontalScrollView picker=(HorizontalScrollView)field(page,"weekPicker");
            shot(test,"classroom-week-before-"+args.getString("theme","light"));
            WorkspaceDeviceCheck.swipe(test,picker,.8f,.2f,20);SystemClock.sleep(450);
            require(monday.equals(field(session,"week"))&&picker.getScrollX()>0,"ordinary horizontal pan stays in week: week="+field(session,"week")+" x="+picker.getScrollX()+" width="+picker.getWidth()+" content="+picker.getChildAt(0).getWidth()+" attached="+picker.isAttachedToWindow()+" same="+(picker==field(page,"weekPicker"))+" down="+field(picker,"downX")+" last="+field(picker,"lastX"));
            test.runOnMainSync(()->picker.scrollTo(0,0));
            WorkspaceDeviceCheck.swipe(test,picker,.15f,.85f,20);
            waitFor(()->monday.minusWeeks(1).equals(field(page,"shownWeek"))&&!Boolean.TRUE.equals(field(page,"weekTransition")),"right edge swipe shows previous week");
            require(field(page,"weekBar")==bar,"week header stays attached");
            HorizontalScrollView previous=(HorizontalScrollView)field(page,"weekPicker");
            test.runOnMainSync(()->previous.scrollTo(100000,0));
            WorkspaceDeviceCheck.swipe(test,previous,.85f,.15f,20);
            waitFor(()->monday.equals(field(page,"shownWeek"))&&!Boolean.TRUE.equals(field(page,"weekTransition")),"left edge swipe shows next week");
            HorizontalScrollView current=(HorizontalScrollView)field(page,"weekPicker");
            test.runOnMainSync(()->current.scrollTo(0,0));
            WorkspaceDeviceCheck.swipe(test,current,.45f,.5f,20);SystemClock.sleep(350);
            require(monday.equals(field(session,"week")),"short drag leaves week unchanged");
            drag(test,current,.5f,.5f,180,30,android.view.MotionEvent.ACTION_UP);
            SystemClock.sleep(250);
            android.widget.ScrollView vertical=(android.widget.ScrollView)field(page,"scroll");
            require(monday.equals(field(session,"week"))&&vertical.getScrollY()>0,"vertical pan scrolls without changing week");
            test.runOnMainSync(()->{vertical.scrollTo(0,0);current.scrollTo(0,0);});
            drag(test,current,.2f,.8f,20,20,android.view.MotionEvent.ACTION_CANCEL);
            require(monday.equals(field(session,"week")),"cancelled edge drag keeps week");
            multiTouch(test,current);
            require(monday.equals(field(session,"week")),"multi-touch movement does not change week");
            require(host.selectedPage()==1,"table gestures stay in classroom workspace page");
            test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday.plusWeeks(1)));
            waitFor(()->field(page,"weekMotion")!=null,"week motion starts");
            test.runOnMainSync(()->{
                android.animation.ValueAnimator motion=(android.animation.ValueAnimator)field(page,"weekMotion");
                motion.pause();motion.setCurrentFraction(.5f);
            });
            View incoming=(View)field(page,"weekPicker"),outgoing=(View)field(page,"outgoingWeek");
            require(Math.abs(incoming.getTranslationX()-outgoing.getTranslationX()-incoming.getWidth())<2,"moving page edges meet without overlapping");
            shot(test,"classroom-week-mid-"+args.getString("theme","light"));
            test.runOnMainSync(()->{
                invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday.minusWeeks(1));
                ((android.animation.ValueAnimator)field(page,"weekMotion")).resume();
            });
            waitFor(()->monday.minusWeeks(1).equals(field(page,"shownWeek"))&&!Boolean.TRUE.equals(field(page,"weekTransition")),"rapid reversal reaches latest selected week");
            require(((ViewGroup)field(page,"weekViewport")).getChildCount()==1
                    &&((View)field(page,"weekPicker")).getTranslationX()==0,"settled week leaves one untransformed tree");
            test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday));
            waitFor(()->monday.equals(field(page,"shownWeek"))&&!Boolean.TRUE.equals(field(page,"weekTransition")),"return to current week");
            HorizontalScrollView settled=(HorizontalScrollView)field(page,"weekPicker");
            test.runOnMainSync(()->settled.scrollTo(200,0));int offset=settled.getScrollX();
            Method setScale=android.animation.ValueAnimator.class.getDeclaredMethod("setDurationScale",float.class);
            float scale=android.animation.ValueAnimator.getDurationScale();
            try {
                test.runOnMainSync(()->{try{setScale.invoke(null,0f);}catch(Exception e){throw new IllegalStateException(e);}});
                require(!UiMotion.isEnabled(),"reduced motion active");
                test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday.plusWeeks(1)));
                waitFor(()->((View)field(page,"weekPicker")).getWidth()>0,"reduced motion target laid out");
                require(((HorizontalScrollView)field(page,"weekPicker")).getScrollX()==offset
                        &&field(page,"weekMotion")==null,"reduced motion switch retains horizontal position");
                test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday));
            }finally{test.runOnMainSync(()->{try{setScale.invoke(null,scale);}catch(Exception e){throw new IllegalStateException(e);}});}
            waitFor(()->monday.equals(field(page,"shownWeek"))&&!Boolean.TRUE.equals(field(page,"weekTransition"))
                    &&((View)field(page,"weekPicker")).getWidth()>0,"stable current-week tree");
            require(((TextView)((ViewGroup)((ViewGroup)((HorizontalScrollView)field(page,"weekPicker")).getChildAt(0)).getChildAt(0)).getChildAt(1)).getText().toString()
                    .startsWith(monday.format(java.time.format.DateTimeFormatter.ofPattern("M/d"))),"header and grid show the same settled week");
            shot(test,"classroom-week-"+args.getString("theme","light"));
        }finally{test.runOnMainSync(activity::finish);}
        timetable(test,args,monday,store);
        return new JSONObject().put("passed",true).put("assertions",assertions).toString();
    }

    private static void timetable(Instrumentation test,Bundle args,LocalDate monday,ClassroomStore store)throws Exception{
        ArrayList<ZjuSnapshot.Lesson> rows=new ArrayList<>();
        for(int week=0;week<4;week++)for(int day=0;day<5;day++){
            long start=monday.plusWeeks(week).plusDays(day).atTime(8+day,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
            rows.add(new ZjuSnapshot.Lesson("r"+week+"d"+day,"课程 "+day,"东1","2026-2027 秋",start,start+5400000,"实际日期"));
        }
        long makeup=monday.plusDays(5).atTime(8,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
        rows.add(new ZjuSnapshot.Lesson("makeup","课程 0","东1","2026-2027 秋",makeup,makeup+5400000,"单次调课"));
        new ZjuTimetableStore(test.getTargetContext()).save(new ZjuSettingsStore(test.getTargetContext()).account(),rows,List.of("2026-2027 秋"));
        StringBuilder ics=new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nX-WR-CALNAME:2026-2027秋冬\r\n");
        for(String start:List.of("20260914","20261109"))for(int day=0;day<5;day++){
            LocalDate date=LocalDate.parse(start,java.time.format.DateTimeFormatter.BASIC_ISO_DATE).plusDays(day);
            String stamp=date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            ics.append("BEGIN:VEVENT\r\nUID:").append(stamp).append("\r\nDTSTART;TZID=Asia/Shanghai:").append(stamp).append("T080000\r\nDTEND;TZID=Asia/Shanghai:").append(stamp).append("T093000\r\nSUMMARY:课程 ").append(day).append("\r\nRRULE:FREQ=WEEKLY;COUNT=4\r\nEND:VEVENT\r\n");
        }
        ics.append("END:VCALENDAR\r\n");
        TimetableStore.Document doc=TimetableStore.prepare("合成学期",ics.toString(),ClassroomLesson.ZONE);
        doc=doc.configured(new AcademicTerms.Plan(2026,0,LocalDate.of(2026,9,14),LocalDate.of(2026,11,9),LocalDate.of(2026,12,31)));
        new TimetableStore(test.getTargetContext()).save(new TimetableStore.Library(List.of(doc),"2026/0"));
        Activity activity=test.startActivitySync(new Intent(test.getTargetContext(),TimetableActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            ScheduleWorkspaceActivity host=(ScheduleWorkspaceActivity)activity;waitFor(()->host.currentPage() instanceof TimetablePage,"timetable page");Object page=host.currentPage(),session=field(page,"session");
            waitFor(()->!Boolean.TRUE.equals(field(session,"schoolLoading"))&&field(page,"horizontal")!=null&&((View)field(page,"horizontal")).getWidth()>0,"school overview ready");
            TimetableGrid grid=findType((View)field(page,"body"),TimetableGrid.class);
            require(grid!=null,"regular school grid retained");
            require(findText((View)field(page,"body"),"特殊安排 · 1")!=null,"single makeup appears below grid");
            shot(test,"timetable-school-"+args.getString("theme","light"));
            View row=findText((View)field(page,"body"),"课程 0\n");require(row!=null,"actual makeup date remains selectable");
            test.runOnMainSync(()->row.requestRectangleOnScreen(new android.graphics.Rect(0,0,row.getWidth(),row.getHeight()),true));
            shot(test,"timetable-school-special-"+args.getString("theme","light"));
            test.runOnMainSync(row::performClick);SystemClock.sleep(250);
            android.app.Dialog dialog=(android.app.Dialog)field(page,"floating");
            require(dialog!=null&&dialog.isShowing()&&findText(dialog.getWindow().getDecorView(),"移至特殊安排")==null,"school makeup detail is read only");
            test.runOnMainSync(dialog::dismiss);
            test.runOnMainSync(()->invoke(page,"selectSource",new Class<?>[]{int.class},1));
            waitFor(()->field(page,"horizontal")!=null&&((View)field(page,"horizontal")).getWidth()>0&&!Boolean.TRUE.equals(field(session,"busy")),"imported grid ready");
            HorizontalScrollView horizontal=(HorizontalScrollView)field(page,"horizontal");
            test.runOnMainSync(()->horizontal.scrollTo(320,0));require(horizontal.getScrollX()>0,"import grid has horizontal offset");
            int x=horizontal.getScrollX();List<Integer> firstDraws=new ArrayList<>();Set<View> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            View body=(View)field(page,"body");
            android.view.ViewTreeObserver.OnDrawListener watcher=()->{
                HorizontalScrollView h=(HorizontalScrollView)field(page,"horizontal");
                if(h!=null&&seen.add(h))firstDraws.add(h.getScrollX());
            };
            test.runOnMainSync(()->body.getViewTreeObserver().addOnDrawListener(watcher));
            for(String id:List.of("2026/1","2026/0")){
                test.runOnMainSync(()->invoke(page,"selectSemester",new Class<?>[]{String.class},id));
                waitFor(()->!Boolean.TRUE.equals(field(session,"busy"))&&id.equals(((TimetableStore.Library)field(session,"library")).selected),"semester saved");SystemClock.sleep(400);
                require(((HorizontalScrollView)field(page,"horizontal")).getScrollX()==x,"semester retains horizontal offset");
            }
            test.runOnMainSync(()->body.getViewTreeObserver().removeOnDrawListener(watcher));
            require(firstDraws.size()>=2&&firstDraws.stream().allMatch(value->value==x),"every rebuilt grid starts its first drawn frame at retained offset");
            shot(test,"timetable-import-"+args.getString("theme","light"));
        }finally{test.runOnMainSync(activity::finish);}
    }
    private static Object field(Object o,String name){for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}catch(Exception e){throw new IllegalStateException(e);}throw new IllegalArgumentException(name);}
    private static void invoke(Object o,String name,Class<?>[] types,Object...args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void waitFor(BooleanSupplier condition,String label){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(condition.getAsBoolean()){assertions++;return;}SystemClock.sleep(15);}throw new AssertionError(label);}
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);assertions++;}
    private static <T extends View>T findType(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T v=findType(group.getChildAt(i),type);if(v!=null)return v;}return null;}
    private static View findText(View root,String prefix){if(root instanceof TextView text&&text.getText().toString().startsWith(prefix))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View v=findText(group.getChildAt(i),prefix);if(v!=null)return v;}return null;}
    private static void shot(Instrumentation test,String name)throws Exception{SystemClock.sleep(100);android.graphics.Bitmap b=test.getUiAutomation().takeScreenshot();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getFilesDir(),name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private static void drag(Instrumentation test,View view,float from,float to,int top,int bottom,int finish)throws Exception{
        int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));float density=view.getResources().getDisplayMetrics().density;
        long down=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){
            float p=i/12f;int action=i==0?0:i==12?finish:2;
            android.view.MotionEvent e=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,xy[0]+view.getWidth()*(from+(to-from)*p),xy[1]+(top+(bottom-top)*p)*density,0);
            test.sendPointerSync(e);e.recycle();SystemClock.sleep(20);
        }
    }
    private static void multiTouch(Instrumentation test,View view)throws Exception{
        int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));long down=SystemClock.uptimeMillis();
        android.view.MotionEvent.PointerProperties[] properties=new android.view.MotionEvent.PointerProperties[2];
        android.view.MotionEvent.PointerCoords[] coords=new android.view.MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++){properties[i]=new android.view.MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=1;
            coords[i]=new android.view.MotionEvent.PointerCoords();coords[i].x=xy[0]+view.getWidth()*(.2f+i*.1f);coords[i].y=xy[1]+30;coords[i].pressure=1;coords[i].size=1;}
        for(int step=0;step<5;step++){
            int count=step==0||step==4?1:2;int action=step==0?0:step==1?5|(1<<8):step==2?2:step==3?6|(1<<8):1;
            if(step==2)for(var c:coords)c.x+=view.getWidth()*.5f;
            android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
            test.sendPointerSync(event);event.recycle();SystemClock.sleep(40);
        }
    }
}
