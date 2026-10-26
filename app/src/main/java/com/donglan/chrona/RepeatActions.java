package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.LinkedCalendarEvents;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatOccurrences;
import com.donglan.chrona.data.RepeatRule;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import org.json.JSONObject;
import java.util.List;

/** One series mutation shared by native and LAN clients; provider failures leave local content intact. */
final class RepeatActions {
    private RepeatActions() { }
    static EventCandidate edit(Context context,long taskId,long id,String baseline,long original,JSONObject fields)throws Exception{
        synchronized(AndroidSync.LOCK){try(TaskStore store=new TaskStore(context);
                LinkedCalendarEvents writes=new LinkedCalendarEvents(store,new CalendarStore(context))){
            store.getWritableDatabase().beginTransaction();EventCandidate changed;
            try{
                EventCandidate current=store.getCandidatesByIds(List.of(id)).stream().filter(item->item.taskId==taskId).findFirst().orElse(null);
                CandidateEditBaseline.require(current,baseline);
                changed=editLocked(store,writes,current,original,fields,null);
                store.getWritableDatabase().setTransactionSuccessful();
            }finally{store.getWritableDatabase().endTransaction();}
            writes.committed();return changed;
        }}
    }
    /** Caller owns the sync lock and transaction so an announcement outcome commits with this edit. */
    static EventCandidate editLocked(TaskStore store,LinkedCalendarEvents writes,EventCandidate current,long original,
            JSONObject fields,JSONObject source)throws Exception{
        TaskRecord task=store.getTask(current.taskId);
        if(task==null||TaskRecord.PROCESSING.equals(task.status)||TaskRecord.QUEUED.equals(task.status))throw new IllegalStateException("请等待解析完成");
        if(store.isManagedHomework(current.id,current.taskId))throw new IllegalArgumentException("同步作业由学在浙大管理");
        EventCandidate before=RepeatOccurrences.at(current,original),changed=RepeatOccurrences.edit(current,original,fields);
        JSONObject exceptions=RepeatOccurrences.exceptions(changed),entry=exceptions.getJSONObject(Long.toString(original));
        org.json.JSONArray history=entry.optJSONArray("history");if(history==null)history=new org.json.JSONArray();
        JSONObject audit=new JSONObject().put("at",System.currentTimeMillis()).put("action","edit")
                .put("before",OccurrenceUpdates.snapshot(before)).put("after",OccurrenceUpdates.snapshot(RepeatOccurrences.at(changed,original)));
        if(source!=null)audit.put("source",new JSONObject(source.toString()));
        entry.put("history",history.put(audit));changed=changed.withRepeatState(exceptions.toString(),changed.repeatStoppedAtMillis);
        if(!store.updateCandidate(changed))throw new IllegalStateException("原占位已不存在");
        store.updateReviewStatus(changed.taskId);
        if(changed.calendarEventId!=null&&!writes.update(changed,input(changed)))throw new IllegalStateException("系统日历日程已不存在，请重新关联后操作");
        return store.getCandidatesByIds(List.of(changed.id)).get(0);
    }
    static EventCandidate change(Context context, long taskId, long id, String baseline,
            String action, Long original, Long moved, RepeatRule rule) throws Exception {
        synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(context);
                    LinkedCalendarEvents writes = new LinkedCalendarEvents(store,new CalendarStore(context))) {
                List<EventCandidate> rows = store.getCandidatesByIds(List.of(id));
                EventCandidate current = rows.isEmpty() ? null : rows.get(0);
                CandidateEditBaseline.require(current, baseline);
                if (current.taskId != taskId) throw new IllegalArgumentException("日程身份已变化");
                TaskRecord task = store.getTask(taskId);
                if (task == null || TaskRecord.PROCESSING.equals(task.status) || TaskRecord.QUEUED.equals(task.status))
                    throw new IllegalStateException("请等待解析完成");
                if (store.isManagedHomework(id, taskId)) throw new IllegalArgumentException("同步作业由学在浙大管理");
                EventCandidate changed;
                if ("rule".equals(action)) {
                    if (current.repeatRule != null) throw new IllegalArgumentException("已有重复系列请停止后新建，历史记录将保留");
                    if (current.completed()) throw new IllegalArgumentException("请先恢复这条待办，再设置重复");
                    if (rule == null || current.anchor() == null || current.isRecord)
                        throw new IllegalArgumentException("请先设置首次日程时间");
                    rule.validateAnchor(current.anchor(), current.timeZoneId);
                    changed = current.withRepeat(rule);
                } else {
                    if (current.repeatRule == null) throw new IllegalArgumentException("此日程未设置重复");
                    if ("stop".equals(action)) {
                        changed = current.withRepeatState(current.repeatExceptions,
                                current.repeatStoppedAtMillis == null ? System.currentTimeMillis() : current.repeatStoppedAtMillis);
                    } else {
                        if (original == null) throw new IllegalArgumentException("请选择具体一次日程");
                        EventCandidate occurrence = RepeatOccurrences.at(current, original);
                        if (occurrence == null) throw new IllegalArgumentException("这一次日程已跳过或停止");
                        JSONObject exceptions = RepeatOccurrences.exceptions(current);
                        String key = Long.toString(original);
                        JSONObject edit = exceptions.optJSONObject(key);
                        if (edit == null) edit = new JSONObject();
                        if ("skip".equals(action)) edit.put("skipped", true);
                        else if ("move".equals(action)) {
                            if (moved == null || moved < -62_135_596_800_000L || moved > 253_402_300_799_999L)
                                throw new IllegalArgumentException("改期时间无效");
                            if (current.allDay && moved % 86_400_000L != 0) throw new IllegalArgumentException("全天日程请选择日期");
                            if (current.repeatStoppedAtMillis != null && moved >= current.repeatStoppedAtMillis)
                                throw new IllegalArgumentException("已停止的系列不能改到停止时间之后");
                            edit.put("start", moved);
                        } else if ("complete".equals(action) || "reopen".equals(action)) {
                            if (!occurrence.isTask()) throw new IllegalArgumentException("只有待办和作业可以完成");
                            edit.put("completed", "complete".equals(action) ? System.currentTimeMillis() : JSONObject.NULL);
                        } else throw new IllegalArgumentException("不支持的重复日程操作");
                        exceptions.put(key, edit);
                        changed = current.withRepeatState(exceptions.toString(), current.repeatStoppedAtMillis);
                    }
                }
                store.getWritableDatabase().beginTransaction();
                try {
                    if (!store.updateCandidate(changed)) throw new IllegalStateException("日程已不存在");
                    if (changed.calendarEventId != null) {
                        if (!writes.update(changed, input(changed)))
                            throw new IllegalStateException("系统日历日程已不存在，请重新关联后操作");
                    }
                    store.updateReviewStatus(taskId);
                    store.getWritableDatabase().setTransactionSuccessful();
                } finally { store.getWritableDatabase().endTransaction(); }
                writes.committed();
                return store.getCandidatesByIds(List.of(id)).get(0);
            }
        }
    }

    static CalendarStore.EventInput input(EventCandidate series) {
        return new CalendarStore.EventInput(series.title, series.calendarStart(), series.calendarEnd(),
                series.timeZoneId, series.allDay, series.description, series.location,
                series.reminderMinutesBefore == null ? List.of() : List.of(series.reminderMinutesBefore)).withRepeat(series);
    }
}
