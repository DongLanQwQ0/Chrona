"""Execute production image composition with recording graphics transport, not a pixel-render test."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/week-export-check'
files = {
    'android/graphics/Paint.java': '''package android.graphics;public class Paint {
public static int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}public Paint(int f){}public void setColor(int c){}public void setStrokeWidth(float w){}public void setTextSize(float s){}public void setStyle(Style s){}}''',
    'android/graphics/Bitmap.java': '''package android.graphics;public class Bitmap {
public static int lastHeight;public enum Config{ARGB_8888}public enum CompressFormat{PNG}
public static Bitmap createBitmap(int w,int h,Config c){lastHeight=h;return new Bitmap();}
public boolean compress(CompressFormat f,int q,java.io.OutputStream s)throws java.io.IOException{s.write(1);return true;}public void recycle(){}}''',
    'android/graphics/Canvas.java': '''package android.graphics;public class Canvas {
public static java.util.List<String> labels=new java.util.ArrayList<>();public static int lines;
public Canvas(Bitmap b){}public void drawColor(int c){}public void save(){}public void restore(){}public void clipRect(float a,float b,float c,float d){}
public void translate(float x,float y){}public void drawLine(float a,float b,float c,float d,Paint p){lines++;}
public void drawRoundRect(RectF r,float x,float y,Paint p){}public void drawText(String s,float x,float y,Paint p){labels.add(s);}}''',
    'android/graphics/RectF.java': '''package android.graphics;public class RectF {
public float left,top,right,bottom;public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public float width(){return right-left;}public float height(){return bottom-top;}}''',
    'android/graphics/Color.java': '''package android.graphics;public class Color {public static int red(int c){return c>>16&255;}public static int green(int c){return c>>8&255;}public static int blue(int c){return c&255;}public static int rgb(int r,int g,int b){return r<<16|g<<8|b;}}''',
    'android/text/Layout.java': 'package android.text;public class Layout {public enum Alignment{ALIGN_NORMAL}}',
    'android/text/TextUtils.java': 'package android.text;public class TextUtils {public enum TruncateAt{END}}',
    'android/text/TextPaint.java': 'package android.text;public class TextPaint extends android.graphics.Paint {public TextPaint(int f){super(f);}}',
    'android/text/StaticLayout.java': '''package android.text;public class StaticLayout {
public static int unlimitedBuilds;int h;String value;public int getHeight(){return h;}
public void draw(android.graphics.Canvas c){android.graphics.Canvas.labels.add(value);}
public static class Builder {String text;int width,max;public static Builder obtain(String s,int a,int b,TextPaint p,int w){Builder x=new Builder();x.text=s;x.width=w;return x;}
public Builder setAlignment(Layout.Alignment a){return this;}public Builder setIncludePad(boolean b){return this;}public Builder setLineSpacing(int a,int b){return this;}
public Builder setMaxLines(int m){max=m;return this;}public Builder setEllipsize(TextUtils.TruncateAt t){return this;}
public StaticLayout build(){StaticLayout l=new StaticLayout();l.value=text;int n=Math.max(1,(text.length()*28+width-1)/width);if(max==0)unlimitedBuilds++;else n=Math.min(n,max);l.h=n*33;return l;}}}''',
    'com/donglan/chrona/UiStyle.java': 'package com.donglan.chrona;class UiStyle {static class Palette {int text,primary,muted,background,surface,outline;}}',
    'com/donglan/chrona/WeekExportCheck.java': '''package com.donglan.chrona;
import java.util.*;import java.time.*;import java.io.*;import android.graphics.*;import android.text.StaticLayout;
public class WeekExportCheck {
static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
static void reset(){Canvas.labels.clear();Canvas.lines=0;StaticLayout.unlimitedBuilds=0;}
public static void main(String[] args)throws Exception {
File directory=new File(args[0]);LocalDate first=LocalDate.of(2026,10,12);ZoneId zone=ZoneId.of("Asia/Shanghai");
long t=first.atTime(9,0).atZone(zone).toInstant().toEpochMilli();
List<AgendaEntry> entries=List.of(new AgendaEntry(1,1,t,t+3600000,"测试日程","教室","","拾时",false,false,true));
reset();File plain=WeekAgendaImage.write(directory,first,zone,entries,new UiStyle.Palette(),"",false,false);
int plainHeight=Bitmap.lastHeight,gridLines=Canvas.lines;
check(!Canvas.labels.contains("日程索引")&&StaticLayout.unlimitedBuilds==0,"plain week must not construct index paragraphs");
check(Canvas.labels.stream().anyMatch(s->s.startsWith("测试日程"))&&!Canvas.labels.stream().anyMatch(s->s.startsWith("1 · ")),"plain cards retain titles without index numbers");
plain.delete();reset();File indexed=WeekAgendaImage.write(directory,first,zone,entries,new UiStyle.Palette(),"",false,true);
check(Canvas.labels.contains("日程索引")&&StaticLayout.unlimitedBuilds==1,"index paragraphs present only when enabled");
check(Bitmap.lastHeight>plainHeight&&Canvas.lines==gridLines,"same complete grid, optional index height");indexed.delete();
List<AgendaEntry> many=new ArrayList<>();for(int i=0;i<80;i++)many.add(new AgendaEntry(i,i,t,t+3600000,"长".repeat(10000),"","","拾时",false,false,true));
reset();File dense=WeekAgendaImage.write(directory,first,zone,many,new UiStyle.Palette(),"",false,false);
check(StaticLayout.unlimitedBuilds==0&&Bitmap.lastHeight==plainHeight,"excluded index cannot trip pixel limit");dense.delete();
boolean rejected=false;try{WeekAgendaImage.write(directory,first,zone,many,new UiStyle.Palette(),"",false,true);}catch(IOException expected){rejected=true;}
check(rejected,"included oversized index retains explicit size error");
reset();File empty=WeekAgendaImage.write(directory,first,zone,List.of(),new UiStyle.Palette(),"",false,false);
check(!Canvas.labels.contains("日程索引")&&Canvas.lines==gridLines,"empty plain export preserves grid without empty index");empty.delete();
System.out.println("Week export checks passed: "+checks+"; production optional-index composition and size-limit behavior");
}}'''
}
paths = []
for name, content in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
paths += [src / (name + '.java') for name in ('WeekAgendaImage', 'AgendaEntry', 'CalendarTimelineLayout', 'CalendarSourceColors')]
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *map(str, paths)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.WeekExportCheck', str(out)], check=True)
