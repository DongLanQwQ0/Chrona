"""Offline test of the production LAN authorization implementation; no device or network."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
OUT = ROOT / "build/lan-security-checks"
OUT.mkdir(parents=True, exist_ok=True)
subprocess.run([str(JAVA / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(OUT),
                str(ROOT / "app/src/main/java/com/donglan/chrona/LanSecurity.java"),
                str(ROOT / "checks/LanSecurityCheck.java")], check=True)
subprocess.run([str(JAVA / "java.exe"), "-cp", str(OUT), "com.donglan.chrona.LanSecurityCheck"], check=True)
