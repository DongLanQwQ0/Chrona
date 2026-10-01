"""Offline validation of the device report parser; does not measure app startup."""
from measure_startup import duration_ms, summarize, measurement_from_logs

assert duration_ms("+932ms") == 932
assert duration_ms("+1s24ms") == 1024
assert duration_ms("+1m2s3ms") == 62003
assert duration_ms("unknown") is None
assert duration_ms("") is None
assert summarize([{"fully_drawn_ms": 932}], 1000)["target_met_in_this_run"]
assert not summarize([{"fully_drawn_ms": None}], 1000)["target_met_in_this_run"]
assert not summarize([{"fully_drawn_ms": 700}, {"fully_drawn_ms": None}], 1000)["target_met_in_this_run"]
assert not summarize([{"fully_drawn_ms": 700}, {"fully_drawn_ms": 1100}], 1000)["target_met_in_this_run"]
assert summarize([{"fully_drawn_ms": value} for value in range(100, 1100, 100)], 1000)["p90_ms"] == 900
display = "Displayed com.donglan.chrona/.DashboardActivity: +300ms"
ready = "ChronaStartup: content_ready activity_ms=500 run=123"
full = "Fully drawn com.donglan.chrona/.DashboardActivity: +700ms"
assert measurement_from_logs([display, full, ready], 123)["fully_drawn_ms"] == 700
assert measurement_from_logs([full, display, ready], 123) is None  # Old result before this launch.
assert measurement_from_logs([display, full, ready], 12) is None  # Different launch token.
assert measurement_from_logs([display, full.replace("DashboardActivity", "GuideActivity"), ready], 123) is None
assert measurement_from_logs([display, full.replace("700ms", "400ms"), ready], 123) is None
assert measurement_from_logs([full, ready], 123) is None  # No matching initial display.
print("Startup metric parser checks passed; no device timing measured")
