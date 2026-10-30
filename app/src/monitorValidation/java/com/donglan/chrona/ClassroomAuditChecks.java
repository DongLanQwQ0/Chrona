package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Local synthetic records only. No school, AI, device pairing or public-directory writes. */
final class ClassroomAuditChecks {
    private final ClassroomMonitorInstrumentation test;private final Bundle args;
    private final File output;private final JSONArray evidence=new JSONArray();
    private int checks;private File dir;private Activity screen,lessonScreen;
    private ClassroomLesson lesson;private String account;private final List<File> owned=new ArrayList<>();
    private ClassroomAuditChecks(ClassroomMonitorInstrumentation test,Bundle args){
        this.test=test;this.args=args;output=new File(test.getTargetContext().getExternalFilesDir(null),
                "classroom-audit-"+args.getString("orientation","portrait")+"-"+args.getString("theme","light"));output.mkdirs();
    }
    static int run(ClassroomMonitorInstrumentation test,Bundle args)throws Exception{
        if(!test.getTargetContext().getPackageName().equals("com.donglan.chrona.monitor.validation")||!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned emulator fixture required");
        ClassroomAuditChecks c=new ClassroomAuditChecks(test,args);
        try{if("unfinished".equals(args.getString("case")))c.unfinished();else{c.projection();c.page();}return c.checks;}
        catch(Throwable failure){c.capture("failure");throw failure;}
        finally{ClassroomStore.write(new File(c.output,"report.json"),new JSONObject().put("checks",c.checks).put("evidence",c.evidence).put("paid_calls",0));
            if(c.screen!=null)test.runOnMainSync(c.screen::finish);if(c.lessonScreen!=null)test.runOnMainSync(c.lessonScreen::finish);
            for(File file:c.owned)file.delete();if(c.dir!=null)c.dir.delete();}
    }
    private JSONObject fixture()throws Exception{
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content","PRIVATE-SYSTEM"))
                .put(new JSONObject().put("role","user").put("content",new JSONArray()
                        .put(new JSONObject().put("type","text").put("text","PRIVATE-PROMPT"))
                        .put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/jpeg;base64,PRIVATE-IMAGE")))));
        JSONObject usage=new JSONObject().put("prompt_tokens",1200).put("completion_tokens",40).put("total_tokens",1240)
                .put("prompt_tokens_details",new JSONObject().put("cached_tokens",900));
        JSONArray turns=new JSONArray();for(int i=0;i<18;i++)turns.put(new JSONObject().put("stage",i==0?"correct-0":i==1?"draft-0-user-retry-1":"final-"+i)
                .put("status",i==1?"failed":"received").put("started",System.currentTimeMillis()-18000+i*500)
                .put("finished",System.currentTimeMillis()-17500+i*500).put("message_count",2).put("usage",i==1?JSONObject.NULL:new JSONObject(usage.toString()))
                .put("response",new JSONObject().put("secret","PRIVATE-RESPONSE")).put("error","PRIVATE-ERROR"));
        JSONObject draft=new JSONObject().put("image_assessment",new JSONObject().put("decision","request").put("reason","核对例题的图示、限制条件与评分细节。"))
                .put("image_requests",new JSONArray().put(new JSONObject().put("start",12).put("end",40).put("reason","核对曲线旁的标签和限制条件。")));
        return new JSONObject().put("run","synthetic-audit").put("config","PRIVATE-CONFIG").put("conversation",new JSONObject().put("messages",messages)
                .put("options",new JSONObject().put("secret","PRIVATE-OPTIONS")).put("turns",turns))
                .put("drafts",new JSONArray().put(draft)).put("image_audit",new JSONObject().put("selected_count",1))
                .put("analysed",new JSONArray().put(new JSONObject().put("file","image-4.jpg")));
    }
    private void projection()throws Exception{
        JSONObject run=fixture();String original=run.toString();JSONObject projection=ClassroomAudit.project(run,null);
        check(original.equals(run.toString()),"projection never mutates saved source");
        check(!projection.toString().contains("PRIVATE-"),"whitelist excludes original messages, response, image bytes, options and errors");
        JSONObject first=projection.getJSONArray("turns").getJSONObject(0),failed=projection.getJSONArray("turns").getJSONObject(1);
        check(first.getLong("input")==1200&&first.getLong("output")==40&&first.getLong("cached")==900,"actual provider usage fields retained");
        check(failed.isNull("input")&&failed.isNull("cached"),"missing usage remains unknown rather than zero");
        check(first.getInt("context_images")==1,"request image count uses recorded message prefix without decoding image bytes");
        check(projection.getInt("selected")==1&&projection.getInt("analysed")==1,"selected and analysed remain separate source counts");
        check(projection.getJSONArray("decisions").getJSONObject(0).getString("reason").contains("限制条件"),"enhanced per-block image basis retained");
        JSONObject source=run.getJSONObject("conversation").getJSONArray("turns").getJSONObject(0);
        source.put("usage",new JSONObject().put("prompt_tokens","12").put("completion_tokens",-1).put("total_tokens",1.5).put("prompt_cache_hit_tokens",0));
        JSONObject zero=ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(0);
        check(zero.getLong("cached")==0,"explicit provider cache zero is not unknown");
        check(zero.isNull("input")&&zero.isNull("output")&&zero.isNull("total"),"invalid string, negative and fractional token values are not coerced");
        JSONObject imageAudit=new JSONObject().put("assessment",new JSONObject().put("decision","not_needed").put("reason","字幕已完整描述，未缺视觉条件。"))
                .put("requests",new JSONArray()).put("selected_count",0).put("analysed_count",0);
        JSONObject summary=new JSONObject().put("image_audit",imageAudit);
        check(ClassroomAudit.project(null,summary).getJSONArray("decisions").getJSONObject(0).getString("decision").equals("not_needed"),"saved summary-only image audit stays readable");
        check(ClassroomAudit.project(new JSONObject(),summary).getJSONArray("decisions").length()==0,"new run never inherits the previous summary image audit");
        check(ClassroomAudit.project(null,null).getJSONArray("turns").length()==0,"no audit remains an empty read-only state");
        JSONObject disabled=new JSONObject().put("draft",new JSONObject().put("image_assessment",new JSONObject().put("decision","disabled").put("reason","未获图片上传许可。"))
                .put("image_requests",new JSONArray()));
        check(ClassroomAudit.project(disabled,null).getJSONArray("decisions").getJSONObject(0).getString("decision").equals("disabled"),"default draft preserves the no-upload decision and basis");
    }
    private void page()throws Exception{
        check(Boolean.parseBoolean(args.getString("nativeScreen","false")),"native screen metrics required without density override");
        account=new ZjuSettingsStore(test.getTargetContext()).account();lesson=new ClassroomLesson("993500",Long.toUnsignedString(System.nanoTime()),"课堂处理审计验收","合成课堂","教室A","6",System.currentTimeMillis()-600000);
        dir=new ClassroomStore(test.getTargetContext(),account).localLesson(lesson);
        JSONObject summary=new JSONObject().put("lesson",lesson.json()).put("complete",true).put("run","synthetic-audit").put("updated",System.currentTimeMillis())
                .put("overview","已完成的合成笔记").put("parts",new JSONArray()).put("images",new JSONArray());
        write("run.json",fixture());write("summary.json",summary);String original=checkpoint();
        lessonScreen=open(new Intent(test.getTargetContext(),ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()));
        await(()->{View button=find(lessonScreen.getWindow().getDecorView(),"处理记录");return button!=null&&button.isEnabled();});
        tap(find(lessonScreen.getWindow().getDecorView(),"处理记录"));await(()->test.lastResumed instanceof ClassroomAuditActivity);screen=test.lastResumed;
        await(()->text(screen.getWindow().getDecorView()).contains("缓存读取 900"));SystemClock.sleep(700);
        check(text(screen.getWindow().getDecorView()).contains("输入 未提供"),"native page distinguishes absent usage from actual cache counts");
        check(!text(screen.getWindow().getDecorView()).contains("PRIVATE-"),"native page never renders original private payloads");
        check(test.enteredPages.contains("ClassroomAuditActivity"),"actual audit content uses shared entry animation");capture("initial");
        ScrollView scroll=(ScrollView)field(screen,"viewport");int before=scroll.getScrollY();swipe();await(()->scroll.getScrollY()>before);
        Rect bounds=new Rect();scroll.getGlobalVisibleRect(bounds);evidence.put(new JSONObject().put("phase","reading-height").put("viewport",bounds.height())
                .put("scrollY",scroll.getScrollY()).put("screenHeight",screen.getResources().getDisplayMetrics().heightPixels));
        check(find(screen.getWindow().getDecorView(),"处理记录").getGlobalVisibleRect(new Rect())==false,"real upward reading scrolls the title out of the viewport");capture("reading");
        test.runOnMainSync(()->scroll.scrollTo(0,0));SystemClock.sleep(250);tap(find(screen.getWindow().getDecorView(),"刷新处理记录"));
        await(()->((View)fieldUnchecked(screen,"refresh")).isEnabled());
        check(original.equals(checkpoint()),"entry and refresh preserve exact original checkpoint bytes");
        test.runOnMainSync(screen::recreate);await(()->test.lastResumed instanceof ClassroomAuditActivity&&test.lastResumed!=screen);screen=test.lastResumed;
        await(()->text(screen.getWindow().getDecorView()).contains("缓存读取 900"));SystemClock.sleep(500);capture("restored");
        check(original.equals(checkpoint()),"configuration restoration stays read-only");
        ClassroomStore.begin(dir);try{tap(find(screen.getWindow().getDecorView(),"刷新处理记录"));await(()->text(screen.getWindow().getDecorView()).contains("课堂正在处理，请稍后刷新"));
            check(original.equals(checkpoint()),"busy lesson is not read or rewritten while a producer owns its files");}
        finally{ClassroomStore.end(dir);}
        tap(find(screen.getWindow().getDecorView(),"刷新处理记录"));await(()->text(screen.getWindow().getDecorView()).contains("缓存读取 900"));
        test.runOnMainSync(()->new ZjuSettingsStore(test.getTargetContext()).options(false,false));await(()->screen.isFinishing());
        check(true,"disabling the account closes its audit page");
    }
    private void unfinished()throws Exception{
        account=new ZjuSettingsStore(test.getTargetContext()).account();lesson=new ClassroomLesson("993500",Long.toUnsignedString(System.nanoTime()),"中断请求审计验收","合成课堂","","",System.currentTimeMillis());
        dir=new ClassroomStore(test.getTargetContext(),account).localLesson(lesson);JSONObject run=fixture();
        JSONObject turn=run.getJSONObject("conversation").getJSONArray("turns").getJSONObject(0);turn.put("status","running").remove("finished");
        run.getJSONObject("conversation").put("turns",new JSONArray().put(turn));write("run.json",run);String original=checkpoint();
        check(!ClassroomStore.active(dir),"unfinished saved request has no active processing owner");
        check(ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(0).isNull("finished"),"absent completion time remains unknown in the projection");
        screen=open(new Intent(test.getTargetContext(),ClassroomAuditActivity.class).putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,account).putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()));
        await(()->text(screen.getWindow().getDecorView()).contains("未记录结果"));SystemClock.sleep(600);capture("unfinished");
        check(!text(screen.getWindow().getDecorView()).contains("请求中"),"interrupted historical request is not presented as live network activity");
        check(original.equals(checkpoint()),"unknown-result display preserves the original running checkpoint");
    }
    private Activity open(Intent intent){return test.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private String checkpoint()throws Exception{return new String(Files.readAllBytes(new File(dir,"run.json").toPath()),StandardCharsets.UTF_8);}
    private void write(String name,JSONObject value)throws Exception{File file=new File(dir,name);ClassroomStore.write(file,value);owned.add(file);}
    private static View find(View view,String label){if(view instanceof TextView text&&label.contentEquals(text.getText())||label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),label);if(result!=null)return result;}return null;}
    private static String text(View view){StringBuilder result=new StringBuilder();if(view instanceof TextView t)result.append(t.getText()).append('\n');if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.append(text(group.getChildAt(i)));return result.toString();}
    private void tap(View view){if(view==null)throw new AssertionError("missing observed control");int[] point=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(point));long now=SystemClock.uptimeMillis();
        float x=point[0]+view.getWidth()/2f,y=point[1]+view.getHeight()/2f;MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,x,y,0);
        try{test.sendPointerSync(down);SystemClock.sleep(80);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}SystemClock.sleep(300);}
    private void swipe(){float width=test.getTargetContext().getResources().getDisplayMetrics().widthPixels,height=test.getTargetContext().getResources().getDisplayMetrics().heightPixels;long now=SystemClock.uptimeMillis();
        for(int i=0;i<=12;i++){MotionEvent event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,width*.65f,height*(.8f-.55f*i/12),0);try{test.sendPointerSync(event);}finally{event.recycle();}SystemClock.sleep(35);}}
    private void check(boolean value,String description)throws Exception{checks++;evidence.put(new JSONObject().put("case",description).put("passed",value));if(!value)throw new AssertionError(description);}
    private void await(BooleanSupplier condition){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(condition.getAsBoolean())return;SystemClock.sleep(100);}throw new AssertionError("audit state timeout");}
    private void capture(String name)throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();if(raw==null)return;float scale=Math.min(1,1400f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true);try(FileOutputStream out=new FileOutputStream(new File(output,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,73,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
    private static Object field(Object owner,String name)throws Exception{java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static Object fieldUnchecked(Object owner,String name){try{return field(owner,name);}catch(Exception e){throw new AssertionError(e);}}
}
