package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.net.RequestControl;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

/** Synthetic fixtures only; explicitly refuses real hardware and an existing school account. */
final class ClassroomDeviceCheck {
    private static final String ACCOUNT="00000000-0000-0000-0000-000000000036";
    private static int assertions;
    static String run(Instrumentation test,Bundle args) throws Exception {
        Context context=test.getTargetContext();
        if(!context.getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated followup package only");
        boolean reminders=AgendaReminderPreferences.enabled(context);
        AgendaReminderPreferences.setEnabled(context,false);
        context.getSystemService(android.app.NotificationManager.class).cancelAll();
        try { return runFixture(test,args); }
        finally {
            new ZjuSettingsStore(context).prefs.edit().putBoolean("enabled",false).commit();
            AgendaReminderPreferences.setEnabled(context,reminders);
        }
    }
    private static String runFixture(Instrumentation test,Bundle args) throws Exception {
        if(!Build.FINGERPRINT.contains("generic")&&!Build.MODEL.contains("Emulator")
                &&!("ranchu".equals(Build.HARDWARE)&&Build.MODEL.startsWith("Android SDK built for")))throw new IllegalStateException("Emulator only");
        Context context=test.getTargetContext();ZjuSettingsStore settings=new ZjuSettingsStore(context);
        if(settings.configured()&&!ACCOUNT.equals(settings.account()))throw new IllegalStateException("Existing account; fixture refused");
        settings.prefs.edit().putString("account",ACCOUNT).putString("credentials","synthetic-not-a-login")
                .putBoolean("enabled",true).putBoolean("timetable",true).commit();
        ThemeStore.setMode(context,args.getString("theme","light"));
        ClassroomStore store=new ClassroomStore(context,ACCOUNT);LocalDate monday=LocalDate.now(ClassroomLesson.ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long now=System.currentTimeMillis();List<ClassroomLesson> lessons=new ArrayList<>();
        List<com.donglan.chrona.zju.ZjuSnapshot.Lesson> timetableRows=new ArrayList<>();
        boolean headingOnly="school_heading".equals(args.getString("scope"));
        for(int day=0;day<(headingOnly?10:5);day++) {
            long start=monday.plusDays(day%5).atTime(10,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
            String course=headingOnly?"课程 "+(day+1):day%2==0?"微积分（甲）":"线性代数（甲）";
            timetableRows.add(new com.donglan.chrona.zju.ZjuSnapshot.Lesson("synthetic-"+day,course,"东1-201","2026-2027 秋",start,start+90*60000,"合成课表验收"));
        }
        new ZjuTimetableStore(context).save(ACCOUNT,timetableRows,java.util.List.of("2026-2027 秋"));
        ClassroomLesson ready=new ClassroomLesson("100","1000","微积分 · 导数与变化率","切线、极限与导数","东1-201","6",monday.atTime(8,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli());
        if(!ready.selectable(now))ready=new ClassroomLesson("100","1000",ready.course,ready.title,ready.room,"6",now-3600000);
        lessons.add(ready);
        lessons.add(new ClassroomLesson("100","1001","准备课堂","录像处理中","东1-201","2",now-1800000));
        lessons.add(new ClassroomLesson("200","2000","未来课堂","向量空间与线性映射","西2-301","6",now+86400000));
        for(int i=0;i<70;i++)lessons.add(new ClassroomLesson("300",Integer.toString(3000+i),"线性代数","第 "+(70-i)+" 讲","东2-102","6",now-(i+1)*86400000L));
        lessons.sort(Comparator.comparingLong((ClassroomLesson l)->l.startsAt).reversed());store.catalog(lessons);
        File dir=store.lesson(ready);JSONArray segments=new JSONArray()
                .put(new JSONObject().put("start",15).put("end",42).put("text","导数描述函数在一点附近的瞬时变化率。把割线两点的距离逐渐缩小，割线斜率的极限就是切线斜率。"))
                .put(new JSONObject().put("start",120).put("end",160).put("text","以 f(x)=x² 为例，代入差商并展开，得到 2x+h，当 h 趋于零，导数为 2x。"))
                .put(new JSONObject().put("start",300).put("end",320).put("text","课后完成教材习题 3.1 的第 2、4、6 题，下次课讨论。"));
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",segments).put("images",new JSONArray()).put("raw_images",367));
        Bitmap slide=slide();try(FileOutputStream out=new FileOutputStream(new File(dir,"image-0.jpg"))){slide.compress(Bitmap.CompressFormat.JPEG,92,out);}slide.recycle();
        JSONArray notes=new JSONArray().put(new JSONObject().put("title","从割线走向切线").put("text","平均变化率是两点之间的割线斜率。令间隔 h 趋于 0，得到某一点的瞬时变化率。\nf′(x) = lim[h→0] (f(x+h) − f(x)) / h")
                .put("sources",new JSONArray().put(0)).put("images",new JSONArray().put("image-0.jpg")))
                .put(new JSONObject().put("title","例题：平方函数的导数").put("text","将 f(x)=x² 代入差商：(x+h)² − x² = 2xh + h²。除以 h 后得到 2x+h，因此 f′(x)=2x。")
                        .put("sources",new JSONArray().put(1)).put("images",new JSONArray()));
        JSONObject summary=new JSONObject().put("lesson",ready.json()).put("updated",now).put("complete",true).put("run","native-fixture")
                .put("overview","导数把局部变化率与曲线切线联系起来。先理解差商，再通过极限计算导数；平方函数给出最直接的例子。")
                .put("parts",new JSONArray().put(new JSONObject().put("overview","导数的定义和平方函数例题").put("notes",notes)
                        .put("assignments",new JSONArray().put(new JSONObject().put("text","完成教材习题 3.1 第 2、4、6 题，下次课讨论。").put("sources",new JSONArray().put(2))))))
                .put("total_parts",1).put("images",new JSONArray().put(new JSONObject().put("file","image-0.jpg").put("second",20)))
                .put("candidate_images",44).put("raw_images",367).put("planned_images",1);
        ClassroomStore.write(new File(dir,"summary.json"),summary);
        if("week_timetable".equals(args.getString("scope")))return WeekTimetableDeviceCheck.run(test,args,store,lessons,monday);
        if("portability".equals(args.getString("scope")))return portability(test,dir,summary);
        if("export_share".equals(args.getString("scope")))return ExportShareDeviceCheck.classroom(test,args,ready);
        if("settings".equals(args.getString("scope"))) {
            File unused=new File(dir,"raw-999.img");ClassroomStore.writeBytes(unused,new byte[]{1,2,3});
            Activity page=test.startActivitySync(new Intent(context,ZjuSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                View save=(View)field(page,"save"),logout=(View)field(page,"logout"),sync=(View)field(page,"sync");
                waitFor(test,()->save.getWidth()>0,"account action row laid out");
                require(save.getParent()==logout.getParent()&&save.getTop()==logout.getTop(),"save and logout share one row");
                require(((TextView)save).getLineCount()==1&&((TextView)logout).getLineCount()==1,"account actions remain readable on one line");
                require(sync instanceof android.widget.ImageButton&&"立即同步".contentEquals(sync.getContentDescription()),"sync is header icon action");
                screenshot(test,context,"classroom-settings-top-"+args.getString("theme","light"));
                View cleanup=findText(page.getWindow().getDecorView(),"清理未引用的课堂图片",true);
                test.runOnMainSync(()->cleanup.requestRectangleOnScreen(new Rect(0,0,cleanup.getWidth(),cleanup.getHeight()),true));test.waitForIdleSync();
                screenshot(test,context,"classroom-settings-cleanup-"+args.getString("theme","light"));
                test.runOnMainSync(cleanup::performClick);waitFor(test,()->cleanup.isEnabled()&&!unused.exists(),"manual cleanup completes");
                require(new File(dir,"image-0.jpg").isFile(),"manual cleanup preserves referenced image");
            }finally{test.runOnMainSync(page::finish);}
            return new JSONObject().put("assertions",assertions).put("settingsAndCleanup",true).toString();
        }
        if("markdown".equals(args.getString("scope"))) {
            summary.getJSONArray("images").put(new JSONObject().put("file","image-999.jpg").put("second",20));
            notes.getJSONObject(0).put("text","平均变化率为 $\\frac{f(x+h)-f(x)}{h}$。\n\n$$\nf'(x)=\\lim_{h\\to0}\\frac{f(x+h)-f(x)}{h}\n$$\n\n![割线与切线](images/image-0.jpg)");
            notes.getJSONObject(1).put("text","矩阵的秩为 $r(A)=2$。\n\n$$A=\\begin{pmatrix}1&0\\\\0&1\\end{pmatrix}$$\n\n**例题**：$\\int_0^1 x^2\\,\\mathrm{d}x=\\frac13$。\n\n`$code$` 保留原样。");
            ClassroomStore.write(new File(dir,"summary.json"),summary);
            return markdown(test,args,ready);
        }
        File imagesDir=new File(dir,"bitmap-check");imagesDir.mkdirs();
        for(int i=0;i<3;i++)java.nio.file.Files.copy(new File(dir,"image-0.jpg").toPath(),new File(imagesDir,"raw-"+i+".img").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        JSONObject imageMaterial=new JSONObject().put("images",new JSONArray()
                .put(new JSONObject().put("url","https://video.cmc.zju.edu.cn/synthetic0").put("second",1))
                .put(new JSONObject().put("url","https://video.cmc.zju.edu.cn/synthetic1").put("second",2))
                .put(new JSONObject().put("url","https://video.cmc.zju.edu.cn/synthetic2").put("second",3)));
        try(ClassroomClient client=new ClassroomClient("unused","unused");RequestControl control=new RequestControl(30000)) {
            JSONArray dedup=ClassroomImages.prepare(imagesDir,imageMaterial,client,control,status->{});
            require(dedup.length()==1&&dedup.getJSONObject(0).getInt("index")==2,"native bitmap final duplicate");
        }
        Activity activity=test.startActivitySync(new Intent(context,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFor(test,()->!busy(activity),"local catalog");
        ScheduleWorkspaceActivity workspace=(ScheduleWorkspaceActivity)activity;
        test.runOnMainSync(()->workspace.selectPage(0));
        waitFor(test,()->workspace.selectedPage()==0&&findType(workspace.currentPage().content(),TimetableGrid.class)!=null,"school timetable uses saved school cache");
        require(findText(workspace.currentPage().content(),"2026-2027 秋",true)!=null,"school original term label retained");
        DockNavigationLayout sources=(DockNavigationLayout)field(workspace,"sourceDock");
        View refreshSchool=(View)field(workspace,"refreshButton");
        require(refreshSchool.getParent() instanceof WorkspacePageHeader,"school actions and source selector share header");
        WorkspacePageHeader schoolHeader=(WorkspacePageHeader)refreshSchool.getParent();
        LinearLayout.LayoutParams sourceSlot=(LinearLayout.LayoutParams)schoolHeader.getChildAt(1).getLayoutParams();
        int gap=Math.round(8*activity.getResources().getDisplayMetrics().density);
        require(sourceSlot.leftMargin==gap&&sourceSlot.rightMargin==gap,"school selector has 8dp adjacent gaps");
        require(schoolHeader.getChildDrawingOrder(3,2)==1&&schoolHeader.getChildAt(2)==refreshSchool,
                "source highlight draws above refresh while action stays at right");
        require(findText(workspace.currentPage().content(),"导入 ICS",true)==null,"no redundant header import button");
        require(findText(workspace.currentPage().content(),"2026-2027 秋",true).isClickable(),"term title opens term chooser");
        // WindowManager's launch transform can still be active after the view tree is idle.
        screenshot(test,context,"school-selector-before-input");
        if ("school_heading".equals(args.getString("scope"))) {
            TextView term=(TextView)findText(workspace.currentPage().content(),"2026-2027 秋",true);
            View parent=(View)term.getParent();
            require(Math.abs(term.getLeft()+term.getWidth()/2f-parent.getWidth()/2f)<=1,"term centered on card");
            require((term.getPaintFlags()&Paint.UNDERLINE_TEXT_FLAG)!=0,"term text underlined");
            test.runOnMainSync(activity::finish);
            return "PASS: school term centered on card and underlined";
        }
        tap(test,sources.itemAt(1));
        waitFor(test,()->findText(workspace.currentPage().content(),"选择 ICS 文件",true)!=null,"import empty state");
        require(field(workspace,"sourceDock")==sources&&sources.isAttachedToWindow(),"source animation retains navigation instance");
        waitFor(test,()->refreshSchool.getVisibility()==View.GONE,"refresh exit finishes");
        require(!refreshSchool.isClickable()&&refreshSchool.getLayoutParams().width==0
                &&((LinearLayout.LayoutParams)schoolHeader.getChildAt(1).getLayoutParams()).rightMargin==0,
                "hidden refresh leaves no clickable or empty slot");
        screenshot(test,context,"import-empty-"+args.getString("theme","light"));
        if ("header".equals(args.getString("scope"))) {
            tap(test,sources.itemAt(0));
            waitFor(test,()->refreshSchool.getAlpha()==1f,"refresh enters");
            test.runOnMainSync(()->schoolHeader.setActionVisible(false));
            SystemClock.sleep(48);
            final float[] middle=new float[1];
            test.runOnMainSync(()->{middle[0]=refreshSchool.getAlpha();schoolHeader.setActionVisible(true);
                require(Math.abs(refreshSchool.getAlpha()-middle[0])<.001f,"reverse keeps current opacity");});
            require(middle[0]>0f&&middle[0]<1f,"refresh exit has intermediate frames");
            waitFor(test,()->refreshSchool.getAlpha()==1f,"reverse settles visible");
            test.runOnMainSync(()->workspace.selectPage(1));
            waitFor(test,()->workspace.selectedPage()==1&&!busy(activity),"classroom visible");
            WorkspacePageHeader classroomHeader=(WorkspacePageHeader)findType(workspace.currentPage().content(),WorkspacePageHeader.class);
            LinearLayout.LayoutParams classroomSlot=(LinearLayout.LayoutParams)classroomHeader.getChildAt(1).getLayoutParams();
            require(classroomSlot.leftMargin==gap&&classroomSlot.rightMargin==gap,"classroom selector has matching 8dp gaps");
            screenshot(test,context,"classroom-header-"+args.getString("theme","light"));
            test.runOnMainSync(activity::finish);
            return "PASS: selector 8dp gaps; refresh exit, collapsed slot and continuous reversal";
        }
        CompactUiDeviceCheck.uniformLabels(test,sources);
        CompactUiDeviceCheck.dockMotion(test,sources,"compact-source",true);
        tap(test,sources.itemAt(0));
        waitFor(test,()->findType(workspace.currentPage().content(),TimetableGrid.class)!=null,"return to school source");
        screenshot(test,context,"school-timetable-"+args.getString("theme","light"));
        test.runOnMainSync(()->workspace.selectPage(1));waitFor(test,()->workspace.selectedPage()==1,"return to classroom");
        require((int)field(session(activity),"mode")==0,"default week");
        DateIntervalBar dates=(DateIntervalBar)field(activity,"weekBar");
        test.runOnMainSync(()-> {
            ViewGroup viewport=(ViewGroup)dates.getChildAt(0);
            float scale=dates.getResources().getDisplayMetrics().density;
            require(Math.abs(viewport.getHeight()-(dates.getHeight()-8*scale))<=2,
                    "date viewport fills rail height with symmetric margins");
            require(viewport.getChildAt(0).getHeight()==viewport.getHeight(),"date text centered within full viewport");
        });
        require(findText(activity.getWindow().getDecorView(),"上一周",true)==null
                &&findText(activity.getWindow().getDecorView(),"下一周",true)==null,"week navigation reuses shared date rail");
        LocalDate originalWeek=(LocalDate)field(session(activity),"week");
        WorkspaceDeviceCheck.swipe(test, dates, .70f, .30f, 24);
        waitFor(test,()->originalWeek.plusWeeks(1).equals(field(session(activity),"week")),"physical swipe advances classroom week");
        require(workspace.selectedPage()==1,"date swipe keeps classroom workspace page");
        WorkspaceDeviceCheck.swipe(test, dates, .30f, .70f, 24);
        waitFor(test,()->originalWeek.equals(field(session(activity),"week")),"physical swipe returns classroom week");
        require(workspace.selectedPage()==1,"reverse date swipe keeps classroom workspace page");
        test.runOnMainSync(()->dates.getChildAt(2).performClick());
        waitFor(test,()->originalWeek.plusWeeks(1).equals(field(session(activity),"week")),"shared week rail next");
        require(field(activity,"weekBar")==dates&&dates.isAttachedToWindow(),"week rail retained during update");
        test.runOnMainSync(()->dates.getChildAt(1).performClick());
        waitFor(test,()->originalWeek.equals(field(session(activity),"week")),"shared week rail previous");
        View future=findText(activity.getWindow().getDecorView(),"未来课堂",false);
        if(future!=null)require(!future.isEnabled(),"future disabled");
        require(!findText(activity.getWindow().getDecorView(),"准备课堂",false).isEnabled(),"preparing disabled");
        screenshot(test,context,"classroom-week-"+args.getString("theme","light"));
        DockNavigationLayout navigation=(DockNavigationLayout)field(activity,"navigation");
        WorkspacePageHeader classroomHeader=(WorkspacePageHeader)navigation.getParent().getParent();
        require(classroomHeader.getChildDrawingOrder(3,2)==1,"classroom highlight draws above refresh");
        CompactUiDeviceCheck.uniformLabels(test,navigation);
        CompactUiDeviceCheck.dockMotion(test,navigation,"compact-classroom",true);
        float density=context.getResources().getDisplayMetrics().density;
        require(navigation.getHeight()>=Math.round(54*density),"classroom dock includes explicit height and padding");
        require(navigation.getPaddingLeft()>=Math.round(4*density),"classroom dock horizontal padding");
        require(navigation.itemAt(0).getHeight()>=Math.round(48*density),"classroom option touch height");
        tap(test,navigation.itemAt(1));waitFor(test,()->(int)field(session(activity),"mode")==1,"list tab");
        LinearLayout list=(LinearLayout)field(activity,"listBody");require(list.getChildCount()==50,"first page 50");
        require(findText(list,"未来课堂",false)==null,"list excludes future");
        screenshot(test,context,"classroom-list-"+args.getString("theme","light"));
        View more=findDescription(activity.getWindow().getDecorView(),"下一页");
        test.runOnMainSync(more::performClick);test.waitForIdleSync();
        list=(LinearLayout)field(activity,"listBody");require(list.getChildCount()==22,"second page replaces first page");
        View previous=findDescription(activity.getWindow().getDecorView(),"上一页");test.runOnMainSync(previous::performClick);test.waitForIdleSync();
        require(((LinearLayout)field(activity,"listBody")).getChildCount()==50,"previous page returns to first fifty lessons");
        test.runOnMainSync(()->rootScroll(activity).scrollTo(0,0));test.waitForIdleSync();
        tap(test,navigation.itemAt(2));waitFor(test,()->(int)field(session(activity),"mode")==2,"history tab");
        Instrumentation.ActivityMonitor detailMonitor=test.addMonitor(ClassroomLessonActivity.class.getName(),null,false);
        tap(test,findText(activity.getWindow().getDecorView(),"微积分",false));
        Activity lessonActivity=test.waitForMonitorWithTimeout(detailMonitor,10000);test.removeMonitor(detailMonitor);
        require(lessonActivity instanceof ClassroomLessonActivity && lessonActivity!=activity,"lesson uses a separate Activity");
        waitFor(test,()->!busy(lessonActivity)&&field(session(lessonActivity),"selected")!=null,"open cached history");
        waitFor(test,()->field(lessonActivity,"documentView")!=null,"summary WebView visible");
        screenshot(test,context,"classroom-detail-"+args.getString("theme","light"));
        Object detailSession=session(lessonActivity);
        test.runOnMainSync(()->{
            set(detailSession,"busy",true);
            set(detailSession,"startedAt",SystemClock.elapsedRealtime()-65000);
            set(detailSession,"progress",new ClassroomProgress("下载课堂截图",66,130,
                    "已缓存 66 张"));
            try {java.lang.reflect.Method update=ClassroomLessonActivity.class.getDeclaredMethod("update");update.setAccessible(true);update.invoke(lessonActivity);}
            catch(Exception error){throw new IllegalStateException(error);}
        });
        TextView progressLabel=(TextView)field(lessonActivity,"progressCount");
        require(progressLabel.getLineCount()==1,"progress count stays on one line");
        require(progressLabel.getText().toString().equals("66/130")&&((TextView)field(lessonActivity,"progressTime")).getText().toString().startsWith("00:01:"),"separate count and elapsed time");
        android.widget.ProgressBar bar=(android.widget.ProgressBar)field(lessonActivity,"progressBar");
        require(!bar.isIndeterminate()&&bar.getMax()==130&&bar.getProgress()==66,"actual completed units");
        View progressPanel=(View)field(lessonActivity,"progressPanel");int panelHeight=progressPanel.getHeight();
        test.runOnMainSync(()->{set(detailSession,"progress",new ClassroomProgress("下载课堂截图",100,130,"已缓存 100 张"));});
        SystemClock.sleep(1200);test.waitForIdleSync();require(progressPanel.getHeight()==panelHeight,"progress numbers do not resize layout");
        screenshot(test,context,"classroom-progress-"+args.getString("theme","light"));
        test.runOnMainSync(()->{set(detailSession,"busy",false);set(detailSession,"message","");});
        android.webkit.WebView note=(android.webkit.WebView)field(lessonActivity,"documentView");
        test.runOnMainSync(()->{ScrollView parent=(ScrollView)findType(lessonActivity.getWindow().getDecorView(),ScrollView.class);parent.fullScroll(View.FOCUS_DOWN);});test.waitForIdleSync();
        require("true".equals(javascript(test,note,"document.images.length>0&&[...document.images].every(i=>i.complete&&i.naturalWidth>0)")),"local inline image decoded");
        screenshot(test,context,"classroom-note-"+args.getString("theme","light"));
        File archive=new File(context.getExternalFilesDir(null),"classroom-native-export.zip");
        try(FileOutputStream out=new FileOutputStream(archive)){ClassroomExport.write(dir,summary,out);}
        try(ZipFile zip=new ZipFile(archive)){require(zip.getEntry("课堂总结.md")!=null&&zip.getEntry("images/image-0.jpg")!=null&&zip.getEntry("sources.md")!=null,"native export payload");}
        test.runOnMainSync(()->{try{
            java.lang.reflect.Method method=ClassroomLessonActivity.class.getDeclaredMethod("generationDialog",com.donglan.chrona.ai.AiSettings.class,boolean.class);
            method.setAccessible(true);method.invoke(lessonActivity,new com.donglan.chrona.ai.AiSettings("https://example.invalid/long-service-path/for-dialog-layout/v1","synthetic-vision-model","synthetic-unused"),false);
        }catch(Exception e){throw new IllegalStateException(e);}});
        test.waitForIdleSync();
        View dialogRoot=null;
        for(View candidate:android.view.inspector.WindowInspector.getGlobalWindowViews())
            if(findText(candidate,"生成课堂总结",true)!=null)dialogRoot=candidate;
        require(dialogRoot!=null,"generation dialog shown");
        final View dialog=dialogRoot;
        android.widget.CheckBox upload=(android.widget.CheckBox)findType(dialog,android.widget.CheckBox.class);
        require(upload!=null&&upload.isChecked()&&upload.getText().toString().contains("40"),"image upload checkbox with forty-image budget");
        test.runOnMainSync(upload::performClick);require(!upload.isChecked(),"text-only mode can be selected");
        View dismiss=findText(dialog,"取消",true);
        test.runOnMainSync(()->dismiss.requestRectangleOnScreen(new Rect(0,0,dismiss.getWidth(),dismiss.getHeight()),true));
        test.waitForIdleSync();
        Rect visible=new Rect();require(dismiss.getGlobalVisibleRect(visible)&&visible.height()>=dismiss.getHeight()-2,"dialog cancel reachable");
        screenshot(test,context,"classroom-dialog-"+args.getString("theme","light"));
        test.runOnMainSync(dismiss::performClick);test.waitForIdleSync();
        test.runOnMainSync(()->settings.prefs.edit().putBoolean("enabled",false).commit());
        waitFor(test,lessonActivity::isFinishing,"disabled gate finishes");
        require(!activity.isFinishing(),"account gate retains the workspace");
        return new JSONObject().put("assertions",assertions).put("synthetic",true).put("theme",args.getString("theme","light"))
                .put("screenshotDirectory",context.getExternalFilesDir(null).getAbsolutePath()).put("nativeBitmapAtomicExport",true).toString();
    }
    private static String javascript(Instrumentation test,android.webkit.WebView web,String script)throws Exception {
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] result={null};
        test.runOnMainSync(()->web.evaluateJavascript(script,value->{result[0]=value;latch.countDown();}));
        if(!latch.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("WebView JavaScript timeout");return result[0];
    }
    private static String portability(Instrumentation test,File dir,JSONObject summary)throws Exception {
        Context context=test.getTargetContext();String id=ClassroomDocument.syncId(summary.getJSONObject("lesson"));
        com.donglan.chrona.sync.SyncState state=new com.donglan.chrona.sync.SyncState("classroom-device-fixture");
        synchronized(AndroidSync.LOCK){try(AndroidSyncData data=new AndroidSyncData(context)){
            data.db.delete("sync_classroom_map",null,null);
            AndroidSyncData.Snapshot snapshot=data.prepareCapture();data.captureClassrooms(state,snapshot);
            JSONObject payload=state.records().get(id).get(0).payload;
            require(payload.getString("kind").equals("classroom")&&payload.getJSONArray("attachments").length()==1,"sync captures completed summary and referenced JPEG");
            com.donglan.chrona.sync.TaskCodec.validateId(id,payload);
            require(com.donglan.chrona.sync.SyncExchange.attachments(state).size()==1,"exchange enumerates classroom attachments");
            JSONObject remote=new JSONObject(payload.toString());remote.getJSONObject("document").getJSONObject("summary").put("overview","远端完整总结 $r(A)=2$");
            state.put(id,remote);data.applyClassrooms(state,snapshot,null);
            require(ClassroomStore.read(new File(dir,"summary.json")).getString("overview").equals("远端完整总结 $r(A)=2$"),"remote document replaces local completed copy");
            require(new File(dir,"image-0.jpg").isFile(),"remote apply preserves actual referenced JPEG");
            String before=state.toJson().toString();snapshot=data.prepareCapture();data.captureClassrooms(state,snapshot);
            require(before.equals(state.toJson().toString()),"imported document recapture creates no echo version");
            JSONObject localEdit=ClassroomStore.read(new File(dir,"summary.json"));localEdit.put("overview","new local edit").put("updated",System.currentTimeMillis()+1000);ClassroomStore.write(new File(dir,"summary.json"),localEdit);
            data.applyClassrooms(state,snapshot,null);
            require(ClassroomStore.read(new File(dir,"summary.json")).getString("overview").equals("new local edit"),"late local edit survives stale remote apply");
            ClassroomStore.write(new File(dir,"summary.json"),remote.getJSONObject("document").getJSONObject("summary"));
            for(File cached:ClassroomStore.lessonDirectories(context))if(cached.getName().equals("100-1000"))ClassroomArchive.remove(cached);
            snapshot=data.prepareCapture();data.applyClassrooms(state,snapshot,null);
            File recovered=new ClassroomStore(context,ACCOUNT).lesson(ClassroomLesson.fromJson(summary.getJSONObject("lesson")));
            require(ClassroomStore.read(new File(recovered,"summary.json")).getString("overview").equals("远端完整总结 $r(A)=2$"),"unchanged remote baseline restores a missing local cache");
            // Restore the account-local copy as well for the remaining fixture assertions.
            java.util.Map<String,File> recoveredImages=new java.util.HashMap<>();recoveredImages.put("image-0.jpg",new File(recovered,"image-0.jpg"));
            ClassroomArchive.write(dir,remote.getJSONObject("document"),recoveredImages);snapshot=data.prepareCapture();
            JSONObject unsafe=new JSONObject(remote.toString());unsafe.getJSONArray("attachments").getJSONObject(0).put("name","../escape.jpg");
            boolean rejected=false;try{com.donglan.chrona.sync.TaskCodec.validate(unsafe);}catch(java.io.IOException expected){rejected=true;}require(rejected,"sync rejects traversal image names");
            ClassroomStore.begin(dir);try{
                JSONObject blocked=new JSONObject(remote.toString());blocked.getJSONObject("document").getJSONObject("summary").put("overview","must wait");state.put(id,blocked);data.applyClassrooms(state,snapshot,null);
                require(ClassroomStore.read(new File(dir,"summary.json")).getString("overview").equals("远端完整总结 $r(A)=2$"),"remote apply never replaces an active lesson");
            }finally{ClassroomStore.end(dir);}
        }}
        // This fixture deliberately has no encrypted login; backup restore must see it as unconfigured.
        new ZjuSettingsStore(context).prefs.edit().remove("credentials").putBoolean("enabled",false).commit();
        java.lang.reflect.Method create=ChronaDataBackup.class.getDeclaredMethod("createArchive",Context.class,boolean.class);create.setAccessible(true);
        File archive=(File)create.invoke(null,context,false);
        try(ZipFile zip=new ZipFile(archive)){
            require(zip.getEntry("classrooms/100-1000/document.json")!=null&&zip.getEntry("classrooms/100-1000/image-0.jpg")!=null,"full backup contains document and image");
            require(zip.stream().noneMatch(e->e.getName().contains("raw-")||e.getName().contains("run.json")),"backup excludes download and AI retry checkpoints");
        }
        ChronaDataBackup.Prepared prepared=ChronaDataBackup.prepare(context,android.net.Uri.fromFile(archive));
        try{
            JSONObject changed=ClassroomStore.read(new File(dir,"summary.json"));changed.put("overview","changed after backup");ClassroomStore.write(new File(dir,"summary.json"),changed);
            ClassroomStore.begin(dir);try{
                boolean refused=false;try{ChronaDataBackup.restore(context,prepared);}catch(java.io.IOException expected){refused=true;}
                require(refused,"restore refuses an active classroom generation");
            }finally{ClassroomStore.end(dir);}
            java.lang.reflect.Field staging=ChronaDataBackup.Prepared.class.getDeclaredField("directory");staging.setAccessible(true);
            File stagedImage=new File((File)staging.get(prepared),"classrooms/100-1000/image-0.jpg");
            byte[] savedImage=java.nio.file.Files.readAllBytes(stagedImage.toPath());java.nio.file.Files.delete(stagedImage.toPath());
            boolean refused=false;try{ChronaDataBackup.restore(context,prepared);}catch(java.io.IOException expected){refused=true;}
            require(refused,"restore detects image missing after preview");
            require(ClassroomStore.read(new File(dir,"summary.json")).getString("overview").equals("changed after backup")&&new File(dir,"image-0.jpg").isFile(),"failed restore preserves local classroom document and image");
            java.nio.file.Files.write(stagedImage.toPath(),savedImage);
            ChronaDataBackup.restore(context,prepared);
            File restored=new ClassroomStore(context,ACCOUNT).lesson(ClassroomLesson.fromJson(summary.getJSONObject("lesson")));
            require(ClassroomStore.read(new File(restored,"summary.json")).getString("overview").equals("远端完整总结 $r(A)=2$"),"full restore restores original Markdown source");
            require(new File(restored,"image-0.jpg").isFile()&&ClassroomStore.read(new File(restored,"materials.json")).getJSONArray("segments").length()==3,"restore preserves image and complete transcript");
            require(new ClassroomStore(context,ACCOUNT).history().size()>0,"restored shared history is reachable from account");
        }finally{prepared.cleanup();archive.delete();}
        return new JSONObject().put("assertions",assertions).put("nativeSyncAndBackup",true).put("synthetic",true).toString();
    }
    private static View findDescription(View view,String description){
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;}return null;
    }
    private static String markdown(Instrumentation test,Bundle args,ClassroomLesson lesson)throws Exception {
        Context context=test.getTargetContext();Activity activity=test.startActivitySync(new Intent(context,ClassroomLessonActivity.class)
                .putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFor(test,()->field(activity,"documentView")!=null,"Markdown view created");
        android.webkit.WebView web=(android.webkit.WebView)field(activity,"documentView");
        long deadline=SystemClock.elapsedRealtime()+30000;String ready="";
        while(SystemClock.elapsedRealtime()<deadline){ready=javascript(test,web,"window.classroomReady===true");if("true".equals(ready))break;SystemClock.sleep(150);}
        require("true".equals(ready),"offline Markdown and MathJax ready");
        JSONObject metrics=new JSONObject(javascript(test,web,"({svg:document.querySelectorAll('mjx-container svg').length,errors:document.querySelectorAll('mjx-merror,.formula-error').length,images:document.images.length,loaded:[...document.images].every(i=>i.complete&&i.naturalWidth>0),overflow:document.documentElement.scrollWidth>innerWidth+1,code:document.querySelector('code').textContent,assignment:getComputedStyle(document.querySelector('.assignment')).color})"));
        require(metrics.getInt("svg")==5&&metrics.getInt("errors")==0,"fractions, limit, rank, matrix and integral render as SVG");
        require(metrics.getInt("images")==1&&metrics.getBoolean("loaded"),"inline image loads once without duplicate appendix");
        require(!metrics.getBoolean("overflow"),"document fits viewport");require(metrics.getString("code").equals("$code$"),"code dollars stay literal");
        require(metrics.getString("assignment").equals(args.getString("theme","light").equals("dark")?"rgb(128, 186, 255)":"rgb(20, 104, 200)"),"assignment text is blue");
        require(web.getHeight()>200,"WebView height follows actual rendered content");
        require(((View)field(activity,"export")).getParent()!=null,"export icon retained in native header");
        screenshot(test,context,"classroom-markdown-top-"+args.getString("theme","light"));
        test.runOnMainSync(()->{ScrollView scroll=(ScrollView)findType(activity.getWindow().getDecorView(),ScrollView.class);scroll.fullScroll(View.FOCUS_DOWN);});test.waitForIdleSync();
        metrics.put("layout",new JSONObject(javascript(test,web,"({height:document.querySelector('main').getBoundingClientRect().height,tail:document.querySelector('.assignment').getBoundingClientRect().bottom,viewport:innerHeight,scrollY,devicePixelRatio})")));
        metrics.put("nativeHeight",web.getHeight()).put("nativeScroll",web.getScrollY());
        screenshot(test,context,"classroom-markdown-bottom-"+args.getString("theme","light"));
        javascript(test,web,"document.querySelector('a[href=\"action/source/0\"]').click();true");test.waitForIdleSync();
        View sourceDialog=null;for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews())if(findText(window,"00:00:15 — 00:00:42",true)!=null)sourceDialog=window;
        require(sourceDialog!=null,"Markdown source opens native transcript dialog");
        require(countClassroomImages(sourceDialog)==1,"source dialog omits cleaned unused images without blank placeholders");
        screenshot(test,context,"classroom-markdown-source-"+args.getString("theme","light"));
        View close=findText(sourceDialog,"关闭",true);test.runOnMainSync(close::performClick);test.waitForIdleSync();
        javascript(test,web,"document.querySelector('.assignment').click();true");test.waitForIdleSync();
        boolean confirmation=false;for(View window:android.view.inspector.WindowInspector.getGlobalWindowViews())if(findText(window,"提取课堂安排",true)!=null)confirmation=true;
        require(confirmation,"blue assignment opens existing native extraction confirmation");
        screenshot(test,context,"classroom-markdown-extract-"+args.getString("theme","light"));
        test.runOnMainSync(activity::finish);
        return new JSONObject().put("assertions",assertions).put("metrics",metrics).put("synthetic",true).toString();
    }
    private static Bitmap slide(){Bitmap b=Bitmap.createBitmap(1200,675,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(248,247,242));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(30,65,68));p.setTextSize(52);c.drawText("导数：从平均变化率到瞬时变化率",70,100,p);p.setTextSize(36);c.drawText("f(x) = x²     f′(x) = 2x",70,170,p);p.setStrokeWidth(4);c.drawLine(160,560,1000,560,p);c.drawLine(160,560,160,230,p);p.setColor(Color.rgb(39,130,132));for(int x=0;x<700;x++){float y=x*x/1800f;c.drawPoint(180+x,540-y,p);}p.setColor(Color.rgb(194,101,60));c.drawLine(370,540,850,300,p);p.setTextSize(30);c.drawText("h → 0",790,280,p);return b;}
    private static Object session(Activity a){return field(a,"s");}
    private static void set(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception error){throw new IllegalStateException(error);}}
    private static boolean busy(Activity a){return (boolean)field(session(a),"busy");}
    private static Object field(Object object,String name){if(object instanceof ScheduleWorkspaceActivity workspace)object=workspace.currentPage();try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void waitFor(Instrumentation test,BooleanSupplier condition,String label) throws Exception {long end=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<end){test.waitForIdleSync();if(condition.getAsBoolean())return;SystemClock.sleep(100);}throw new IllegalStateException("Timed out: "+label);}
    private static View findText(View view,String text,boolean exact){if(view instanceof TextView t&&(exact?t.getText().toString().equals(text):t.getText().toString().contains(text)))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),text,exact);if(found!=null)return found;}return null;}
    private static boolean hasImage(View view){if(view instanceof ImageView image&&image.getDrawable()!=null&&"课堂截图，点击放大".contentEquals(image.getContentDescription()))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasImage(group.getChildAt(i)))return true;return false;}
    private static int countClassroomImages(View view){int count=view instanceof ImageView&&"课堂截图，点击放大".contentEquals(view.getContentDescription())?1:0;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=countClassroomImages(group.getChildAt(i));return count;}
    private static ScrollView rootScroll(Activity a){return (ScrollView)findType(a.getWindow().getDecorView(),ScrollView.class);}
    private static View findType(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=findType(g.getChildAt(i),type);if(result!=null)return result;}return null;}
    private static void tap(Instrumentation test,View view)throws Exception{if(view==null)throw new IllegalStateException("Tap target missing");waitFor(test,()->view.hasWindowFocus()&&view.isShown()&&view.getWidth()>0,"tap window ready");int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));long t=SystemClock.uptimeMillis();float x=xy[0]+view.getWidth()/2f,y=xy[1]+view.getHeight()/2f;MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,x,y,0);test.sendPointerSync(down);test.sendPointerSync(up);down.recycle();up.recycle();}
    private static void screenshot(Instrumentation test,Context context,String name) throws Exception {test.waitForIdleSync();SystemClock.sleep(400);Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IllegalStateException("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private static void require(boolean value,String label){assertions++;if(!value)throw new IllegalStateException(label);}
}
