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
        for(int day=0;day<5;day++){long start=monday.plusDays(day).atTime(10,0).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();timetableRows.add(new com.donglan.chrona.zju.ZjuSnapshot.Lesson("synthetic-"+day,day%2==0?"微积分（甲）":"线性代数（甲）","东1-201","2026-2027 秋",start,start+90*60000,"合成课表验收"));}
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
        screenshot(test,context,"school-timetable-"+args.getString("theme","light"));
        test.runOnMainSync(()->workspace.selectPage(1));waitFor(test,()->workspace.selectedPage()==1,"return to classroom");
        require((int)field(session(activity),"mode")==0,"default week");
        View future=findText(activity.getWindow().getDecorView(),"未来课堂",false);
        if(future!=null)require(!future.isEnabled(),"future disabled");
        require(!findText(activity.getWindow().getDecorView(),"准备课堂",false).isEnabled(),"preparing disabled");
        screenshot(test,context,"classroom-week-"+args.getString("theme","light"));
        DockNavigationLayout navigation=(DockNavigationLayout)field(activity,"navigation");
        float density=context.getResources().getDisplayMetrics().density;
        require(navigation.getHeight()>=Math.round(54*density),"classroom dock includes explicit height and padding");
        require(navigation.getPaddingLeft()>=Math.round(4*density),"classroom dock horizontal padding");
        require(navigation.itemAt(0).getHeight()>=Math.round(48*density),"classroom option touch height");
        tap(test,navigation.itemAt(1));waitFor(test,()->(int)field(session(activity),"mode")==1,"list tab");
        LinearLayout list=(LinearLayout)field(activity,"listBody");require(list.getChildCount()==50,"first page 50");
        require(findText(list,"未来课堂",false)==null,"list excludes future");
        screenshot(test,context,"classroom-list-"+args.getString("theme","light"));
        View more=findText(activity.getWindow().getDecorView(),"加载更多",true);
        test.runOnMainSync(more::performClick);test.waitForIdleSync();
        require(list.getChildCount()==72,"append second page");
        test.runOnMainSync(()->rootScroll(activity).scrollTo(0,0));test.waitForIdleSync();
        tap(test,navigation.itemAt(2));waitFor(test,()->(int)field(session(activity),"mode")==2,"history tab");
        Instrumentation.ActivityMonitor detailMonitor=test.addMonitor(ClassroomLessonActivity.class.getName(),null,false);
        tap(test,findText(activity.getWindow().getDecorView(),"微积分",false));
        Activity lessonActivity=test.waitForMonitorWithTimeout(detailMonitor,10000);test.removeMonitor(detailMonitor);
        require(lessonActivity instanceof ClassroomLessonActivity && lessonActivity!=activity,"lesson uses a separate Activity");
        waitFor(test,()->!busy(lessonActivity)&&field(session(lessonActivity),"selected")!=null,"open cached history");
        require(findText(lessonActivity.getWindow().getDecorView(),"重点概览",false)!=null,"summary visible");
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
        View note=findText(lessonActivity.getWindow().getDecorView(),"从割线走向切线",true);
        test.runOnMainSync(()->note.requestRectangleOnScreen(new Rect(0,0,note.getWidth(),note.getHeight()),true));test.waitForIdleSync();
        waitFor(test,()->hasImage(lessonActivity.getWindow().getDecorView()),"native image decoded");
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
        require(upload!=null&&upload.isChecked()&&upload.getText().toString().contains("20"),"image upload checkbox with twenty-image budget");
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
    private static Bitmap slide(){Bitmap b=Bitmap.createBitmap(1200,675,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(248,247,242));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(30,65,68));p.setTextSize(52);c.drawText("导数：从平均变化率到瞬时变化率",70,100,p);p.setTextSize(36);c.drawText("f(x) = x²     f′(x) = 2x",70,170,p);p.setStrokeWidth(4);c.drawLine(160,560,1000,560,p);c.drawLine(160,560,160,230,p);p.setColor(Color.rgb(39,130,132));for(int x=0;x<700;x++){float y=x*x/1800f;c.drawPoint(180+x,540-y,p);}p.setColor(Color.rgb(194,101,60));c.drawLine(370,540,850,300,p);p.setTextSize(30);c.drawText("h → 0",790,280,p);return b;}
    private static Object session(Activity a){return field(a,"s");}
    private static void set(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception error){throw new IllegalStateException(error);}}
    private static boolean busy(Activity a){return (boolean)field(session(a),"busy");}
    private static Object field(Object object,String name){if(object instanceof ScheduleWorkspaceActivity workspace)object=workspace.currentPage();try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void waitFor(Instrumentation test,BooleanSupplier condition,String label) throws Exception {long end=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<end){test.waitForIdleSync();if(condition.getAsBoolean())return;SystemClock.sleep(100);}throw new IllegalStateException("Timed out: "+label);}
    private static View findText(View view,String text,boolean exact){if(view instanceof TextView t&&(exact?t.getText().toString().equals(text):t.getText().toString().contains(text)))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),text,exact);if(found!=null)return found;}return null;}
    private static boolean hasImage(View view){if(view instanceof ImageView image&&image.getDrawable()!=null&&"课堂截图，点击放大".contentEquals(image.getContentDescription()))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasImage(group.getChildAt(i)))return true;return false;}
    private static ScrollView rootScroll(Activity a){return (ScrollView)findType(a.getWindow().getDecorView(),ScrollView.class);}
    private static View findType(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=findType(g.getChildAt(i),type);if(result!=null)return result;}return null;}
    private static void tap(Instrumentation test,View view){if(view==null)throw new IllegalStateException("Tap target missing");int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));long t=SystemClock.uptimeMillis();float x=xy[0]+view.getWidth()/2f,y=xy[1]+view.getHeight()/2f;MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,x,y,0);test.sendPointerSync(down);test.sendPointerSync(up);down.recycle();up.recycle();}
    private static void screenshot(Instrumentation test,Context context,String name) throws Exception {test.waitForIdleSync();SystemClock.sleep(400);Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IllegalStateException("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private static void require(boolean value,String label){assertions++;if(!value)throw new IllegalStateException(label);}
}
