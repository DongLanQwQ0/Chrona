package com.donglan.chrona;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ZjuHomeworkHistoryActivity extends Activity {
    private static final int PAGE_SIZE=50;
    private LinearLayout root;
    private android.widget.Button more;
    private long beforeId=Long.MAX_VALUE;
    private boolean loading;
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"作业更新记录");
        more=new android.widget.Button(this);UiStyle.button(more,false);root.addView(more);more.setOnClickListener(v->loadMore());
        SettingsPageLayout.show(this,root,false);loadMore();
    }
    private void loadMore(){
        if(loading)return;loading=true;more.setEnabled(false);more.setText("正在读取");
        long cursor=beforeId;
        ZjuSync.WORKER.execute(()->{
            List<ZjuHomeworkStore.History> result;
            try{result=new ZjuHomeworkStore(this).history(PAGE_SIZE,cursor);}
            catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;loading=false;more.setEnabled(true);more.setText("读取失败，重试");});return;}
            List<ZjuHomeworkStore.History> rows=result;
            runOnUiThread(()->{if(isDestroyed())return;root.removeView(more);
                if(rows.isEmpty()&&cursor==Long.MAX_VALUE){TextView empty=new TextView(this);empty.setText("暂无更新记录");UiStyle.muted(empty);root.addView(empty);}
                DateTimeFormatter time=DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault());
                for(ZjuHomeworkStore.History row:rows){
                    TextView text=new TextView(this);text.setTextSize(16);text.setPadding(16,16,16,16);UiStyle.glass(text);
                    text.setText(row.title+"\n"+row.change+" · "+time.format(Instant.ofEpochMilli(row.at)));text.setTextColor(UiStyle.colors(this).text);UiStyle.addSpaced(root,text,0,10);
                    text.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle(row.title).setMessage(historyText(row)).setPositiveButton("关闭",null).show());
                }
                if(!rows.isEmpty())beforeId=rows.get(rows.size()-1).id;
                loading=false;
                if(rows.size()==PAGE_SIZE){more.setEnabled(true);more.setText("加载更多");root.addView(more);}
            });
        });
    }
    private String historyText(ZjuHomeworkStore.History row){
        try{
            org.json.JSONObject after=new org.json.JSONObject(row.after),before=row.before==null?new org.json.JSONObject():new org.json.JSONObject(row.before);
            StringBuilder text=new StringBuilder(row.change);
            for(String key:new String[]{"title","deadline","submitted"})if(!java.util.Objects.equals(before.opt(key),after.opt(key)))text.append("\n\n").append(key.equals("title")?"标题":key.equals("deadline")?"截止时间":"提交状态").append("\n").append(value(before,key)).append(" → ").append(value(after,key));
            return text.toString();
        }catch(Exception e){return row.change;}
    }
    private String value(org.json.JSONObject json,String key){if(json.isNull(key)||!json.has(key))return "未设置";if(key.equals("submitted"))return json.optBoolean(key)?"已提交":"未提交";if(key.equals("deadline"))try{return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(json.getLong(key)));}catch(Exception ignored){}return json.optString(key);}
}
