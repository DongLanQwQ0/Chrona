"""Execute the real themed shortcut publisher with Android transport stand-ins."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
out = root/'build/launcher-shortcuts-check'
files = {
'android/content/Context.java': '''package android.content;public class Context {
public int color=0xff008080;public android.content.pm.ShortcutManager manager=new android.content.pm.ShortcutManager();
public Context getApplicationContext(){return this;}public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public android.graphics.drawable.Drawable getDrawable(int id){return new android.graphics.drawable.Drawable();}
public String getString(int id){return "label"+id;}public <T>T getSystemService(Class<T> c){return c.cast(manager);}}''',
'android/content/res/Resources.java': 'package android.content.res;public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}',
'android/util/DisplayMetrics.java': 'package android.util;public class DisplayMetrics {public float density=3;}',
'android/util/Log.java': 'package android.util;public class Log {public static int w(String t,String m,Throwable e){return 0;}}',
'android/content/Intent.java': 'package android.content;public class Intent {public Class<?> target;public String action;public Intent(Context c,Class<?> t){target=t;}public Intent setAction(String a){action=a;return this;}}',
'android/graphics/Bitmap.java': 'package android.graphics;public class Bitmap {public int color;public enum Config {ARGB_8888}public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap();}}',
'android/graphics/Canvas.java': 'package android.graphics;public class Canvas {public Bitmap bitmap;public Canvas(Bitmap b){bitmap=b;}}',
'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable;public class Drawable {int tint;public Drawable mutate(){return this;}public void setTint(int c){tint=c;}public void setBounds(int a,int b,int c,int d){}public void draw(android.graphics.Canvas c){c.bitmap.color=tint;}}',
'android/graphics/drawable/Icon.java': 'package android.graphics.drawable;public class Icon {public android.graphics.Bitmap bitmap;public static Icon createWithBitmap(android.graphics.Bitmap b){Icon i=new Icon();i.bitmap=b;return i;}}',
'android/content/pm/ShortcutInfo.java': '''package android.content.pm;public class ShortcutInfo {
public String id;public int rank;public android.graphics.drawable.Icon icon;public android.content.Intent intent;
public static class Builder {ShortcutInfo s=new ShortcutInfo();public Builder(android.content.Context c,String id){s.id=id;}
public Builder setRank(int r){s.rank=r;return this;}public Builder setShortLabel(String l){return this;}public Builder setIcon(android.graphics.drawable.Icon i){s.icon=i;return this;}
public Builder setIntent(android.content.Intent i){s.intent=i;return this;}public ShortcutInfo build(){return s;}}}''',
'android/content/pm/ShortcutManager.java': '''package android.content.pm;public class ShortcutManager {
public int calls;public boolean accepted=true;public java.util.List<ShortcutInfo>items;
public boolean setDynamicShortcuts(java.util.List<ShortcutInfo> s){calls++;if(accepted)items=s;return accepted;}}''',
'com/donglan/chrona/UiStyle.java': 'package com.donglan.chrona;class UiStyle {static class Palette {int primary;}static Palette colors(android.content.Context c){Palette p=new Palette();p.primary=c.color;return p;}}',
'com/donglan/chrona/R.java': 'package com.donglan.chrona;class R {static class string {static int shortcut_capture=1,shortcut_week=2,shortcut_schedule=3,shortcut_inbox=4;}static class drawable {static int ic_add=10,ic_week_calendar=11,ic_nav_schedule=12,ic_nav_inbox=13;}}',
'com/donglan/chrona/LauncherShortcutActivity.java': 'package com.donglan.chrona;class LauncherShortcutActivity {}',
'com/donglan/chrona/LauncherShortcutsCheck.java': '''package com.donglan.chrona;public class LauncherShortcutsCheck {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
public static void main(String[]args)throws Exception {
android.content.Context c=new android.content.Context();java.lang.reflect.Method p=LauncherShortcuts.class.getDeclaredMethod("publish",android.content.Context.class);p.setAccessible(true);
p.invoke(null,c);check(c.manager.items.size()==4,"four actions");
for(int i=0;i<4;i++){android.content.pm.ShortcutInfo s=c.manager.items.get(i);check(s.rank==i&&s.icon.bitmap.color==c.color,"theme tint and rank");check(s.intent.target==LauncherShortcutActivity.class,"explicit routing");}
p.invoke(null,c);check(c.manager.calls==1,"unchanged theme avoids rate limit");
c.color=0xff6633cc;p.invoke(null,c);check(c.manager.calls==2&&c.manager.items.get(2).icon.bitmap.color==c.color,"theme change updates icons");
c.color=0xff115588;c.manager.accepted=false;p.invoke(null,c);c.manager.accepted=true;p.invoke(null,c);
check(c.manager.calls==4&&c.manager.items.get(3).icon.bitmap.color==c.color,"rate limited publication can retry");
System.out.println("Launcher shortcut publisher passed: all icons tinted, four routes, same-theme suppression and throttled retry");}}'''
}
paths=[]
for name,content in files.items():
    path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8');paths.append(path)
paths.append(root/'app/src/main/java/com/donglan/chrona/LauncherShortcuts.java')
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),*map(str,paths)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'com.donglan.chrona.LauncherShortcutsCheck'],check=True)
