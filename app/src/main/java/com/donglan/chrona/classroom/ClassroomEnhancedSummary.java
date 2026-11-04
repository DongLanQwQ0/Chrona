package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.*;
import com.donglan.chrona.net.RequestControl;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bounded outputs over one growing, immutable-prefix conversation. Never compacts earlier inputs. */
final class ClassroomEnhancedSummary {
    private static final int BLOCK_CHARS=6000;
    static final int WHOLE_LESSON_CHARS=60000;
    private static final int LEGACY_EDITOR_BATCH_CHARS=12000;
    private final File dir;private final JSONObject state,materials,candidates,full;
    private final ClassroomLesson lesson;private final ClassroomConversation conversation;
    private final RequestControl control;private final ClassroomImages.Progress progress;
    private final int affairsSchema;
    ClassroomEnhancedSummary(File dir,ClassroomLesson lesson,JSONObject state,JSONObject materials,JSONObject candidates,
            JSONObject full,ClassroomConversation conversation,RequestControl control,ClassroomImages.Progress progress){
        this.dir=dir;this.lesson=lesson;this.state=state;this.materials=materials;this.candidates=candidates;
        this.full=full;this.conversation=conversation;this.control=control;this.progress=progress;
        this.affairsSchema=state.optInt("affairs_schema",0);
    }
    JSONObject generate(int imageLimit)throws Exception{
        if(!state.has("blocks"))state.put("blocks",state.optBoolean("compact_pipeline")
                ?ClassroomCorrectionCache.compactBlocks(dir,full.getJSONArray("segments")):blocks(full.getJSONArray("segments")));
        JSONArray blocks=state.getJSONArray("blocks");
        ClassroomCorrectionCache.restore(dir,state,blocks);
        JSONArray corrected=state.optJSONArray("corrected");
        if(corrected==null){corrected=new JSONArray();state.put("corrected",corrected);}
        for(int i=corrected.length();i<blocks.length();i++){
            status("AI 更正文稿",i,blocks.length());
            String prompt=(i==0||state.has("correction_reused_from")?"课程："+lesson.course+"；课堂："+lesson.title+"。全部任务沿用同一消息历史，只追加，不修改此前资料。":"")
                    +"当前为更正文稿第 "+(i+1)+"/"+blocks.length()+" 段。输出本段完整更正文稿Markdown，不输出JSON、不总结或删减内容。"
                    +"结合前文纠正识别、断句、术语及公式，不确定处保留原文并标[待核对]。保留每条原始[id]、所有题目条件、改卷得失分细节及细微暗示；不要编造来源。"
                    +"只更正以下原始来源，不重写前面段落：\n"+ClassroomSummary.uploadChunk(blockInput(blocks.getJSONArray(i),new JSONArray()));
            String text=text("correct-"+i,prompt,Collections.emptyList());
            corrected.put(text);save();
            ClassroomStore.write(new File(dir,"corrected.json"),new JSONObject().put("run",state.getString("run")).put("parts",new JSONArray(corrected.toString()))
                    .put("blocks",new JSONArray(blocks.toString())).put("complete",corrected.length()==blocks.length()));
        }
        if(!state.has("parts")&&!state.has("compact_note")){ClassroomStore.write(new File(dir,"summary.json"),assemble(new JSONArray(),new JSONArray(),false));status("更正文稿已保存",corrected.length(),blocks.length());}
        if(state.optBoolean("compact_pipeline"))return compact(imageLimit,corrected);
        int contentBatchSize=Math.max(1,state.optInt("content_batch_size",1));
        JSONArray groups=contentGroups(blocks,contentBatchSize);
        JSONArray drafts=state.optJSONArray("drafts");if(drafts==null){drafts=new JSONArray();state.put("drafts",drafts);}
        JSONObject catalogue=full.getJSONObject("image_catalogue");
        for(int i=drafts.length();i<groups.length();i++){
            status("AI 分段整理与核图计划",i,groups.length());
            JSONArray block=groups.getJSONArray(i);StringJoiner transcript=new StringJoiner("\n\n");
            int first=i*contentBatchSize,last=Math.min(corrected.length(),(i+1)*contentBatchSize);
            for(int n=first;n<last;n++)transcript.add("【原始段 "+(n+1)+" 开始】\n"+corrected.getString(n)+"\n【原始段 "+(n+1)+" 结束】");
            String prompt=(i==0&&state.has("correction_reused_from")?"课程："+lesson.course+"；课堂："+lesson.title+"。以下复用已完成的修正文稿，按本轮要求重新整理笔记。":"")
                    +"更正文稿已全部完成。现在只整理第 "+(i+1)+(contentBatchSize==1?" 段。":" 内容组（原文第 "+(i*contentBatchSize+1)+" 至 "+Math.min(blocks.length(),(i+1)*contentBatchSize)+" 段）。")
                    +"本请求须完整整理下列整组的全部原始段，从头至尾扫描，不仅摘要第一段或开头。逐项保留独特概念、定理及证明、例题及必要计算、考核评分线索和不同对象的明确行动；重复内容合并，不能以精简为由省去整项教学内容。"
                    +"不写全课总结；同题跨组可以引用已提供的同课真实来源，保留跨组续讲，后续统一整稿。"
                    +(affairsSchema==0?"本段没有教学内容或明确安排时，overview为空字符串，notes和assignments为空数组；不能用闲聊或‘未提及评分规则’填充。":"本段无教学内容时overview为空且notes=[]；非行动事务进入affairs，具体行动只进入assignments，三者均无内容时返回空数组，不用闲聊填充。")
                    +assignmentRules()+ClassroomSummary.draftSchema(catalogue,imageLimit,true,affairsSchema)
                    +"\n本次要提取的更正文稿（历史原消息保持不变，此处仅追加定位）：\n"+transcript
                    +"\n事实核对及安排原话定位用原始字幕（更正文稿与其冲突时核对原话；evidence逐字摘录并列全覆盖的sources）：\n"+ClassroomSummary.uploadChunk(blockInput(block,new JSONArray()))
                    +"\n全课PPT可用目录（只表示时间分布，不是图片内容）："+catalogue+"\n"+scope(block)
                    +"\n本组更正文稿已有标题，仅作覆盖提醒，不是成品目录："+coverageOutline(corrected,first,last)
                    +"。输出前在本次处理内核对整组首尾及全部教学单元，不因某标题缺失而忽略无标题内容；标题可合并重排，但实质内容不能漏掉。不额外输出检查报告。";
            JSONObject draft=json("draft-"+i,prompt,Collections.emptyList(),value->{ClassroomSummary.validateBlock(value,blockInput(full.getJSONArray("segments"),new JSONArray()),affairsSchema);ClassroomSummary.validateAssignmentEvidence(value,full.getJSONArray("segments"));ClassroomSummary.validateImageAssessment(value,catalogue);});
            recordScopeWarnings("draft-"+i,draft,block);drafts.put(draft);save();
        }
        if(!state.has("selected")){
            JSONArray requests=new JSONArray();for(int i=0;i<drafts.length();i++){JSONArray rows=drafts.getJSONObject(i).getJSONArray("image_requests");for(int n=0;n<rows.length();n++)requests.put(rows.getJSONObject(n));}
            JSONArray picked=ClassroomSummary.selectRequested(candidates.getJSONArray("images"),requests,imageLimit),selected=new JSONArray();
            for(int i=0;i<picked.length();i++){JSONObject p=picked.getJSONObject(i);JSONObject row=new JSONObject().put("file","image-"+p.getInt("index")+".jpg").put("second",p.getDouble("second")).put("raw",p.getString("raw"));if(p.optJSONObject("quality")!=null)row.put("quality",p.getJSONObject("quality"));selected.put(row);}
            state.put("selected",selected).put("image_audit",new JSONObject().put("catalogue",catalogue).put("blocks",drafts).put("requests",requests).put("selected_count",selected.length()));save();
        }
        JSONArray selected=state.getJSONArray("selected"),parts=state.optJSONArray("parts"),analysed=state.optJSONArray("analysed");
        if(parts==null){parts=new JSONArray();state.put("parts",parts);}if(analysed==null){analysed=new JSONArray();state.put("analysed",analysed);}
        for(int i=parts.length();i<groups.length();i++){
            status("AI 分段完成笔记",i,groups.length());
            JSONArray inventory=new JSONArray();List<byte[]> bytes=new ArrayList<>();List<String> imageNames=new ArrayList<>();
            for(int n=0;n<selected.length();n++){
                JSONObject p=selected.getJSONObject(n);if(!requested(p,drafts.getJSONObject(i).getJSONArray("image_requests"))||contains(analysed,p.getString("file")))continue;
                File file=new File(dir,p.getString("file"));if(!file.exists())ClassroomStore.writeBytes(file,ClassroomImages.jpeg(new File(dir,p.getString("raw"))));
                inventory.put(ClassroomFigureReview.inventory(p));bytes.add(ClassroomStore.bytes(file));
                imageNames.add(p.getString("file"));
            }
            JSONObject value;
            boolean reuse=false;for(int n=0;n<analysed.length();n++)reuse|=requested(analysed.getJSONObject(n),drafts.getJSONObject(i).getJSONArray("image_requests"));
            if(inventory.length()==0&&!reuse)value=drafts.getJSONObject(i);
            else{
                JSONArray allowed=new JSONArray(analysed.toString());for(int n=0;n<inventory.length();n++)allowed.put(inventory.getJSONObject(n));
                JSONArray block=groups.getJSONArray(i);
                String prompt="只核实并完善第 "+(i+1)+(contentBatchSize==1?" 段笔记。":" 内容组笔记（原文第 "+(i*contentBatchSize+1)+" 至 "+Math.min(blocks.length(),(i+1)*contentBatchSize)+" 段）。")+"当前主体来源id："+ids(block)+"。允许引用已提供的同课真实来源，不因分块边界删掉有用内容；跨段重复在后续全课整稿中合并。"
                        +"保留题目和改卷线索、原话条件及不确定性。只修订下面指定初稿，不要依据最后一条历史回复另写一段，不重述其它段。"+assignmentRules()
                        +"核图只补充或纠正证据，不把文字中的明确行动当作图片无关内容删去。逐项对照初稿assignments及原始字幕，仍有原话依据的行动完整保留；补全推导、整理成文等明确交给学生执行的任务，即使没有提交要求也属于行动。"
                        +"本次图片依次对应以下file原值，编号可能不连续，禁止重新编号："+inventory
                        +"。此前已提供的图片保持在原消息位置。仅可引用本次或此前实际提供的file，不得把请求过但未附的图片当作已看到。"+ClassroomSummary.schema(true,affairsSchema)+ClassroomFigureReview.schema(state.optInt("figure_policy"))
                        +"\n安排原话核验用原始字幕：\n"+ClassroomSummary.uploadChunk(blockInput(block,new JSONArray()))
                        +"\n本次唯一要修订的初稿：\n"+drafts.getJSONObject(i)+"\n"+refinementScope(block,affairsSchema);
                value=json("final-"+i,prompt,bytes,imageNames,result->{
                    validateRefinement(result,blockInput(full.getJSONArray("segments"),allowed),block,affairsSchema);
                    if(state.optInt("figure_policy")>0)ClassroomFigureReview.apply(result,allowed,inventory,state.optJSONArray("figure_reviews"));
                });
                if(value.has("image_reviews")){ClassroomFigureReview.merge(state,value.getJSONArray("image_reviews"));ClassroomFigureReview.filter(value,state.getJSONArray("figure_reviews"));}
                for(int n=0;n<inventory.length();n++)analysed.put(inventory.getJSONObject(n));
            }
            recordScopeWarnings("final-"+i,value,groups.getJSONArray(i));parts.put(value);save();ClassroomStore.write(new File(dir,"summary.json"),assemble(parts,analysed,false));
        }
        edit(parts,analysed);
        editAssignments(parts);
        consolidateAffairs(parts,analysed);
        JSONObject result=assemble(parts,analysed,true);state.put("final",result);save();ClassroomStore.write(new File(dir,"summary.json"),result);return result;
    }
    /** One evidence request and one complete publication; no intermediate notebook to rewrite. */
    private JSONObject compact(int imageLimit,JSONArray corrected)throws Exception{
        JSONObject catalogue=full.getJSONObject("image_catalogue");
        String originals=ClassroomSummary.uploadChunk(blockInput(full.getJSONArray("segments"),new JSONArray())).toString();
        if(!state.has("image_plan")&&(imageLimit==0||catalogue.getInt("count")==0)){
            state.put("image_plan",new JSONObject().put("image_assessment",new JSONObject()
                    .put("decision",imageLimit==0?"disabled":"unavailable")
                    .put("reason",imageLimit==0?"用户未授权上传图片":"本课堂没有可用截图"))
                    .put("image_requests",new JSONArray())).put("image_plan_origin","local_availability");save();
        }
        if(!state.has("image_plan")){
            status("AI 判断全课图片需求",0,1);
            String decision=imageLimit==0?"disabled":catalogue.getInt("count")==0?"unavailable":"request";
            String prompt="课程："+lesson.course+"；课堂："+lesson.title+"。更正文稿已完成。现在只判断图片需求，不生成初稿、主题编排或任何知识／事务正文。"
                    +"已完成更正文稿在同一历史中；缓存复用文稿在下方按原顺序原字追加。结合全课原始字幕与PPT时间目录，指出公式、图形、题设或书面要求有哪些视觉信息缺口，再决定是否取图。"
                    +"返回JSON {\"image_assessment\":{\"decision\":\""+decision+"\",\"reason\":\"具体依据\"},\"image_requests\":[]}。"
                    +"需要图时decision=request，每项image_requests为{\"start\":0,\"end\":60,\"reason\":\"视觉信息缺口\"}；无需图时not_needed且[]。禁止在关闭上传或无可用图片时请求。"
                    +ClassroomSummary.imageInstructions(catalogue,imageLimit)
                    +"\n复用更正文稿：\n"+(state.has("correction_reused_from")?corrected.toString():"更正文稿已在本轮原始历史中，不重复附录。")
                    +"\n全课原始字幕：\n"+originals+"\nPPT时间目录："+catalogue;
            JSONObject plan=json("image-plan",prompt,Collections.emptyList(),value->ClassroomSummary.validateImageAssessment(value,catalogue));
            state.put("image_plan",plan).put("image_plan_origin","model");save();
        }
        if(!state.has("selected")){
            JSONObject plan=state.getJSONObject("image_plan");
            JSONArray picked=ClassroomSummary.selectRequested(candidates.getJSONArray("images"),plan.getJSONArray("image_requests"),imageLimit),selected=new JSONArray();
            for(int i=0;i<picked.length();i++){
                JSONObject p=picked.getJSONObject(i),row=new JSONObject().put("file","image-"+p.getInt("index")+".jpg").put("second",p.getDouble("second")).put("raw",p.getString("raw"));
                if(p.optJSONObject("quality")!=null)row.put("quality",p.getJSONObject("quality"));selected.put(row);
            }
            state.put("selected",selected).put("image_audit",new JSONObject().put("catalogue",catalogue).put("assessment",plan.getJSONObject("image_assessment"))
                    .put("assessment_source",state.getString("image_plan_origin"))
                    .put("requests",plan.getJSONArray("image_requests")).put("selected_count",selected.length()).put("analysed_count",0));save();
        }
        JSONArray inventory=new JSONArray(),selected=state.getJSONArray("selected");
        List<byte[]> bytes=new ArrayList<>();List<String> names=new ArrayList<>();
        for(int i=0;i<selected.length();i++){
            control.check();JSONObject row=selected.getJSONObject(i);File file=new File(dir,row.getString("file"));
            if(!file.exists())ClassroomStore.writeBytes(file,ClassroomImages.jpeg(new File(dir,row.getString("raw"))));
            inventory.put(ClassroomFigureReview.inventory(row));bytes.add(ClassroomStore.bytes(file));names.add(row.getString("file"));
        }
        if(!state.has("compact_note")){
            status("AI 生成全课笔记",0,1);
            String prompt="现在根据同一历史中的更正文稿、全课原始字幕及本次实际附图，一次生成最终完整笔记。没有独立提取稿、编排稿或后续整稿，不预留待后补的题干或计算。"
                    +(state.has("correction_reused_from")&&!"model".equals(state.optString("image_plan_origin"))?"\n复用更正文稿（按原顺序原字追加）：\n"+corrected:"历史中的更正文稿保持原位，不重复附录。")
                    +"知识只进入notes，课程事务只进入affairs，具体执行任务只进入assignments，各自独立、不混写、不跨区重复。概览只简述知识主线，不放图。"
                    +"按当前笔记整理强度组织；3档是精简完整讲义，按知识依赖积极重排，保留自学需要的条件、关键过程、解释和结论，不扩写教材或流水账。原题不明可用明确标注的同类型补充例题，同机制不并列重复原题和补题。"
                    +"数学先还原表达式与操作链，区分合法运算与真正失效的前提；不能为解释错误结论而否定合法约分、变形或求导。"
                    +"每次代入不等式或换元均对照定义逐项替换，保持分子、系数、幂次、符号与趋近方向一致，不能只因最后答案相同就省略或改变中间等式。"
                    +"教师事务以原话为依据，集中保留考试、评分、范围、出勤和资源等有效信息。允许／禁止、保留／划掉、给分条件须核对动作、对象、触发行为和变化方向；不以常识覆盖原话，也不把派生文稿中反复出现的解释当作证据。"
                    +"保留细微考核线索和不同适用对象，音近术语无法确认时保留可靠事实，不猜额外得分入口。删无关闲聊、生产说明、重复小结；一般答卷规范不额外变成待办。"
                    +assignmentRules()+ClassroomSummary.schema(true,affairsSchema)+ClassroomFigureReview.schema(state.optInt("figure_policy"))
                    +"\n本次图片按顺序绑定实际file（编号不连续，不重编号）："+inventory
                    +"。只可引用实际附图，图注描述实际可见内容；不把字幕里的证明当作图片已显示。空白、标题页、遮挡及低置信度图不放成品；不强行配图。"
                    +"\n全课原始字幕：\n"+originals;
            JSONObject value=json("whole-note",prompt,bytes,names,result->{
                ClassroomSummary.validateBlock(result,blockInput(full.getJSONArray("segments"),inventory),affairsSchema);
                ClassroomSummary.validateAssignmentEvidence(result,full.getJSONArray("segments"));
                if(state.optInt("figure_policy")>0)ClassroomFigureReview.apply(result,inventory,inventory,state.optJSONArray("figure_reviews"));
                ClassroomSummary.validateBlock(result,blockInput(full.getJSONArray("segments"),inventory),affairsSchema);
            });
            if(value.has("image_reviews")){ClassroomFigureReview.merge(state,value.getJSONArray("image_reviews"));ClassroomFigureReview.filter(value,state.getJSONArray("figure_reviews"));}
            state.put("compact_note",value).put("analysed",inventory);
            state.getJSONObject("image_audit").put("analysed_count",inventory.length());save();
        }
        JSONObject result=assemble(new JSONArray(),inventory,true);state.put("final",result);save();ClassroomStore.write(new File(dir,"summary.json"),result);return result;
    }
    private static String scope(JSONArray block)throws Exception{
        return "本段主体字幕来源id为："+ids(block)+"；可引用已提供的同课真实来源，编号不得凭空编造。";
    }
    static String refinementScope(JSONArray block,int affairsSchema)throws Exception{
        return "当前核图块的主体来源id："+ids(block)+"。允许引用已提供的同课真实字幕，跨块引用只记审计提示，不影响本阶段提交；不得编造不存在的编号。"
                +"保留完整题目条件与有用事实，跨块重复在后续整稿合并；安排原话按同课真实字幕核验。返回完整JSON对象，来源编号示例只表示结构。";
    }
    private static String assignmentRules(){
        return "课堂安排必须是老师明确布置的具体任务，不能把知识点、一般编程建议、自拟复习事项转成安排。"
                +"每项assignments除text、sources外还必须带evidence，逐字摘录原始字幕中表达该要求的最短连续原话，不用更正文稿的改写，不拼接非连续片段。"
                +"sources须列全引文覆盖的每条来源，不可跳过中间字幕；只摘具体行动要求，不带入无关分数或背景段落。没有真实要求就返回[]。"
                +"明确行动须按动作及对象分别提取，不因措辞相同或相邻教学内容而合并不同对象，也不因未规定提交时间或方式而漏掉；只保留原文明确要求，不自行升级成提交作业。"
                +"先核对要求的时态、面向对象及上下文。仅回顾已结束或仅属往届、其他班级且未要求本班当前执行的任务不提取，不能因出现‘布置作业’等词就创建本班当前行动。本班仍有效的既有任务，其截止提醒、补交或继续执行要求仍须保留，不要求重新布置才算行动。";
    }
    static void validateRefinement(JSONObject part,JSONObject lecture,JSONArray block)throws Exception{
        validateRefinement(part,lecture,block,ClassroomAffairsPolicy.VERSION);
    }
    static void validateRefinement(JSONObject part,JSONObject lecture,JSONArray block,int affairsSchema)throws Exception{
        ClassroomSummary.validateBlock(part,lecture,affairsSchema);
        ClassroomSummary.validateAssignmentEvidence(part,lecture.getJSONArray("segments"));
    }
    private void recordScopeWarnings(String stage,JSONObject part,JSONArray block)throws Exception{
        JSONArray warnings=state.optJSONArray("source_scope_warnings");if(warnings==null){warnings=new JSONArray();state.put("source_scope_warnings",warnings);}
        Set<Integer> current=new HashSet<>();for(int i=0;i<block.length();i++)current.add(block.getJSONObject(i).getInt("id"));
        for(String collection:new String[]{"notes","affairs","assignments"}){
            JSONArray entries=part.optJSONArray(collection);if(entries==null)continue;
            for(int i=0;i<entries.length();i++){
                JSONArray refs=entries.getJSONObject(i).getJSONArray("sources"),outside=new JSONArray();
                for(int n=0;n<refs.length();n++)if(!current.contains(refs.getInt(n)))outside.put(refs.getInt(n));
                if(outside.length()>0)warnings.put(new JSONObject().put("stage",stage).put("field",collection+"["+i+"].sources").put("sources",outside));
            }
        }
    }
    /** Keep the original plan stable; commit prose in bounded consecutive-topic batches. */
    private void edit(JSONArray parts,JSONArray analysed)throws Exception{
        boolean originalEditorial=state.optBoolean("editorial_source_first");
        if(!state.has("editor_plan")){
            status("AI 全课主题编排",0,1);
            JSONArray index=new JSONArray();
            for(int p=0;p<parts.length();p++)for(String collection:collections(affairsSchema)){JSONArray notes=parts.getJSONObject(p).getJSONArray(collection);for(int n=0;n<notes.length();n++){
                JSONObject note=notes.getJSONObject(n),row=originalEditorial?editorialReference(note):new JSONObject().put("title",note.getString("title")).put("text",note.getString("text")).put("sources",note.getJSONArray("sources"));
                row.put("ref",reference(p,collection,n,affairsSchema));
                if(affairsSchema>0)row.put("collection",collection);index.put(row);
            }}
            String prompt="全部分段提取与核图已完成，现在编排一篇与普通模式相同的全课笔记，不向用户展示分段流水账。"
                    +(originalEditorial?"索引以sources定位下方唯一原始字幕集合中的语境，origin=original_subtitles，images定位已提供的图片，不附历史派生稿正文。题名和原集合仅用于定位，不作为事实或分类结论；结合原话及历史中实际已提供的原图、核图证据，逐条判断主题。":"逐条对照历史中完整的提取与核图正文，索引只用于引用，不能仅凭标题分组。")
                    +"同一事实即使措辞和标题不同也应归入同一主题；不同题目条件、评分例外和相反结论不可误并。先比较正文中的规则、对象和条件，再确定主题。不同标题重复讲同一规则时，将其归入同一主题并保留互补细节，不分别编排后再重复生成。"
                    +(state.optInt("note_strength",1)==3
                            ?"同一知识点的重复提取必须放入同一主题，合并跨段续讲；以老师讲授方向为准，可按知识依赖重排为精简讲义，保留不同题目条件、代码、纠错和评分细节，不能按输入段号设章节。"
                            :"同一知识点的重复提取必须放入同一主题，合并跨段续讲，按课程讲授顺序组织；保留不同题目条件、代码、纠错和评分细节，不能按输入段号设章节。")
                    +(affairsSchema==0?"只输出紧凑JSON {\"overview\":\"全课统一重点概览\",\"sections\":[{\"title\":\"主题\",\"refs\":[\"0:0\"]}]}。":"只输出紧凑JSON {\"overview\":\"知识重点概览\",\"sections\":[{\"title\":\"主题\",\"collection\":\"notes\",\"refs\":[\"0:notes:0\"]}]}。collection只可为notes或affairs，表示本主题按真实正文确定的最终类别，可纠正初稿分类；ref里的原集合只用于准确定位，不限制最终类别。")
                    +(affairsSchema==0?"overview用少量完整句说明全课核心，只用文字不附图，不逐段复述、不写第几段或未提及的类别。refs是下面真实索引，每条必须恰好使用一次，不得遗漏、重复或编造；本轮不生成笔记正文。没有教学笔记则sections=[]，overview为空。":"overview仅概括知识，只用文字不附图；没有知识时overview为空，课程事务仍须编排。每条notes及affairs索引必须恰好使用一次，不得遗漏、重复或编造；本轮不生成正文。相同事实即使分别来自初稿notes和affairs，也须合入同一正确类别的主题，保留独特事实、完整考核依据、范围和不确定性；不同知识与课程事务仍各自成节，不在同一节混写。")
                    +"概览只交代少量知识主线，不列整套题号、答案或逐项公式。编排须覆盖全课并统一后续指代，局部‘未提及’结论不能覆盖其它段已有信息。"
                    +"\n已完成笔记索引：\n"+index
                    +(originalEditorial?"\n编排原始字幕集合：\n"+editorialContext(index,full.getJSONArray("segments")):"");
            state.put("editor_plan",json("edit-plan",prompt,Collections.emptyList(),value->validatePlan(value,parts,affairsSchema)));save();
        }
        JSONObject plan=state.getJSONObject("editor_plan");JSONArray sections=plan.getJSONArray("sections"),edited=state.optJSONArray("editor_notes");
        if(edited==null){edited=new JSONArray();state.put("editor_notes",edited);}
        for(int i=state.optInt("editor_section_done",edited.length());i<sections.length();){
            status("AI 全课主题整稿",i,sections.length());
            boolean sourceFirst=state.optBoolean("affairs_source_first");
            int end=editorBatchEnd(parts,sections,i,state.optInt("editor_batch_size",1),state.optInt("editor_batch_chars",LEGACY_EDITOR_BATCH_CHARS),sourceFirst,full.getJSONArray("segments"),originalEditorial),count=end-i;
            boolean batch=count>1;
            JSONObject section=sections.getJSONObject(i);String collection=affairsSchema==0?"notes":section.getString("collection");JSONArray inputs=new JSONArray(),topics=new JSONArray();Set<Integer> sources=new HashSet<>();long extractedCharacters=0;
            for(int s=i;s<end;s++){
                JSONObject current=sections.getJSONObject(s);JSONArray topicInputs=new JSONArray(),refs=current.getJSONArray("refs");
                for(int n=0;n<refs.length();n++){
                    JSONObject note=noteAt(parts,refs.getString(n));
                    extractedCharacters+=note.getString("text").length();
                    JSONObject supplied=originalEditorial?editorialReference(note):editorialInput(note,full.getJSONArray("segments"),sourceFirst&&"affairs".equals(current.optString("collection")));
                    inputs.put(supplied);topicInputs.put(supplied);JSONArray ids=note.getJSONArray("sources");for(int k=0;k<ids.length();k++)sources.add(ids.getInt(k));
                }
                topics.put(new JSONObject().put("title",current.getString("title")).put("collection",affairsSchema==0?"notes":current.getString("collection")).put("inputs",topicInputs));
            }
            JSONArray segments=new JSONArray(),all=full.getJSONArray("segments");for(int n=0;n<all.length();n++)if(sources.contains(all.getJSONObject(n).getInt("id")))segments.put(all.getJSONObject(n));
            boolean unifiedAffairs=sourceFirst&&collection.equals("affairs");
            JSONArray proseInputs=unifiedAffairs&&!originalEditorial?unifiedAffairsInput(inputs,all):inputs;
            String prompt="全课整稿主题 "+(i+1)+"/"+sections.length()+"："+section.getString("title")+"。"
                    +(unifiedAffairs?"本次统一整理本组课程事务。原计划标题只用于定位，不是必须逐项生成的成品目录；按原话中的事实、适用对象与条件重新归类，同一事实只归入一处，不先写宽泛总节再按细目重写。":batch?"本次连续处理 "+count+" 个原计划主题，主题清单及各自提取稿见末尾。保留各主题的独立标题，不把所有主题揉成一个大段；仅同义主题可合并去重。":"把下面同主题提取稿合为一节完整笔记，")
                    +"消除重复叙述、重复截图和‘图片核对’流水账。保持普通模式的精炼组织，不因内部多轮而增加重复概览、分段编号、无关闲聊或未提及事项的说明。"
                    +(originalEditorial?"本组inputs仅定位sources与已提供图片，原话及邻文在下方唯一原始字幕集合中，不附待润色的旧笔记；题名不预设正确解释。根据原话、已提供原图及本组可见内容证据重新组织讲义，历史更正文稿和提取稿仅帮助定位，不能作独立事实依据。数学过程先还原实际表达式与操作链，再解释真正失效的前提；教师指令按原话的动作和作用对象确定方向，不能沿用多份派生稿共同重复的误读。原图独有的题设、推导与书面要求也应保留，不能因字幕未口述而省去。":"")
                    +(sourceFirst&&collection.equals("affairs")&&!originalEditorial?"本组事务输入的text直接来自原始字幕及相邻语境，origin=original_subtitles；主题标题仅供定位，历史更正文稿与派生稿不作教师事实模板。根据这些原话及实际已提供的原图重新整理事务，不能沿用历史派生稿中的结论或其错误否定方向。原图明确展示的资源、题号等仍可记录，不能因字幕没有口述就忽略；也不能由图片中的解题内容推断口头考核承诺。把本组同一规则、对象及适用条件集中讲一次，范围不明只保留能确认的部分。":"")
                    +"保留每个有来源的独特事实、题目数字与条件、代码、纠错、明确不考/会考以及改卷给分细节；相同事实只写一次，不可为缩短而删除这些细节。不改变原话确定性。"
                    +"先核对本主题的题干、公式、定义域和证明是否自洽，再成稿；补齐已知条件可推出的关键步骤和最终结果，不能以‘化简即得’掩盖缺失计算。"
                    +"严格与非严格不等式不可直接互换；若结论需要严格号，应说明等号何时成立或为何在当前条件下不能成立，不能从非严格估计直接跳到严格结论。"
                    +"用实际公式核对操作文字；相反数与倒数、求导与取极限、除以整个式子与分子分母同除不能混写。若描述与公式矛盾，直接改正描述，不保留互相冲突的旧说法。"
                    +"同一章节包含不同证明工具或转化机制时，保留能独立复算的代表过程；同一完整例题已覆盖多个机制时不另写重复例题，不按机制名称机械扩写。"
                    +"区分求候选值与证明存在：可先在存在假设下求必要候选值，再独立证明存在性并核实定理前提；不能把候选值计算当成存在性证明。循环推理或未满足定理条件的尝试才归错误分析，不能以尚未成立的条件支撑正确解法。"
                    +"已核实的识别错误直接采用正确结果，不复述误识别词、核图过程或相互冲突的旧草稿。无关闲聊、轶事和无信息的尾段不保留。"
                    +"补充例题仅填补原题确实不明的学习缺口；后续已从原图或资料确认原题时，以完整原题替代同机制补题，不把两道仅换函数或数字而解法同构的题并列扩写。"
                    +"不把‘题干未读出、细节未确认、此处按公式补齐’等内部生产说明写进正文。确有数学条件不明时只标明影响结论的具体条件；同一机制已有完整例题，不再保留无题干空壳。"
                    +(batch&&affairsSchema>0?"每个主题按实际内容独立归入notes知识或affairs课程事务，可纠正初稿分类；知识保留解法与例题，事务保留规则、条件与例外，禁止将事务混入知识。具体行动保持在assignments，不重复抄录。":affairsSchema>0&&collection.equals("notes")
                            ?"本主题以知识、方法与例题为主；若提取稿实际只有评分或考试等事务，应纠正到affairs，不为维持初稿类别伪写知识。具体行动保持在assignments，不重复抄录。"
                            :affairsSchema>0?"本主题只保留有学生使用价值的课程事务，不重讲解法或题目答案；同一规则的分值、条件和例外合在一处，保留真实不确定性，不列无关的未提供字段清单。":"")
                    +"与历史中本轮已整稿主题比较，重复事实只在所属主题完整讲一次；关联用主题名称，不用‘上一节／下一节’等重排后易失效的指代，不承诺尚未发布的任务清单。"
                    +"title由页面统一呈现，text不再重复该标题；正文内的小节使用简短加粗引导，不再插入与外层主题同级的Markdown标题，避免标题堆叠造成拼装感。"
                    +(state.optInt("note_strength",1)==3
                            ?"按3档生成精简讲义，积极重组但压紧表达；保留自学需要的过程。原题无法确定时可换用同主线、同类型及相近难度的完整题目，标题标为‘补充例题’，给出必要推导和结论，不冒充老师原题或考核要求。删掉无关叙事与重复，不堆砌标题或小结，不扩写通用教材。"
                            :originalEditorial?"沿用普通单篇总结的详略，正文不扩写成教程。本组原提取稿正文合计"+extractedCharacters+"个UTF16字符，仅用此数值控制成品长度，不超过该合计；重复较多时应明显缩短。"
                            :"沿用普通单篇总结的详略，正文不扩写成教程，不超过本主题提取稿正文的合计长度；重复较多时应明显缩短。")
                    +scope(segments)+"sources汇集实际使用的来源；图片只能使用已实际分析且支持本主题的原file，历史图片不重新上传。"
                    +(state.optInt("figure_policy")>0?"图注仅描述实际可见内容，不能把字幕讲过的公式或证明当作图片已显示；已排除的低置信度、空白、仅标题或遮挡图片不得重新引用。实际图像分析已在历史原消息中，最终图片准入表（included=false不得引用）："+figureAdmission(state.optJSONArray("figure_reviews")):"")
                    +"只返回一个JSON，overview为空，按实际内容放入"+(affairsSchema==0?"notes":"notes或affairs")
                    +"，覆盖本组全部主题；可合并同义主题，也可将混合主题拆为知识和事务，不按输入条数限定输出条数。assignments必须=[]，不重复抄录具体行动。"+ClassroomSummary.topicBatchSchema(affairsSchema)
                    +(unifiedAffairs?"\n本主题提取稿：\n"+proseInputs:batch?"\n本组主题清单及提取稿：\n"+topics:"\n本主题提取稿：\n"+inputs)
                    +"\n本组引用图片的已有可见内容核对："+topicFigureEvidence(inputs,state.optJSONArray("figure_reviews"))
                    +"。准入只表示图片本身可用，不表示与任意段落匹配；逐项比较实际题干、公式及对象，不能将另一题的截图插入当前例题或按正文改写图注。无匹配图片时保留文字解法，不强行配图。"
                    +"\n对应原始字幕（用于核对教师事实、指令方向及与更正文稿的冲突，不仅用于安排引文定位）：\n"
                    +(originalEditorial?editorialContext(inputs,all):unifiedAffairs?"本组原文及相邻语境已在上方原始字幕输入中给出，不重复附录。":ClassroomSummary.uploadChunk(blockInput(segments,new JSONArray())).toString())
                    +"\n原字幕是教师事实依据，更正文稿与提取稿都是待核对的派生稿。出现冲突时回到原字幕及相邻语境，不因多份派生稿重复同一解释就认定为真。"
                    +"对未能确认的范围或术语，保留可靠事实并仅标明具体疑点；删除无依据的事实分支，不能用‘相关’或直接引号包装成确定结论。";
            JSONObject value=json("edit-note-"+i,prompt,Collections.emptyList(),result->{
                ClassroomSummary.validateBlock(result,blockInput(full.getJSONArray("segments"),analysed),affairsSchema);
                if(state.optInt("figure_policy")>0)ClassroomFigureReview.filter(result,state.optJSONArray("figure_reviews"));
                validateEditorial(result,affairsSchema);
            });
            recordScopeWarnings("edit-note-"+i,value,segments);
            for(String actualCollection:value.has("affairs")?new String[]{"notes","affairs"}:new String[]{"notes"}){
                JSONArray rows=value.getJSONArray(actualCollection);
                for(int n=0;n<rows.length();n++){JSONObject note=new JSONObject(rows.getJSONObject(n).toString());if(affairsSchema>0)note.put("collection",actualCollection);edited.put(note);}
            }
            state.put("editor_section_done",end);save();i=end;
            ClassroomStore.write(new File(dir,"summary.json"),assemble(parts,analysed,false));
        }
    }
    static int editorBatchEnd(JSONArray parts,JSONArray sections,int start,int batchSize,int batchChars)throws Exception{
        return editorBatchEnd(parts,sections,start,batchSize,batchChars,false,null);
    }
    static int editorBatchEnd(JSONArray parts,JSONArray sections,int start,int batchSize,int batchChars,boolean sourceFirst,JSONArray all)throws Exception{
        return editorBatchEnd(parts,sections,start,batchSize,batchChars,sourceFirst,all,false);
    }
    static int editorBatchEnd(JSONArray parts,JSONArray sections,int start,int batchSize,int batchChars,boolean sourceFirst,JSONArray all,boolean originalEditorial)throws Exception{
        int end=start,limit=Math.max(1,batchSize);long chars=0;JSONArray evidence=new JSONArray();
        String collection=sections.getJSONObject(start).optString("collection","notes");
        if(sourceFirst&&collection.equals("affairs"))limit=Integer.MAX_VALUE;
        while(end<sections.length()&&end-start<limit){
            if(sourceFirst&&!collection.equals(sections.getJSONObject(end).optString("collection","notes")))break;
            long next=0;JSONArray refs=sections.getJSONObject(end).getJSONArray("refs");
            for(int n=0;n<refs.length();n++){
                JSONObject note=noteAt(parts,refs.getString(n));
                if(originalEditorial)evidence.put(editorialReference(note));else next+=editorialInput(note,all,sourceFirst&&collection.equals("affairs")).getString("text").length();
            }
            long total=originalEditorial?editorialContext(evidence,all).length():chars+next;
            if(end>start&&total>batchChars)break;
            chars=total;end++;
        }
        return end;
    }
    static JSONObject editorialInput(JSONObject note,JSONArray all,boolean useOriginal)throws Exception{
        if(!useOriginal)return new JSONObject(note.toString());
        Set<Integer> cited=new HashSet<>();JSONArray ids=note.getJSONArray("sources");for(int i=0;i<ids.length();i++)cited.add(ids.getInt(i));
        Set<Integer> positions=new TreeSet<>();
        for(int i=0;i<all.length();i++)if(cited.contains(all.getJSONObject(i).getInt("id"))){
            for(int n=Math.max(0,i-1);n<=Math.min(all.length()-1,i+1);n++)positions.add(n);
        }
        JSONArray context=new JSONArray();for(int position:positions)context.put(all.getJSONObject(position));
        JSONObject reference=editorialReference(note),input=new JSONObject(note.toString());
        return input.put("origin","original_subtitles").put("images",reference.getJSONArray("images"))
                .put("text",ClassroomSummary.uploadChunk(blockInput(context,new JSONArray())).toString());
    }
    static JSONObject editorialReference(JSONObject note)throws Exception{
        Set<String> files=new LinkedHashSet<>();JSONArray originals=note.optJSONArray("images");
        if(originals!=null)for(int i=0;i<originals.length();i++)files.add(ClassroomDocument.imageName(originals.getString(i)));
        files.addAll(ClassroomDocument.inlineImages(note.optString("text")));files.addAll(ClassroomDocument.inlineImages(note.optString("title")));
        JSONArray images=new JSONArray();for(String file:files)images.put(file);
        return new JSONObject().put("title",note.getString("title")).put("sources",new JSONArray(note.getJSONArray("sources").toString()))
                .put("origin","original_subtitles").put("images",images);
    }
    static String editorialContext(JSONArray inputs,JSONArray all)throws Exception{
        return unifiedAffairsInput(inputs,all).getJSONObject(0).getString("text");
    }
    /** One chronological evidence stream, rather than overlapping copies under derived headings. */
    static JSONArray unifiedAffairsInput(JSONArray inputs,JSONArray all)throws Exception{
        Set<Integer> seen=new TreeSet<>();Set<String> files=new LinkedHashSet<>();
        for(int i=0;i<inputs.length();i++){
            JSONArray ids=inputs.getJSONObject(i).getJSONArray("sources");
            for(int n=0;n<ids.length();n++)seen.add(ids.getInt(n));
            JSONArray images=inputs.getJSONObject(i).optJSONArray("images");
            if(images!=null)for(int n=0;n<images.length();n++)files.add(ClassroomDocument.imageName(images.getString(n)));
        }
        JSONArray ids=new JSONArray();for(int id:seen)ids.put(id);
        JSONArray images=new JSONArray();for(String file:files)images.put(file);
        JSONObject row=new JSONObject().put("title","课程事务原文").put("sources",ids).put("images",images).put("text","");
        return new JSONArray().put(editorialInput(row,all,true));
    }
    /** All editorial branches feed one final affairs publication, never an appended second copy. */
    private void consolidateAffairs(JSONArray parts,JSONArray analysed)throws Exception{
        if(affairsSchema==0||!state.optBoolean("unified_affairs")||state.has("editor_affairs"))return;
        JSONObject evidence=affairsEvidence(parts,state.optJSONArray("editor_notes"),full.getJSONArray("segments"),state.optJSONArray("figure_reviews"));
        if(full.getJSONArray("segments").length()==0){state.put("editor_affairs",new JSONArray());save();return;}
        status("AI 全课事务整合",0,1);
        String prompt="现在只生成全课唯一的课程事务区，替换此前所有分段与整稿中的事务，不追加第二份。"
                +"以下original_subtitles是全课原始字幕，不受前轮候选或标题范围限制。完整扫描每条，寻找有实际使用价值的考核、评分、出勤、资源与课堂要求，不漏掉前轮未提取的事实；知识讲解和闲聊略去。"
                +"按相同规则、适用对象和条件合并重复；互补分值、例外、范围和细微考核线索保留，一项事实只出现一次。不要把宽泛总节和其中各条分别再写一遍。"
                +"笔记整理强度只控制知识编排，不授权改写教师事务的动作方向。涉及允许、禁止、保留、划掉或给分的要求，正文直接保留决定方向的最短原话再交代条件；不能用通常建议替换原话，不采用历史派生稿的结论。否定对象不明时只保留清楚语句支持的要求，不自行增加禁止项。"
                +"音近片段无法确认时只保留前后清楚语句支持的事实，不猜术语、不增加得分入口；不扩写批改轶事或空泛学习建议。"
                +"对待遇或分数调整，分别核对原有状态、触发行为、可能变化方向和决定者；不能把一次触发改成再次触发，数值不清也不能把变化擅定为增加。"
                +"学习解法不重讲，具体行动已在独立安排集合，不再重复。事务图片应直接呈现该条事务讨论的对象或书面规则，且与原话关联；不能仅凭数学公式、板书划改或软件状态推断画面未呈现、教师也未说的要求。图注只写可见内容，缺乏直接关联时不配图，不拿另一题的演算截图充作评分禁令证据。"
                +"只输出JSON {\"affairs\":[{\"title\":\"事务主题\",\"text\":\"精简完整事务正文\",\"sources\":[0],\"images\":[]}]}。无可靠事务时affairs=[]。"
                +"sources使用真实原id；原文只用于核对，不抄录无关口语。\n全课事务证据：\n"+evidence;
        JSONObject value=json("edit-affairs",prompt,Collections.emptyList(),result->{
            JSONObject wrapped=new JSONObject().put("overview","").put("notes",new JSONArray()).put("assignments",new JSONArray()).put("affairs",result.getJSONArray("affairs"));
            ClassroomSummary.validateBlock(wrapped,blockInput(full.getJSONArray("segments"),analysed),affairsSchema);
            if(state.optInt("figure_policy")>0)ClassroomFigureReview.filter(wrapped,state.optJSONArray("figure_reviews"));
            ClassroomSummary.validateBlock(wrapped,blockInput(full.getJSONArray("segments"),analysed),affairsSchema);
        });
        state.put("editor_affairs",value.getJSONArray("affairs"));save();
    }
    static JSONObject affairsEvidence(JSONArray parts,JSONArray edited,JSONArray all,JSONArray reviews)throws Exception{
        JSONArray inputs=new JSONArray();
        for(int i=0;i<parts.length();i++){
            JSONArray rows=parts.getJSONObject(i).optJSONArray("affairs");
            if(rows!=null)for(int n=0;n<rows.length();n++)inputs.put(editorialInput(rows.getJSONObject(n),all,true));
        }
        if(edited!=null)for(int i=0;i<edited.length();i++)if("affairs".equals(edited.getJSONObject(i).optString("collection")))inputs.put(editorialInput(edited.getJSONObject(i),all,true));
        JSONObject combined=unifiedAffairsInput(inputs,all).getJSONObject(0);
        return new JSONObject().put("original_subtitles",ClassroomSummary.uploadChunk(blockInput(all,new JSONArray())))
                .put("images",combined.getJSONArray("images")).put("visible",topicFigureEvidence(new JSONArray().put(combined),reviews));
    }
    static JSONArray figureAdmission(JSONArray reviews)throws Exception{
        JSONArray result=new JSONArray();if(reviews!=null)for(int i=0;i<reviews.length();i++){
            JSONObject row=reviews.getJSONObject(i);result.put(new JSONObject().put("file",row.getString("file")).put("included",row.optBoolean("included")));
        }
        return result;
    }
    static JSONArray topicFigureEvidence(JSONArray inputs,JSONArray reviews)throws Exception{
        Set<String> files=new HashSet<>();
        for(int i=0;i<inputs.length();i++){
            JSONObject note=inputs.getJSONObject(i);JSONArray images=note.optJSONArray("images");
            if(images!=null)for(int n=0;n<images.length();n++)files.add(ClassroomDocument.imageName(images.getString(n)));
            files.addAll(ClassroomDocument.inlineImages(note.optString("text")));
            files.addAll(ClassroomDocument.inlineImages(note.optString("title")));
        }
        JSONArray result=new JSONArray();
        if(reviews!=null)for(int i=0;i<reviews.length();i++){
            JSONObject row=reviews.getJSONObject(i);
            if(files.contains(row.getString("file")))result.put(new JSONObject().put("file",row.getString("file"))
                    .put("included",row.optBoolean("included")).put("visible",row.optString("visible")));
        }
        return result;
    }
    static void validateEditorial(JSONObject value,int affairsSchema)throws Exception{
        int total=value.getJSONArray("notes").length()+(value.has("affairs")?value.getJSONArray("affairs").length():0);
        if(total<1||value.getJSONArray("assignments").length()!=0)throw new IOException("整稿须保留知识或课程事务，具体安排保留在安排集合");
    }
    static void validatePlan(JSONObject plan,JSONArray parts)throws Exception{
        validatePlan(plan,parts,ClassroomAffairsPolicy.VERSION);
    }
    static void validatePlan(JSONObject plan,JSONArray parts,int affairsSchema)throws Exception{
        String overview=plan.getString("overview");Set<String> remaining=new HashSet<>();
        boolean teaching=false;
        for(int p=0;p<parts.length();p++)for(String collection:collections(affairsSchema))for(int n=0;n<parts.getJSONObject(p).getJSONArray(collection).length();n++)remaining.add(reference(p,collection,n,affairsSchema));
        if(!ClassroomDocument.inlineImages(overview).isEmpty())throw new IOException("整稿概览只允许文字，配图须在主题笔记中核对");
        JSONArray sections=plan.getJSONArray("sections");Map<String,Integer> topicOwners=new HashMap<>();
        for(int i=0;i<sections.length();i++){
            JSONObject section=sections.getJSONObject(i);if(section.getString("title").isBlank())throw new IOException("整稿主题标题为空");
            String collection=affairsSchema==0?"notes":section.getString("collection");
            if(!Arrays.asList(collections(affairsSchema)).contains(collection))throw new IOException("整稿主题类别无效："+collection);
            teaching|=collection.equals("notes");
            JSONArray refs=section.getJSONArray("refs");if(refs.length()==0)throw new IOException("整稿主题缺少原笔记");
            for(int n=0;n<refs.length();n++){
                String ref=refs.getString(n);if(!remaining.remove(ref))throw new IOException("整稿引用了重复或不存在的笔记："+ref);
                String topic=collection+":"+noteAt(parts,ref).getString("title").replaceAll("\\s+","");Integer owner=topicOwners.putIfAbsent(topic,i);
                if(owner!=null&&owner!=i)throw new IOException("同名主题被拆成重复章节："+topic);
            }
        }
        if(!remaining.isEmpty())throw new IOException("整稿遗漏了已提取笔记："+remaining);
        if(teaching&&overview.isBlank())throw new IOException("整稿缺少全课重点概览");
        if(affairsSchema>0&&!teaching&&!overview.isBlank())throw new IOException("无教学笔记时知识概览必须为空");
    }
    private static String[] collections(int affairsSchema){return affairsSchema==0?new String[]{"notes"}:new String[]{"notes","affairs"};}
    private static String reference(int part,String collection,int index,int affairsSchema){return affairsSchema==0?part+":"+index:part+":"+collection+":"+index;}
    private static JSONObject noteAt(JSONArray parts,String ref)throws Exception{String[] pair=ref.split(":");return parts.getJSONObject(Integer.parseInt(pair[0])).getJSONArray(pair.length==2?"notes":pair[1]).getJSONObject(Integer.parseInt(pair[pair.length-1]));}
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
        if(state.has("image_audit")&&state.has("figure_reviews"))state.getJSONObject("image_audit").put("figures",state.getJSONArray("figure_reviews"));
        JSONObject plan=state.optJSONObject("editor_plan");String overview=plan==null?"":plan.getString("overview");
        JSONArray edited=state.optJSONArray("editor_notes"),tasks=state.optJSONArray("editor_assignments");
        JSONObject document=new JSONObject().put("overview",overview).put("notes",new JSONArray()).put("assignments",tasks==null?new JSONArray():new JSONArray(tasks.toString()));
        if(affairsSchema>0)document.put("affairs",new JSONArray());
        if(edited!=null)for(int i=0;i<edited.length();i++){JSONObject note=new JSONObject(edited.getJSONObject(i).toString());String collection=affairsSchema==0?"notes":note.getString("collection");note.remove("collection");document.getJSONArray(collection).put(note);}
        if(affairsSchema>0&&state.has("editor_affairs"))document.put("affairs",new JSONArray(state.getJSONArray("editor_affairs").toString()));
        if(state.has("compact_note"))document=new JSONObject(state.getJSONObject("compact_note").toString());
        if(document.getJSONArray("notes").length()==0)document.put("overview","");
        ClassroomSummary.validateBlock(document,blockInput(full.getJSONArray("segments"),analysed),affairsSchema);
        if(state.optInt("figure_policy")>0)ClassroomFigureReview.filter(document,state.optJSONArray("figure_reviews"));
        overview=document.getString("overview");
        JSONArray corrected=state.getJSONArray("corrected");StringBuilder transcript=new StringBuilder();
        for(int i=0;i<corrected.length();i++){if(i>0)transcript.append("\n\n");transcript.append(corrected.getString(i));}
        return new JSONObject().put("lesson",lesson.json()).put("run",state.getString("run")).put("updated",System.currentTimeMillis()).put("complete",complete)
                .put("note_strength",state.optInt("note_strength",1))
                .put("affairs_schema",affairsSchema)
                .put("overview",overview).put("parts",new JSONArray().put(document)).put("total_parts",1).put("source_blocks",state.getJSONArray("blocks").length())
                .put("corrected_transcript",ClassroomDocument.normalizeMarkdown(transcript.toString())).put("images",new JSONArray(analysed.toString()))
                .put("planned_images",state.has("selected")?state.getJSONArray("selected").length():0).put("raw_images",materials.getInt("raw_images"))
                .put("candidate_images",candidates.getJSONArray("images").length()).put("image_audit",state.optJSONObject("image_audit"))
                .put("source_scope_warnings",state.optJSONArray("source_scope_warnings"));
    }
    private JSONObject json(String stage,String prompt,List<byte[]> images,ClassroomJsonStage.Validator validator)throws Exception{
        return json(stage,prompt,images,Collections.emptyList(),validator);
    }
    private JSONObject json(String stage,String prompt,List<byte[]> images,List<String> imageNames,ClassroomJsonStage.Validator validator)throws Exception{
        return new ClassroomJsonStage(dir,state,conversation,control).generate(stage,prompt,images,imageNames,validator,
                "-user-retry-","用户授权继续当前板块。上一条回复未通过校验：",null);
    }
    private String text(String stage,String prompt,List<byte[]> images)throws Exception{control.check();return conversation.text(stage,prompt,images);}
    private void status(String stage,int done,int total)throws Exception{control.check();progress.update(new ClassroomProgress(stage,done,total,"已完成 "+done+" / "+total));}
    private void save()throws IOException{ClassroomStore.write(new File(dir,"run.json"),state);}
    private static JSONObject blockInput(JSONArray segments,JSONArray images)throws Exception{return new JSONObject().put("segments",segments).put("images",images);}
    static JSONArray contentGroups(JSONArray blocks,int batchSize)throws Exception{
        JSONArray result=new JSONArray();int size=Math.max(1,batchSize);
        for(int start=0;start<blocks.length();start+=size){
            JSONArray group=new JSONArray();
            for(int i=start;i<Math.min(blocks.length(),start+size);i++){
                JSONArray block=blocks.getJSONArray(i);for(int n=0;n<block.length();n++)group.put(block.getJSONObject(n));
            }
            result.put(group);
        }
        return result;
    }
    static JSONArray coverageOutline(JSONArray corrected,int first,int last)throws Exception{
        Set<String> titles=new LinkedHashSet<>();
        char fence=0;int fenceLength=0;
        for(int i=0;i<last;i++)for(String line:corrected.getString(i).split("\\R")){
            String trimmed=line.strip();
            char marker=trimmed.isEmpty()?0:trimmed.charAt(0);int count=0;
            if(marker=='`'||marker=='~')while(count<trimmed.length()&&trimmed.charAt(count)==marker)count++;
            if(count>=3){
                if(fence==0){fence=marker;fenceLength=count;continue;}
                if(marker==fence&&count>=fenceLength&&trimmed.substring(count).isBlank()){fence=0;continue;}
            }
            if(fence!=0)continue;
            if(i>=first&&trimmed.matches("#{1,6}\\s+.+"))titles.add(trimmed.replaceFirst("^#{1,6}\\s+", ""));
        }
        return new JSONArray(titles);
    }
    static JSONArray blocks(JSONArray segments)throws Exception{
        return blocks(segments,BLOCK_CHARS);
    }
    static boolean compactEligible(JSONArray segments)throws Exception{
        long chars=0;for(int i=0;i<segments.length();i++)chars+=segments.getJSONObject(i).getString("text").length();
        return chars<=WHOLE_LESSON_CHARS;
    }
    static JSONArray blocks(JSONArray segments,int maxChars)throws Exception{
        JSONArray result=new JSONArray(),block=new JSONArray();int chars=0;
        for(int i=0;i<segments.length();i++){
            JSONObject row=segments.getJSONObject(i);String text=row.getString("text");
            int offset=0;
            do{
                if(offset==0&&chars>0&&text.length()<=maxChars&&text.length()>maxChars-chars){result.put(block);block=new JSONArray();chars=0;}
                if(chars==maxChars){result.put(block);block=new JSONArray();chars=0;}
                int end=Math.min(text.length(),offset+maxChars-chars);
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
