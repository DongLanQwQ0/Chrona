package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.donglan.chrona.classroom.*;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real native catalogue/history touches, synthetic files on the isolated emulator only. */
final class ClassroomHistoryDeviceCheck {
    private static int assertions;
    static String run(Instrumentation test,Bundle args)throws Exception {
        Context context=test.getTargetContext();FixtureIsolation.require(context);
        ZjuSettingsStore school=new ZjuSettingsStore(context);
        Map<String,?> before=school.prefs.getAll();
        school.prefs.edit().putString("account","00000000-0000-0000-0000-000000000036")
                .putString("credentials","synthetic-not-a-login").putBoolean("enabled",true).commit();
        ThemeStore.setMode(context,args.getString("theme","light"));
        ClassroomStore store=new ClassroomStore(context,school.account());
        List<ClassroomLesson> old=store.catalog();List<File> created=new ArrayList<>();
        Activity home=null,detail=null;assertions=0;
        Instrumentation.ActivityMonitor monitor=test.addMonitor(ClassroomLessonActivity.class.getName(),null,false);
        try {
            long now=System.currentTimeMillis();String id=Long.toString(System.nanoTime());
            ClassroomLesson downloaded=lesson(id,"已下载课堂 · 评分与考试","2",now),
                    partial=lesson(id+"1","下载中断课堂","6",now),fresh=lesson(id+"2","待下载课堂","6",now),
                    complete=lesson(id+"3","已有总结课堂","6",now);
            File dir=store.lesson(downloaded);created.add(dir);
            ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",new JSONArray()
                    .put(new JSONObject().put("start",0).put("end",10).put("text","期末考试占60%，平时成绩占40%。"))).put("raw_images",0));
            ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",new JSONArray()));
            File interrupted=store.lesson(partial);created.add(interrupted);
            ClassroomStore.write(new File(interrupted,"materials.json"),ClassroomStore.read(new File(dir,"materials.json")));
            File done=store.lesson(complete);created.add(done);
            ClassroomStore.write(new File(done,"summary.json"),new JSONObject().put("lesson",complete.json()).put("complete",true).put("updated",now));
            List<ClassroomLesson> catalog=new ArrayList<>(List.of(downloaded,partial,fresh,complete));
            for(int i=0;i<52;i++)catalog.add(lesson(id+"4"+i,"待下载课程 "+i,"6",now));
            store.catalog(catalog);
            home=test.startActivitySync(new Intent(context,ClassroomSummaryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ScheduleWorkspaceActivity workspace=(ScheduleWorkspaceActivity)home;
            await(test,()->!busy(workspace.currentPage()),"local catalogue ready");
            DockNavigationLayout dock=(DockNavigationLayout)field(workspace.currentPage(),"navigation");
            tap(test,dock.itemAt(1));await(test,()->(int)field(field(workspace.currentPage(),"s"),"mode")==1&&!busy(workspace.currentPage()),"classroom list ready");
            View root=workspace.currentPage().content();
            require(find(root,partial.course)!=null&&find(root,fresh.course)!=null,"incomplete and new classroom remain available");
            require(find(root,downloaded.course)==null&&find(root,complete.course)==null,"downloaded and summarized classrooms leave classroom list");
            capture(test,context,"classroom-list-"+args.getString("theme","light"));
            require(((ViewGroup)field(workspace.currentPage(),"listBody")).getChildCount()==50,"filtered first page still contains fifty items");
            android.widget.ScrollView scrolling=(android.widget.ScrollView)field(workspace.currentPage(),"listScroll");
            test.runOnMainSync(()->scrolling.scrollTo(0,Math.round(160*context.getResources().getDisplayMetrics().density)));
            await(test,()->scrolling.getScrollY()>0,"nonzero list position");int savedY=scrolling.getScrollY();
            Object originalList=field(workspace.currentPage(),"listBody");
            test.runOnMainSync(()->((Runnable)field(workspace.currentPage(),"refreshTick")).run());
            await(test,()->!busy(workspace.currentPage()),"local list refresh");
            require(scrolling.getScrollY()==savedY&&field(workspace.currentPage(),"listBody")==originalList,"local refresh preserves nonzero position and list instance");
            View visible=null;ViewGroup rows=(ViewGroup)originalList;
            for(int i=0;i<rows.getChildCount();i++){View item=rows.getChildAt(i);android.graphics.Rect bounds=new android.graphics.Rect();
                if(item.getGlobalVisibleRect(bounds)&&bounds.height()>=item.getHeight()){visible=item;break;}}
            tap(test,visible);detail=monitor.waitForActivityWithTimeout(10000);require(detail!=null,"visible list row opens by touch");
            Activity pending=detail;await(test,()->!busy(pending),"local pending classroom ready");
            File pendingDir=(File)field(field(pending,"s"),"dir");if(!created.contains(pendingDir))created.add(pendingDir);
            test.runOnMainSync(pending::finish);detail=null;
            await(test,()->homeFocused(workspace)&&!busy(workspace.currentPage()),"list returns from detail");
            require(scrolling.getScrollY()==savedY,"detail return preserves nonzero list position");
            View next=description(root,"下一页");test.runOnMainSync(()->next.requestRectangleOnScreen(new android.graphics.Rect(0,0,next.getWidth(),next.getHeight()),true));
            test.waitForIdleSync();tap(test,next);
            await(test,()->((ViewGroup)field(workspace.currentPage(),"listBody")).getChildCount()==4,"filtered second page contains remaining four items");
            require((int)field(field(workspace.currentPage(),"s"),"page")==1,"pagination uses filtered classroom count");
            tap(test,dock.itemAt(2));await(test,()->!busy(workspace.currentPage()),"history ready");
            View saved=find(root,downloaded.course);
            require(saved instanceof TextView&&((TextView)saved).getText().toString().contains("已下载"),"download-only classroom shown with accurate status");
            require(find(root,partial.course)==null&&find(root,fresh.course)==null,"incomplete downloads excluded from history");
            require(((TextView)find(root,complete.course)).getText().toString().contains("已完成"),"finished summary retained");
            capture(test,context,"classroom-history-"+args.getString("theme","light"));
            test.removeMonitor(monitor);monitor=test.addMonitor(ClassroomLessonActivity.class.getName(),null,false);
            tap(test,saved);detail=monitor.waitForActivityWithTimeout(10000);
            require(detail!=null,"real history touch opens native lesson despite stale remote ready status");
            Activity opened=detail;await(test,()->!busy(opened),"cached materials loaded offline");
            require(((ClassroomLesson)field(field(opened,"s"),"selected")).id.equals(downloaded.id),"history opens the selected downloaded identity");
            await(test,()->find(opened.getWindow().getDecorView(),"字幕就绪 · 1 条")!=null,"downloaded materials rendered after local load");
            require(field(field(opened,"s"),"prepared")!=null,"downloaded materials load without requesting school or AI");
            capture(test,context,"classroom-downloaded-detail-"+args.getString("theme","light"));
            test.runOnMainSync(opened::finish);detail=null;
            await(test,()->homeFocused(workspace)&&!busy(workspace.currentPage()),"history resumes");
            require(field(workspace.currentPage(),"navigation")==dock,"history return preserves selector instance");
            require(find(root,downloaded.course)!=null,"history entry remains after returning");
            // A completed background download joins history on the existing page's local refresh.
            long completedAt=SystemClock.elapsedRealtime();
            ClassroomStore.write(new File(interrupted,"candidates.json"),new JSONObject().put("images",new JSONArray()));
            await(test,()->!busy(workspace.currentPage())&&find(root,partial.course)!=null,"scheduled local completion updates history",45000);
            long backgroundRefreshMillis=SystemClock.elapsedRealtime()-completedAt;
            require(field(workspace.currentPage(),"navigation")==dock,"local refresh does not rebuild selector");
            View unchanged=find(root,downloaded.course);
            test.runOnMainSync(()->((Runnable)field(workspace.currentPage(),"refreshTick")).run());
            await(test,()->!busy(workspace.currentPage()),"unchanged local refresh");
            require(find(root,downloaded.course)==unchanged,"unchanged history retains the existing entry instance");
            capture(test,context,"classroom-history-refreshed-"+args.getString("theme","light"));
            return new JSONObject().put("assertions",assertions).put("synthetic",true).put("history",true).put("backgroundRefreshMillis",backgroundRefreshMillis).toString();
        } finally {
            if(detail!=null){Activity close=detail;test.runOnMainSync(close::finish);}
            if(home!=null){Activity close=home;test.runOnMainSync(close::finish);}
            test.removeMonitor(monitor);store.catalog(old);
            for(File dir:created)try(var paths=java.nio.file.Files.walk(dir.toPath())){
                for(var path:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);
            }
            var edit=school.prefs.edit();for(String key:List.of("account","credentials","enabled")){
                Object value=before.get(key);if(value instanceof String text)edit.putString(key,text);
                else if(value instanceof Boolean flag)edit.putBoolean(key,flag);else edit.remove(key);
            }edit.commit();
        }
    }
    private static ClassroomLesson lesson(String id,String title,String status,long now){return new ClassroomLesson("99535",id,title,"评分、考试与复习要求","",status,now-3600000);}
    private static boolean homeFocused(Activity a){return a.hasWindowFocus();}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean busy(Object target){Object session=field(target,"s");return (boolean)field(session,"busy");}
    private static View find(View root,String text){if(!root.isShown())return null;if(root instanceof TextView v&&v.getText().toString().contains(text))return v;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static View description(View root,String text){if(root.getContentDescription()!=null&&text.contentEquals(root.getContentDescription()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=description(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static void await(Instrumentation test,BooleanSupplier condition,String label){await(test,condition,label,15000);}
    private static void await(Instrumentation test,BooleanSupplier condition,String label,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){test.waitForIdleSync();if(condition.getAsBoolean())return;SystemClock.sleep(80);}throw new AssertionError(label);}
    private static void tap(Instrumentation test,View view){if(view==null)throw new AssertionError("Missing touch target");await(test,()->view.hasWindowFocus()&&view.isShown()&&view.getWidth()>0,"touch target ready");int[] xy=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(xy));long time=SystemClock.uptimeMillis();float x=xy[0]+view.getWidth()/2f,y=xy[1]+view.getHeight()/2f;MotionEvent down=MotionEvent.obtain(time,time,0,x,y,0),up=MotionEvent.obtain(time,time+80,1,x,y,0);test.sendPointerSync(down);test.sendPointerSync(up);down.recycle();up.recycle();}
    private static void capture(Instrumentation test,Context context,String name)throws Exception{test.waitForIdleSync();SystemClock.sleep(450);Bitmap image=test.getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("No render");try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private static void require(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
}
