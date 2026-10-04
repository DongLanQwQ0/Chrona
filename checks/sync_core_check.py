"""Offline Java sync checks; uses Gradle's exported runtime classpath or an override."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
OUT = ROOT / "build" / "sync-core-checks"
OUT.mkdir(parents=True, exist_ok=True)
classpath = os.environ.get("CHRONA_SYNC_CLASSPATH")
if not classpath:
    cache = Path(os.environ.get("GRADLE_USER_HOME", "F:/Android/GradleCache")) / "caches/modules-2/files-2.1"
    jars = [ROOT / "build/ai-checks/json.jar"]
    for group, module in (("com.squareup.okhttp3", "okhttp"), ("com.squareup.okio", "okio-jvm"),
                          ("org.jetbrains.kotlin", "kotlin-stdlib"), ("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
                          ("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")):
        candidates = sorted(path for path in (cache / group / module).glob("*/*/*.jar")
                            if not path.name.endswith(("-sources.jar", "-javadoc.jar")))
        if candidates:
            jars.append(candidates[-1])
    if not all(path.is_file() for path in jars) or len(jars) < 4:
        raise SystemExit("Set CHRONA_SYNC_CLASSPATH to existing org.json, okhttp, okio and Kotlin runtime jars")
    classpath = os.pathsep.join(map(str, jars))
sources = [str(ROOT / "shared/src/main/java/com/donglan/chrona/sync" / name) for name in ("SyncState.java", "WebDavClient.java")]
subprocess.run([str(JAVA / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-cp", classpath,
                "-d", str(OUT), *sources, str(ROOT / "checks/SyncCoreCheck.java")], check=True)
subprocess.run([str(JAVA / "java.exe"), "-cp", str(OUT) + os.pathsep + classpath, "SyncCoreCheck"], check=True)
