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
        try{checks.seed();if(!"reader".equals(checks.args.getString("polish_scope")))checks.browse();checks.reader();return checks.assertions;}
        finally{if(checks.activity!=null)test.runOnMainSync(checks.activity::finish);}
    }
    private void seed()throws Exception{
        ClassroomStore store=new ClassroomStore(app,account);long now=System.currentTimeMillis();
        for(int i=0;i<56;i++){
            ClassroomLesson lesson=new ClassroomLesson("8800",""+(8900+i),i==55?"检索课程Ω":"目录样本"+i,
                    i==55?"课题：矩阵与空间":"目录课堂"+i,i==55?"紫金港检索楼":"教室"+i,"6",now-(i+1)*3_600_000L);
            catalog.add(lesson);if(i==55)searchable=lesson;
        }
        for(int i=0;i<55;i++){
            ClassroomLesson lesson=new ClassroomLesson("8810",""+(9000+i),"排序样本"+String.format(java.util.Locale.ROOT,"%02d",i),"历史课堂"+i,"历史教室","6",now-(i+2)*86_400_000L);
            catalog.add(lesson);
            JSONObject summary=new JSONObject().put("lesson",lesson.json()).put("updated",now-(55-i)*60_000L)
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
        ClassroomStore.writeBytes(new File(dir,"raw-1.img"),bytes.toByteArray());ClassroomStore.writeBytes(new File(dir,"raw-2.img"),bytes.toByteArray());
        ClassroomStore.write(new File(dir,"monitor-candidates.json"),new JSONObject().put("updated",now).put("images",new JSONArray().put(new JSONObject().put("raw","raw-0.img").put("second",20)).put(new JSONObject().put("raw","raw-1.img").put("second",40)).put(new JSONObject().put("raw","raw-2.img").put("second",60))));
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
        setQuery(input,"检索课程Ω");capture("search");
        test.runOnMainSync(()->app.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0));idle(200);
        Button result=buttons((View)field(page,"listBody")).get(0);
        Instrumentation.ActivityMonitor detail=test.addMonitor(ClassroomLessonActivity.class.getName(),null,false);tap(result);
        Activity opened=test.waitForMonitorWithTimeout(detail,5000);test.removeMonitor(detail);check(opened!=null,"search result opens the actual classroom detail");
        test.runOnMainSync(opened::finish);idle(500);check(field(page(),"search")==input&&"检索课程Ω".contentEquals(input.getText()),"return from detail preserves the query and input instance");
        mode(3);idle(250);mode(1);idle(200);check("检索课程Ω".contentEquals(input.getText()),"switching views preserves the query");
        recreate();page=page();input=(EditText)field(page,"search");check("检索课程Ω".contentEquals(input.getText()),"recreation preserves query");
        setQuery(input,"");page=page();check(buttons((View)field(page,"listBody")).size()==50,"catalogue paginates only after filtering");
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
        activity=test.startActivitySync(new Intent(app,ClassroomTranscriptActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("account",account).putExtra("lesson",readerLesson.json().toString()));idle(800);
        View root=activity.findViewById(android.R.id.content);CheckBox repeat=(CheckBox)field(activity,"repeat");
        check("后续课程".contentEquals(repeat.getText())&&"监视后续课程".contentEquals(repeat.getContentDescription()),"scope checkbox keeps its short label and full accessible meaning");
        check(findDescription(root,"监视运行设置")==null,"dedicated monitor page removes the settings gear");
        View title=findText(root,readerLesson.course);Rect titleBounds=bounds(title),scopeBounds=bounds(repeat);check(!Rect.intersects(titleBounds,scopeBounds)&&Math.abs(titleBounds.top-scopeBounds.top)<repeat.getHeight(),"course information and scope checkbox share the top area without overlap");
        DockNavigationLayout dock=(DockNavigationLayout)field(activity,"sections");View host=(View)dock.getParent();ScrollView captions=(ScrollView)field(activity,"scroll");LinearLayout rows=(LinearLayout)field(activity,"rows");
        check(bounds(host).bottom<=bounds(captions).bottom+1&&bounds(host).top>bounds(captions).centerY(),"subtitle/PPT dock floats at the bottom of the actual reader");
        check(rows.getPaddingBottom()>host.getHeight(),"caption content reserves the complete measured dock envelope");capture("reader-bottom-dock");
        int initialY=captions.getScrollY();check(initialY>0,"first opening performs its one latest-caption position");
        appendCaption("首次进入后的追加不拉动偏移");idle(500);check(captions.getScrollY()==initialY,"appending even at the initial latest position preserves exact Y");
        Rect viewport=bounds(captions);long held=SystemClock.uptimeMillis();float x=viewport.centerX(),start=viewport.top+viewport.height()*.18f,end=viewport.top+viewport.height()*.66f;
        pointer(held,held,MotionEvent.ACTION_DOWN,x,start);for(int i=1;i<=10;i++){SystemClock.sleep(20);pointer(held,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,start+(end-start)*i/10);}
        SystemClock.sleep(120);int heldY=captions.getScrollY();View firstParagraph=rows.getChildAt(0);appendCaption("触摸仍按住字幕子项时追加");idle(450);check(captions.getScrollY()==heldY&&rows.getChildAt(0)==firstParagraph,"actual held caption browsing survives incoming text without replacing paragraphs");
        pointer(held,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,end);idle(500);int before=captions.getScrollY();
        for(int i=0;i<3;i++){appendCaption("连续追加"+i);idle(350);check(captions.getScrollY()==before,"consecutive appends preserve the settled reading offset");}
        readerSegments.getJSONObject(0).put("text","首句修订增加文本：课堂知识点、矩阵与空间，保持当前滚动数值。".repeat(3));writeCaptions();idle(400);check(captions.getScrollY()==before,"within-sentence revisions preserve exact reading Y");
        check(!(Boolean)field(activity,"pendingLatest"),"user touch cancels any pending latest-position request");capture("reader-held-append");
        View latest=(View)field(activity,"latest");check(bounds(latest).bottom<bounds(host).top,"return-to-latest action sits above the complete dock envelope");
        View readingBody=(View)field(activity,"readingBody");java.util.concurrent.atomic.AtomicBoolean transitionObserved=new java.util.concurrent.atomic.AtomicBoolean();
        android.view.ViewTreeObserver.OnPreDrawListener transitionFrame=()->{if(readingBody.getAlpha()>0f&&readingBody.getAlpha()<1f)transitionObserved.set(true);return true;};
        test.runOnMainSync(()->readingBody.getViewTreeObserver().addOnPreDrawListener(transitionFrame));tap(dock.itemAt(1));idle(350);
        test.runOnMainSync(()->readingBody.getViewTreeObserver().removeOnPreDrawListener(transitionFrame));if(UiMotion.isEnabled())check(transitionObserved.get(),"subtitle to PPT renders actual intermediate public content-transition frames");
        appendCaption("PPT浏览期间新增字幕");idle(350);tap(dock.itemAt(0));idle(350);check(captions.getScrollY()==before&&field(activity,"sections")==dock,"tab return preserves reading Y and original dock");
        tap(repeat);idle(150);recreate();check(((CheckBox)field(activity,"repeat")).isChecked(),"unstarted monitoring scope selection restores before monitoring begins");
        captions=(ScrollView)field(activity,"scroll");check(captions.getScrollY()==before,"real activity recreation restores reading Y after asynchronous caption layout");
        latest=(View)field(activity,"latest");tap(latest);idle(350);check((Boolean)field(activity,"follow")&&captions.getScrollY()>before,"actual latest button explicitly positions the last caption");
        dock=(DockNavigationLayout)field(activity,"sections");holdOut(dock.itemAt(0),"dock-first-held");holdOut(dock.itemAt(1),"dock-last-held");
        dock=(DockNavigationLayout)field(activity,"sections");tap(dock.itemAt(1));idle(450);check((Integer)field(activity,"mode")==1&&((ImageView)field(activity,"slideImage")).getDrawable()!=null,"bottom PPT dock opens the actual cached frame");capture("reader-ppt");
        ImageView slide=(ImageView)field(activity,"slideImage");View frame=(View)field(activity,"slideFrame");check(frame.getClipToOutline()&&frame.getOutlineProvider()!=null,"PPT frame clips to the shared corner scale");
        android.graphics.Outline pictureOutline=new android.graphics.Outline();test.runOnMainSync(()->slide.getOutlineProvider().getOutline(slide,pictureOutline));Rect pictureBounds=new Rect();
        check(slide.getClipToOutline()&&pictureOutline.getRect(pictureBounds)&&pictureOutline.getRadius()>0&&Math.abs(pictureBounds.width()/(float)pictureBounds.height()-320f/180f)<.02,"actual FIT_CENTER picture edges retain rounded corners inside letterboxed frame");
        View rail=(View)field(activity,"slidesBar");check(rail instanceof DateIntervalBar,"PPT navigation uses the shared date-interval component");horizontalSwipe(rail,.7f,.3f);idle(450);check((Integer)field(activity,"pictureIndex")==1,"real left swipe on the common control selects next PPT");
        horizontalSwipe(slide,.75f,.25f);idle(450);check((Integer)field(activity,"pictureIndex")==2,"real left swipe on the picture selects the same next direction");
        horizontalSwipe(slide,.25f,.75f);idle(450);check((Integer)field(activity,"pictureIndex")==1,"picture right swipe returns to previous frame");
        tap((View)field(activity,"previousSlide"));idle(350);check((Integer)field(activity,"pictureIndex")==0,"common arrow and image gestures share the same selected frame");
        Rect railBounds=bounds(rail);tap(new Rect(railBounds.centerX()-4,railBounds.centerY()-4,railBounds.centerX()+4,railBounds.centerY()+4));idle(200);tapText("2 · "+ClassroomExport.time(40));idle(400);check((Integer)field(activity,"pictureIndex")==1,"control centre opens existing choice menu and selects a frame");
        openViewerAndSave();
        recreate();check((Integer)field(activity,"mode")==1&&(Integer)field(activity,"pictureIndex")==1&&((ImageView)field(activity,"slideImage")).getDrawable()!=null,"PPT reading and cached decoding restore after recreation");
        dock=(DockNavigationLayout)field(activity,"sections");Rect dockBounds=bounds(dock);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,dockBounds.left+dockBounds.width()*.2f,dockBounds.centerY());SystemClock.sleep(20);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,dockBounds.right-dockBounds.width()*.15f,dockBounds.centerY());pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,dockBounds.right-dockBounds.width()*.15f,dockBounds.centerY());idle(400);check(field(activity,"sections")==dock,"fast dock fling preserves the original dock instance");capture("reader-after-fling");
        test.runOnMainSync(activity::finish);activity=null;
        attachmentViewer();
    }
    private void appendCaption(String text)throws Exception{int index=readerSegments.length();readerSegments.put(new JSONObject().put("start",index).put("end",index+1).put("text",text));writeCaptions();}
    private void writeCaptions()throws Exception{ClassroomStore.write(new File(new ClassroomStore(app,account).localLesson(readerLesson),"live-transcript.json"),new JSONObject().put("segments",readerSegments));}
    private void openViewerAndSave()throws Exception{
        Activity monitorActivity=activity;Instrumentation.ActivityMonitor opening=test.addMonitor(AttachmentViewerActivity.class.getName(),null,false);tap((View)field(activity,"slideImage"));
        Activity viewer=test.waitForMonitorWithTimeout(opening,5000);test.removeMonitor(opening);check(viewer!=null,"picture click reuses the actual full-screen attachment viewer");activity=viewer;idle(400);
        ImageView full=(ImageView)field(viewer,"image");check(full.getDrawable()!=null&&full.getHeight()>((View)field(monitorActivity,"slideImage")).getHeight(),"existing viewer renders a full-screen image instead of a custom dialog");
        doubleTap(full);idle(200);check((Float)field(viewer,"scale")>1f,"existing full-screen double-tap zoom remains usable");capture("ppt-fullscreen");
        saveThroughUi("image/png",".png","ppt-original.png");test.runOnMainSync(viewer::finish);activity=monitorActivity;idle(400);
    }
    private void attachmentViewer()throws Exception{
        com.donglan.chrona.image.ImageStore store=new com.donglan.chrona.image.ImageStore(app);
        String name=store.importImage(android.net.Uri.fromFile(new File(new ClassroomStore(app,account).localLesson(readerLesson),"raw-0.img")));
        try{
            activity=test.startActivitySync(new Intent(app,AttachmentViewerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AttachmentViewerActivity.EXTRA_IMAGE_NAME,name));idle(350);
            check(((ImageView)field(activity,"image")).getDrawable()!=null,"original UUID attachment entry still renders its image");saveThroughUi("image/jpeg",".jpg","attachment-original.jpg");capture("attachment-fullscreen");
            test.runOnMainSync(activity::finish);activity=null;
        }finally{store.delete(name);}
    }
    private void saveThroughUi(String mime,String suffix,String filename)throws Exception{
        View save=findDescription(activity.findViewById(android.R.id.content),"保存图片");check(save!=null,"shared viewer exposes the Lucide save action");
        tap(save);idle(450);var root=test.getUiAutomation().getRootInActiveWindow();check(root!=null&&!app.getPackageName().contentEquals(root.getPackageName()),"save action opens the real Android document picker");capture("save-picker-"+suffix.substring(1));
        test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);idle(300);
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
    private Rect bounds(View view){Rect rect=new Rect();test.runOnMainSync(()->{if(!view.getGlobalVisibleRect(rect))throw new AssertionError("Pointer target is not visible: "+view.getContentDescription());});return rect;}
    private static List<Button> buttons(View root){List<Button> result=new ArrayList<>();if(root instanceof Button b)result.add(b);else if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(buttons(group.getChildAt(i)));return result;}
    private static View findText(View root,String text){if(root instanceof TextView t&&text.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=findText(g.getChildAt(i),text);if(result!=null)return result;}return null;}
    private static View findDescription(View root,String text){if(root.getContentDescription()!=null&&text.contentEquals(root.getContentDescription()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=findDescription(g.getChildAt(i),text);if(result!=null)return result;}return null;}
    private boolean findTextInWindow(String text){var root=test.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private boolean nodeChecked(String text){var root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText()))return node.isChecked();throw new AssertionError("Missing checkbox: "+text);}
    private void tapText(String text){var root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText())){Rect r=new Rect();node.getBoundsInScreen(r);tap(r);return;}throw new AssertionError("Missing menu option: "+text);}
    private void tap(View view){tap(bounds(view));}
    private void tap(Rect rect){long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY());SystemClock.sleep(60);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY());}
    private void doubleTap(View view){Rect rect=bounds(view);tap(rect);SystemClock.sleep(70);tap(rect);}
    private void horizontalSwipe(View view,float start,float end){Rect r=bounds(view);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,r.left+r.width()*start,r.centerY());for(int i=1;i<=12;i++){SystemClock.sleep(18);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,r.left+r.width()*(start+(end-start)*i/12),r.centerY());}pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,r.left+r.width()*end,r.centerY());}
    private void holdOut(View view,String name)throws Exception{Rect rect=bounds(view);long down=SystemClock.uptimeMillis();pointer(down,down,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY());SystemClock.sleep(450);capture(name);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,rect.exactCenterX(),rect.top-80);pointer(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,rect.exactCenterX(),rect.top-80);idle(350);}
    private void pointer(long down,long at,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,at,action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(event);event.recycle();}
    private void capture(String name)throws Exception{Bitmap image=test.getUiAutomation().takeScreenshot();File directory=new File(app.getExternalFilesDir(null),"monitor-validation");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create UI evidence directory");try(FileOutputStream out=new FileOutputStream(new File(directory,"polish-"+name+"-"+args.getString("theme","light")+"-"+args.getString("widthDp","400")+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
}
