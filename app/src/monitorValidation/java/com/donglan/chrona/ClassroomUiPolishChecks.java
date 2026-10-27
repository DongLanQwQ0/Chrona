package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Focused UI fixture invoked by the existing monitor runner in its isolated emulator package. */
final class ClassroomUiPolishChecks {
    private final Instrumentation test;
    private final Bundle args;
    private final Context app;
    private final String account;
    private Activity activity;
    private int assertions;
    private final List<ClassroomLesson> catalog=new ArrayList<>();
    private ClassroomLesson searchable,readerLesson;
    private JSONArray readerSegments;
    private ClassroomUiPolishChecks(Instrumentation test,Bundle args){
        this.test=test;this.args=args;app=test.getTargetContext();account=new ZjuSettingsStore(app).account();
    }
    static int run(Instrumentation test,Bundle args)throws Exception{
        Context app=test.getTargetContext();
        if(!"com.donglan.chrona.monitor.validation".equals(app.getPackageName()))throw new SecurityException("Fixture package required");
        if(!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned emulator required");
        ClassroomUiPolishChecks checks=new ClassroomUiPolishChecks(test,args==null?new Bundle():args);
        try{
            checks.seed();
            if("true".equals(checks.args.getString("week_gesture_only"))){checks.weekGestureAudit();return checks.assertions;}
            if("true".equals(checks.args.getString("all_pages")))return LandscapePageAuditChecks.run(test,checks.args,checks.account,checks.readerLesson);
            if("true".equals(checks.args.getString("height_only"))){checks.heightAudit();return checks.assertions;}
            if("true".equals(checks.args.getString("dashboard_dock_only"))){checks.dashboardDock();return checks.assertions;}
            if("true".equals(checks.args.getString("highlight_only"))){checks.highlights();return checks.assertions;}
            if("true".equals(checks.args.getString("header_only"))){checks.header();return checks.assertions;}
            String scope=checks.args.getString("polish_scope","");
            if(!"reader".equals(scope))checks.browse();if(!"browse".equals(scope))checks.reader();
            if(UiMotion.isEnabled()&&test instanceof ClassroomMonitorInstrumentation monitor){
                if("true".equals(checks.args.getString("landscape_only")))checks.check(monitor.enteredPages.contains("AttachmentViewerActivity"),"landscape viewer renders intermediate entry frames");
                else if(!"browse".equals(scope)&&!"true".equals(checks.args.getString("entry_only"))&&!"true".equals(checks.args.getString("rapid_only")))checks.check(monitor.enteredPages.contains("ClassroomTranscriptActivity")&&monitor.enteredPages.contains("AttachmentViewerActivity"),"real reader and viewer entry render intermediate frames: "+monitor.enteredPages);
                if(!"reader".equals(scope))checks.check(monitor.enteredPages.contains("ClassroomSummaryActivity")&&monitor.enteredPages.contains("ClassroomLessonActivity"),"real catalogue and lesson entry render intermediate frames");
            }
            return checks.assertions;
        }
        finally{if(checks.activity!=null)test.runOnMainSync(checks.activity::finish);}
    }
    private void seed()throws Exception{
        ClassroomStore store=new ClassroomStore(app,account);long now=System.currentTimeMillis();
        for(int i=0;i<106;i++){
            ClassroomLesson lesson=new ClassroomLesson("8800",""+(8900+i),i==55?"检索课程Ω":"目录样本"+i,
                    i==55?"课题：矩阵与空间":"目录课堂"+i,i==55?"紫金港检索楼":"教室"+i,"6",now-(i+1)*3_600_000L);
            catalog.add(lesson);if(i==55)searchable=lesson;
        }
        for(int i=0;i<105;i++){
            ClassroomLesson lesson=new ClassroomLesson("8810",""+(9000+i),"排序样本"+String.format(java.util.Locale.ROOT,"%02d",i),"历史课堂"+i,"历史教室","6",now-(i+2)*86_400_000L);
            catalog.add(lesson);
            JSONObject summary=new JSONObject().put("lesson",lesson.json()).put("updated",now-(105-i)*60_000L)
                    .put("complete",true).put("overview","排序验证笔记").put("parts",new JSONArray()).put("images",new JSONArray()).put("total_parts",1);
            ClassroomStore.write(new File(store.localLesson(lesson),"summary.json"),summary);
        }
        ClassroomLesson downloaded=new ClassroomLesson("8810","9055","仅下载课堂","尚未总结","下载教室","6",now-100*86_400_000L);
        catalog.add(downloaded);File downloadedDir=store.localLesson(downloaded);
        ClassroomStore.write(new File(downloadedDir,"materials.json"),new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("text","已下载字幕"))).put("monitoring",false));
        ClassroomStore.write(new File(downloadedDir,"candidates.json"),new JSONObject().put("lesson",downloaded.json()).put("images",new JSONArray()).put("updated",now+86_400_000));
        readerLesson=new ClassroomLesson("8820","9100","线性代数（甲）·课堂信息自然换行验证","实时课堂","紫金港东2教学楼203","2",now-600_000);
        catalog.add(readerLesson);store.catalog(catalog);File dir=store.localLesson(readerLesson);
        readerSegments=new JSONArray();for(int i=0;i<80;i++)readerSegments.put(new JSONObject().put("start",i).put("end",i+1).put("text","第"+i+"句：课堂知识点和完整字幕阅读，查看历史时保持当前位置。"));
        ClassroomStore.write(new File(dir,"live-transcript.json"),new JSONObject().put("segments",readerSegments));
        Bitmap picture=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);picture.eraseColor(android.graphics.Color.rgb(35,115,155));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();picture.compress(Bitmap.CompressFormat.PNG,100,bytes);picture.recycle();ClassroomStore.writeBytes(new File(dir,"raw-0.img"),bytes.toByteArray());
        Bitmap portrait=Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888);portrait.eraseColor(android.graphics.Color.rgb(165,75,45));ByteArrayOutputStream portraitBytes=new ByteArrayOutputStream();portrait.compress(Bitmap.CompressFormat.PNG,100,portraitBytes);portrait.recycle();
        Bitmap last=Bitmap.createBitmap(480,270,Bitmap.Config.ARGB_8888);last.eraseColor(android.graphics.Color.rgb(35,135,85));ByteArrayOutputStream lastBytes=new ByteArrayOutputStream();last.compress(Bitmap.CompressFormat.PNG,100,lastBytes);last.recycle();
        ClassroomStore.writeBytes(new File(dir,"raw-1.img"),portraitBytes.toByteArray());ClassroomStore.writeBytes(new File(dir,"raw-2.img"),lastBytes.toByteArray());
        ClassroomStore.write(new File(dir,"monitor-candidates.json"),new JSONObject().put("updated",now).put("images",new JSONArray().put(new JSONObject().put("raw","raw-0.img").put("second",20)).put(new JSONObject().put("raw","raw-1.img").put("second",40)).put(new JSONObject().put("raw","raw-2.img").put("second",60))));
    }
    private void heightAudit()throws Exception{
        activity=test.startActivitySync(new Intent(app,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("classroom_mode",1));idle(700);
        JSONObject report=new JSONObject();
        for(int selected:new int[]{1,3,2,0}){
            mode(selected);Object page=page();View scroll=selected==0?((ClassroomLessonPickerView)field(page,"weekPicker")).verticalViewport():(View)field(page,"listScroll");
            auditCollapse("classroom-"+selected,(CollapsingPageLayout)((SchedulePage)page).content(),scroll,report);
        }
        test.runOnMainSync(activity::finish);activity=null;
        activity=test.startActivitySync(new Intent(app,CalendarOverviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));idle(800);
        for(int selected:new int[]{1,0,2,3,4}){
            Object page=page();int mode=selected;test.runOnMainSync(()->{try{method(page,"changeMode",int.class).invoke(page,mode);}catch(Exception failure){throw new RuntimeException(failure);}});idle(600);
            auditCollapse("calendar-"+selected,findCollapse(((SchedulePage)page).content()),(View)field(page,"verticalViewport"),report);
        }
        test.runOnMainSync(activity::finish);activity=null;
        try(var writer=new com.donglan.chrona.processing.StreamingOutputStore(app).begin(882011)){writer.append("模型输出正文，用于横屏连续阅读与工具联动验收。\n".repeat(180));}
        activity=test.startActivitySync(new Intent(app,ModelOutputActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("task_id",882011L));idle(450);
        auditCollapse("model-output",findCollapse(activity.findViewById(android.R.id.content)),(View)field(activity,"scroll"),report);test.runOnMainSync(activity::finish);activity=null;
        activity=test.startActivitySync(new Intent(app,GuideActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));idle(400);
        auditCollapse("guide",findCollapse(activity.findViewById(android.R.id.content)),(View)field(activity,"page"),report);
        var bars=activity.getWindow().getDecorView().getRootWindowInsets().getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());Rect window=bounds(activity.findViewById(android.R.id.content));
        check(bounds((View)field(activity,"next")).bottom<=window.bottom-bars.bottom,"guide bottom step control remains inside usable window");
        pickerAudit();test.runOnMainSync(activity::finish);activity=null;
        ClassroomStore.write(new File(app.getExternalFilesDir(null),"monitor-validation/height-audit-"+args.getString("theme","light")+".json"),report);
    }
    private CollapsingPageLayout findCollapse(View root){if(root instanceof CollapsingPageLayout layout)return layout;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){CollapsingPageLayout found=findCollapse(group.getChildAt(i));if(found!=null)return found;}return null;}
    private void weekGestureAudit()throws Exception{
        activity=test.startActivitySync(new Intent(app,CalendarOverviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));idle(600);
        Object page=page();test.runOnMainSync(()->{try{method(page,"changeMode",int.class).invoke(page,2);}catch(Exception failure){throw new RuntimeException(failure);}});idle(600);
        FreeCalendarScrollView grid=(FreeCalendarScrollView)field(page,"horizontalViewport");View vertical=(View)field(page,"verticalViewport");Object date=field(page,"date");
        int maxX=grid.getChildAt(0).getWidth()-grid.getWidth();check(maxX>0,"week fixture has a genuinely horizontally scrollable grid");
        test.runOnMainSync(()->grid.scrollTo(maxX/2,0));idle(100);int initialX=grid.getScrollX();
        auditCollapse("week-gesture",findCollapse(((SchedulePage)page).content()),vertical,new JSONObject());
        check(grid.getScrollX()==initialX&&date.equals(field(page,"date")),"collapse and reverse restore preserve horizontal position and week");
        test.runOnMainSync(()->grid.scrollTo(maxX,0));idle(100);Rect r=bounds(vertical);long down=SystemClock.uptimeMillis();float start=r.left+r.width()*.8f,end=r.left+r.width()*.2f,y=r.centerY();
        pointer(down,down,MotionEvent.ACTION_DOWN,start,y);for(int i=1;i<=10;i++){SystemClock.sleep(20);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start+(end-start)*i/10,y);}pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,end,y);idle(350);
        check(date.equals(field(page,"date")),"cancelled week-edge gesture never switches week");
        down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,start,y);
        MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;}
        for(int step=0;step<=6;step++){
            MotionEvent.PointerCoords[] points={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};for(int i=0;i<2;i++){points[i].x=start+(end-start)*Math.min(step,5)/5+(i==0?0:30);points[i].y=y;points[i].pressure=1;points[i].size=1;}
            int action=step==0?MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):step==6?MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,properties,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);try{test.sendPointerSync(event);}finally{event.recycle();}SystemClock.sleep(25);
        }
        pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,end,y);idle(350);check(date.equals(field(page,"date")),"two-finger week gesture never commits a single-finger edge switch");
        test.runOnMainSync(activity::finish);activity=null;
    }
    private void auditCollapse(String name,CollapsingPageLayout root,View viewport,JSONObject report)throws Exception{
        check(root!=null&&viewport!=null,"height audit has actual viewport for "+name);idle(150);
        int before=viewport.getHeight();capture("height-"+name+"-before");
        JSONArray progress=new JSONArray();for(int attempt=0;attempt<4&&(Integer)field(root,"hidden")<(Integer)field(root,"range");attempt++){verticalSwipe(viewport,true);idle(100);progress.put(new JSONObject().put("hidden",field(root,"hidden")).put("y",viewport.getScrollY()));}
        int after=viewport.getHeight(),range=(Integer)field(root,"range"),hidden=(Integer)field(root,"hidden");
        report.put(name,new JSONObject().put("usable_height_px",root.getHeight()).put("initial_body_px",before).put("expanded_body_px",after).put("tools_px",range).put("hidden_px",hidden).put("progress",progress));
        check(range>0&&hidden==range,"real upward content gesture removes every top tool for "+name+": "+report.opt(name));
        check(after>=before+range-3,"content viewport grows by the reclaimed tool height for "+name+": "+report.opt(name));capture("height-"+name+"-after");
        check(root.getClipBounds()!=null&&root.getClipBounds().top==root.getPaddingTop(),"collapsed tools cannot remain in the system-safe top padding for "+name);
        for(int attempt=0;attempt<12&&(Integer)field(root,"hidden")>0;attempt++){verticalSwipe(viewport,false);idle(80);}
        check((Integer)field(root,"hidden")==0,"downward gestures restore tools at content start for "+name);capture("height-"+name+"-restored");
    }
    private void pickerAudit()throws Exception{
        for(boolean time:new boolean[]{false,true}){
            String[] chosen={null};
            test.runOnMainSync(()->{if(time)UiDialogs.timeDialog(activity,"横屏时间",623,value->chosen[0]=Integer.toString(value));else UiDialogs.dateDialog(activity,"横屏日期",java.time.LocalDate.of(2026,10,27),value->chosen[0]=value.toString());});idle(350);
            var root=test.getUiAutomation().getRootInActiveWindow();String action=time?"保存":"确定";var nodes=root.findAccessibilityNodeInfosByText(action);check(!nodes.isEmpty(),"short-window picker exposes confirmation");Rect confirm=new Rect();nodes.get(0).getBoundsInScreen(confirm);Rect actual=new Rect();root.getBoundsInScreen(actual);check(actual.contains(confirm),"picker confirmation is inside the real dialog window");
            var wheels=root.findAccessibilityNodeInfosByText(time?"10:23":"2026-10-27");check(!wheels.isEmpty(),"wheel exposes the selected value without truncation");Rect wheel=new Rect();wheels.get(0).getBoundsInScreen(wheel);check(wheel.height()>=48*activity.getResources().getDisplayMetrics().density,"short-window picker reserves a readable selected wheel row");
            capture(time?"height-time-picker":"height-date-picker");tapText(action);idle(220);check((time?"623":"2026-10-27").equals(chosen[0]),"bounded picker preserves the selected date/time");
        }
    }
    private void verticalSwipe(View viewport,boolean up){
        Rect area=bounds(viewport);float x=area.centerX(),start=area.top+area.height()*(up?.85f:.2f),end=area.top+area.height()*(up?.2f:.85f);long time=SystemClock.uptimeMillis();pointer(time,time,MotionEvent.ACTION_DOWN,x,start);
        for(int i=1;i<=12;i++){SystemClock.sleep(20);pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,start+(end-start)*i/12);}
        SystemClock.sleep(100);pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,end);
    }
    private void dashboardDock()throws Exception{
        activity=test.startActivitySync(new Intent(app,DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));idle(800);
        DockNavigationLayout dock=(DockNavigationLayout)field(activity,"navigation");View highlight=(View)field(dock,"highlightView");
        assertDockSpacing(dock);
        check(dock.getPaddingTop()==dock.getPaddingBottom()&&dock.getPaddingLeft()==dock.getPaddingRight(),"landscape Dock has symmetric horizontal and vertical static insets");
        for(int selected:new int[]{0,2}){
            Rect rect=bounds(dock.itemAt(selected));long time=SystemClock.uptimeMillis();pointer(time,time,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY());idle(500);
            android.graphics.RectF painted=new android.graphics.RectF(0,0,highlight.getWidth(),highlight.getHeight());android.graphics.Matrix matrix=new android.graphics.Matrix();highlight.transformMatrixToGlobal(matrix);matrix.mapRect(painted);
            Rect board=bounds(dock);check(selected==0?painted.top<board.top:painted.bottom>board.bottom,"held first/last highlight extends outside the landscape plate: "+painted+" / "+board);
            for(android.view.ViewParent parent=dock.getParent();parent instanceof ViewGroup group&&group!=activity.getWindow().getDecorView();parent=group.getParent())check(!group.getClipChildren()&&!group.getClipToPadding(),"Dock ancestor permits motion: "+group.getClass().getSimpleName());
            capture("dashboard-dock-held-"+selected);pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,rect.centerX(),rect.centerY());idle(350);
        }
        tap(dock.itemAt(1));idle(600);assertDockSpacing(dock);capture("dashboard-dock-adjacent-spacing");
        test.runOnMainSync(activity::finish);activity=null;
    }
    private void assertDockSpacing(DockNavigationLayout dock)throws Exception{
        View page=(View)field(activity,"content");float density=activity.getResources().getDisplayMetrics().density;Rect board=bounds(dock),body=bounds(page);
        check(Math.abs(page.getPaddingLeft()-20*density)<2&&Math.abs(page.getPaddingRight()-20*density)<2,"main and adjacent sections share the 20dp content gutters");
        check(Math.abs(body.left+page.getPaddingLeft()-board.right-33*density)<4,"actual Dock-to-content gap is 33dp, without a second centred-column gutter");
    }
    private void highlights()throws Exception{
        activity=test.startActivitySync(new Intent(app,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle(500);
        FrameLayout root=new FrameLayout(activity);test.runOnMainSync(()->{root.setBackgroundColor(UiStyle.colors(activity).surface);activity.setContentView(root);});
        float density=activity.getResources().getDisplayMetrics().density;
        for(int level=1;level<=5;level++)for(int extent:new int[]{44,48})for(int rowCount=1;rowCount<=3;rowCount++){
            int corner=level,count=rowCount;HeaderActionGroup[] ref={null};List<View> cells=new ArrayList<>();int[] clicks={0};
            test.runOnMainSync(()->{
                ThemeStore.setCornerLevel(activity,corner);root.removeAllViews();HeaderActionGroup group=new HeaderActionGroup(activity,extent);ref[0]=group;
                for(int i=0;i<count;i++){View action=i==1?new CalendarVisibilityToggle(activity):UiStyle.navigationIcon(activity,R.drawable.ic_refresh,"测试操作",()->clicks[0]++);if(action instanceof CheckBox)action.setOnClickListener(v->clicks[0]++);group.addAction(action,extent);cells.add(action);}group.finish();
                FrameLayout.LayoutParams slot=new FrameLayout.LayoutParams(-2,Math.round(extent*density));slot.leftMargin=Math.round(40*density);slot.topMargin=Math.round(160*density);root.addView(group,slot);
            });idle(90);
            for(int i=0;i<count;i++){
                View cell=cells.get(i);int position=i;
                for(float scale:new float[]{1f,1.10f}){
                    test.runOnMainSync(()->{
                        ref[0].setScaleX(scale);ref[0].setScaleY(scale);
                        var mask=((android.graphics.drawable.RippleDrawable)cell.getForeground()).findDrawableByLayerId(android.R.id.mask);
                        Bitmap bitmap=Bitmap.createBitmap(cell.getWidth(),cell.getHeight(),Bitmap.Config.ARGB_8888);mask.draw(new android.graphics.Canvas(bitmap));
                        int middle=bitmap.getHeight()/2;
                        check(android.graphics.Color.alpha(bitmap.getPixel(0,middle))>240&&android.graphics.Color.alpha(bitmap.getPixel(bitmap.getWidth()-1,middle))>240,"mask fills both cell edges at level/size/count/position/scale "+corner+"/"+extent+"/"+count+"/"+position+"/"+scale);
                        if(count>1){int x=position==0?bitmap.getWidth()-2:position==count-1?1:bitmap.getWidth()/2;check(android.graphics.Color.alpha(bitmap.getPixel(x,1))>240,"inner separator has square continuous highlight corners");}
                        bitmap.recycle();
                    });
                }
                test.runOnMainSync(()->{ref[0].setScaleX(1f);ref[0].setScaleY(1f);});
                if(level==4&&count==2){Rect rect=bounds(cell);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY());idle(250);capture("highlight-"+extent+"-"+position);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,rect.centerX(),rect.centerY());idle(180);check(clicks[0]==0,"cancelled hold does not invoke action");}
            }
            tap(cells.get(0));idle(120);check(clicks[0]==1,"full action remains clickable");
        }
        test.runOnMainSync(()->ThemeStore.setCornerLevel(activity,4));
        test.runOnMainSync(activity::finish);activity=null;
    }
    private void header()throws Exception{
        activity=test.startActivitySync(new Intent(app,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("classroom_mode",1));idle(700);
        Object page=page(),session=field(page,"s");View settings=(View)field(page,"monitorSettings");HeaderActionGroup group=(HeaderActionGroup)field(page,"headerActions");View refresh=(View)field(page,"refresh");
        float density=activity.getResources().getDisplayMetrics().density;boolean[] sawCancel={false},sawMotion={false};
        android.view.ViewTreeObserver.OnPreDrawListener frames=()->{if("停止刷新".contentEquals(refresh.getContentDescription()))sawCancel[0]=true;if(group.getWidth()>48*density+1&&group.getWidth()<97*density-1)sawMotion[0]=true;return true;};
        test.runOnMainSync(()->group.getViewTreeObserver().addOnPreDrawListener(frames));
        for(int selected:new int[]{2,3,0,1,2}){
            TextView compact=(TextView)field(page,"compactMode");DockNavigationLayout dock=(DockNavigationLayout)field(page,"navigation");
            if(compact.getVisibility()==View.VISIBLE){tap(compact);idle(100);tapText(new String[]{"课表","课堂","监视","历史"}[selected]);}else tap(dock.itemAt(selected));idle(320);
            check((Integer)field(session,"mode")==selected,"real mode selection reaches "+selected);
            check(selected==2?settings.getVisibility()==View.VISIBLE&&settings.isClickable()&&settings.isEnabled():settings.getVisibility()==View.GONE&&!settings.isClickable()&&!settings.isEnabled(),"settings exists only in monitor mode "+selected);
            check(Math.abs(group.getWidth()-Math.round((selected==2?97:48)*density))<=1,"header contracts button and separator without an empty slot "+selected);
            check(!sawCancel[0],"local view changes never paint the cancel icon "+selected);capture("header-mode-"+selected);
        }
        check(!UiMotion.isEnabled()||sawMotion[0],"conditional settings paints intermediate shared-width animation");
        java.util.concurrent.CountDownLatch held=new java.util.concurrent.CountDownLatch(1),ready=new java.util.concurrent.CountDownLatch(1);
        ((java.util.concurrent.ExecutorService)field(session,"worker")).execute(()->{ready.countDown();try{held.await();}catch(InterruptedException failure){Thread.currentThread().interrupt();}});
        check(ready.await(3,java.util.concurrent.TimeUnit.SECONDS),"owned local-read gate entered");
        try{test.runOnMainSync(()->{try{Class<?> kind=Class.forName("com.donglan.chrona.ClassroomSummaryPage$Load");Object cache=java.lang.Enum.valueOf((Class)kind,"CACHE");method(session,"load",kind).invoke(session,cache);}catch(Exception failure){throw new RuntimeException(failure);}});idle(150);
            check((Boolean)field(session,"busy")&&!(Boolean)field(session,"refreshing")&&"刷新课堂".contentEquals(refresh.getContentDescription())&&!sawCancel[0],"blocked real cache read remains refresh rather than flashing cancel");
        }finally{held.countDown();}
        idle(300);tap(settings);idle(200);check(findTextInWindow("自动总结上传图片")&&findTextInWindow("后台运行设置"),"monitor-only settings still opens existing runtime controls");capture("header-runtime-settings");
        test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle(200);
        test.runOnMainSync(()->{group.setActionVisible(settings,false);});SystemClock.sleep(55);test.runOnMainSync(()->{group.setActionVisible(settings,true);});idle(320);
        check(settings.getVisibility()==View.VISIBLE&&Math.abs(group.getWidth()-Math.round(97*density))<=1,"rapid reversal settles at the requested full action group");
        test.runOnMainSync(()->group.getViewTreeObserver().removeOnPreDrawListener(frames));
        test.runOnMainSync(activity::finish);activity=null;
    }
    private void browse()throws Exception{
        activity=test.startActivitySync(new Intent(app,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("classroom_mode",1));idle(900);
        Object page=page();Object session=field(page,"s");EditText input=(EditText)field(page,"search");
        check(input.isShown(),"classroom search is visible above the list");
        setQuery(input,"矩阵与空间");check(buttons((View)field(page,"listBody")).size()==1,"search reaches a title beyond the first catalogue page");
        check(buttons((View)field(page,"listBody")).get(0).getText().toString().contains(searchable.course),"title query finds the intended lesson");
        check(input.hasFocus()&&field(page,"search")==input,"query updates preserve input instance and focus");
        setQuery(input,"紫金港检索楼");check(buttons((View)field(page,"listBody")).size()==1,"room query matches full catalogue before pagination");
        setQuery(input,searchable.date().toString());check(!buttons((View)field(page,"listBody")).isEmpty(),"date text is searchable");
        setQuery(input,"不存在的课堂-Ω");check(findText((View)field(page,"listBody"),"未找到课堂")!=null,"empty query results render in place");
        setQuery(input,"检索课程Ω");
        check(((ScrollView)field(page,"toolsViewport")).getChildAt(0).getPaddingBottom()==0,"top tools never receive bottom Dock clearance");
        check(bounds(buttons((View)field(page,"listBody")).get(0)).top-bounds(input).bottom<=24*app.getResources().getDisplayMetrics().density,"first result follows tools without a phantom gap");capture("search");
        test.runOnMainSync(()->app.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0));idle(200);
        Button result=buttons((View)field(page,"listBody")).get(0);
        Instrumentation.ActivityMonitor detail=test.addMonitor(ClassroomLessonActivity.class.getName(),null,false);tap(result);
        Activity opened=test.waitForMonitorWithTimeout(detail,5000);test.removeMonitor(detail);check(opened!=null,"search result opens the actual classroom detail");
        test.runOnMainSync(opened::finish);idle(500);check(field(page(),"search")==input&&"检索课程Ω".contentEquals(input.getText()),"return from detail preserves the query and input instance");
        mode(3);idle(250);mode(1);idle(200);check("检索课程Ω".contentEquals(input.getText()),"switching views preserves the query");
        recreate();page=page();input=(EditText)field(page,"search");check("检索课程Ω".contentEquals(input.getText()),"recreation preserves query");
        setQuery(input,"");page=page();check(buttons((View)field(page,"listBody")).size()==50,"catalogue paginates only after filtering");
        DateIntervalBar pageBar=(DateIntervalBar)field(page,"pageBar");DockNavigationLayout sameDock=(DockNavigationLayout)field(page,"navigation");
        horizontalSwipe(pageBar,.7f,.3f);idle(400);check((Integer)field(field(page(),"s"),"page")==1&&field(page(),"pageBar")==pageBar&&field(page(),"search")==input,"real page-bar swipe changes page and preserves controls");
        Rect pagesBounds=bounds(pageBar);tap(new Rect(pagesBounds.centerX()-4,pagesBounds.centerY()-4,pagesBounds.centerX()+4,pagesBounds.centerY()+4));idle(150);tapText("第 3 页");idle(350);
        check((Integer)field(field(page(),"s"),"page")==2&&field(page(),"navigation")==sameDock,"page bar click selects third page without replacing Dock");
        horizontalSwipe(pageBar,.7f,.3f);idle(350);check((Integer)field(field(page(),"s"),"page")==2,"last catalogue page does not overflow");
        tap(new Rect(pagesBounds.centerX()-4,pagesBounds.centerY()-4,pagesBounds.centerX()+4,pagesBounds.centerY()+4));idle(150);tapText("第 1 页");idle(350);
        View modeBody=(View)field(page(),"bodyHost");java.util.concurrent.atomic.AtomicBoolean slideObserved=new java.util.concurrent.atomic.AtomicBoolean();
        android.view.ViewTreeObserver.OnPreDrawListener modeFrame=()->{if(Math.abs(modeBody.getTranslationX())>1&&modeBody.getAlpha()>0f&&modeBody.getAlpha()<1f)slideObserved.set(true);return true;};
        test.runOnMainSync(()->modeBody.getViewTreeObserver().addOnPreDrawListener(modeFrame));
        if(sameDock.itemAt(3).isShown())tap(sameDock.itemAt(3));else{tap((View)field(page(),"compactMode"));idle(100);tapText("历史");}idle(350);
        test.runOnMainSync(()->modeBody.getViewTreeObserver().removeOnPreDrawListener(modeFrame));if(UiMotion.isEnabled())check(slideObserved.get(),"actual mode selection paints intermediate horizontal transition");
        mode(3);idle(250);
        for(int order:new int[]{0,1}){
            page=page();TextView trigger=(TextView)field(page,"historyOrder");tap(trigger);idle(200);tapText(order==0?"课程时间":"总结时间");idle(300);
            session=field(page,"s");
            if((Boolean)field(session,"historyAscending")){tap((View)field(page,"historyDirection"));idle(200);}
            assertHistory(order,false);capture("history-"+order+"-descending");
            tap((View)field(page,"historyDirection"));idle(200);assertHistory(order,true);capture("history-"+order+"-ascending");
        }
        mode(1);mode(3);idle(200);session=field(page(),"s");check((Integer)field(session,"historyOrder")==1&&(Boolean)field(session,"historyAscending"),"sort survives view changes");
        recreate();session=field(page(),"s");check((Integer)field(session,"historyOrder")==1&&(Boolean)field(session,"historyAscending"),"sort survives activity recreation");
        assertHistory(1,true);
        mode(2);idle(250);View settings=findDescription(((SchedulePage)page()).content(),"监视运行设置");check(settings!=null,"runtime settings remain reachable in monitor overview");tap(settings);idle(300);check(findTextInWindow("后台运行设置"),"runtime settings retains the original system permission menu");
        check(settings.getParent() instanceof HeaderActionGroup&&settings.getParent()==((View)field(page(),"refresh")).getParent(),"settings and refresh share one header action surface");
        ClassroomMonitorStore monitorStore=new ClassroomMonitorStore(app,account);check(!monitorStore.summaryImages()&&!nodeChecked("自动总结上传图片"),"automatic summary image upload defaults off in storage and actual checkbox");
        tapText("自动总结上传图片");idle(150);check(new ClassroomMonitorStore(app,account).summaryImages()&&nodeChecked("自动总结上传图片"),"actual checkbox click persists explicit image-upload consent");capture("monitor-runtime-settings");
        test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle(250);tap(settings);idle(250);check(nodeChecked("自动总结上传图片"),"reopened runtime settings restores the saved consent checkbox");
        tapText("自动总结上传图片");idle(150);check(!new ClassroomMonitorStore(app,account).summaryImages(),"unchecking persists text-only automatic summary consent");
        test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle(200);
        test.runOnMainSync(activity::finish);activity=null;idle(200);
    }
    private void assertHistory(int order,boolean ascending)throws Exception{
        Object page=page(),session=field(page,"s");
        @SuppressWarnings("unchecked") List<JSONObject> history=(List<JSONObject>)field(session,"history");
        List<JSONObject> expected=new ArrayList<>(history);expected.sort(ClassroomSummaryPage.historyComparator(order,ascending));
        long previous=ascending?Long.MIN_VALUE:Long.MAX_VALUE;for(JSONObject record:expected){
            if(order==1&&(record.optBoolean("downloaded")||record.optLong("updated")<=0))continue;
            long time=order==0?record.getJSONObject("lesson").getLong("starts_at"):record.optLong("updated");
            check(ascending?time>=previous:time<=previous,"selected field is independently monotonic in the requested direction");previous=time;
        }
        int current=(Integer)field(session,"page");List<Button> visible=buttons((View)field(page,"listBody"));
        check(visible.size()==Math.min(50,Math.max(0,expected.size()-current*50)),"history page contains the expected number of sorted rows");
        for(int i=0;i<visible.size();i++){ClassroomLesson lesson=ClassroomLesson.fromJson(expected.get(current*50+i).getJSONObject("lesson"));check(visible.get(i).getText().toString().startsWith(lesson.course+"\n"),"all rendered history rows follow the selected field and direction");}
        if(order==1){boolean missing=false;for(JSONObject record:expected){long updated=record.optBoolean("downloaded")?0:record.optLong("updated");if(updated<=0)missing=true;else check(!missing,"downloaded-only items remain last in either summary direction");}}
        List<JSONObject> ties=new ArrayList<>();ties.add(new JSONObject().put("lesson",catalog.get(1).json()).put("updated",1));ties.add(new JSONObject().put("lesson",catalog.get(0).json()).put("updated",1));
        ties.sort(ClassroomSummaryPage.historyComparator(1,ascending));check(ties.get(0).getJSONObject("lesson").getLong("starts_at")<ties.get(1).getJSONObject("lesson").getLong("starts_at"),"equal summary timestamps use stable lesson start order");
    }
    private void reader()throws Exception{
        activity=test.startActivitySync(new Intent(app,ClassroomTranscriptActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("account",account).putExtra("lesson",readerLesson.json().toString()));idle(800);
        View root=activity.findViewById(android.R.id.content);CheckBox repeat=(CheckBox)field(activity,"repeat");
        check("后续课程".contentEquals(repeat.getText())&&"监视后续课程".contentEquals(repeat.getContentDescription()),"scope checkbox keeps its short label and full accessible meaning");
        check(findDescription(root,"监视运行设置")==null,"dedicated monitor page removes the settings gear");
        View title=findText(root,readerLesson.course);Rect titleBounds=bounds(title),scopeBounds=bounds(repeat),statusBounds=bounds((View)field(activity,"status"));
        check(findText(root,"课堂监视")==null&&titleBounds.bottom<=scopeBounds.top,"course name is the only page title above compact metadata");
        check(!Rect.intersects(statusBounds,scopeBounds)&&statusBounds.top<scopeBounds.bottom&&statusBounds.bottom>scopeBounds.top,"connection metadata and following-course checkbox share one compact row");
        DockNavigationLayout dock=(DockNavigationLayout)field(activity,"sections");View host=(View)dock.getParent();ScrollView captions=(ScrollView)field(activity,"scroll");LinearLayout rows=(LinearLayout)field(activity,"rows");
        capture("reader-before-dock-check");
        boolean side=(Boolean)field(activity,"sideNavigation");
        check(side?bounds(host).right<=bounds(captions).left:bounds(host).bottom<=bounds(captions).bottom+1&&bounds(host).top>bounds(captions).centerY(),"subtitle/PPT dock occupies its actual reader edge: host="+bounds(host)+", viewport="+bounds(captions));
        if(!side){int safe=activity.getWindow().getDecorView().getRootWindowInsets().getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()).bottom;check(Math.abs(bounds(root).bottom-bounds(host).bottom-safe-4*activity.getResources().getDisplayMetrics().density)<3,"reader Dock uses workspace 4dp plus system-bottom baseline, without form end padding");}
        check(Math.abs(rows.getPaddingBottom()-(side?0:host.getHeight())-12*activity.getResources().getDisplayMetrics().density)<2,"caption end reserves only the measured Dock plus 12dp, without a phantom action row");capture("reader-bottom-dock");
        entranceCancellation(root);
        if("true".equals(args.getString("entry_only"))){ClassroomMonitorInstrumentation monitor=(ClassroomMonitorInstrumentation)test;check(monitor.enteredPages.contains("ClassroomTranscriptActivity"),"reader entry frames="+monitor.enteredPages+", creates="+monitor.entryCreates+", frames="+monitor.entryFrameCounts);test.runOnMainSync(activity::finish);activity=null;return;}
        if("true".equals(args.getString("landscape_only"))){tap(dock.itemAt(1));idle(500);landscapeViewer();test.runOnMainSync(activity::finish);activity=null;return;}
        if("true".equals(args.getString("rapid_only"))){tap(dock.itemAt(1));idle(500);rapidGallery();test.runOnMainSync(activity::finish);activity=null;return;}
        if("true".equals(args.getString("gallery_only"))){tap(dock.itemAt(1));idle(500);tap((View)field(activity,"nextSlide"));idle(400);openViewerAndSave();test.runOnMainSync(activity::finish);activity=null;return;}
        int initialY=captions.getScrollY();check(initialY>0,"first opening performs its one latest-caption position: rows="+rows.getChildCount()+"/"+rows.getHeight()+", viewport="+captions.getHeight()+", destroyed="+activity.isDestroyed()+", config="+activity.getResources().getConfiguration()+", pending="+field(activity,"pendingLatest")+"/"+field(activity,"pendingRestore"));
        appendCaption("首次进入后的追加不拉动偏移");idle(500);check(captions.getScrollY()==initialY,"appending even at the initial latest position preserves exact Y");
        Rect viewport=bounds(captions);long held=SystemClock.uptimeMillis();float x=viewport.centerX(),start=viewport.top+viewport.height()*.18f,end=viewport.top+viewport.height()*.66f;
        pointer(held,held,MotionEvent.ACTION_DOWN,x,start);for(int i=1;i<=10;i++){SystemClock.sleep(20);pointer(held,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,start+(end-start)*i/10);}
        SystemClock.sleep(120);int heldY=captions.getScrollY();View firstParagraph=rows.getChildAt(0);appendCaption("触摸仍按住字幕子项时追加");idle(450);check(captions.getScrollY()==heldY&&rows.getChildAt(0)==firstParagraph,"actual held caption browsing survives incoming text without replacing paragraphs");
        pointer(held,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,end);idle(500);int before=captions.getScrollY();
        for(int i=0;i<3;i++){appendCaption("连续追加"+i);idle(350);check(captions.getScrollY()==before,"consecutive appends preserve the settled reading offset");}
        readerSegments.getJSONObject(0).put("text","首句修订增加文本：课堂知识点、矩阵与空间，保持当前滚动数值。".repeat(3));writeCaptions();idle(400);check(captions.getScrollY()==before,"within-sentence revisions preserve exact reading Y");
        check(!(Boolean)field(activity,"pendingLatest"),"user touch cancels any pending latest-position request");capture("reader-held-append");
        View latest=(View)field(activity,"latest");check(side?!Rect.intersects(bounds(latest),bounds(host)):bounds(latest).bottom<bounds(host).top,"return-to-latest action stays outside the dock envelope");
        View readingBody=(View)field(activity,"readingBody");java.util.concurrent.atomic.AtomicBoolean transitionObserved=new java.util.concurrent.atomic.AtomicBoolean();
        int[] transitionFrames={0};float[] transitionAlpha={1f};Rect transitionVisible=new Rect();test.runOnMainSync(()->readingBody.getLocalVisibleRect(transitionVisible));
        android.view.ViewTreeObserver.OnPreDrawListener transitionFrame=()->{transitionFrames[0]++;transitionAlpha[0]=Math.min(transitionAlpha[0],readingBody.getAlpha());if(readingBody.getAlpha()>0f&&readingBody.getAlpha()<1f)transitionObserved.set(true);return true;};
        test.runOnMainSync(()->readingBody.getViewTreeObserver().addOnPreDrawListener(transitionFrame));tap(dock.itemAt(1));idle(350);
        test.runOnMainSync(()->readingBody.getViewTreeObserver().removeOnPreDrawListener(transitionFrame));if(UiMotion.isEnabled())check(transitionObserved.get(),"subtitle to PPT renders actual intermediate public content-transition frames: frames="+transitionFrames[0]+", minAlpha="+transitionAlpha[0]+", mode="+field(activity,"mode")+", active="+field(activity,"active")+", visible="+transitionVisible+", attached="+readingBody.isAttachedToWindow()+", size="+readingBody.getWidth()+"x"+readingBody.getHeight());
        appendCaption("PPT浏览期间新增字幕");idle(350);tap(dock.itemAt(0));idle(350);check(captions.getScrollY()==before&&field(activity,"sections")==dock,"tab return preserves reading Y and original dock");
        tap(repeat);idle(150);recreate();check(((CheckBox)field(activity,"repeat")).isChecked(),"unstarted monitoring scope selection restores before monitoring begins");
        captions=(ScrollView)field(activity,"scroll");check(captions.getScrollY()==before,"real activity recreation restores reading Y after asynchronous caption layout");
        latest=(View)field(activity,"latest");tap(latest);idle(350);check((Boolean)field(activity,"follow")&&captions.getScrollY()>before,"actual latest button explicitly positions the last caption");
        dock=(DockNavigationLayout)field(activity,"sections");holdOut(dock.itemAt(0),"dock-first-held");holdOut(dock.itemAt(1),"dock-last-held");
        dock=(DockNavigationLayout)field(activity,"sections");tap(dock.itemAt(1));idle(450);check((Integer)field(activity,"mode")==1&&((ImageView)field(activity,"slideImage")).getDrawable()!=null,"bottom PPT dock opens the actual cached frame");capture("reader-ppt");
        ImageView slide=(ImageView)field(activity,"slideImage");View frame=(View)field(activity,"slideFrame");check(frame.getClipToOutline()&&frame.getOutlineProvider()!=null,"PPT frame clips to the shared corner scale");
        android.graphics.Outline pictureOutline=new android.graphics.Outline();test.runOnMainSync(()->frame.getOutlineProvider().getOutline(frame,pictureOutline));Rect pictureBounds=new Rect();
        check(!slide.getClipToOutline()&&pictureOutline.getRect(pictureBounds)&&pictureOutline.getRadius()>0&&slide.getWidth()==frame.getWidth()&&slide.getHeight()==frame.getHeight()&&Math.abs(frame.getWidth()/(float)frame.getHeight()-320f/180f)<.02,"one rounded image boundary, zero letterbox and original ratio");
        galleryDrag((ImageSlidePager)field(activity,"slidePager"),false);
        View rail=(View)field(activity,"slidesBar");check(rail instanceof DateIntervalBar,"PPT navigation uses the shared date-interval component");horizontalSwipe(rail,.7f,.3f);idle(450);check((Integer)field(activity,"pictureIndex")==1,"real left swipe on the common control selects next PPT");
        horizontalSwipe(slide,.75f,.25f);idle(450);check((Integer)field(activity,"pictureIndex")==2,"real left swipe on the picture selects the same next direction");
        horizontalSwipe(slide,.25f,.75f);idle(450);check((Integer)field(activity,"pictureIndex")==1,"picture right swipe returns to previous frame");
        tap((View)field(activity,"previousSlide"));idle(500);check((Integer)field(activity,"pictureIndex")==0,"common arrow and image gestures share the same selected frame");
        Rect railBounds=bounds(rail);tap(new Rect(railBounds.centerX()-4,railBounds.centerY()-4,railBounds.centerX()+4,railBounds.centerY()+4));idle(200);tapText("2 · "+ClassroomExport.time(40));idle(550);check((Integer)field(activity,"pictureIndex")==1,"control centre opens existing choice menu and selects a frame");
        openViewerAndSave();
        recreate();check((Integer)field(activity,"mode")==1&&(Integer)field(activity,"pictureIndex")==2&&((ImageView)field(activity,"slideImage")).getDrawable()!=null,"PPT reading and cached decoding restore after recreation");
        dock=(DockNavigationLayout)field(activity,"sections");Rect dockBounds=bounds(dock);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,dockBounds.left+dockBounds.width()*.2f,dockBounds.centerY());SystemClock.sleep(20);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,dockBounds.right-dockBounds.width()*.15f,dockBounds.centerY());pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,dockBounds.right-dockBounds.width()*.15f,dockBounds.centerY());idle(400);check(field(activity,"sections")==dock,"fast dock fling preserves the original dock instance");capture("reader-after-fling");
        test.runOnMainSync(activity::finish);activity=null;
        attachmentViewer();
    }
    private void entranceCancellation(View root)throws Exception{
        FrameLayout parent=(FrameLayout)root;View probe=new View(activity);float[] minAlpha={1f};int[] frames={0};
        android.view.ViewTreeObserver.OnPreDrawListener observer=()->{frames[0]++;minAlpha[0]=Math.min(minAlpha[0],probe.getAlpha());return true;};
        test.runOnMainSync(()->{parent.addView(probe,new FrameLayout.LayoutParams(32,32));probe.getViewTreeObserver().addOnPreDrawListener(observer);UiMotion.enterPage(probe);UiMotion.finishSwap(probe);});idle(350);
        check(probe.getAlpha()==1f&&probe.getTranslationY()==0f&&minAlpha[0]==1f,"explicit finish before first draw cancels entrance without later restart");
        test.runOnMainSync(()->{UiMotion.enterPage(probe);probe.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){probe.getViewTreeObserver().removeOnPreDrawListener(this);UiMotion.finishSwap(probe);minAlpha[0]=1f;frames[0]=0;return true;}});});idle(350);
        check(frames[0]>0&&minAlpha[0]==1f&&probe.getAlpha()==1f&&probe.getTranslationY()==0f,"finish after pre-draw cancels queued entrance while the window stays attached");
        test.runOnMainSync(()->{probe.getViewTreeObserver().removeOnPreDrawListener(observer);parent.removeView(probe);});
    }
    private void appendCaption(String text)throws Exception{int index=readerSegments.length();readerSegments.put(new JSONObject().put("start",index).put("end",index+1).put("text",text));writeCaptions();}
    private void writeCaptions()throws Exception{ClassroomStore.write(new File(new ClassroomStore(app,account).localLesson(readerLesson),"live-transcript.json"),new JSONObject().put("segments",readerSegments));}
    private void openViewerAndSave()throws Exception{
        Activity monitorActivity=activity;Instrumentation.ActivityMonitor opening=test.addMonitor(AttachmentViewerActivity.class.getName(),null,false);tap((View)field(activity,"slideImage"));
        Activity viewer=test.waitForMonitorWithTimeout(opening,5000);test.removeMonitor(opening);check(viewer!=null,"picture click reuses the actual full-screen attachment viewer");activity=viewer;idle(400);
        ImageView full=(ImageView)field(viewer,"image");View fullPager=(View)field(viewer,"pager");check(full.getDrawable()!=null&&full.getWidth()==fullPager.getWidth()&&full.getHeight()==fullPager.getHeight()&&full.getWidth()>=viewer.getWindow().getDecorView().getWidth()*.8f,"existing viewer fills its actual full-screen viewport instead of a custom dialog");
        capture("viewer-before-doubletap");check(!viewer.isDestroyed()&&full.isAttachedToWindow(),"viewer touch target is the active attached activity");
        doubleTap(full);idle(200);check((Float)field(viewer,"scale")>1f,"existing full-screen double-tap zoom remains usable: scale="+field(viewer,"scale")+", matrixReady="+field(viewer,"matrixReady"));capture("ppt-fullscreen");
        String originalRaw=(String)field(viewer,"rawName");horizontalSwipe(full,.75f,.25f);idle(300);check(originalRaw.equals(field(viewer,"rawName")),"zoomed image pan does not switch gallery");
        doubleTap(full);idle(400);check((Float)field(viewer,"scale")<=1.001f&&!viewer.isFinishing(),"double tap within the panned picture resets zoom");String pinchTrace=pinch(full);idle(250);check(full.isAttachedToWindow()&&!viewer.isFinishing()&&(Float)field(viewer,"scale")>1.1f&&originalRaw.equals(field(viewer,"rawName")),"two-finger pinch zooms the attached viewer without paging: scale="+field(viewer,"scale")+", raw="+field(viewer,"rawName")+", trace="+pinchTrace);
        capture("viewer-after-pinch");doubleTap(full);idle(400);check((Float)field(viewer,"scale")<=1.001f,"double tap resets zoom before a separate paging gesture: "+field(viewer,"scale"));galleryDrag((ImageSlidePager)field(viewer,"pager"),true);
        ImageSlidePager gallery=(ImageSlidePager)field(viewer,"pager");fastSwipe(gallery,false);fastSwipe(gallery,false);idle(550);check((Integer)field(gallery,"index")==0,"full-screen consecutive reverse gestures reach the first frame");fastSwipe(gallery,true);fastSwipe(gallery,true);idle(550);check((Integer)field(gallery,"index")==2,"full-screen consecutive forward gestures reach the last frame");
        saveThroughUi("image/png",".png","ppt-original.png");
        viewer=activity;
        full=(ImageView)field(viewer,"image");Rect fullBounds=bounds(full);android.graphics.RectF actual=new android.graphics.RectF(0,0,full.getDrawable().getIntrinsicWidth(),full.getDrawable().getIntrinsicHeight());full.getImageMatrix().mapRect(actual);
        float emptyX=actual.left>16?actual.left/2:full.getWidth()/2f,emptyY=actual.left>16?full.getHeight()/2f:(actual.bottom+full.getHeight())/2f;
        tap(new Rect(fullBounds.left+(int)emptyX-4,fullBounds.top+(int)emptyY-4,fullBounds.left+(int)emptyX+4,fullBounds.top+(int)emptyY+4));idle(450);
        check(viewer.isFinishing(),"blank outside actual image closes existing viewer");idle(400);activity=((ClassroomMonitorInstrumentation)test).lastResumed;check(activity instanceof ClassroomTranscriptActivity&&(Integer)field(activity,"pictureIndex")==2,"return keeps the image selected in full screen");
    }
    private void landscapeViewer()throws Exception{
        Activity parent=activity;Instrumentation.ActivityMonitor opening=test.addMonitor(AttachmentViewerActivity.class.getName(),null,false);tap((View)field(activity,"slideImage"));Activity viewer=test.waitForMonitorWithTimeout(opening,5000);test.removeMonitor(opening);check(viewer!=null,"landscape uses existing classroom viewer");activity=viewer;idle(500);
        long readyBy=SystemClock.uptimeMillis()+4000;
        do{Activity resumed=((ClassroomMonitorInstrumentation)test).lastResumed;if(resumed instanceof AttachmentViewerActivity&&!resumed.isDestroyed())viewer=resumed;if(!viewer.isDestroyed()&&!viewer.isFinishing()&&viewer.hasWindowFocus()&&viewer.getWindow().getDecorView().getRootWindowInsets()!=null)break;idle(100);}while(SystemClock.uptimeMillis()<readyBy);
        activity=viewer;check(!viewer.isDestroyed()&&!viewer.isFinishing()&&viewer.getWindow().getDecorView().getRootWindowInsets()!=null,"landscape viewer remains attached after configuration recovery");
        View root=viewer.findViewById(android.R.id.content),back=findDescription(root,"返回"),save=findDescription(root,"保存图片");float density=viewer.getResources().getDisplayMetrics().density;
        var insets=viewer.getWindow().getDecorView().getRootWindowInsets();var safe=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());Rect window=bounds(root),left=bounds(back),right=bounds(save);
        check(!insets.isVisible(android.view.WindowInsets.Type.statusBars()),"landscape PPT hides system status bar");
        check(findText(root,"课堂PPT")==null,"landscape has no overlaid PPT title");
        check(Math.abs(left.left-window.left-safe.left-20*density)<3&&Math.abs(window.right-safe.right-right.right-20*density)<3&&Math.abs(left.top-window.top-safe.top-16*density)<3&&left.top==right.top,"return and save occupy safe upper corners");
        capture("landscape-ppt-corners");
        ImageSlidePager gallery=(ImageSlidePager)field(viewer,"pager");burstSwipes(gallery,true);idle(800);check((Integer)field(gallery,"index")==2,"landscape image retains rapid paging");
        saveThroughUi("image/png",".png","landscape-ppt.png");
        check(!activity.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.statusBars()),"return from save restores landscape immersive status");
        tap(findDescription(activity.findViewById(android.R.id.content),"返回"));idle(450);activity=((ClassroomMonitorInstrumentation)test).lastResumed;
        check(activity instanceof ClassroomTranscriptActivity&&activity.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.statusBars()),"leaving the viewer restores the current reader system status bar");
    }
    private void rapidGallery()throws Exception{
        ImageSlidePager pager=(ImageSlidePager)field(activity,"slidePager");
        for(int pass=0;pass<3;pass++){
            fastSwipe(pager,true);fastSwipe(pager,true);idle(500);
            check((Integer)field(pager,"index")==2,"two consecutive left swipes reach last image, pass="+pass+", index="+field(pager,"index"));
            fastSwipe(pager,false);fastSwipe(pager,false);idle(500);
            check((Integer)field(pager,"index")==0,"two consecutive right swipes return to first image, pass="+pass);
        }
        burstSwipes(pager,true);idle(700);check((Integer)field(pager,"index")==2,"two short swipes without synchronous UP waits reach the last image: index="+field(pager,"index")+", requested="+field(pager,"requested")+", offset="+field(pager,"offset"));
        burstSwipes(pager,false);idle(700);check((Integer)field(pager,"index")==0,"two short reverse swipes without synchronous UP waits reach the first image");
        boolean[] animated={false};android.view.ViewTreeObserver.OnPreDrawListener frames=()->{if(Math.abs(pager.image().getTranslationX())>1)animated[0]=true;return true;};
        test.runOnMainSync(()->pager.getViewTreeObserver().addOnPreDrawListener(frames));
        tap((View)field(activity,"nextSlide"));idle(450);
        check((Integer)field(pager,"index")==1,"arrow selects the next image");
        if(UiMotion.isEnabled())check(animated[0],"arrow paints intermediate image translation frames");
        capture("rapid-gallery-arrow");
        tap((View)field(activity,"previousSlide"));idle(450);
        tap((View)field(activity,"nextSlide"));tap((View)field(activity,"nextSlide"));idle(450);
        check((Integer)field(pager,"index")==2,"two quick arrow taps advance twice");
        holdOut((View)field(activity,"previousSlide"),"date-arrow-highlight");
        test.runOnMainSync(()->pager.getViewTreeObserver().removeOnPreDrawListener(frames));
    }
    private void fastSwipe(ImageSlidePager pager,boolean forward)throws Exception{
        Rect area=bounds(pager);float y=Math.min(area.centerY(),bounds(activity.findViewById(android.R.id.content)).bottom-100);
        float start=area.left+area.width()*(forward?.8f:.2f),sign=forward?-1:1;long time=SystemClock.uptimeMillis();
        pointer(time,time,MotionEvent.ACTION_DOWN,start,y);
        pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start+sign*area.width()*.08f,y);
        pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start+sign*area.width()*.58f,y);
        pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,start+sign*area.width()*.58f,y);
    }
    private void burstSwipes(ImageSlidePager pager,boolean forward){
        Rect area=bounds(pager);float y=area.top+area.height()*.4f,start=area.left+area.width()*(forward?.8f:.2f),sign=forward?-1:1;
        for(int gesture=0;gesture<2;gesture++){
            long down=SystemClock.uptimeMillis();
            for(int step=0;step<4;step++){
                long remaining=down+step*8-SystemClock.uptimeMillis();if(remaining>0)SystemClock.sleep(remaining);
                MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),step==0?MotionEvent.ACTION_DOWN:step==3?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,start+sign*area.width()*(step==0?0:step==1?.08f:.4f),y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try{if(!test.getUiAutomation().injectInputEvent(event,false))throw new AssertionError("Owned burst swipe rejected");}finally{event.recycle();}
            }
            SystemClock.sleep(8);
        }
        test.waitForIdleSync();
    }
    private void galleryDrag(ImageSlidePager pager,boolean full)throws Exception{
        Rect area=bounds(pager),picture=imageTouchBounds(pager.image());int before=full?1:0;long time=SystemClock.uptimeMillis();float y=picture.centerY(),start=picture.left+picture.width()*.8f;
        pointer(time,time,MotionEvent.ACTION_DOWN,start,y);pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start-area.width()*.08f,y);
        pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start-area.width()*.4f,y);idle(100);
        capture(full?"gallery-full-held":"gallery-inline-held");check(pager.image().getTranslationX()<-area.width()*.2f,"image follows MOVE before release: ready="+pager.ready()+", blocked="+field(pager,"blocked")+", tracking="+field(pager,"tracking")+", index="+field(pager,"index")+", offset="+field(pager,"offset")+(full?", scale="+field(activity,"scale"):""));
        ImageView neighbour=(ImageView)field(pager,"next");check(neighbour.getDrawable()!=null&&neighbour.getTranslationX()>0&&neighbour.getTranslationX()<area.width(),"adjacent image is visible during finger drag");capture(full?"gallery-full-held":"gallery-inline-held");
        pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,start-area.width()*.4f,y);idle(500);check((Integer)field(pager,"index")==before+1,"release settles on next image");
        if(full){horizontalSwipe(pager,.75f,.25f);idle(500);check((Integer)field(pager,"index")==2,"last page does not wrap");return;}
        horizontalSwipe(pager,.25f,.75f);idle(450);check((Integer)field(pager,"index")==0,"reverse drag settles on previous image");
        time=SystemClock.uptimeMillis();pointer(time,time,MotionEvent.ACTION_DOWN,start,y);pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start-area.width()*.08f,y);pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start-area.width()*.4f,y);
        pointer(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,start-area.width()*.4f,y);idle(500);check((Integer)field(pager,"index")==0&&Math.abs(pager.image().getTranslationX())<1,"cancel returns current image without switching");
    }
    private String pinch(View image)throws Exception{
        Rect area=imageTouchBounds(image);float cx=area.centerX(),cy=area.centerY(),span=area.width()*.7f,endSpan=Math.min(span*3,bounds(image).width()*.9f);long down=SystemClock.uptimeMillis();
        pointer(down,down,MotionEvent.ACTION_DOWN,cx-span/2,cy);
        MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
        for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;}
        StringBuilder trace=new StringBuilder("minSpan="+ViewConfiguration.get(app).getScaledMinimumScalingSpan()+";");
        for(int step=0;step<=9;step++){
              MotionEvent.PointerCoords[] points={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};float distance=(span+(endSpan-span)*Math.min(step,8)/8f)/2;
            for(int i=0;i<2;i++){points[i].x=cx+(i==0?-distance:distance);points[i].y=cy;points[i].pressure=1;points[i].size=1;}
            int action=step==0?MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):step==9?MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,properties,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
            try{test.sendPointerSync(event);}finally{event.recycle();}SystemClock.sleep(24);
            android.view.ScaleGestureDetector detector=(android.view.ScaleGestureDetector)field(activity,"scaleDetector");trace.append(step).append(':').append(detector.getCurrentSpan()).append('/').append(detector.getScaleFactor()).append('/').append(detector.isInProgress()).append(';');
        }
          pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,cx-endSpan/2,cy);
        return trace.toString();
    }
    private void attachmentViewer()throws Exception{
        com.donglan.chrona.image.ImageStore store=new com.donglan.chrona.image.ImageStore(app);
        String name=store.importImage(android.net.Uri.fromFile(new File(new ClassroomStore(app,account).localLesson(readerLesson),"raw-0.img")));
        try{
            activity=test.startActivitySync(new Intent(app,AttachmentViewerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME,name));idle(350);
            check(((ImageView)field(activity,"image")).getDrawable()!=null,"original UUID attachment entry still renders its image");saveThroughUi("image/jpeg",".jpg","attachment-original.jpg");capture("attachment-fullscreen");
            ImageSlidePager pager=(ImageSlidePager)field(activity,"pager");android.graphics.drawable.Drawable complete=pager.image().getDrawable();java.util.concurrent.CountDownLatch decode=new java.util.concurrent.CountDownLatch(1);
            test.runOnMainSync(()->pager.setImages(java.util.List.of("replacement"),0,new ImageSlidePager.Source(){public boolean allowed(){return true;}public File file(String ignored)throws Exception{decode.await();return store.fileFor(name);}}));
            idle(120);check(pager.image().getDrawable()==complete&&!pager.ready(),"replacement list retains the last complete frame until decode");decode.countDown();idle(400);check(pager.ready(),"prepared replacement becomes current without an empty frame");
            test.runOnMainSync(activity::finish);activity=null;
        }finally{store.delete(name);}
    }
    private void saveThroughUi(String mime,String suffix,String filename)throws Exception{
        View save=findDescription(activity.findViewById(android.R.id.content),"保存图片");check(save!=null,"shared viewer exposes the Lucide save action");
        tap(save);idle(450);var root=test.getUiAutomation().getRootInActiveWindow();check(root!=null&&!app.getPackageName().contentEquals(root.getPackageName()),"save action opens the real Android document picker");capture("save-picker-"+suffix.substring(1));
        test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle(300);
        Activity resumed=null;long deadline=SystemClock.uptimeMillis()+3000;
        do{resumed=((ClassroomMonitorInstrumentation)test).lastResumed;var window=test.getUiAutomation().getRootInActiveWindow();if(resumed instanceof AttachmentViewerActivity&&!resumed.isDestroyed()&&resumed.hasWindowFocus()&&window!=null&&app.getPackageName().contentEquals(window.getPackageName()))break;idle(100);}while(SystemClock.uptimeMillis()<deadline);
        check(resumed instanceof AttachmentViewerActivity&&!resumed.isDestroyed()&&resumed.hasWindowFocus(),"document picker returns focus to the current viewer instance before another owned touch");activity=resumed;save=findDescription(activity.findViewById(android.R.id.content),"保存图片");
        File output=new File(new File(app.getExternalFilesDir(null),"monitor-validation"),filename);if(output.isFile()&&!output.delete())throw new IOException("Cannot replace synthetic save target");
        final Intent[] request={null};Instrumentation.ActivityMonitor picker=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(!Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction()))return null;request[0]=intent;return new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(output)));}};
        test.addMonitor(picker);try{tap(save);idle(450);}finally{test.removeMonitor(picker);}
        check(request[0]!=null&&mime.equals(request[0].getType())&&request[0].getStringExtra(Intent.EXTRA_TITLE).endsWith(suffix),"document request preserves actual image MIME and extension");
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(output.getPath(),bounds);
        check(output.isFile()&&bounds.outWidth>0&&mime.equals(bounds.outMimeType),"document result writes decodable original bytes in their actual format");
    }
    private Object page(){return ((ScheduleWorkspaceActivity)activity).currentPage();}
    private void mode(int value)throws Exception{Object page=page();test.runOnMainSync(()->{try{method(page,"selectMode",int.class).invoke(page,value);}catch(Exception e){throw new RuntimeException(e);}});idle(200);}
    private void setQuery(EditText input,String query){test.runOnMainSync(()->{input.requestFocus();input.setText(query);input.setSelection(input.length());});idle(180);}
    private void recreate()throws Exception{Instrumentation.ActivityMonitor monitor=test.addMonitor(activity.getClass().getName(),null,false);test.runOnMainSync(activity::recreate);Activity next=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);check(next!=null,"actual activity recreation completes");activity=next;idle(700);}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void field(Object value,String name,Object next)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.set(value,next);}
    private static Method method(Object value,String name,Class<?>... types)throws Exception{Method m=value.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private void idle(long millis){test.waitForIdleSync();SystemClock.sleep(millis);test.waitForIdleSync();}
    private Rect bounds(View view){Rect rect=new Rect();test.runOnMainSync(()->{if(!view.getGlobalVisibleRect(rect))throw new AssertionError("Pointer target is not visible: "+view.getContentDescription());int[] screen=new int[2],window=new int[2];view.getLocationOnScreen(screen);view.getLocationInWindow(window);rect.offset(screen[0]-window[0],screen[1]-window[1]);});return rect;}
    private static List<Button> buttons(View root){List<Button> result=new ArrayList<>();if(root instanceof Button b)result.add(b);else if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(buttons(group.getChildAt(i)));return result;}
    private static View findText(View root,String text){if(root instanceof TextView t&&text.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=findText(g.getChildAt(i),text);if(result!=null)return result;}return null;}
    private static View findDescription(View root,String text){if(root.getContentDescription()!=null&&text.contentEquals(root.getContentDescription()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=findDescription(g.getChildAt(i),text);if(result!=null)return result;}return null;}
    private boolean findTextInWindow(String text){var root=test.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private boolean nodeChecked(String text){var root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText()))return node.isChecked();throw new AssertionError("Missing checkbox: "+text);}
    private void tapText(String text){var root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText())&&node.isClickable()&&node.isEnabled()&&node.isVisibleToUser()){Rect r=new Rect();node.getBoundsInScreen(r);tap(r);return;}throw new AssertionError("Missing clickable menu option: "+text);}
    private void tap(View view){tap(bounds(view));}
    private void tap(Rect rect){long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY());SystemClock.sleep(60);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY());}
    private void doubleTap(View view){
        Rect rect=imageTouchBounds(view);var active=test.getUiAutomation().getRootInActiveWindow();
        if(!view.isAttachedToWindow()||active==null||!app.getPackageName().contentEquals(active.getPackageName()))throw new AssertionError("Double tap requires the owned fixture window: attached="+view.isAttachedToWindow()+", finishing="+activity.isFinishing()+", package="+(active==null?"null":active.getPackageName()));
        long base=SystemClock.uptimeMillis();
        // Queue the native input stream without waiting for each frame; synchronous UP waits exceed the double-tap timeout.
        for(int i=0;i<4;i++){long at=base+new int[]{0,45,120,165}[i];long remaining=at-SystemClock.uptimeMillis();if(remaining>0)SystemClock.sleep(remaining);MotionEvent event=MotionEvent.obtain(base+(i>=2?120:0),at,i%2==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY(),0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{if(!test.getUiAutomation().injectInputEvent(event,false))throw new AssertionError("Owned double tap rejected");}finally{event.recycle();}}
        test.waitForIdleSync();
    }
    private Rect imageTouchBounds(View view){
        if(!(view instanceof ImageView image)||image.getDrawable()==null)return bounds(view);
        android.graphics.RectF actual=new android.graphics.RectF(0,0,image.getDrawable().getIntrinsicWidth(),image.getDrawable().getIntrinsicHeight());image.getImageMatrix().mapRect(actual);
        if(!actual.intersect(0,0,image.getWidth(),image.getHeight()))throw new AssertionError("No visible image for a picture gesture");
        int[] location=new int[2];image.getLocationOnScreen(location);Rect result=new Rect((int)Math.ceil(actual.left)+location[0],(int)Math.ceil(actual.top)+location[1],(int)Math.floor(actual.right)+location[0],(int)Math.floor(actual.bottom)+location[1]);
        if(!result.intersect(bounds(image)))throw new AssertionError("Picture outside visible viewport");return result;
    }
    private void horizontalSwipe(View view,float start,float end){Rect r=bounds(view);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,r.left+r.width()*start,r.centerY());for(int i=1;i<=12;i++){SystemClock.sleep(18);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,r.left+r.width()*(start+(end-start)*i/12),r.centerY());}pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,r.left+r.width()*end,r.centerY());}
    private void holdOut(View view,String name)throws Exception{Rect rect=bounds(view);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY());SystemClock.sleep(450);capture(name);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,rect.exactCenterX(),rect.top-80);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,rect.exactCenterX(),rect.top-80);idle(350);}
    private void pointer(long down,long at,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,at,action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(event);event.recycle();}
    private void capture(String name)throws Exception{Bitmap image=test.getUiAutomation().takeScreenshot();File directory=new File(app.getExternalFilesDir(null),"monitor-validation");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create UI evidence directory");try(FileOutputStream out=new FileOutputStream(new File(directory,"polish-"+name+"-"+args.getString("theme","light")+"-"+args.getString("widthDp","400")+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
}
