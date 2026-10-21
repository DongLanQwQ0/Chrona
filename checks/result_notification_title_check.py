"""Execute the production result-title selection without network or calendar writes."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/processing/ProcessingJobService.java').read_text(encoding='utf-8')
start = source.index('List<com.donglan.chrona.data.EventCandidate> written =')
end = source.index('int count = 0;', start)
selection = source[start:end].replace('com.donglan.chrona.data.EventCandidate', 'Item')
out = root / 'build/result-notification-title-check'
out.mkdir(parents=True, exist_ok=True)
harness = '''import java.util.*;
public class ResultNotificationTitleCheck {
 record Item(String title,Long calendarEventId,boolean isRecord){}
 static String title(List<Item> candidates){String title="处理结果";SELECTION return title;}
 static void check(String expected,List<Item> rows){if(!title(rows).equals(expected))throw new AssertionError(title(rows));}
 public static void main(String[]args){
 check("处理结果",List.of());
 check("微积分讨论",List.of(new Item("微积分讨论",null,false)));
 check("已写入的活动",List.of(new Item("待确认草稿",null,false),new Item("已写入的活动",1L,false)));
 check("第一项 等 2 项",List.of(new Item("草稿",null,false),new Item("第一项",1L,false),new Item("第二项",2L,false)));
 check("普通事项 等 2 项",List.of(new Item("普通事项",null,false),new Item("记录",null,true)));
 check("活动",List.of(new Item("旧记录",3L,true),new Item("活动",4L,false)));
 System.out.println("PASS: 6 production result-title selection checks");
 }}'''.replace('SELECTION', selection.replace('item.calendarEventId', 'item.calendarEventId()').replace('item.isRecord', 'item.isRecord()').replace('named.get(0).title', 'named.get(0).title()'))
file = out / 'ResultNotificationTitleCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'ResultNotificationTitleCheck'], check=True)
