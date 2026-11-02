package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.zju.ZjuSnapshot;
import com.donglan.chrona.zju.ZjuTimetableView;
import com.donglan.chrona.timetable.Timetable;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

/** Only an account-owned, unambiguous school timetable can supply a lecture's end. */
final class ClassroomMonitorTiming {
    private static String cachedAccount;
    private static long cachedRevision = -1;
    private static List<ZjuSnapshot.Lesson> cachedLessons = List.of();
    private static Map<String,Timetable> cachedTables = Map.of();

    static synchronized long endsAt(Context app,String account,ClassroomLesson lesson)throws Exception {
        long revision=ZjuTimetableStore.revision();
        if(!account.equals(cachedAccount)||revision!=cachedRevision){
            List<ZjuSnapshot.Lesson> lessons=new ZjuTimetableStore(app).load(account);
            Map<String,Timetable> tables=ZjuTimetableView.terms(lessons);
            cachedLessons=lessons;cachedTables=tables;cachedAccount=account;cachedRevision=revision;
        }
        return matchingEnd(lesson,cachedLessons,cachedTables);
    }
    static boolean ended(Context app,String account,ClassroomLesson lesson)throws Exception {
        long end=endsAt(app,account,lesson);
        return end>0&&System.currentTimeMillis()>=end;
    }
    static long matchingEnd(ClassroomLesson lesson,List<ZjuSnapshot.Lesson> lessons){
        return matchingEnd(lesson,lessons,ZjuTimetableView.terms(lessons));
    }
    private static long matchingEnd(ClassroomLesson lesson,List<ZjuSnapshot.Lesson> lessons,Map<String,Timetable> tables){
        String name=normalized(lesson.course);long exactEnd=0;
        if(name.isEmpty()||lesson.startsAt<=0)return 0;
        for(ZjuSnapshot.Lesson candidate:lessons){
            if(candidate.start!=lesson.startsAt||!name.equals(normalized(candidate.title)))continue;
            if(candidate.end<=candidate.start||exactEnd!=0&&candidate.end!=exactEnd)return 0;
            exactEnd=candidate.end;
        }
        if(exactEnd==0)return 0;
        long result=0;
        for(Timetable table:tables.values())for(Timetable.Block block:table.blocks){
            if(!name.equals(normalized(block.title)))continue;
            boolean anchored=false;long blockEnd=0;
            for(Timetable.Occurrence entry:block.occurrences()){
                if(!lesson.date().equals(entry.start.toLocalDate()))continue;
                if(entry.start.atZone(table.zone).toInstant().toEpochMilli()==lesson.startsAt)anchored=true;
                blockEnd=Math.max(blockEnd,entry.end.atZone(table.zone).toInstant().toEpochMilli());
            }
            if(!anchored)continue;
            // The Grid's block already owns all teaching-break and same-date/room rules.
            // Its occurrences span weeks: only this actual date supplies the end epoch.
            if(blockEnd<=lesson.startsAt||result!=0&&blockEnd!=result)return 0;
            result=blockEnd;
        }
        return result;
    }
    private static String normalized(String value){
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+","");
    }
}
