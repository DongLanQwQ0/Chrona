package com.donglan.chrona.classroom;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;
import com.donglan.chrona.net.RequestControl;

/** Local committed-correction reuse and the real missing-image preparation path. No provider calls. */
public final class ClassroomRegenerationCheck {
    private static int checks;
    private static void require(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    private static JSONObject read(File dir,String name)throws Exception{return ClassroomStore.read(new File(dir,name));}
    private static void write(File dir,String name,JSONObject value)throws Exception{ClassroomStore.write(new File(dir,name),value);}
    private static JSONArray blocks(JSONObject material)throws Exception{return ClassroomEnhancedSummary.blocks(ClassroomSummary.lessonInput(material.getJSONArray("segments"),new JSONArray()).getJSONArray("segments"));}
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0],"regeneration-"+System.nanoTime());dir.mkdirs();
        JSONObject material=new JSONObject().put("raw_images",1).put("segments",new JSONArray()
                .put(new JSONObject().put("start",0).put("end",30).put("text","甲".repeat(3500)))
                .put(new JSONObject().put("start",31).put("end",60).put("text","乙".repeat(3500))))
                .put("images",new JSONArray().put(new JSONObject().put("second",0).put("cache_index",8).put("url","fixture:slide")));
        write(dir,"materials.json",material);JSONArray sourceBlocks=blocks(material);
        JSONArray corrected=new JSONArray().put("[0] 已完成修正文稿甲").put("[1] 已完成修正文稿乙");
        JSONObject old=new JSONObject().put("run","old-run").put("blocks",sourceBlocks).put("corrected",corrected)
                .put("conversation",new JSONObject().put("messages",new JSONArray().put(new JSONObject().put("content","原输入\\n不得重写"))));
        byte[] original=("  \n"+old.toString()+"\n").getBytes(StandardCharsets.UTF_8);
        ClassroomStore.writeBytes(new File(dir,"run.json"),original);
        JSONObject summary=new JSONObject().put("run","old-run").put("complete",true).put("source_blocks",2).put("marker","旧成品");
        write(dir,"summary.json",summary);String materialsBefore=read(dir,"materials.json").toString();
        ClassroomCorrectionCache.capture(dir);ClassroomStore.beginSummaryRun(dir);
        File[] audit=new File(dir,"runs").listFiles();require(audit!=null&&audit.length==1,"new run archives exactly the previous audit");
        require(Arrays.equals(original,Files.readAllBytes(audit[0].toPath())),"previous audit is preserved byte for byte, not reconstructed");
        require(read(dir,"run.json").length()==0,"fresh run has no legacy schema, messages, selected images or draft");
        require(read(dir,"materials.json").toString().equals(materialsBefore)&&read(dir,"summary.json").toString().equals(summary.toString()),"raw subtitle material and previous readable summary are untouched");
        JSONObject fresh=new JSONObject().put("run","fresh-run").put("blocks",sourceBlocks);
        ClassroomCorrectionCache.restore(dir,fresh,sourceBlocks);
        require(fresh.getJSONArray("corrected").toString().equals(corrected.toString()),"all committed correction parts reused unchanged");
        require(fresh.getString("correction_reused_from").equals("old-run")&&!fresh.has("conversation"),"new run records provenance but does not import a previous conversation");
        require(read(dir,"corrected.json").getBoolean("complete")&&read(dir,"corrected.json").getString("run").equals("fresh-run"),"reused corrected document belongs to the new run");
        JSONObject own=new JSONObject().put("run","own-run").put("corrected",new JSONArray().put("当前轮已提交"));
        ClassroomCorrectionCache.restore(dir,own,sourceBlocks);require(own.getJSONArray("corrected").length()==1,"continuation never overwrites its own committed correction state");
        JSONArray different=new JSONArray(sourceBlocks.toString());different.getJSONArray(0).getJSONObject(0).put("text","不同原始内容");
        JSONObject changed=new JSONObject().put("run","changed-run");ClassroomCorrectionCache.restore(dir,changed,different);
        require(!changed.has("corrected"),"changed source text cannot reuse a stale correction");
        write(dir,"run.json",new JSONObject(old.toString()).put("corrected",new JSONArray().put(corrected.getString(0))));
        ClassroomCorrectionCache.capture(dir);JSONObject partial=new JSONObject().put("run","partial-run");ClassroomCorrectionCache.restore(dir,partial,sourceBlocks);
        require(partial.getJSONArray("corrected").length()==1&&!read(dir,"corrected.json").getBoolean("complete"),"only successful partial correction blocks are reused");
        write(dir,"run.json",new JSONObject(old.toString()).put("corrected",new JSONArray()).put("pending_reply","未提交片段"));
        ClassroomCorrectionCache.capture(dir);JSONObject failed=new JSONObject().put("run","failed-run");ClassroomCorrectionCache.restore(dir,failed,sourceBlocks);
        require(!failed.has("corrected"),"uncommitted response fragments are not correction input");
        write(dir,"run.json",new JSONObject(old.toString()).put("blocks",different));
        write(dir,"corrected.json",new JSONObject().put("run","old-run").put("parts",corrected).put("complete",true));
        ClassroomCorrectionCache.capture(dir);JSONObject stale=new JSONObject().put("run","stale-run");ClassroomCorrectionCache.restore(dir,stale,sourceBlocks);
        require(!stale.has("corrected"),"mismatching known source blocks cannot fall through to a legacy corrected file");
        Files.delete(new File(dir,"run.json").toPath());ClassroomCorrectionCache.capture(dir);
        JSONObject unsupported=new JSONObject().put("run","unsupported-run");ClassroomCorrectionCache.restore(dir,unsupported,sourceBlocks);
        require(!unsupported.has("corrected"),"legacy correction without source provenance is not rebound merely by matching block count");
        write(dir,"corrected.json",new JSONObject().put("run","old-run").put("parts",corrected).put("blocks",sourceBlocks).put("complete",true));ClassroomCorrectionCache.capture(dir);
        JSONObject cleaned=new JSONObject().put("run","cleaned-run");ClassroomCorrectionCache.restore(dir,cleaned,sourceBlocks);
        require(cleaned.getJSONArray("corrected").length()==2,"paired complete corrected file survives cleaned run metadata");
        JSONObject changedMaterials=new JSONObject(material.toString());changedMaterials.getJSONArray("segments").getJSONObject(0).put("text","不同原文且分块数不变"+"丙".repeat(3500));
        write(dir,"materials.json",changedMaterials);ClassroomCorrectionCache.capture(dir);JSONObject changedAfterCleanup=new JSONObject().put("run","changed-after-cleanup");ClassroomCorrectionCache.restore(dir,changedAfterCleanup,blocks(changedMaterials));
        require(!changedAfterCleanup.has("corrected"),"cleaned run plus changed transcript of the same block count cannot reuse the old correction");
        write(dir,"materials.json",material);
        write(dir,"correction-cache.json",new JSONObject().put("blocks",sourceBlocks).put("parts",new JSONArray()));
        write(dir,"corrected.json",new JSONObject().put("run","wrong-run").put("parts",corrected).put("complete",true));ClassroomCorrectionCache.capture(dir);
        JSONObject unpaired=new JSONObject().put("run","unpaired-run");ClassroomCorrectionCache.restore(dir,unpaired,sourceBlocks);require(!unpaired.has("corrected"),"unpaired legacy correction is not reused");
        require(!ClassroomImages.hasCompleteCache(dir,material),"surviving metadata with cleaned raw image is not a complete cache");
        Files.write(new File(dir,"raw-8.img").toPath(),new byte[0]);require(!ClassroomImages.hasCompleteCache(dir,material),"empty raw file also requires download");
        java.awt.image.BufferedImage bitmap=new java.awt.image.BufferedImage(96,96,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<96;y++)for(int x=0;x<96;x++)bitmap.setRGB(x,y,(x%8==0||y%8==0)?0x101010:0xeeeeee);
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();javax.imageio.ImageIO.write(bitmap,"jpg",encoded);
        ClassroomClient client=new ClassroomClient();client.images.put("fixture:slide",encoded.toByteArray());
        write(dir,"candidates.json",new JSONObject().put("visual_version",ClassroomFigureReview.VERSION).put("images",new JSONArray().put(new JSONObject().put("raw","raw-8.img").put("index",8).put("second",0))));
        ClassroomLesson lesson=new ClassroomLesson("1","2","数学","第1–2节","教室","6",1L);
        try(RequestControl control=new RequestControl(20000)){
            JSONObject prepared=ClassroomSummary.prepare(dir,lesson,client,control,p->{});
            require(client.downloads==1&&client.materialReads==0&&prepared.getInt("candidates")==1,"missing image redownloads through the existing preparer without refetching subtitles");
            require(ClassroomImages.hasCompleteCache(dir,material),"redownload restores the precise cache_index raw identity");
            ClassroomSummary.prepare(dir,lesson,client,control,p->{});
            require(client.downloads==1,"a second preparation reuses complete cached images");
        }
        require(read(dir,"materials.json").toString().equals(materialsBefore),"image supplementation preserves original subtitle content");
        android.content.Context context=new android.content.Context(new File(args[0],"cleanup-context-"+System.nanoTime()));
        ClassroomStore store=new ClassroomStore(context,"cleanup-account");
        File cleanup=store.localLesson(lesson);write(cleanup,"materials.json",material);write(cleanup,"candidates.json",read(dir,"candidates.json"));
        write(cleanup,"summary.json",new JSONObject(summary.toString()).put("parts",new JSONArray()).put("images",new JSONArray()));
        write(cleanup,"run.json",new JSONObject(old.toString()).put("final",new JSONObject()));
        write(cleanup,"corrected.json",new JSONObject().put("run","old-run").put("parts",corrected).put("complete",true));
        Files.write(new File(cleanup,"raw-8.img").toPath(),encoded.toByteArray());
        require(ClassroomStore.cleanUnused(context,true,System.currentTimeMillis())>0&&!new File(cleanup,"run.json").exists(),"real cache cleanup retires raw images and the completed source run");
        require(read(cleanup,"corrected.json").getJSONArray("blocks").length()==2,"cleanup transfers same-run source blocks before removing the checkpoint");
        ClassroomCorrectionCache.capture(cleanup);JSONObject afterCleanup=new JSONObject().put("run","after-cleanup");ClassroomCorrectionCache.restore(cleanup,afterCleanup,sourceBlocks);
        require(afterCleanup.getJSONArray("corrected").length()==2,"regeneration after real cleanup reuses committed corrections with verified original text");
        System.out.println("ClassroomRegenerationCheck PASS: "+checks+" checks; committed correction reuse, immutable audit, source isolation, image redownload");
    }
}
