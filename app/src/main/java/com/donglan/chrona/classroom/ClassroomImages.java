package com.donglan.chrona.classroom;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.donglan.chrona.net.RequestControl;

/** Low-resolution comparison only; no OCR runtime, model or additional dependency. */
public final class ClassroomImages {
    private static final int SIDE=48;
    public interface Progress { void update(String status); }
    private ClassroomImages() { }
    public static JSONArray prepare(File dir,JSONObject materials,ClassroomClient client,RequestControl control,Progress progress) throws Exception {
        JSONArray source=materials.getJSONArray("images");List<JSONObject> ordered=new ArrayList<>();
        for(int i=0;i<source.length();i++)ordered.add(source.getJSONObject(i));
        ordered.sort(Comparator.comparingDouble(o->o.optDouble("second")));
        List<JSONObject> candidates=new ArrayList<>();int[] previous=null;
        for(int i=0;i<ordered.size();i++) {
            control.check();progress.update("截图去重 "+(i+1)+" / "+ordered.size());
            JSONObject image=ordered.get(i);File raw=new File(dir,"raw-"+i+".img");
            if(!raw.isFile())ClassroomStore.writeBytes(raw,client.image(image.getString("url")));
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(raw.getPath(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("课堂截图无法解码");
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/SIDE);
            Bitmap decoded=BitmapFactory.decodeFile(raw.getPath(),options);
            if(decoded==null)throw new IOException("课堂截图无法解码");
            Bitmap small=Bitmap.createScaledBitmap(decoded,SIDE,SIDE,true);int[] pixels=new int[SIDE*SIDE];
            small.getPixels(pixels,0,SIDE,0,0,SIDE,SIDE);if(small!=decoded)small.recycle();decoded.recycle();
            for(int p=0;p<pixels.length;p++){int color=pixels[p];pixels[p]=(((color>>16)&255)*30+((color>>8)&255)*59+(color&255)*11)/100;}
            // Near-identical progressive frames keep the later, more complete state.
            boolean similar=previous!=null&&difference(previous,pixels)<3.0;
            JSONObject candidate=new JSONObject().put("raw",raw.getName()).put("second",image.getDouble("second")).put("index",i);
            if(similar)candidates.set(candidates.size()-1,candidate);else candidates.add(candidate);
            previous=pixels;
        }
        JSONArray result=new JSONArray();for(JSONObject candidate:candidates)result.put(candidate);return result;
    }
    public static double difference(int[] a,int[] b) { long delta=0;for(int i=0;i<a.length;i++)delta+=Math.abs(a[i]-b[i]);return delta/(double)a.length; }
    /** Spread the finite budget across the entire recording, including its final slide. */
    public static JSONArray select(JSONArray candidates,int limit) throws Exception {
        JSONArray selected=new JSONArray();int count=Math.min(limit,candidates.length());
        for(int i=0;i<count;i++){int index=count==1?candidates.length()-1:(int)Math.round(i*(candidates.length()-1.0)/(count-1));selected.put(candidates.getJSONObject(index));}return selected;
    }
    public static byte[] jpeg(File raw) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(raw.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>24_000_000)throw new IOException("截图分辨率无法处理");
        Bitmap bitmap=BitmapFactory.decodeFile(raw.getPath());if(bitmap==null)throw new IOException("截图无法读取");
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,bytes))throw new IOException("截图转换失败");return bytes.toByteArray();
        }finally{bitmap.recycle();}
    }
}
