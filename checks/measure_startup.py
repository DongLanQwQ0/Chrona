"""Measure a connected device; never substitutes a placeholder frame for ready content.

Uses the already-installed APK. Force-stop interrupts any current app work, so run
on a test device after processing has finished. No installation or data clearing.
"""
import argparse
import json
import math
import re
import statistics
import subprocess
import time
from pathlib import Path

PACKAGE = "com.donglan.chrona"
COMPONENT = re.escape(PACKAGE) + r"/(?:\.|com\.donglan\.chrona\.)DashboardActivity(?=[:\s])"


def duration_ms(value):
    parts = re.fullmatch(r"\+?(?:(\d+)m)?(?:(\d+)s)?(?:(\d+)ms)?", value.strip())
    if not parts or not any(parts.groups()):
        return None
    minutes, seconds, millis = (int(part or 0) for part in parts.groups())
    return minutes * 60_000 + seconds * 1000 + millis


def summarize(samples, target):
    verified = [sample["fully_drawn_ms"] for sample in samples
                if sample.get("fully_drawn_ms") is not None]
    return {
        "samples": samples,
        "target_ms": target,
        "all_samples_verified": len(verified) == len(samples),
        "median_ms": statistics.median(verified) if verified else None,
        "p90_ms": sorted(verified)[math.ceil(len(verified) * .9) - 1] if verified else None,
        "max_ms": max(verified) if verified else None,
        "target_met_in_this_run": bool(verified) and len(verified) == len(samples)
        and max(verified) <= target,
    }


def measurement_from_logs(fresh, token):
    marker = next((line for line in fresh if "content_ready" in line
                   and re.search(r"\brun=" + str(token) + r"(?:\s|$)", line)), None)
    if marker is None:
        return None
    activity = re.search(r"activity_ms=(\d+)", marker)
    displays = [index for index, line in enumerate(fresh)
                if re.search(r"Displayed " + COMPONENT, line)]
    if activity is None or not displays:
        return None
    full = [line for line in fresh[displays[-1] + 1:]
            if re.search(r"Fully drawn " + COMPONENT, line)]
    if not full:
        return None
    duration = re.search(r":\s*(\+[0-9ms]+)\s*$", full[-1])
    system_ms = duration_ms(duration[1]) if duration else None
    activity_ms = int(activity[1])
    if system_ms is None or system_ms < activity_ms:
        return None
    return {"fully_drawn_ms": system_ms, "activity_content_ms": activity_ms}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, help="Path to the existing adb executable")
    parser.add_argument("--serial", required=True, help="Explicit test-device serial")
    parser.add_argument("--samples", type=int, default=10)
    parser.add_argument("--target-ms", type=int, default=1000)
    parser.add_argument("--allow-force-stop", action="store_true")
    parser.add_argument("--output", type=Path, default=Path("build/startup-device.json"))
    args = parser.parse_args()
    if not args.allow_force_stop:
        parser.error("--allow-force-stop is required: finish all active parsing first")
    if not 1 <= args.samples <= 100 or args.target_ms <= 0:
        parser.error("samples must be 1..100; target-ms must be positive")

    def adb(*command):
        result = subprocess.run([args.adb, "-s", args.serial, *command],
                                text=True, encoding="utf-8", errors="replace",
                                capture_output=True, timeout=40, check=True)
        return result.stdout

    def logs():
        return adb("logcat", "-d", "-v", "threadtime", "-t", "500",
                   "-s", "ActivityTaskManager:I", "ChronaStartup:I").splitlines()

    samples = []
    for index in range(args.samples):
        # Remove the old Activity/process before taking the log boundary, not inside start -S.
        adb("shell", "am", "force-stop", PACKAGE)
        before = set(logs())
        token = time.time_ns()
        output = adb("shell", "am", "start", "-W", "-n",
                     PACKAGE + "/.DashboardActivity", "-a", "android.intent.action.MAIN",
                     "-c", "android.intent.category.LAUNCHER", "--el",
                     "startup_measurement", str(token))
        if "Status: ok" not in output:
            raise RuntimeError("Activity launch failed: " + output.strip())
        sample = {"run": index + 1, "fully_drawn_ms": None}
        total = re.search(r"TotalTime:\s*(\d+)", output)
        sample["initial_display_ms"] = int(total[1]) if total else None
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            fresh = [line for line in logs() if line not in before]
            measured = measurement_from_logs(fresh, token)
            if measured:
                sample.update(measured)
                break
            time.sleep(.1)
        if sample["fully_drawn_ms"] is None:
            sample["error"] = "No matching ready-content and system Fully drawn pair; unverified"
        samples.append(sample)
        print(json.dumps(sample, ensure_ascii=False), flush=True)
    result = summarize(samples, args.target_ms)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "samples"},
                     ensure_ascii=False))
    return 0 if result["target_met_in_this_run"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
