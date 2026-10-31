package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.*;
import com.donglan.chrona.net.RequestControl;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bounded outputs over one growing, immutable-prefix conversation. Never compacts earlier inputs. */
final class ClassroomEnhancedSummary {
    private static final int BLOCK_CHARS=6000;
    private final File dir;private final JSONObject state,materials,candidates,full;
    private final ClassroomLesson lesson;private final ClassroomConversation conversation;
    private final RequestControl control;private final ClassroomImages.Progress progress;
    ClassroomEnhancedSummary(File dir,ClassroomLesson lesson,JSONObject state,JSONObject materials,JSONObject candidates,
            JSONObject full,ClassroomConversation conversation,RequestControl control,ClassroomImages.Progress progress){
        this.dir=dir;this.lesson=lesson;this.state=state;this.materials=materials;this.candidates=candidates;
        this.full=full;this.conversation=conversation;this.control=control;this.progress=progress;
    }
    JSONObject generate(int imageLimit)throws Exception{
        if(!state.has("blocks"))state.put("blocks",blocks(full.getJSONArray("segments")));
        JSONArray blocks=state.getJSONArray("blocks"),corrected=state.optJSONArray("corrected");
        if(corrected==null){corrected=new JSONArray();state.put("corrected",corrected);}
        for(int i=corrected.length();i<blocks.length();i++){
            status("AI 更正文稿",i,blocks.length());
            String prompt=(i==0?"课程："+lesson.course+"；课堂："+lesson.title+"。全部任务沿用同一消息历史，只追加，不修改此前资料。":"")
                    +"当前为更正文稿第 "+(i+1)+"/"+blocks.length()+" 段。输出本段完整更正文稿Markdown，不输出JSON、不总结或删减内容。"
                    +"结合前文纠正识别、断句、术语及公式，不确定处保留原文并标[待核对]。保留每条原始[id]、所有题目条件、改卷得失分细节及细微暗示；不要编造来源。"
                    +"只更正以下原始来源，不重写前面段落：\n"+ClassroomSummary.uploadChunk(blockInput(blocks.getJSONArray(i),new JSONArray()));
            String text=text("correct-"+i,prompt,Collections.emptyList());
            corrected.put(text);save();
            ClassroomStore.write(new File(dir,"corrected.json"),new JSONObject().put("run",state.getString("run")).put("parts",new JSONArray(corrected.toString())).put("complete",corrected.length()==blocks.length()));
        }
        if(!state.has("parts")){ClassroomStore.write(new File(dir,"summary.json"),assemble(new JSONArray(),new JSONArray(),false));status("更正文稿已保存",corrected.length(),blocks.length());}
        JSONArray drafts=state.optJSONArray("drafts");if(drafts==null){drafts=new JSONArray();state.put("drafts",drafts);}
        JSONObject catalogue=full.getJSONObject("image_catalogue");
        for(int i=drafts.length();i<blocks.length();i++){
            status("AI 分段整理与核图计划",i,blocks.length());
            JSONArray block=blocks.getJSONArray(i);
            String prompt="更正文稿已全部完成。现在只整理第 "+(i+1)+" 段。"+scope(block)
                    +"历史仅用于理解语境，不得把最后一段或其它段当作本段。只提取本段事实，不写全课总结；同一内容不跨段重述。"
                    +"本段没有教学内容或明确安排时，overview为空字符串，notes和assignments为空数组；不能用闲聊或‘未提及评分规则’填充。"
                    +assignmentRules()+ClassroomSummary.draftSchema(catalogue,imageLimit,true)
                    +"\n本次要提取的更正文稿（历史原消息保持不变，此处仅追加定位）：\n"+corrected.getString(i)
                    +"\n安排原话核验用原始字幕（只作evidence逐字定位，列全引文覆盖的sources）：\n"+ClassroomSummary.uploadChunk(blockInput(block,new JSONArray()))
                    +"\n全课PPT可用目录（只表示时间分布，不是图片内容）："+catalogue;
            drafts.put(json("draft-"+i,prompt,Collections.emptyList(),value->{ClassroomSummary.validateBlock(value,blockInput(block,new JSONArray()));ClassroomSummary.validateAssignmentEvidence(value,block);ClassroomSummary.validateImageAssessment(value,catalogue);}));save();
        }
        if(!state.has("selected")){
            JSONArray requests=new JSONArray();for(int i=0;i<drafts.length();i++){JSONArray rows=drafts.getJSONObject(i).getJSONArray("image_requests");for(int n=0;n<rows.length();n++)requests.put(rows.getJSONObject(n));}
            JSONArray picked=ClassroomSummary.selectRequested(candidates.getJSONArray("images"),requests,imageLimit),selected=new JSONArray();
            for(int i=0;i<picked.length();i++){JSONObject p=picked.getJSONObject(i);selected.put(new JSONObject().put("file","image-"+p.getInt("index")+".jpg").put("second",p.getDouble("second")).put("raw",p.getString("raw")));}
            state.put("selected",selected).put("image_audit",new JSONObject().put("catalogue",catalogue).put("blocks",drafts).put("requests",requests).put("selected_count",selected.length()));save();
        }
        JSONArray selected=state.getJSONArray("selected"),parts=state.optJSONArray("parts"),analysed=state.optJSONArray("analysed");
        if(parts==null){parts=new JSONArray();state.put("parts",parts);}if(analysed==null){analysed=new JSONArray();state.put("analysed",analysed);}
        for(int i=parts.length();i<blocks.length();i++){
            status("AI 分段完成笔记",i,blocks.length());
            JSONArray inventory=new JSONArray();List<byte[]> bytes=new ArrayList<>();
            for(int n=0;n<selected.length();n++){
                JSONObject p=selected.getJSONObject(n);if(!requested(p,drafts.getJSONObject(i).getJSONArray("image_requests"))||contains(analysed,p.getString("file")))continue;
                File file=new File(dir,p.getString("file"));if(!file.exists())ClassroomStore.writeBytes(file,ClassroomImages.jpeg(new File(dir,p.getString("raw"))));
                inventory.put(new JSONObject().put("file",p.getString("file")).put("second",p.getDouble("second")));bytes.add(ClassroomStore.bytes(file));
            }
            JSONObject value;
            boolean reuse=false;for(int n=0;n<analysed.length();n++)reuse|=requested(analysed.getJSONObject(n),drafts.getJSONObject(i).getJSONArray("image_requests"));
            if(inventory.length()==0&&!reuse)value=drafts.getJSONObject(i);
            else{
                JSONArray allowed=new JSONArray(analysed.toString());for(int n=0;n<inventory.length();n++)allowed.put(inventory.getJSONObject(n));
                JSONArray block=blocks.getJSONArray(i);
                String prompt="只核实并完善第 "+(i+1)+" 段笔记。当前主体来源id："+ids(block)+"。每条笔记至少引用一条本块来源；可补引同课已提供的真实前文作对照，不引用后续来源、不只重写其它块。课堂安排仅引用本块来源。"
                        +"保留题目和改卷线索、原话条件及不确定性。只修订下面指定初稿，不要依据最后一条历史回复另写一段，不重述其它段。"+assignmentRules()
                        +"本次图片依次对应以下file原值，编号可能不连续，禁止重新编号："+inventory
                        +"。此前已提供的图片保持在原消息位置。仅可引用本次或此前实际提供的file，不得把请求过但未附的图片当作已看到。"+ClassroomSummary.BLOCK_SCHEMA
                        +"\n安排原话核验用原始字幕：\n"+ClassroomSummary.uploadChunk(blockInput(block,new JSONArray()))
                        +"\n本次唯一要修订的初稿：\n"+drafts.getJSONObject(i);
                value=json("final-"+i,prompt,bytes,result->validateRefinement(result,blockInput(full.getJSONArray("segments"),allowed),block));
                for(int n=0;n<inventory.length();n++)analysed.put(inventory.getJSONObject(n));
            }
            parts.put(value);save();ClassroomStore.write(new File(dir,"summary.json"),assemble(parts,analysed,false));
        }
        edit(parts,analysed);
        editAssignments(parts);
        JSONObject result=assemble(parts,analysed,true);state.put("final",result);save();ClassroomStore.write(new File(dir,"summary.json"),result);return result;
    }
    private static String scope(JSONArray block)throws Exception{
        return "本次允许的字幕来源id仅为："+ids(block)+"；sources不得引用这个集合之外的编号。";
    }
    private static String assignmentRules(){
        return "课堂安排必须是老师明确布置的具体任务，不能把知识点、一般编程建议、自拟复习事项转成安排。"
                +"每项assignments除text、sources外还必须带evidence，逐字摘录原始字幕中表达该要求的最短连续原话，不用更正文稿的改写，不拼接非连续片段。"
                +"sources须列全引文覆盖的每条来源，不可跳过中间字幕；只摘具体行动要求，不带入无关分数或背景段落。没有真实要求就返回[]。";
    }
    static void validateRefinement(JSONObject part,JSONObject lecture,JSONArray block)throws Exception{
        ClassroomSummary.validateBlock(part,lecture);
        Set<Integer> current=new HashSet<>();int first=Integer.MAX_VALUE;
        for(int i=0;i<block.length();i++){int id=block.getJSONObject(i).getInt("id");current.add(id);first=Math.min(first,id);}
        JSONArray notes=part.getJSONArray("notes");
        for(int n=0;n<notes.length();n++){
            JSONArray refs=notes.getJSONObject(n).getJSONArray("sources");boolean anchored=false;
            for(int k=0;k<refs.length();k++){int id=refs.getInt(k);if(current.contains(id))anchored=true;else if(id>=first)throw new IOException("核图笔记引用了本块之后的字幕："+id);}
            if(!anchored)throw new IOException("核图笔记缺少本块来源，不可只重写前一段");
        }
        JSONArray tasks=part.getJSONArray("assignments");for(int i=0;i<tasks.length();i++){
            JSONArray refs=tasks.getJSONObject(i).getJSONArray("sources");for(int n=0;n<refs.length();n++)if(!current.contains(refs.getInt(n)))throw new IOException("课堂安排引用了本块之外的字幕："+refs.getInt(n));
        }
        ClassroomSummary.validateAssignmentEvidence(part,block);
    }
    /** Only references are planned globally; prose is edited one topic at a time. */
    private void edit(JSONArray parts,JSONArray analysed)throws Exception{
        if(!state.has("editor_plan")){
            status("AI 全课主题编排",0,1);
            JSONArray index=new JSONArray();
            for(int p=0;p<parts.length();p++){JSONArray notes=parts.getJSONObject(p).getJSONArray("notes");for(int n=0;n<notes.length();n++){
                JSONObject note=notes.getJSONObject(n);index.put(new JSONObject().put("ref",p+":"+n).put("title",note.getString("title")).put("sources",note.getJSONArray("sources")));
            }}
            String prompt="全部分段提取与核图已完成，现在编排一篇与普通模式相同的全课笔记，不向用户展示分段流水账。"
                    +"同一知识点的重复提取必须放入同一主题，合并跨段续讲，按课程讲授顺序组织；保留不同题目条件、代码、纠错和评分细节，不能按输入段号设章节。"
                    +"只输出紧凑JSON {\"overview\":\"全课统一重点概览\",\"sections\":[{\"title\":\"主题\",\"refs\":[\"0:0\"]}]}。"
                    +"overview用少量完整句说明全课核心，不逐段复述、不写第几段或未提及的类别。refs是下面真实索引，每条必须恰好使用一次，不得遗漏、重复或编造；本轮不生成笔记正文。没有教学笔记则sections=[]，overview为空。"
                    +"\n已完成笔记索引：\n"+index;
            state.put("editor_plan",json("edit-plan",prompt,Collections.emptyList(),value->validatePlan(value,parts)));save();
        }
        JSONObject plan=state.getJSONObject("editor_plan");JSONArray sections=plan.getJSONArray("sections"),edited=state.optJSONArray("editor_notes");
        if(edited==null){edited=new JSONArray();state.put("editor_notes",edited);}
        for(int i=edited.length();i<sections.length();i++){
            status("AI 全课主题整稿",i,sections.length());
            JSONObject section=sections.getJSONObject(i);JSONArray inputs=new JSONArray();Set<Integer> sources=new HashSet<>();
            JSONArray refs=section.getJSONArray("refs");
            for(int n=0;n<refs.length();n++){JSONObject note=noteAt(parts,refs.getString(n));inputs.put(note);JSONArray ids=note.getJSONArray("sources");for(int k=0;k<ids.length();k++)sources.add(ids.getInt(k));}
            JSONArray segments=new JSONArray(),all=full.getJSONArray("segments");for(int n=0;n<all.length();n++)if(sources.contains(all.getJSONObject(n).getInt("id")))segments.put(all.getJSONObject(n));
            String prompt="全课整稿主题 "+(i+1)+"/"+sections.length()+"："+section.getString("title")+"。"
                    +"把下面同主题提取稿合为一节完整笔记，消除重复叙述、重复截图和‘图片核对’流水账。保持普通模式的精炼组织，不因内部多轮而增加重复概览、分段编号、无关闲聊或未提及事项的说明。"
                    +"保留每个有来源的独特事实、题目数字与条件、代码、纠错、明确不考/会考以及改卷给分细节；相同事实只写一次，不可为缩短而删除这些细节。不改变原话确定性。"
                    +"沿用普通单篇总结的详略，正文不扩写成教程，不超过本主题提取稿正文的合计长度；重复较多时应明显缩短。"
                    +scope(segments)+"sources汇集实际使用的来源；图片只能使用已实际分析且支持本主题的原file，历史图片不重新上传。"
                    +"只返回一个JSON，overview为空，notes恰好一项，assignments=[]。"+ClassroomSummary.TOPIC_SCHEMA
                    +"\n本主题提取稿：\n"+inputs;
            JSONObject value=json("edit-note-"+i,prompt,Collections.emptyList(),result->{
                ClassroomSummary.validateBlock(result,blockInput(segments,analysed));
                if(result.getJSONArray("notes").length()!=1||result.getJSONArray("assignments").length()!=0||!result.getString("overview").isBlank())throw new IOException("整稿主题必须仅返回一节笔记，不重复概览或安排");
            });
            edited.put(value.getJSONArray("notes").getJSONObject(0));save();
            ClassroomStore.write(new File(dir,"summary.json"),assemble(parts,analysed,false));
        }
    }
    static void validatePlan(JSONObject plan,JSONArray parts)throws Exception{
        String overview=plan.getString("overview");Set<String> remaining=new HashSet<>();
        for(int p=0;p<parts.length();p++)for(int n=0;n<parts.getJSONObject(p).getJSONArray("notes").length();n++)remaining.add(p+":"+n);
        if(!remaining.isEmpty()&&overview.isBlank())throw new IOException("整稿缺少全课重点概览");
        JSONArray sections=plan.getJSONArray("sections");Map<String,Integer> topicOwners=new HashMap<>();
        for(int i=0;i<sections.length();i++){
            JSONObject section=sections.getJSONObject(i);if(section.getString("title").isBlank())throw new IOException("整稿主题标题为空");
            JSONArray refs=section.getJSONArray("refs");if(refs.length()==0)throw new IOException("整稿主题缺少原笔记");
            for(int n=0;n<refs.length();n++){
                String ref=refs.getString(n);if(!remaining.remove(ref))throw new IOException("整稿引用了重复或不存在的笔记："+ref);
                String topic=noteAt(parts,ref).getString("title").replaceAll("\\s+","");Integer owner=topicOwners.putIfAbsent(topic,i);
                if(owner!=null&&owner!=i)throw new IOException("同名主题被拆成重复章节："+topic);
            }
        }
        if(!remaining.isEmpty())throw new IOException("整稿遗漏了已提取笔记："+remaining);
    }
    private static JSONObject noteAt(JSONArray parts,String ref)throws Exception{String[] pair=ref.split(":");return parts.getJSONObject(Integer.parseInt(pair[0])).getJSONArray("notes").getJSONObject(Integer.parseInt(pair[1]));}
    private void editAssignments(JSONArray parts)throws Exception{
        if(state.has("editor_assignments"))return;
        JSONObject original=new JSONObject();JSONArray index=new JSONArray();
        for(int p=0;p<parts.length();p++){JSONArray rows=parts.getJSONObject(p).getJSONArray("assignments");for(int n=0;n<rows.length();n++){
            String ref=p+":"+n;JSONObject row=rows.getJSONObject(n);original.put(ref,row);index.put(new JSONObject().put("ref",ref).put("assignment",row));
        }}
        JSONArray result;
        if(index.length()<2){result=new JSONArray();if(index.length()==1)result.put(new JSONObject(index.getJSONObject(0).getJSONObject("assignment").toString()));}
        else{
            status("AI 合并课堂安排",0,1);
            String prompt="合并全课课堂安排中的同义重复，只返回索引分组，不重写原文。返回JSON {\"groups\":[[\"0:0\",\"0:1\"],[\"0:2\"]]}。"
                    +"每个真实ref恰好出现一次，不得遗漏、重复、编造或返回空组。仅在首项原文已完整涵盖组内其它项的同一行动、日期、条件及限制时合并，首项选最完整的原文；简短重述并入完整要求。"
                    +"不同任务、有互补细节或相互矛盾的要求各自成组，保留讲授顺序。不新增行动或解释，不改写任何已发送历史。\n已核验的安排索引：\n"+index;
            JSONObject grouping=json("edit-assignments",prompt,Collections.emptyList(),value->groupAssignments(value,original));
            result=groupAssignments(grouping,original);
        }
        state.put("editor_assignments",result);save();
    }
    static JSONArray groupAssignments(JSONObject grouping,JSONObject original)throws Exception{
        Set<String> remaining=new HashSet<>();Iterator<String> keys=original.keys();while(keys.hasNext())remaining.add(keys.next());
        JSONArray result=new JSONArray(),groups=grouping.getJSONArray("groups");
        for(int i=0;i<groups.length();i++){
            JSONArray refs=groups.getJSONArray(i);if(refs.length()==0)throw new IOException("课堂安排分组为空");
            JSONObject kept=null;Set<Integer> sources=new LinkedHashSet<>();
            for(int n=0;n<refs.length();n++){
                String ref=refs.getString(n);if(!remaining.remove(ref))throw new IOException("课堂安排引用了重复或不存在的索引："+ref);
                JSONObject row=original.getJSONObject(ref);if(kept==null)kept=new JSONObject(row.toString());
                JSONArray ids=row.getJSONArray("sources");for(int k=0;k<ids.length();k++)sources.add(ids.getInt(k));
            }
            JSONArray ids=new JSONArray();for(int id:sources)ids.put(id);kept.put("sources",ids);result.put(kept);
        }
        if(!remaining.isEmpty())throw new IOException("课堂安排分组遗漏了索引："+remaining);
        return result;
    }
    private JSONObject assemble(JSONArray parts,JSONArray analysed,boolean complete)throws Exception{
        JSONObject plan=state.optJSONObject("editor_plan");String overview=plan==null?"":plan.getString("overview");
        JSONArray notes=state.optJSONArray("editor_notes"),tasks=state.optJSONArray("editor_assignments");
        JSONObject document=new JSONObject().put("overview",overview).put("notes",notes==null?new JSONArray():new JSONArray(notes.toString())).put("assignments",tasks==null?new JSONArray():new JSONArray(tasks.toString()));
        JSONArray corrected=state.getJSONArray("corrected");StringBuilder transcript=new StringBuilder();
        for(int i=0;i<corrected.length();i++){if(i>0)transcript.append("\n\n");transcript.append(corrected.getString(i));}
        return new JSONObject().put("lesson",lesson.json()).put("run",state.getString("run")).put("updated",System.currentTimeMillis()).put("complete",complete)
                .put("overview",overview).put("parts",new JSONArray().put(document)).put("total_parts",1).put("source_blocks",state.getJSONArray("blocks").length())
                .put("corrected_transcript",ClassroomDocument.normalizeMarkdown(transcript.toString())).put("images",new JSONArray(analysed.toString()))
                .put("planned_images",state.has("selected")?state.getJSONArray("selected").length():0).put("raw_images",materials.getInt("raw_images"))
                .put("candidate_images",candidates.getJSONArray("images").length()).put("image_audit",state.optJSONObject("image_audit"));
    }
    interface Validator{void check(JSONObject result)throws Exception;}
    private JSONObject json(String stage,String prompt,List<byte[]> images,Validator validator)throws Exception{
        int attempt=state.optInt(stage+"_attempt");
        String raw=text(attempt==0?stage:stage+"-user-retry-"+attempt,attempt==0?prompt:
                "用户授权继续当前板块。上一条回复未通过校验，当前板块未提交结果已回滚："+state.optString(stage+"_error")+"。只依据已有原始资料和实际已附图片，从头完整生成此块JSON，不续写、不输出补丁或拼接上次片段，来源和图片编号必须真实。完整保留本块所需字段：\n"+prompt,attempt==0?images:Collections.emptyList());
        try{JSONObject result=ClassroomSummary.parseObject(raw);validator.check(result);return result;}
        catch(JSONException malformed){
            if(attempt>0&&conversation.attempted(stage+"-repair")){invalid(stage,attempt,malformed);throw malformed;}
            String repaired=text(stage+"-repair","上一条回复的JSON结构或字段不完整："+malformed.getMessage()+"。本块结果尚未提交。保留可核实事实、来源和图片编号，重新返回完整JSON对象，不输出补丁或剩余片段，不编造未见图片或新事实。这是该块唯一一次格式修复。\n本块要求："+prompt,Collections.emptyList());
            try{JSONObject result=ClassroomSummary.parseObject(repaired);validator.check(result);return result;}
            catch(Exception invalid){invalid(stage,attempt,invalid);throw invalid;}
        }
        catch(IOException invalid){invalid(stage,attempt,invalid);throw invalid;}
    }
    private void invalid(String stage,int attempt,Exception error)throws Exception{state.put(stage+"_attempt",attempt+1).put(stage+"_error",error.getMessage());save();}
    private String text(String stage,String prompt,List<byte[]> images)throws Exception{control.check();return conversation.text(stage,prompt,images);}
    private void status(String stage,int done,int total)throws Exception{control.check();progress.update(new ClassroomProgress(stage,done,total,"已完成 "+done+" / "+total));}
    private void save()throws IOException{ClassroomStore.write(new File(dir,"run.json"),state);}
    private static JSONObject blockInput(JSONArray segments,JSONArray images)throws Exception{return new JSONObject().put("segments",segments).put("images",images);}
    static JSONArray blocks(JSONArray segments)throws Exception{
        JSONArray result=new JSONArray(),block=new JSONArray();int chars=0;
        for(int i=0;i<segments.length();i++){
            JSONObject row=segments.getJSONObject(i);String text=row.getString("text");
            int offset=0;
            do{
                if(offset==0&&chars>0&&text.length()<=BLOCK_CHARS&&text.length()>BLOCK_CHARS-chars){result.put(block);block=new JSONArray();chars=0;}
                if(chars==BLOCK_CHARS){result.put(block);block=new JSONArray();chars=0;}
                int end=Math.min(text.length(),offset+BLOCK_CHARS-chars);
                if(end<text.length()&&end>offset&&Character.isHighSurrogate(text.charAt(end-1)))end--;
                if(end==offset&&offset<text.length()){result.put(block);block=new JSONArray();chars=0;continue;}
                JSONObject slice=new JSONObject(row.toString()).put("text",text.substring(offset,end));
                block.put(slice);chars+=end-offset;offset=end;
            }while(offset<text.length());
        }
        if(block.length()>0)result.put(block);return result;
    }
    private static JSONArray ids(JSONArray rows)throws Exception{JSONArray ids=new JSONArray();for(int i=0;i<rows.length();i++)ids.put(rows.getJSONObject(i).getInt("id"));return ids;}
    private static boolean contains(JSONArray rows,String file)throws Exception{for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("file").equals(file))return true;return false;}
    private static boolean requested(JSONObject image,JSONArray ranges)throws Exception{for(int i=0;i<ranges.length();i++){JSONObject r=ranges.getJSONObject(i);if(image.getDouble("second")>=r.getDouble("start")&&image.getDouble("second")<=r.getDouble("end"))return true;}return false;}
}
