"""Offline source-browser contract checks; native rendering needs device acceptance."""
from pathlib import Path
import re
import subprocess
import os

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/donglan/chrona"
settings = (JAVA / "ScheduleSourceSettingsActivity.java").read_text(encoding="utf-8")
browser = (JAVA / "ScheduleSourceContentsActivity.java").read_text(encoding="utf-8")
repository = (JAVA / "ScheduleSourceRepository.java").read_text(encoding="utf-8")

assert "control.setChecked(!control.isChecked())" not in settings
assert "row.setOnClickListener(view -> startActivity(ScheduleSourceContentsActivity.intent" in settings
assert "control.setOnCheckedChangeListener((button, checked) -> save.accept(checked))" in settings
assert 'primary.setText("重复课表 · " + name' in settings
assert 'keys.indexOf(effectivePrimary())' in settings
assert 'if (new ZjuSettingsStore(this).enabled()) { keys.add("zju")' in settings
assert 'return "zju".equals(key) && !new ZjuSettingsStore(this).enabled() ? "all" : key;' in settings
assert 'ScheduleSources.setPrimary(this, "all")' not in settings

# A disabled inclusion switch never filters read-only contents, while the Zju master switch does.
assert "ScheduleSources." not in browser
assert "CourseAgenda.load(context,begin,end,zone)" in repository
assert 'if(!settings.enabled())throw new IllegalArgumentException' in repository
assert browser.count("if (hiddenZju())") >= 3
assert 'CalendarContract.Instances.CALENDAR_ID+" = ? AND "' in repository
assert 'week.plusWeeks(1).atStartOfDay(zone)' in browser
assert 'if(result.size()>PAGE_SIZE)break;' in repository
assert 'state == null || state.getBoolean("all", true)' in browser
assert 'afterStart = last.start; afterId = last.providerId;' in browser
assert 'startColumn+" ASC,"+idColumn+" ASC"' in repository
assert 'CourseAgenda.all(context,zone)' in repository
assert 'result.removeIf(source->source.count==0)' in repository
assert 'CalendarContract.Events.CONTENT_URI' in repository
assert 'if (importedCount != 0)' in settings
assert "generation != request" in browser
assert 'SettingsPageLayout.content(this)' in browser
assert 'SettingsPageLayout.header(this, root' in browser
assert 'SettingsPageLayout.show(this, root, state == null)' in browser
assert 'UiStyle.card(card)' in browser
assert 'title.setMaxLines(2)' in browser
assert 'setPadding(dp(16), dp(12), dp(16), dp(12))' in browser
assert "AlertDialog" not in settings + browser

# Run the production Entry date formatting on the JVM, including exclusive all-day end dates.
entry = browser[browser.index("    private static final class Entry {"):]
entry = entry[:entry.rfind("\n}")]
entry = entry.replace("private static final class Entry", "static final class Entry")
work = ROOT / "build/source-contents-check"
work.mkdir(parents=True, exist_ok=True)
source = """import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
public class SourceContentsCheck {
static class Intent {}
""" + entry + """
public static void main(String[] args) {
 TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
 long start = Instant.parse("2026-10-12T00:00:00Z").toEpochMilli();
 long end = Instant.parse("2026-10-13T00:00:00Z").toEpochMilli();
 Entry one = new Entry("课程", "", start, end, true, true, null);
 if (!one.time().equals("10月12日 周一 · 全天")) throw new AssertionError(one.time());
 Entry many = new Entry("课程", "", start, end + 86400000L, true, true, null);
 if (!many.time().equals("10月12日 周一 — 10月13日 周二 · 全天")) throw new AssertionError(many.time());
 Entry clock = new Entry("课程", "", start, start + 3600000L, false, false, null);
 if (!clock.time().equals("10月12日 周一 08:00 — 10月12日 周一 09:00")) throw new AssertionError(clock.time());
 if (!new Entry(null, null, start, end, false, false, null).title.equals("未命名日程")) throw new AssertionError();
 System.out.println("source contents: production date formatting passed");
}
}
"""
path = work / "SourceContentsCheck.java"
path.write_text(source, encoding="utf-8")
home = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21"))
subprocess.run([str(home / "bin/javac.exe"), "-encoding", "UTF-8", str(path)], check=True)
subprocess.run([str(home / "bin/java.exe"), "-cp", str(work), "SourceContentsCheck"], check=True)
print("source contents: row navigation, source isolation, gates, default-all paging and all-date empty-source contracts passed")
