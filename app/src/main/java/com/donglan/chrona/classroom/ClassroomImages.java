package com.donglan.chrona.classroom;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
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
    private static final int SIDE=96;
    private static final int TILES=6;
    private static final int MAX_PIXELS=24_000_000;
    private static final double MAX_MEAN_CHANGE=1.5;
    private static final double MAX_TILE_CHANGE=3.0;
    private static final int MAX_LOCAL_CHANGE=18;
    public interface Progress { void update(ClassroomProgress progress); }
    private ClassroomImages() { }
    /** A null downloader permits cache-only recomputation; missing/broken originals fail explicitly. */
    public static JSONArray prepare(File dir,JSONObject materials,ClassroomClient client,RequestControl control,Progress progress) throws Exception {
        JSONArray source=materials.getJSONArray("images");List<JSONObject> ordered=new ArrayList<>();
        for(int i=0;i<source.length();i++)ordered.add(source.getJSONObject(i));
        ordered.sort(Comparator.comparingDouble(o->o.optDouble("second")));
        List<JSONObject> candidates=new ArrayList<>();int[] anchor=null,representative=null;
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
            grayscale(pixels);
            JSONObject quality=ClassroomImageQuality.analyze(pixels,SIDE,SIDE);
            boolean similar=anchor!=null&&nearDuplicate(anchor,pixels)&&nearDuplicate(representative,pixels);
            JSONObject candidate=new JSONObject().put("raw",raw.getName()).put("second",image.getDouble("second"))
                    .put("index",cacheIndex).put("quality",quality);
            if(similar) {
                JSONObject kept=candidates.get(candidates.size()-1);
                if(preferLater(kept.getJSONObject("quality"),quality,representative,pixels)) {
                    candidates.set(candidates.size()-1,candidate);representative=pixels;
                }
            } else { candidates.add(candidate);anchor=pixels;representative=pixels; }
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
        if(client==null)throw new IOException("原图缓存缺失或损坏，请重新获取课堂素材");
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
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>MAX_PIXELS)throw new ImageDecodeException();
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
    private static void grayscale(int[] pixels) {
        for(int p=0;p<pixels.length;p++){int color=pixels[p];pixels[p]=(((color>>16)&255)*30+((color>>8)&255)*59+(color&255)*11)/100;}
    }
    public static JSONObject quality(File raw) throws IOException,JSONException {
        int[] gray=pixels(raw);grayscale(gray);return ClassroomImageQuality.analyze(gray,SIDE,SIDE);
    }
    private static boolean nearDuplicate(int[] a,int[] b) {
        if(difference(a,b)>MAX_MEAN_CHANGE)return false;
        int tileSide=SIDE/TILES;
        for(int ty=0;ty<TILES;ty++)for(int tx=0;tx<TILES;tx++) {
            long delta=0;
            for(int y=ty*tileSide;y<(ty+1)*tileSide;y++)for(int x=tx*tileSide;x<(tx+1)*tileSide;x++) {
                int change=Math.abs(a[y*SIDE+x]-b[y*SIDE+x]);
                if(change>MAX_LOCAL_CHANGE)return false;
                delta+=change;
            }
            if(delta/(double)(tileSide*tileSide)>MAX_TILE_CHANGE)return false;
        }
        return true;
    }
    private static boolean preferLater(JSONObject old,JSONObject next,int[] before,int[] after) {
        if(next.optDouble("score")<old.optDouble("score")-1e-9
                ||next.optDouble("detail_fraction")<old.optDouble("detail_fraction")-1e-9)return false;
        // A higher global score must not hide loss of detail in one local region.
        int tileSide=SIDE/TILES;
        for(int ty=0;ty<TILES;ty++)for(int tx=0;tx<TILES;tx++) {
            long oldEdges=0,newEdges=0;
            for(int y=ty*tileSide;y<(ty+1)*tileSide-1;y++)for(int x=tx*tileSide;x<(tx+1)*tileSide-1;x++) {
                int p=y*SIDE+x;
                oldEdges+=Math.abs(before[p]-before[p+1])+Math.abs(before[p]-before[p+SIDE]);
                newEdges+=Math.abs(after[p]-after[p+1])+Math.abs(after[p]-after[p+SIDE]);
            }
            if(newEdges<oldEdges)return false;
        }
        return true;
    }
    /** Spread the finite budget across the recording; rank information only within each interval. */
    public static JSONArray select(JSONArray candidates,int limit) throws Exception {
        JSONArray selected=new JSONArray();int count=Math.min(limit,candidates.length());
        if(count<=0)return selected;
        boolean hasQuality=false;for(int i=0;i<candidates.length();i++)if(candidates.getJSONObject(i).optJSONObject("quality")!=null)hasQuality=true;
        if(!hasQuality) {
            for(int i=0;i<count;i++){int index=count==1?candidates.length()-1:(int)Math.round(i*(candidates.length()-1.0)/(count-1));selected.put(candidates.getJSONObject(index));}
            return selected;
        }
        double start=candidates.getJSONObject(0).optDouble("second"),end=candidates.getJSONObject(candidates.length()-1).optDouble("second");
        int nextFrom=0;
        for(int bucket=0;bucket<count;bucket++) {
            // Reserve a non-empty contiguous interval for every slot, even with clustered timestamps.
            int from=bucket*candidates.length()/count,to=(bucket+1)*candidates.length()/count;
            if(end>start&&count>1) {
                int remaining=count-bucket-1;
                from=selected.length()==0?0:nextFrom;
                to=from+1;
                double boundary=start+(end-start)*(bucket+1)/count;
                if(bucket==count-1)to=candidates.length();
                else while(to<candidates.length()-remaining&&candidates.getJSONObject(to).optDouble("second")<boundary)to++;
            }
            int best=from;
            for(int i=from+1;i<to;i++)if(informationScore(candidates.getJSONObject(i))>=informationScore(candidates.getJSONObject(best)))best=i;
            selected.put(candidates.getJSONObject(best));nextFrom=to;
        }
        return selected;
    }
    private static double informationScore(JSONObject candidate) {
        JSONObject quality=candidate.optJSONObject("quality");return quality==null?-1:quality.optDouble("score",0);
    }
    public static byte[] jpeg(File raw) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(raw.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>MAX_PIXELS)throw new IOException("截图分辨率无法处理");
        Bitmap bitmap=BitmapFactory.decodeFile(raw.getPath());if(bitmap==null)throw new IOException("截图无法读取");
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,bytes))throw new IOException("截图转换失败");return bytes.toByteArray();
        }finally{bitmap.recycle();}
    }
}
