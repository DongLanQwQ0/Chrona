package com.donglan.chrona;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import com.donglan.chrona.calendar.CalendarOccurrence;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Pair previews and durable user decisions; all collection stays off the main thread. */
public final class CourseSourceMatchingActivity extends Activity {
    private LinearLayout content;
    private List<CalendarOccurrence> rows=new ArrayList<>(),courses=new ArrayList<>();
    private List<CalendarOccurrence[]> suggestions=new ArrayList<>();
    private int generation;
    private boolean limited;
    private boolean schoolEnabled;
    private boolean firstDataPresentation;
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);
        firstDataPresentation=state==null||state.getBoolean("first_data_presentation",false);
        LinearLayout root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"课程关联");
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        schoolEnabled=new ZjuSettingsStore(this).enabled();
        SettingsPageLayout.show(this,root,state==null);load();
    }
    @Override protected void onResume(){super.onResume();boolean enabled=new ZjuSettingsStore(this).enabled();if(schoolEnabled!=enabled){schoolEnabled=enabled;load();}}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("first_data_presentation",firstDataPresentation);super.onSaveInstanceState(state);}
    private void load(){
        int current=++generation;content.removeAllViews();text("正在比对最近两周与未来六周的课程…");
        new Thread(()->{
            try{
                CourseSourceProbe.Result data=CourseSourceProbe.load(this);
                Map<String,CalendarOccurrence> unique=new LinkedHashMap<>();
                for(CalendarOccurrence c:data.rows)if(!c.allDay)unique.putIfAbsent(CourseSourceMatching.identity(c),c);
                List<CalendarOccurrence[]> candidates=new ArrayList<>();Set<String> seen=new HashSet<>();
                Map<String,List<CalendarOccurrence>> slots=new HashMap<>();
                for(CalendarOccurrence c:data.rows)if(!c.allDay){
                    String slot=Math.floorMod(c.startMillis,7L*86400000L)+":"+(c.endMillis-c.startMillis);
                    List<CalendarOccurrence> peers=slots.computeIfAbsent(slot,k->new ArrayList<>());
                    for(CalendarOccurrence other:peers){
                        String pair=CourseSourceMatching.pairKey(c,other);
                        if(!seen.contains(pair)&&CourseSourceMatching.suggested(c,other)){
                            seen.add(pair);candidates.add(new CalendarOccurrence[]{other,c});
                        }
                    }
                    peers.add(c);
                }
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||generation!=current)return;
                    if(!new ZjuSettingsStore(this).enabled()){
                        data.rows.removeIf(c->c.calendarId==CourseSourceMatching.ZJU);
                        unique.values().removeIf(c->c.calendarId==CourseSourceMatching.ZJU);
                        candidates.removeIf(p->p[0].calendarId==CourseSourceMatching.ZJU||p[1].calendarId==CourseSourceMatching.ZJU);
                    }
                    rows=data.rows;courses=new ArrayList<>(unique.values());suggestions=candidates;limited=data.limited;render();
                    if(firstDataPresentation){firstDataPresentation=false;UiMotion.enterPage(content);}});
            }catch(Exception failure){android.util.Log.w("Chrona","Course source probe failed",failure);
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||generation!=current)return;
                    content.removeAllViews();button("读取课程失败 · 重试",this::load);});}
        },"chrona-course-matching").start();
    }
    private void render(){
        content.removeAllViews();button("手动关联课程名字",this::manual);
        if(checkSelfPermission(android.Manifest.permission.READ_CALENDAR)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            text("系统日历未授权；可返回日程来源允许读取。");
        if(limited)text("实例较多，当前展示有界样本。未出现的课程可调整日程后重试。");
        Map<String,String> rules=new HashMap<>(ScheduleSources.courseRules(this));
        if(!new ZjuSettingsStore(this).enabled())rules.keySet().removeIf(pair->pair.startsWith("zju\n")||pair.contains("\u001fzju\n"));
        if(!rules.isEmpty()){
            text("已保存的关联与排除");
            for(Map.Entry<String,String> entry:new TreeMap<>(rules).entrySet()){
                String pair=entry.getKey();String[] names=pair.split("\u001f",-1);
                entry(pretty(names[0])+"\n"+pretty(names.length>1?names[1]:""),
                    ("reject".equals(entry.getValue())?"保持独立":"主来源 · "+sourceName(entry.getValue())),()->{
                        UiDialogs.choiceDialog(this,"课程关联",new String[]{"解除关联 / 撤销排除","取消"},-1,choice->{
                            if(choice==0){ScheduleSources.setCourseRule(this,pair,null);render();}});
                    });
            }
        }
        text("待确认课程");
        int count=0;
        for(CalendarOccurrence[] pair:suggestions){
            CalendarOccurrence a=pair[0],b=pair[1];String key=CourseSourceMatching.pairKey(a,b);
            if(rules.containsKey(key))continue;
            boolean strong=CourseSourceMatching.strong(a,b);
            entry(a.title+"\n"+b.title,a.calendarName+" · "+b.calendarName+"\n"+
                (CourseSourceMatching.conflict(a,b)?"地点或分组存在差异":strong?"同名/同课号且时段一致":"名称近似 · 需要确认"),()->preview(a,b));
            if(++count>=80){text("已展示前 80 对，可通过手动关联选择其余课程。");break;}
        }
        if(count==0)text("当前样本暂无新的关联建议");
    }
    private void manual(){
        if(courses.size()<2){Feedback.show(this,"当前样本中可关联的课程不足");return;}
        String[] names=new String[courses.size()];for(int i=0;i<names.length;i++)names[i]=label(courses.get(i));
        UiDialogs.choiceDialog(this,"选择第一门课程",names,-1,first->{
            CalendarOccurrence a=courses.get(first);List<CalendarOccurrence> others=new ArrayList<>();
            for(CalendarOccurrence c:courses)if(!CourseSourceMatching.source(a).equals(CourseSourceMatching.source(c)))others.add(c);
            if(others.isEmpty()){Feedback.show(this,"需要来自另一来源的课程");return;}
            String[] targets=new String[others.size()];for(int i=0;i<targets.length;i++)targets[i]=label(others.get(i));
            UiDialogs.choiceDialog(this,"选择另一来源的课程名字",targets,-1,second->preview(a,others.get(second)));
        });
    }
    private void preview(CalendarOccurrence a,CalendarOccurrence b){
        String warning=CourseSourceMatching.conflict(a,b)?"\n地点或分组存在差异，请核对后选择。":"";
        String message=label(a)+"\n"+times(a)+"\n\n"+label(b)+"\n"+times(b)+warning;
        android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout panel=UiDialogs.dialogPanel(this,"核对课程关联");
        TextView details=new TextView(this);details.setText(message);details.setTextSize(15);details.setTextColor(UiStyle.colors(this).text);
        details.setTextIsSelectable(true);UiDialogs.dialogBody(panel,details);
        modalAction(panel,"选择主来源",()->{dialog.dismiss();UiDialogs.choiceDialog(this,"这门课程的主来源",
                new String[]{a.calendarName+" · "+a.title,b.calendarName+" · "+b.title},-1,selected->{
                    ScheduleSources.setCourseRule(this,CourseSourceMatching.pairKey(a,b),CourseSourceMatching.source(selected==0?a:b));render();});});
        modalAction(panel,"保持独立",()->{dialog.dismiss();ScheduleSources.setCourseRule(this,CourseSourceMatching.pairKey(a,b),"reject");render();});
        modalAction(panel,"取消",dialog::dismiss);UiDialogs.showFloatingDialog(dialog,panel);
    }
    private String times(CalendarOccurrence course){
        DateTimeFormatter format=DateTimeFormatter.ofPattern("M月d日 E HH:mm",Locale.CHINA).withZone(ZoneId.systemDefault());
        StringBuilder value=new StringBuilder();int count=0;
        for(CalendarOccurrence c:rows)if(CourseSourceMatching.identity(course).equals(CourseSourceMatching.identity(c))){
            if(count++>=4){value.append("\n…");break;}
            if(value.length()>0)value.append('\n');value.append(format.format(Instant.ofEpochMilli(c.startMillis)))
                .append("–").append(DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(c.endMillis)));
        }return value.toString();
    }
    private String label(CalendarOccurrence c){return c.title+"\n"+c.calendarName+" · "+(c.location.isBlank()?"地点未提供":c.location);}
    private String sourceName(String key){for(CalendarOccurrence c:courses)if(key.equals(CourseSourceMatching.source(c)))return c.calendarName;
        return "imported".equals(key)?"导入课表":"zju".equals(key)?"浙大课表服务":key;}
    private String pretty(String identity){int split=identity.indexOf('\n');return split<0?identity:sourceName(identity.substring(0,split))+" · "+identity.substring(split+1);}
    private void text(String value){TextView view=new TextView(this);view.setText(value);view.setTextSize(15);view.setTextColor(UiStyle.colors(this).text);UiStyle.addSpaced(content,view,0,12);}
    private void button(String value,Runnable click){Button button=new Button(this);button.setText(value);UiStyle.button(button,false);UiStyle.addSpaced(content,button,0,12);button.setOnClickListener(v->click.run());}
    private void modalAction(LinearLayout parent,String label,Runnable run){UiDialogs.addMenuItem(parent,label,false,run);}
    private void entry(String title,String detail,Runnable run){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);UiStyle.card(card);
        int density=Math.round(16*getResources().getDisplayMetrics().density);
        card.setPadding(density,Math.round(12*getResources().getDisplayMetrics().density),density,Math.round(12*getResources().getDisplayMetrics().density));
        TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(16);heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);UiStyle.title(heading);card.addView(heading);
        TextView meta=new TextView(this);meta.setText(detail);meta.setTextSize(13);UiStyle.muted(meta);UiStyle.addSpaced(card,meta,4,0);
        card.setOnClickListener(v->run.run());UiStyle.pressable(card);UiStyle.addSpaced(content,card,0,8);
    }
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
