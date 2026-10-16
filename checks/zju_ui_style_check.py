"""Check changed native UI contracts; this does not simulate Android rendering."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
pages = ['ZjuHomeworkHistoryActivity', 'CourseSourceMatchingActivity', 'ZjuCalendarSettingsActivity',
         'ScheduleSourceSettingsActivity', 'ScheduleSourceContentsActivity', 'ExternalCourseDetailActivity']
for page in pages:
    text = (src / (page + '.java')).read_text(encoding='utf-8')
    assert 'AlertDialog' not in text, page + ': default platform modal'
    assert 'SettingsPageLayout.content(this)' in text and 'SettingsPageLayout.header(this,' in text, page
history = (src / 'ZjuHomeworkHistoryActivity.java').read_text(encoding='utf-8')
assert 'setPadding(16,16,16,16)' not in history
assert 'UiStyle.card(card); card.setPadding(dp(16), dp(12), dp(12), dp(12))' in history
assert 'title.setMaxLines(2)' in history and 'title.setEllipsize(TextUtils.TruncateAt.END)' in history
assert 'if (!day.equals(lastDay))' in history
assert 'created && key.equals("title")' in history
assert 'if (!created)' in history and '状态未知' in history
assert 'UiStyle.dialogPanel(this, record.title)' in history and 'UiStyle.dialogBody(panel, body)' in history
assert 'UiStyle.showFloatingDialog(dialog, panel)' in history
probe = (src / 'CourseSourceProbe.java').read_text(encoding='utf-8')
assert 'if(settings.enabled())for(' in probe
matching = (src / 'CourseSourceMatchingActivity.java').read_text(encoding='utf-8')
assert 'rules.keySet().removeIf' in matching and 'if(!new ZjuSettingsStore(this).enabled())' in matching
detail = (src / 'ExternalCourseDetailActivity.java').read_text(encoding='utf-8')
assert 'if(schoolSource&&!new ZjuSettingsStore(this).enabled()){finish();return;}' in detail
assert 'sourceIntent(android.content.Context context,String source,String key,long start,long end)' in detail
assert '.load(settings.account())' in detail
style = (src / 'UiStyle.java').read_text(encoding='utf-8')
assert 'static LinearLayout dialogPanel' in style and 'static void dialogBody' in style
assert 'MeasureSpec.AT_MOST' in style and 'scroll.setVerticalScrollBarEnabled(false)' in style
assert (root / 'docs/ui-style-contract.md').is_file()
assert 'ui-style-contract.md' in (root / 'docs/development.md').read_text(encoding='utf-8')
print('Native UI style contract: changed pages, theme dialogs, density spacing, overflow, feature gates passed; rendering not exercised')
