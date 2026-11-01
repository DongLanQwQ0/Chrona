package com.donglan.chrona.classroom;

import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reuse committed correction work, never a previous summary or an unfinished model reply. */
public final class ClassroomCorrectionCache {
    private ClassroomCorrectionCache() { }

    /** Capture before explicitly starting another summary run, after material preparation. */
    public static void capture(File dir)throws Exception {
        JSONObject materials=ClassroomStore.read(new File(dir,"materials.json"));
        if(materials==null)return;
        JSONArray blocks=ClassroomEnhancedSummary.blocks(ClassroomSummary.lessonInput(materials.getJSONArray("segments"),new JSONArray()).getJSONArray("segments"));
        JSONObject prior=ClassroomStore.read(new File(dir,"run.json"));
        JSONArray parts=null;String origin="";
        if(prior!=null&&sameBlocks(prior.optJSONArray("blocks"),blocks)&&validParts(prior.optJSONArray("corrected"),blocks.length())){
            parts=prior.getJSONArray("corrected");origin=prior.optString("run");
        }else if(prior==null||prior.optJSONArray("blocks")==null){
            JSONObject saved=ClassroomStore.read(new File(dir,"corrected.json")),summary=ClassroomStore.read(new File(dir,"summary.json"));
            if(saved!=null&&summary!=null&&saved.optBoolean("complete")&&!saved.optString("run").isBlank()
                    &&saved.optString("run").equals(summary.optString("run"))&&summary.optInt("source_blocks")==blocks.length()
                    &&sameBlocks(saved.optJSONArray("blocks"),blocks)
                    &&validParts(saved.optJSONArray("parts"),blocks.length())&&saved.getJSONArray("parts").length()==blocks.length()){
                parts=saved.getJSONArray("parts");origin=saved.getString("run");
            }else{
                JSONObject cache=ClassroomStore.read(new File(dir,"correction-cache.json"));
                if(cache!=null&&sameBlocks(cache.optJSONArray("blocks"),blocks)&&validParts(cache.optJSONArray("parts"),blocks.length())){
                    parts=cache.getJSONArray("parts");origin=cache.optString("source_run");
                }
            }
        }
        // Empty explicitly invalidates an older cache; it is never permission to reuse stale text.
        ClassroomStore.write(new File(dir,"correction-cache.json"),new JSONObject().put("blocks",blocks)
                .put("parts",parts==null?new JSONArray():new JSONArray(parts.toString())).put("source_run",origin));
    }

    static void restore(File dir,JSONObject state,JSONArray blocks)throws Exception {
        if(state.has("corrected"))return;
        JSONObject cached=ClassroomStore.read(new File(dir,"correction-cache.json"));
        if(cached==null||!sameBlocks(cached.optJSONArray("blocks"),blocks)||!validParts(cached.optJSONArray("parts"),blocks.length()))return;
        JSONArray parts=cached.getJSONArray("parts");if(parts.length()==0)return;
        state.put("corrected",new JSONArray(parts.toString())).put("correction_reused_from",cached.optString("source_run"));
        ClassroomStore.write(new File(dir,"run.json"),state);
        ClassroomStore.write(new File(dir,"corrected.json"),new JSONObject().put("run",state.getString("run"))
                .put("parts",new JSONArray(parts.toString())).put("blocks",new JSONArray(blocks.toString()))
                .put("complete",parts.length()==blocks.length()).put("reused_from",cached.optString("source_run")));
    }

    private static boolean validParts(JSONArray parts,int count){
        if(parts==null||parts.length()>count)return false;
        for(int i=0;i<parts.length();i++)if(!(parts.opt(i) instanceof String text)||text.isBlank())return false;
        return true;
    }
    private static boolean sameBlocks(JSONArray left,JSONArray right){
        if(left==null||left.length()!=right.length())return false;
        for(int i=0;i<right.length();i++){
            JSONArray a=left.optJSONArray(i),b=right.optJSONArray(i);if(a==null||b==null||a.length()!=b.length())return false;
            for(int n=0;n<b.length();n++){
                JSONObject x=a.optJSONObject(n),y=b.optJSONObject(n);
                if(x==null||y==null||x.optInt("id",-1)!=y.optInt("id",-1)||!x.optString("text").equals(y.optString("text")))return false;
            }
        }
        return true;
    }
}
