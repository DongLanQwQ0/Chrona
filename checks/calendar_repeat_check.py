"""Execute provider recurrence-boundary checks with the existing Android SDK API stubs."""
from pathlib import Path
import os
import subprocess
from recurrence_dependencies import recurrence_java
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/calendar-repeat-check'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
SDK = Path(os.environ.get('ANDROID_HOME', 'F:/Android/Sdk')) / 'platforms/android-36/android.jar'
if not SDK.is_file():
    raise RuntimeError('Existing Android 36 platform JAR is required; nothing will be installed')
source = OUT / 'ScheduleSources.java'
source.write_text('package com.donglan.chrona; public class ScheduleSources { public static boolean allowsCalendar(android.content.Context context, long id) { return true; } }', encoding='utf-8')
SRC = ROOT / 'app/src/main/java/com/donglan/chrona'
sources = [SRC / name for name in ('calendar/CalendarStore.java', 'calendar/AllDayDates.java', 'calendar/CalendarOccurrence.java')]
sources += [source, ROOT / 'checks/CalendarRepeatCheck.java']
subprocess.run(recurrence_java([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(SDK), '-d', str(OUT), *map(str, sources)]), check=True)
subprocess.run(recurrence_java([str(JAVA / 'java.exe'), '-cp', str(OUT) + os.pathsep + str(SDK), 'com.donglan.chrona.calendar.CalendarRepeatCheck']), check=True)
