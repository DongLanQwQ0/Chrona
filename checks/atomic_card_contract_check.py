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
progress_start = source.index('    private static Bitmap progress(')
progress_end = source.index('\n    private static Bitmap dot(', progress_start)
dot_end = source.index('\n    private static Bitmap icon(', progress_end)
mix_start = source.index('    private static int mix(')
mix_end = source.index('\n    private AtomicAgendaCard()', mix_start)
graphics = '''
class Color {static int red(int c){return (c>>>16)&255;}static int green(int c){return (c>>>8)&255;}static int blue(int c){return c&255;}static int rgb(int r,int g,int b){return 0xff000000|(r<<16)|(g<<8)|b;}}
class Paint {enum Style{FILL,STROKE}Style style=Style.FILL;static final int ANTI_ALIAS_FLAG=1;int color;Paint(int flags){}void setColor(int c){color=c;}void setStyle(Style s){style=s;}}
class Bitmap {boolean solidDot;enum Config{ARGB_8888}java.util.List<float[]> rects=new java.util.ArrayList<>();java.util.List<Integer> colors=new java.util.ArrayList<>();static Bitmap createBitmap(int w,int h,Config c){return new Bitmap();}}
class Canvas {Bitmap image;Canvas(Bitmap b){image=b;}void drawCircle(float x,float y,float r,Paint p){image.solidDot=p.style==Paint.Style.FILL&&(p.color>>>24)==255;}void drawRect(float l,float t,float r,float b,Paint p){image.rects.add(new float[]{l,t,r,b});image.colors.add(p.color);}}
'''
symbols = root / 'app/build/intermediates/runtime_symbol_list/release/processReleaseResources/R.txt'
ids = {}
for line in symbols.read_text(encoding='utf-8').splitlines():
    match = re.fullmatch(r'int id (atomic_ticker_\d+) (0x[0-9a-f]+)', line)
    if match:
        ids[match[1]] = int(match[2], 16)
assert len(ids) == 6
resources = ''.join(f'static int {name}={value};' for name, value in ids.items())
harness = (graphics + 'class ValueAnimator {static boolean enabled=true;static boolean areAnimatorsEnabled(){return enabled;}}'
           'class R {static class id {' + resources + '}}'
           'public class AtomicCardContractCheck {' + array + method + location_method + source[progress_start:dot_end] + source[mix_start:mix_end] +
           'public static void main(String[] args){'
           'for(String empty:new String[]{null,"","  ","\\t\\n"})'
           'if(!locationText(empty).equals("在你手里~"))throw new AssertionError("empty location");'
           'if(!locationText(" 教室 ").equals("教室"))throw new AssertionError("real location");'
           'for(int color:new int[]{0xffff8800,0xff9068e8,0xff315ca7})if(!dot(color).solidDot)throw new AssertionError("endpoint must be opaque and filled");'
           'for(double ratio:new double[]{0,.1,.5,.95,1}){Bitmap p=progress(0xffff8800,0xff101015,ratio);'
           'if((p.colors.get(0)>>>24)!=255||p.colors.get(0)==0xff101015)throw new AssertionError("invisible remaining track");'
           'if(p.rects.get(0)[2]!=320||p.rects.get(1)[2]!=(float)(320*ratio))throw new AssertionError("progress endpoint");}'
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
assert 'R.id.atomic_to_dot, dot(palette.primary)' in source, 'Right endpoint must not depend on progress'
# Endpoint centres and the track edges share the same inset.
nodes = {node.get(android + 'id'): node for node in layout.iter()}
track = nodes['@+id/atomic_progress']
for side, dot_id in [('Left', 'atomic_from_dot'), ('Right', 'atomic_to_dot')]:
    dot = nodes['@+id/' + dot_id]
    value = lambda node, attr: float(node.get(android + attr).removesuffix('dp'))
    assert value(track, 'layout_margin' + side) == value(dot, 'layout_margin' + side) + value(dot, 'layout_width') / 2
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
