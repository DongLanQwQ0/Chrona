package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.text.Normalizer;
import java.util.List;

/** Only an account-owned, unambiguous school timetable can supply a lecture's end. */
final class ClassroomMonitorTiming {
    private static String cachedAccount;
    private static long cachedRevision = -1;
    private static List<ZjuSnapshot.Lesson> cachedLessons = List.of();

    static synchronized long endsAt(Context app,String account,ClassroomLesson lesson)throws Exception {
        long revision=ZjuTimetableStore.revision();
        if(!account.equals(cachedAccount)||revision!=cachedRevision){
            List<ZjuSnapshot.Lesson> lessons=new ZjuTimetableStore(app).load(account);
            cachedLessons=lessons;cachedAccount=account;cachedRevision=revision;
        }
        return matchingEnd(lesson,cachedLessons);
    }
    static boolean ended(Context app,String account,ClassroomLesson lesson)throws Exception {
        long end=endsAt(app,account,lesson);
        return end>0&&System.currentTimeMillis()>=end;
    }
    static long matchingEnd(ClassroomLesson lesson,List<ZjuSnapshot.Lesson> lessons){
        String name=normalized(lesson.course);long result=0;
        if(name.isEmpty()||lesson.startsAt<=0)return 0;
        for(ZjuSnapshot.Lesson candidate:lessons){
            if(candidate.start!=lesson.startsAt||!name.equals(normalized(candidate.title)))continue;
            if(candidate.end<=candidate.start||result!=0&&candidate.end!=result)return 0;
            result=candidate.end;
        }
        return result;
    }
    private static String normalized(String value){
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+","");
    }
}
