package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import com.donglan.chrona.data.*;
import com.donglan.chrona.zju.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** Real normal-screen navigation; private fixture records only, no AI/school provider calls. */
final class PageMotionChecks {
    private final ClassroomMonitorInstrumentation test;private final Bundle args;private final Context app;
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    private Activity screen,anchor;private long task,candidate;private int checks;
    private com.donglan.chrona.classroom.ClassroomLesson motionLesson;
    private final JSONArray report=new JSONArray();
    private PageMotionChecks(ClassroomMonitorInstrumentation t,Bundle a){test=t;args=a;app=t.getTargetContext();}
    static int run(ClassroomMonitorInstrumentation t,Bundle args)throws Exception{
        PageMotionChecks c=new PageMotionChecks(t,args);
        if(!c.app.getPackageName().equals("com.donglan.chrona.monitor.validation")||!Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned AVD only");
        ZjuTimetableStore tables=new ZjuTimetableStore(c.app);JSONObject previous=tables.snapshot();
        File examFile=new File(c.app.getCacheDir(),"zju-exams.json");byte[] previousExams=examFile.isFile()?java.nio.file.Files.readAllBytes(examFile.toPath()):null;
        try{c.seed(tables);c.bootstrap();if("markdown".equals(args.getString("scope"))){c.classroomDocument();return c.checks;}c.pages();c.slowDetails();c.classroomDocument();c.timetable();c.interruption();c.returnNavigation();c.reduced();c.supplementary();return c.checks;}
        catch(Throwable failure){c.capture("failure");throw failure;}
        finally{
            t.runOnMainSync(()->{if(c.screen!=null)c.screen.finish();if(c.anchor!=null)c.anchor.finish();});
            try(TaskStore store=new TaskStore(c.app)){if(c.task>0)store.deleteTask(c.task);}
            new com.donglan.chrona.processing.StreamingOutputStore(c.app).delete(c.task);tables.restore(previous);
            if(c.motionLesson!=null)new com.donglan.chrona.classroom.ClassroomStore(c.app,"page-motion").deleteLesson(c.motionLesson,()->{});
            if(previousExams==null)new ZjuExamStore(c.app).clear();else java.nio.file.Files.write(examFile.toPath(),previousExams);
            for(var entry:c.saved.entrySet())restore(c.app.getSharedPreferences(entry.getKey(),0),entry.getValue());
            java.nio.file.Files.write(new File(c.app.getExternalFilesDir(null),"page-motion-"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+".json").toPath(),c.report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private void seed(ZjuTimetableStore tables)throws Exception{
        for(String name:List.of("chrona_zju","appearance","usage_guide","release_updates","zju_calendar"))saved.put(name,new HashMap<>(app.getSharedPreferences(name,0).getAll()));
        app.getSharedPreferences("chrona_zju",0).edit().clear().putString("account","page-motion").putString("credentials","invalid-fixture").putBoolean("enabled",true).commit();
        app.getSharedPreferences("appearance",0).edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();
        app.getSharedPreferences("release_updates",0).edit().putBoolean("automatic",false).commit();
        long now=System.currentTimeMillis();
        try(TaskStore store=new TaskStore(app)){task=store.insertTask("动效验收原文",null,"motion-fixture",now);candidate=store.appendCandidate(new EventCandidate(0,task,"重复动效验收",now+3600000,now+7200000,"Asia/Shanghai","验收教室","",null,false));store.updateStatus(task,TaskRecord.READY,null);}
        try(var writer=new com.donglan.chrona.processing.StreamingOutputStore(app).begin(task)){writer.append("模型输出分页动效验收。\n".repeat(1800));}
        long start=LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(10,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        tables.save("page-motion",List.of(new ZjuSnapshot.Lesson("a","当前学期验收课程","验收教室","秋学期",start,start+3600000,""),new ZjuSnapshot.Lesson("b","其他学期验收课程","另一教室","冬学期",start+90*86400000L,start+90*86400000L+3600000,"")),List.of("秋学期","冬学期"));
    }
    private void pages()throws Exception{
        for(Class<? extends Activity> type:List.of(SettingsHubActivity.class,AppearanceActivity.class,BackupRestoreActivity.class,GuideActivity.class,UpdateActivity.class,ScheduleCompareActivity.class,ModelOutputActivity.class,RepeatScheduleActivity.class,ExternalCourseDetailActivity.class,SettingsActivity.class,SyncSettingsActivity.class,ScheduleSourceSettingsActivity.class,ZjuHomeworkHistoryActivity.class,ZjuCalendarSettingsActivity.class,SystemAgendaDetailActivity.class,CandidateMergeActivity.class)){
            Intent intent=new Intent(app,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("task_id",task).putExtra("candidate_id",candidate);
            if(type==ExternalCourseDetailActivity.class)intent=ExternalCourseDetailActivity.sourceIntent(app,"zju","a",System.currentTimeMillis()-86400000L,System.currentTimeMillis()+86400000L).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(type==SystemAgendaDetailActivity.class)intent=SystemAgendaDetailActivity.intent(app,990001L,System.currentTimeMillis()+3600000,System.currentTimeMillis()+7200000,false,"系统日程动效验收","验收教室","原日历");
            Activity beforeOpen=test.lastResumed;Intent opening=intent.setFlags(0);test.runOnMainSync(()->anchor.startActivity(opening));
            await(()->test.lastResumed!=beforeOpen&&test.lastResumed.getClass()==type);screen=test.lastResumed;String name=type.getSimpleName();
            await(()->test.entranceFrames.getOrDefault(screen,0)>0);check(true,name+" has visible initial content entrance");capture(name+"-entered");SystemClock.sleep(950);if(test.lastResumed.getClass()==type)screen=test.lastResumed;
            android.util.TypedValue style=new android.util.TypedValue();screen.getTheme().resolveAttribute(android.R.attr.windowAnimationStyle,style,true);
            var animation=screen.obtainStyledAttributes(style.resourceId,new int[]{android.R.attr.activityCloseEnterAnimation,android.R.attr.activityCloseExitAnimation});
            check(animation.getResourceId(0,0)!=0&&animation.getResourceId(1,0)!=0,name+" retains Android return enter/exit transitions");animation.recycle();
            if(type==ModelOutputActivity.class){
                await(()->((View)get(screen,"next")).isEnabled());tap("下一页");await(()->((Integer)get(screen,"shownIndex"))==1);capture("output-switch");SystemClock.sleep(300);tap("上一页");await(()->((Integer)get(screen,"shownIndex"))==0);check(true,"model output directional next and previous complete");
            }
            SystemClock.sleep(350);capture(name+"-settled");
            test.runOnMainSync(screen::recreate);await(()->test.lastResumed!=screen&&test.lastResumed.getClass()==type);screen=test.lastResumed;SystemClock.sleep(950);if(test.lastResumed.getClass()==type)screen=test.lastResumed;
            int before=test.entryFrameCounts.getOrDefault(name,0);SystemClock.sleep(250);
            check(test.entranceFrames.getOrDefault(screen,0)==0&&!hasEntrance(screen.getWindow().getDecorView()),name+" restored content does not replay entrance");
            test.runOnMainSync(screen::finish);SystemClock.sleep(550);check(screen.isFinishing(),name+" exits without delaying finish or changing return semantics");
            report.put(new JSONObject().put("page",name).put("entryFrames",before).put("restore","settled").put("exit","Android window transition"));
        }
    }
    private void bootstrap()throws Exception{
        // Keep a normal parent window live: returning to the portrait-only launcher between
        // landscape cases would force an unrelated configuration recreation before first draw.
        screen=test.startActivitySync(new Intent(app,SettingsHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(700);
        // Launcher is NOSENSOR: rotate only after an ordinary application window owns the display.
        test.getUiAutomation().setRotation("landscape".equals(args.getString("orientation"))?UiAutomation.ROTATION_FREEZE_90:UiAutomation.ROTATION_FREEZE_0);
        SystemClock.sleep(1000);anchor=test.lastResumed;screen=anchor;
        check(screen.getResources().getConfiguration().orientation==("landscape".equals(args.getString("orientation"))?2:1),"normal display orientation matches the requested acceptance case");
    }
    private void slowDetails()throws Exception{
        ZjuExam exam=new ZjuExam("motion","微积分动效验收","期中","2026年11月07日(14:00-16:00)","考场A","12");
        new ZjuExamStore(app).save("page-motion",List.of(exam));
        for(int mode=0;mode<5;mode++){
            Object lock=staticField(mode==0||mode>=3?ZjuTimetableStore.class:ZjuExamStore.class,"LOCK");int before;
            synchronized(lock){
                Intent intent=mode==0?ExternalCourseDetailActivity.sourceIntent(app,"zju","a",0,Long.MAX_VALUE):mode==1?new Intent(app,ZjuExamsActivity.class):mode==2?new Intent(app,ExternalCourseDetailActivity.class).putExtra("source","exam").putExtra("event_id",ScheduleListing.projection("page-motion",exam).id):mode==3?new Intent(app,CourseSourceMatchingActivity.class):ScheduleSourceContentsActivity.intent(app,"zju","教务来源动效验收");
                open(intent);SystemClock.sleep(700);currentScreen();before=test.entranceFrames.getOrDefault(screen,0);
                check(node(mode==0||mode==4?"当前学期验收课程":mode==3?"手动关联课程名字":"微积分动效验收")==null,"fixture holds actual cache reader until loading entrance ends "+mode);
            }
            await(()->{currentScreen();return test.entranceFrames.getOrDefault(screen,0)>before;});check(true,"actual late content has its own first entrance "+mode);capture("late-content-"+mode);SystemClock.sleep(400);capture("late-content-"+mode+"-settled");
            if(mode==4){tap("切换按周查看");SystemClock.sleep(400);check(((View)get(screen,"rows")).getAlpha()==1f,"source mode switch settles");tap("下周");SystemClock.sleep(400);check(((View)get(screen,"rows")).getTranslationX()==0f,"source week switch settles");}
            test.runOnMainSync(screen::finish);SystemClock.sleep(500);
        }
    }
    private void returnNavigation()throws Exception{
        open(new Intent(app,SettingsHubActivity.class));SystemClock.sleep(700);tap("外观与配色");await(()->test.lastResumed instanceof AppearanceActivity);screen=test.lastResumed;SystemClock.sleep(700);
        tap("返回");capture("android-return");await(()->test.lastResumed instanceof SettingsHubActivity);screen=test.lastResumed;SystemClock.sleep(600);capture("android-return-settled");check(true,"actual header back returns to the existing settings page");test.runOnMainSync(screen::finish);SystemClock.sleep(450);
    }
    private void classroomDocument()throws Exception{
        motionLesson=new com.donglan.chrona.classroom.ClassroomLesson("882777",Long.toUnsignedString(System.nanoTime()),"课堂入场验收","公式和正文","验收教室","6",System.currentTimeMillis()-600000);
        File dir=new com.donglan.chrona.classroom.ClassroomStore(app,"page-motion").localLesson(motionLesson);
        com.donglan.chrona.classroom.ClassroomStore.write(new File(dir,"summary.json"),new JSONObject().put("lesson",motionLesson.json()).put("complete",true).put("overview","## 课堂动效验收\n\n真实本地正文就绪后显示，公式 $x^2+y^2=z^2$。\n\n".repeat(4)).put("parts",new JSONArray()).put("updated",System.currentTimeMillis()));
        open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,motionLesson.json().toString()).putExtra("hold_markdown_ready",true));
        await(()->{currentScreen();Object view=get(screen,"documentView");return view!=null&&"true".equals(js((ClassroomMarkdownView)view,"Boolean(window.chronaReleaseReady&&document.querySelector('main').children.length)"));});
        ClassroomMarkdownView held=(ClassroomMarkdownView)get(screen,"documentView");SystemClock.sleep(700);
        check(!(Boolean)get(held,"ready")&&held.getVisibility()==View.INVISIBLE,"real HTML remains invisible while formula startup is delayed");capture("classroom-document-held");
        js(held,"window.chronaReleaseReady()");
        await(()->{currentScreen();Object view=get(screen,"documentView");return view!=null&&(Boolean)get(view,"ready")&&hasEntrance((View)view);});ClassroomMarkdownView document=(ClassroomMarkdownView)get(screen,"documentView");check(!(Boolean)get(screen,"firstDocumentPresentation"),"actual WebView document readiness consumes only its first entrance");capture("classroom-document-entered");SystemClock.sleep(950);currentScreen();document=(ClassroomMarkdownView)get(screen,"documentView");capture("classroom-document-settled");
        test.runOnMainSync(()->invoke(screen,"updateDetail"));check(get(screen,"documentView")==document,"unchanged classroom update retains the document and does not replay entry");
        check(document.getVisibility()==View.VISIBLE&&document.getAlpha()==1f,"unchanged update never hides the displayed document");
        Activity previous=screen;test.runOnMainSync(previous::recreate);await(()->test.lastResumed!=previous);screen=test.lastResumed;
        await(()->get(screen,"documentView")!=null);ClassroomMarkdownView restored=(ClassroomMarkdownView)get(screen,"documentView");
        check(restored.getVisibility()==View.VISIBLE,"restoration does not hide an already presented document");
        await(()->"true".equals(js(restored,"Boolean(window.chronaReleaseReady)")));js(restored,"window.chronaReleaseReady()");await(()->(Boolean)get(restored,"ready"));SystemClock.sleep(300);
        check(!hasEntrance(restored),"restored document does not replay entrance");
        test.runOnMainSync(screen::finish);SystemClock.sleep(500);
        String scale=android.provider.Settings.Global.getString(app.getContentResolver(),"animator_duration_scale");
        try{
            test.getUiAutomation().executeShellCommand("settings put global animator_duration_scale 0").close();await(()->!UiMotion.isEnabled());
            open(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,motionLesson.json().toString()).putExtra("hold_markdown_ready",true));await(()->get(screen,"documentView")!=null);
            ClassroomMarkdownView reduced=(ClassroomMarkdownView)get(screen,"documentView");await(()->"true".equals(js(reduced,"Boolean(window.chronaReleaseReady)")));
            check(reduced.getVisibility()==View.INVISIBLE,"reduced animation still waits for valid first content");js(reduced,"window.chronaReleaseReady()");await(()->(Boolean)get(reduced,"ready"));
            check(reduced.getVisibility()==View.VISIBLE&&reduced.getAlpha()==1f&&reduced.getTranslationY()==0f,"reduced animation presents ready content at its final state");test.waitForIdleSync();SystemClock.sleep(150);capture("classroom-document-reduced-initial");
            report.put(new JSONObject().put("phase","reduced-native-ready").put("width",reduced.getWidth()).put("height",reduced.getHeight()).put("shown",reduced.isShown()).put("windowFocus",reduced.hasWindowFocus()).put("dom",js(reduced,"JSON.stringify({ready:window.classroomReady,text:document.querySelector('main').innerText.length,height:document.querySelector('main').getBoundingClientRect().height})")));
            SystemClock.sleep(500);capture("classroom-document-reduced");test.runOnMainSync(screen::finish);SystemClock.sleep(350);
        }finally{test.getUiAutomation().executeShellCommand("settings put global animator_duration_scale "+(scale==null?"1":scale)).close();}
    }
    private String js(ClassroomMarkdownView document,String script)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] value={null};
        test.runOnMainSync(()->document.evaluateJavascript(script,result->{value[0]=result;done.countDown();}));
        if(!done.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("JavaScript callback timeout");return value[0];
    }
    private void reduced()throws Exception{
        String scale=android.provider.Settings.Global.getString(app.getContentResolver(),"animator_duration_scale");
        try{
            test.getUiAutomation().executeShellCommand("settings put global animator_duration_scale 0").close();await(()->!UiMotion.isEnabled());
            open(new Intent(app,ModelOutputActivity.class).putExtra("task_id",task));SystemClock.sleep(950);currentScreen();await(()->((View)get(screen,"next")).isEnabled());
            tap("下一页");await(()->((Integer)get(screen,"shownIndex"))==1);View scroll=(View)get(screen,"scroll");
            check(scroll.getAlpha()==1f&&scroll.getTranslationX()==0f&&((android.widget.FrameLayout)scroll.getParent()).getChildCount()==1,"disabled animations switch output directly without a snapshot or residual transform");capture("reduced-output");
            test.runOnMainSync(screen::finish);SystemClock.sleep(450);
        }finally{test.getUiAutomation().executeShellCommand("settings put global animator_duration_scale "+(scale==null?"1":scale)).close();boolean enabled=scale==null||Float.parseFloat(scale)!=0;await(()->UiMotion.isEnabled()==enabled);}
    }
    private void timetable()throws Exception{
        open(new Intent(app,TimetableActivity.class));
        SystemClock.sleep(950);currentScreen();
        TimetablePage page=(TimetablePage)((ScheduleWorkspaceActivity)screen).currentPage();
        await(()->node("当前学期验收课程")!=null);SystemClock.sleep(450);
        View body=(View)get(page,"body");check(body.getParent() instanceof android.widget.FrameLayout,"timetable content has a shared snapshot host");
        tap("导入课表");check(body.getAlpha()<1f||body.getTranslationX()!=0f,"source switch is animated");capture("source-switch");
        SystemClock.sleep(350);tap("浙大课表");SystemClock.sleep(350);await(()->node("当前学期验收课程")!=null);
        test.runOnMainSync(()->invoke(page,"chooseSchoolTerm"));await(()->node("冬学期")!=null);tap("冬学期");await(()->node("其他学期验收课程")!=null);capture("school-term-switch");
        SystemClock.sleep(350);check(body.getAlpha()==1f&&body.getTranslationX()==0f,"school term switch settles at exact identity");
        // Interrupt a second direction with a third, not a delayed click after the motion ended.
        test.runOnMainSync(()->{invoke(page,"selectSource",new Class[]{int.class},1);invoke(page,"selectSource",new Class[]{int.class},0);});
        SystemClock.sleep(350);check(((android.widget.FrameLayout)body.getParent()).getChildCount()==1,"rapid source reversal releases the previous snapshot");
        test.runOnMainSync(screen::finish);SystemClock.sleep(500);
    }
    private void interruption()throws Exception{
        open(new Intent(app,AppearanceActivity.class));
        SystemClock.sleep(950);currentScreen();
        View page=(View)get(screen,"page");test.runOnMainSync(()->{UiMotion.enterPage(page);UiMotion.swap(page,()->{});});SystemClock.sleep(350);
        Map<?,?> pending=(Map<?,?>)staticField(UiMotion.class,"PENDING_ENTRANCES");
        check(!pending.containsKey(page)&&page.getAlpha()==1f&&page.getTranslationX()==0f&&page.getTranslationY()==0f,"switch cancels a still-pending initial entrance");
        test.runOnMainSync(()->UiMotion.finishSwap(page));test.runOnMainSync(screen::finish);SystemClock.sleep(450);
    }
    private void supplementary()throws Exception{
        open(new Intent(app,SyncSettingsActivity.class));SystemClock.sleep(950);currentScreen();
        synchronized(AndroidSync.LOCK){test.runOnMainSync(()->invoke(screen,"loadPending"));SystemClock.sleep(650);check((Boolean)get(screen,"firstPending"),"pending list waits for its first actual result");}
        await(()->!(Boolean)get(screen,"firstPending"));capture("sync-pending-entered");SystemClock.sleep(350);check(((View)get(screen,"pending")).getAlpha()==1f,"late pending result settles");test.runOnMainSync(screen::finish);SystemClock.sleep(450);
        java.util.concurrent.CountDownLatch gate=new java.util.concurrent.CountDownLatch(1),entered=new java.util.concurrent.CountDownLatch(1);
        ZjuSync.WORKER.execute(()->{entered.countDown();try{gate.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});entered.await(2,java.util.concurrent.TimeUnit.SECONDS);
        try{open(new Intent(app,ZjuHomeworkHistoryActivity.class));SystemClock.sleep(950);currentScreen();check((Boolean)get(screen,"firstDataPresentation"),"history loading shell does not consume data entrance");}finally{gate.countDown();}
        await(()->!(Boolean)get(screen,"firstDataPresentation"));capture("history-late-entered");SystemClock.sleep(350);test.runOnMainSync(screen::finish);SystemClock.sleep(450);
        open(new Intent(app,ZjuCalendarSettingsActivity.class));SystemClock.sleep(950);currentScreen();tap("校区 ·");tap("海宁国际校区");tap("载入");capture("calendar-campus-switch");SystemClock.sleep(350);check(ZjuCalendarStore.load(app).campus.equals("haining")&&((View)get(screen,"body")).getAlpha()==1f,"campus switch preserves page and settles");test.runOnMainSync(screen::finish);SystemClock.sleep(450);
        // Only this owned task receives duplicate fixtures, and the scan cannot delete them.
        try(TaskStore store=new TaskStore(app)){long start=System.currentTimeMillis()+86400000L;for(int group=0;group<13;group++)for(int copy=0;copy<2;copy++)store.appendCandidate(new EventCandidate(0,task,"动效验收重复第"+group+"组",start+group*3600000L,start+(group+1)*3600000L,"Asia/Shanghai","验收教室","",null,false));}
        open(new Intent(app,CandidateMergeActivity.class));SystemClock.sleep(950);currentScreen();await(()->get(get(screen,"session"),"preview")!=null);SystemClock.sleep(400);
        View body=(View)get(screen,"body");test.runOnMainSync(()->{android.view.ViewParent p=body.getParent();while(p!=null&&!(p instanceof android.widget.ScrollView))p=p.getParent();if(p instanceof android.widget.ScrollView scroll)scroll.fullScroll(View.FOCUS_DOWN);});SystemClock.sleep(150);tap("下一页");check((Integer)get(get(screen,"session"),"page")==1,"duplicate preview next page uses existing session");capture("merge-page-switch");SystemClock.sleep(350);check(body.getAlpha()==1f&&((android.widget.FrameLayout)body.getParent()).getChildCount()==1,"duplicate preview transition clears its snapshot");
        test.runOnMainSync(screen::finish);SystemClock.sleep(350);
    }
    private void tap(String label)throws Exception{
        test.waitForIdleSync();SystemClock.sleep(450);var n=node(label);if(n==null)throw new AssertionError("Missing "+label);Rect r=new Rect();n.getBoundsInScreen(r);long down=SystemClock.uptimeMillis();
        for(int action:new int[]{0,1}){MotionEvent e=MotionEvent.obtain(down,down+(action==1?60:0),action,r.centerX(),r.centerY(),0);try{test.sendPointerSync(e);}finally{e.recycle();}}
        // Waiting for UI idleness here can consume the entire transition being observed.
        SystemClock.sleep(45);
    }
    private void open(Intent intent)throws Exception{
        Activity previous=test.lastResumed;Class<?> type=Class.forName(intent.getComponent().getClassName());
        test.runOnMainSync(()->anchor.startActivity(intent.setFlags(0)));
        await(()->test.lastResumed!=previous&&test.lastResumed.getClass()==type);screen=test.lastResumed;
    }
    private void currentScreen(){if(test.lastResumed!=null&&screen!=null&&test.lastResumed.getClass()==screen.getClass())screen=test.lastResumed;}
    private AccessibilityNodeInfo node(String text){var root=test.getUiAutomation().getRootInActiveWindow();var exact=findExact(root,text);return exact==null?find(root,text):exact;}
    private static AccessibilityNodeInfo findExact(AccessibilityNodeInfo n,String text){if(n==null)return null;if(text.equals(String.valueOf(n.getText()))||text.equals(String.valueOf(n.getContentDescription())))return n;for(int i=0;i<n.getChildCount();i++){var found=findExact(n.getChild(i),text);if(found!=null)return found;}return null;}
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){if(n==null)return null;if(String.valueOf(n.getText()).contains(text)||String.valueOf(n.getContentDescription()).contains(text))return n;for(int i=0;i<n.getChildCount();i++){var found=find(n.getChild(i),text);if(found!=null)return found;}return null;}
    private static boolean hasEntrance(View v){if(v.getAlpha()>0&&v.getAlpha()<1&&v.getTranslationY()>0)return true;if(v instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++)if(hasEntrance(g.getChildAt(i)))return true;return false;}
    private interface Condition{boolean ok()throws Exception;}
    private void await(Condition c)throws Exception{long end=SystemClock.uptimeMillis()+10000;while(!c.ok()){if(SystemClock.uptimeMillis()>end)throw new AssertionError("UI timeout: "+(screen==null?"none":screen+" destroyed="+screen.isDestroyed()+" frames="+test.entranceFrames.getOrDefault(screen,0))+" resumed="+test.lastResumed+" entries="+test.entryCreates);SystemClock.sleep(20);}}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object staticField(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void invoke(Object o,String name){invoke(o,name,new Class[]{ });}
    private static void invoke(Object o,String name,Class<?>[] signature,Object...values){try{Method m=o.getClass().getDeclaredMethod(name,signature);m.setAccessible(true);m.invoke(o,values);}catch(Exception e){throw new RuntimeException(e);}}
    private static void restore(SharedPreferences p,Map<String,?> values){var e=p.edit().clear();for(var v:values.entrySet()){Object x=v.getValue();if(x instanceof String s)e.putString(v.getKey(),s);else if(x instanceof Boolean b)e.putBoolean(v.getKey(),b);else if(x instanceof Integer i)e.putInt(v.getKey(),i);else if(x instanceof Long l)e.putLong(v.getKey(),l);else if(x instanceof Float f)e.putFloat(v.getKey(),f);else if(x instanceof Set s)e.putStringSet(v.getKey(),s);}e.commit();}
    private void capture(String name)throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();float scale=Math.min(1,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true);File file=new File(app.getExternalFilesDir(null),"page-motion-"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+"-"+name+".jpg");try(FileOutputStream out=new FileOutputStream(file)){small.compress(Bitmap.CompressFormat.JPEG,72,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
}
