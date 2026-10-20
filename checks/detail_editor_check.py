"""Exercise the production start-time watchers and LAN start/end correction.

The Android portion uses narrow field doubles, not device rendering or gestures.
Nested selection-bar gesture ownership is covered by dock_navigation_check.py.
"""
from recurrence_dependencies import recurrence_java

import os
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DETAIL = ROOT / "app/src/main/java/com/donglan/chrona/TaskDetailActivity.java"
source = DETAIL.read_text(encoding="utf-8")


def block(text, anchor):
    start = text.index(anchor)
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


start_watcher = block(source, "start.addTextChangedListener(new TextWatcher() {") + ");"
end_watcher = block(source, "end.addTextChangedListener(new TextWatcher() {") + ");"
format_method = block(source, "private static String format(Long millis)")
parse_method = block(source, "private static Long parse(String value, ZoneId zone)")
interval = re.search(r"SHIFTED_START_INTERVAL_MILLIS\s*=\s*([^;]+);", source).group(1)
for expected in (
    "DockNavigationLayout category = new DockNavigationLayout(this,",
    "类型选择栏，",
    "pager.addGesturePriorityChild(category)",
    "detailPager.addGesturePriorityChild(category)",
    "editorSelectionBars = saved.selectionBars",
    "CategoryTimeChange.from(",
    "category.fullWidthMotionHost()",
):
    if expected not in source:
        raise AssertionError(f"Missing detail selection-bar integration: {expected}")
if 'UiDialogs.choiceDialog(this, "日程类型"' in source:
    raise AssertionError("Detail type must use the selection bar, not the previous picker")
picker = block(source, "private void configureTimePicker(")
assert '"截止时间", candidate.isHomework() && !managedHomework, null)' in source
assert 'if (clearable)' in picker and 'clear.setText("清空")' in picker
assert 'clear.setOnClickListener(v -> { field.setText(""); dialog.dismiss(); });' in picker
assert 'if (value.isEmpty() && fallback != null) value = fallback.getText().toString().trim();' in picker
# A fallback seeds the picker only; no field write happens just by opening it.
assert 'field.setText' not in picker[:picker.index('LinearLayout actions =')]
assert 'candidate.isHomework() ? start : null' in source
assert 'due.setOnClickListener(null)' in source and 'due.setClickable(false)' in source
poll = block(source, 'private void pollForResult()')
assert 'refreshAcknowledgementActions(content)' in poll
assert poll.index('refreshAcknowledgementActions(content)') < poll.index('!hasUnsavedEdits')
ack_refresh = block(source, 'private void refreshAcknowledgementActions(')
assert 'hasUnsavedEdits' not in ack_refresh and 'markCandidateDirty' not in ack_refresh
save_click = source[source.index('publish[0] = button(card, "保存", () -> {'):]
save_fields = save_click[save_click.index('ZoneId zone ='):save_click.index('if ((edited.calendarStart()')]
day_start_method = block(source, 'private static Long parseDayStart(')
day_end_method = block(source, 'private static Long parseDayEnd(')

harness = r'''
import java.time.*;
import java.time.format.*;
import java.util.*;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.EventTimeDefaults;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.calendar.AllDayDates;

public final class DetailEditorCheck {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final long SHIFTED_START_INTERVAL_MILLIS = __INTERVAL__;
    static int checks;
    static final class Candidate { long id = 1; boolean task; boolean isTask() { return task; } }
    static final class Editable {
        final String text;
        Editable(String text) { this.text = text; }
        public String toString() { return text; }
    }
    interface TextWatcher {
        void beforeTextChanged(CharSequence value, int start, int count, int after);
        void onTextChanged(CharSequence value, int start, int before, int count);
        void afterTextChanged(Editable value);
    }
    static final class EditText {
        String value;
        int writes;
        final List<TextWatcher> watchers = new ArrayList<>();
        EditText(String value) { this.value = value; }
        String getText() { return value; }
        void addTextChangedListener(TextWatcher watcher) { watchers.add(watcher); }
        void setText(String next) {
            String previous = value;
            for (TextWatcher watcher : watchers) watcher.beforeTextChanged(previous, 0, previous.length(), next.length());
            value = next; writes++;
            for (TextWatcher watcher : watchers) watcher.onTextChanged(next, 0, previous.length(), next.length());
            for (TextWatcher watcher : watchers) watcher.afterTextChanged(new Editable(next));
        }
    }
    static final class AllDayToggle {
        final boolean checked;
        AllDayToggle(boolean checked) { this.checked = checked; }
        boolean isChecked() { return checked; }
    }
    final Candidate candidate = new Candidate();
    final boolean[] autoEnd, syncingRange = {false};
    final int[] selectedCategory;
    final EditText start, end;
    final AllDayToggle allDay;
    int dirty;
    DetailEditorCheck(boolean task, boolean generated, boolean dateOnly, String from, String until) {
        candidate.task = task;
        autoEnd = new boolean[]{generated};
        selectedCategory = new int[]{EventCategory.indexOf(task ? EventCategory.TASK : EventCategory.EVENT)};
        start = new EditText(from); end = new EditText(until); allDay = new AllDayToggle(dateOnly);
        __START_WATCHER__
        __END_WATCHER__
    }
    void markCandidateDirty(long id) { dirty++; }
    __FORMAT__
    __PARSE__
    __DAY_START__
    __DAY_END__
    EventCandidate savedHomework(long sourceDue) {
        boolean managedHomework = true;
        EventCandidate candidate = new EventCandidate(1, 1, "作业", null, null, "Asia/Shanghai", "", "平台内容",
                null, false, null, EventCategory.HOMEWORK, false, false, EventCandidate.CERTAIN, sourceDue, null, false);
        String titleText = candidate.title;
        EditText due = new EditText(""), reminder = new EditText(""), location = new EditText("");
        String[] currentDescription = {""};
        __SAVE_FIELDS__
        return edited;
    }
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    static DetailEditorCheck timed(boolean task, boolean generated, String end) {
        return new DetailEditorCheck(task, generated, false, "2026-10-15 09:00", end);
    }
    public static void main(String[] args) {
        for (boolean task : new boolean[]{false, true}) {
            DetailEditorCheck crossed = timed(task, false, "2026-10-15 10:00");
            crossed.start.setText("2026-10-15 12:00");
            check(crossed.end.value.equals("2026-10-15 12:05"), "existing end follows later start by five minutes");
            check(crossed.end.writes == 1 && !crossed.syncingRange[0] && crossed.dirty == 2,
                    "both fields become dirty once; correction is nonrecursive");
            DetailEditorCheck equal = timed(task, false, "2026-10-15 10:00");
            equal.start.setText("2026-10-15 10:00");
            check(equal.end.value.equals("2026-10-15 10:05"), "equal start and end are corrected");
            DetailEditorCheck earlier = timed(task, false, "2026-10-15 13:00");
            earlier.start.setText("2026-10-15 12:00");
            check(earlier.end.value.equals("2026-10-15 13:00") && earlier.end.writes == 0,
                    "valid explicit end remains unchanged");
        }
        for (boolean generated : new boolean[]{false, true}) {
            DetailEditorCheck noEnd = timed(true, generated, "");
            noEnd.start.setText("2026-10-15 12:00");
            check(noEnd.end.value.isEmpty() && noEnd.end.writes == 0, "task with no end must keep no end");
        }
        DetailEditorCheck automatic = timed(false, true, "2026-10-15 10:00");
        automatic.start.setText("2026-10-15 12:00");
        check(automatic.end.value.equals("2026-10-15 12:05") && !automatic.autoEnd[0],
                "crossing the old generated end takes precedence over the old default duration");
        automatic.start.setText("2026-10-15 11:00");
        check(automatic.end.value.equals("2026-10-15 12:05"), "five-minute correction preserves explicit ending intent");
        DetailEditorCheck defaulted = timed(false, true, "");
        defaulted.start.setText("2026-10-15 12:00");
        check(defaulted.end.value.equals("2026-10-15 13:00"), "existing activity missing-end default remains available");
        DetailEditorCheck incomplete = timed(false, true, "2026-10-15 10:00");
        incomplete.start.setText("2026-10-15 1");
        check(incomplete.end.writes == 0 && !incomplete.syncingRange[0], "partial start cannot change the end");
        DetailEditorCheck invalidEnd = timed(false, true, "2026-10-15 1");
        invalidEnd.start.setText("2026-10-15 12:00");
        check(invalidEnd.end.writes == 0, "partial end cannot be overwritten");
        DetailEditorCheck restoring = timed(false, false, "2026-10-15 10:00");
        restoring.syncingRange[0] = true; restoring.start.setText("2026-10-15 12:00");
        check(restoring.end.writes == 0, "restoring a draft does not normalize half-restored fields");
        DetailEditorCheck allDay = new DetailEditorCheck(false, true, true, "2026-10-15", "2026-10-15");
        allDay.start.setText("2026-10-16");
        check(allDay.end.value.equals("2026-10-15") && allDay.end.writes == 0,
                "all-day inclusive display/exclusive persistence stay outside minute correction");
        for (boolean dateOnly : new boolean[]{false, true}) {
            DetailEditorCheck clearStart = new DetailEditorCheck(true, false, dateOnly,
                    dateOnly ? "2026-10-15" : "2026-10-15 09:00",
                    dateOnly ? "2026-10-16" : "2026-10-15 10:00");
            clearStart.start.setText("");
            check(clearStart.start.value.isEmpty() && clearStart.end.value.isEmpty(),
                    "clearing task start clears the whole range, including all-day");
            DetailEditorCheck clearEnd = timed(true, false, "2026-10-15 10:00");
            clearEnd.end.setText("");
            check(clearEnd.start.value.equals("2026-10-15 09:00") && clearEnd.end.value.isEmpty(),
                    "clearing task end retains start");
        }
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        long sourceDue = Instant.parse("2026-10-20T15:59:00Z").toEpochMilli();
        DetailEditorCheck clearedAllDay = new DetailEditorCheck(true, false, true, "", "");
        EventCandidate deadlineOnly = clearedAllDay.savedHomework(sourceDue);
        check(!deadlineOnly.allDay && deadlineOnly.timeZoneId.equals("Asia/Shanghai")
                && deadlineOnly.startAtMillis == null && deadlineOnly.endAtMillis == null
                && deadlineOnly.dueAtMillis.equals(sourceDue),
                "production save keeps source due timestamp and removes all-day semantics after clearing execution range");
        DetailEditorCheck datedAllDay = new DetailEditorCheck(true, false, true, "2026-10-17", "2026-10-18");
        EventCandidate scheduled = datedAllDay.savedHomework(sourceDue);
        check(scheduled.allDay && scheduled.timeZoneId.equals("UTC")
                && scheduled.startAtMillis.equals(Instant.parse("2026-10-17T00:00:00Z").toEpochMilli())
                && scheduled.endAtMillis.equals(Instant.parse("2026-10-19T00:00:00Z").toEpochMilli())
                && scheduled.dueAtMillis.equals(sourceDue),
                "production save retains filled all-day execution dates and unchanged source due");
        System.out.println("Detail editor passed: " + checks + " production-watcher checks; selection-bar wiring present");
    }
}
'''.replace("__INTERVAL__", interval).replace("__START_WATCHER__", start_watcher).replace(
    "__END_WATCHER__", end_watcher).replace("__FORMAT__", format_method).replace("__PARSE__", parse_method).replace(
    "__DAY_START__", day_start_method).replace("__DAY_END__", day_end_method).replace("__SAVE_FIELDS__", save_fields)

out = ROOT / "build/detail-editor-check"
out.mkdir(parents=True, exist_ok=True)
java_file = out / "DetailEditorCheck.java"
java_file.write_text(harness, encoding="utf-8")
java_home = Path(os.environ.get("JAVA_HOME", r"D:\Minecraft\java21"))
data = ROOT / "app/src/main/java/com/donglan/chrona/data"
subprocess.run(recurrence_java([str(java_home / "bin/javac"), "-encoding", "UTF-8", "-d", str(out), str(java_file),
                str(ROOT / "app/src/main/java/com/donglan/chrona/calendar/AllDayDates.java"),
                *[str(data / name) for name in ("EventTimeDefaults.java", "EventCandidate.java", "EventCategory.java")]]), check=True)
subprocess.run(recurrence_java([str(java_home / "bin/java"), "-cp", str(out), "DetailEditorCheck"]), check=True)

web = (ROOT / "app/src/main/assets/lan/app.js").read_text(encoding="utf-8")
web_constant = re.search(r"const SHIFTED_START_INTERVAL_MILLIS=[^;]+;", web).group(0)
web_functions = "\n".join(block(web, f"function {name}(")
                          for name in ("alignEndAfterStart", "localValue", "instant"))
web_harness = web_constant + "\n" + web_functions + r'''
let checks=0;
function check(value,label){checks++;if(!value)throw new Error(label);}
function field(value,valid=true){return{value,validity:{valid}};}
const start=field('2026-10-15T12:00'),end=field('2026-10-15T10:00');
alignEndAfterStart(start,end,false,'Asia/Shanghai');
check(end.value==='2026-10-15T12:05','LAN existing end follows later start');
const equal=field(start.value);alignEndAfterStart(start,equal,false,'Asia/Shanghai');
check(equal.value==='2026-10-15T12:05','LAN equal end follows start');
const absent=field('');alignEndAfterStart(start,absent,false,'Asia/Shanghai');
check(absent.value==='','LAN missing task end remains absent');
const allDay=field('2026-10-15');alignEndAfterStart(field('2026-10-16'),allDay,true,'UTC');
check(allDay.value==='2026-10-15','LAN all-day exclusive date semantics untouched');
const later=field('2026-10-15T13:00');alignEndAfterStart(start,later,false,'Asia/Shanghai');
check(later.value==='2026-10-15T13:00','LAN valid end unchanged');
const partial=field('2026-10-15T10:00');alignEndAfterStart(field('2026-10',false),partial,false,'Asia/Shanghai');
check(partial.value==='2026-10-15T10:00','LAN partial values untouched');
const gapEnd=field('2026-03-08T01:00');alignEndAfterStart(field('2026-03-08T02:30'),gapEnd,false,'America/New_York');
check(gapEnd.value==='2026-03-08T01:00','LAN DST-gap input remains available for explicit validation');
console.log('LAN detail editor passed: '+checks+' production-function checks');
'''
web_file = out / "detail-editor-check.js"
web_file.write_text(web_harness, encoding="utf-8")
subprocess.run([os.environ.get("NODE_BINARY", "node"), str(web_file)], check=True)
