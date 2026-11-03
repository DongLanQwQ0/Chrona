package com.donglan.chrona;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.text.*;
import org.json.*;

/** Local, searchable topic selection and optional explicit course labels. */
public final class PtaScopeActivity extends Activity {
    private PtaSettingsStore settings;
    private LinearLayout rows;
    private boolean courses;
    private String account;
    private EditText search;
    private android.widget.ScrollView viewport;
    @Override protected void onCreate(Bundle saved){
        ThemeStore.apply(this);super.onCreate(saved);settings=new PtaSettingsStore(this);account=settings.account();courses=getIntent().getBooleanExtra("courses",false);
        LinearLayout root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,courses?"关联课程":"同步题集");
        search=new EditText(this);search.setHint("搜索题集");search.setSingleLine();UiStyle.input(search);UiStyle.addSpaced(root,search,0,8);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);root.addView(rows);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render(s.toString());}public void afterTextChanged(Editable e){}});
        search.setText(saved==null?"":saved.getString("search",""));render(search.getText().toString());SettingsPageLayout.show(this,root,saved==null);
        viewport=(android.widget.ScrollView)root.getParent();
        if(saved!=null){int y=saved.getInt("scroll");viewport.post(()->viewport.scrollTo(0,y));}
    }
    private void render(String search){
        rows.removeAllViews();
        if(!account.equals(settings.account())){finish();return;}
        try{
            JSONArray items=new JSONArray(settings.prefs.getString("catalog:"+account,"[]"));int shown=0;
            for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i);String id=item.getString("id"),title=item.getString("title");
                if(!title.toLowerCase(java.util.Locale.ROOT).contains(search.toLowerCase(java.util.Locale.ROOT)))continue;
                shown++;
                if(courses){TextView[] value=new TextView[1];value[0]=SettingsPageLayout.row(this,rows,title,settings.course(id),()->UiDialogs.textEditorDialog(this,"课程名称",settings.course(id),"保存",text->{
                    if(!account.equals(settings.account()))return;
                    synchronized(AndroidSync.LOCK){settings.prefs.edit().putString("course:"+account+":"+id,text.trim()).putLong("attempt",0)
                            .putLong("options_revision",settings.prefs.getLong("options_revision",0)+1).apply();}
                    value[0].setText(text.trim());ZjuSync.requestPta(this,null);
                }));}
                else{
                    Switch selected=SettingsPageLayout.toggle(this,rows,title,!settings.excluded().contains(id));
                    selected.setOnCheckedChangeListener((v,on)->{
                        if(!account.equals(settings.account()))return;
                        java.util.Set<String> excluded=new java.util.HashSet<>(settings.excluded());if(on)excluded.remove(id);else excluded.add(id);
                        synchronized(AndroidSync.LOCK){settings.prefs.edit().putStringSet("excluded:"+account,excluded).putLong("attempt",0)
                                .putLong("options_revision",settings.prefs.getLong("options_revision",0)+1).apply();}ZjuSync.requestPta(this,null);
                    });
                }
            }
            if(shown==0){TextView empty=new TextView(this);empty.setText(items.length()==0?"请先同步 PTA 题集":"无匹配题集");UiStyle.muted(empty);rows.addView(empty);}
        }catch(JSONException e){Feedback.show(this,"无法读取题集，请重新同步");}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("search",search.getText().toString());out.putInt("scroll",viewport.getScrollY());}
}
