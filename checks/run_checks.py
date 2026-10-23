"""Explicit offline suites: quick needs Python only; full also needs existing Java/JARs, Node and PowerShell 7.

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
        "startup_metrics_check.py", "zju_homework_persistence_check.py",
    ),
    "java": (
        "fixture_isolation_check.py",
        "ui_library_check.py",
        "inbox_query_check.py", "schedule_query_check.py", "detail_draft_check.py", "detail_editor_check.py", "detail_carousel_restore_check.py",
        "job_execution_check.py", "restore_concurrency_check.py", "result_notification_title_check.py", "dock_navigation_check.py", "acrylic_translation_check.py", "calendar_build_frame_check.py", "calendar_slide_check.py", "press_feedback_check.py", "carousel_motion_check.py", "choice_motion_check.py",
        "lan_security_check.py", "todo_due_default_check.py", "edit_conflict_check.py", "settings_layout_check.py", "attachment_remove_check.py",
        "lifecycle_motion_check.py", "native_motion_core_check.py", "section_motion_check.py", "section_history_check.py",
        "automatic_update_check.py",
        "background_preference_check.py", "custom_color_check.py", "accent_palette_cache_check.py", "corner_scale_check.py",
        "dialog_layers_check.py", "dialog_scale_check.py", "widget_danmaku_refresh_check.py",
        "home_filter_reference_check.py", "sync_settings_check.py",
        "widget_source_failure_check.py",
        "widget_preferences_check.py",
        "zju_sync_cadence_check.py",
        "agenda_features_check.py", "agenda_notification_check.py", "agenda_slots_check.py", "reminder_preferences_check.py", "live_reminder_service_check.py", "completion_alerts_check.py", "notification_delivery_status_check.py",
        "calendar_review_check.py", "week_calendar_check.py", "week_time_scale_check.py", "week_export_check.py", "launcher_shortcuts_check.py",
        "dashboard_switch_check.py", "dashboard_dock_chrome_check.py", "schedule_refresh_frame_check.py", "review_badge_check.py", "vivo_atomic_check.py", "atomic_capsule_check.py", "atomic_capsule_observer_check.py", "calendar_event_build_check.py",
        "calendar_visibility_toggle_check.py", "course_matching_check.py",
        "completion_card_motion_check.py", "schedule_filter_layout_check.py", "source_contents_check.py", "zju_ui_style_check.py",
    ),
    "cached_dependencies": (
        "classroom_summary_check.py", "classroom_client_check.py", "classroom_images_check.py", "classroom_work_service_check.py", "zju_timetable_view_check.py",
        "repeat_ai_check.py", "repeat_projection_check.py", "calendar_repeat_check.py",
        "config_backup_check.py", "backup_restore_check.py", "linked_calendar_events_check.py", "timetable_check.py", "academic_terms_check.py", "lan_qr_check.py", "candidate_merge_check.py",
        "sync_core_check.py", "sync_lock_check.py", "lan_sync_api_check.py", "lan_sources_check.py", "lan_attachment_upload_check.py",
        "atomic_card_contract_check.py", "island_camera_check.py",
        "ai_auto_publish_check.py", "system_agenda_details_check.py",
        "school_data_backup_check.py", "schedule_sources_backup_check.py",
        "zju_calendar_check.py", "zju_sources_check.py",
        "zju_homework_update_check.py", "zju_sync_recovery_check.py", "course_calendar_export_check.py", "homework_links_check.py", "bulk_calendar_publish_check.py",
    ),
    "javascript": ("lan_dom_reuse_check.py", "lan_approval_flow_check.py"),
    "powershell": ("vivo_provision_check.py",),
}
# Explicit evidence/browser tools stay outside these dependency-bounded suites:
# interaction_acceptance_check.py needs two candidate-specific Android reports.
# web_parity_check.py launches installed Edge, beyond full's Node-only JS checks.
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
