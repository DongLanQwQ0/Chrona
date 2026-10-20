package com.donglan.chrona;

import android.content.Context;
import android.view.Gravity;
import android.widget.*;
import com.donglan.chrona.classroom.ClassroomLesson;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Start-time matrix: unknown end times never become invented lesson durations. */
final class ClassroomLessonPickerView extends FreeCalendarScrollView {
    ClassroomLessonPickerView(Context context,List<ClassroomLesson> lessons,LocalDate monday,Consumer<ClassroomLesson> select){
        super(context);setHorizontalScrollBarEnabled(true);
        TextView metrics=label(context,"",dp(64));
        float widestDigit=0;for(char digit='0';digit<='9';digit++)widestDigit=Math.max(widestDigit,metrics.getPaint().measureText(String.valueOf(digit)));
        int timeWidth=Math.max(dp(64),(int)Math.ceil(widestDigit*4+metrics.getPaint().measureText(":"))+dp(16));
        int courseWidth=Math.max(dp(128),(int)Math.ceil(metrics.getPaint().measureText("课程标题课堂笔记"))+dp(16));
        TableLayout table=new TableLayout(context);addView(table);
        TableRow head=new TableRow(context);head.addView(label(context,"开始",timeWidth));
        for(int d=0;d<7;d++){LocalDate date=monday.plusDays(d);head.addView(label(context,date.format(DateTimeFormatter.ofPattern("M/d"))+"\n"+new String[]{"周一","周二","周三","周四","周五","周六","周日"}[d],courseWidth));}table.addView(head);
        SortedMap<String,List<ClassroomLesson>> slots=new TreeMap<>();
        for(ClassroomLesson l:lessons)if(!l.date().isBefore(monday)&&l.date().isBefore(monday.plusDays(7))){String time=Instant.ofEpochMilli(l.startsAt).atZone(ClassroomLesson.ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));slots.computeIfAbsent(time,k->new ArrayList<>()).add(l);}
        for(Map.Entry<String,List<ClassroomLesson>> slot:slots.entrySet()){
            TableRow row=new TableRow(context);row.setGravity(Gravity.TOP);TextView time=label(context,slot.getKey(),timeWidth);time.setSingleLine(true);row.addView(time);
            for(int d=0;d<7;d++){LinearLayout cell=new LinearLayout(context);cell.setOrientation(LinearLayout.VERTICAL);cell.setPadding(dp(4),dp(4),dp(4),dp(4));
                for(ClassroomLesson l:slot.getValue())if(l.date().equals(monday.plusDays(d))){TextView item=label(context,l.course+"\n"+l.title+"\n"+l.room+" · "+l.state(System.currentTimeMillis()),courseWidth-dp(8));item.setGravity(Gravity.START);item.setTextSize(14);UiStyle.choice(item,false,UiStyle.RADIUS_FIELD);boolean ready=l.selectable(System.currentTimeMillis());item.setEnabled(ready);if(!ready)item.setAlpha(.55f);else item.setOnClickListener(v->select.accept(l));UiStyle.addSpaced(cell,item,0,6);}
                row.addView(cell,new TableRow.LayoutParams(courseWidth,-2));}table.addView(row);
        }
        if(slots.isEmpty())table.addView(label(context,"本周暂无课堂",dp(240)));
    }
    private TextView label(Context c,String text,int widthPixels){TextView v=new TextView(c);v.setText(text);v.setTextSize(14);UiStyle.title(v);v.setGravity(Gravity.CENTER);v.setPadding(dp(8),dp(12),dp(8),dp(12));v.setMinWidth(widthPixels);v.setMaxWidth(widthPixels);return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
