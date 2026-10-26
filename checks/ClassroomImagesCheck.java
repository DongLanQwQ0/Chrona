import com.donglan.chrona.classroom.*;
import com.donglan.chrona.net.RequestControl;
import java.io.*;
import java.nio.file.*;
import org.json.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public final class ClassroomImagesCheck {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        JSONObject payload=ClassroomTranscript.payload("cd1&m{\"sourcetext\":\"老师刚刚说的内容\"}");
        check(payload.getString("sourcetext").equals("老师刚刚说的内容"),"official Glue default-channel payload");
        check(ClassroomTranscript.payload("pi")==null&&ClassroomTranscript.payload("cd1&x{}") == null,"control and other channels are not captions");
        long base=1_000_000;JSONObject line=new JSONObject().put("sourcetext","第一句").put("text_begin_time",base+1000).put("text_end_time",base+2000).put("end_time","0");
        JSONObject transcript=ClassroomTranscript.append(null,line,base,base+3000);
        line.put("sourcetext","第一句补全").put("text_end_time",base+2500);transcript=ClassroomTranscript.append(transcript,line,base,base+3500);
        check(transcript.getJSONArray("segments").length()==0&&ClassroomTranscript.snapshot(transcript).length()==1,"partial updates replace the current utterance");
        line.put("end_time","1");transcript=ClassroomTranscript.append(transcript,line,base,base+4000);
        transcript=ClassroomTranscript.append(transcript,line,base,base+4500);
        check(transcript.getJSONArray("segments").length()==1&&!transcript.has("current"),"final and reconnected duplicate never double-append");
        check(transcript.getJSONArray("segments").getJSONObject(0).getDouble("start")==1&&transcript.getJSONArray("segments").getJSONObject(0).getDouble("end")==2.5,"verified epoch milliseconds align to class-relative seconds");
        line.put("text_begin_time",base+5000).put("text_end_time",base+5500).put("end_time","0").put("sourcetext","第二句");
        transcript=ClassroomTranscript.append(transcript,line,base,base+6000);
        check(ClassroomTranscript.snapshot(transcript).length()==2,"saved history and current sentence are both readable");
        File dir=Files.createTempDirectory("chrona-images-").toFile();
        try(RequestControl control=new RequestControl(60000)) {
            ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(48,48,BufferedImage.TYPE_INT_RGB),"png",output);
            ClassroomClient client=new ClassroomClient();client.bytes=output.toByteArray();
            JSONObject materials=new JSONObject().put("images",new JSONArray().put(new JSONObject().put("url","fixture").put("second",0)));
            File raw=new File(dir,"raw-0.img");Files.writeString(raw.toPath(),"broken");
            ClassroomImages.prepare(dir,materials,client,control,p->{});
            check(client.downloads==1&&ImageIO.read(raw)!=null,"corrupt cache replaced once");
            ClassroomImages.prepare(dir,materials,client,control,p->{});
            check(client.downloads==1,"valid cache reused");
            raw.delete();client.bytes="broken".getBytes();
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("bad download"); }
            catch(IOException e){check(e.getMessage().contains("第 1/1 张截图"),"safe indexed decode failure");}
            check(!raw.exists()&&!new File(dir,"raw-0.img.validating").exists(),"bad download not cached");
            client.bytes=output.toByteArray();ClassroomImages.prepare(dir,materials,client,control,p->{});
            check(ImageIO.read(raw)!=null,"retry can recover");
            materials.getJSONArray("images").put(new JSONObject().put("url","fixture2").put("second",1));client.bytes="broken".getBytes();
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("second bad image"); }
            catch(IOException e){check(e.getMessage().contains("第 2/2 张截图"),"failed index exact");}
            check(raw.exists()&&!new File(dir,"raw-1.img").exists(),"successful cache preserved");
            client.failure=new java.net.SocketTimeoutException("sensitive URL");
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("timeout"); }
            catch(java.net.SocketTimeoutException e){check(e.getMessage().contains("第 2/2 张截图")&&!e.getMessage().contains("sensitive"),"timeout retains type and safe index");}
            client.failure=new IOException("sensitive URL");
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("network"); }
            catch(IOException e){check(!e.getMessage().contains("sensitive"),"network message sanitized");}
            client.failure=null;client.bytes=output.toByteArray();
            JSONObject old=new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("start",0).put("end",1).put("text","old")))
                    .put("images",new JSONArray().put(new JSONObject().put("url","fixture").put("second",10)));
            JSONObject incoming=new JSONObject().put("segments",new JSONArray()
                    .put(new JSONObject().put("start",0).put("end",1).put("text","corrected"))
                    .put(new JSONObject().put("start",5).put("end",6).put("text","new")))
                    .put("images",new JSONArray().put(new JSONObject().put("url","fixture-earlier").put("second",1)));
            JSONObject merged=ClassroomMaterialUpdates.merge(old,incoming);
            check(merged.getJSONArray("segments").length()==2&&merged.getJSONArray("segments").getJSONObject(0).getString("text").equals("corrected"),"append subtitles and replace timed correction");
            check(!old.getJSONArray("images").getJSONObject(0).has("cache_index")&&old.getJSONArray("segments").getJSONObject(0).getString("text").equals("old"),"merge does not mutate persisted inputs");
            int before=client.downloads;
            JSONArray prepared=ClassroomImages.prepare(dir,merged,client,control,p->{});
            check(client.downloads==before+1&&new File(dir,"raw-1.img").isFile(),"earlier arriving slide gets a new cache file");
            check(prepared.getJSONObject(prepared.length()-1).getString("raw").equals("raw-0.img"),"chronological sorting preserves original raw identity");
            merged=ClassroomMaterialUpdates.merge(merged,incoming);
            check(merged.getJSONArray("segments").length()==2&&merged.getJSONArray("images").length()==2,"repeated polls do not duplicate data");
            ClassroomImages.prepare(dir,merged,client,control,p->{});
            check(client.downloads==before+1,"all valid observed slides reuse original downloader cache");
            JSONObject empty=new JSONObject().put("segments",new JSONArray()).put("images",new JSONArray());
            merged=ClassroomMaterialUpdates.merge(merged,empty);
            check(merged.getJSONArray("segments").length()==2&&merged.getJSONArray("images").length()==2,"empty observation preserves previous subtitle and image chunks");
            JSONObject finalRecording=new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("start",.2).put("end",2).put("text","authoritative sentence")))
                    .put("images",new JSONArray().put(new JSONObject().put("url","fixture-earlier").put("second",.5))).put("raw_images",1);
            JSONObject complete=ClassroomMaterialUpdates.complete(merged,finalRecording);
            check(complete.getJSONArray("segments").toString().equals(finalRecording.getJSONArray("segments").toString()),"processed transcript replaces all provisional timing and text");
            check(complete.getJSONArray("images").length()==1&&complete.getJSONArray("images").getJSONObject(0).getDouble("second")==.5&&complete.getInt("raw_images")==1,"processed image manifest matches direct download and removes provisional-only slides");
            check(complete.getJSONArray("images").getJSONObject(0).getInt("cache_index")==1,"processed recording reuses matching image cache identity");
            before=client.downloads;ClassroomImages.prepare(dir,complete,client,control,p->{});check(client.downloads==before,"final material preparation reuses downloaded matching PPT");
            check(!finalRecording.getJSONArray("images").getJSONObject(0).has("cache_index"),"completion preserves authoritative input");
            old.getJSONArray("images").getJSONObject(0).put("cache_index",-1);
            try{ClassroomMaterialUpdates.merge(old,empty);throw new AssertionError("negative cache index");}
            catch(IllegalArgumentException expected){checks++;}
            control.cancel();int downloads=client.downloads;
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("cancel"); }
            catch(InterruptedIOException e){check(downloads==client.downloads,"cancel performs no download");}
        } finally {for(File f:dir.listFiles())f.delete();dir.delete();}
        System.out.println("Classroom image checks: "+checks+" passed");
    }
}
