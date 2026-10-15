"""Validate saved Android interaction evidence; never starts ADB or changes a device.

Capture the two phases with the existing instrumentation runner after installation:
  adb shell am instrument -w -e check ui -e phase interaction_acceptance com.donglan.chrona.test/com.donglan.chrona.LanAccessInstrumentation
  adb shell am instrument -w -e check ui -e phase interaction_layout com.donglan.chrona.test/com.donglan.chrona.LanAccessInstrumentation
Save each output separately, then pass both paths to this script. Direct ui_report
JSON is also accepted. The Activity-opening measurement is instance-cache cold,
not process/storage cold. Worker completion is an observation, not SQL timing.
"""
import argparse
import json
import math
from pathlib import Path


def report(path):
    raw = path.read_text(encoding="utf-8-sig")
    marker = "INSTRUMENTATION_RESULT: ui_report="
    if marker in raw:
        if "INSTRUMENTATION_CODE: -1" not in raw:
            raise ValueError(f"{path}: instrumentation did not finish successfully")
        raw = raw.split(marker, 1)[1]
    value, _ = json.JSONDecoder().raw_decode(raw.lstrip())
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected a ui_report object")
    return value


def require(value, label):
    if not value:
        raise ValueError(label)


def number(case, key):
    value = case.get(key)
    require(isinstance(value, (int, float)) and not isinstance(value, bool)
            and math.isfinite(value) and value >= 0, f"Invalid {key}: {value}")
    return value


def validate(interaction, layout, args):
    require(interaction.get("phase") == "interaction_acceptance", "Wrong interaction phase")
    require(layout.get("phase") == "interaction_layout", "Wrong layout phase")
    for field in ("version", "model", "sdk"):
        require(interaction.get(field) is not None and interaction[field] == layout.get(field),
                f"Reports are from different candidates/devices: {field}")
    required = {"cold_activity_open", "uncached_mode_switch", "cached_mode_return",
                "long_hold_and_drag_out", "rapid_reverse_dock"}
    cases = interaction.get("cases", [])
    by_name = {item.get("case"): item for item in cases}
    require(len(cases) == len(required) and set(by_name) == required, "Missing/duplicate interaction scenarios")
    cold = by_name["cold_activity_open"]
    require(cold.get("emptyInstanceCache") is True and cold.get("processCold") is False,
            "Opening evidence must identify the empty Activity cache, not claim process cold")
    require(by_name["cached_mode_return"].get("cacheHitVerified") is True, "Cached return did not reuse its snapshot")
    press = by_name["long_hold_and_drag_out"]
    for key in ("dragOutDidNotClick", "releaseReset", "nextTapWorked"):
        require(press.get(key) is True, f"Press scenario failed: {key}")
    require(number(press, "holdMs") >= 700, "Hold interval too short")
    for key in ("heldScaleFirst", "heldScaleSecond"):
        require(number(press, key) >= 1.045, f"Held scale did not persist: {key}")
    reverse = by_name["rapid_reverse_dock"]
    require(reverse.get("acknowledgedModes") == [4, 0, 4, 0, 4, 0]
            and reverse.get("finalMode") == 0 and reverse.get("highlightSettled") is True,
            "Dock reversals lost an input or did not settle")
    for name, case in by_name.items():
        frames = case.get("frameMetrics", {})
        require(frames.get("measurementAvailable") is True and number(frames, "frames") > 0,
                f"{name}: no usable device frame measurements")
        require(number(frames, "lostMetricCallbacks") == 0, f"{name}: frame callbacks were dropped; capture again")
        p95 = number(frames, "p95Ms")
        if args.max_frame_p95_ms is not None:
            require(p95 <= args.max_frame_p95_ms, f"{name}: frame p95 exceeds the supplied budget")
        detail = f"{name}: frame p95={p95:.2f}ms, max={number(frames, 'maxMs'):.2f}ms"
        if "workerCompleteObservedMs" in case:
            worker, ui = number(case, "workerCompleteObservedMs"), number(case, "uiCompleteObservedMs")
            require(case.get("observationIntervalMs") == 10, f"{name}: unknown observation interval")
            if args.max_worker_observed_ms is not None:
                require(worker <= args.max_worker_observed_ms, f"{name}: worker observation exceeds the supplied budget")
            if args.max_ui_ms is not None:
                require(ui <= args.max_ui_ms, f"{name}: UI completion exceeds the supplied budget")
            detail += f", worker observed={worker:.0f}ms, UI complete={ui:.0f}ms"
        print(detail)
    expected = {(w, h, f, n) for w, h in ((320, 568), (360, 640), (640, 360))
                for f in (1, 1.5, 2) for n in (2, 3, 5)}
    matrix = layout.get("cases", [])
    actual = {(item.get("widthDp"), item.get("heightDp"), item.get("fontScale"), item.get("optionCount")) for item in matrix}
    require(len(matrix) == len(expected) and actual == expected, "Incomplete configuration-local layout matrix")
    require(layout.get("systemSettingsChanged") is False and layout.get("fullActivityLayout") is False,
            "Layout evidence must identify its configuration-local component scope")
    for item in matrix:
        require(item.get("viewportClipPreserved") is True and item.get("textFits") is True and item.get("image"),
                "Configuration render/clip/text evidence missing")
        edges = item.get("edges", [])
        require([edge.get("edge") for edge in edges] == [0, item["optionCount"] - 1], "Missing held Dock edge")
        require(all(edge.get("longPressFrames", 0) >= 240 and edge.get("flingFrames", 0) >= 480
                    and edge.get("limitingEnvelopeFits") is True for edge in edges), "Incomplete selection-bar envelope evidence")
    print("PASS: interaction behavior, configuration-local component geometry, and device measurement completeness")
    if any(value is None for value in (args.max_frame_p95_ms, args.max_worker_observed_ms, args.max_ui_ms)):
        print("Performance acceptance pending: supply the agreed timing budgets; this result does not establish FPS.")
    print("Visual acceptance is separate: inspect rendered PNGs and full-page landscape/large-font behavior on the device.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("interaction_report", type=Path)
    parser.add_argument("layout_report", type=Path)
    parser.add_argument("--max-frame-p95-ms", type=float)
    parser.add_argument("--max-worker-observed-ms", type=float)
    parser.add_argument("--max-ui-ms", type=float)
    args = parser.parse_args()
    for field in ("max_frame_p95_ms", "max_worker_observed_ms", "max_ui_ms"):
        value = getattr(args, field)
        if value is not None and (not math.isfinite(value) or value <= 0):
            parser.error(f"--{field.replace('_', '-')} must be positive and finite")
    try:
        validate(report(args.interaction_report), report(args.layout_report), args)
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"FAIL: {error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
