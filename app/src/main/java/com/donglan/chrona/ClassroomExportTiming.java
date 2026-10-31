package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.classroom.ClassroomExport;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.zju.ZjuCalendarStore;

/** Archive dates use the timetable; period lookup also covers historical recordings. */
final class ClassroomExportTiming {
    private ClassroomExportTiming() { }
    static long endsAt(Context app,String account,ClassroomLesson lesson)throws Exception{
        long matched=ClassroomMonitorTiming.endsAt(app,account,lesson);
        return matched>0?matched:ClassroomExport.periodEnd(lesson,ZjuCalendarStore.load(app).ends);
    }
}
