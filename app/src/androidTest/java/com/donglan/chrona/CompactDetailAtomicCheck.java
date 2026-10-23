package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;

/** Disposable emulator only: actual detail header and actual applied RemoteViews. */
final class CompactDetailAtomicCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        FixtureIsolation.require(context);
        if ("automatic_merge".equals(args.getString("scope"))) return automaticMerge(test, args);
        if ("merge".equals(args.getString("scope"))) return mergePreview(test, args);
        String oldTheme = ThemeStore.mode(context);
        Activity page = null;
        long task = 0;
        try {
            ThemeStore.setMode(context, args.getString("theme", "light"));
            try (TaskStore store = new TaskStore(context)) {
                task = store.insertTask("紧凑交互验收", null, "UI fixture", System.currentTimeMillis());
                store.appendCandidate(new EventCandidate(0, task, "例会", null, null,
                        "Asia/Shanghai", "", "", null, true, null, "event", false));
                store.updateStatus(task, TaskRecord.READY, null);
            }
            page = test.startActivitySync(new Intent(context, TaskDetailActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("task_id", task));
            Activity activity = page;
            View menu = null;
            for (int i = 0; i < 100 && menu == null; i++) {
                test.waitForIdleSync();
                menu = activity.getWindow().getDecorView().findViewWithTag("detail_header_menu");
                if (menu == null) SystemClock.sleep(50);
            }
            require(menu instanceof ImageButton, "menu uses vector ImageButton");
            ImageButton icon = (ImageButton) menu;
            require(icon.getDrawable().getIntrinsicWidth() == dp(icon, 24), "24dp vector");
            require(icon.getPaddingLeft() == icon.getPaddingRight()
                    && icon.getPaddingTop() == icon.getPaddingBottom(), "symmetric icon inset");
            require(icon.getWidth() >= dp(icon, 48) && icon.getHeight() >= dp(icon, 48), "48dp touch area");
            HeaderActionGroup group = (HeaderActionGroup) icon.getParent();
            require(!group.getClipChildren(), "group does not clip motion");
            screenshot(test, context, args, "menu-rest");
            int[] at = new int[2]; icon.getLocationOnScreen(at);
            long down = SystemClock.uptimeMillis();
            MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN,
                    at[0] + icon.getWidth()/2f, at[1] + icon.getHeight()/2f, 0);
            try { test.sendPointerSync(press); } finally { press.recycle(); }
            try {
                SystemClock.sleep(300);
                require(group.getScaleX() > 1.08f && group.getScaleY() > 1.08f, "actual held group scale");
                screenshot(test, context, args, "menu-held");
            } finally {
                MotionEvent cancel = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL,
                        at[0] + icon.getWidth()/2f, at[1] + icon.getHeight()/2f, 0);
                try { test.sendPointerSync(cancel); } finally { cancel.recycle(); }
            }
            SystemClock.sleep(400);
            require(Math.abs(group.getScaleX()-1f) < .01f, "cancel restores group scale");
            // Complete a real click as well as cancellation; the production listener opens its menu.
            long tap = SystemClock.uptimeMillis();
            for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                MotionEvent event = MotionEvent.obtain(tap, SystemClock.uptimeMillis(), action,
                        at[0]+icon.getWidth()/2f, at[1]+icon.getHeight()/2f, 0);
                try { test.sendPointerSync(event); } finally { event.recycle(); }
            }
            for(int i=0;i<100&&MenuDeviceCheck.rows().isEmpty();i++){test.waitForIdleSync();SystemClock.sleep(30);}
            require(!MenuDeviceCheck.rows().isEmpty(), "real click opens original menu");
            SystemClock.sleep(400);
            require(Math.abs(group.getScaleX()-1f)<.01f, "click release restores group scale");
            screenshot(test,context,args,"menu-open");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            test.waitForIdleSync();
            long now = System.currentTimeMillis();
            PendingIntent details = PendingIntent.getActivity(context, 773,
                    new Intent(context, DashboardActivity.class), PendingIntent.FLAG_IMMUTABLE);
            StringBuilder report = new StringBuilder("PASS actual menu/vector/48dp/held 1.10/cancel/click-open/release; RemoteViews heights(dp): ");
            test.runOnMainSync(() -> {
                for (String title : new String[]{"活动", "信息资源部第三次例会与项目开发交流"}) {
                    AgendaEntry entry = new AgendaEntry(-773, 0, now+2700000, now+6300000,
                            title, "开物苑2幢学生交流中心", "", "test", true, false, true);
                    View card = AtomicAgendaCard.forIsland(activity, entry, now, now, "下一项：课程讨论", details)
                            .apply(activity, null);
                    int width = activity.getResources().getDisplayMetrics().widthPixels - dp(card, 40);
                    card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    card.layout(0, 0, width, card.getMeasuredHeight());
                    View action = card.findViewById(R.id.atomic_complete);
                    require(action.getWidth() >= dp(card, 48) && action.getHeight() >= dp(card, 48), "card action stays reachable");
                    TextView text = card.findViewById(R.id.atomic_island_title);
                    require(text.getLayout() != null && text.getLayout().getLineCount() >= 1, "title measured");
                    require(text.getLayout().getEllipsisCount(text.getLayout().getLineCount()-1) == 0, "sample title fits");
                    Bitmap image = Bitmap.createBitmap(width, card.getHeight(), Bitmap.Config.ARGB_8888);
                    card.draw(new Canvas(image));
                    try {
                        save(context, args, title.equals("活动") ? "card-short" : "card-long", image);
                    } catch (Exception error) { throw new RuntimeException(error); }
                    finally { image.recycle(); }
                    report.append(title.equals("活动") ? "short=" : "long=")
                            .append(card.getHeight()/card.getResources().getDisplayMetrics().density).append(' ');
                }
            });
            return report.toString();
        } finally {
            if (page != null) { Activity activity = page; test.runOnMainSync(activity::finish); test.waitForIdleSync(); }
            if (task > 0) try (TaskStore store = new TaskStore(context)) { store.deleteTask(task); }
            ThemeStore.setMode(context, oldTheme);
        }
    }
    private static int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }
    private static String automaticMerge(Instrumentation test, Bundle args) throws Exception {
        Context context=test.getTargetContext();
        String theme=ThemeStore.mode(context);
        var prefs=context.getSharedPreferences("duplicate_review",Context.MODE_PRIVATE);
        var shown=new java.util.HashSet<>(prefs.getStringSet("shown",java.util.Collections.emptySet()));
        java.util.List<Long> tasks=new java.util.ArrayList<>();
        Activity activity=null;
        long now=System.currentTimeMillis()+86400000;
        try {
            try(TaskStore store=new TaskStore(context)){for(TaskRecord record:store.listTasks())if("自动合并隔离夹具".equals(record.rawText)||"无关原文修改".equals(record.rawText))store.deleteTask(record.id);}
            ThemeStore.setMode(context,args.getString("theme","light"));
            long first=mergeTask(context,tasks,"来源甲","信息资源部第三次例会",now,"开物苑");
            activity=test.startActivitySync(new Intent(context,TaskDetailActivity.class).putExtra("task_id",first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            SystemClock.sleep(900);
            mergeTask(context,tasks,"来源乙","信息资源部第三次例会通知",now,"");
            android.app.Dialog dialog=waitReview(test,true);
            require(member(dialog.getWindow().getDecorView(),"来源甲")!=null,"auto popup shows original source");
            require(member(dialog.getWindow().getDecorView(),"来源乙")!=null,"auto popup shows additional source");
            var confirm=findText(dialog.getWindow().getDecorView(),"合并所选");
            require(confirm!=null&&!confirm.isEnabled(),"no default consent");
            screenshot(test,context,args,"automatic-merge-preview");
            android.widget.ScrollView scroll=findScroll(dialog.getWindow().getDecorView());
            test.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));
            SystemClock.sleep(200);
            screenshot(test,context,args,"automatic-merge-sources");
            if("true".equals(args.getString("renderOnly")))return "PASS actual automatic popup scrolls to final source with fixed actions; render-only supplement";
            var cancel=findText(dialog.getWindow().getDecorView(),"暂不合并");
            test.runOnMainSync(cancel::performClick);
            SystemClock.sleep(2400);
            require(!reviewShowing(),"cancel suppresses unchanged group");
            try(TaskStore store=new TaskStore(context)){store.updateRawText(first,"无关原文修改");}
            SystemClock.sleep(2400);
            require(!reviewShowing(),"unrelated revision does not repeat prompt");
            Activity page=activity;test.runOnMainSync(()->page.moveTaskToBack(true));
            SystemClock.sleep(500);
            mergeTask(context,tasks,"来源丙","实验课程讨论安排",now+7200000,"教学楼");
            mergeTask(context,tasks,"来源丁","实验课程讨论安排通知",now+7200000,"");
            SystemClock.sleep(2600);
            require(!reviewShowing(),"background completion never starts popup");
            test.runOnMainSync(page::finish);
            activity=test.startActivitySync(new Intent(context,TaskDetailActivity.class).putExtra("task_id",first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            dialog=waitReview(test,true);
            require(findChoice(dialog.getWindow().getDecorView()).getText().toString().startsWith("实验课程讨论安排"),"new background batch appears on resume");
            require(member(dialog.getWindow().getDecorView(),"来源甲")==null,"old dismissed group excluded from fresh popup");
            android.widget.CheckBox choice=findChoice(dialog.getWindow().getDecorView());
            test.runOnMainSync(choice::performClick);
            confirm=findText(dialog.getWindow().getDecorView(),"确认合并 1 份");
            require(confirm!=null&&confirm.isEnabled(),"explicit choice enables accurate count");
            test.waitForIdleSync();SystemClock.sleep(200);
            screenshot(test,context,args,"automatic-merge-selected");
            android.view.View submit=confirm;test.runOnMainSync(submit::performClick);
            waitReview(test,false);
            try(TaskStore store=new TaskStore(context)){
                require(store.getCandidates(tasks.get(0)).size()+store.getCandidates(tasks.get(1)).size()==2,"dismissed group not merged");
                require(store.getCandidates(tasks.get(2)).size()+store.getCandidates(tasks.get(3)).size()==1,"chosen group merged exactly once");
                require(store.getTask(tasks.get(2))!=null&&store.getTask(tasks.get(3))!=null,"source tasks preserved");
            }
            return "PASS automatic foreground/background discovery, source details, no default consent, cancel and unrelated revision suppression, new batch, selected-only real SQLite merge";
        } finally {
            if(activity!=null){Activity page=activity;test.runOnMainSync(page::finish);test.waitForIdleSync();}
            try(TaskStore store=new TaskStore(context)){for(long task:tasks)store.deleteTask(task);}
            prefs.edit().putStringSet("shown",shown).commit();ThemeStore.setMode(context,theme);
        }
    }
    private static long mergeTask(Context context,java.util.List<Long> tasks,String source,String title,long now,String place){
        try(TaskStore store=new TaskStore(context)){long id=store.insertTask("自动合并隔离夹具",null,source,System.currentTimeMillis());tasks.add(id);store.appendCandidate(new EventCandidate(0,id,title,now,now+3600000,"Asia/Shanghai",place,"",null,false,null,"event",false));store.updateStatus(id,TaskRecord.READY,null);return id;}
    }
    private static android.app.Dialog review() throws Exception {var field=DuplicateScheduleReview.class.getDeclaredField("dialog");field.setAccessible(true);return (android.app.Dialog)field.get(null);}
    private static boolean reviewShowing() throws Exception {var window=review();return window!=null&&window.isShowing();}
    private static android.app.Dialog waitReview(Instrumentation test,boolean visible)throws Exception{for(int i=0;i<160;i++){test.waitForIdleSync();if(reviewShowing()==visible)return review();SystemClock.sleep(50);}throw new AssertionError("automatic popup visible="+visible);}
    private static android.widget.CheckBox findChoice(View view){if(view instanceof android.widget.CheckBox choice)return choice;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){var result=findChoice(group.getChildAt(i));if(result!=null)return result;}return null;}
    private static android.widget.ScrollView findScroll(View view){if(view instanceof android.widget.ScrollView scroll)return scroll;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){var result=findScroll(group.getChildAt(i));if(result!=null)return result;}return null;}
    private static TextView findText(View view,String value){if(view instanceof TextView text&&text.getText().toString().equals(value))return text;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){var result=findText(group.getChildAt(i),value);if(result!=null)return result;}return null;}
    private static String mergePreview(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        String theme = ThemeStore.mode(context);
        long first = 0, second = 0;
        Activity activity = null;
        try {
            ThemeStore.setMode(context, args.getString("theme", "light"));
            try (TaskStore store = new TaskStore(context)) {
                first = store.insertTask("合并预览夹具甲", null, "来源甲", System.currentTimeMillis());
                second = store.insertTask("合并预览夹具乙", null, "来源乙", System.currentTimeMillis());
                long now = System.currentTimeMillis()+86400000;
                store.appendCandidate(new EventCandidate(0, first, "信息资源部第三次例会", now, now+3600000, "Asia/Shanghai", "开物苑", "", null, false, null, "event", false));
                store.appendCandidate(new EventCandidate(0, second, "信息资源部第三次例会通知", now, now+3600000, "Asia/Shanghai", "", "", null, false, null, "event", false));
                store.updateStatus(first, TaskRecord.READY, null); store.updateStatus(second, TaskRecord.READY, null);
            }
            activity = test.startActivitySync(new Intent(context, CandidateMergeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View source = null;
            for (int i=0; i<100 && source==null; i++) {
                test.waitForIdleSync(); source = member(activity.getWindow().getDecorView(), "来源乙");
                if (source==null) SystemClock.sleep(50);
            }
            require(source!=null, "fuzzy pair appears in actual preview");
            require(member(activity.getWindow().getDecorView(), "来源甲")!=null, "both member sources visible");
            View row = source;
            test.runOnMainSync(() -> row.requestRectangleOnScreen(new android.graphics.Rect(0,0,row.getWidth(),row.getHeight()),true));
            SystemClock.sleep(150); screenshot(test,context,args,"merge-preview");
            return "PASS actual fuzzy merge preview contains both member titles, locations and source actions; no confirmation executed";
        } finally {
            if(activity!=null){Activity page=activity;test.runOnMainSync(page::finish);test.waitForIdleSync();}
            try(TaskStore store=new TaskStore(context)){if(first>0)store.deleteTask(first);if(second>0)store.deleteTask(second);}
            ThemeStore.setMode(context,theme);
        }
    }
    private static View member(View root,String source){
        if(root instanceof android.widget.Button text && text.getText().toString().contains(source)
                && text.getText().toString().contains("信息资源部第三次例会"))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=member(group.getChildAt(i),source);if(found!=null)return found;}
        return null;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void screenshot(Instrumentation test, Context context, Bundle args, String name) throws Exception {
        Bitmap image = test.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
        require(image != null, "screenshot available");
        try { save(context, args, name, image); } finally { image.recycle(); }
    }
    private static void save(Context context, Bundle args, String name, Bitmap image) throws Exception {
        File file = new File(context.getExternalFilesDir(null), "compact-" + args.getString("theme", "light") + "-" + name + ".png");
        try (FileOutputStream stream = new FileOutputStream(file)) { require(image.compress(Bitmap.CompressFormat.PNG, 100, stream), "image saved"); }
    }
}
