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
        try{checks.seed();String scope=checks.args.getString("polish_scope","");if(!"reader".equals(scope))checks.browse();if(!"browse".equals(scope))checks.reader();if(UiMotion.isEnabled()&&test instanceof ClassroomMonitorInstrumentation monitor){if(!"browse".equals(scope)&&!"true".equals(checks.args.getString("entry_only")))checks.check(monitor.enteredPages.contains("ClassroomTranscriptActivity")&&monitor.enteredPages.contains("AttachmentViewerActivity"),"real reader and viewer entry render intermediate frames: "+monitor.enteredPages);if(!"reader".equals(scope))checks.check(monitor.enteredPages.contains("ClassroomSummaryActivity")&&monitor.enteredPages.contains("ClassroomLessonActivity"),"real catalogue and lesson entry render intermediate frames");}return checks.assertions;}
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
        View title=findText(root,readerLesson.course);Rect titleBounds=bounds(title),scopeBounds=bounds(repeat);check(!Rect.intersects(titleBounds,scopeBounds)&&Math.abs(titleBounds.top-scopeBounds.top)<repeat.getHeight(),"course information and scope checkbox share the top area without overlap");
        DockNavigationLayout dock=(DockNavigationLayout)field(activity,"sections");View host=(View)dock.getParent();ScrollView captions=(ScrollView)field(activity,"scroll");LinearLayout rows=(LinearLayout)field(activity,"rows");
        capture("reader-before-dock-check");
        boolean side=(Boolean)field(activity,"sideNavigation");
        check(side?bounds(host).right<=bounds(captions).left:bounds(host).bottom<=bounds(captions).bottom+1&&bounds(host).top>bounds(captions).centerY(),"subtitle/PPT dock occupies its actual reader edge: host="+bounds(host)+", viewport="+bounds(captions));
        check(side?rows.getPaddingBottom()>=48*app.getResources().getDisplayMetrics().density:rows.getPaddingBottom()>host.getHeight(),"caption content reserves the measured floating actions");capture("reader-bottom-dock");
        entranceCancellation(root);
        if("true".equals(args.getString("entry_only"))){ClassroomMonitorInstrumentation monitor=(ClassroomMonitorInstrumentation)test;check(monitor.enteredPages.contains("ClassroomTranscriptActivity"),"reader entry frames="+monitor.enteredPages+", creates="+monitor.entryCreates+", frames="+monitor.entryFrameCounts);test.runOnMainSync(activity::finish);activity=null;return;}
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
        tap((View)field(activity,"previousSlide"));idle(350);check((Integer)field(activity,"pictureIndex")==0,"common arrow and image gestures share the same selected frame");
        Rect railBounds=bounds(rail);tap(new Rect(railBounds.centerX()-4,railBounds.centerY()-4,railBounds.centerX()+4,railBounds.centerY()+4));idle(200);tapText("2 · "+ClassroomExport.time(40));idle(400);check((Integer)field(activity,"pictureIndex")==1,"control centre opens existing choice menu and selects a frame");
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
        saveThroughUi("image/png",".png","ppt-original.png");
        viewer=activity;
        full=(ImageView)field(viewer,"image");Rect fullBounds=bounds(full);android.graphics.RectF actual=new android.graphics.RectF(0,0,full.getDrawable().getIntrinsicWidth(),full.getDrawable().getIntrinsicHeight());full.getImageMatrix().mapRect(actual);
        float emptyX=actual.left>16?actual.left/2:full.getWidth()/2f,emptyY=actual.left>16?full.getHeight()/2f:(actual.bottom+full.getHeight())/2f;
        tap(new Rect(fullBounds.left+(int)emptyX-4,fullBounds.top+(int)emptyY-4,fullBounds.left+(int)emptyX+4,fullBounds.top+(int)emptyY+4));idle(450);
        check(viewer.isFinishing(),"blank outside actual image closes existing viewer");idle(400);activity=((ClassroomMonitorInstrumentation)test).lastResumed;check(activity instanceof ClassroomTranscriptActivity&&(Integer)field(activity,"pictureIndex")==2,"return keeps the image selected in full screen");
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
    private void tapText(String text){var root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText())){Rect r=new Rect();node.getBoundsInScreen(r);tap(r);return;}throw new AssertionError("Missing menu option: "+text);}
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
