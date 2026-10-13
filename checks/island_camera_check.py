"""Verify production cutout padding and expanded-card title topology."""
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/AtomicAgendaCard.java').read_text(encoding='utf-8')
constants = '\n'.join(re.findall(r'private static final int (?:ISLAND_HEADER_SAFE_BOTTOM_DP|CAMERA_CLEARANCE_DP) = \d+;', source))
start = source.index('    static int cameraHeaderPaddingPixels(')
end = source.index('\n    static String marqueeConfiguration', start)
method = source[start:end]
fixture = '''public class IslandCameraCheck { __CONSTANTS__ __METHOD__
public static void main(String[] args){int checks=0;
for(float density:new float[]{1,1.5f,2,3,4})for(int bottomDp:new int[]{0,12,24,36,44,48,60,90}){
int bottom=Math.round(bottomDp*density),padding=cameraHeaderPaddingPixels(bottom,density);
if(padding<0||Math.round(44*density)+padding<bottom+Math.round(4*density))throw new AssertionError("title intersects cutout");
if(bottomDp<=36&&padding!=0)throw new AssertionError("unnecessary height");checks++;}
System.out.println("Camera header padding passed: "+checks+" density/cutout cases");}}
'''.replace('__CONSTANTS__', constants).replace('__METHOD__', method)
out = root / 'build/island-camera-check'; out.mkdir(parents=True, exist_ok=True)
file = out / 'IslandCameraCheck.java'; file.write_text(fixture, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'IslandCameraCheck'], check=True)
layout = ET.parse(root / 'app/src/main/res/layout/notification_atomic_card.xml')
a = '{http://schemas.android.com/apk/res/android}'
header = next(node for node in layout.iter() if node.get(a+'id') == '@+id/atomic_header')
parent = next(node for node in layout.iter() if header in list(node))
title = next(node for node in parent if node.get(a+'id') == '@+id/atomic_island_title')
icon = next(node for node in header if node.get(a+'id') == '@+id/atomic_icon')
minimum_dp = sum(int(value.removesuffix('dp')) for value in (
    parent.get(a+'paddingVertical'), icon.get(a+'layout_height'), title.get(a+'layout_marginTop')))
assert minimum_dp == int(re.search(r'ISLAND_HEADER_SAFE_BOTTOM_DP = (\d+)', source).group(1))
assert list(parent).index(title) > list(parent).index(header)
assert title.get(a+'layout_width') == 'match_parent' and title.get(a+'ellipsize') == 'end'
assert any(node.get(a+'id') == '@+id/atomic_camera_space' for node in header)
assert all(node.get(a+'id') != '@+id/atomic_island_title' for node in header.iter())
print('Expanded island title remains below the camera band; normal notification uses its own header')
