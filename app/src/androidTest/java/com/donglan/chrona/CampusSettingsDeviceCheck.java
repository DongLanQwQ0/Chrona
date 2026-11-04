package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.zju.ZjuSnapshot;

/** Isolated production-window evidence, with real pointer input and display rotation. */
final class CampusSettingsDeviceCheck {
    static String run(Instrumentation t, Bundle args) throws Exception {
        Context c=t.getTargetContext();FixtureIsolation.require(c);
        PtaSettingsStore pta=new PtaSettingsStore(c);
        require(!pta.configured()&&!pta.enabled()&&pta.account().isEmpty(),"refuse real PTA account");
        require(CookieManager.getInstance().getCookie("https://pintia.cn")==null || !CookieManager.getInstance().getCookie("https://pintia.cn").contains("PTASession="),"refuse existing PTA browser session");
        String page=args.getString("page","account"),theme=args.getString("theme","light"),old=ThemeStore.mode(c);
        int rotation=Integer.parseInt(args.getString("rotation","0"));
        String run=page+"-"+theme+"-r"+rotation+"-f"+args.getString("font","1.0");
        File dir=new File(c.getFilesDir(),"campus-ui-20261104");require(dir.isDirectory()||dir.mkdirs(),"output dir");
        JSONObject report=new JSONObject().put("page",page).put("theme",theme).put("rotation",rotation).put("fixture_isolation",true);
        Activity activity=null;Instrumentation.ActivityMonitor monitor=null;long task=0;
        ZjuHomeworkStore homework=new ZjuHomeworkStore(c,2);ZjuHomeworkStore.State prior=homework.state();
        try {
            ThemeStore.setMode(c,theme);
            Class<? extends Activity> cls=switch(page){case "hub"->ZjuSettingsActivity.class;case "pta"->PtaSettingsActivity.class;case "login"->PtaLoginActivity.class;case "tongge"->ScheduleCompareActivity.class;case "detail"->TaskDetailActivity.class;default->SchoolSettingsActivity.class;};
            Intent intent=new Intent(c,cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra("page",page);
            if(page.equals("detail")) {
                require(!prior.enabled&&prior.captureId==0&&prior.account.isEmpty(),"PTA source must be empty for fixture");
                String account="campus-ui-fixture-20261104";homework.configure(account,true);var state=homework.state();task=state.captureId;
                homework.apply(account,state.generation,List.of(new ZjuSnapshot.Homework("device-ui","course","程序设计 · 校园界面测试题集","只用于隔离应用的界面测试，不调用 AI。","https://pintia.cn/problem-sets/9000000000000", "",null,false)));
                intent.putExtra("task_id",task);
            }
            monitor=t.addMonitor(cls.getName(),null,false);activity=t.startActivitySync(intent);
            settle(t);require(t.getUiAutomation().setRotation(rotation==0?UiAutomation.ROTATION_FREEZE_0:UiAutomation.ROTATION_FREEZE_90),"rotation accepted");
            long until=SystemClock.uptimeMillis()+12000;long stable=0;
            while(SystemClock.uptimeMillis()<until){Activity latest=monitor.getLastActivity();if(latest!=null)activity=latest;Activity a=activity;boolean[] ready={false};
                main(t,()->ready[0]=!a.isDestroyed()&&a.hasWindowFocus()&&a.getWindow().getDecorView().isLaidOut()&&a.getResources().getConfiguration().orientation==(rotation==0?1:2));
                if(ready[0]){if(stable==0)stable=SystemClock.uptimeMillis();if(SystemClock.uptimeMillis()-stable>600)break;}else stable=0;SystemClock.sleep(60);
            }
            Activity a=activity;View root=a.getWindow().getDecorView();
            main(t,()->require(!a.isDestroyed()&&a.hasWindowFocus()&&a.getResources().getConfiguration().orientation==(rotation==0?1:2),"stable requested orientation"));
            JSONArray views=new JSONArray();main(t,()->inventory(root,views));report.put("views",views).put("density",a.getResources().getDisplayMetrics().density).put("fontScale",a.getResources().getConfiguration().fontScale);
            main(t,()->{
                int refresh=count(root,"立即同步",true)+count(root,"刷新",true);
                if(page.equals("hub"))require(count(root,"立即同步",true)==1&&count(root,"账号与同步",false)==1,"hub account and refresh exactly once");
                else if(!page.equals("tongge")&&!page.equals("login")&&!page.equals("detail"))require(refresh==0,"no duplicate subpage refresh");
                if(page.equals("homework")||page.equals("timetable"))require(count(root,"账号与同步",false)==0,"no duplicate account entry");
                if(page.equals("account")){ArrayList<EditText> fields=new ArrayList<>();collect(root,EditText.class,fields);require(fields.size()==2,"two account fields");for(EditText field:fields){var lp=(ViewGroup.MarginLayoutParams)field.getLayoutParams();require(lp.leftMargin==dp(field,16)&&lp.rightMargin==dp(field,16),"actual field 16dp card inset");}}
            });
            capture(t,dir,run+"-rest");
            if(page.equals("login")||page.equals("tongge")) {
                WebView web=find(root,WebView.class);require(web!=null,"production WebView");SystemClock.sleep(10000);
                main(t,()->{View host=(View)web.getParent();require(host.getClipToOutline(),"actual WebView host outline clipping");require(host.getOutlineProvider()!=null,"rounded outline provider");reportSafe(report,"web_url",web.getUrl());reportSafe(report,"web_host",bounds(host));});
                capture(t,dir,run+"-real-web");
                main(t,()->{web.stopLoading();web.loadDataWithBaseURL("https://example.invalid/","<html><body style='margin:0;background:#ff0055;height:100vh'><div style='position:absolute;inset:0;border:18px solid #00ff66;box-sizing:border-box'></div></body></html>","text/html","UTF-8",null);});
                SystemClock.sleep(1200);capture(t,dir,run+"-local-clip-supplement");report.put("local_content_is_clip_supplement_only",true);
            }
            if(page.equals("account")){
                ArrayList<EditText> fields=new ArrayList<>();main(t,()->collect(root,EditText.class,fields));EditText field=fields.get(1);touch(t,field,80);main(t,()->field.setText("fixture-not-saved"));SystemClock.sleep(800);capture(t,dir,run+"-keyboard");
                main(t,()->require(field.hasFocus()&&field.getGlobalVisibleRect(new Rect()),"password reachable with actual keyboard"));t.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle(t);
            }
            if(page.equals("detail")){
                View open=findText(root,"PTA"),delete=findDescription(root,"删除作业 程序设计 · 校园界面测试题集");require(open!=null&&delete!=null,"actual PTA and delete controls");
                main(t,()->{require(open.getParent()==delete.getParent(),"PTA and delete same row");require(open.getLayoutParams().width==ViewGroup.LayoutParams.WRAP_CONTENT,"PTA wraps content");reportSafe(report,"pta_button",bounds(open));reportSafe(report,"delete_button",bounds(delete));});
                main(t,()->open.requestRectangleOnScreen(new Rect(0,0,open.getWidth(),open.getHeight()),true));settle(t);capture(t,dir,run+"-actions");
                int[] at=new int[2];main(t,()->open.getLocationOnScreen(at));long down=SystemClock.uptimeMillis();PtaUiInteractionCheck.touch(t,down,down,0,at[0]+open.getWidth()/2f,at[1]+open.getHeight()/2f);SystemClock.sleep(650);capture(t,dir,run+"-long-press");PtaUiInteractionCheck.touch(t,down,SystemClock.uptimeMillis(),3,at[0]+open.getWidth()/2f,at[1]+open.getHeight()/2f);settle(t);
                touch(t,delete,80);settle(t);capture(t,dir,run+"-delete-confirm");t.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle(t);
            }
            ScrollView scroll=find(root,ScrollView.class);
            if(scroll!=null){int[] before={0};main(t,()->before[0]=scroll.getScrollY());swipe(t,scroll);settle(t);report.put("scroll_before",before[0]).put("scroll_after",scroll.getScrollY());capture(t,dir,run+"-scrolled");}
            report.put("completed",true);
        } finally {
            if(monitor!=null){Activity latest=monitor.getLastActivity();if(latest!=null)main(t,latest::finish);t.removeMonitor(monitor);}if(activity!=null){Activity a=activity;main(t,()->{if(!a.isDestroyed())a.finish();});}
            settle(t);
            if(task>0)try(TaskStore store=new TaskStore(c)){require(store.deleteTask(task),"delete this fixture capture");var db=store.getWritableDatabase();String[] own={"campus-ui-fixture-20261104"};
                db.execSQL("DELETE FROM zju_homework_deleted_tasks WHERE sync_id IN (SELECT sync_id FROM sync_task_map WHERE local_id IN (SELECT capture_id FROM zju_homework_captures WHERE account=?))",own);
                db.execSQL("DELETE FROM sync_task_map WHERE local_id IN (SELECT capture_id FROM zju_homework_captures WHERE account=?)",own);
                db.execSQL("DELETE FROM sync_candidate_map WHERE local_id IN (SELECT candidate_id FROM zju_homework_map WHERE account=?)",own);
                db.delete("zju_homework_captures","account=?",own);db.delete("zju_homework_history","account=?",own);db.delete("zju_homework_map","account=?",own);db.execSQL("UPDATE managed_zju_state SET enabled=?,account=?,generation=?,capture_id=NULL WHERE id=2",new Object[]{prior.enabled?1:0,prior.account,prior.generation});report.put("fixture_deleted",store.getTask(task)==null);}
            ThemeStore.setMode(c,old);t.getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);
            try(FileOutputStream out=new FileOutputStream(new File(dir,run+".json"))){out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        }
        return report.toString();
    }
    static void main(Instrumentation t,Runnable r){Throwable[] e={null};t.runOnMainSync(()->{try{r.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    static int dp(View v,int n){return Math.round(n*v.getResources().getDisplayMetrics().density);}
    static void settle(Instrumentation t){SystemClock.sleep(750);t.waitForIdleSync();}
    static <T extends View>void collect(View v,Class<T> type,List<T> out){if(type.isInstance(v))out.add(type.cast(v));if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),type,out);}
    static <T extends View>T find(View v,Class<T> type){ArrayList<T> out=new ArrayList<>();collect(v,type,out);return out.isEmpty()?null:out.get(0);}
    static View findText(View v,String text){if(v instanceof TextView t&&text.contentEquals(t.getText()))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View x=findText(g.getChildAt(i),text);if(x!=null)return x;}return null;}
    static View findDescription(View v,String text){if(android.text.TextUtils.equals(text,v.getContentDescription()))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View x=findDescription(g.getChildAt(i),text);if(x!=null)return x;}return null;}
    static int count(View v,String text,boolean desc){int n=(desc?android.text.TextUtils.equals(text,v.getContentDescription()):v instanceof TextView t&&text.contentEquals(t.getText()))?1:0;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)n+=count(g.getChildAt(i),text,desc);return n;}
    static String bounds(View v){int[] p=new int[2];v.getLocationOnScreen(p);return p[0]+","+p[1]+","+(p[0]+v.getWidth())+","+(p[1]+v.getHeight());}
    static void reportSafe(JSONObject o,String k,Object v){try{o.put(k,v);}catch(Exception e){throw new RuntimeException(e);}}
    static void inventory(View v,JSONArray a){if(v instanceof TextView t){JSONObject o=new JSONObject();reportSafe(o,"type",v.getClass().getSimpleName());reportSafe(o,"text",v instanceof EditText?((EditText)v).getHint():t.getText());reportSafe(o,"bounds",bounds(v));reportSafe(o,"shown",v.isShown());a.put(o);}if(v.getContentDescription()!=null){JSONObject o=new JSONObject();reportSafe(o,"description",v.getContentDescription());reportSafe(o,"bounds",bounds(v));a.put(o);}if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)inventory(g.getChildAt(i),a);}
    static void touch(Instrumentation t,View v,int hold){int[] p=new int[2];main(t,()->v.getLocationOnScreen(p));long now=SystemClock.uptimeMillis();PtaUiInteractionCheck.touch(t,now,now,0,p[0]+v.getWidth()/2f,p[1]+v.getHeight()/2f);SystemClock.sleep(hold);PtaUiInteractionCheck.touch(t,now,SystemClock.uptimeMillis(),1,p[0]+v.getWidth()/2f,p[1]+v.getHeight()/2f);}
    static void swipe(Instrumentation t,View v){int[] p=new int[2];main(t,()->v.getLocationOnScreen(p));long now=SystemClock.uptimeMillis();for(int i=0;i<=20;i++){PtaUiInteractionCheck.touch(t,now,SystemClock.uptimeMillis(),i==0?0:i==20?1:2,p[0]+v.getWidth()*.7f,p[1]+v.getHeight()*(.85f-.65f*i/20));SystemClock.sleep(16);}}
    static void capture(Instrumentation t,File d,String name)throws Exception{Bitmap raw=t.getUiAutomation().takeScreenshot();require(raw!=null,"screenshot");float scale=Math.min(1,1600f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true);try(FileOutputStream out=new FileOutputStream(new File(d,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,87,out);}if(small!=raw)small.recycle();raw.recycle();}
}
