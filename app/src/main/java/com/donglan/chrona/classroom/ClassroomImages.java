package com.donglan.chrona.classroom;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import com.donglan.chrona.zju.ZjuClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.donglan.chrona.net.RequestControl;
import com.donglan.chrona.net.NetworkFailure;

/** Low-resolution comparison only; no OCR runtime, model or additional dependency. */
public final class ClassroomImages {
    private static final int SIDE=48;
    public interface Progress { void update(ClassroomProgress progress); }
    private ClassroomImages() { }
    public static JSONArray prepare(File dir,JSONObject materials,ClassroomClient client,RequestControl control,Progress progress) throws Exception {
        JSONArray source=materials.getJSONArray("images");List<JSONObject> ordered=new ArrayList<>();
        for(int i=0;i<source.length();i++)ordered.add(source.getJSONObject(i));
        ordered.sort(Comparator.comparingDouble(o->o.optDouble("second")));
        List<JSONObject> candidates=new ArrayList<>();int[] previous=null;
        for(int i=0;i<ordered.size();i++) {
            control.check();
            JSONObject image=ordered.get(i);int cacheIndex=image.optInt("cache_index",i);
            if(cacheIndex<0)throw new IOException("截图缓存编号无效");
            File raw=new File(dir,"raw-"+cacheIndex+".img");
            progress.update(new ClassroomProgress(raw.isFile()?"读取已缓存截图":"下载课堂截图",i,ordered.size(),
                    "当前第 "+(i+1)+" 张 · "+ClassroomExport.time(image.getDouble("second"))+"\n已保留 "+candidates.size()+" 张"));
            int[] pixels;
            try { pixels=prepareImage(raw,image.getString("url"),client,control); }
            catch(IOException e) {
                control.check();
                String prefix="第 "+(i+1)+"/"+ordered.size()+" 张截图：";
                if(e instanceof InterruptedIOException && !(e instanceof SocketTimeoutException))throw e;
                String reason=e instanceof ImageDecodeException?"图像损坏或格式无法解码":
                        e instanceof ZjuClient.SchoolException&&!NetworkFailure.hasDetails(e)?e.getMessage():NetworkFailure.describe(e);
                throw NetworkFailure.context(prefix+reason,e);
            }
            for(int p=0;p<pixels.length;p++){int color=pixels[p];pixels[p]=(((color>>16)&255)*30+((color>>8)&255)*59+(color&255)*11)/100;}
            // Near-identical progressive frames keep the later, more complete state.
            boolean similar=previous!=null&&difference(previous,pixels)<3.0;
            JSONObject candidate=new JSONObject().put("raw",raw.getName()).put("second",image.getDouble("second")).put("index",cacheIndex);
            if(similar)candidates.set(candidates.size()-1,candidate);else candidates.add(candidate);
            previous=pixels;
            progress.update(new ClassroomProgress("截图去重",i+1,ordered.size(),
                    "保留 "+candidates.size()+" 张 · 合并重复 "+(i+1-candidates.size())+" 张"));
        }
        JSONArray result=new JSONArray();for(JSONObject candidate:candidates)result.put(candidate);return result;
    }
    private static int[] prepareImage(File raw,String url,ClassroomClient client,RequestControl control) throws IOException {
        if(raw.isFile()) {
            try { return pixels(raw); }
            catch(ImageDecodeException invalid) {
                if(!raw.delete())throw NetworkFailure.context("本地缓存：无法移除损坏的截图，请检查存储空间与访问权限", invalid);
            }
        }
        control.check();
        byte[] bytes=client.image(url);
        control.check();
        File stage=new File(raw.getParentFile(),raw.getName()+".validating");
        try {
            ClassroomStore.writeBytes(stage,bytes);
            int[] pixels=pixels(stage);
            control.check();
            ClassroomStore.writeBytes(raw,bytes);
            return pixels;
        } catch (ImageDecodeException | InterruptedIOException e) { throw e; }
        catch (IOException e) { throw NetworkFailure.context("保存课堂截图：本地缓存写入失败，请检查存储空间与访问权限（"+e.getClass().getSimpleName()+"）", e); }
        finally { if(stage.exists())stage.delete(); }
    }
    private static final class ImageDecodeException extends IOException { }
    private static int[] pixels(File raw) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(raw.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new ImageDecodeException();
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/SIDE);
        Bitmap decoded=BitmapFactory.decodeFile(raw.getPath(),options);
        if(decoded==null)throw new ImageDecodeException();
        Bitmap small=null;
        try {
            small=Bitmap.createScaledBitmap(decoded,SIDE,SIDE,true);int[] pixels=new int[SIDE*SIDE];
            small.getPixels(pixels,0,SIDE,0,0,SIDE,SIDE);return pixels;
        } finally { if(small!=null&&small!=decoded)small.recycle();decoded.recycle(); }
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
