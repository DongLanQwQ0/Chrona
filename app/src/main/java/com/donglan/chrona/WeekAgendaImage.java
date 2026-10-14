package com.donglan.chrona;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Full-week PNG, independent of viewport position. Numbered index preserves overflowed card text. */
final class WeekAgendaImage {
    static final int WIDTH = 2240;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final int LEFT = 112, RIGHT = 32, TOP = 240, HOUR_HEIGHT = 100;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    static File write(File directory, LocalDate first, ZoneId zone, List<AgendaEntry> input,
            UiStyle.Palette colors, String filter, boolean system) throws java.io.IOException {
        long begin=first.atStartOfDay(zone).toInstant().toEpochMilli(),until=first.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
        List<AgendaEntry> entries=new ArrayList<>();
        for(AgendaEntry e:input)if(e.overlaps(begin,until))entries.add(e);
        List<List<CalendarTimelineLayout.Block>> days=new ArrayList<>();
        int firstHour=8,lastHour=20,maxAllDay=0;
        for(int d=0;d<7;d++) {
            LocalDate day=first.plusDays(d);List<CalendarTimelineLayout.Block> blocks=CalendarTimelineLayout.day(entries,day,zone);days.add(blocks);
            for(CalendarTimelineLayout.Block b:blocks){firstHour=Math.min(firstHour,(int)Math.floor(b.startMinute/60));lastHour=Math.max(lastHour,(int)Math.ceil(b.endMinute/60));}
            int count=0;long a=day.atStartOfDay(zone).toInstant().toEpochMilli(),b=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            for(AgendaEntry e:entries)if(e.allDay&&e.overlaps(a,b))count++;maxAllDay=Math.max(maxAllDay,count);
        }
        int allDayHeight=maxAllDay==0?0:40+maxAllDay*46,gridTop=TOP+allDayHeight,gridHeight=(lastHour-firstHour)*HOUR_HEIGHT;
        List<StaticLayout> details=new ArrayList<>();int detailsHeight=entries.isEmpty()?64:0;
        for(int i=0;i<entries.size();i++) {
            AgendaEntry e=entries.get(i);String range=e.allDay?Instant.ofEpochMilli(e.start).atZone(zone).format(DateTimeFormatter.ofPattern("M/d"))+" — "+Instant.ofEpochMilli(e.finish()-1).atZone(zone).format(DateTimeFormatter.ofPattern("M/d"))+" · 全天":
                    Instant.ofEpochMilli(e.start).atZone(zone).format(DateTimeFormatter.ofPattern("M/d HH:mm"))+" — "+Instant.ofEpochMilli(e.finish()).atZone(zone).format(DateTimeFormatter.ofPattern("M/d HH:mm"));
            String value=(i+1)+"  "+e.title+"\n"+range+(e.location.isBlank()?"":" · "+e.location)+" · "+e.source+(e.confirmed?"":" · 待确认");
            StaticLayout layout=paragraph(value,WIDTH-96,28,colors.text,0,false);details.add(layout);detailsHeight+=layout.getHeight()+24;
            if((long)WIDTH*(gridTop+gridHeight+120+detailsHeight+64)>MAX_PIXELS)throw new java.io.IOException("本周日程过多，图片过长；请先用搜索缩小范围再导出");
        }
        int height=gridTop+gridHeight+120+detailsHeight+64;
        if((long)WIDTH*height>MAX_PIXELS)throw new java.io.IOException("本周日程过多，图片过长；请先用搜索缩小范围再导出");
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888);
        File file=null;
        try {
            Canvas canvas=new Canvas(bitmap);canvas.drawColor(colors.background);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            label(canvas,paint,"周日程",48,72,54,colors.primary);
            label(canvas,paint,first+" — "+first.plusDays(6),48,120,30,colors.text);
            String source=(system?"包含系统日程":"仅拾时日程")+(filter.isBlank()?"":" · 筛选："+filter);
            canvas.save();canvas.clipRect(48,128,WIDTH-48,168);label(canvas,paint,source,48,162,26,colors.muted);canvas.restore();
            float dayWidth=(WIDTH-LEFT-RIGHT)/7f;
            String[] names={"周一","周二","周三","周四","周五","周六","周日"};
            for(int d=0;d<7;d++) {
                float x=LEFT+d*dayWidth;
                label(canvas,paint,names[d]+"  "+first.plusDays(d).format(DateTimeFormatter.ofPattern("M/d")),x+16,216,30,colors.primary);
                if(maxAllDay>0) {
                    int row=0;long a=first.plusDays(d).atStartOfDay(zone).toInstant().toEpochMilli(),b=first.plusDays(d+1).atStartOfDay(zone).toInstant().toEpochMilli();
                    for(AgendaEntry e:entries)if(e.allDay&&e.overlaps(a,b)) {
                        String text=(entries.indexOf(e)+1)+" · "+e.title;
                        drawText(canvas,text,x+10,TOP+row*46,Math.round(dayWidth-20),38,24,colors.text);row++;
                    }
                }
                paint.setColor(colors.outline);paint.setStrokeWidth(1);canvas.drawLine(x,TOP,x,gridTop+gridHeight,paint);
            }
            paint.setColor(colors.outline);canvas.drawLine(WIDTH-RIGHT,TOP,WIDTH-RIGHT,gridTop+gridHeight,paint);
            for(int hour=firstHour;hour<=lastHour;hour++) {
                float y=gridTop+(hour-firstHour)*HOUR_HEIGHT;paint.setColor(colors.outline);canvas.drawLine(LEFT,y,WIDTH-RIGHT,y,paint);
                label(canvas,paint,String.format(java.util.Locale.ROOT,"%02d:00",hour),18,y+10,24,colors.muted);
            }
            for(int d=0;d<7;d++)for(CalendarTimelineLayout.Block block:days.get(d)) {
                float laneWidth=(dayWidth-12)/block.lanes;
                float x=LEFT+d*dayWidth+6+block.lane*laneWidth,y=gridTop+(float)(block.startMinute-firstHour*60)/60*HOUR_HEIGHT;
                float h=(float)(block.endMinute-block.startMinute)/60*HOUR_HEIGHT;
                float gridBottom=gridTop+gridHeight,top=Math.min(y+2,gridBottom-6);
                RectF rect=new RectF(x,top,x+laneWidth-5,Math.min(gridBottom,Math.max(top+4,y+h-2)));
                int accent=CalendarSourceColors.mix(block.entry.calendarColor,colors.primary);
                paint.setStyle(Paint.Style.FILL);paint.setColor(mix(colors.surface,accent,.14f));canvas.drawRoundRect(rect,14,14,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(accent);canvas.drawRoundRect(rect,14,14,paint);paint.setStyle(Paint.Style.FILL);
                String text=(entries.indexOf(block.entry)+1)+" · "+block.entry.title+"\n"+CLOCK.format(Instant.ofEpochMilli(block.entry.start).atZone(zone))+" — "+CLOCK.format(Instant.ofEpochMilli(block.entry.finish()).atZone(zone));
                if(rect.height()<38) {
                    drawText(canvas,(entries.indexOf(block.entry)+1)+" · "+block.entry.title,rect.left+4,
                            Math.max(gridTop,rect.top-29),Math.max(1,(int)rect.width()-8),27,22,colors.text);
                } else drawText(canvas,text,rect.left+8,rect.top+6,Math.max(1,(int)rect.width()-16),Math.max(0,(int)rect.height()-12),26,colors.text);
            }
            int y=gridTop+gridHeight+64;label(canvas,paint,"日程索引",48,y,34,colors.primary);y+=32;
            if(entries.isEmpty())label(canvas,paint,"本周暂无日程",48,y+40,28,colors.muted);
            for(StaticLayout detail:details){canvas.save();canvas.translate(48,y);detail.draw(canvas);canvas.restore();y+=detail.getHeight()+24;}
            label(canvas,paint,"拾时 · Chrona",48,height-24,24,colors.muted);
            file=File.createTempFile("week-export-",".png",directory);
            try(FileOutputStream stream=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))throw new java.io.IOException("无法生成图片");}
            return file;
        } catch(java.io.IOException|RuntimeException error) {if(file!=null)file.delete();throw error;}
        finally {bitmap.recycle();}
    }
    private static void drawText(Canvas canvas,String text,float x,float y,int width,int height,int size,int color) {
        if(height<size)return;
        StaticLayout layout=paragraph(text,width,size,color,Math.max(1,height/(size+5)),true);
        canvas.save();canvas.clipRect(x,y,x+width,y+height);canvas.translate(x,y);layout.draw(canvas);canvas.restore();
    }
    private static StaticLayout paragraph(String text,int width,int size,int color,int maxLines,boolean ellipsis) {
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(color);paint.setTextSize(size);
        StaticLayout.Builder builder=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(5,1);
        if(maxLines>0)builder.setMaxLines(maxLines);if(ellipsis)builder.setEllipsize(TextUtils.TruncateAt.END);
        return builder.build();
    }
    private static void label(Canvas canvas,Paint paint,String text,float x,float y,int size,int color){paint.setTextSize(size);paint.setColor(color);canvas.drawText(text,x,y,paint);}
    private static int mix(int base,int accent,float ratio){return android.graphics.Color.rgb(Math.round(android.graphics.Color.red(base)*(1-ratio)+android.graphics.Color.red(accent)*ratio),Math.round(android.graphics.Color.green(base)*(1-ratio)+android.graphics.Color.green(accent)*ratio),Math.round(android.graphics.Color.blue(base)*(1-ratio)+android.graphics.Color.blue(accent)*ratio));}
    private WeekAgendaImage() { }
}
