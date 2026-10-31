package com.donglan.chrona.classroom;

import com.donglan.chrona.net.RequestControl;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Saved visual evidence and a conservative presentation gate, independent of transcript claims. */
public final class ClassroomFigureReview {
    public static final int VERSION=1;
    private static final Set<String> STATUSES=Set.of("usable","blank","title_only","occluded","unreadable","irrelevant");
    private static final Set<String> CONFIDENCES=Set.of("high","medium","low");
    private ClassroomFigureReview() { }
    public static int policy(JSONObject run){
        return run!=null&&run.has("conversation")&&!run.has("final")?run.optInt("figure_policy",0):VERSION;
    }
    public static String instructions(int policy){
        return policy==0?"":"\n每张附图必须独立检查实际可见内容，字幕只用于定位，不能把字幕提到的证明、公式或过程当作画面已显示。"
                +"空白、仅标题/页码、动画尚未展开、菜单或软件遮挡、难以辨认或无关的图不可充当完整证明配图。"
                +"quality是本地画面信息量启发式，不是语义正确率；大面积空白会降低置信度，即使仍可辅助读图，也不能据此臆造图中细节。"
                +"图注必须符合画面实际所示，部分步骤只能标作部分步骤；缺乏可靠配图时用文字保留知识，不强行配图。";
    }
    static String schema(int policy){
        return policy==0?"":"另必填顶层image_reviews数组，对本次每张实际附图及正文引用的此前附图逐一返回"
                +"{\"file\":\"实际file\",\"status\":\"usable|blank|title_only|occluded|unreadable|irrelevant\","
                +"\"confidence\":\"high|medium|low\",\"visible\":\"仅描述实际看见的文字、公式或图形，空白可为空\","
                +"\"reason\":\"为何支持当前笔记或为何排除\"}。同一file只出现一次。"
                +"只有usable且置信度不低的图才可进入notes.images和Markdown；仍返回被排除图的依据。低信息图可以参与理解，不为保留配图而虚构可见内容。";
    }
    static void prepareQuality(File dir,JSONObject candidates,JSONObject materials,RequestControl control,ClassroomImages.Progress progress)throws Exception{
        if(candidates.optInt("visual_version")==VERSION)return;
        // Rebuild from the complete local manifest: merely scoring the old representatives
        // could never recover a fuller frame discarded by the former later-frame rule.
        if(materials.optJSONArray("images")!=null)candidates.put("images",ClassroomImages.prepare(dir,materials,null,control,progress));
        else{
            JSONArray images=candidates.getJSONArray("images");
            for(int i=0;i<images.length();i++){
                control.check();JSONObject image=images.getJSONObject(i);String raw=image.getString("raw");
                if(!raw.matches("raw-[0-9]+\\.img"))throw new IOException("截图缓存名称无效");
                progress.update(new ClassroomProgress("检查截图信息量",i,images.length(),"第 "+(i+1)+" 张"));
                image.put("quality",ClassroomImages.quality(new File(dir,raw)));
            }
        }
        candidates.put("visual_version",VERSION);ClassroomStore.write(new File(dir,"candidates.json"),candidates);
    }
    static JSONObject inventory(JSONObject source)throws Exception{
        JSONObject image=new JSONObject().put("file",source.getString("file")).put("second",source.getDouble("second"));
        if(source.optJSONObject("quality")!=null)image.put("quality",new JSONObject(source.getJSONObject("quality").toString()));
        return image;
    }
    /** Validate the raw response before filtering; never turn unknown file references into success. */
    static JSONArray apply(JSONObject part,JSONArray allowed,JSONArray required)throws Exception{
        return apply(part,allowed,required,null);
    }
    static JSONArray apply(JSONObject part,JSONArray allowed,JSONArray required,JSONArray priorReviews)throws Exception{
        JSONArray rows=part.getJSONArray("image_reviews");Map<String,JSONObject> inventory=new LinkedHashMap<>(),reviews=new LinkedHashMap<>();
        Map<String,JSONObject> rejected=new LinkedHashMap<>();
        if(priorReviews!=null)for(int i=0;i<priorReviews.length();i++){
            JSONObject prior=priorReviews.getJSONObject(i);if(!prior.optBoolean("included"))rejected.putIfAbsent(prior.getString("file"),prior);
        }
        for(int i=0;i<allowed.length();i++){JSONObject image=allowed.getJSONObject(i);inventory.put(image.getString("file"),image);}
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String file=row.getString("file");
            if(!inventory.containsKey(file)||reviews.containsKey(file))throw new IOException("图片核对出现未附图或重复编号："+file);
            String status=row.getString("status"),confidence=row.getString("confidence"),visible=row.getString("visible"),reason=row.getString("reason");
            if(!STATUSES.contains(status)||!CONFIDENCES.contains(confidence)||reason.isBlank())throw new IOException("图片核对状态或依据无效："+file);
            if("usable".equals(status)&&visible.isBlank())throw new IOException("图片缺少实际可见内容："+file);
            JSONObject quality=inventory.get(file).optJSONObject("quality");
            boolean localLow=quality!=null&&"low".equals(quality.optString("confidence"));
            String effective=localLow?"low":confidence;
            JSONObject checked=new JSONObject(row.toString()).put("effective_confidence",effective)
                    .put("included","usable".equals(status)&&!"low".equals(effective));
            retainRejection(checked,rejected.get(file));
            if(quality!=null)checked.put("quality",new JSONObject(quality.toString()));reviews.put(file,checked);
        }
        Set<String> needed=new LinkedHashSet<>();for(int i=0;i<required.length();i++)needed.add(required.getJSONObject(i).getString("file"));
        needed.addAll(ClassroomDocument.referencedImages(new JSONObject().put("overview",part.optString("overview")).put("parts",new JSONArray().put(part))));
        for(String file:needed)if(!reviews.containsKey(file))throw new JSONException("image_reviews缺少实际图片核对："+file);
        JSONArray checked=new JSONArray();for(JSONObject row:reviews.values())checked.put(row);
        filter(part,checked);part.put("image_reviews",checked);return checked;
    }
    static void merge(JSONObject state,JSONArray reviews)throws Exception{
        Map<String,JSONObject> values=new LinkedHashMap<>();JSONArray saved=state.optJSONArray("figure_reviews");
        if(saved!=null)for(int i=0;i<saved.length();i++){JSONObject row=saved.getJSONObject(i);values.put(row.getString("file"),row);}
        for(int i=0;i<reviews.length();i++){
            JSONObject row=new JSONObject(reviews.getJSONObject(i).toString());String file=row.getString("file");
            retainRejection(row,values.get(file));values.put(file,row);
        }
        JSONArray rows=new JSONArray();for(JSONObject row:values.values())rows.put(row);state.put("figure_reviews",rows);
    }
    private static void retainRejection(JSONObject row,JSONObject prior)throws JSONException{
        if(prior==null||prior.optBoolean("included"))return;
        JSONObject cause=prior.optJSONObject("prior_rejection");
        row.put("included",false).put("prior_rejection",new JSONObject((cause==null?prior:cause).toString()));
    }
    /** The editorial pass cannot resurrect frames already rejected during visual analysis. */
    static void filter(JSONObject part,JSONArray reviews)throws Exception{
        if(reviews==null)return;Set<String> rejected=new LinkedHashSet<>();
        for(int i=0;i<reviews.length();i++){JSONObject row=reviews.getJSONObject(i);if(!row.optBoolean("included"))rejected.add(row.getString("file"));}
        part.put("overview",without(part.optString("overview"),rejected));
        for(String collection:new String[]{"notes","assignments"}){
            JSONArray rows=part.getJSONArray(collection);
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);for(String field:new String[]{"title","text"})if(row.has(field))row.put(field,without(row.getString(field),rejected));
                JSONArray images=row.optJSONArray("images");if(images==null)continue;JSONArray kept=new JSONArray();
                for(int n=0;n<images.length();n++)if(!rejected.contains(ClassroomDocument.imageName(images.getString(n))))kept.put(images.getString(n));row.put("images",kept);
            }
        }
    }
    private static String without(String text,Set<String> rejected)throws IOException{
        return ClassroomMarkdownImages.rewrite(text,rejected,false);
    }
}
