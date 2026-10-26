package com.donglan.chrona;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.*;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.timetable.Timetable;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Classroom identities over the same renderer as the school timetable. No invented end times. */
final class ClassroomLessonPickerView extends FreeCalendarScrollView {
    private final LocalDate monday;
    private final ScrollView viewport;
    private final Consumer<ClassroomLesson> select;
    private final Map<Timetable.Block,ClassroomLesson> lessonsByBlock=new LinkedHashMap<>();
    private TimetableGrid grid;
    private TextView emptyState;
    private int gridWidth;
    ClassroomLessonPickerView(Context context,List<ClassroomLesson> lessons,LocalDate monday,Consumer<ClassroomLesson> select){
        super(context);this.monday=monday;this.select=select;setHorizontalScrollBarEnabled(true);
        viewport=new ScrollView(context);viewport.setVerticalScrollBarEnabled(false);
        addView(viewport,new FrameLayout.LayoutParams(-2,-1));vertical(viewport);
        for(ClassroomLesson lesson:lessons)if(inWeek(lesson)){
            var start=Instant.ofEpochMilli(lesson.startsAt).atZone(ClassroomLesson.ZONE);
            lessonsByBlock.put(Timetable.Block.startingAt(lesson.course,lesson.room,start.getDayOfWeek().getValue()-1,
                    start.toLocalTime().toSecondOfDay()/60),lesson);
        }
        rebuild(Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(40)));
    }
    private boolean inWeek(ClassroomLesson lesson){return !lesson.date().isBefore(monday)&&lesson.date().isBefore(monday.plusDays(7));}
    private void rebuild(int width){
        gridWidth=width;int x=getScrollX(),y=viewport.getScrollY();viewport.removeAllViews();
        grid=new TimetableGrid(getContext(),new ArrayList<>(lessonsByBlock.keySet()),width,block->select.accept(lessonsByBlock.get(block)));
        String[] days=new String[7];for(int d=0;d<7;d++)days[d]=monday.plusDays(d).format(DateTimeFormatter.ofPattern("M/d"))+"\n"+TimetableGrid.DAYS[d];grid.dayLabels(days);
        LinearLayout content=new LinearLayout(getContext());content.setOrientation(LinearLayout.VERTICAL);
        int edge=dp(5);content.setPadding(edge,edge,edge,edge);content.setClipChildren(false);content.addView(grid);
        viewport.addView(content,new ScrollView.LayoutParams(-2,-2));
        emptyState=null;
        if(lessonsByBlock.isEmpty()){emptyState=new TextView(getContext());emptyState.setText("本周暂无课堂");emptyState.setTextSize(16);UiStyle.muted(emptyState);emptyState.setPadding(dp(8),dp(8),dp(8),0);content.addView(emptyState,new LinearLayout.LayoutParams(width-edge*2,-2));}
        refreshStates(System.currentTimeMillis());
        post(()->{scrollTo(x,0);viewport.scrollTo(0,y);});
    }
    ScrollView verticalViewport(){return viewport;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&w!=gridWidth)rebuild(w);}
    @Override protected void onScrollChanged(int x,int y,int oldx,int oldy){super.onScrollChanged(x,y,oldx,oldy);if(emptyState!=null)emptyState.setTranslationX(x);}
    void refreshStates(long now){
        grid.bindCourses((block,view)->{
            ClassroomLesson lesson=lessonsByBlock.get(block);String state=ClassroomMonitorUi.watching(getContext(),lesson)?"监视中":lesson.state(now);
            int lines=view.getMaxLines();float width=view.getLayoutParams().width-view.getPaddingLeft()-view.getPaddingRight();
            String title=android.text.TextUtils.ellipsize(lesson.course,view.getPaint(),width*Math.max(1,lines-(lesson.room.isEmpty()?1:2)),android.text.TextUtils.TruncateAt.END).toString();
            String room=android.text.TextUtils.ellipsize(lesson.room,view.getPaint(),width,android.text.TextUtils.TruncateAt.END).toString();
            String value=title+"\n"+(room.isEmpty()?"":room+"\n")+state;
            if(!value.contentEquals(view.getText())){SpannableString styled=new SpannableString(value);styled.setSpan(new StyleSpan(Typeface.BOLD),0,title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.setText(styled);}
            view.setTag(lesson);view.setContentDescription(lesson.course+"，"+lesson.title+"，"+lesson.date()+" "+Timetable.time(block.startMinute)+" 开始，"+lesson.room+"，"+state+"，"+(lesson.selectable(now)?"打开课堂，长按进入课堂监视":"进入课堂监视"));
            view.setOnLongClickListener(v->{if(getContext() instanceof android.app.Activity)ClassroomMonitorUi.open((android.app.Activity)getContext(),(ClassroomLesson)v.getTag());return true;});
        });
    }
    boolean refreshLessons(List<ClassroomLesson> lessons){
        Map<String,ClassroomLesson> next=new HashMap<>();for(ClassroomLesson lesson:lessons)if(inWeek(lesson))next.put(ClassroomMonitorStore.key(lesson),lesson);
        if(next.size()!=lessonsByBlock.size())return false;
        for(ClassroomLesson old:lessonsByBlock.values()){ClassroomLesson value=next.get(ClassroomMonitorStore.key(old));if(value==null||value.startsAt!=old.startsAt||!value.course.equals(old.course)||!value.room.equals(old.room)||!value.title.equals(old.title))return false;}
        lessonsByBlock.replaceAll((block,old)->next.get(ClassroomMonitorStore.key(old)));refreshStates(System.currentTimeMillis());return true;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
