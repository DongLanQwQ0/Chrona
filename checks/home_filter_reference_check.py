"""Exercise actual filter rules and the job retry method without network or Android devices."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / "build/home-filter-reference-checks"
out.mkdir(parents=True, exist_ok=True)
java = Path(os.environ["JAVA_HOME"]) / "bin"
source = (root / "app/src/main/java/com/donglan/chrona/processing/ProcessingJobService.java").read_text(encoding="utf-8")
start = source.index("private ParseResult requestWithRetries(")
opening = source.index("{", start)
depth, end = 1, opening + 1
while depth:
    if source[end] == "{": depth += 1
    elif source[end] == "}": depth -= 1
    end += 1
method = source[start:end]
harness = r'''
import java.io.*;
import java.util.*;
import java.time.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.net.RequestControl;
public class HomeFilterReferenceCheck {
    static final int MAX_REQUEST_ATTEMPTS=3;
    static final long RETRY_DELAY_MILLIS=1;
    METHOD
    static void previewFailure(long id,IOException error) { throw new AssertionError(error); }
    static boolean sleep(long ms) { return true; }
    static boolean isTransient(IOException error) { return true; }
    static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        long captured=ZonedDateTime.of(2026,9,30,23,58,0,0,ZoneId.systemDefault()).toInstant().toEpochMilli();
        TaskRecord record=new TaskRecord(5,"明天下午三点交作业","text",captured,TaskRecord.PROCESSING,null);
        HomeFilterReferenceCheck job=new HomeFilterReferenceCheck();
        job.requestWithRetries(new AiSettings(),record,List.of(),null,"",5,new RequestControl(240000));
        require(ChatCompletionClient.anchors.size()==3,"must exercise transient retries");
        for(long anchor:ChatCompletionClient.anchors) require(anchor==captured,"queue and retry must preserve captured time");
        TaskRecord edited=new TaskRecord(5,"明天下午四点交作业","text",captured,TaskRecord.PROCESSING,null);
        job.requestWithRetries(new AiSettings(),edited,List.of(),null,"",5,new RequestControl(240000));
        require(ChatCompletionClient.anchors.get(3)==captured,"reparse must keep displayed reference");
        LocalDate today=LocalDate.of(2026,10,3);
        ScheduleFilterState state=new ScheduleFilterState(0,0,0,0,0,today,today);
        require(ScheduleFilterSummary.describe(state,"",today).isEmpty(),"default has no redundant summary");
        state.range=ScheduleFilterState.CUSTOM;state.date=today.minusDays(1);state.until=today.plusDays(1);
        state.category=4;state.publication=1;
        String summary=ScheduleFilterSummary.describe(state,"考核",today);
        require(summary.contains("2026-10-02 至 2026-10-04") && summary.contains("截止")
                && summary.contains("待确认") && summary.contains("考核"),"all active conditions visible");
        state.reset(today);state.source=1;
        summary=ScheduleFilterSummary.describe(state,"",today);
        require(summary.contains("系统日历") && summary.contains("2026-10-01 至 2026-10-31"),"implicit system month visible");
        state.reset(today);
        require(ScheduleFilterSummary.describe(state,"",today).isEmpty(),"reset removes constraints");
        System.out.println("Home filter and actual retry reference checks passed");
    }
}
class AiSettings { }
class ParseResult { }
class DiagLog { static void add(Object context,String message) { } }
class ChatCompletionClient {
    interface PreviewSink { void append(String s) throws IOException; void appendReasoning(String s) throws IOException; }
    static List<Long> anchors=new ArrayList<>();
    ChatCompletionClient(AiSettings settings,RequestControl control) { }
    ParseResult parseImages(long id,String raw,List<byte[]> images,String links,String files,long now,String zone,
            BestEffortPreview preview,boolean stream) throws IOException {
        anchors.add(now);
        if(anchors.size()<3) throw new IOException("temporary transport failure");
        return new ParseResult();
    }
}
class StreamingOutputStore {
    StreamingOutputStore(Object context) { }
    Writer begin(long id) throws IOException { return new Writer(); }
    static class Writer implements AutoCloseable {
        void append(String s) throws IOException { }
        void appendReasoning(String s) throws IOException { }
        public void close() { }
    }
}
class BestEffortPreview implements AutoCloseable {
    BestEffortPreview(ChatCompletionClient.PreviewSink sink,StreamingOutputStore.Writer writer,
            java.util.function.Consumer<IOException> onError) { }
    public void close() { }
}
'''.replace("METHOD", method)
check = out / "HomeFilterReferenceCheck.java"
check.write_text(harness, encoding="utf-8")
sources = [root / "app/src/main/java/com/donglan/chrona" / relative for relative in (
    "data/TaskRecord.java", "data/ScheduleFilterState.java", "data/ScheduleFilterSummary.java",
    "data/ScheduleRangeStep.java", "data/EventCategory.java", "net/RequestControl.java")]
subprocess.run([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(out),
                *map(str, sources), str(check)], check=True)
subprocess.run([str(java / "java.exe"), "-cp", str(out), "HomeFilterReferenceCheck"], check=True)
subprocess.run([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-cp", str(out), "-d", str(out),
                str(root / "app/src/main/java/com/donglan/chrona/CandidateFeedback.java"),
                str(root / "app/src/main/java/com/donglan/chrona/CategoryTimeChange.java"),
                str(root / "app/src/main/java/com/donglan/chrona/data/EventCandidate.java"),
                str(root / "app/src/main/java/com/donglan/chrona/data/EventTimeDefaults.java"),
                str(root / "checks/CandidateFeedbackCheck.java")], check=True)
subprocess.run([str(java / "java.exe"), "-cp", str(out), "com.donglan.chrona.CandidateFeedbackCheck"], check=True)
