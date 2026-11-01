package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Native, local-only course-affairs acceptance in the existing isolated emulator package. */
final class ClassroomAffairsDeviceChecks {
    private final Instrumentation test;private final Bundle args;private final Context app;
    private final List<File> owned=new ArrayList<>(),dirs=new ArrayList<>();private final List<Activity> screens=new ArrayList<>();
    private final JSONArray evidence=new JSONArray();private volatile Activity resumed;private int assertions;private File output;private String account;
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity a){resumed=a;screens.add(a);android.util.Log.i("ChronaAffairsCheck","resumed "+System.identityHashCode(a)+" "+a.getClass().getSimpleName()+" orientation="+a.getResources().getConfiguration().orientation);}public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}
        public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){android.util.Log.i("ChronaAffairsCheck","destroyed "+System.identityHashCode(a)+" changing="+a.isChangingConfigurations());}
    };
    static String run(Instrumentation test,Bundle args)throws Exception{return new ClassroomAffairsDeviceChecks(test,args).run();}
    private ClassroomAffairsDeviceChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;app=test.getTargetContext();}
    private String run()throws Exception{
        if(!app.getPackageName().equals("com.donglan.chrona.batch.validation")||!Build.HARDWARE.equals("ranchu")||args.containsKey("widthDp"))throw new SecurityException("Owned emulator with native metrics required");
        account=new ZjuSettingsStore(app).account();if(!account.equals("00000000-0000-0000-0000-000000000036"))throw new SecurityException("Synthetic account required");
        String variant=args.getString("variant","");
        output=new File(app.getExternalFilesDir(null),"course-affairs/"+args.getString("orientation","portrait")+"-"+args.getString("theme","light")+(variant.isEmpty()?"":"-"+variant));output.mkdirs();
        Application application=(Application)app.getApplicationContext();test.runOnMainSync(()->application.registerActivityLifecycleCallbacks(lifecycle));
        ClassroomStore store=new ClassroomStore(app,account);ClassroomLesson first=null;
        try{
            for(JSONObject record:store.history()){
                ClassroomLesson stale=ClassroomLesson.fromJson(record.getJSONObject("lesson"));
                if(!Set.of("993850","993851").contains(stale.courseId)||!stale.course.equals("事务验收课程"))continue;
                File dir=store.existingSummary(stale);if(dir==null)continue;File[] files=dir.listFiles();
                if(files!=null&&Arrays.stream(files).allMatch(file->Set.of("summary.json","materials.json","run.json","image-0.jpg").contains(file.getName()))){for(File file:files)file.delete();dir.delete();}
            }
            for(int i=0;i<3;i++){
                ClassroomLesson lesson=new ClassroomLesson(i==2?"993851":"993850",Long.toUnsignedString(System.nanoTime()),"事务验收课程","2026-10-"+(20+i)+"第7-8节","教室A","6",java.time.LocalDate.of(2026,10,20+i).atTime(14,15).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli());
                File dir=store.localLesson(lesson);dirs.add(dir);if(i==0)first=lesson;
                JSONArray affairs=new JSONArray();for(int n=0;n<12;n++)affairs.put(new JSONObject().put("title","评分与考核 "+n).put("text","**实验占比为30%**，教师明确要求保留运行结果。重复要求保留原样。这里是足够长的真实排版合成事务，便于连续阅读与检查页面高度。").put("sources",new JSONArray().put(0)).put("images",new JSONArray()));
                JSONObject part=new JSONObject().put("notes",new JSONArray().put(new JSONObject().put("title","知识不进入汇总").put("text","HIDDEN-LEARNING").put("sources",new JSONArray().put(0)).put("images",new JSONArray())))
                        .put("affairs",affairs).put("assignments",new JSONArray().put(new JSONObject().put("text",i==2?"OTHER-COURSE-ACTION":"下周交实验报告").put("sources",new JSONArray().put(0))));
                JSONObject summary=new JSONObject().put("lesson",lesson.json()).put("complete",i!=1).put("affairs_schema",1).put("updated",3-i).put("overview","HIDDEN-OVERVIEW")
                        .put("parts",new JSONArray().put(part)).put("images",new JSONArray()).put("raw_images",0).put("candidate_images",0);
                affairs.getJSONObject(0).put("text",affairs.getJSONObject(0).getString("text")+"\n\n![事务示例图](images/image-0.jpg)").put("images",new JSONArray().put("image-0.jpg"));
                summary.put("images",new JSONArray().put(new JSONObject().put("file","image-0.jpg").put("second",0)));
                Bitmap bitmap=Bitmap.createBitmap(240,80,Bitmap.Config.ARGB_8888);bitmap.eraseColor(i==0?android.graphics.Color.rgb(170,60,30):android.graphics.Color.rgb(20,90,150));
                File image=new File(dir,"image-0.jpg");try(OutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);}finally{bitmap.recycle();}owned.add(image);
                write(dir,"summary.json",summary);write(dir,"materials.json",new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("start",0).put("end",20).put("text","合成原文"))));
                write(dir,"run.json",new JSONObject().put("sentinel","append-only-history"));
            }
            ClassroomLesson initial=first;File initialDir=dirs.get(0);String source=read(new File(initialDir,"summary.json")),history=read(new File(initialDir,"run.json"));
            Activity detail=test.startActivitySync(new Intent(app,ClassroomLessonActivity.class).putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,account).putExtra(ClassroomLessonActivity.EXTRA_LESSON,initial.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->find(detail.getWindow().getDecorView(),"课程事务")!=null&&find(detail.getWindow().getDecorView(),"课程事务").isEnabled());
            tap(find(detail.getWindow().getDecorView(),"课程事务"));await(()->resumed instanceof ClassroomAffairsActivity);
            Activity page=settledPage();String content=js(page,"document.querySelector('main').textContent");
            check(content.contains("实验占比为30%")&&content.contains("下周交实验报告"),"actual reader contains affairs and actions");
            check(!content.contains("HIDDEN-")&&!content.contains("OTHER-COURSE-ACTION"),"knowledge overview and another course are absent");
            check(js(page,"document.querySelectorAll('h2 a').length").equals("2"),"each saved lesson has one real source link");
            check(js(page,"document.querySelector('h2 a').getAttribute('aria-label')").contains("2026-10-20"),"source accessibility name includes its actual lesson date");
            check(js(page,"document.querySelectorAll('.assignment').length").equals("0"),"aggregate has no automatic task extraction");
            await(()->{try{return js(page,"[...document.images].every(image=>image.complete&&image.naturalWidth===240)").equals("true");}catch(Exception e){return false;}});
            check(js(page,"[...document.images].map(image=>new URL(image.src).pathname).join('|')").contains(initial.id+"/images/image-0.jpg"),"same image names resolve through actual per-lesson resource scope");
            boolean[] blocked={false};test.runOnMainSync(()->blocked[0]=((WebView)fieldUnchecked(page,"document")).getSettings().getBlockNetworkLoads());check(blocked[0],"same offline renderer blocks external network");
            check(((TextView)field(page,"courseField")).getText().toString().contains("993850"),"same-name courses are disambiguated by actual identity");
            check(((TextView)field(page,"courseField")).getContentDescription()==null,"current visible course is the accessible field name");
            await(()->visibleReader(page));test.waitForIdleSync();SystemClock.sleep(100);capture("initial");
            if(Boolean.parseBoolean(args.getString("captureOnly","false")))return assertions+" native initial render assertions; no paid or school requests";
            ScrollView viewport=(ScrollView)field(page,"viewport");Rect bounds=new Rect();viewport.getGlobalVisibleRect(bounds);
            swipe();await(()->viewport.getScrollY()>80);check(!find(page.getWindow().getDecorView(),"课程事务").getGlobalVisibleRect(new Rect()),"upward reading scrolls title and tools off screen");
            evidence.put(new JSONObject().put("available_height",bounds.height()).put("viewport_height",viewport.getHeight()).put("scroll_after",viewport.getScrollY()));capture("reading");
            int position=viewport.getScrollY();test.runOnMainSync(page::recreate);await(()->resumed instanceof ClassroomAffairsActivity&&resumed!=page);Activity restored=settledPage();
            await(()->((ScrollView)fieldUnchecked(restored,"viewport")).getScrollY()>position-30);check(true,"rotation/recreation preserves course and reading offset");
            test.runOnMainSync(()->((ScrollView)fieldUnchecked(restored,"viewport")).scrollTo(0,0));SystemClock.sleep(200);
            tap(find(restored.getWindow().getDecorView(),"刷新课程事务"));await(()->find(restored.getWindow().getDecorView(),"刷新课程事务").isEnabled());
            check(source.equals(read(new File(initialDir,"summary.json")))&&history.equals(read(new File(initialDir,"run.json"))),"entry refresh and recreation preserve exact summary and conversation bytes");
            ClassroomStore.begin(initialDir);try{tap(find(restored.getWindow().getDecorView(),"刷新课程事务"));await(()->find(restored.getWindow().getDecorView(),"刷新课程事务").isEnabled());check(js(restored,"document.querySelectorAll('h2 a').length").equals("2"),"busy refresh retains the valid prior read-only projection");}finally{ClassroomStore.end(initialDir);}
            ClassroomStore.begin(dirs.get(2));try{
                tap((View)field(restored,"courseField"));await(()->findWindow("事务验收课程（993851）")!=null);SystemClock.sleep(300);tap(findWindow("事务验收课程（993851）"));
                await(()->((TextView)fieldUnchecked(restored,"courseField")).getText().toString().contains("993851")&&find(restored.getWindow().getDecorView(),"课堂正在处理，请稍后刷新")!=null&&find(restored.getWindow().getDecorView(),"刷新课程事务").isEnabled());
                check(field(restored,"document")==null&&((TextView)field(restored,"courseField")).getText().toString().contains("993851"),"busy new-course choice never presents previous course content");capture("busy-new-course");
                ClassroomStore.begin(initialDir);try{
                    tap((View)field(restored,"courseField"));await(()->findWindow("事务验收课程（993850）")!=null);SystemClock.sleep(300);tap(findWindow("事务验收课程（993850）"));
                    await(()->((TextView)fieldUnchecked(restored,"courseField")).getText().toString().contains("993850")&&find(restored.getWindow().getDecorView(),"刷新课程事务").isEnabled());
                    check(field(restored,"document")==null,"error-only source state also updates to a different busy course");
                    tap((View)field(restored,"courseField"));await(()->findWindow("事务验收课程（993851）")!=null);SystemClock.sleep(300);tap(findWindow("事务验收课程（993851）"));
                    await(()->((TextView)fieldUnchecked(restored,"courseField")).getText().toString().contains("993851")&&find(restored.getWindow().getDecorView(),"刷新课程事务").isEnabled());
                }finally{ClassroomStore.end(initialDir);}
            }finally{ClassroomStore.end(dirs.get(2));}
            tap(find(restored.getWindow().getDecorView(),"刷新课程事务"));await(()->{try{return ready(restored)&&js(restored,"document.querySelector('main').textContent").contains("OTHER-COURSE-ACTION");}catch(Exception e){return false;}});
            check(js(restored,"document.querySelectorAll('h2 a').length").equals("1"),"real course choice switches only to the selected course");
            tap((View)field(restored,"courseField"));await(()->findWindow("事务验收课程（993850）")!=null);tap(findWindow("事务验收课程（993850）"));await(()->{try{return ready(restored)&&js(restored,"document.querySelectorAll('h2 a').length").equals("2");}catch(Exception e){return false;}});
            test.runOnMainSync(()->((ScrollView)fieldUnchecked(restored,"viewport")).scrollTo(0,0));SystemClock.sleep(300);
            ScrollView returnedViewport=(ScrollView)field(restored,"viewport");int sourceY=Math.round(40*app.getResources().getDisplayMetrics().density);test.runOnMainSync(()->returnedViewport.scrollTo(0,sourceY));SystemClock.sleep(200);
            int sourcePosition=returnedViewport.getScrollY();tapElement(restored,"document.querySelector('h2 a')");await(()->resumed instanceof ClassroomLessonActivity);Activity original=settled(ClassroomLessonActivity.class,"documentView");
            check(ClassroomLesson.fromJson(new JSONObject(original.getIntent().getStringExtra(ClassroomLessonActivity.EXTRA_LESSON))).id.equals(initial.id),"real source tap opens its original lesson identity");
            SystemClock.sleep(350);capture("original-detail");
            test.runOnMainSync(original::finish);await(()->resumed instanceof ClassroomAffairsActivity);Activity returned=settledPage();SystemClock.sleep(500);ScrollView finalViewport=(ScrollView)field(returned,"viewport");
            evidence.put(new JSONObject().put("source_position",sourcePosition).put("returned_position",finalViewport.getScrollY()).put("pending_restore",field(returned,"restoreY")));
            check(Math.abs(finalViewport.getScrollY()-sourcePosition)<10,"return from original lesson preserves aggregate reading position");capture("returned");
            test.runOnMainSync(()->new ZjuSettingsStore(app).options(false,false));await(returned::isFinishing);check(true,"account disable closes aggregate reader");
        }catch(Throwable failure){capture("failure");throw failure;}
        finally{
            test.runOnMainSync(()->{for(Activity screen:screens)if(!screen.isDestroyed())screen.finish();application.unregisterActivityLifecycleCallbacks(lifecycle);new ZjuSettingsStore(app).options(true,false);});
            for(File file:owned)file.delete();for(File dir:dirs)dir.delete();
            Files.write(new File(output,"report.json").toPath(),new JSONObject().put("assertions",assertions).put("evidence",evidence).put("paid_calls",0).toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return assertions+" native affairs assertions; no paid or school requests";
    }
    private void write(File dir,String name,JSONObject value)throws Exception{File file=new File(dir,name);ClassroomStore.write(file,value);owned.add(file);}
    private static String read(File file)throws Exception{return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    private boolean ready(Activity page){boolean[] value={false};test.runOnMainSync(()->{try{Object document=field(page,"document");value[0]=!page.isFinishing()&&!page.isDestroyed()&&document!=null&&Boolean.TRUE.equals(field(document,"ready"));}catch(Exception ignored){}});return value[0];}
    private boolean documentReady(Activity page,String fieldName){boolean[] value={false};test.runOnMainSync(()->{try{Object document=field(page,fieldName);value[0]=document!=null&&Boolean.TRUE.equals(field(document,"ready"));}catch(Exception ignored){}});return value[0];}
    private boolean visibleReader(Activity page){boolean[] value={false};test.runOnMainSync(()->{View view=(View)fieldUnchecked(page,"document");value[0]=view!=null&&view.getVisibility()==View.VISIBLE&&view.getAlpha()>=.999f&&view.getGlobalVisibleRect(new Rect());});return value[0];}
    private Activity settledPage(){return settled(ClassroomAffairsActivity.class,"document");}
    private Activity settled(Class<?> type,String documentField){long end=SystemClock.uptimeMillis()+15000,stable=0;Activity candidate=null;while(SystemClock.uptimeMillis()<end){Activity now=resumed;
        if(now!=candidate){candidate=now;stable=SystemClock.uptimeMillis();}
        int expected="landscape".equals(args.getString("orientation"))?android.content.res.Configuration.ORIENTATION_LANDSCAPE:android.content.res.Configuration.ORIENTATION_PORTRAIT;
        if(type.isInstance(candidate)&&!candidate.isDestroyed()&&candidate.getResources().getConfiguration().orientation==expected&&documentReady(candidate,documentField)&&SystemClock.uptimeMillis()-stable>500)return candidate;SystemClock.sleep(50);}throw new AssertionError("no stable "+type.getSimpleName());}
    private String js(Activity page,String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};test.runOnMainSync(()->{WebView document=(WebView)fieldUnchecked(page,"document");if(document==null){result[0]="MISSING reader; finishing="+page.isFinishing()+" destroyed="+page.isDestroyed();done.countDown();return;}document.evaluateJavascript(code,value->{result[0]=value;done.countDown();});});if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("javascript timeout");if(result[0].startsWith("MISSING"))throw new AssertionError(result[0]);return result[0];}
    private void check(boolean value,String description){assertions++;try{evidence.put(new JSONObject().put("case",description).put("passed",value));}catch(Exception e){throw new AssertionError(e);}if(!value)throw new AssertionError(description);}
    private void await(BooleanSupplier condition){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(condition.getAsBoolean())return;SystemClock.sleep(100);}throw new AssertionError("affairs state timeout");}
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object fieldUnchecked(Object owner,String name){try{return field(owner,name);}catch(Exception e){throw new AssertionError(e);}}
    private static View find(View view,String label){if(view instanceof TextView text&&label.contentEquals(text.getText())||view.getContentDescription()!=null&&label.contentEquals(view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}return null;}
    private static View findWindow(String label){List<View> roots=android.view.inspector.WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0;i--){View result=find(roots.get(i),label);if(result!=null)return result;}return null;}
    private void tapElement(Activity page,String expression)throws Exception{String bounds=js(page,"(()=>{const r=("+expression+").getBoundingClientRect();return JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2})})()");JSONObject point=new JSONObject((String)new JSONTokener(bounds).nextValue());WebView web=(WebView)field(page,"document");int[] at=new int[2];test.runOnMainSync(()->web.getLocationOnScreen(at));float density=app.getResources().getDisplayMetrics().density;tapAt(at[0]+(float)point.getDouble("x")*density,at[1]+(float)point.getDouble("y")*density);}
    private void tap(View view){if(view==null)throw new AssertionError("control missing");int[] at=new int[2];test.runOnMainSync(()->view.getLocationOnScreen(at));long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,0,at[0]+view.getWidth()/2f,at[1]+view.getHeight()/2f,0),up=MotionEvent.obtain(now,now+80,1,down.getX(),down.getY(),0);try{test.sendPointerSync(down);SystemClock.sleep(80);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}}
    private void tapAt(float x,float y){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,0,x,y,0),up=MotionEvent.obtain(now,now+80,1,x,y,0);try{test.sendPointerSync(down);SystemClock.sleep(80);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}}
    private void swipe(){int w=app.getResources().getDisplayMetrics().widthPixels,h=app.getResources().getDisplayMetrics().heightPixels;long start=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),i==0?0:i==12?1:2,w*.65f,h*(.8f-.55f*i/12),0);try{test.sendPointerSync(event);}finally{event.recycle();}SystemClock.sleep(30);}}
    private void capture(String name)throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();if(raw==null)return;float ratio=Math.min(1,1400f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*ratio),Math.round(raw.getHeight()*ratio),true);try(OutputStream out=new FileOutputStream(new File(output,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,73,out);}finally{if(raw!=small)small.recycle();raw.recycle();}}
}
