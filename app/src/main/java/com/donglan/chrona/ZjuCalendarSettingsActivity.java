package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import com.donglan.chrona.zju.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Local calendar editor; imports/exports only the calendar's validated schema. */
public final class ZjuCalendarSettingsActivity extends Activity {
    private static final int IMPORT=41, EXPORT=42;
    private ZjuCalendarConfig config;
    private String year;
    @Override protected void onCreate(Bundle state){ThemeStore.apply(this);super.onCreate(state);try{config=ZjuCalendarStore.load(this);}catch(Exception e){Feedback.show(this,"校历读取失败，请重新导入");finish();return;}year=config.selectedYear.isEmpty()?"2026-2027":config.selectedYear;render();}
    private void render(){
        LinearLayout root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"校历");
        button(root,"校区 · "+(config.campus.equals("ordinary")?"普通本科":config.campus.equals("haining")?"海宁国际校区":"自定义"),()->UiStyle.choiceDialog(this,"校区",new String[]{"普通本科","海宁国际校区","自定义"},config.campus.equals("ordinary")?0:config.campus.equals("haining")?1:2,i->{
            String selected=List.of("ordinary","haining","custom").get(i);if(selected.equals(config.campus))return;
            UiStyle.choiceDialog(this,"切换校区并载入对应校历？",new String[]{"取消","载入"},0,n->{if(n==1){config=i==0?ZjuCalendarConfig.ordinary():i==1?ZjuCalendarConfig.haining():new ZjuCalendarConfig();config.campus=selected;persist();}});
        }));
        button(root,"学年 · "+year,()->edit("学年",year,false,value->{if(!value.matches("\\d{4}-\\d{4}"))throw new IllegalArgumentException("学年格式应为 2026-2027");year=value;config.selectedYear=value;persist();}));
        button(root,"同步学期 · "+(config.selectedTerm.isEmpty()?"全部已配置学期":config.selectedTerm),()->{
            List<String> names=new ArrayList<>(List.of("全部已配置学期"));for(ZjuCalendarConfig.Term t:config.terms)if(t.year.equals(year))names.add(t.name);
            UiStyle.choiceDialog(this,"同步学期",names.toArray(new String[0]),0,i->{config.selectedYear=year;config.selectedTerm=i==0?"":names.get(i);persist();});
        });
        for(ZjuCalendarConfig.Term t:new ArrayList<>(config.terms))if(t.year.equals(year))button(root,t.name+" · "+t.start+" — "+t.teachingEnd,()->term(t));
        button(root,"添加学期",()->edit("学期名称","",false,name->{if(name.isBlank())throw new IllegalArgumentException("请输入学期名称");if(config.term(year,name)!=null)throw new IllegalArgumentException("学期已存在");LocalDate monday=LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));ZjuCalendarConfig.Term t=new ZjuCalendarConfig.Term(year,name,monday.toString(),monday.toString(),monday.toString());term(t);}));
        button(root,"作息节次 · "+config.starts.size()+"节",()->edit("每行一节：08:00-08:45",periodText(),true,value->{List<String>s=new ArrayList<>(),e=new ArrayList<>();for(String line:value.split("\\R")){if(line.isBlank())continue;String[]p=line.trim().split("-");if(p.length!=2)throw new IllegalArgumentException("节次格式无效");s.add(p[0].trim());e.add(p[1].trim());}ZjuCalendarConfig next=copy();next.starts.clear();next.starts.addAll(s);next.ends.clear();next.ends.addAll(e);commit(next);}));
        button(root,"停课日 · "+config.closed.size()+"天",()->edit("每行一个日期：2027-04-05",String.join("\n",config.closed.stream().map(Object::toString).toArray(String[]::new)),true,value->{ZjuCalendarConfig next=copy();next.closed.clear();for(String line:value.split("\\R"))if(!line.isBlank())next.closed.add(LocalDate.parse(line.trim()));commit(next);}));
        button(root,"补课 · "+config.makeup.size()+"项",()->edit("每行：实际日期=原授课日期",makeupText(),true,value->{ZjuCalendarConfig next=copy();next.makeup.clear();for(String line:value.split("\\R")){if(line.isBlank())continue;String[]p=line.split("=");if(p.length!=2)throw new IllegalArgumentException("补课格式无效");LocalDate a=LocalDate.parse(p[0].trim());if(next.makeup.put(a,LocalDate.parse(p[1].trim()))!=null)throw new IllegalArgumentException("实际日期重复");}commit(next);}));
        if(config.campus.equals("ordinary")){TextView note=new TextView(this);note.setText("2027年调休待学校公布，请核对停课与补课安排");UiStyle.muted(note);UiStyle.addSpaced(root,note,12,12);}
        button(root,"导入校历 JSON",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),IMPORT));
        button(root,"导出校历 JSON",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"Chrona-校历.json").addCategory(Intent.CATEGORY_OPENABLE),EXPORT));
        SettingsPageLayout.show(this,root,false);
    }
    private void term(ZjuCalendarConfig.Term original){
        ZjuCalendarConfig.Term t=new ZjuCalendarConfig.Term(original.year,original.name,original.start.toString(),original.end.toString(),original.teachingEnd.toString());
        Dialog d=new Dialog(this);LinearLayout panel=panel(t.name+"学期");
        button(panel,"开始 · "+t.start,()->date("开始日期",t.start,v->{t.start=v;d.dismiss();term(t);}));
        button(panel,"结束 · "+t.end,()->date("学期结束",t.end,v->{t.end=v;d.dismiss();term(t);}));
        button(panel,"普通授课截止 · "+t.teachingEnd,()->date("普通授课截止",t.teachingEnd,v->{t.teachingEnd=v;d.dismiss();term(t);}));
        button(panel,"保存",()->{try{ZjuCalendarConfig next=copy();next.terms.removeIf(x->x.year.equals(t.year)&&x.name.equals(t.name));next.terms.add(t);commit(next);d.dismiss();}catch(Exception e){error(e);}});
        button(panel,"删除学期",()->{try{ZjuCalendarConfig next=copy();next.terms.removeIf(x->x.year.equals(t.year)&&x.name.equals(t.name));next.closed.removeIf(x->!x.isBefore(t.start)&&!x.isAfter(t.end));next.makeup.entrySet().removeIf(x->!x.getValue().isBefore(t.start)&&!x.getValue().isAfter(t.end));if(next.selectedTerm.equals(t.name))next.selectedTerm="";commit(next);d.dismiss();}catch(Exception e){error(e);}});
        button(panel,"取消",d::dismiss);UiStyle.showFloatingDialog(d,panel);
    }
    private void date(String title,LocalDate initial,java.util.function.Consumer<LocalDate> save){Dialog d=new Dialog(this);LinearLayout p=panel(title);GlassDateTimePickerView picker=new GlassDateTimePickerView(this,initial.atStartOfDay());picker.showTime(false);p.addView(picker,new LinearLayout.LayoutParams(-1,dp(220)));button(p,"确定",()->{save.accept(picker.value().toLocalDate());d.dismiss();});button(p,"取消",d::dismiss);UiStyle.showFloatingDialog(d,p);}
    private interface Save{void run(String s)throws Exception;}
    private void edit(String title,String initial,boolean multiline,Save save){Dialog d=new Dialog(this);LinearLayout p=panel(title);EditText input=new EditText(this);input.setText(initial);input.setSingleLine(!multiline);if(multiline){input.setMinLines(5);input.setMaxLines(10);}UiStyle.input(input);p.addView(input);button(p,"保存",()->{try{save.run(input.getText().toString().trim());d.dismiss();}catch(Exception e){error(e);}});button(p,"取消",d::dismiss);UiStyle.showFloatingDialog(d,p);}
    private ZjuCalendarConfig copy(){return ZjuCalendarConfig.parse(config.json().toString());}
    private void commit(ZjuCalendarConfig next){next.validate();ZjuCalendarStore.save(this,next);config=next;render();}
    private void persist(){try{commit(config);}catch(Exception e){error(e);}}
    private void error(Exception e){Feedback.show(this,e instanceof IllegalArgumentException?e.getMessage():"校历保存失败，请检查填写内容");}
    private String periodText(){List<String> l=new ArrayList<>();for(int i=0;i<config.starts.size();i++)l.add(config.starts.get(i)+"-"+config.ends.get(i));return String.join("\n",l);}
    private String makeupText(){List<String>l=new ArrayList<>();config.makeup.forEach((a,b)->l.add(a+"="+b));return String.join("\n",l);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{if(request==IMPORT){try(java.io.InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IllegalArgumentException("无法读取校历");java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();byte[]chunk=new byte[8192];int count,total=0;while((count=in.read(chunk,0,Math.min(chunk.length,262145-total)))!=-1){total+=count;if(total>262144)throw new IllegalArgumentException("校历文件过大");buffer.write(chunk,0,count);}byte[]bytes=buffer.toByteArray();commit(ZjuCalendarConfig.parse(new String(bytes,StandardCharsets.UTF_8)));year=config.selectedYear.isEmpty()?"2026-2027":config.selectedYear;render();}}else if(request==EXPORT){try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IllegalArgumentException("无法写入校历");out.write(config.json().toString(2).getBytes(StandardCharsets.UTF_8));}Feedback.show(this,"校历已导出");}}catch(Exception e){error(e);}}
    private LinearLayout panel(String title){return UiStyle.dialogPanel(this,title);}
    private void button(LinearLayout root,String label,Runnable action){Button b=new Button(this);b.setText(label);UiStyle.button(b,false);b.setOnClickListener(v->action.run());UiStyle.addSpaced(root,b,4,6);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
