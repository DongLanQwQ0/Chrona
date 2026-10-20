package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
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
            if("empty_viewport".equals(args.getString("scope"))){
                HorizontalScrollView old=(HorizontalScrollView)field(page,"weekPicker");
                test.runOnMainSync(()->{old.scrollTo(200,0);invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday.plusWeeks(8));});
                waitFor(()->!Boolean.TRUE.equals(field(page,"weekTransition"))&&((View)field(page,"weekPicker")).getWidth()>0,"empty week settled");
                HorizontalScrollView empty=(HorizontalScrollView)field(page,"weekPicker");
                require(empty.getScrollX()==200,"empty week preserves horizontal offset");
                TextView label=(TextView)findText(empty,"本周暂无课堂");
                android.graphics.Rect visible=new android.graphics.Rect();label.getGlobalVisibleRect(visible);
                require(visible.width()==label.getWidth()&&visible.height()==label.getHeight(),"entire empty state visible at retained offset");
                require(!((android.widget.ScrollView)field(page,"scroll")).canScrollVertically(1),"empty week has no vertical range");
                shot(test,"classroom-viewport-empty-fixed-"+args.getString("theme","light"));
                test.runOnMainSync(()->empty.scrollTo(100000,0));
                SystemClock.sleep(100);label.getGlobalVisibleRect(visible);
                require(visible.width()==label.getWidth(),"empty state remains visible at far horizontal edge");
                return new JSONObject().put("passed",true).put("assertions",assertions).toString();
            }
            View bar=(View)field(page,"weekBar");
            HorizontalScrollView picker=(HorizontalScrollView)field(page,"weekPicker");
            View header=findType(host.currentPage().content(),WorkspacePageHeader.class);
            int[] headerBefore=position(header),dateBefore=position(bar);
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
            require(Arrays.equals(headerBefore,position(header))&&Arrays.equals(dateBefore,position(bar)),"header and dates remain fixed during table scrolling");
            smoothVerticalDrag(test,current,vertical);
            shot(test,"classroom-viewport-scrolled-"+args.getString("theme","light"));
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
            test.waitForIdleSync();SystemClock.sleep(100);
            HorizontalScrollView settled=(HorizontalScrollView)field(page,"weekPicker");
            test.runOnMainSync(()->settled.scrollTo(200,0));int offset=settled.getScrollX();
            Method setScale=android.animation.ValueAnimator.class.getDeclaredMethod("setDurationScale",float.class);
            float scale=android.animation.ValueAnimator.getDurationScale();
            try {
                test.runOnMainSync(()->{try{setScale.invoke(null,0f);}catch(Exception e){throw new IllegalStateException(e);}});
                require(!UiMotion.isEnabled(),"reduced motion active");
                test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday.plusWeeks(1)));
                waitFor(()->((View)field(page,"weekPicker")).getWidth()>0&&((View)field(page,"weekPicker")).getScrollX()==offset,"reduced motion target laid out at retained offset");
                require(((HorizontalScrollView)field(page,"weekPicker")).getScrollX()==offset
                        &&field(page,"weekMotion")==null,"reduced motion switch retains horizontal position: expected="+offset+" actual="+((View)field(page,"weekPicker")).getScrollX());
                test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday));
            }finally{test.runOnMainSync(()->{try{setScale.invoke(null,scale);}catch(Exception e){throw new IllegalStateException(e);}});}
            waitFor(()->monday.equals(field(page,"shownWeek"))&&!Boolean.TRUE.equals(field(page,"weekTransition"))
                    &&((View)field(page,"weekPicker")).getWidth()>0,"stable current-week tree");
            require(((TextView)((ViewGroup)table((HorizontalScrollView)field(page,"weekPicker")).getChildAt(0)).getChildAt(1)).getText().toString()
                    .startsWith(monday.format(java.time.format.DateTimeFormatter.ofPattern("M/d"))),"header and grid show the same settled week");
            shot(test,"classroom-week-"+args.getString("theme","light"));
            if("viewport_status".equals(args.getString("scope")))viewportStatus(test,args,host,page,session,store,lessons,monday);
        }finally{test.runOnMainSync(activity::finish);}
        if(!"viewport_status".equals(args.getString("scope")))timetable(test,args,monday,store);
        return new JSONObject().put("passed",true).put("assertions",assertions).toString();
    }

    private static void viewportStatus(Instrumentation test,Bundle args,ScheduleWorkspaceActivity host,Object page,Object session,
            ClassroomStore store,List<ClassroomLesson> lessons,LocalDate monday)throws Exception {
        android.widget.ScrollView scroll=(android.widget.ScrollView)field(page,"scroll");
        test.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));SystemClock.sleep(200);
        ViewGroup table=table((HorizontalScrollView)field(page,"weekPicker"));
        View last=((ViewGroup)table).getChildAt(((ViewGroup)table).getChildCount()-1);
        android.graphics.Rect visible=new android.graphics.Rect();last.getGlobalVisibleRect(visible);
        require(visible.height()==last.getHeight(),"last table row is fully reachable above the workspace dock");
        test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday.plusWeeks(8)));
        waitFor(()->!Boolean.TRUE.equals(field(page,"weekTransition"))&&((View)field(page,"scroll")).getScrollY()==0,"empty week settles at top");
        android.widget.ScrollView emptyScroll=(android.widget.ScrollView)field(page,"scroll");
        require(!emptyScroll.canScrollVertically(1)&&!emptyScroll.canScrollVertically(-1),"short empty week has no vertical scroll range");
        shot(test,"classroom-viewport-empty-"+args.getString("theme","light"));
        test.runOnMainSync(()->invoke(page,"setWeek",new Class<?>[]{LocalDate.class},monday));
        waitFor(()->!Boolean.TRUE.equals(field(page,"weekTransition")),"current week restored");
        long now=System.currentTimeMillis();
        ClassroomLesson future=new ClassroomLesson("boundary","future","自动状态","到点可回看","东1","6",now+2500);
        ClassroomLesson processing=new ClassroomLesson("boundary","processing","远端状态","准备完成","东1","2",now-60000);
        List<ClassroomLesson> changed=new ArrayList<>(lessons);changed.add(future);changed.add(processing);
        test.runOnMainSync(()->{set(session,"catalog",changed);set(session,"nextRemoteAt",SystemClock.elapsedRealtime()+300000);invoke(page,"update",new Class<?>[]{});invoke(page,"scheduleRefresh",new Class<?>[]{});});
        View body=(View)field(page,"weekPicker");
        TextView timed=(TextView)findText(body,"自动状态");TextView remote=(TextView)findText(body,"远端状态");
        require(timed!=null&&!timed.isEnabled()&&timed.getText().toString().contains("未开始"),"future cell begins disabled");
        waitFor(()->timed.isEnabled()&&timed.getText().toString().contains("可回看"),"time boundary updates without a tap or rebuilding");
        require(body==field(page,"weekPicker"),"timer preserves the table instance");
        test.runOnMainSync(()->((HorizontalScrollView)body).scrollTo(150,0));int pan=body.getScrollX();
        List<ClassroomLesson> refreshed=new ArrayList<>(changed);refreshed.remove(processing);
        refreshed.add(new ClassroomLesson(processing.courseId,processing.id,processing.course,processing.title,processing.room,"6",processing.startsAt));
        test.runOnMainSync(()->{set(session,"catalog",refreshed);invoke(session,"publish",new Class<?>[]{});});
        waitFor(()->remote.isEnabled(),"remote preparation status updates existing cell");
        require(body==field(page,"weekPicker")&&body.getScrollX()==pan,"status-only refresh retains table and horizontal offset");
        require(((ClassroomLesson)remote.getTag()).selectable(System.currentTimeMillis()),"new clickable lesson uses the refreshed record");
        android.os.Handler handler=(android.os.Handler)field(page,"refreshHandler");Runnable tick=(Runnable)field(page,"refreshTick");
        test.runOnMainSync(()->invoke(page,"selectMode",new Class<?>[]{int.class},1));
        require(!handler.hasCallbacks(tick),"automatic catalogue work stops outside the table mode");
        test.runOnMainSync(()->invoke(page,"selectMode",new Class<?>[]{int.class},0));
        require(handler.hasCallbacks(tick),"table mode resumes automatic work");
        test.runOnMainSync(()->host.selectPage(0));
        waitFor(()->host.selectedPage()==0,"workspace transition leaves classroom page");
        require(!handler.hasCallbacks(tick)&&!Boolean.TRUE.equals(field(page,"active")),"leaving the page removes all refresh callbacks");
        test.runOnMainSync(()->host.selectPage(1));
        waitFor(()->host.selectedPage()==1&&!Boolean.TRUE.equals(field(session,"busy")),"resume cache read finishes");
        // Invalid synthetic credentials fail locally, before any school connection.
        test.runOnMainSync(()->{set(session,"nextRemoteAt",SystemClock.elapsedRealtime()-1);tick.run();});
        waitFor(()->!Boolean.TRUE.equals(field(session,"busy")),"automatic request failure completes locally");
        long next=(long)field(session,"nextRemoteAt");
        require(next-SystemClock.elapsedRealtime()>295000,"failed automatic attempt retains five-minute retry spacing");
        test.runOnMainSync(tick);
        require(!Boolean.TRUE.equals(field(session,"busy"))&&(long)field(session,"nextRemoteAt")==next,"timer does not immediately repeat a failed request");
        test.runOnMainSync(()->{set(session,"busy",true);load(session,"REFRESH");});
        require((long)field(session,"nextRemoteAt")==next,"busy refresh does not overlap or reset cadence");
        test.runOnMainSync(()->{set(session,"busy",false);set(session,"nextRemoteAt",SystemClock.elapsedRealtime()+900000);load(session,"REFRESH");});
        waitFor(()->!Boolean.TRUE.equals(field(session,"busy")),"manual refresh bypasses automatic cadence");
        require((long)field(session,"nextRemoteAt")-SystemClock.elapsedRealtime()<301000,"manual attempt resets normal cadence");
        store.catalog(lessons);
        test.runOnMainSync(()->{set(session,"initialized",false);load(session,"CACHE");});
        waitFor(()->!Boolean.TRUE.equals(field(session,"busy")),"fresh cached catalogue loaded");
        require((long)field(session,"nextRemoteAt")-SystemClock.elapsedRealtime()>290000,"fresh cache waits the remaining catalogue interval");
        require(new java.io.File((java.io.File)field(store,"root"),"catalog.json").setLastModified(System.currentTimeMillis()-400000),"fixture ages cached catalogue");
        test.runOnMainSync(()->{invoke(page,"onPause",new Class<?>[]{});set(session,"initialized",false);load(session,"CACHE");});
        waitFor(()->!Boolean.TRUE.equals(field(session,"busy")),"stale cached catalogue loaded while paused");
        require((long)field(session,"nextRemoteAt")<=SystemClock.elapsedRealtime(),"stale cache becomes immediately eligible");
        require(!handler.hasCallbacks(tick),"asynchronous completion cannot restart a paused page timer");
        test.runOnMainSync(()->invoke(page,"onResume",new Class<?>[]{}));
        waitFor(()->!Boolean.TRUE.equals(field(session,"busy")),"resumed catalogue settled");
        DockNavigationLayout navigation=(DockNavigationLayout)field(page,"navigation");
        require(navigation.isAttachedToWindow(),"Dock ready for fixed-header motion verification");
        CompactUiDeviceCheck.uniformLabels(test,navigation);
        require(true,"fixed-header label geometry verified");
        CompactUiDeviceCheck.dockMotion(test,navigation,"classroom-fixed-header-"+args.getString("theme","light"),true);
        require(field(page,"navigation")==navigation,"fixed header retains Dock through holds, drag-out and fast flings");
        new ZjuSettingsStore(test.getTargetContext()).prefs.edit().putBoolean("enabled",false).commit();
        waitFor(()->field(page,"s")==null&&!handler.hasCallbacks(tick),"account disable detaches session and removes timer after its UI callback");
        require(!handler.hasCallbacks(tick),"account disable removes timer");
    }
    private static int[] position(View view){int[] p=new int[2];view.getLocationOnScreen(p);return p;}
    private static ViewGroup table(HorizontalScrollView picker){return (ViewGroup)((android.widget.ScrollView)picker.getChildAt(0)).getChildAt(0);}
    private static void smoothVerticalDrag(Instrumentation test,HorizontalScrollView picker,android.widget.ScrollView scroll)throws Exception{
        require(scroll.getParent()==picker,"week agenda hierarchy: stationary horizontal owner outside native vertical viewport");
        test.runOnMainSync(()->scroll.scrollTo(0,0));SystemClock.sleep(80);
        int[] anchor=position(picker);float density=picker.getResources().getDisplayMetrics().density;
        float x=anchor[0]+picker.getWidth()*.5f,start=anchor[1]+300*density;long down=SystemClock.uptimeMillis();
        int previous=0;ArrayList<Integer> samples=new ArrayList<>();
        for(int i=0;i<=16;i++){
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),i==0?0:2,x,start-i*8*density,0);
            test.sendPointerSync(event);event.recycle();SystemClock.sleep(25);
            int[] current={0};test.runOnMainSync(()->current[0]=scroll.getScrollY());samples.add(current[0]);
            require(Arrays.equals(anchor,position(picker)),"gesture owner stays stationary at move "+i);
            require(current[0]>=previous,"vertical offset never reverses during an upward drag at move "+i+": "+samples);
            if(i>2)require(Math.abs(current[0]-i*8*density)<=i+2,"table follows cumulative finger travel without feedback oscillation at move "+i+": "+samples);
            previous=current[0];
        }
        MotionEvent cancel=MotionEvent.obtain(down,SystemClock.uptimeMillis(),3,x,start-128*density,0);test.sendPointerSync(cancel);cancel.recycle();
        android.util.Log.i("ClassroomViewportCheck","SCROLL_SAMPLES "+samples);
    }
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new IllegalStateException(e);}}
    @SuppressWarnings({"unchecked","rawtypes"}) private static void load(Object session,String kind){try{Class type=Class.forName("com.donglan.chrona.ClassroomSummaryPage$Load");invoke(session,"load",new Class<?>[]{type},Enum.valueOf(type,kind));}catch(Exception e){throw new IllegalStateException(e);}}

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
    private static void waitFor(BooleanSupplier condition,String label){android.util.Log.i("ClassroomViewportCheck","WAIT "+label);long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(condition.getAsBoolean()){assertions++;android.util.Log.i("ClassroomViewportCheck","PASS "+label);return;}SystemClock.sleep(15);}throw new AssertionError(label);}
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);assertions++;android.util.Log.i("ClassroomViewportCheck","PASS "+label);}
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
