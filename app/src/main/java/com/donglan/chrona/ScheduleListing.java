package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.data.*;
import com.donglan.chrona.zju.ZjuExam;
import java.util.ArrayList;
import java.util.List;

/** One read-only schedule composition for native and LAN; school exams never become local candidates. */
final class ScheduleListing {
    static final class Row {
        final EventCandidate candidate;
        final ZjuExam exam;
        Row(EventCandidate candidate,ZjuExam exam){this.candidate=candidate;this.exam=exam;}
    }
    static final class Page {
        List<Long> ids;
        List<Row> rows;
        int total,page;
        String account="",scope="",warning="";
        boolean examsLoaded;
    }
    static Page load(Context context,TaskStore store,ScheduleQuery query,boolean oldest,int requested,long focus){
        Page result=new Page();result.scope=scope(context);result.ids=store.queryScheduleIds(query);
        List<Row> exams=new ArrayList<>();
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        result.account=settings.account();
        if(query.includesOfficialExams()&&settings.enabled()&&settings.configured())try{
            List<ZjuExam> cached=new ZjuExamStore(context).load(result.account);
            result.examsLoaded=cached!=null;
            if(cached!=null)for(ZjuExam exam:cached){
                EventCandidate projection=projection(result.account,exam);
                if(query.matchesOfficialExam(projection,exam.title+" "+exam.time+" "+exam.room+" "+exam.seat+" "+exam.kind+" 考试"))
                    exams.add(new Row(projection,exam));
            }
        }catch(Exception failure){result.warning="考试缓存无法读取，请在考试安排中刷新";}
        if(!schoolCurrent(context,result.account))exams.clear();
        result.total=result.ids.size()+exams.size();
        int localFocus=focus>0?store.schedulePageForCandidate(query,oldest,focus):-1;
        result.page=Math.max(0,Math.min(localFocus>=0?localFocus:requested,Math.max(0,(result.total-1)/ScheduleQuery.PAGE_SIZE)));
        List<Row> combined=prefix(store,query,oldest,result.page,exams);
        if(localFocus>=0){
            for(int i=0;i<combined.size();i++)if(combined.get(i).exam==null&&combined.get(i).candidate.id==focus){
                int actual=i/ScheduleQuery.PAGE_SIZE;
                if(actual!=result.page){result.page=actual;combined=prefix(store,query,oldest,actual,exams);}
                break;
            }
        }
        int begin=Math.min(combined.size(),result.page*ScheduleQuery.PAGE_SIZE);
        result.rows=new ArrayList<>(combined.subList(begin,Math.min(combined.size(),begin+ScheduleQuery.PAGE_SIZE)));
        return result;
    }
    private static List<Row> prefix(TaskStore store,ScheduleQuery query,boolean oldest,int page,List<Row> exams){
        int limit=(int)Math.min(Integer.MAX_VALUE,((long)page+1)*ScheduleQuery.PAGE_SIZE);
        List<Row> all=new ArrayList<>(exams);
        for(EventCandidate item:store.queryScheduleFirst(query,oldest,limit))all.add(new Row(item,null));
        java.util.Comparator<EventCandidate> order=query.comparator(oldest);
        all.sort((a,b)->order.compare(a.candidate,b.candidate));
        return all;
    }
    static boolean schoolCurrent(Context context,String account){
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        return settings.enabled()&&settings.configured()&&settings.account().equals(account);
    }
    static String scope(Context context){
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        return settings.account()+":"+settings.enabled()+":"+settings.configured()+":"+ZjuExamStore.revision();
    }
    static EventCandidate projection(String account,ZjuExam exam){
        long id=ExternalCourseAgenda.id("zju-exam:"+account+":"+exam.courseKey+":"+exam.kind+":"+exam.time+":"+exam.room+":"+exam.seat);
        return new EventCandidate(id,0,exam.title,exam.start,exam.end,"Asia/Shanghai",location(exam),
                exam.kind+"\n"+exam.time,null,false);
    }
    static String location(ZjuExam exam){return (exam.room.isEmpty()?"考场待公布":exam.room)+" · "+(exam.seat.isEmpty()?"座位待公布":"座位 "+exam.seat);}
    static ZjuExam find(Context context,long id)throws Exception{
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        if(!settings.enabled()||!settings.configured())return null;
        String account=settings.account();List<ZjuExam> rows=new ZjuExamStore(context).load(account);
        if(rows!=null)for(ZjuExam exam:rows)if(projection(account,exam).id==id&&schoolCurrent(context,account))return exam;
        return null;
    }
    private ScheduleListing(){}
}
