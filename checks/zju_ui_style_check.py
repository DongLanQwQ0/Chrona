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
assert 'UiDialogs.dialogPanel(this, record.title)' in history and 'UiDialogs.dialogBody(panel, body)' in history
assert 'UiDialogs.showFloatingDialog(dialog, panel)' in history
probe = (src / 'CourseSourceProbe.java').read_text(encoding='utf-8')
assert 'if(settings.enabled())for(' in probe
matching = (src / 'CourseSourceMatchingActivity.java').read_text(encoding='utf-8')
assert 'rules.keySet().removeIf' in matching and 'if(!new ZjuSettingsStore(this).enabled())' in matching
detail = (src / 'ExternalCourseDetailActivity.java').read_text(encoding='utf-8')
assert 'if(schoolSource&&!new ZjuSettingsStore(this).enabled()){finish();return;}' in detail
assert 'sourceIntent(android.content.Context context,String source,String key,long start,long end)' in detail
assert '.load(settings.account())' in detail
assert 'ZjuSync.WORKER' not in detail and '"course-detail-local-reader").start()' in detail
assert 'if(!item.description.isBlank())detail(card,item.description);' in detail
style = (src / 'UiDialogs.java').read_text(encoding='utf-8')
assert 'static LinearLayout dialogPanel' in style and 'static ScrollView dialogBody' in style
assert 'MeasureSpec.AT_MOST' in style and 'scroll.setVerticalScrollBarEnabled(false)' in style
assert (root / 'docs/ui-style-contract.md').is_file()
assert 'ui-style-contract.md' in (root / 'docs/development.md').read_text(encoding='utf-8')
school = (src / 'SchoolSettingsActivity.java').read_text(encoding='utf-8')
hub = (src / 'ZjuSettingsActivity.java').read_text(encoding='utf-8')
pta = (src / 'PtaSettingsActivity.java').read_text(encoding='utf-8')
assert 'R.drawable.ic_refresh' not in school and 'R.drawable.ic_refresh' not in pta
assert 'R.drawable.ic_refresh' in hub and 'ZjuSync.request(this,' in hub
assert 'SettingsPageLayout.row(this,group,"账号与同步"' not in school
assert 'SettingsPageLayout.row(this,identity,"账号与同步"' in hub
assert 'params.setMargins(dp(16),dp(group.getChildCount()==0?12:4),dp(16),dp(8))' in school
assert 'UiStyle.input(f)' in school and 'status=new TextView' not in school
assert 'ZjuSync.requestSchool(this,' in school and 'Feedback.show(this,r)' in school
task = (src / 'TaskDetailActivity.java').read_text(encoding='utf-8')
assert 'new LinearLayout.LayoutParams(-2,dp(48));compact.setMarginEnd(dp(8))' in task
assert 'candidateActions.addView(mobile,0,compact)' in task
login = (src / 'PtaLoginActivity.java').read_text(encoding='utf-8')
tongge = (src / 'ScheduleCompareActivity.java').read_text(encoding='utf-8')
assert 'UiSurfaces.clipRounded(website,UiStyle.RADIUS_CARD)' in login
assert 'UiSurfaces.clipRounded(website, UiStyle.RADIUS_CARD)' in tongge
assert 'website.addView(web,new FrameLayout.LayoutParams(-1,-1))' in login
assert 'root.addView(web,' not in login and 'private void round(' not in tongge
surfaces = (src / 'UiSurfaces.java').read_text(encoding='utf-8')
assert 'static void clipRounded(View view,int radiusKind)' in surfaces
assert 'view.setClipToOutline(true)' in surfaces
print('Native UI style contract: changed pages, theme dialogs, density spacing, overflow, feature gates passed; rendering not exercised')
