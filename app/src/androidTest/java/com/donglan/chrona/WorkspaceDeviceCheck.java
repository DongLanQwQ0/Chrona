package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.time.LocalDate;
import com.donglan.chrona.data.ScheduleFilterState;
import java.util.function.BooleanSupplier;

/** Native page gestures and week-edge behavior; no calendar writes or external service calls. */
final class WorkspaceDeviceCheck {
    static String toggleReentry(Instrumentation test) throws Exception {
        if (!test.getTargetContext().getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated followup package only");
        ScheduleWorkspaceActivity activity=(ScheduleWorkspaceActivity)test.startActivitySync(new Intent(test.getTargetContext(),CalendarOverviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CalendarVisibilityToggle[] toggle=new CalendarVisibilityToggle[1];
        try {
            test.runOnMainSync(()-> {
                toggle[0]=new CalendarVisibilityToggle(activity);
                ((ViewGroup)activity.getWindow().getDecorView()).addView(toggle[0],new ViewGroup.LayoutParams(120,120));
                toggle[0].setOnCheckedChangeListener((button,enabled)->{if(enabled)button.setChecked(false);});
                toggle[0].setChecked(true);
            });
            SystemClock.sleep(500);test.waitForIdleSync();
            require(!toggle[0].isChecked()&&Math.abs(((Number)field(toggle[0],"checkedFraction")).floatValue())<.001f,"Native CompoundButton rejection settles off visually");
            test.runOnMainSync(()->{toggle[0].setOnCheckedChangeListener(null);toggle[0].setChecked(true);});
            SystemClock.sleep(500);test.waitForIdleSync();
            require(toggle[0].isChecked()&&Math.abs(((Number)field(toggle[0],"checkedFraction")).floatValue()-1)<.001f,"Accepted native change still settles on");
            return "PASS: native reentrant rejection and accepted state animation";
        } finally {test.runOnMainSync(activity::finish);}
    }
    static String compactCalendar(Instrumentation test, android.os.Bundle args) throws Exception {
        ThemeStore.setMode(test.getTargetContext(),args.getString("theme","light"));
        ScheduleWorkspaceActivity activity=(ScheduleWorkspaceActivity)test.startActivitySync(new Intent(test.getTargetContext(),CalendarOverviewActivity.class).putExtra("open_week",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            waitFor(test,()->activity.currentPage() instanceof CalendarOverviewPage,"calendar page");
            CalendarOverviewPage calendar=(CalendarOverviewPage)activity.currentPage();
            DockNavigationLayout tabs=(DockNavigationLayout)field(calendar,"tabs");
            test.runOnMainSync(()->tabs.itemAt(2).performClick());
            waitFor(test,()->tabs.getWidth()>0,"calendar tabs layout");
            checkActions(test,calendar); checkDates(test,calendar);
            capture(test,"compact-calendar");
            CompactUiDeviceCheck.uniformLabels(test,tabs);
            CompactUiDeviceCheck.dockMotion(test,tabs,"compact-calendar-tabs",true);
            test.runOnMainSync(()->tabs.itemAt(0).performClick());
            waitFor(test,()->yearGridsFit(activity.getWindow().getDecorView())==12,"12 readable year grids");
            capture(test,"compact-calendar-year");
            return "PASS: full-width tabs, capsule collapse/reversal, calendar toggle and dates";
        } finally {test.runOnMainSync(activity::finish);}
    }
    private static int yearGridsFit(View view) {
        if(view instanceof YearMonthGridView) return view.getWidth() >= YearMonthGridView.minimumWidth(view.getContext()) ? 1 : 0;
        int count=0;
        if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) count+=yearGridsFit(group.getChildAt(i));
        return count;
    }
    static String dateClipping(Instrumentation test)throws Exception {
        ScheduleWorkspaceActivity activity=(ScheduleWorkspaceActivity)test.startActivitySync(new Intent(test.getTargetContext(),CalendarOverviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {waitFor(test,()->activity.currentPage() instanceof CalendarOverviewPage,"calendar page");checkDates(test,(CalendarOverviewPage)activity.currentPage());checkFilter(test,activity);return "PASS: calendar/filter text clips and outer press envelopes";}
        finally {test.runOnMainSync(activity::finish);}
    }
    static String run(Instrumentation test) throws Exception {
        if (!test.getTargetContext().getPackageName().endsWith(".validation")) throw new IllegalStateException("Validation package only");
        ZjuSettingsStore settings = new ZjuSettingsStore(test.getTargetContext());
        if (settings.enabled() || settings.configured() || !settings.account().isEmpty())
            throw new IllegalStateException("Isolated validation package must have no school account");
        ScheduleWorkspaceActivity activity = (ScheduleWorkspaceActivity)test.startActivitySync(new Intent(
                test.getTargetContext(), TimetableActivity.class).putExtra("open_week",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.waitForIdleSync();
        try {
        SchedulePage timetable = activity.currentPage(); View timetableView = timetable.content();
        View pager = (View)field(activity, "pager");
        checkOverlay(test, activity, pager);
        swipe(test, pager, .80f, .18f, 24);
        waitFor(test, () -> activity.selectedPage() == 1, "timetable to classroom swipe");
        require(activity.currentPage() instanceof ClassroomSummaryPage, "classroom page");
        swipe(test, pager, .80f, .18f, 24);
        waitFor(test, () -> activity.selectedPage() == 2, "classroom to calendar swipe");
        CalendarOverviewPage calendar = (CalendarOverviewPage)activity.currentPage();
        waitFor(test, () -> field(calendar, "horizontalViewport") instanceof FreeCalendarScrollView, "week view loaded");
        FreeCalendarScrollView horizontal = (FreeCalendarScrollView)field(calendar, "horizontalViewport");
        ScrollView vertical = (ScrollView)field(calendar, "verticalViewport");
        require(horizontal.getOverScrollMode() == View.OVER_SCROLL_NEVER && vertical.getOverScrollMode() == View.OVER_SCROLL_NEVER,
                "both axes disable platform overscroll");
        test.runOnMainSync(() -> { horizontal.scrollTo(0,0); vertical.scrollTo(0,0); });
        int[] xy = new int[2]; test.runOnMainSync(() -> horizontal.getLocationOnScreen(xy));
        drag(test, xy[0]+horizontal.getWidth()*.5f, xy[1]+80, xy[0]+horizontal.getWidth()*.5f, xy[1]+200);
        SystemClock.sleep(400);
        require(vertical.getScrollY() == 0, "vertical top stays clamped after outward fling");
        require(activity.selectedPage() == 2, "week gesture does not switch workspace pages");
        swipe(test,horizontal,.30f,.75f,80);
        SystemClock.sleep(400);
        require(horizontal.getScrollX()==0, "horizontal left edge stays clamped after outward fling");
        require(activity.selectedPage()==2, "week horizontal edge does not hand off to workspace");
        test.runOnMainSync(() -> activity.selectPage(0));
        waitFor(test, () -> activity.selectedPage() == 0, "Dock selection");
        require(activity.currentPage() == timetable && activity.currentPage().content() == timetableView, "retained timetable tree");
        checkBodyHandoff(test, activity, (SwipePagerLayout)pager, timetable);
        test.runOnMainSync(() -> activity.selectPage(2));
        waitFor(test, () -> activity.selectedPage() == 2, "calendar selected for date checks");
        checkDates(test, calendar);
        checkActions(test, calendar);
        capture(test,"workspace-calendar");
        checkFilter(test, activity);
        test.runOnMainSync(() -> { activity.selectPage(2); activity.selectPage(1); activity.selectPage(0); });
        waitFor(test, () -> activity.selectedPage() == 0 && Math.abs(((Number)field(activity,"position")).floatValue()) < .001f, "rapid selection settles");
        return "PASS: title swipes; synthetic timetable body scroll and edge handoff; overlay Dock; retained trees; shared date rails with swipe/reversal; week action widths; week edge clamp";
        } finally { test.runOnMainSync(activity::finish); }
    }
    private static void checkOverlay(Instrumentation test, ScheduleWorkspaceActivity activity, View pager) {
        test.runOnMainSync(() -> {
            View dock = (View)field(activity,"dockHost");
            View shell = (View)pager.getParent();
            require(dock.getParent() == shell.getParent(), "Dock is sibling overlay, not a pager row");
            int[] p=new int[2], d=new int[2];pager.getLocationOnScreen(p);dock.getLocationOnScreen(d);
            require(p[1]+pager.getHeight()>d[1]+dock.getHeight()/2, "pager extends beneath Dock");
        });
    }
    private static void checkBodyHandoff(Instrumentation test, ScheduleWorkspaceActivity activity,
            SwipePagerLayout pager, SchedulePage timetable) throws Exception {
        // A temporary wide body exercises the real workspace pager without importing school/calendar data.
        FrameLayout body=(FrameLayout)field(timetable,"body");
        Object session=field(timetable,"session");
        waitFor(test, () -> (Boolean)field(session,"initialized") && !(Boolean)field(session,"busy")
                && !(Boolean)field(session,"schoolLoading") && body.isShown() && body.getWidth()>0
                && body.getWidth()==((Number)field(timetable,"gridWidth")).intValue(),
                "timetable loading and body layout finish before synthetic fixture");
        HorizontalScrollView synthetic=new HorizontalScrollView(activity);
        test.runOnMainSync(() -> {
            TextView grid=new TextView(activity); grid.setText("Synthetic timetable body");
            // HorizontalScrollView measures its child with UNSPECIFIED width; a LayoutParams
            // width alone does not make short text wide. Keep a real scrollable content minimum.
            grid.setMinWidth(pager.getWidth()*4);
            synthetic.addView(grid,new FrameLayout.LayoutParams(pager.getWidth()*4,-1));
            body.addView(synthetic,new FrameLayout.LayoutParams(-1,-1));
        });
        try {
            try {
                waitFor(test, () -> synthetic.isAttachedToWindow() && synthetic.getWidth()>0
                        && synthetic.canScrollHorizontally(1), "synthetic body laid out");
            } catch(AssertionError timeout) {
                test.runOnMainSync(() -> {
                    throw new AssertionError("Synthetic fixture: attached="+synthetic.isAttachedToWindow()
                            +", viewportWidth="+synthetic.getWidth()+", contentWidth="+synthetic.getChildAt(0).getWidth()
                            +", busy="+field(session,"busy")+", schoolLoading="+field(session,"schoolLoading"),timeout);
                });
                throw timeout;
            }
            test.runOnMainSync(() -> pager.addGesturePriorityChild(synthetic));
            swipe(test,synthetic,.75f,.35f,60);
            test.waitForIdleSync();
            require(activity.selectedPage()==0 && synthetic.getScrollX()>0, "body owns inner horizontal scroll");
            StringBuilder trace=new StringBuilder();
            try {
                // Start a new scroller trajectory, rather than scrollTo while the inner fling
                // still owns an older trajectory that can overwrite that position next frame.
                test.runOnMainSync(() -> synthetic.smoothScrollTo(synthetic.getChildAt(0).getWidth(),0));
                long[] edgeSince={0}; int[] lastX={-1};
                waitFor(test, () -> {
                    int x=synthetic.getScrollX();
                    if(synthetic.canScrollHorizontally(1) || x!=lastX[0])edgeSince[0]=0;
                    lastX[0]=x;
                    if(edgeSince[0]==0 && !synthetic.canScrollHorizontally(1))edgeSince[0]=SystemClock.uptimeMillis();
                    return edgeSince[0]!=0 && SystemClock.uptimeMillis()-edgeSince[0]>=400;
                }, "body right edge stable across frames");
                int[] xy=new int[2]; test.runOnMainSync(() -> synthetic.getLocationOnScreen(xy));
                float y=xy[1]+dp(synthetic,60);
                int[] step={0};
                drag(test,xy[0]+synthetic.getWidth()*.80f,y,xy[0]+synthetic.getWidth()*.18f,y,()->{
                    int sample=step[0]++;
                    if(sample==0 || sample==4 || sample==8 || sample==11 || sample==12)
                        trace.append("\nstep ").append(sample).append(": ").append(handoffState(activity,pager,synthetic));
                });
                waitFor(test, () -> activity.selectedPage()==1, "timetable body edge hands off to classroom");
            } catch(AssertionError timeout) {
                test.runOnMainSync(() -> trace.append("\nfinal: ").append(handoffState(activity,pager,synthetic)));
                throw new AssertionError(timeout.getMessage()+trace,timeout);
            }
        } finally { test.runOnMainSync(() -> body.removeView(synthetic)); }
    }
    private static String handoffState(ScheduleWorkspaceActivity activity, SwipePagerLayout pager,
            HorizontalScrollView body) {
        return "scrollX="+body.getScrollX()+", canScrollRight="+body.canScrollHorizontally(1)
                +", viewportWidth="+body.getWidth()+", contentWidth="+body.getChildAt(0).getWidth()
                +", attached="+body.isAttachedToWindow()+", horizontalEdgeHandoff="+field(pager,"horizontalEdgeHandoff")
                +", horizontalAtDown="+(field(pager,"horizontalAtDown")==body?"synthetic":field(pager,"horizontalAtDown"))
                +", touchStartedInPriorityChild="+field(pager,"touchStartedInPriorityChild")
                +", pagingGesture="+field(pager,"pagingGesture")+", startedAtSystemEdge="+field(pager,"startedAtSystemEdge")
                +", selected="+activity.selectedPage()+", position="+field(activity,"position");
    }
    private static void checkDates(Instrumentation test, CalendarOverviewPage calendar) throws Exception {
        DateIntervalBar rail=(DateIntervalBar)field(calendar,"dateTitle");
        LocalDate initial=(LocalDate)field(calendar,"date");
        swipe(test,rail,.70f,.30f,24);
        waitFor(test, () -> initial.plusWeeks(1).equals(field(calendar,"date")), "date rail swipe advances week");
        // Opposite arrow clicks happen within one animation frame, before either transition can finish.
        test.runOnMainSync(() -> { rail.getChildAt(1).performClick(); rail.getChildAt(2).performClick(); rail.getChildAt(1).performClick(); });
        waitFor(test, () -> initial.equals(field(calendar,"date")) && railSettled(rail), "calendar rapid reversal final date and label");
        checkRailClipping(test,rail,"calendar");
    }
    private static boolean railSettled(DateIntervalBar rail) {
        TextView label=(TextView)field(rail,"value");
        return label.getText().toString().equals(rail.displayText()) && Math.abs(label.getTranslationX())<.5f
                && label.getAlpha()>.99f && !((Boolean)field(rail,"moving"));
    }
    private static void checkActions(Instrumentation test, CalendarOverviewPage calendar) throws Exception {
        View slot=(View)field(calendar,"weekExportSlot");
        ViewGroup group=(ViewGroup)slot.getParent();
        View add=group.getChildAt(group.getChildCount()-1);
        View source=(View)field(calendar,"sourceSwitch");
        require(source instanceof CalendarVisibilityToggle && source.getParent()==group
                && group instanceof HeaderActionGroup,"system calendar toggle shares header capsule");
        DockNavigationLayout tabs=(DockNavigationLayout)field(calendar,"tabs");
        int expanded=dp(slot,45), addWidth=dp(slot,44), systemWidth=dp(slot,44)+dp(slot,1);
        require(tabs.getWidth()==((View)tabs.getParent()).getWidth(),"calendar tabs fill content width");
        waitFor(test, () -> slot.getWidth()==expanded && group.getWidth()==systemWidth+expanded+addWidth, "week three-action capsule expanded width");
        test.runOnMainSync(() -> tabs.itemAt(1).performClick());
        waitFor(test, () -> slot.getVisibility()==View.GONE && slot.getLayoutParams().width==0
                && group.getWidth()==systemWidth+addWidth && add.getWidth()==addWidth, "month hides export and collapses group");
        test.runOnMainSync(() -> { tabs.itemAt(2).performClick(); tabs.itemAt(3).performClick(); tabs.itemAt(2).performClick(); });
        waitFor(test, () -> slot.getVisibility()==View.VISIBLE && slot.getWidth()==expanded
                && group.getWidth()==systemWidth+expanded+addWidth && add.getWidth()==addWidth, "rapid mode reversal restores week group");
    }
    private static void checkFilter(Instrumentation test, ScheduleWorkspaceActivity activity) throws Exception {
        ScheduleFilterState original=new ScheduleFilterState(ScheduleFilterState.DATE,0,0,0,3,
                LocalDate.of(2026,10,19),LocalDate.of(2026,10,19));
        java.lang.reflect.Constructor<ScheduleFilterSheet> constructor=ScheduleFilterSheet.class.getDeclaredConstructor(
                android.app.Activity.class,ScheduleFilterState.class,ScheduleFilterSheet.DatePicker.class,java.util.function.Consumer.class);
        constructor.setAccessible(true);
        ScheduleFilterSheet[] holder=new ScheduleFilterSheet[1];
        test.runOnMainSync(() -> {
            try {
                holder[0]=constructor.newInstance(activity,original,
                        (ScheduleFilterSheet.DatePicker)(title,initial,chosen)->{},
                        (java.util.function.Consumer<ScheduleFilterState>)value->{throw new AssertionError("Must not apply synthetic filter");});
                java.lang.reflect.Method show=ScheduleFilterSheet.class.getDeclaredMethod("show");show.setAccessible(true);show.invoke(holder[0]);
            } catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        });
        ScheduleFilterSheet sheet=holder[0];
        android.app.Dialog dialog=(android.app.Dialog)field(sheet,"dialog");
        try {
            DateIntervalBar rail=(DateIntervalBar)field(sheet,"interval");
            ScheduleFilterState draft=(ScheduleFilterState)field(sheet,"draft");
            waitFor(test, () -> rail.isShown() && rail.getWidth()>0, "filter uses visible shared date rail");
            swipe(test,rail,.70f,.30f,24);
            waitFor(test, () -> draft.date.equals(original.date.plusDays(1)), "filter swipe advances day");
            test.runOnMainSync(() -> { rail.getChildAt(1).performClick(); rail.getChildAt(2).performClick(); rail.getChildAt(1).performClick(); });
            waitFor(test, () -> draft.date.equals(original.date) && railSettled(rail), "filter reversal final date and label");
            require(original.date.equals(LocalDate.of(2026,10,19)), "filter leaves original state untouched");
            checkRailClipping(test,rail,"filter");
            capture(test,"workspace-date-filter");
        } finally { test.runOnMainSync(dialog::dismiss); }
    }
    private static void capture(Instrumentation test,String name) throws Exception {
        test.waitForIdleSync();SystemClock.sleep(300);
        android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private static void checkRailClipping(Instrumentation test,DateIntervalBar rail,String name)throws Exception {
        TextView label=(TextView)field(rail,"value");View host=(View)label.getParent();
        int[] xy=new int[2],textXY=new int[2];
        capture(test,name+"-before-clipping");
        test.runOnMainSync(()->{
            require(label.getLayout()!=null&&label.getLayout().getHeight()<=label.getHeight(),"full date range fits without reducing font: layout="+(label.getLayout()==null?null:label.getLayout().getHeight())+", label="+label.getWidth()+"x"+label.getHeight()+", rail="+rail.getWidth()+"x"+rail.getHeight());
            require(label.getText().toString().replace("\n"," — ").equals(rail.text()),"both date endpoints retained");
            require(host.getClipBounds()!=null&&host.getClipBounds().width()==host.getWidth(),"explicit date text viewport");
            rail.getLocationOnScreen(xy);host.getLocationOnScreen(textXY);label.setAlpha(0);
        });
        SystemClock.sleep(200);android.graphics.Bitmap baseline=test.getUiAutomation().takeScreenshot();
        try{
            for(int direction:new int[]{-1,1}){
                test.runOnMainSync(()->{label.setAlpha(1);label.setTranslationX(direction*label.getWidth()*.8f);});
                SystemClock.sleep(120);android.graphics.Bitmap moved=test.getUiAutomation().takeScreenshot();
                try{
                    int different=0;
                    for(int y=xy[1]+dp(rail,8);y<xy[1]+rail.getHeight()-dp(rail,8);y++)
                        for(int x=xy[0]+dp(rail,3);x<xy[0]+rail.getWidth()-dp(rail,3);x++)
                            if(x<textXY[0]-1||x>textXY[0]+host.getWidth()+1){
                                int a=baseline.getPixel(x,y),b=moved.getPixel(x,y);
                                // Ignore compositor rounding, not visible text pixels.
                                if(Math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))>4
                                        ||Math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))>4
                                        ||Math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))>4)different++;
                            }
                    if(different>=8){
                        saveBitmap(test,baseline,"date-clip-baseline");saveBitmap(test,moved,"date-clip-moved");
                    }
                    require(different<8,name+" date text stays out of arrow zones: "+different
                            +" rail="+xy[0]+","+xy[1]+" text="+textXY[0]+" width="+host.getWidth()+" scale="+rail.getScaleX());
                }finally{moved.recycle();}
            }
            capture(test,"workspace-date-clip-"+name);
        }finally{baseline.recycle();test.runOnMainSync(()->{label.setTranslationX(0);label.setAlpha(1);});}
        test.runOnMainSync(()->rail.setPressed(true));SystemClock.sleep(250);
        test.runOnMainSync(()->{
            android.graphics.Rect visible=new android.graphics.Rect();rail.getGlobalVisibleRect(visible);
            require(rail.getScaleX()>1,"date rail press enlarges");
            require(visible.width()>=Math.floor(rail.getWidth()*rail.getScaleX())-2,"date rail outer press envelope is visible");
            rail.setPressed(false);
        });SystemClock.sleep(200);
    }
    private static void saveBitmap(Instrumentation test,android.graphics.Bitmap bitmap,String name)throws Exception{
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
    }
    private static int dp(View view,int value){return Math.round(value*view.getResources().getDisplayMetrics().density);}
    static void swipe(Instrumentation test, View view, float from, float to, int yDp) throws Exception {
        int[] xy = new int[2]; test.runOnMainSync(() -> view.getLocationOnScreen(xy));
        float y = xy[1]+yDp*view.getResources().getDisplayMetrics().density;
        drag(test, xy[0]+view.getWidth()*from, y, xy[0]+view.getWidth()*to, y);
    }
    private static void drag(Instrumentation test, float x, float y, float endX, float endY) throws Exception {
        drag(test,x,y,endX,endY,null);
    }
    private static void drag(Instrumentation test, float x, float y, float endX, float endY, Runnable sample) throws Exception {
        long start = SystemClock.uptimeMillis();
        for (int i = 0; i <= 12; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 12 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            float f = i / 12f;
            MotionEvent event = MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x+(endX-x)*f,y+(endY-y)*f,0);
            test.sendPointerSync(event); event.recycle(); SystemClock.sleep(20);
            if(sample!=null)test.runOnMainSync(sample);
        }
    }
    private static Object field(Object object, String name) {
        Class<?> type = object.getClass();
        while (type != null) try { Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object); }
        catch (NoSuchFieldException missing) { type=type.getSuperclass(); }
        catch (Exception error) { throw new IllegalStateException(error); }
        throw new IllegalStateException(name);
    }
    private static void waitFor(Instrumentation test, BooleanSupplier ready, String label) throws Exception {
        long end=SystemClock.uptimeMillis()+20000;
        while(SystemClock.uptimeMillis()<end) {test.waitForIdleSync();boolean[] result={false};test.runOnMainSync(()->result[0]=ready.getAsBoolean());if(result[0])return;SystemClock.sleep(80);}
        throw new AssertionError(label);
    }
    private static void require(boolean value,String label) { if(!value)throw new AssertionError(label); }
}
