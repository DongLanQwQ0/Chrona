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
import android.widget.FrameLayout;
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
    private Activity screen,anchor;
    private ClassroomLesson lesson;
    private ClassroomMarkdownView document;
    private File dir;
    private JSONObject summary;
    private int checks;

    private EnhancedDocumentChecks(ClassroomMonitorInstrumentation test,Bundle args){
        this.test=test;this.args=args;app=test.getTargetContext();account=new ZjuSettingsStore(app).account();
        String suffix=switch(args.getString("case","")){case "failure-recovery"->"-failure-recovery";case "background-ready"->"-background-ready";default->"";};
        output=new File(app.getExternalFilesDir(null),"enhanced-document-"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+suffix);output.mkdirs();
    }
    static int run(ClassroomMonitorInstrumentation test,Bundle args)throws Exception{
        if(!test.getTargetContext().getPackageName().equals("com.donglan.chrona.monitor.validation")||!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned emulator fixture required");
        EnhancedDocumentChecks c=new EnhancedDocumentChecks(test,args);
        try{c.seed();if("background-ready".equals(args.getString("case"))){c.backgroundReady();return c.checks;}if("failure-recovery".equals(args.getString("case")))c.openRecovery();else{c.reader();c.exports();}c.failureReopen();return c.checks;}
        catch(Throwable failure){c.capture("failure");throw failure;}
        finally{
            ClassroomStore.write(new File(c.output,"report.json"),new JSONObject().put("checks",c.checks).put("evidence",c.evidence).put("synthetic",true).put("paid_calls",0));
            if(c.screen!=null)test.runOnMainSync(c.screen::finish);
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
        await(()->{try{document=(ClassroomMarkdownView)field(screen,"documentView");return document!=null&&"true".equals(js("window.classroomReady===true"));}catch(Exception e){return false;}});
        SystemClock.sleep(650);JSONObject closed=state();
        check(closed.getBoolean("nativeToggle")&&!closed.getBoolean("open")&&!closed.getBoolean("loaded"),"native corrected transcript initially collapsed and lazily uncreated");
        check(closed.getInt("errors")==0&&closed.getInt("math")==0,"closed corrected transcript defers formula layout without blocking notes");
        check(!closed.getBoolean("systemDisclosure")&&"true".equals(js("!document.querySelector('main').innerText.includes('普通第一段')&&!document.querySelector('[data-formula]')")),"main document contains neither system details nor corrected transcript HTML");
        Button toggle=correctionToggle();double disclosure=toggle.getHeight(),minimum=toggle.getMinimumHeight();
        float density=app.getResources().getDisplayMetrics().density;
        evidence.put(new JSONObject().put("phase","disclosure-hit-area").put("nativeHeight",disclosure).put("nativeMinimum",minimum).put("requiredPhysicalPixels",Math.round(48*density)));
        check(minimum>=Math.round(48*density)&&disclosure>=Math.round(48*density),"native disclosure has the shared minimum 48dp touch area in physical layout pixels");
        int before=sectionHeight(),mainBefore=document.getHeight();capture("collapsed");tapSummary();
        await(()->{try{return state().getBoolean("open")&&state().getBoolean("ready")&&state().getInt("math")>=2&&sectionHeight()>before+200&&correctionHolder().getAlpha()==1f;}catch(Exception e){return false;}});
        ClassroomMarkdownView firstCorrection=correction();
        JSONObject opened=state();check(opened.getInt("errors")==0,"expanded transcript keeps valid formula rendering");
        check(opened.getBoolean("paragraphs")&&opened.getBoolean("codeLiteral"),"paragraph recovery preserves escaped code literals");
        check(opened.getBoolean("rawIds"),"native corrected reader preserves raw source identifiers");
        check(!opened.getBoolean("systemDisclosure")&&document.getHeight()==mainBefore,"opening native transcript never inserts system details or changes the main WebView height");
        check(opened.getDouble("overflow")<=2,"ordinary portrait or landscape content stays within its viewport");
        capture("expanded");int expanded=sectionHeight();
        ScrollView scroll=scrollOwner();int prior=scroll.getScrollY();swipeUp();
        await(()->scroll.getScrollY()>prior);check(true,"real upward reading gesture scrolls the content and header");
        position(firstCorrection,"pre");capture("expanded-code");
        check("true".equals(js(firstCorrection,"document.querySelector('pre').textContent.includes('String ticks')")),"expanded code remains complete");
        position(firstCorrection,"main > p:last-child");capture("expanded-end");
        check("true".equals(js(firstCorrection,"document.querySelector('main').lastElementChild.textContent.includes('完整上下文仍按原始顺序保留')")),"complete corrected transcript ending is reachable");
        tapSummary();await(()->{try{return !state().getBoolean("open")&&sectionHeight()<expanded-200&&correctionHolder().getVisibility()==View.GONE;}catch(Exception e){return false;}});
        check(sectionHeight()<=before+3&&correctionHolder().getVisibility()==View.GONE,"collapse restores natural document height without a blank reserved region");capture("collapsed-again");
        float[] rapidPoint=position(correctionToggle());
        for(int i=0;i<3;i++){tap(rapidPoint,20);evidence.put(new JSONObject().put("phase","rapid-toggle").put("step",i).put("height",correctionHolder().getHeight()).put("alpha",correctionHolder().getAlpha()));}
        await(()->{try{return state().getBoolean("open")&&correctionHolder().getVisibility()==View.VISIBLE&&sectionHeight()>before+200&&correctionHolder().getAlpha()==1f;}catch(Exception e){return false;}});
        check(correction()==firstCorrection&&correctionHolder().getChildCount()==1,"rapid open/close/reopen reuses one ready transcript view without duplicate children");
        check(state().getInt("errors")==0&&!state().getBoolean("systemDisclosure"),"rapid reversal keeps the rendered formula document and native-only disclosure");capture("rapid-expanded");
        rotationRecovery();
        JSONObject partial=new JSONObject(summary.toString()).put("complete",false).put("overview","").put("parts",new JSONArray());
        write("summary.json",partial);
        Object session=field(screen,"s");test.runOnMainSync(()->{try{set(session,"summary",partial);invoke(screen,"updateDetail");}catch(Exception e){throw new RuntimeException(e);}});
        await(()->{try{document=(ClassroomMarkdownView)field(screen,"documentView");return document!=null&&"true".equals(js("window.classroomReady===true"));}catch(Exception e){return false;}});
        SystemClock.sleep(400);capture("corrected-only-draft");
        check(correctionToggle().getVisibility()==View.VISIBLE&&!state().getBoolean("open")&&!state().getBoolean("loaded"),"corrected-only checkpoint exposes the collapsed native disclosure before note generation");
        check("true".equals(js("![...document.querySelectorAll('main>h2')].some(n=>n.textContent==='重点概览')&&!document.querySelector('main').innerText.includes('等待图片分析')")),"corrected-only draft has no empty overview or invented image phase");
        tapSummary();await(()->{try{return state().getBoolean("ready")&&state().getInt("math")>=2;}catch(Exception e){return false;}});
        check(state().getBoolean("paragraphs")&&state().getBoolean("codeLiteral")&&state().getBoolean("rawIds")&&!state().getBoolean("systemDisclosure"),"corrected-only native expansion retains complete paragraphs, code and source IDs");capture("corrected-only-draft-expanded");
        evidence.put(new JSONObject().put("phase","native-layout").put("screenWidth",screen.getResources().getDisplayMetrics().widthPixels).put("screenHeight",screen.getResources().getDisplayMetrics().heightPixels).put("collapsedHeight",before).put("expandedHeight",expanded).put("closed",closed).put("opened",opened));
        write("summary.json",summary);
    }
    private void backgroundReady()throws Exception{
        JSONArray parts=new JSONArray();JSONObject original=summary.getJSONArray("parts").getJSONObject(0);
        for(int i=0;i<5;i++){JSONObject part=new JSONObject(original.toString());part.getJSONArray("notes").getJSONObject(0).put("text",original.getJSONArray("notes").getJSONObject(0).getString("text")+"\n\n代码与题干条件，逐项保留限制和步骤。\n\n".repeat(40));parts.put(part);}
        summary.put("parts",parts).put("corrected_transcript",summary.getString("corrected_transcript").repeat(10)+"\n\n$x_i=i+1$ ".repeat(4631));write("summary.json",summary);
        screen=open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).putExtra("hold_markdown_ready",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->{try{document=(ClassroomMarkdownView)field(screen,"documentView");return document!=null&&"\"function\"".equals(js("typeof window.chronaReleaseReady"));}catch(Exception e){return false;}});
        check(document.getVisibility()==View.INVISIBLE,"first long document stays hidden until local rendering is ready");
        anchor=open(new Intent(app,SettingsHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check(test.lastResumed==anchor&&anchor!=screen,"another actual Activity covers the reader");
        js("window.chronaReleaseReady()");SystemClock.sleep(1000);test.runOnMainSync(anchor::finish);anchor=null;
        await(()->screen.hasWindowFocus());
        await(()->{try{return "true".equals(js("window.classroomReady===true"))&&document.getVisibility()==View.VISIBLE&&document.getAlpha()==1f;}catch(Exception e){return false;}});
        check(document.getHeight()>1000,"background-ready long document receives its full native height after resume");
        check("true".equals(js("document.querySelectorAll('main>h2').length>=6"))&&correctionToggle().getVisibility()==View.VISIBLE,"all five note blocks and native corrected disclosure are present");
        check(state().getInt("math")==0,"long folded transcript does not eagerly typeset hidden formulas");
        check(!state().getBoolean("loaded")&&!state().getBoolean("systemDisclosure")&&"true".equals(js("document.querySelectorAll('[data-formula]').length===0&&!document.querySelector('main').innerText.includes('普通第一段')")),"long corrected source stays outside the main document and uncreated until native expansion");
        capture("background-resumed");
        evidence.put(new JSONObject().put("phase","background-ready").put("visibility",document.getVisibility()).put("height",document.getHeight()).put("alpha",document.getAlpha()).put("js",state()));
        float[] point=position(correctionToggle());
        check(point[0]>=0&&point[1]>=0&&point[0]<screen.getResources().getDisplayMetrics().widthPixels&&point[1]<screen.getResources().getDisplayMetrics().heightPixels,"actual native transcript disclosure is reachable");
        View decor=screen.getWindow().getDecorView();FrameLayout holder=correctionHolder();
        class ExpansionFrames implements android.view.ViewTreeObserver.OnPreDrawListener {
            final long started=SystemClock.uptimeMillis();
            final JSONArray frames=new JSONArray();
            final CountDownLatch completed=new CountDownLatch(1);
            volatile long firstReady=-1,lastReady=-1;
            volatile boolean endFrameSeen;
            volatile String failure="";
            @Override public boolean onPreDraw(){
                if(completed.getCount()==0)return true;
                try{
                    long elapsed=SystemClock.uptimeMillis()-started;
                    boolean ready=(Boolean)field(screen,"correctionReady"),expanded=(Boolean)field(screen,"correctionExpanded");
                    View child=holder.getChildCount()==0?null:holder.getChildAt(0);
                    int layoutHeight=holder.getLayoutParams().height;
                    frames.put(new JSONObject().put("elapsedMs",elapsed).put("holderHeight",holder.getHeight()).put("layoutHeight",layoutHeight)
                            .put("alpha",holder.getAlpha()).put("childHeight",child==null?0:child.getHeight()).put("ready",ready).put("expanded",expanded));
                    if(ready){if(firstReady<0)firstReady=elapsed;lastReady=elapsed;}
                    if(ready&&expanded&&layoutHeight==android.view.ViewGroup.LayoutParams.WRAP_CONTENT&&holder.getAlpha()==1f)endFrameSeen=true;
                    if(firstReady>=0&&elapsed-firstReady>=400||elapsed>=8000)completed.countDown();
                    // Request observation frames even after the section settles; this changes
                    // no layout, timing, MathJax promise or production rendering callback.
                    else decor.postInvalidateOnAnimation();
                }catch(Exception error){failure=error.toString();completed.countDown();}
                return true;
            }
        }
        ExpansionFrames sampling=new ExpansionFrames();long tapStarted=-1;
        test.runOnMainSync(()->{decor.getViewTreeObserver().addOnPreDrawListener(sampling);decor.postInvalidateOnAnimation();});
        try{
            tapStarted=SystemClock.uptimeMillis()-sampling.started;tap(point,200);
            await(()->{try{return state().getBoolean("ready")&&correctionHolder().getHeight()>1;}catch(Exception e){return false;}});
            JSONObject firstScreen=state();
            check(firstScreen.getInt("formulas")==4651,"all 4651 deferred formulas preserve complete source in the dedicated corrected view");
            check(firstScreen.getInt("math")>=2&&firstScreen.getInt("math")<4651,"corrected first screen is ready before the offscreen formula queue completes");
            check(firstScreen.getInt("errors")==0&&firstScreen.getBoolean("paragraphs")&&firstScreen.getBoolean("codeLiteral")&&firstScreen.getBoolean("rawIds"),"long native transcript retains valid formulas, complete paragraphs, code and raw source IDs");
            check(correction().getWidth()>0&&correctionHolder().getHeight()>1,"lazy transcript obtains real width and expands beyond its one-pixel initialization host");
            capture("background-transcript-first-screen");evidence.put(new JSONObject().put("phase","transcript-first-screen-ready").put("js",firstScreen));
            long remaining=Math.max(0,8000-(SystemClock.uptimeMillis()-sampling.started));sampling.completed.await(remaining,TimeUnit.MILLISECONDS);
            check(sampling.failure.isEmpty()&&sampling.firstReady>=0&&sampling.lastReady-sampling.firstReady>=400&&sampling.endFrameSeen,"actual first expansion frames include the settled WRAP_CONTENT frame and at least 400ms after first ready");
            capture("background-transcript-expanded-frames");
        }finally{
            test.runOnMainSync(()->{if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnPreDrawListener(sampling);});
            evidence.put(new JSONObject().put("phase","transcript-first-expansion-frames").put("clock","uptime milliseconds since listener creation").put("tapStartedMs",tapStarted)
                    .put("firstReadyMs",sampling.firstReady).put("lastReadyMs",sampling.lastReady).put("settledFrameSeen",sampling.endFrameSeen)
                    .put("samplingError",sampling.failure).put("frames",sampling.frames));
        }
        String scale=android.provider.Settings.Global.getString(app.getContentResolver(),"animator_duration_scale");
        try{
            test.getUiAutomation().executeShellCommand("settings put global animator_duration_scale 0").close();await(()->!UiMotion.isEnabled());
            ClassroomMarkdownView loaded=correction();
            tapSummary();await(()->{try{return !(Boolean)field(screen,"correctionExpanded");}catch(Exception error){return false;}});
            check(correctionHolder().getVisibility()==View.GONE&&correctionHolder().getAlpha()==1f,"system-disabled animation collapses native transcript directly without reserved height after the real click is delivered");
            tapSummary();await(()->{try{return (Boolean)field(screen,"correctionExpanded");}catch(Exception error){return false;}});
            check(correction()==loaded&&correctionHolder().getVisibility()==View.VISIBLE&&correctionHolder().getAlpha()==1f&&correctionHolder().getLayoutParams().height==-2,"system-disabled animation reopens the same transcript at its natural final height after the real click is delivered");
            capture("background-transcript-reduced");
        }finally{
            test.getUiAutomation().executeShellCommand(scale==null?"settings delete global animator_duration_scale":"settings put global animator_duration_scale "+scale).close();
            boolean enabled=scale==null||Float.parseFloat(scale)!=0;await(()->UiMotion.isEnabled()==enabled);
        }
    }
    private JSONObject state()throws Exception{
        JSONObject main=json(js("JSON.stringify({systemDisclosure:!!document.querySelector('details,summary'),errors:document.querySelectorAll('.formula-error').length,overflow:document.documentElement.scrollWidth-innerWidth})"));
        ClassroomMarkdownView corrected=correction();
        JSONObject value=corrected==null?new JSONObject().put("systemDisclosure",false).put("math",0).put("formulas",0).put("errors",0).put("paragraphs",false).put("codeLiteral",false).put("rawIds",false).put("overflow",0).put("height",0)
                :json(js(corrected,"JSON.stringify({systemDisclosure:!!document.querySelector('details,summary'),math:document.querySelectorAll('mjx-container').length,formulas:document.querySelectorAll('[data-formula]').length,errors:document.querySelectorAll('.formula-error').length,paragraphs:[...document.querySelectorAll('p')].some(p=>p.textContent==='普通第一段')&&[...document.querySelectorAll('p')].some(p=>p.textContent==='普通第二段'),codeLiteral:!!document.querySelector('code')?.textContent.includes('\\\\n \\\\n'),rawIds:document.querySelector('main').innerText.includes('[id:99]'),overflow:document.documentElement.scrollWidth-innerWidth,height:document.querySelector('main').getBoundingClientRect().height})"));
        return value.put("systemDisclosure",main.getBoolean("systemDisclosure")||value.getBoolean("systemDisclosure"))
                .put("errors",main.getInt("errors")+value.getInt("errors"))
                .put("overflow",Math.max(main.getDouble("overflow"),value.getDouble("overflow")))
                .put("nativeToggle",correctionToggle()!=null&&correctionToggle().getVisibility()==View.VISIBLE)
                .put("open",(Boolean)field(screen,"correctionExpanded")).put("ready",(Boolean)field(screen,"correctionReady")).put("loaded",corrected!=null);
    }
    private static JSONObject json(String value)throws Exception{return new JSONObject(new JSONArray("["+value+"]").getString(0));}
    private ClassroomMarkdownView correction()throws Exception{return (ClassroomMarkdownView)field(screen,"correctionView");}
    private FrameLayout correctionHolder()throws Exception{return (FrameLayout)field(screen,"correctionHolder");}
    private Button correctionToggle()throws Exception{return (Button)field(screen,"correctionToggle");}
    private int sectionHeight()throws Exception{return ((View)field(screen,"notes")).getHeight();}
    private void rotationRecovery()throws Exception{
        String initial=args.getString("orientation","portrait"),opposite="landscape".equals(initial)?"portrait":"landscape";
        ClassroomMarkdownView prior=correction();Object session=field(screen,"s");
        try{
            rotateTo(opposite);
            await(()->{try{return state().getBoolean("ready")&&state().getInt("math")>=2&&correctionHolder().getHeight()>200;}catch(Exception e){return false;}});
            JSONObject restored=state();
            check(restored.getBoolean("open")&&correction()!=prior&&field(screen,"s")==session,"actual orientation change restores expanded state with one fresh view and retained session");
            check(restored.getBoolean("paragraphs")&&restored.getBoolean("codeLiteral")&&restored.getBoolean("rawIds")&&restored.getInt("errors")==0&&restored.getDouble("overflow")<=2&&!restored.getBoolean("systemDisclosure"),"rotated native transcript keeps full content, valid formulas and viewport bounds");capture("rotated-expanded");
            tapSummary();await(()->{try{return !state().getBoolean("open")&&correctionHolder().getVisibility()==View.GONE;}catch(Exception e){return false;}});
            rotateTo(initial);
            check(!state().getBoolean("open")&&!state().getBoolean("loaded")&&correctionHolder().getVisibility()==View.GONE,"actual rotation back restores collapsed state without eagerly recreating the transcript");
            check(!state().getBoolean("systemDisclosure"),"both actual rotated document instances remain free of system details");capture("rotated-back-collapsed");
        }finally{
            if(!initial.equals(args.getString("orientation"))){
                args.putString("orientation",initial);
                Activity current=test.lastResumed instanceof ClassroomLessonActivity?test.lastResumed:screen;
                test.runOnMainSync(()->current.setRequestedOrientation("landscape".equals(initial)?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                test.getUiAutomation().setRotation("landscape".equals(initial)?android.app.UiAutomation.ROTATION_FREEZE_90:android.app.UiAutomation.ROTATION_FREEZE_0);
            }
        }
    }
    private void rotateTo(String orientation)throws Exception{
        Activity previous=screen;int expected="landscape".equals(orientation)?android.content.res.Configuration.ORIENTATION_LANDSCAPE:android.content.res.Configuration.ORIENTATION_PORTRAIT;
        args.putString("orientation",orientation);
        test.runOnMainSync(()->previous.setRequestedOrientation(expected==android.content.res.Configuration.ORIENTATION_LANDSCAPE?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        test.getUiAutomation().setRotation(expected==android.content.res.Configuration.ORIENTATION_LANDSCAPE?android.app.UiAutomation.ROTATION_FREEZE_90:android.app.UiAutomation.ROTATION_FREEZE_0);
        await(()->test.lastResumed instanceof ClassroomLessonActivity&&test.lastResumed!=previous&&test.lastResumed.getResources().getConfiguration().orientation==expected);
        screen=test.lastResumed;
        await(()->{try{document=(ClassroomMarkdownView)field(screen,"documentView");return document!=null&&"true".equals(js("window.classroomReady===true"));}catch(Exception e){return false;}});
        evidence.put(new JSONObject().put("phase","real-rotation").put("orientation",orientation).put("configuration",screen.getResources().getConfiguration().orientation).put("newActivity",screen!=previous));
    }
    private void tapSummary()throws Exception{
        float[] point=position(correctionToggle());
        check(point[0]>=0&&point[1]>=0&&point[0]<screen.getResources().getDisplayMetrics().widthPixels&&point[1]<screen.getResources().getDisplayMetrics().heightPixels,"actual native transcript disclosure is reachable");
        tap(point,200);
    }
    private void tap(float[] point,int settleMillis){
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0),up=MotionEvent.obtain(now,now+35,MotionEvent.ACTION_UP,point[0],point[1],0);
        try{test.sendPointerSync(down);SystemClock.sleep(35);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}SystemClock.sleep(settleMillis);
    }
    private ScrollView scrollOwner(){return scrollOwner(document);}
    private ScrollView scrollOwner(View child){for(ViewParent parent=child.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView value)return value;throw new AssertionError("Missing shared page scroll viewport");}
    private float[] position(View view){
        ScrollView owner=scrollOwner(view);float density=view.getResources().getDisplayMetrics().density;
        test.runOnMainSync(()->{int[] at=new int[2],s=new int[2];view.getLocationOnScreen(at);owner.getLocationOnScreen(s);owner.scrollTo(0,owner.getScrollY()+Math.round(at[1]+view.getHeight()/2f-s[1]-80*density));});SystemClock.sleep(200);
        float[] point=new float[2];test.runOnMainSync(()->{android.graphics.Rect visible=new android.graphics.Rect();if(!view.getGlobalVisibleRect(visible))throw new AssertionError("Native disclosure is not visible");point[0]=visible.exactCenterX();point[1]=visible.exactCenterY();});return point;
    }
    private float[] position(ClassroomMarkdownView view,String selector)throws Exception{
        JSONObject rect=json(js(view,"JSON.stringify((()=>{let r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})())"));
        ScrollView owner=scrollOwner(view);float density=view.getResources().getDisplayMetrics().density;
        test.runOnMainSync(()->{int[] w=new int[2],s=new int[2];view.getLocationOnScreen(w);owner.getLocationOnScreen(s);owner.scrollTo(0,owner.getScrollY()+Math.round(w[1]+(float)rect.optDouble("y")*density-s[1]-80*density));});SystemClock.sleep(200);
        int[] at=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(at));float x=at[0]+(float)rect.getDouble("x")*density,y=at[1]+(float)rect.getDouble("y")*density;
        return new float[]{x,y};
    }
    private void swipeUp(){
        float width=app.getResources().getDisplayMetrics().widthPixels,height=app.getResources().getDisplayMetrics().heightPixels;long now=SystemClock.uptimeMillis();
        for(int i=0;i<=12;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,width*.62f,height*(.8f-.4f*i/12),0);try{test.sendPointerSync(event);}finally{event.recycle();}SystemClock.sleep(35);}
        SystemClock.sleep(250);
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
