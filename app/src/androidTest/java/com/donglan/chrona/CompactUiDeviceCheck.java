package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Rendered header geometry and compact home fixtures in the isolated package only. */
final class CompactUiDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context=test.getTargetContext();
        if(!context.getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated followup package only");
        ZjuSettingsStore school=new ZjuSettingsStore(context);
        if(school.enabled())throw new IllegalStateException("School service must be disabled");
        var guide=context.getSharedPreferences("usage_guide",0);int oldGuide=guide.getInt("version",0);
        Field version=GuideActivity.class.getDeclaredField("GUIDE_VERSION");version.setAccessible(true);
        guide.edit().putInt("version",version.getInt(null)).commit();
        ThemeStore.setMode(context,args.getString("theme","dark"));
        long[] tasks=new long[3];Activity screen=null;JSONArray headers=new JSONArray();
        try {
            long now=System.currentTimeMillis();
            try(TaskStore store=new TaskStore(context)) {
                try(var stale=store.getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE raw_text LIKE ? AND source = ?",
                        new String[]{"紧凑布局验收 %","device_check"})) {
                    while(stale.moveToNext())store.deleteTask(stale.getLong(0));
                }
                for(int i=0;i<3;i++) {
                    tasks[i]=store.insertTask("紧凑布局验收 "+i,null,"device_check",now);
                    store.appendCandidate(new EventCandidate(0,tasks[i],i==0?"微积分课后讨论":"紧凑布局验收 "+i,
                            now+(i+1)*3600000L,now+(i+1)*3600000L+1800000,java.time.ZoneId.systemDefault().getId(),
                            i==0?"东1-201":i==1?"紫金港校区东教学区二楼讨论室":"","",null,false,null,"task",false));
                    store.updateStatus(tasks[i],i==1&&"filter_overflow".equals(args.getString("scope"))?TaskRecord.FAILED:TaskRecord.READY,null);
                }
            }
            screen=test.startActivitySync(new Intent(context,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity home=screen;
            waitFor(test,()->findText(home.getWindow().getDecorView(),"东1-201")!=null,"home location");
            if("menu".equals(args.getString("scope"))) return MenuDeviceCheck.run(test,home,args);
            if("homework_refresh".equals(args.getString("scope"))) return homeworkRefresh(test,home,args);
            onMain(test,()-> {
                TextView place=findText(home.getWindow().getDecorView(),"东1-201");
                ViewGroup summary=(ViewGroup)place.getParent();
                TextView title=(TextView)summary.getChildAt(0);
                TextView old=new TextView(home);old.setTextSize(16);old.setTypeface(title.getTypeface());
                old.setIncludeFontPadding(false);old.setMinLines(2);old.setMaxLines(2);old.setText(title.getText());
                old.measure(View.MeasureSpec.makeMeasureSpec(summary.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                require(summary.getHeight()<=old.getMeasuredHeight()+1,"location fits original two-line height: "+summary.getHeight()+" / "+old.getMeasuredHeight()+" min="+summary.getMinimumHeight()+" children="+title.getHeight()+","+place.getHeight()+" line="+title.getLineHeight());
                require(((ViewGroup.MarginLayoutParams)place.getLayoutParams()).topMargin==0,"no added location margin");
                android.graphics.Rect titleGlyph=new android.graphics.Rect(),placeGlyph=new android.graphics.Rect();
                title.getPaint().getTextBounds(title.getText().toString(),0,title.length(),titleGlyph);
                place.getPaint().getTextBounds(place.getText().toString(),0,place.length(),placeGlyph);
                require(title.getTop()+title.getBaseline()+titleGlyph.bottom<=place.getTop()+place.getBaseline()+placeGlyph.top,"title and location glyphs remain separate");
                HeaderActionGroup group=(HeaderActionGroup)findType(home.getWindow().getDecorView(),HeaderActionGroup.class);
                require(group!=null,"home shared action capsule");
                // Build the enabled-school header only; never run school lifecycle/network work.
                school.prefs.edit().putBoolean("enabled",true).commit();
                try {
                    Method build=DashboardActivity.class.getDeclaredMethod("buildHeaderControls");build.setAccessible(true);
                    View replacement=(View)build.invoke(home);ViewGroup parent=(ViewGroup)group.getParent();
                    int index=parent.indexOfChild(group);ViewGroup.LayoutParams params=group.getLayoutParams();
                    parent.removeView(group);parent.addView(replacement,index,params);
                    require(((ViewGroup)replacement).getChildCount()==5,"three actions and two separators");
                } catch(ReflectiveOperationException e){throw new AssertionError(e);}
                finally {school.prefs.edit().putBoolean("enabled",false).commit();}
            });
            if("home_toggle".equals(args.getString("scope"))) {
                test.getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.READ_CALENDAR);
                CalendarVisibilityToggle toggle=(CalendarVisibilityToggle)findType(home.getWindow().getDecorView(),CalendarVisibilityToggle.class);
                boolean original=HomeTimelinePreferences.includesSystemCalendar(context);
                try {
                    for(boolean enabled:new boolean[]{false,true,false}) {
                        onMain(test,()-> {if(toggle.isChecked()!=enabled)toggle.performClick();});
                        SystemClock.sleep(600);
                        onMain(test,()-> {
                            require(toggle.isChecked()==enabled&&HomeTimelinePreferences.includesSystemCalendar(context)==enabled,"home toggle persists real state");
                            require((enabled?"已开启":"已关闭").contentEquals(toggle.getStateDescription()),"accessible checked state");
                            Bitmap bitmap=Bitmap.createBitmap(toggle.getWidth(),toggle.getHeight(),Bitmap.Config.ARGB_8888);
                            toggle.draw(new android.graphics.Canvas(bitmap));
                            int x=Math.round(6*toggle.getResources().getDisplayMetrics().density);
                            require(android.graphics.Color.alpha(bitmap.getPixel(x,bitmap.getHeight()/2))==0,"checked toggle has no selected fill outside icon");
                            bitmap.recycle();
                            require(Math.abs(((Number)field(toggle,"checkedFraction")).floatValue()-(enabled?1:0))<.001f,"icon colour settles to selected state");
                        });
                        capture(test,"home-toggle-"+(enabled?"on":"off")+"-"+args.getString("theme","light"));
                    }
                } finally {onMain(test,()->toggle.setChecked(original));}
                return new JSONObject().put("passed",true).put("states",3).put("assertions",12).toString();
            }
            capture(test,"compact-home");
            Method switchTo=DashboardActivity.class.getDeclaredMethod("switchTo",int.class);switchTo.setAccessible(true);
            for(int section:new int[]{DashboardActivity.SCHEDULE,DashboardActivity.INBOX}) {
                onMain(test,()->{try{switchTo.invoke(home,section);}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
                waitFor(test,()->!Boolean.TRUE.equals(field(home,"pageTransitionRunning"))&&Integer.valueOf(section).equals(field(home,"section")),"list transition settled");
                waitFor(test,()->findText(home.getWindow().getDecorView(),section==DashboardActivity.SCHEDULE?"微积分课后讨论":"紧凑布局验收 0")!=null,"compact list loaded");
                if ("filter_overflow".equals(args.getString("scope"))) {
                    DockNavigationLayout dock=(DockNavigationLayout)field(home,"filterChips");
                    LinearLayout compact=(LinearLayout)dock.getTag();
                    boolean collapsed=compact.isAttachedToWindow();
                    onMain(test,()-> {
                        TextView expected=new TextView(home); expected.setTextSize(14);
                        for(int i=0;i<dock.itemCount();i++) {
                            View cell=dock.itemAt(i);TextView label=cell instanceof TextView?(TextView)cell:(TextView)((ViewGroup)cell).getChildAt(0);
                            require(Math.abs(label.getTextSize()-expected.getTextSize())<.1f,"uniform original 14sp labels");
                            require(label.getAutoSizeTextType()==TextView.AUTO_SIZE_TEXT_TYPE_NONE,"no automatic shrinking");
                            if(!collapsed) {
                                require(label.getWidth()>=label.getPaint().measureText(label.getText().toString())+label.getPaddingLeft()+label.getPaddingRight()-1,"complete inline label "+label.getText());
                                require(label.getLayout().getEllipsisCount(0)==0,"no ellipsis");
                            }
                        }
                        float density=home.getResources().getDisplayMetrics().density;
                        require((collapsed?compact:dock).getHeight()<=density*54+1,"compact row retains original height");
                    });
                    String name="filter-menu-"+section+"-"+args.getString("theme","light");
                    capture(test,name+"-base");
                    if(!collapsed) {onMain(test,()->require(dock.getWidth()>=dock.getMinimumWidth(),"fitting rail remains full width"));continue;}
                    tap(test,compact);
                    waitForNode(test,"筛选");
                    for(int i=0;i<dock.itemCount();i++) {
                        View cell=dock.itemAt(i);TextView label=cell instanceof TextView?(TextView)cell:(TextView)((ViewGroup)cell).getChildAt(0);
                        String option=filterOption(cell);
                        android.graphics.Rect rect=waitForNode(test,option);
                        require(rect.width()>label.getPaint().measureText(option),"dialog has complete option width");
                    }
                    capture(test,name+"-open");
                    test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                    SystemClock.sleep(500);
                    onMain(test,()->require(Integer.valueOf(0).equals(field(home,section==DashboardActivity.SCHEDULE?"scheduleTab":"statusIndex")),"cancel preserves selected filter"));
                    for(int choice:new int[]{dock.itemCount()/2,dock.itemCount()-1,0}) {
                        tap(test,compact);waitForNode(test,"筛选");
                        View cell=dock.itemAt(choice);TextView label=cell instanceof TextView?(TextView)cell:(TextView)((ViewGroup)cell).getChildAt(0);
                        android.graphics.Rect rect=waitForNode(test,filterOption(cell));
                        long down=SystemClock.uptimeMillis();physical(test,down,0,rect.exactCenterX(),rect.exactCenterY());physical(test,down,1,rect.exactCenterX(),rect.exactCenterY());
                        waitFor(test,()->Integer.valueOf(choice).equals(field(home,section==DashboardActivity.SCHEDULE?"scheduleTab":"statusIndex")),"dialog selection updates real filter");
                        onMain(test,()-> {
                            require(label.getText().toString().contentEquals(((TextView)compact.getChildAt(0)).getText()),"compact value follows selection");
                            require(("筛选："+label.getText()).contentEquals(compact.getContentDescription()),"accessible current value");
                            require(Integer.valueOf(section).equals(field(home,"section")),"selection stays on workspace");
                        });
                    }
                    capture(test,name+"-selected");
                    continue;
                }
                capture(test,section==DashboardActivity.SCHEDULE?"compact-schedule":"compact-inbox");
            }
            if ("filter_overflow".equals(args.getString("scope"))) return new JSONObject().put("passed",true).put("sections",2).toString();
            test.runOnMainSync(home::finish);screen=null;
            Class<?>[] pages={MainActivity.class,SettingsHubActivity.class,AppearanceActivity.class,SettingsActivity.class,
                    ScheduleSourceSettingsActivity.class,SystemCalendarSettingsActivity.class,NotificationSettingsActivity.class,
                    SyncSettingsActivity.class,LanSettingsActivity.class,BackupRestoreActivity.class,UpdateActivity.class,
                    ZjuSettingsActivity.class,ZjuCalendarSettingsActivity.class,ReminderDiagnosticsActivity.class,
                    CandidateMergeActivity.class,CourseSourceMatchingActivity.class,RepeatScheduleActivity.class,
                    ModelOutputActivity.class,com.donglan.chrona.debug.DebugActivity.class,TaskDetailActivity.class};
            for(Class<?> page:pages) {
                if("home".equals(args.getString("scope"))&&page!=MainActivity.class)continue;
                Intent intent=new Intent(context,page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if(page==TaskDetailActivity.class||page==ModelOutputActivity.class)intent.putExtra("task_id",tasks[0]);
                screen=test.startActivitySync(intent);Activity current=screen;
                waitFor(test,()->findDescription(current.getWindow().getDecorView(),"返回")!=null,"header "+page.getSimpleName());
                SystemClock.sleep(350);
                onMain(test,()-> {
                    View back=findDescription(current.getWindow().getDecorView(),"返回");
                    int[] point=new int[2];back.getLocationOnScreen(point);
                    int safe=back.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()).top;
                    float density=back.getResources().getDisplayMetrics().density;
                    float top=(point[1]-safe)/density;
                    require(back instanceof ImageButton,"vector navigation icon "+page.getSimpleName());
                    View row=(View)back.getParent();int[] rowPoint=new int[2];row.getLocationOnScreen(rowPoint);
                    require(Math.abs((rowPoint[1]-safe)/density-8)<1.1f,"header safe-area padding "+page.getSimpleName());
                    require(Math.abs(top-(8+(row.getHeight()/density-48)/2))<1.1f,"header centered button "+page.getSimpleName()+" = "+top);
                    require(Math.abs(point[0]/density-20)<1.1f,"header left "+page.getSimpleName());
                    require(Math.abs(back.getWidth()/density-48)<1,"48dp hit target");
                    try {headers.put(new JSONObject().put("page",page.getSimpleName()).put("safeTopDp",top));}
                    catch(org.json.JSONException e){throw new AssertionError(e);}
                });
                if(page==MainActivity.class||page==SettingsHubActivity.class||page==AppearanceActivity.class||page==TaskDetailActivity.class)
                    capture(test,"compact-"+page.getSimpleName());
                test.runOnMainSync(current::finish);screen=null;
            }
            return new JSONObject().put("headers",headers).put("homeLocation",true).toString();
        } finally {
            Activity current=screen;if(current!=null)test.runOnMainSync(current::finish);
            try(TaskStore store=new TaskStore(context)){for(long id:tasks)if(id>0)store.deleteTask(id);}
            guide.edit().putInt("version",oldGuide).commit();
        }
    }
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static TextView findText(View view,String text){if(view instanceof TextView label&&text.contentEquals(label.getText()))return label;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=findText(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static View findType(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findType(group.getChildAt(i),type);if(found!=null)return found;}return null;}
    private static void waitFor(Instrumentation test,BooleanSupplier ready,String label)throws Exception{long until=SystemClock.elapsedRealtime()+15000;boolean[] value={false};do{test.runOnMainSync(()->value[0]=ready.getAsBoolean());if(value[0])return;SystemClock.sleep(80);}while(SystemClock.elapsedRealtime()<until);throw new AssertionError(label);}
    // A held Dock deliberately keeps producing frames; fence UI work instead of
    // waiting indefinitely for an idle queue while capturing that animation.
    private static void capture(Instrumentation test,String name)throws Exception{test.runOnMainSync(()->{});SystemClock.sleep(300);Bitmap bitmap=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    private static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static Object field(Object object,String name){try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static String homeworkRefresh(Instrumentation test, Activity home, Bundle args) throws Exception {
        waitFor(test, home::hasWindowFocus, "home is foreground");
        View root = home.getWindow().getDecorView();
        View refresh = root.findViewWithTag("home-homework-refresh");
        TextView all = findText(root, "全部待办");
        require(refresh != null && all != null, "all-todos and homework refresh exist");
        ViewGroup group = (ViewGroup) refresh.getParent();
        require(group instanceof HeaderActionGroup && group.getChildCount() == 3
                && group.getChildAt(0) == refresh, "refresh precedes disclosure in shared action group");
        View disclosure = group.getChildAt(2);
        onMain(test, () -> {
            int[] a = new int[2], r = new int[2], d = new int[2];
            all.getLocationOnScreen(a); refresh.getLocationOnScreen(r); disclosure.getLocationOnScreen(d);
            require(a[0] + all.getWidth() <= r[0] && r[0] + refresh.getWidth() <= d[0], "all / refresh / disclosure ordered without overlap");
            require(refresh.getWidth() >= 48 * refresh.getResources().getDisplayMetrics().density - 1, "full refresh target");
            TextView title = (TextView) ((ViewGroup) all.getParent()).getChildAt(0);
            for (TextView label : new TextView[]{title, all})
                require(label.getLayout() != null && label.getLayout().getHeight() <= label.getHeight() - label.getPaddingTop() - label.getPaddingBottom(),
                        "complete header text: " + label.getText());
        });
        capture(test, "homework-refresh-home-" + args.getString("theme", "light"));
        var prefs = home.getSharedPreferences("home_timeline", 0);
        boolean expanded = prefs.getBoolean("todos_expanded", false);
        onMain(test, disclosure::performClick);
        waitFor(test, () -> prefs.getBoolean("todos_expanded", false) != expanded, "disclosure still toggles real preference");
        onMain(test, disclosure::performClick);
        waitFor(test, () -> prefs.getBoolean("todos_expanded", false) == expanded, "disclosure restores original state");
        Field running = DashboardActivity.class.getDeclaredField("homeworkRefreshRunning"); running.setAccessible(true);
        Method update = DashboardActivity.class.getDeclaredMethod("updateHomeworkRefreshButtons", View.class); update.setAccessible(true);
        onMain(test, () -> {
            try {
                running.setBoolean(home, true); update.invoke(home, root);
                require(!refresh.isEnabled() && "正在刷新作业".contentEquals(refresh.getContentDescription()), "busy refresh is disabled and accessible");
                running.setBoolean(home, false); update.invoke(home, root);
                require(refresh.isEnabled() && "刷新作业".contentEquals(refresh.getContentDescription()), "idle refresh recovers");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        Instrumentation.ActivityMonitor monitor = test.addMonitor(ZjuSettingsActivity.class.getName(), null, false);
        Activity settings = null;
        try {
            onMain(test, refresh::performClick);
            settings = test.waitForMonitorWithTimeout(monitor, 5000);
            require(settings != null, "unconfigured refresh opens school settings instead of networking");
        } finally {
            Activity opened = settings; if (opened != null) test.runOnMainSync(opened::finish);
            test.removeMonitor(monitor);
        }
        return "PASS: real home refresh placement/text/target/disclosure/busy feedback/settings entry; configured homework-only scope covered offline";
    }

    static void dockMotion(Instrumentation test,DockNavigationLayout dock,String name)throws Exception {
        dockMotion(test,dock,name,false);
    }
    static void uniformLabels(Instrumentation test,DockNavigationLayout dock) {
        onMain(test,()-> {
            ViewGroup cells=(ViewGroup)dock.itemAt(0).getParent();
            require(cells.getLeft()==dock.getPaddingLeft()&&cells.getRight()==dock.getWidth()-dock.getPaddingRight(),"labels fill plate inner width without motion margins");
            float expected=cells.getWidth()/(float)dock.itemCount();
            for(int i=0;i<dock.itemCount();i++) {
                View item=dock.itemAt(i);
                require(Math.abs(item.getWidth()-expected)<=1.1f,"equal option widths");
                require(Math.abs((item.getLeft()+item.getRight())/2f-expected*(i+.5f))<=1.1f,"uniform label centers");
                require(item instanceof TextView&&((TextView)item).getGravity()==android.view.Gravity.CENTER,"centered text");
            }
        });
    }
    static void dockMotion(Instrumentation test,DockNavigationLayout dock,String name,boolean allowScreenOverflow)throws Exception {
        waitFor(test,()->dock.isAttachedToWindow()&&dock.getWidth()>0,name+" attached");
        for(int edge:new int[]{0,dock.itemCount()-1}) {
            View item=dock.itemAt(edge);float x=item.getX()+((View)item.getParent()).getX()+item.getWidth()/2f,y=dock.getHeight()/2f;
            long down=SystemClock.uptimeMillis();event(test,dock,down,0,x,y);SystemClock.sleep(650);
            capture(test,name+"-held-"+edge);bounds(test,dock,name+" held "+edge,allowScreenOverflow);
            event(test,dock,down,2,edge==0?-dock.getPaddingLeft():dock.getWidth()+dock.getPaddingRight(),y);
            event(test,dock,down,1,edge==0?-dock.getPaddingLeft():dock.getWidth()+dock.getPaddingRight(),y);
            for(int frame=0;frame<24;frame++){SystemClock.sleep(20);bounds(test,dock,name+" release "+edge,allowScreenOverflow);}
        }
        for(boolean reverse:new boolean[]{false,true}) {
            float a=dock.getWidth()*(reverse?.9f:.1f),b=dock.getWidth()*(reverse?.1f:.9f),y=dock.getHeight()/2f;
            long down=SystemClock.uptimeMillis();event(test,dock,down,0,a,y);
            for(int step=1;step<=5;step++){SystemClock.sleep(12);event(test,dock,down,2,a+(b-a)*step/5f,y);bounds(test,dock,name+" fling",allowScreenOverflow);}
            event(test,dock,down,1,b,y);
            for(int frame=0;frame<45;frame++){SystemClock.sleep(20);bounds(test,dock,name+" coast",allowScreenOverflow);}
        }
    }
    private static void event(Instrumentation test,DockNavigationLayout dock,long down,int action,float x,float y){onMain(test,()->{android.view.MotionEvent e=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{dock.dispatchTouchEvent(e);}finally{e.recycle();}});}
    private static void physical(Instrumentation test,long down,int action,float x,float y){android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{test.sendPointerSync(event);}finally{event.recycle();}}
    private static void tap(Instrumentation test,View view){int[] p=new int[2];onMain(test,()->view.getLocationOnScreen(p));long down=SystemClock.uptimeMillis();physical(test,down,0,p[0]+view.getWidth()/2f,p[1]+view.getHeight()/2f);physical(test,down,1,p[0]+view.getWidth()/2f,p[1]+view.getHeight()/2f);}
    private static String filterOption(View cell){TextView label=cell instanceof TextView?(TextView)cell:(TextView)((ViewGroup)cell).getChildAt(0);String option=label.getText().toString();if(cell instanceof ViewGroup group&&group.getChildCount()>1&&group.getChildAt(1).getVisibility()==View.VISIBLE)option+=" · "+((TextView)group.getChildAt(1)).getText();return option;}
    private static android.graphics.Rect waitForNode(Instrumentation test,String text)throws Exception{long until=SystemClock.elapsedRealtime()+5000;do{var root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText()==null?"":node.getText())){android.graphics.Rect rect=new android.graphics.Rect();node.getBoundsInScreen(rect);return rect;}SystemClock.sleep(80);}while(SystemClock.elapsedRealtime()<until);throw new AssertionError("dialog option "+text);}
    private static void bounds(Instrumentation test,DockNavigationLayout dock,String label,boolean allowScreenOverflow){onMain(test,()->{try{Field f=DockNavigationLayout.class.getDeclaredField("highlightView");f.setAccessible(true);View highlight=(View)f.get(dock);android.graphics.Matrix m=new android.graphics.Matrix();highlight.transformMatrixToGlobal(m);android.graphics.RectF r=new android.graphics.RectF(0,0,highlight.getWidth(),highlight.getHeight());m.mapRect(r);require(Float.isFinite(r.left)&&Float.isFinite(r.right)&&r.width()>0,label+" valid highlight geometry");if(!allowScreenOverflow)require(r.left>=0&&r.right<=dock.getResources().getDisplayMetrics().widthPixels,label+" screen envelope "+r);require(dock.isAttachedToWindow(),label+" retained");}catch(ReflectiveOperationException e){throw new AssertionError(e);}});}
    private static void onMain(Instrumentation test,Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
}
