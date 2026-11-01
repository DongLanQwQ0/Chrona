package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ScrollView;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipInputStream;

/** Actual local WebView and ZIP entry points, synthetic course only; never calls AI or school. */
final class EnhancedDocumentChecks {
    private final ClassroomMonitorInstrumentation test;
    private final Context app;
    private final Bundle args;
    private final File output;
    private final JSONArray evidence=new JSONArray();
    private final String account;
    private final List<File> owned=new ArrayList<>();
    private Activity screen,anchor,lessonScreen;
    private ClassroomLesson lesson;
    private ClassroomMarkdownView document;
    private File dir;
    private JSONObject summary;
    private int checks;

    private EnhancedDocumentChecks(ClassroomMonitorInstrumentation test,Bundle args){
        this.test=test;this.args=args;app=test.getTargetContext();account=new ZjuSettingsStore(app).account();
        String suffix=switch(args.getString("case","")){case "failure-recovery"->"-failure-recovery";case "background-ready"->"-background-ready";case "corrected-stable"->"-corrected-stable";default->"";};
        output=new File(app.getExternalFilesDir(null),"enhanced-document-"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+suffix);output.mkdirs();
    }
    static int run(ClassroomMonitorInstrumentation test,Bundle args)throws Exception{
        if(!test.getTargetContext().getPackageName().equals("com.donglan.chrona.monitor.validation")||!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned emulator fixture required");
        EnhancedDocumentChecks c=new EnhancedDocumentChecks(test,args);
        try{c.seed();if("corrected-stable".equals(args.getString("case"))){c.correctedStable();return c.checks;}if("background-ready".equals(args.getString("case"))){c.backgroundReady();return c.checks;}if("failure-recovery".equals(args.getString("case")))c.openRecovery();else{c.reader();c.exports();}c.failureReopen();return c.checks;}
        catch(Throwable failure){c.capture("failure");throw failure;}
        finally{
            ClassroomStore.write(new File(c.output,"report.json"),new JSONObject().put("checks",c.checks).put("evidence",c.evidence).put("synthetic",true).put("paid_calls",0));
            if(c.screen!=null)test.runOnMainSync(c.screen::finish);
            if(c.lessonScreen!=null&&c.lessonScreen!=c.screen)test.runOnMainSync(c.lessonScreen::finish);
            if(c.anchor!=null)test.runOnMainSync(c.anchor::finish);
            for(File file:c.owned)file.delete();if(c.dir!=null)c.dir.delete();
        }
    }
    private void seed()throws Exception{
        check(ClassroomMonitor.allowed(app,account),"runner provides enabled synthetic account");
        check(Boolean.parseBoolean(args.getString("nativeScreen","false")),"real display metrics required; no density override");
        lesson=new ClassroomLesson("993400",Long.toUnsignedString(System.nanoTime()),"增强文稿阅读验收","公式、代码与完整导出","合成教室","6",(System.currentTimeMillis()/60000-10)*60000);
        dir=new ClassroomStore(app,account).localLesson(lesson);
        JSONArray segments=new JSONArray().put(new JSONObject().put("id",0).put("start",0).put("end",12).put("text","请保留题目里的限制条件，步骤完整才能拿到全部分数。"))
                .put(new JSONObject().put("id",1).put("start",12).put("end",25).put("text","使用积分和程序代码验证结果。"));
        write("materials.json",new JSONObject().put("lesson",lesson.json()).put("segments",segments).put("raw_images",1).put("images",new JSONArray()));
        write("candidates.json",new JSONObject().put("lesson",lesson.json()).put("images",new JSONArray().put(new JSONObject().put("index",0).put("second",12).put("raw","raw-0.img"))));
        Bitmap image=Bitmap.createBitmap(640,240,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.rgb(230,240,245));Paint p=new Paint(3);p.setColor(Color.rgb(15,70,110));p.setTextSize(40);canvas.drawText("PPT - x + y = z",40,125,p);
        File picture=new File(dir,"image-0.jpg");try(FileOutputStream out=new FileOutputStream(picture)){image.compress(Bitmap.CompressFormat.JPEG,90,out);}image.recycle();owned.add(picture);
        String corrected="## 更正后的授课内容\n\n[0] 题目中的限制条件与步骤分必须保留。[id:99] 原始来源编号。\n\n普通第一段\\n \\n普通第二段\n\n"
                +"行内公式 $\\nabla f=(2x,2y)$。\n\n$$\\int_0^1 x^2\\,dx=\\frac{1}{3}$$\n\n"
                +"```java\nString marker = \"\\n \\n\";\nString ticks = \"```\";\n```\n\n"
                +"![更正文稿课堂截图](images/image-0.jpg)\n\n"
                +"[1] 完整上下文仍按原始顺序保留。\n\n".repeat(20);
        JSONObject part=new JSONObject().put("overview","本块总结").put("notes",new JSONArray().put(new JSONObject().put("title","题目细节与判分")
                .put("text","题目限制、推导步骤与例外都需要记录。\n\n![课堂截图](images/image-0.jpg)").put("sources",new JSONArray().put(0)).put("images",new JSONArray().put("image-0.jpg"))))
                .put("assignments",new JSONArray());
        summary=new JSONObject().put("lesson",lesson.json()).put("run","synthetic-enhanced").put("complete",true).put("updated",System.currentTimeMillis())
                .put("overview","核对老师给出的题目条件与评分依据。").put("parts",new JSONArray().put(part)).put("total_parts",1).put("corrected_transcript",corrected)
                .put("images",new JSONArray().put(new JSONObject().put("file","image-0.jpg").put("second",12))).put("raw_images",1).put("candidate_images",1);
        write("summary.json",summary);
    }
    private void reader()throws Exception{
        anchor=open(new Intent(app,SettingsHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(600);
        screen=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitDocument("documentView");SystemClock.sleep(650);
        Activity parent=screen;ClassroomMarkdownView main=document;int notesHeight=notesHeight(parent),mainHeight=main.getHeight();
        checkTitleAction();
        JSONObject notes=state();
        check(notes.getInt("errors")==0&&notes.getInt("math")==0,"main notes do not typeset an unopened corrected transcript");
        check(!notes.getBoolean("systemDisclosure")&&!notes.getBoolean("paragraphs")&&!notes.getBoolean("rawIds"),"main document has neither system disclosure nor corrected transcript HTML");
        check(countDocuments(parent.getWindow().getDecorView())==1,"lesson owns only its main WebView before corrected-page navigation");capture("lesson-title-action");
        ClassroomStore store=new ClassroomStore(app,account);ClassroomStore.begin(dir);
        try{
            synchronized(ClassroomStore.LOCK){
                check(dir.getCanonicalFile().equals(store.publishedSummary(lesson).getCanonicalFile()),"published summary remains readable while a new owned run leases this lesson");
                boolean rejected=false;try{store.existingSummary(lesson);}catch(IOException busy){rejected=true;}
                check(rejected,"existingSummary retains its idle-only contract while published reading is permitted");
            }
            openCorrection();
        }finally{ClassroomStore.end(dir);}
        await(()->{try{return state().getInt("math")>=2&&document.getAlpha()==1f;}catch(Exception e){return false;}});
        JSONObject opened=state();
        check(opened.getInt("errors")==0,"dedicated corrected page keeps valid formula rendering");
        check(opened.getBoolean("paragraphs")&&opened.getBoolean("codeLiteral"),"paragraph recovery preserves escaped code literals");
        check(opened.getBoolean("rawIds"),"dedicated corrected reader preserves raw source identifiers");
        check(!opened.getBoolean("systemDisclosure")&&main.getHeight()==mainHeight,"corrected page neither uses system details nor alters the parent main height");
        check(opened.getDouble("overflow")<=2,"ordinary portrait or landscape corrected content stays within its viewport");
        check(!"true".equals(js("document.querySelector('main').innerText.includes("+JSONObject.quote(lesson.course)+")")),"corrected body starts with original content without repeated course metadata");
        capture("corrected-page");
        ScrollView viewport=scrollOwner();View title=findText(screen.getWindow().getDecorView(),"更正文稿");
        check(title!=null,"dedicated secondary page has the native corrected-transcript title");
        int prior=viewport.getScrollY();swipeUp();await(()->viewport.getScrollY()>prior);
        check(true,"actual reading swipe scrolls the corrected page and its native header");
        swipeUp();int[] headingAt=new int[2],viewportAt=new int[2];
        test.runOnMainSync(()->{title.getLocationOnScreen(headingAt);viewport.getLocationOnScreen(viewportAt);});
        check(headingAt[1]+title.getHeight()<=viewportAt[1],"whole-page scrolling can move the native header entirely above the viewport");capture("corrected-header-scrolled");
        position(document,"pre");capture("corrected-code");
        check("true".equals(js("document.querySelector('pre').textContent.includes('String ticks')")),"corrected code remains complete");
        position(document,"main > p:last-child");capture("corrected-end");
        check("true".equals(js("document.querySelector('main').lastElementChild.textContent.includes('完整上下文仍按原始顺序保留')")),"complete corrected transcript ending is reachable");
        imageReturn();
        position(document,"pre");rotationRecovery();
        returnToLesson(parent,main,notesHeight);
        JSONObject partial=new JSONObject(summary.toString()).put("complete",false).put("overview","").put("parts",new JSONArray());
        write("summary.json",partial);Object session=field(screen,"s");
        test.runOnMainSync(()->{try{set(session,"summary",partial);invoke(screen,"updateDetail");}catch(Exception e){throw new RuntimeException(e);}});
        awaitDocument("documentView");SystemClock.sleep(400);checkTitleAction();capture("corrected-only-draft");
        check("true".equals(js("![...document.querySelectorAll('main>h2')].some(n=>n.textContent==='重点概览')&&!document.querySelector('main').innerText.includes('等待图片分析')")),"corrected-only draft has no empty overview or invented image phase");
        main=document;notesHeight=notesHeight(screen);openCorrection();
        await(()->{try{return state().getInt("math")>=2;}catch(Exception e){return false;}});
        JSONObject draft=state();check(draft.getBoolean("paragraphs")&&draft.getBoolean("codeLiteral")&&draft.getBoolean("rawIds")&&!draft.getBoolean("systemDisclosure"),"corrected-only checkpoint opens a complete dedicated page before note generation");capture("corrected-only-page");
        returnToLesson(parent,main,notesHeight);
        evidence.put(new JSONObject().put("phase","native-corrected-page").put("screenWidth",screen.getResources().getDisplayMetrics().widthPixels).put("screenHeight",screen.getResources().getDisplayMetrics().heightPixels).put("font",screen.getResources().getConfiguration().fontScale).put("notes",notes).put("corrected",opened));
        write("summary.json",summary);
    }
    private void backgroundReady()throws Exception{
        JSONArray parts=new JSONArray();JSONObject original=summary.getJSONArray("parts").getJSONObject(0);
        for(int i=0;i<5;i++){JSONObject part=new JSONObject(original.toString());part.getJSONArray("notes").getJSONObject(0).put("text",original.getJSONArray("notes").getJSONObject(0).getString("text")+"\n\n代码与题干条件，逐项保留限制和步骤。\n\n".repeat(40));parts.put(part);}
        summary.put("parts",parts).put("corrected_transcript",summary.getString("corrected_transcript").repeat(10)+"\n\n$x_i=i+1$ ".repeat(4631));write("summary.json",summary);
        screen=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).putExtra("hold_markdown_ready",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->{try{document=(ClassroomMarkdownView)field(screen,"documentView");return document!=null&&"\"function\"".equals(js("typeof window.chronaReleaseReady"));}catch(Exception e){return false;}});
        check(document.getVisibility()==View.INVISIBLE,"first long main document stays hidden until local rendering is ready");
        anchor=open(new Intent(app,SettingsHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check(test.lastResumed==anchor&&anchor!=screen,"another actual Activity covers the reader");
        js("window.chronaReleaseReady()");SystemClock.sleep(1000);test.runOnMainSync(anchor::finish);anchor=null;
        await(()->screen.hasWindowFocus());
        await(()->{try{return "true".equals(js("window.classroomReady===true"))&&document.getVisibility()==View.VISIBLE&&document.getAlpha()==1f;}catch(Exception e){return false;}});
        check(document.getHeight()>1000,"background-ready long notes receive their full native height after resume");
        check("true".equals(js("document.querySelectorAll('main>h2').length>=6"))&&correctionToggle().getVisibility()==View.VISIBLE,"all five note blocks and title-row corrected-page action are present");
        JSONObject notes=state();
        check(notes.getInt("math")==0,"unopened corrected transcript does not eagerly typeset formulas");
        check(!notes.getBoolean("systemDisclosure")&&"true".equals(js("document.querySelectorAll('[data-formula]').length===0&&!document.querySelector('main').innerText.includes('普通第一段')")),"long corrected source stays outside the main notes HTML");
        check(countDocuments(screen.getWindow().getDecorView())==1,"long lesson owns no hidden corrected WebView");capture("background-resumed");
        evidence.put(new JSONObject().put("phase","background-ready").put("visibility",document.getVisibility()).put("height",document.getHeight()).put("alpha",document.getAlpha()).put("js",notes));
        Activity parent=screen;ClassroomMarkdownView main=document;int height=notesHeight(parent);
        openCorrection();JSONObject firstScreen=state();
        check(firstScreen.getInt("formulas")==4651,"all 4651 deferred formulas preserve complete source on the separate page");
        check(firstScreen.getInt("math")>=2&&firstScreen.getInt("math")<4651,"corrected first screen is ready before the offscreen formula queue completes");
        check(firstScreen.getInt("errors")==0&&firstScreen.getBoolean("paragraphs")&&firstScreen.getBoolean("codeLiteral")&&firstScreen.getBoolean("rawIds"),"long corrected page retains valid formulas, complete paragraphs, code and raw source IDs");
        check(document.getWidth()>0&&document.getHeight()>1&&document.getVisibility()==View.VISIBLE,"dedicated reader has a real visible viewport after its first-ready bridge");
        capture("background-corrected-first-screen");evidence.put(new JSONObject().put("phase","corrected-page-first-screen-ready").put("js",firstScreen));
        returnToLesson(parent,main,height);
    }
    private void correctedStable()throws Exception{
        JSONObject partial=new JSONObject(summary.toString()).put("complete",false).put("overview","").put("parts",new JSONArray());
        write("summary.json",partial);
        anchor=open(new Intent(app,SettingsHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(600);
        screen=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitDocument("documentView");await(()->{try{return screen.hasWindowFocus()&&document.getAlpha()==1f&&!(Boolean)field(screen,"firstDocumentPresentation");}catch(Exception e){return false;}});
        checkTitleAction();
        evidence.put(new JSONObject().put("phase","stable-checkpoint-parent-action").put("complete",false).put("parts",0).put("action",geometry(correctionToggle())));capture("stable-parent-title");
        openCorrection();await(()->{try{return state().getInt("math")>=2;}catch(Exception e){return false;}});
        JSONObject content=state();check(content.getBoolean("paragraphs")&&content.getBoolean("codeLiteral")&&content.getBoolean("rawIds")&&content.getInt("errors")==0&&!content.getBoolean("systemDisclosure"),"corrected-only checkpoint retains complete original content before stable native-frame capture");
        captureStableCorrected();
        ZjuSettingsStore settings=new ZjuSettingsStore(app);boolean present=settings.prefs.contains("enabled"),enabled=settings.prefs.getBoolean("enabled",false);
        Activity corrected=screen;ClassroomMarkdownView reader=document;
        check(account.equals(settings.account())&&enabled,"account revocation check owns only the enabled fixture account");
        try{
            test.runOnMainSync(()->{if(!settings.prefs.edit().putBoolean("enabled",false).commit())throw new AssertionError("Fixture disable was not saved");});
            await(()->corrected.isFinishing()&&corrected.isDestroyed());
            check(field(corrected,"document")==null&&reader.getParent()==null,"disabling the fixture account actually closes the corrected page and disposes its document");
            evidence.put(new JSONObject().put("phase","stable-checkpoint-account-revoked").put("activityFinished",corrected.isFinishing()).put("activityDestroyed",corrected.isDestroyed()).put("documentDisposed",field(corrected,"document")==null).put("accountUnchanged",account.equals(settings.account())));
        }finally{
            test.runOnMainSync(()->{android.content.SharedPreferences.Editor edit=settings.prefs.edit();if(present)edit.putBoolean("enabled",enabled);else edit.remove("enabled");if(!edit.commit())throw new AssertionError("Fixture enabled setting was not restored");});
        }
    }
    private void captureStableCorrected()throws Exception{
        Activity target=screen;ClassroomMarkdownView rendered=document;View decor=target.getWindow().getDecorView();
        await(()->{try{return target.hasWindowFocus()&&decor.isShown()&&rendered.isShown()&&rendered.getWidth()>0&&rendered.getHeight()>0
                &&rendered.getAlpha()==1f&&rendered.getTranslationX()==0f&&rendered.getTranslationY()==0f
                &&(Boolean)field(target,"documentReady")&&!(Boolean)field(target,"firstPresentation")&&"true".equals(js(rendered,"window.classroomReady===true"));}catch(Exception e){return false;}});
        check(decor.isHardwareAccelerated(),"stable checkpoint uses real hardware frame-commit callbacks");
        CountDownLatch committed=new CountDownLatch(2);JSONArray draws=new JSONArray();String[] failure={""};boolean[] pending={false};long start=SystemClock.uptimeMillis();
        android.view.ViewTreeObserver.OnPreDrawListener observer=()->{
            if(committed.getCount()==0)return true;
            if(!pending[0]&&target.hasWindowFocus()&&rendered.isShown()&&rendered.getAlpha()==1f&&rendered.getTranslationY()==0f){
                try{
                    JSONObject frame=new JSONObject().put("preDrawMs",SystemClock.uptimeMillis()-start).put("document",geometry(rendered));draws.put(frame);pending[0]=true;
                    decor.getViewTreeObserver().registerFrameCommitCallback(()->{
                        try{frame.put("committedMs",SystemClock.uptimeMillis()-start);}catch(Exception error){failure[0]=error.toString();}
                        pending[0]=false;committed.countDown();if(committed.getCount()>0)decor.postInvalidateOnAnimation();
                    });
                }catch(Exception error){failure[0]=error.toString();while(committed.getCount()>0)committed.countDown();}
            }else if(!pending[0])decor.postInvalidateOnAnimation();
            return true;
        };
        test.runOnMainSync(()->{decor.getViewTreeObserver().addOnPreDrawListener(observer);decor.postInvalidateOnAnimation();});
        boolean completed;
        try{completed=committed.await(8,TimeUnit.SECONDS);}
        finally{test.runOnMainSync(()->{if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnPreDrawListener(observer);});}
        evidence.put(new JSONObject().put("phase","stable-checkpoint-drawn-frames").put("frames",draws).put("failure",failure[0]).put("completed",completed));
        check(completed&&failure[0].isEmpty()&&draws.length()==2,"corrected-only firstPresentation is consumed and two actual native frames commit before capture");
        View title=findText(decor,"更正文稿"),back=findDescription(decor,"返回");
        check(title!=null&&back!=null,"stable checkpoint has its native secondary header and back control");
        JSONObject[] layout={null};test.runOnMainSync(()->{try{layout[0]=new JSONObject().put("windowFocus",target.hasWindowFocus()).put("documentReady",field(target,"documentReady")).put("firstPresentation",field(target,"firstPresentation"))
                .put("window",geometry(decor)).put("headerTitle",geometry(title)).put("back",geometry(back)).put("body",geometry((View)field(target,"body"))).put("viewport",geometry((View)field(target,"viewport"))).put("document",geometry(rendered));}catch(Exception error){throw new RuntimeException(error);}});
        evidence.put(new JSONObject().put("phase","stable-checkpoint-first-screen").put("layout",layout[0]).put("dom",state()));capture("stable-corrected-only-page");
    }
    private static JSONObject geometry(View view)throws Exception{
        android.graphics.Rect visible=new android.graphics.Rect();boolean seen=view.getGlobalVisibleRect(visible);int[] at=new int[2];view.getLocationOnScreen(at);
        JSONObject result=new JSONObject().put("visible",seen).put("visibleBounds",visible.toShortString()).put("x",at[0]).put("y",at[1]).put("width",view.getWidth()).put("height",view.getHeight())
                .put("visibility",view.getVisibility()).put("shown",view.isShown()).put("alpha",view.getAlpha()).put("translationX",view.getTranslationX()).put("translationY",view.getTranslationY());
        if(view instanceof android.widget.ImageView image){android.graphics.drawable.Drawable drawable=image.getDrawable();result.put("hasDrawable",drawable!=null);if(drawable!=null)result.put("drawableBounds",drawable.getBounds().toShortString()).put("drawableAlpha",drawable.getAlpha());}
        return result;
    }
    private void checkTitleAction()throws Exception{
        Button button=correctionToggle();android.view.ViewGroup row=(android.view.ViewGroup)button.getParent();
        View course=null;for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i) instanceof android.widget.TextView text&&text!=button&&lesson.course.contentEquals(text.getText()))course=text;
        check(course!=null&&button.getVisibility()==View.VISIBLE&&button.isEnabled(),"corrected-page action shares the native course-title row");
        View title=course;int[] left=new int[2],right=new int[2];test.runOnMainSync(()->{title.getLocationOnScreen(left);button.getLocationOnScreen(right);});
        int required=Math.round(48*button.getResources().getDisplayMetrics().density);
        check(button.getMinimumHeight()>=required&&button.getHeight()>=required,"title-row corrected action retains the shared 48dp native touch area");
        check(left[0]+title.getWidth()<=right[0]&&row.indexOfChild(button)==row.getChildCount()-1,"corrected-page action is at the far right after the course name");
        check(Arrays.stream(button.getCompoundDrawablesRelative()).allMatch(Objects::isNull)&&"查看更正文稿".contentEquals(button.getContentDescription()),"title-row entry is the shared text-only action with an accurate accessible name");
    }
    private void openCorrection()throws Exception{
        Activity parent=screen;lessonScreen=parent;long count=test.entryCreates.stream().filter(s->s.startsWith("ClassroomCorrectedActivity:")).count();
        Button action=correctionToggle();float[] point=position(action);
        check(point[0]>=0&&point[1]>=0&&point[0]<screen.getResources().getDisplayMetrics().widthPixels&&point[1]<screen.getResources().getDisplayMetrics().heightPixels,"actual title-row corrected-page action is reachable");
        tap(point,40);await(()->test.lastResumed instanceof ClassroomCorrectedActivity&&test.lastResumed!=parent);screen=test.lastResumed;awaitDocument("document");
        check(test.entryCreates.stream().filter(s->s.startsWith("ClassroomCorrectedActivity:")).count()==count+1&&countDocuments(screen.getWindow().getDecorView())==1,"one actual title-row tap opens one secondary Activity with one document instance");
        check(!parent.isDestroyed()&&(Boolean)field(screen,"documentReady"),"separate corrected page reads a ready published snapshot without replacing its parent");
    }
    private void returnToLesson(Activity parent,ClassroomMarkdownView main,int height)throws Exception{
        ScrollView viewport=scrollOwner();test.runOnMainSync(()->viewport.scrollTo(0,0));SystemClock.sleep(150);
        View back=findDescription(screen.getWindow().getDecorView(),"返回");check(back!=null,"corrected secondary page exposes the shared native back action");
        Activity child=screen;tap(position(back),60);await(()->test.lastResumed==parent&&child.isFinishing());screen=parent;document=(ClassroomMarkdownView)field(parent,"documentView");
        check(document==main&&notesHeight(parent)==height,"actual native back returns to the same parent document and unchanged notes height");
        check(!state().getBoolean("systemDisclosure")&&countDocuments(parent.getWindow().getDecorView())==1,"return leaves no corrected document or inline disclosure in the lesson");capture("returned-lesson");
    }
    private void imageReturn()throws Exception{
        await(()->{try{return "true".equals(js("!!document.querySelector('img')?.complete&&document.querySelector('img').naturalWidth>0"));}catch(Exception e){return false;}});
        float[] point=position(document,"img");ScrollView viewport=scrollOwner();int before=viewport.getScrollY();
        check(before>0,"corrected image opens from a real nonzero native reading offset");capture("corrected-image-before");
        Activity corrected=screen;ClassroomMarkdownView reader=document;
        tap(point,60);await(()->test.lastResumed instanceof AttachmentViewerActivity&&test.lastResumed!=corrected);screen=test.lastResumed;
        await(()->{try{android.widget.ImageView image=(android.widget.ImageView)field(screen,"image");return image!=null&&image.getDrawable()!=null&&image.getWidth()>0;}catch(Exception e){return false;}});
        check("image-0.jpg".equals(field(screen,"rawName"))&&(Boolean)field(screen,"classroomSummary"),"actual corrected Markdown image opens the existing summary-image viewer with its original identity");capture("corrected-image-viewer");
        View back=findDescription(screen.getWindow().getDecorView(),"返回");check(back!=null,"existing image viewer exposes its native return action");
        android.graphics.Rect bounds=new android.graphics.Rect();test.runOnMainSync(()->{if(!back.getGlobalVisibleRect(bounds))throw new AssertionError("Viewer back action is not visible");});
        Activity viewer=screen;tap(new float[]{bounds.exactCenterX(),bounds.exactCenterY()},60);
        await(()->test.lastResumed==corrected&&viewer.isFinishing());screen=corrected;document=(ClassroomMarkdownView)field(corrected,"document");
        check(document==reader,"native image return restores the same corrected-page reader instance");
        await(()->Math.abs(viewport.getScrollY()-before)<=1);
        check(viewport.getScrollY()>0&&Math.abs(viewport.getScrollY()-before)<=1,"image return without a new scroll restores the original nonzero reading offset");capture("corrected-image-returned");
        evidence.put(new JSONObject().put("phase","corrected-image-roundtrip").put("beforeY",before).put("afterY",viewport.getScrollY()).put("sameDocument",document==reader).put("image","image-0.jpg"));
    }
    private void awaitDocument(String name){
        await(()->{try{document=(ClassroomMarkdownView)field(screen,name);return document!=null&&"true".equals(js("window.classroomReady===true"))&&document.getVisibility()==View.VISIBLE;}catch(Exception e){return false;}});
    }
    private JSONObject state()throws Exception{
        return json(js("JSON.stringify({systemDisclosure:!!document.querySelector('details,summary'),math:document.querySelectorAll('mjx-container').length,formulas:document.querySelectorAll('[data-formula]').length,errors:document.querySelectorAll('.formula-error').length,paragraphs:[...document.querySelectorAll('p')].some(p=>p.textContent==='普通第一段')&&[...document.querySelectorAll('p')].some(p=>p.textContent==='普通第二段'),codeLiteral:!!document.querySelector('code')?.textContent.includes('\\\\n \\\\n'),rawIds:document.querySelector('main').innerText.includes('[id:99]'),overflow:document.documentElement.scrollWidth-innerWidth,height:document.querySelector('main').getBoundingClientRect().height})"));
    }
    private static JSONObject json(String value)throws Exception{return new JSONObject(new JSONArray("["+value+"]").getString(0));}
    private Button correctionToggle()throws Exception{return (Button)field(screen,"correctionToggle");}
    private static int notesHeight(Activity screen)throws Exception{return ((View)field(screen,"notes")).getHeight();}
    private static int countDocuments(View view){int count=view instanceof ClassroomMarkdownView?1:0;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=countDocuments(group.getChildAt(i));return count;}
    private static View findText(View view,String text){if(view instanceof android.widget.TextView label&&text.contentEquals(label.getText()))return view;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static View findDescription(View view,String text){if(text.equals(String.valueOf(view.getContentDescription())))return view;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private void rotationRecovery()throws Exception{
        String initial=args.getString("orientation","portrait"),opposite="landscape".equals(initial)?"portrait":"landscape";
        ScrollView before=scrollOwner();int y=before.getScrollY();ClassroomMarkdownView previous=document;
        check(y>0,"rotation begins from an actual nonzero corrected-page reading position");
        try{
            rotateTo(opposite);await(()->Math.abs(scrollOwner().getScrollY()-y)<=Math.round(24*screen.getResources().getDisplayMetrics().density));
            JSONObject restored=state();
            check(document!=previous&&!(Boolean)field(screen,"firstPresentation"),"actual orientation change restores one fresh corrected view without replaying its initial entrance");
            check(restored.getBoolean("paragraphs")&&restored.getBoolean("codeLiteral")&&restored.getBoolean("rawIds")&&restored.getInt("errors")==0&&restored.getDouble("overflow")<=2&&!restored.getBoolean("systemDisclosure"),"rotated corrected page keeps complete content, valid formulas and viewport bounds");
            capture("corrected-rotated");int rotated=scrollOwner().getScrollY();rotateTo(initial);
            await(()->Math.abs(scrollOwner().getScrollY()-rotated)<=Math.round(24*screen.getResources().getDisplayMetrics().density));
            check(!state().getBoolean("systemDisclosure")&&!(Boolean)field(screen,"firstPresentation"),"actual rotation back preserves reading position and keeps the ready document settled");capture("corrected-rotated-back");
        }finally{
            if(!initial.equals(args.getString("orientation"))){args.putString("orientation",initial);Activity current=test.lastResumed instanceof ClassroomCorrectedActivity?test.lastResumed:screen;
                test.runOnMainSync(()->current.setRequestedOrientation("landscape".equals(initial)?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                test.getUiAutomation().setRotation("landscape".equals(initial)?android.app.UiAutomation.ROTATION_FREEZE_90:android.app.UiAutomation.ROTATION_FREEZE_0);}
        }
    }
    private void rotateTo(String orientation)throws Exception{
        Activity previous=screen;int expected="landscape".equals(orientation)?android.content.res.Configuration.ORIENTATION_LANDSCAPE:android.content.res.Configuration.ORIENTATION_PORTRAIT;args.putString("orientation",orientation);
        test.runOnMainSync(()->previous.setRequestedOrientation(expected==android.content.res.Configuration.ORIENTATION_LANDSCAPE?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        test.getUiAutomation().setRotation(expected==android.content.res.Configuration.ORIENTATION_LANDSCAPE?android.app.UiAutomation.ROTATION_FREEZE_90:android.app.UiAutomation.ROTATION_FREEZE_0);
        await(()->test.lastResumed instanceof ClassroomCorrectedActivity&&test.lastResumed!=previous&&test.lastResumed.getResources().getConfiguration().orientation==expected);
        screen=test.lastResumed;awaitDocument("document");
        evidence.put(new JSONObject().put("phase","corrected-real-rotation").put("orientation",orientation).put("configuration",screen.getResources().getConfiguration().orientation).put("newActivity",screen!=previous).put("scrollY",scrollOwner().getScrollY()));
    }
    private void tap(float[] point,int settleMillis){
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0),up=MotionEvent.obtain(now,now+35,MotionEvent.ACTION_UP,point[0],point[1],0);
        try{test.sendPointerSync(down);SystemClock.sleep(35);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}SystemClock.sleep(settleMillis);
    }
    private ScrollView scrollOwner(){return scrollOwner(document);}
    private ScrollView scrollOwner(View child){for(ViewParent parent=child.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView value)return value;throw new AssertionError("Missing shared page scroll viewport");}
    private float[] position(View view){
        ScrollView owner=scrollOwner(view);float density=view.getResources().getDisplayMetrics().density;
        test.runOnMainSync(()->{int[] at=new int[2],s=new int[2];view.getLocationOnScreen(at);owner.getLocationOnScreen(s);owner.scrollTo(0,owner.getScrollY()+Math.round(at[1]+view.getHeight()/2f-s[1]-80*density));});SystemClock.sleep(150);
        float[] point=new float[2];test.runOnMainSync(()->{android.graphics.Rect visible=new android.graphics.Rect();if(!view.getGlobalVisibleRect(visible))throw new AssertionError("Native action is not visible");point[0]=visible.exactCenterX();point[1]=visible.exactCenterY();});return point;
    }
    private float[] position(ClassroomMarkdownView view,String selector)throws Exception{
        JSONObject rect=json(js(view,"JSON.stringify((()=>{let r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})())"));
        ScrollView owner=scrollOwner(view);float density=view.getResources().getDisplayMetrics().density;
        test.runOnMainSync(()->{int[] w=new int[2],s=new int[2];view.getLocationOnScreen(w);owner.getLocationOnScreen(s);owner.scrollTo(0,owner.getScrollY()+Math.round(w[1]+(float)rect.optDouble("y")*density-s[1]-80*density));});SystemClock.sleep(150);
        int[] at=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(at));return new float[]{at[0]+(float)rect.getDouble("x")*density,at[1]+(float)rect.getDouble("y")*density};
    }
    private void swipeUp(){
        float width=screen.getResources().getDisplayMetrics().widthPixels,height=screen.getResources().getDisplayMetrics().heightPixels;long now=SystemClock.uptimeMillis();
        for(int i=0;i<=12;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,width*.62f,height*(.8f-.4f*i/12),0);try{test.sendPointerSync(event);}finally{event.recycle();}SystemClock.sleep(35);}SystemClock.sleep(250);
    }
    private void exports()throws Exception{
        ByteArrayOutputStream single=new ByteArrayOutputStream();ClassroomExport.write(dir,summary,single);Map<String,String> one=entries(single.toByteArray());
        check(one.keySet().containsAll(List.of("课堂总结.md","sources.md","更正文稿.md","images/image-0.jpg")),"single export contains notes, original sources, corrected transcript and image");
        check(one.get("更正文稿.md").contains("普通第一段\n\n普通第二段")&&one.get("更正文稿.md").contains("String marker = \"\\n \\n\";"),"export shares paragraph and code preservation behavior");
        check(one.get("sources.md").contains("请保留题目里的限制条件"),"original transcript remains a separate source artifact");
        check(!one.get("课堂总结.md").contains("## 课堂安排"),"export omits an empty assignment section");
        ByteArrayOutputStream draft=new ByteArrayOutputStream();
        ClassroomExport.write(dir,new JSONObject(summary.toString()).put("complete",false).put("overview","").put("parts",new JSONArray()).put("images",new JSONArray()),draft);
        Map<String,String> provisional=entries(draft.toByteArray());
        check(provisional.containsKey("更正文稿.md")&&provisional.get("课堂总结.md").contains("**未完成**")&&!provisional.get("课堂总结.md").contains("## 重点概览")&&!provisional.get("课堂总结.md").contains("## 课堂安排"),"corrected-only draft export has an accurate state and no empty sections");
        ByteArrayOutputStream batch=new ByteArrayOutputStream();ClassroomExport.BatchResult result=ClassroomExport.writeBatch(List.of(new ClassroomExport.LessonFiles(lesson,dir)),ClassroomExport.Layout.FLAT,batch,p->{},l->{if(l!=lesson)throw new AssertionError("Unexpected fixture identity");},file->"jpg");
        Map<String,String> many=entries(batch.toByteArray());check(result.success==1&&result.failures.isEmpty(),"batch uses shared complete export writer");
        String prefix=many.keySet().stream().filter(n->n.endsWith("/更正文稿.md")).findFirst().orElseThrow();prefix=prefix.substring(0,prefix.lastIndexOf('/')+1);
        check(one.get("更正文稿.md").equals(many.get(prefix+"更正文稿.md"))&&one.get("课堂总结.md").equals(many.get(prefix+"课堂总结.md")),"single and batch corrected transcript and note contents agree");
        try(FileOutputStream out=new FileOutputStream(new File(output,"single.zip"))){single.writeTo(out);}try(FileOutputStream out=new FileOutputStream(new File(output,"batch.zip"))){batch.writeTo(out);}
    }
    private void failureReopen()throws Exception{
        var constructor=com.donglan.chrona.ai.ChatCompletionClient.RequestException.class.getDeclaredConstructor(int.class,String.class);constructor.setAccessible(true);
        Exception synthetic=constructor.newInstance(502,"untrusted synthetic response https://untrusted.invalid/?token=synthetic-secret");
        String detail=ClassroomAutoSummary.failureDetail(synthetic,false);
        ClassroomLessonActivity.Session record=new ClassroomLessonActivity.Session(app,account);record.dir=dir;
        try{var save=ClassroomAutoSummary.class.getDeclaredMethod("execution",ClassroomLessonActivity.Session.class,String.class,String.class);save.setAccessible(true);save.invoke(null,record,"failed",detail);owned.add(new File(dir,"run.json"));}
        finally{record.detached=true;record.disposeIfDetached();}
        write("summary.json",new JSONObject(summary.toString()).put("complete",false));
        for(int attempt=0;attempt<2;attempt++){
            test.runOnMainSync(screen::finish);SystemClock.sleep(450);
            screen=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->{try{return detail.contentEquals(((android.widget.TextView)field(screen,"status")).getText());}catch(Exception e){return false;}});
            await(()->{try{document=(ClassroomMarkdownView)field(screen,"documentView");return document!=null&&"true".equals(js("window.classroomReady===true"));}catch(Exception e){return false;}});
            test.waitForIdleSync();SystemClock.sleep(650);
            check(detail.contains("HTTP 502")&&!detail.contains("synthetic-secret"),"native restored failure uses safe actionable reason "+attempt);
            android.graphics.Rect visible=new android.graphics.Rect();View status=(View)field(screen,"status");
            test.runOnMainSync(()->status.getGlobalVisibleRect(visible));
            evidence.put(new JSONObject().put("phase","failure-geometry").put("attempt",attempt).put("viewHeight",status.getHeight()).put("visibleHeight",visible.height()).put("bounds",visible.toShortString()));
            check(status.getHeight()>0&&visible.height()>=status.getHeight(),"restored failure text is fully visible "+attempt);capture("failure-reopen-"+attempt);
        }
        check(new File(dir,"summary.json").delete(),"owned fixture can represent a failed correction before the first note draft");
        test.runOnMainSync(screen::finish);SystemClock.sleep(450);
        screen=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->{try{return detail.contentEquals(((android.widget.TextView)field(screen,"status")).getText());}catch(Exception e){return false;}});
        test.waitForIdleSync();SystemClock.sleep(650);
        check("继续整理".contentEquals(((android.widget.TextView)field(screen,"generate")).getText()),"failed correction checkpoint says continue even before a note draft exists");capture("failure-before-summary");
    }
    private void openRecovery()throws Exception{
        anchor=open(new Intent(app,SettingsHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(600);
        screen=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->{try{return (Boolean)field(field(screen,"s"),"localInitialized");}catch(Exception e){return false;}});
    }
    private Activity open(Intent intent){
        java.util.concurrent.atomic.AtomicBoolean delivered=new java.util.concurrent.atomic.AtomicBoolean();
        Thread watchdog=new Thread(()->{try{Thread.sleep(3000);if(delivered.get())return;StringBuilder traces=new StringBuilder();
            for(var item:Thread.getAllStackTraces().entrySet()){traces.append(item.getKey().getName()).append(' ').append(item.getKey().getState()).append('\n');for(StackTraceElement frame:item.getValue())traces.append("  ").append(frame).append('\n');}
            try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(output,"startup-"+intent.getComponent().getShortClassName()+".txt")),StandardCharsets.UTF_8)){out.write(traces.toString());}
        }catch(Exception ignored){}} ,"enhanced-startup-watchdog");watchdog.setDaemon(true);watchdog.start();
        try{return test.startActivitySync(intent);}finally{delivered.set(true);}
    }
    private static Map<String,String> entries(byte[] data)throws Exception{Map<String,String> result=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data),StandardCharsets.UTF_8)){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null)result.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}return result;}
    private String js(String script)throws Exception{return js(document,script);}
    private String js(ClassroomMarkdownView view,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={null};test.runOnMainSync(()->view.evaluateJavascript(script,r->{value[0]=r;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("JavaScript callback timeout");return value[0];}
    private void write(String name,JSONObject value)throws Exception{File file=new File(dir,name);ClassroomStore.write(file,value);if(!owned.contains(file))owned.add(file);}
    private void check(boolean ok,String description)throws Exception{checks++;evidence.put(new JSONObject().put("case",description).put("passed",ok));if(!ok)throw new AssertionError(description);}
    private void await(BooleanSupplier condition){long end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){if(condition.getAsBoolean())return;SystemClock.sleep(100);}throw new AssertionError("Timed out waiting for enhanced document state");}
    private void capture(String name)throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();if(raw==null)return;float scale=Math.min(1,1400f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true);try(FileOutputStream out=new FileOutputStream(new File(output,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,73,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static void invoke(Object object,String name)throws Exception{java.lang.reflect.Method m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(object);}
}
