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
    private final List<TextView> cells = new ArrayList<>();
    private final LocalDate monday;
    private final ScrollView viewport;
    private TextView emptyState;
    ClassroomLessonPickerView(Context context,List<ClassroomLesson> lessons,LocalDate monday,Consumer<ClassroomLesson> select){
        super(context);this.monday=monday;setHorizontalScrollBarEnabled(true);
        TextView metrics=label(context,"",dp(64));
        float widestDigit=0;for(char digit='0';digit<='9';digit++)widestDigit=Math.max(widestDigit,metrics.getPaint().measureText(String.valueOf(digit)));
        int timeWidth=Math.max(dp(64),(int)Math.ceil(widestDigit*4+metrics.getPaint().measureText(":"))+dp(16));
        int courseWidth=Math.max(dp(128),(int)Math.ceil(metrics.getPaint().measureText("课程标题课堂笔记"))+dp(16));
        // Match the week agenda: the horizontal gesture owner stays stationary,
        // while its inner native viewport scrolls the table vertically.
        viewport=new ScrollView(context);viewport.setVerticalScrollBarEnabled(false);
        addView(viewport,new FrameLayout.LayoutParams(-2,-1));vertical(viewport);
        TableLayout table=new TableLayout(context);viewport.addView(table,new ScrollView.LayoutParams(-2,-2));
        TableRow head=new TableRow(context);head.addView(label(context,"开始",timeWidth));
        for(int d=0;d<7;d++){LocalDate date=monday.plusDays(d);head.addView(label(context,date.format(DateTimeFormatter.ofPattern("M/d"))+"\n"+new String[]{"周一","周二","周三","周四","周五","周六","周日"}[d],courseWidth));}table.addView(head);
        SortedMap<String,List<ClassroomLesson>> slots=new TreeMap<>();
        for(ClassroomLesson l:lessons)if(!l.date().isBefore(monday)&&l.date().isBefore(monday.plusDays(7))){String time=Instant.ofEpochMilli(l.startsAt).atZone(ClassroomLesson.ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));slots.computeIfAbsent(time,k->new ArrayList<>()).add(l);}
        for(Map.Entry<String,List<ClassroomLesson>> slot:slots.entrySet()){
            TableRow row=new TableRow(context);row.setGravity(Gravity.TOP);TextView time=label(context,slot.getKey(),timeWidth);time.setSingleLine(true);row.addView(time);
            for(int d=0;d<7;d++){LinearLayout cell=new LinearLayout(context);cell.setOrientation(LinearLayout.VERTICAL);cell.setPadding(dp(4),dp(4),dp(4),dp(4));
                for(ClassroomLesson l:slot.getValue())if(l.date().equals(monday.plusDays(d))){TextView item=label(context,"",courseWidth-dp(8));item.setGravity(Gravity.START);item.setTextSize(14);UiStyle.choice(item,false,UiStyle.RADIUS_FIELD);item.setTag(l);cells.add(item);item.setOnClickListener(v->select.accept((ClassroomLesson)v.getTag()));item.setOnLongClickListener(v->{if(context instanceof android.app.Activity)ClassroomMonitorUi.choose((android.app.Activity)context,(ClassroomLesson)v.getTag());return true;});UiStyle.addSpaced(cell,item,0,6);}
                row.addView(cell,new TableRow.LayoutParams(courseWidth,-2));}table.addView(row);
        }
        if(slots.isEmpty()){
            FrameLayout emptyRow=new FrameLayout(context);
            emptyState=label(context,"本周暂无课堂",0);
            emptyRow.addView(emptyState,new FrameLayout.LayoutParams(0,-2));table.addView(emptyRow);
        }
        refreshStates(System.currentTimeMillis());
    }
    ScrollView verticalViewport(){return viewport;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        if(emptyState!=null){
            emptyState.setMinWidth(w);emptyState.setMaxWidth(w);
            android.view.ViewGroup.LayoutParams params=emptyState.getLayoutParams();params.width=w;emptyState.setLayoutParams(params);
        }
    }
    @Override protected void onScrollChanged(int x,int y,int oldx,int oldy){
        super.onScrollChanged(x,y,oldx,oldy);
        // Keep this local state inside the visible viewport without resetting the table's offset.
        if(emptyState!=null)emptyState.setTranslationX(x);
    }
    void refreshStates(long now) {
        for(TextView view:cells){ClassroomLesson lesson=(ClassroomLesson)view.getTag();
            String value=lesson.course+"\n"+lesson.title+"\n"+lesson.room+" · "+(ClassroomMonitorUi.watching(getContext(),lesson)?"监视中":lesson.state(now));
            if(!value.contentEquals(view.getText()))view.setText(value);
            view.setEnabled(true);view.setAlpha(1f);
            view.setContentDescription(value+"，"+(lesson.selectable(now)?"打开课堂":"选择课堂监视"));
        }
    }
    boolean refreshLessons(List<ClassroomLesson> lessons) {
        Map<String,ClassroomLesson> visible=new HashMap<>();
        for(ClassroomLesson lesson:lessons)if(!lesson.date().isBefore(monday)&&lesson.date().isBefore(monday.plusDays(7)))
            visible.put(lesson.courseId+"/"+lesson.id,lesson);
        if(visible.size()!=cells.size())return false;
        for(TextView cell:cells){ClassroomLesson old=(ClassroomLesson)cell.getTag(),next=visible.get(old.courseId+"/"+old.id);
            if(next==null||next.startsAt!=old.startsAt||!next.course.equals(old.course)||!next.title.equals(old.title)||!next.room.equals(old.room))return false;
        }
        for(TextView cell:cells){ClassroomLesson old=(ClassroomLesson)cell.getTag();cell.setTag(visible.get(old.courseId+"/"+old.id));}
        refreshStates(System.currentTimeMillis());return true;
    }
    private TextView label(Context c,String text,int widthPixels){TextView v=new TextView(c);v.setText(text);v.setTextSize(14);UiStyle.title(v);v.setGravity(Gravity.CENTER);v.setPadding(dp(8),dp(12),dp(8),dp(12));v.setMinWidth(widthPixels);v.setMaxWidth(widthPixels);return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
