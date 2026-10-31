package com.donglan.chrona.classroom;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Pure presentation-policy checks; optional real export is read-only, with derived output in build. */
public final class ClassroomFigureReviewCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    interface Task{void run()throws Exception;}
    private static void rejects(Task task,String message)throws Exception{try{task.run();throw new AssertionError(message);}catch(IOException|JSONException expected){checks++;}}
    private static JSONObject image(String file,String confidence)throws Exception{return new JSONObject().put("file",file).put("second",1).put("quality",new JSONObject().put("confidence",confidence).put("blank_fraction",confidence.equals("low")?.95:.25));}
    private static JSONObject review(String file,String status,String confidence)throws Exception{return new JSONObject().put("file",file).put("status",status).put("confidence",confidence).put("visible",status.equals("usable")?"实际可见公式 f(x) 与辅助函数":"").put("reason","对应画面可见依据");}
    private static JSONObject part(String text)throws Exception{return new JSONObject().put("overview","").put("notes",new JSONArray().put(new JSONObject().put("title","证明").put("text",text).put("sources",new JSONArray().put(0)).put("images",new JSONArray().put("image-48.jpg").put("images/image-61.jpg")))).put("assignments",new JSONArray());}
    private static JSONObject inlinePart(String text)throws Exception{JSONObject p=part(text);p.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray());return p;}
    private static void policy()throws Exception{
        JSONArray allowed=new JSONArray().put(image("image-48.jpg","low")).put(image("image-61.jpg","normal"));
        String text="公式 $f(x)$ 不变。![错误证明](images/image-48.jpg)\n![完整证明](images/image-61.jpg)\n`![代码](images/image-48.jpg)`\n```text\n![围栏](images/image-48.jpg)\n```\n    ![缩进](images/image-48.jpg)";
        JSONObject result=part(text).put("image_reviews",new JSONArray().put(review("image-48.jpg","usable","high")).put(review("image-61.jpg","usable","high")));
        JSONArray checked=ClassroomFigureReview.apply(result,allowed,allowed);String filtered=result.getJSONArray("notes").getJSONObject(0).getString("text");
        check(!filtered.contains("![错误证明]"),"local blank confidence caps overconfident AI and removes misleading caption");
        check(filtered.contains("![完整证明]")&&filtered.contains("$f(x)$"),"informative picture and mathematical text preserved");
        check(filtered.contains("`![代码]")&&filtered.contains("![围栏]")&&filtered.contains("![缩进]"),"literal code examples are not edited");
        check(result.getJSONArray("notes").getJSONObject(0).getJSONArray("images").length()==1,"array and inline references share presentation decision");
        check(!checked.getJSONObject(0).getBoolean("included")&&checked.getJSONObject(0).getString("effective_confidence").equals("low"),"low local confidence recorded without rewriting model confidence");
        check(checked.getJSONObject(0).getString("confidence").equals("high"),"actual model confidence retained for audit");
        for(String status:List.of("blank","title_only","occluded","unreadable","irrelevant")){
            JSONObject p=part("![遮挡图](images/image-48.jpg)").put("image_reviews",new JSONArray().put(review("image-48.jpg",status,"high")).put(review("image-61.jpg","usable","high")));
            ClassroomFigureReview.apply(p,allowed,allowed);check(!p.getJSONArray("notes").getJSONObject(0).getString("text").contains("遮挡图"),"rejected visual state cannot be embedded: "+status);
        }
        JSONObject missing=part("").put("image_reviews",new JSONArray());rejects(()->ClassroomFigureReview.apply(missing,allowed,allowed),"missing per-image evidence rejected");
        JSONObject duplicate=part("").put("image_reviews",new JSONArray().put(review("image-48.jpg","blank","low")).put(review("image-48.jpg","blank","low")));rejects(()->ClassroomFigureReview.apply(duplicate,allowed,allowed),"duplicate review rejected");
        JSONObject invented=part("").put("image_reviews",new JSONArray().put(review("image-999.jpg","usable","high")));rejects(()->ClassroomFigureReview.apply(invented,allowed,allowed),"unseen image cannot enter visual evidence");
        JSONObject emptyVisible=part("").put("image_reviews",new JSONArray().put(review("image-48.jpg","usable","high").put("visible","")));rejects(()->ClassroomFigureReview.apply(emptyVisible,allowed,allowed),"usable verdict needs actual visible evidence");
        JSONObject state=new JSONObject();ClassroomFigureReview.merge(state,checked);JSONObject edited=part("![编辑误加](images/image-48.jpg) ![正确](images/image-61.jpg)");ClassroomFigureReview.filter(edited,state.getJSONArray("figure_reviews"));
        check(!edited.getJSONArray("notes").getJSONObject(0).getString("text").contains("编辑误加"),"whole-lesson editor cannot resurrect rejected frame");
        check(ClassroomFigureReview.policy(null)==1,"new runs use visual review policy");
        check(ClassroomFigureReview.policy(new JSONObject().put("conversation",new JSONObject()))==0,"legacy unfinished messages keep original visual contract");
        check(ClassroomFigureReview.schema(0).isEmpty()&&ClassroomFigureReview.instructions(0).isEmpty(),"legacy prefix and schema unchanged");
        codeExamples();astImages();priorRejections();
    }
    private static void codeExamples()throws Exception{
        String image="![拒绝图](images/image-48.jpg)";
        JSONArray rejected=new JSONArray().put(review("image-48.jpg","occluded","high").put("included",false));
        for(String prefix:List.of("普通文字中未配对的 ` 符号。\n","普通文字中未配对的 `` 符号。\n","```text\n示例\n  ```\n","  ~~~text\n示例\n   ~~~~  \n")){
            JSONObject p=part(prefix+image);ClassroomFigureReview.filter(p,rejected);
            check(p.getJSONArray("notes").getJSONObject(0).getString("text").equals(prefix),"visible rejected image is removed after unmatched tick or indented fence close: "+prefix);
        }
        for(String literal:List.of("`"+image+"`","`` ` "+image+" ``","  ```markdown\n"+image+"\n ```", " ~~~markdown\n"+image+"\n   ~~~~", "```markdown\n"+image,"~~~markdown\n"+image,"    "+image,"\t"+image,"\\"+image)){
            JSONObject p=part(literal);ClassroomFigureReview.filter(p,rejected);
            check(p.getJSONArray("notes").getJSONObject(0).getString("text").equals(literal),"actual code or escaped image remains literal: "+literal);
        }
        for(String prefix:List.of("```text\n ``` trailing text\n", "~~~text\n```\n", "````text\n```\n")){
            JSONObject p=part(prefix+image);ClassroomFigureReview.filter(p,rejected);
            check(p.getJSONArray("notes").getJSONObject(0).getString("text").equals(prefix+image),"invalid fence close remains code: "+prefix);
        }
        JSONObject escapedTick=part("\\` 普通文字 "+image);ClassroomFigureReview.filter(escapedTick,rejected);
        check(escapedTick.getJSONArray("notes").getJSONObject(0).getString("text").equals("\\` 普通文字 "),"escaped tick does not open a code span");
    }
    private static void priorRejections()throws Exception{
        JSONArray allowed=new JSONArray().put(image("image-48.jpg","normal"));
        for(String status:List.of("occluded","irrelevant")){
            JSONObject first=inlinePart("![旧图](images/image-48.jpg)").put("image_reviews",new JSONArray().put(review("image-48.jpg",status,"high")));
            JSONArray initial=ClassroomFigureReview.apply(first,allowed,allowed);JSONObject state=new JSONObject();ClassroomFigureReview.merge(state,initial);
            JSONObject raw=review("image-48.jpg","usable","high");String original=raw.toString();
            JSONObject later=inlinePart("![复活](images/image-48.jpg)").put("image_reviews",new JSONArray().put(raw));
            JSONArray checked=ClassroomFigureReview.apply(later,allowed,allowed,state.getJSONArray("figure_reviews"));
            check(later.getJSONArray("notes").getJSONObject(0).getString("text").isEmpty(),"later usable review cannot revive previous rejection: "+status);
            JSONObject effective=checked.getJSONObject(0);
            check(!effective.getBoolean("included")&&effective.getString("status").equals("usable")&&effective.getJSONObject("prior_rejection").getString("status").equals(status),"effective exclusion preserves current claim and original rejection: "+status);
            check(raw.toString().equals(original),"original AI review is not rewritten: "+status);
            // Even callers without prior context cannot overwrite the durable rejection during merge.
            JSONObject unaware=inlinePart("![复活](images/image-48.jpg)").put("image_reviews",new JSONArray().put(review("image-48.jpg","usable","high")));
            JSONArray newer=ClassroomFigureReview.apply(unaware,allowed,allowed);ClassroomFigureReview.merge(state,newer);
            JSONObject saved=state.getJSONArray("figure_reviews").getJSONObject(0);
            check(!saved.getBoolean("included")&&saved.getJSONObject("prior_rejection").getString("status").equals(status),"merge is independently monotonic for rejection: "+status);
            check(newer.getJSONObject(0).getBoolean("included"),"merge does not mutate supplied review records: "+status);
            ClassroomFigureReview.merge(state,checked);
            check(!state.getJSONArray("figure_reviews").getJSONObject(0).getJSONObject("prior_rejection").has("prior_rejection"),"repeated merge keeps a single original rejection cause: "+status);
        }
    }
    private static void astImages()throws Exception{
        String image="![rejected](images/image-48.jpg)";
        JSONArray rejected=new JSONArray().put(review("image-48.jpg","occluded","high").put("included",false));
        JSONObject continuation=inlinePart("Paragraph\n    "+image);ClassroomFigureReview.filter(continuation,rejected);
        check(continuation.getJSONArray("notes").getJSONObject(0).getString("text").equals("Paragraph\n    "),"four-space paragraph continuation is a real picture, not an indented code block");
        String quotedCode="> ```markdown\n> "+image;
        JSONObject quoted=inlinePart(quotedCode);ClassroomFigureReview.filter(quoted,rejected);
        check(quoted.getJSONArray("notes").getJSONObject(0).getString("text").equals(quotedCode),"unclosed fence inside a quote preserves literal image syntax");
        String reference="前 ![rejected][frame] 后\n\n[frame]: images/image-48.jpg\n";
        check(ClassroomDocument.inlineImages(reference).equals(Set.of("image-48.jpg")),"reference-style picture participates in the shared inventory");
        JSONObject ref=inlinePart(reference);ClassroomFigureReview.filter(ref,rejected);
        check(ref.getJSONArray("notes").getJSONObject(0).getString("text").equals("前  后\n\n[frame]: images/image-48.jpg\n"),"reference-style rejected picture is removed without editing its neighbors or definition");
        String unsafeCode="`![unsafe](../../secret)`\n\n> ```markdown\n> ![unsafe](https://example.invalid/image.jpg)\n> ```\n\n    ![unsafe](private.jpg)";
        check(ClassroomDocument.inlineImages(unsafeCode).isEmpty(),"unsafe filenames inside real code are not image references");
        check(ClassroomDocument.exportMarkdown(unsafeCode).equals(unsafeCode),"export leaves code image examples byte-for-byte unchanged");
        rejects(()->ClassroomDocument.inlineImages("![unsafe](../../secret)"),"actual unsafe image destinations remain rejected");
        String unicode="😀前 "+image+" 中 ![完整](image-61.jpg) 后𠮷";
        check(ClassroomMarkdownImages.rewrite(unicode,Set.of("image-48.jpg"),false).equals("😀前  中 ![完整](image-61.jpg) 后𠮷"),"UTF-16 source positions remove only the rejected picture after non-BMP text");
        String multiline="> 😀前 ![第一行\n> 第二行](\n> images/image-48.jpg\n> ) 邻文\n> 后续";
        check(ClassroomMarkdownImages.rewrite(multiline,Set.of("image-48.jpg"),false).equals("> 😀前 \n> \n> \n>  邻文\n> 后续"),"multiline picture deletion preserves quote prefixes, newlines and adjacent text");
        String list="- 前 ![一\n  二](images/image-48.jpg) 后";
        check(ClassroomMarkdownImages.rewrite(list,Set.of("image-48.jpg"),false).equals("- 前 \n   后"),"multiline picture deletion preserves a list container and its text");
        String canonical="![**粗体** \\[方括号\\] `f(x)` &amp;😀](image-61.jpg)";
        String exported=ClassroomDocument.exportMarkdown(canonical);
        check(exported.equals("![粗体 \\[方括号\\] f\\(x\\) \\&😀](images/image-61.jpg)"),"canonical image keeps alt text from text, emphasis and inline code, with literal punctuation escaped");
        check(ClassroomDocument.exportMarkdown("![说明][frame]\n\n[frame]: image-61.jpg").startsWith("![说明](images/image-61.jpg)"),"export canonicalizes reference-style images");
        String quotedMultiline="> 前 ![一\n> 二](image-61.jpg) 后";
        check(ClassroomDocument.exportMarkdown(quotedMultiline).equals("> 前 ![一&#10;二](images/image-61.jpg)\n>  后"),"canonical multiline alt keeps line-break content without injecting unquoted lines");
        String crlf="😀前 "+image+" 后\r\n原文";
        check(ClassroomMarkdownImages.rewrite(crlf,Set.of("image-48.jpg"),false).equals("😀前  后\r\n原文"),"source-span rewriting preserves CRLF and surrounding prose");
    }
    private static void actual(Path source,Path output)throws Exception{
        JSONArray allowed=new JSONArray(),reviews=new JSONArray();JSONObject report=new JSONObject();
        try(var images=Files.list(source.resolve("images"))){for(Path file:images.sorted().toList()){
            JSONObject quality=ClassroomImages.quality(file.toFile());allowed.put(new JSONObject().put("file",file.getFileName().toString()).put("second",0).put("quality",quality));
            // Deliberately simulate overconfident original references; no model is called here.
            reviews.put(review(file.getFileName().toString(),"usable","high"));report.put(file.getFileName().toString(),quality);
        }}
        String original=Files.readString(source.resolve("课堂总结.md"),StandardCharsets.UTF_8);
        JSONObject p=part(original);p.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray());p.put("image_reviews",reviews);
        JSONArray checked=ClassroomFigureReview.apply(p,allowed,allowed);String filtered=p.getJSONArray("notes").getJSONObject(0).getString("text");
        check(!filtered.contains("![柯西中值定理的错误证明](images/image-48.jpg)"),"actual empty proof picture removed despite confident original caption");
        check(filtered.contains("images/image-61.jpg"),"actual complete proof retained");
        Files.createDirectories(output.resolve("images"));Files.writeString(output.resolve("课堂总结.md"),filtered,StandardCharsets.UTF_8);
        Files.copy(source.resolve("sources.md"),output.resolve("sources.md"),StandardCopyOption.REPLACE_EXISTING);
        for(String file:ClassroomDocument.inlineImages(filtered))Files.copy(source.resolve("images").resolve(file),output.resolve("images").resolve(file),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(output.resolve("visual-audit.json"),new JSONObject().put("local_only",true).put("model_called",false).put("quality",report).put("reviews",checked).toString(2),StandardCharsets.UTF_8);
    }
    private static void cache(Path dir)throws Exception{
        Files.createDirectories(dir);java.awt.image.BufferedImage frame=new java.awt.image.BufferedImage(96,96,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<96;y++)for(int x=0;x<96;x++)frame.setRGB(x,y,(x%12<2||y%12<2)?0x101010:0xeeeeee);
        javax.imageio.ImageIO.write(frame,"png",dir.resolve("raw-0.img").toFile());
        for(int y=0;y<96;y++)for(int x=0;x<96;x++)frame.setRGB(x,y,0xeeeeee);
        javax.imageio.ImageIO.write(frame,"png",dir.resolve("raw-1.img").toFile());
        byte[] before=Files.readAllBytes(dir.resolve("raw-0.img"));JSONArray manifest=new JSONArray();
        for(int i=0;i<2;i++)manifest.put(new JSONObject().put("url","never://network").put("second",i).put("cache_index",i));
        JSONObject candidates=new JSONObject().put("images",new JSONArray().put(new JSONObject().put("raw","raw-1.img").put("index",1).put("second",1)));
        JSONObject materials=new JSONObject().put("images",manifest);
        try(var control=new com.donglan.chrona.net.RequestControl(10000)){
            ClassroomFigureReview.prepareQuality(dir.toFile(),candidates,materials,control,p->{});
            check(candidates.getJSONArray("images").length()==2&&candidates.getJSONArray("images").getJSONObject(0).getString("raw").equals("raw-0.img"),"old incomplete representative list is rebuilt from all cached source frames");
            check(Arrays.equals(before,Files.readAllBytes(dir.resolve("raw-0.img"))),"quality recomputation never changes original image bytes");
            String saved=Files.readString(dir.resolve("candidates.json"));
            ClassroomFigureReview.prepareQuality(dir.toFile(),candidates,materials,control,p->{});
            check(saved.equals(Files.readString(dir.resolve("candidates.json"))),"current visual version does not reprocess or rewrite cache");
            candidates.remove("visual_version");manifest.put(new JSONObject().put("url","never://network").put("second",3).put("cache_index",3));
            rejects(()->ClassroomFigureReview.prepareQuality(dir.toFile(),candidates,materials,control,p->{}),"cache-only upgrade fails explicitly rather than downloading a missing frame");
            check(saved.equals(Files.readString(dir.resolve("candidates.json"))),"failed cache upgrade does not publish a partial candidate list");
        }
    }
    public static void main(String[] args)throws Exception{policy();if(args.length==2){if(args[0].equals("--cache"))cache(Path.of(args[1]));else actual(Path.of(args[0]),Path.of(args[1]));}System.out.println("ClassroomFigureReviewCheck: "+checks+" passed; no AI calls");}
}
