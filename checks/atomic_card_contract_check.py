"""Validate the production marquee payload against actual compiled resource IDs/layout."""
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/AtomicAgendaCard.java').read_text(encoding='utf-8')
array = re.search(r'private static final int\[\] TICKER_IDS = .*?;', source, re.S).group()
start = source.index('    static String marqueeConfiguration()')
end = source.index('\n    private static Bitmap progress', start)
method = source[start:end]
location_start = source.index('    static String locationText(')
location_end = source.index('\n    private static int cameraTopPadding', location_start)
location_method = source[location_start:location_end]
symbols = root / 'app/build/intermediates/runtime_symbol_list/release/processReleaseResources/R.txt'
ids = {}
for line in symbols.read_text(encoding='utf-8').splitlines():
    match = re.fullmatch(r'int id (atomic_ticker_\d+) (0x[0-9a-f]+)', line)
    if match:
        ids[match[1]] = int(match[2], 16)
assert len(ids) == 6
resources = ''.join(f'static int {name}={value};' for name, value in ids.items())
harness = ('class ValueAnimator {static boolean enabled=true;static boolean areAnimatorsEnabled(){return enabled;}}'
           'class R {static class id {' + resources + '}}'
           'public class AtomicCardContractCheck {' + array + method + location_method +
           'public static void main(String[] args){'
           'for(String empty:new String[]{null,"","  ","\\t\\n"})'
           'if(!locationText(empty).equals("在你手里~"))throw new AssertionError("empty location");'
           'if(!locationText(" 教室 ").equals("教室"))throw new AssertionError("real location");'
           'System.out.println(marqueeConfiguration());'
           'ValueAnimator.enabled=false;System.out.println(marqueeConfiguration());}}')
out = root / 'build/atomic-card-contract-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'AtomicCardContractCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
lines = subprocess.check_output([str(java / 'java.exe'), '-cp', str(out), 'AtomicCardContractCheck'], text=True).splitlines()
animations = json.loads(lines[0])['animations']
assert json.loads(lines[1]) == {'animations': []}
layout = ET.parse(root / 'app/src/main/res/layout/notification_atomic_card.xml')
android = '{http://schemas.android.com/apk/res/android}'
text_ids = {node.attrib.get(android + 'id', '').removeprefix('@+id/') for node in layout.iter('TextView')}
assert set(ids) <= text_ids
assert len(animations) == 12
assert {(item['elementId'], item['displayLocation']) for item in animations} == {
    (value, location) for value in ids.values() for location in (1, 2)}
for item in animations:
    assert item['animationType'] == 1
    properties = {p['propertyKey']: (p['propertyType'], p['propertyValue']) for p in item['animationProperties']}
    assert properties == {'widget_marquee_repeat_count': (1, -1),
                          'widget_marquee_during_time': (2, 0),
                          'widget_marquee_ellipsize_type': (1, 0)}
assert 'removeAllViews' not in source, 'Reapply must retain animated child identity'
assert 'setAutoStart' not in source, 'ViewFlipper.setAutoStart is not remotely callable'
# The outgoing background must begin at the incoming background's final geometry;
# hold that geometry between flips so the enlarged image never snaps back to its base size.
animations_dir = root / 'app/src/main/res/anim'
incoming = ET.parse(animations_dir / 'atomic_background_in.xml').getroot()
outgoing = ET.parse(animations_dir / 'atomic_background_out.xml').getroot()
for animation in (incoming, outgoing):
    assert animation.attrib[android + 'fillAfter'] == 'true'
    assert all(int(node.attrib[android + 'duration']) == 16000 for node in animation)
for tag, properties in [('scale', ('XScale', 'YScale')), ('translate', ('XDelta', 'YDelta'))]:
    first, second = incoming.find(tag), outgoing.find(tag)
    for prop in properties:
        assert first.attrib[android + 'to' + prop] == second.attrib[android + 'from' + prop]
        assert second.attrib[android + 'to' + prop] == first.attrib[android + 'from' + prop]
flippers = {node.attrib[android + 'id']: node for node in layout.iter('ViewFlipper')}
for suffix in ('in', 'out'):
    assert flippers['@+id/atomic_ticker'].attrib[android + suffix + 'Animation'] == '@anim/atomic_ticker_' + suffix
    transition = ET.parse(animations_dir / ('atomic_ticker_' + suffix + '.xml')).getroot()
    assert transition.find('alpha') is not None and transition.find('translate') is not None
    assert all(0 < int(node.attrib[android + 'duration']) <= 600 for node in transition)
print('Atomic card contract passed: compiled IDs, both hosts, empty location, continuous background geometry and smooth ticker transitions')
