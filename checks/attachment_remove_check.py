"""Execute the real attachment constructor, then rasterize its vector and measured bounds.

Android views are JVM stand-ins; Pillow rasterization validates visible stroke centering,
not Android rendering. No device or font installation is required.
"""
from pathlib import Path
import os
import re
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/attachment-remove-check'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = ROOT / 'app/src/main/java/com/donglan/chrona/AttachmentImageTile.java'


def method(text, marker):
    start = text.index(marker)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


files = {
    'android/app/Activity.java': '''package android.app; public class Activity {
public android.content.res.Resources resources=new android.content.res.Resources();
public android.content.res.Resources getResources(){return resources;}}''',
    'android/content/res/Resources.java': '''package android.content.res; public class Resources {
public Metrics metrics=new Metrics();public Metrics getDisplayMetrics(){return metrics;}
public static class Metrics {public float density=1,scaledDensity=1;}}''',
    'android/content/res/ColorStateList.java': '''package android.content.res; public class ColorStateList {
public int color;public static ColorStateList valueOf(int c){ColorStateList s=new ColorStateList();s.color=c;return s;}}''',
    'android/net/Uri.java': 'package android.net; public class Uri {}',
    'android/graphics/Outline.java': 'package android.graphics; public class Outline {public void setRoundRect(int a,int b,int c,int d,int e){}}',
    'android/view/Gravity.java': 'package android.view; public class Gravity {public static final int TOP=48,END=8388613;}',
    'android/view/ViewOutlineProvider.java': 'package android.view; public abstract class ViewOutlineProvider {public abstract void getOutline(View v,android.graphics.Outline o);}',
    'android/view/View.java': '''package android.view; public class View {
public android.app.Activity activity;public int l,t,r,b,minW,minH;public boolean glass,pressable;public String description;public Click listener;
public View(android.app.Activity a){activity=a;}public android.content.res.Resources getResources(){return activity.getResources();}
public int getWidth(){return 36;}public int getHeight(){return 36;}public void setPadding(int a,int c,int d,int e){l=a;t=c;r=d;b=e;}
public void setMinimumWidth(int v){minW=v;}public void setMinimumHeight(int v){minH=v;}
public void setBackgroundColor(int v){}public void setContentDescription(String v){description=v;}
public interface Click {void call(View v);}public void setOnClickListener(Click c){listener=c;}}''',
    'android/widget/FrameLayout.java': '''package android.widget;public class FrameLayout extends android.view.View {
public java.util.List<android.view.View> children=new java.util.ArrayList<>();public java.util.List<LayoutParams> params=new java.util.ArrayList<>();
public FrameLayout(android.app.Activity a){super(a);}public void setClipChildren(boolean b){}public void setClipToPadding(boolean b){}public void setClipToOutline(boolean b){}public void setOutlineProvider(android.view.ViewOutlineProvider p){}
public void addView(android.view.View v,LayoutParams p){children.add(v);params.add(p);}
public static class LayoutParams {public int width,height,gravity,topMargin,rightMargin;public LayoutParams(int w,int h){width=w;height=h;}public LayoutParams(int w,int h,int g){this(w,h);gravity=g;}public void setMargins(int l,int t,int r,int b){topMargin=t;rightMargin=r;}}}''',
    'android/widget/ImageView.java': '''package android.widget;public class ImageView extends android.view.View {
public enum ScaleType {CENTER_CROP,CENTER_INSIDE}public ScaleType scale;public int resource;public android.content.res.ColorStateList tint;
public ImageView(android.app.Activity a){super(a);}public void setScaleType(ScaleType s){scale=s;}public void setImageResource(int r){resource=r;}public void setImageTintList(android.content.res.ColorStateList c){tint=c;}}''',
    'android/widget/ImageButton.java': 'package android.widget;public class ImageButton extends ImageView {public ImageButton(android.app.Activity a){super(a);}}',
    'com/donglan/chrona/R.java': 'package com.donglan.chrona;class R {static class drawable {static final int ic_x=7;}}',
    'com/donglan/chrona/UiStyle.java': '''package com.donglan.chrona;class UiStyle {static class Colors {int primary=0x553388,surfaceAlt=0xffffff;}static Colors colors(android.app.Activity a){return new Colors();}static void glass(android.view.View v){v.glass=true;}static void pressable(android.view.View v){v.pressable=true;}}''',
}
text = SOURCE.read_text(encoding='utf-8')
files['com/donglan/chrona/AttachmentImageTile.java'] = '''package com.donglan.chrona;
import android.app.Activity;import android.net.Uri;import android.view.*;import android.graphics.Outline;import android.widget.*;
final class AttachmentImageTile extends FrameLayout {private ImageView preview;private Uri imageUri;
''' + method(text, 'AttachmentImageTile(Activity') + method(text, 'private int dp(') + '}'
files['com/donglan/chrona/AttachmentRemoveCheck.java'] = '''package com.donglan.chrona;
import android.widget.*;public class AttachmentRemoveCheck {public static void main(String[] args){
for(float density:new float[]{1,1.5f,2,2.625f,3,3.5f})for(float font:new float[]{1,1.5f,2}){
android.app.Activity a=new android.app.Activity();a.resources.metrics.density=density;a.resources.metrics.scaledDensity=density*font;
int[] calls={0,0};AttachmentImageTile tile=new AttachmentImageTile(a,new android.net.Uri(),"photo",()->calls[0]++,()->calls[1]++);
check(tile.children.size()==2,"preview/remove hierarchy");check(tile.children.get(1) instanceof ImageButton,"vector image button");
ImageButton b=(ImageButton)tile.children.get(1);FrameLayout.LayoutParams p=tile.params.get(1);
check(p.width==Math.round(36*density)&&p.height==p.width&&b.minW==p.width&&b.minH==p.height,"preserved 36dp control");
check(p.gravity==(android.view.Gravity.TOP|android.view.Gravity.END)&&p.topMargin==Math.round(6*density)&&p.rightMargin==p.topMargin,"preserved placement");
check(b.l==b.r&&b.t==b.b&&b.l==b.t&&b.l==Math.round(8*density),"symmetric drawable insets");
check(b.resource==R.drawable.ic_x&&b.scale==ImageView.ScaleType.CENTER_INSIDE,"centered X vector");
check(b.tint.color==UiStyle.colors(a).primary&&b.glass&&b.pressable,"theme/glass/press feedback");
check(b.description.equals("移除图片：photo"),"accessible label");b.listener.call(b);check(calls[1]==1&&calls[0]==0,"remove callback isolation");
tile.children.get(0).listener.call(tile.children.get(0));check(calls[0]==1&&calls[1]==1,"preview callback isolation");
System.out.println(p.width+","+b.l+","+density);}}static void check(boolean v,String message){if(!v)throw new AssertionError(message);}}'''
for name, content in files.items():
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(OUT),
                *[str(OUT/name) for name in files]], check=True)
result = subprocess.run([str(java/'java.exe'), '-cp', str(OUT), 'com.donglan.chrona.AttachmentRemoveCheck'],
                        check=True, capture_output=True, text=True)
# Prove the geometric assertions reject a vertically biased icon inside a centered button.
helper = OUT/'com/donglan/chrona/AttachmentImageTile.java'
original = helper.read_text(encoding='utf-8')
biased = original.replace('remove.setPadding(dp(8), dp(8), dp(8), dp(8))',
                          'remove.setPadding(dp(8), dp(10), dp(8), dp(6))')
assert biased != original
helper.write_text(biased, encoding='utf-8')
try:
    subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-cp', str(OUT), '-d', str(OUT), str(helper)], check=True)
    mutant = subprocess.run([str(java/'java.exe'), '-cp', str(OUT),
                             'com.donglan.chrona.AttachmentRemoveCheck'], capture_output=True, text=True)
    assert mutant.returncode != 0 and 'symmetric drawable insets' in mutant.stderr
finally:
    helper.write_text(original, encoding='utf-8')
    subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                    '-cp', str(OUT), '-d', str(OUT), str(helper)], check=True)
ns = '{http://schemas.android.com/apk/res/android}'
vector = ET.parse(ROOT/'app/src/main/res/drawable/ic_x.xml').getroot()
viewport = float(vector.attrib[ns+'viewportWidth'])
assert viewport == float(vector.attrib[ns+'viewportHeight'])
path = vector.find('path')
assert path.attrib[ns+'strokeLineCap'] == 'round'
points = re.findall(r'M([\d.]+),([\d.]+) L([\d.]+),([\d.]+)', path.attrib[ns+'pathData'])
assert len(points) == 2, 'Expected the two Lucide X strokes'
for line in sorted(set(result.stdout.splitlines())):
    size, padding, density = line.split(',')
    size, padding, density = int(size), int(padding), float(density)
    icon = min(24*density, size-2*padding)
    scale, factor = icon/viewport, 8
    offset = (size-icon)/2
    mask = Image.new('L', (size*factor, size*factor))
    draw = ImageDraw.Draw(mask)
    stroke = float(path.attrib[ns+'strokeWidth'])*scale*factor
    for values in points:
        coords = [(offset+float(values[i])*scale)*factor for i in range(4)]
        draw.line(coords, fill=255, width=round(stroke))
        for x,y in (coords[:2], coords[2:]):
            draw.ellipse((x-stroke/2,y-stroke/2,x+stroke/2,y+stroke/2), fill=255)
    mask = mask.resize((size,size), Image.Resampling.LANCZOS)
    bounds = mask.point(lambda value: 255 if value > 16 else 0).getbbox()
    center = ((bounds[0]+bounds[2])/2, (bounds[1]+bounds[3])/2)
    assert max(abs(c-size/2) for c in center) <= .5, (density, bounds, center)
    if density == 3:
        mask.save(OUT/'x-visible-mask-3x.png')
    print(f'density={density:g}: button={size}px visible bounds={bounds}, center error <=0.5px')
print('18 actual constructor executions passed across density/font scales; offline rasterization, no Android rendering.')
print('Asymmetric padding regression rejected while outer button placement remains unchanged.')
