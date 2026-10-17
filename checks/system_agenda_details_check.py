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
refresh = method(page, '    private void refreshAcknowledgement()').replace('private void', 'void').replace('android.view.View.', 'View.')
course = (src / 'ExternalCourseDetailActivity.java').read_text(encoding='utf-8')
detail = (src / 'TaskDetailActivity.java').read_text(encoding='utf-8')
for activity in (page, course):
    assert 'expiryHandler.postDelayed(expiryRefresh, remaining)' in activity or 'expiryHandler.postDelayed(expiryRefresh,remaining)' in activity
    assert 'expiryHandler.removeCallbacks(expiryRefresh)' in activity
assert 'confirm.setVisibility(item.endMillis>System.currentTimeMillis()?android.view.View.VISIBLE:android.view.View.GONE)' in course
assert '? View.VISIBLE : View.GONE' in method(detail, '    private void refreshAcknowledgementActions(')
harness = '''
import java.time.*;import java.util.*;
class Context{}class TaskDetailActivity{}
class Uri{String value;Uri(String s){value=s;}static Uri parse(String s){return new Uri(s);}}
class Intent{Class<?> target;Uri uri;Map<String,Object> extras=new HashMap<>();Intent(Context c,Class<?> type){target=type;}Intent putExtra(String key,Object value){extras.put(key,value);return this;}Intent setData(Uri u){uri=u;return this;}boolean getBooleanExtra(String key,boolean fallback){return (boolean)extras.getOrDefault(key,fallback);}}
class AgendaEntry{boolean system,allDay;long id=42,taskId=7,start,end;String title="课程",location="教室",source="课表",description="第6节备注\\n第7-8节备注";}
class View{static final int VISIBLE=0,GONE=8;}
class Button{int visibility;boolean enabled;String text;void setVisibility(int value){visibility=value;}void setEnabled(boolean value){enabled=value;}void setText(String value){text=value;}}
class Handler{long delay;void removeCallbacks(Runnable r){delay=0;}void postDelayed(Runnable r,long millis){delay=millis;}}
class AgendaAcknowledgement{static boolean acknowledged;static boolean isAcknowledged(Object context,String key){return acknowledged;}}
class SystemAgendaDetailActivity{
Button acknowledgement=new Button();String occurrenceKey="key";long acknowledgementUntil;boolean occurrenceAvailable=true,resumed=true;
Handler expiryHandler=new Handler();Runnable expiryRefresh=this::refreshAcknowledgement;Intent input=new Intent(new Context(),getClass());Intent getIntent(){return input;}
__FACTORY__ __REFRESH__}
public class SystemAgendaDetailsCheck{__ROUTE__
public static void main(String[] args){Context c=new Context();AgendaEntry e=new AgendaEntry();
Intent local=details(c,e);if(local.target!=TaskDetailActivity.class||!local.extras.get("task_id").equals(7L)||!local.extras.get("candidate_id").equals(42L))throw new AssertionError("local routing changed");
e.system=true;e.start=12345;e.end=67890;Intent system=details(c,e);
if(!e.description.equals(system.extras.get("description")))throw new AssertionError("merged course notes lost in detail routing");
if(system.target!=SystemAgendaDetailActivity.class||!system.extras.get("beginTime").equals(e.start)||!system.extras.get("event_id").equals(42L)||!system.uri.value.endsWith("/42/12345"))throw new AssertionError("system routing");
for(String zone:new String[]{"Asia/Shanghai","America/New_York","Pacific/Auckland"}){
TimeZone.setDefault(TimeZone.getTimeZone(zone));e.allDay=true;LocalDate day=LocalDate.of(2026,10,14);
e.start=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();e.end=day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
Intent allDay=details(c,e);if(!allDay.extras.get("beginTime").equals(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())||!allDay.extras.get("endTime").equals(day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))throw new AssertionError("all-day dates changed");}
SystemAgendaDetailActivity page=new SystemAgendaDetailActivity();page.acknowledgementUntil=System.currentTimeMillis()+60_000L;page.refreshAcknowledgement();
if(page.acknowledgement.visibility!=View.VISIBLE||!page.acknowledgement.enabled||page.expiryHandler.delay<=0)throw new AssertionError("future activity visible and expiry scheduled");
AgendaAcknowledgement.acknowledged=true;page.refreshAcknowledgement();if(!page.acknowledgement.text.equals("取消确认"))throw new AssertionError("cancel confirmation label");
page.acknowledgementUntil=System.currentTimeMillis()-1;page.refreshAcknowledgement();if(page.acknowledgement.visibility!=View.GONE||page.expiryHandler.delay!=0)throw new AssertionError("expiry hides without layout space");
page.acknowledgementUntil=0;page.refreshAcknowledgement();if(page.acknowledgement.visibility!=View.GONE)throw new AssertionError("undated hidden");
page.acknowledgementUntil=System.currentTimeMillis()+60_000L;page.input.putExtra("source_series",true);page.refreshAcknowledgement();if(page.acknowledgement.visibility!=View.GONE)throw new AssertionError("source series stays hidden after refresh");
page.input.putExtra("source_series",false);page.occurrenceAvailable=false;page.refreshAcknowledgement();if(page.acknowledgement.visibility!=View.GONE)throw new AssertionError("unavailable occurrence hidden");
System.out.println("System detail routes passed: local identity, occurrence identity, snapshots and all-day dates across 3 zones");}}
'''.replace('__FACTORY__', factory).replace('__ROUTE__', route).replace('__REFRESH__', refresh)
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
