"""Exercise actual internal system-event routing, including local display all-day conversion."""
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
page = (src / 'SystemAgendaDetailActivity.java').read_text(encoding='utf-8')
repository = (src / 'AgendaRepository.java').read_text(encoding='utf-8')
def method(source, marker):
    begin = source.index(marker)
    end = source.index('{', begin) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[begin:end].replace('android.net.Uri', 'Uri')
factory = method(page, '    static Intent intent(')
route = method(repository, '    static Intent details(')
harness = '''
import java.time.*;import java.util.*;
class Context{}class TaskDetailActivity{}
class Uri{String value;Uri(String s){value=s;}static Uri parse(String s){return new Uri(s);}}
class Intent{Class<?> target;Uri uri;Map<String,Object> extras=new HashMap<>();Intent(Context c,Class<?> type){target=type;}Intent putExtra(String key,Object value){extras.put(key,value);return this;}Intent setData(Uri u){uri=u;return this;}}
class AgendaEntry{boolean system,allDay;long id=42,taskId=7,start,end;String title="课程",location="教室",source="课表";}
class SystemAgendaDetailActivity{__FACTORY__}
public class SystemAgendaDetailsCheck{__ROUTE__
public static void main(String[] args){Context c=new Context();AgendaEntry e=new AgendaEntry();
Intent local=details(c,e);if(local.target!=TaskDetailActivity.class||!local.extras.get("task_id").equals(7L)||!local.extras.get("candidate_id").equals(42L))throw new AssertionError("local routing changed");
e.system=true;e.start=12345;e.end=67890;Intent system=details(c,e);
if(system.target!=SystemAgendaDetailActivity.class||!system.extras.get("beginTime").equals(e.start)||!system.extras.get("event_id").equals(42L)||!system.uri.value.endsWith("/42/12345"))throw new AssertionError("system routing");
for(String zone:new String[]{"Asia/Shanghai","America/New_York","Pacific/Auckland"}){
TimeZone.setDefault(TimeZone.getTimeZone(zone));e.allDay=true;LocalDate day=LocalDate.of(2026,10,14);
e.start=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();e.end=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
Intent allDay=details(c,e);if(!allDay.extras.get("beginTime").equals(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())||!allDay.extras.get("endTime").equals(day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))throw new AssertionError("all-day dates changed");}
System.out.println("System detail routes passed: local identity, occurrence identity, snapshots and all-day dates across 3 zones");}}
'''.replace('__FACTORY__', factory).replace('__ROUTE__', route)
out = root / 'build/system-agenda-details-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'SystemAgendaDetailsCheck.java'; file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'SystemAgendaDetailsCheck'], check=True)
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
a = '{http://schemas.android.com/apk/res/android}'
assert any(node.get(a+'name') == '.SystemAgendaDetailActivity' and node.get(a+'exported') == 'false' for node in manifest.iter('activity'))
assert '跳转至系统日程' in page and 'SettingsPageLayout.header' in page and 'SettingsPageLayout.show' in page
assert 'SystemAgendaDetailActivity.intent' in (src / 'DashboardActivity.java').read_text(encoding='utf-8')
