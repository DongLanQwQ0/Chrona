package com.donglan.chrona;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

/** Common image pickup; no file copy, write grant or durable access. */
final class ImageDrag {
    static boolean start(Activity activity,ImageView image,Uri uri){
        if(!image.isAttachedToWindow()||!(image.getDrawable() instanceof BitmapDrawable picture))return false;
        try{
            if(activity.getContentResolver().getType(uri)==null)throw new java.io.IOException("图片不可用");
            ClipData clip=ClipData.newUri(activity.getContentResolver(),"图片",uri);
            Bitmap bitmap=picture.getBitmap();int limit=Math.round(160*image.getResources().getDisplayMetrics().density);
            float ratio=Math.min(1f,limit/(float)Math.max(bitmap.getWidth(),bitmap.getHeight()));int width=Math.max(1,Math.round(bitmap.getWidth()*ratio)),height=Math.max(1,Math.round(bitmap.getHeight()*ratio));
            float radius=Math.min(UiStyle.radius(activity,UiStyle.RADIUS_CARD)*image.getResources().getDisplayMetrics().density,Math.min(width,height)/2f);
            View.DragShadowBuilder shadow=new View.DragShadowBuilder(image){
                @Override public void onProvideShadowMetrics(Point size,Point touch){size.set(width,height);touch.set(width/2,height/2);}
                @Override public void onDrawShadow(Canvas canvas){Path clip=new Path();clip.addRoundRect(new RectF(0,0,width,height),Math.min(radius,height/2f),Math.min(radius,height/2f),Path.Direction.CW);canvas.clipPath(clip);canvas.drawBitmap(bitmap,null,new RectF(0,0,width,height),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));}
            };
            return image.startDragAndDrop(clip,shadow,null,View.DRAG_FLAG_GLOBAL|View.DRAG_FLAG_GLOBAL_URI_READ);
        }catch(Exception unavailable){Feedback.show(activity,"无法拖动图片");return false;}
    }
}
