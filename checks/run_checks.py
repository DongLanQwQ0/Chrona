"""Explicit offline suites: quick needs Python only; full also needs existing Java/JARs.

Run from any directory: python checks/run_checks.py [quick|full] [--list].
No device operations, Gradle builds, downloads, or paid API requests are performed.
Add completed harnesses to the named groups below, never discover tests implicitly.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
GROUPS = {
    "python": (
        "verify_migration.py", "verify_uncertainty_migration.py",
        "verify_end_provenance.py", "calendar_link_check.py",
        "widget_query_check.py", "data_integrity_check.py",
        "startup_metrics_check.py",
    ),
    "java": (
        "inbox_query_check.py", "schedule_query_check.py", "detail_draft_check.py",
        "job_execution_check.py", "dock_navigation_check.py",
        "lan_security_check.py", "edit_conflict_check.py",
        "lifecycle_motion_check.py", "automatic_update_check.py",
        "background_preference_check.py", "custom_color_check.py",
        "dialog_layers_check.py", "widget_danmaku_refresh_check.py",
        "home_filter_reference_check.py",
        "widget_source_failure_check.py",
        "widget_preferences_check.py",
    ),
    "cached_dependencies": (
        "config_backup_check.py", "timetable_check.py", "academic_terms_check.py",
    ),
}
SUITES = {"quick": ("python",), "full": tuple(GROUPS)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("suite", choices=SUITES, default="quick", nargs="?")
    parser.add_argument("--list", action="store_true", help="List checks without running them")
    args = parser.parse_args()
    selected = [(group, check) for group in SUITES[args.suite] for check in GROUPS[group]]
    if args.list:
        for group, check in selected:
            print(f"{group}: {check}")
        return 0
    if args.suite == "full":
        missing = [key for key in ("JAVA_HOME", "GRADLE_USER_HOME") if not os.environ.get(key)]
        if missing:
            print("Set existing toolchain paths: " + ", ".join(missing), file=sys.stderr)
            return 2
        if not (ROOT / "build/ai-checks/json.jar").is_file():
            print("Missing existing runnable JSON dependency: build/ai-checks/json.jar", file=sys.stderr)
            return 2
    failures = []
    started = time.monotonic()
    for group, check in selected:
        print(f"\n[{group}] {check}", flush=True)
        try:
            subprocess.run([sys.executable, str(ROOT / "checks" / check)], cwd=ROOT, check=True)
        except (OSError, subprocess.CalledProcessError) as error:
            failures.append(check)
            print(f"FAILED: {check}: {error}", file=sys.stderr, flush=True)
    print(f"\n{args.suite}: {len(selected) - len(failures)}/{len(selected)} passed "
          f"in {time.monotonic() - started:.1f}s", flush=True)
    if failures:
        print("Failed: " + ", ".join(failures), file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
