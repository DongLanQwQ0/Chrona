"""Guard the filter sheet's label and spacing contract; no Android rendering is simulated."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/donglan/chrona/ScheduleFilterSheet.java").read_text(encoding="utf-8")
choice = source[source.index("        ChoiceDock("):source.index("        void select(")]

checks = 0


def require(condition, message):
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


require('label(choices[i], 14, true)' in choice, "Options retain their readable 14sp default")
require('option.setPadding(dp(4), dp(6), dp(4), dp(6))' in choice,
        "Every option has real horizontal and vertical text clearance")
require('option.setMinHeight(dp(44))' in choice, "Short labels retain a 44dp minimum target")
require('float labelWidth = option.getPaint().measureText(choices[i]) + option.getPaddingLeft() + option.getPaddingRight() + dp(8)' in choice,
        "Each option's measured text width and real clearance determine its allocation")
require('new LinearLayout.LayoutParams(0, dp(44), labelWidth)' in choice,
        "Text-based weights preserve one-line labels in a 44dp row")
add_item = choice.index('dock.addItem(option,')
for setting in ('option.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)',
                'option.setSingleLine(true)', 'option.setHorizontallyScrolling(false)'):
    require(setting in choice and choice.index(setting) > add_item,
            "Filter label policy must override shared addItem fitting: " + setting)
require('UiStyle.addSpaced(body, dock.motionHost(true), 0, 6)' in choice,
        "Dense filter rows allow animated highlights to overlap neighboring groups")
require('UiStyle.addSpaced(body, heading, 6, 2)' in choice,
        "Filter groups retain six-dp spacing without a full vertical motion envelope")
require('body.setPadding(dp(4), dp(8), dp(4), dp(18))' in source,
        "First and last filter groups retain viewport-edge safety padding")
dock = (root / "app/src/main/java/com/donglan/chrona/DockNavigationLayout.java").read_text(encoding="utf-8")
require('return motionHost(false);' in dock and 'int cross = overlapRows ? clearance' in dock
        and 'plate * 1.53f' in dock,
        "Overlap is opt-in while ordinary motion hosts retain their complete cross-axis envelope")
require('category.sync(draft.source == 1' in source
        and 'draft.source == 0);' in source
        and 'option.setEnabled(enabled)' in source,
        "System-source selection retains disabled filter semantics")
require('R.drawable.ic_chevron_left : R.drawable.ic_chevron_right' in source
        and 'arrow.setOnClickListener(view -> shiftInterval(rangeLabel, direction))' in source,
        "Date interval arrow actions remain available")
print(f"Schedule filter layout contract checks passed: {checks}; device rendering not exercised")
