import com.donglan.chrona.classroom.*;
import com.donglan.chrona.net.RequestControl;
import java.io.*;
import java.nio.file.*;
import org.json.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class ClassroomImagesCheck {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static int[] flat(int value){int[] a=new int[96*96];Arrays.fill(a,value);return a;}
    static int[] proof(){int[] a=flat(245);for(int y=8;y<89;y+=10)for(int x=8;x<89;x++)if(x%7<5)a[y*96+x]=35;return a;}
    static byte[] image(int[] gray)throws Exception {
        BufferedImage image=new BufferedImage(96,96,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<96;y++)for(int x=0;x<96;x++){int v=gray[y*96+x];image.setRGB(x,y,(v<<16)|(v<<8)|v);}
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    static JSONArray frames(File dir,int[]... frames)throws Exception {
        JSONArray images=new JSONArray();
        for(int i=0;i<frames.length;i++) {
            Files.write(new File(dir,"raw-"+i+".img").toPath(),image(frames[i]));
            images.put(new JSONObject().put("cache_index",i).put("url","cached").put("second",i*10));
        }
        try(RequestControl control=new RequestControl(60000)) {
            ClassroomClient client=new ClassroomClient();
            JSONArray result=ClassroomImages.prepare(dir,new JSONObject().put("images",images),client,control,p->{});
            check(client.downloads==0,"quality processing reuses original bytes");return result;
        }
    }
    static void imageQualityChecks(File dir,String[] args)throws Exception {
        int[] blank=flat(245),black=flat(0),title=flat(245),full=proof();
        for(int x=40;x<56;x++)title[8*96+x]=20;
        int[] letterbox=title.clone();for(int y=0;y<20;y++)Arrays.fill(letterbox,y*96,(y+1)*96,0);
        for(int y=76;y<96;y++)Arrays.fill(letterbox,y*96,(y+1)*96,0);
        JSONObject fullQuality=ClassroomImageQuality.analyze(full,96,96);
        for(int[] sparse:new int[][]{blank,black,title,letterbox}) {
            JSONObject quality=ClassroomImageQuality.analyze(sparse,96,96);
            check(quality.getString("confidence").equals("low"),"white/black/title/letterbox sparse content is low information");
            check(quality.getDouble("score")<fullQuality.getDouble("score"),"complete formula page outranks sparse content");
        }
        check(fullQuality.getString("confidence").equals("normal"),"complete formula page has normal information density");
        check(ClassroomImageQuality.analyze(new int[36],6,6).getDouble("blank_fraction")==1,"minimum supported dimensions have nonempty tiles after ROI trimming");
        int[] boxedBlank=flat(245);for(int y=0;y<20;y++)Arrays.fill(boxedBlank,y*96,(y+1)*96,16);
        for(int y=76;y<96;y++)Arrays.fill(boxedBlank,y*96,(y+1)*96,16);
        check(ClassroomImageQuality.analyze(boxedBlank,96,96).getDouble("blank_fraction")==1,"black letterbox boundary is excluded from content statistics");
        int[] mixedBorder=title.clone();
        for(int y=0;y<96;y++)for(int x=0;x<13;x++) {
            int value=x<11?16:x==11?72:190;
            mixedBorder[y*96+x]=value;mixedBorder[y*96+95-x]=value;
        }
        JSONObject mixedQuality=ClassroomImageQuality.analyze(mixedBorder,96,96);
        check(mixedQuality.getString("confidence").equals("low")&&mixedQuality.getDouble("blank_fraction")>=.8,
                "two-pixel compressed black/page transition does not turn sparse title into content");
        File mixedJpeg=new File(dir,"mixed-border-title.jpg");
        javax.imageio.ImageWriter writer=ImageIO.getImageWritersByFormatName("jpeg").next();
        try(javax.imageio.stream.ImageOutputStream out=ImageIO.createImageOutputStream(mixedJpeg)) {
            writer.setOutput(out);javax.imageio.ImageWriteParam params=writer.getDefaultWriteParam();
            params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);params.setCompressionQuality(.92f);
            writer.write(null,new javax.imageio.IIOImage(ImageIO.read(new ByteArrayInputStream(image(mixedBorder))),null,null),params);
        }finally{writer.dispose();}
        JSONObject compressedQuality=ClassroomImages.quality(mixedJpeg);
        check(compressedQuality.getString("confidence").equals("low"),"JPEG92 mixed-border title stays low through production file decoding");
        System.out.println("Mixed-border title: gray="+mixedQuality+", JPEG92="+compressedQuality);
        int[] horizontalMixed=title.clone();
        for(int y=0;y<13;y++)for(int x=0;x<96;x++) {
            int value=y<11?16:y==11?90:205;
            horizontalMixed[y*96+x]=value;horizontalMixed[(95-y)*96+x]=value;
        }
        check(ClassroomImageQuality.analyze(horizontalMixed,96,96).getString("confidence").equals("low"),
                "horizontal resampling transition is excluded only after detected letterbox");
        int[] boxedProof=full.clone();
        for(int y=0;y<96;y++)for(int x=0;x<13;x++) {
            int value=x<11?16:x==11?72:190;
            boxedProof[y*96+x]=value;boxedProof[y*96+95-x]=value;
        }
        check(ClassroomImageQuality.analyze(boxedProof,96,96).getString("confidence").equals("normal"),
                "complete formula page stays normal behind compressed letterbox");
        int[] noBorderContent=flat(245);
        for(int y=0;y<96;y++)for(int x:new int[]{0,1,94,95})noBorderContent[y*96+x]=80;
        check(ClassroomImageQuality.analyze(noBorderContent,96,96).getDouble("detail_fraction")>0,
                "without detected black bars real edge content receives no transition guard");
        int[] cappedBorder=flat(245);
        for(int y=0;y<96;y++)for(int x=0;x<24;x++)cappedBorder[y*96+x]=0;
        for(int y=0;y<96;y++)cappedBorder[y*96+24]=80;
        check(ClassroomImageQuality.analyze(cappedBorder,96,96).getDouble("detail_fraction")>0,
                "transition guard cannot exclude a column beyond the quarter-edge cap");
        JSONArray result=frames(dir,full,full.clone(),full.clone());
        check(result.length()==1&&result.getJSONObject(0).getInt("index")==2,"exact repeats keep one later identity");
        check(result.getJSONObject(0).has("quality"),"each candidate includes quality metadata");
        byte[] original=Files.readAllBytes(new File(dir,"raw-0.img").toPath());
        ClassroomImages.quality(new File(dir,"raw-0.img"));
        check(Arrays.equals(original,Files.readAllBytes(new File(dir,"raw-0.img").toPath())),"quality analysis preserves raw bytes");
        int[] added=full.clone();added[50*96+40]=25;added[50*96+41]=25;
        check(ClassroomImages.difference(full,added)<1,"small formula change evades global mean alone");
        check(frames(dir,full,added).length()==2,"small local formula addition is retained");
        int[] faintAddition=full.clone();faintAddition[50*96+40]-=20;
        check(frames(dir,full,faintAddition).length()==2,"small low-contrast local formula change is retained");
        check(frames(dir,added,full).length()==2,"local formula erasure never overwrites fuller frame");
        check(frames(dir,full,title).length()==2,"different page/title transition is retained");
        int[] faded=full.clone();for(int i=0;i<faded.length;i++)if(faded[i]==35)faded[i]=36;
        result=frames(dir,full,faded);
        check(result.length()==1&&result.getJSONObject(0).getInt("index")==0,"near-duplicate information decline preserves earlier complete frame");
        int[][] drift=new int[8][];for(int i=0;i<drift.length;i++)drift[i]=flat(100+i);
        result=frames(dir,drift);
        check(result.length()==4,"fixed first anchor prevents gradual drift from becoming one chain");
        JSONArray candidates=new JSONArray();
        for(int i=0;i<12;i++)candidates.put(new JSONObject().put("raw","raw-"+i+".img").put("index",i).put("second",i*10)
                .put("quality",new JSONObject().put("score",i%3==1?.9:.01)));
        result=ClassroomImages.select(candidates,4);
        for(int i=0;i<4;i++)check(result.getJSONObject(i).getInt("index")==i*3+1,"quality-ranked temporal bucket preserves coverage and identity");
        check(ClassroomImages.select(candidates,1).getJSONObject(0).getInt("index")==10,"one-image budget chooses information instead of final blank");
        result=ClassroomImages.select(candidates,12);
        for(int i=0;i<12;i++)check(result.getJSONObject(i).getInt("index")==i,"full budget keeps every candidate once including final endpoint");
        JSONArray legacy=new JSONArray();for(int i=0;i<7;i++)legacy.put(new JSONObject().put("index",i).put("second",i));
        result=ClassroomImages.select(legacy,3);
        check(result.getJSONObject(0).getInt("index")==0&&result.getJSONObject(1).getInt("index")==3&&result.getJSONObject(2).getInt("index")==6,"legacy candidates keep exact uniform sampling");
        check(ClassroomImages.select(legacy,1).getJSONObject(0).getInt("index")==6,"legacy one-image budget keeps final image");
        check(ClassroomImages.select(legacy,0).length()==0&&ClassroomImages.select(legacy,-1).length()==0,"nonpositive budget is empty");
        JSONArray irregular=new JSONArray();int[] times={0,1,2,3,4,100,101,102};
        for(int i=0;i<times.length;i++)irregular.put(new JSONObject().put("index",i).put("second",times[i]).put("quality",new JSONObject().put("score",i/10.0)));
        result=ClassroomImages.select(irregular,3);
        check(result.length()==3&&result.getJSONObject(0).getDouble("second")==4&&result.getJSONObject(2).getDouble("second")==102,"time buckets cover widely separated clusters without duplicate slots");
        if(args.length>0) {
            File evidence=new File(args[0]);File reference=new File(evidence,"ref-proof-blank.jpg");
            File clear=args.length>1?new File(args[1],"image-61.jpg"):new File(evidence,"export-image-61-preview.jpg");
            File sparse=args.length>1?new File(args[1],"image-48.jpg"):new File(evidence,"export-image-48-preview.jpg");
            if(reference.isFile()&&clear.isFile()&&sparse.isFile()) {
                JSONObject referenceQuality=ClassroomImages.quality(reference),clearQuality=ClassroomImages.quality(clear),sparseQuality=ClassroomImages.quality(sparse);
                System.out.println("Observed quality: reference="+referenceQuality+", image-48="+sparseQuality+", image-61="+clearQuality);
                check(referenceQuality.getDouble("score")<clearQuality.getDouble("score"),"attached blank proof scores below exported complete proof");
                check(sparseQuality.getDouble("score")<clearQuality.getDouble("score"),"actual exported image-48 scores below image-61");
                check(referenceQuality.getString("confidence").equals("low")&&sparseQuality.getString("confidence").equals("low"),"attached and actual title-only proof are low information");
                JSONObject report=new JSONObject().put("reference_blank",referenceQuality).put("image_48",sparseQuality).put("image_61",clearQuality);
                Files.writeString(new File(evidence,"quality-sample-results.json").toPath(),report.toString(2));
                System.out.println("Sample information heuristics: "+report);
            }
        }
    }
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
        File qualityDir=new File(dir,"quality");qualityDir.mkdir();
        try{imageQualityChecks(qualityDir,args);}finally{for(File f:qualityDir.listFiles())f.delete();qualityDir.delete();}
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
