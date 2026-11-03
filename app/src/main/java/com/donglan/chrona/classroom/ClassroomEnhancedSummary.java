package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.*;
import com.donglan.chrona.net.RequestControl;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bounded outputs over one growing, immutable-prefix conversation. Never compacts earlier inputs. */
final class ClassroomEnhancedSummary {
    private static final int BLOCK_CHARS=6000;
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
        if(!state.has("blocks"))state.put("blocks",blocks(full.getJSONArray("segments")));
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
        if(!state.has("parts")){ClassroomStore.write(new File(dir,"summary.json"),assemble(new JSONArray(),new JSONArray(),false));status("更正文稿已保存",corrected.length(),blocks.length());}
        int contentBatchSize=Math.max(1,state.optInt("content_batch_size",1));
        JSONArray groups=contentGroups(blocks,contentBatchSize);
        JSONArray drafts=state.optJSONArray("drafts");if(drafts==null){drafts=new JSONArray();state.put("drafts",drafts);}
        JSONObject catalogue=full.getJSONObject("image_catalogue");
        for(int i=drafts.length();i<groups.length();i++){
            status("AI 分段整理与核图计划",i,groups.length());
            JSONArray block=groups.getJSONArray(i);StringJoiner transcript=new StringJoiner("\n\n");
            for(int n=i*contentBatchSize;n<Math.min(corrected.length(),(i+1)*contentBatchSize);n++)transcript.add(corrected.getString(n));
            String prompt=(i==0&&state.has("correction_reused_from")?"课程："+lesson.course+"；课堂："+lesson.title+"。以下复用已完成的修正文稿，按本轮要求重新整理笔记。":"")
                    +"更正文稿已全部完成。现在只整理第 "+(i+1)+(contentBatchSize==1?" 段。":" 内容组（原文第 "+(i*contentBatchSize+1)+" 至 "+Math.min(blocks.length(),(i+1)*contentBatchSize)+" 段）。")
                    +"以本段为整理主体，不写全课总结；同题跨段可以引用已提供的同课真实来源，避免重复讲解，后续统一整稿。"
                    +(affairsSchema==0?"本段没有教学内容或明确安排时，overview为空字符串，notes和assignments为空数组；不能用闲聊或‘未提及评分规则’填充。":"本段无教学内容时overview为空且notes=[]；非行动事务进入affairs，具体行动只进入assignments，三者均无内容时返回空数组，不用闲聊填充。")
                    +assignmentRules()+ClassroomSummary.draftSchema(catalogue,imageLimit,true,affairsSchema)
                    +"\n本次要提取的更正文稿（历史原消息保持不变，此处仅追加定位）：\n"+transcript
                    +"\n安排原话核验用原始字幕（只作evidence逐字定位，列全引文覆盖的sources）：\n"+ClassroomSummary.uploadChunk(blockInput(block,new JSONArray()))
                    +"\n全课PPT可用目录（只表示时间分布，不是图片内容）："+catalogue+"\n"+scope(block);
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
            JSONArray inventory=new JSONArray();List<byte[]> bytes=new ArrayList<>();
            for(int n=0;n<selected.length();n++){
                JSONObject p=selected.getJSONObject(n);if(!requested(p,drafts.getJSONObject(i).getJSONArray("image_requests"))||contains(analysed,p.getString("file")))continue;
                File file=new File(dir,p.getString("file"));if(!file.exists())ClassroomStore.writeBytes(file,ClassroomImages.jpeg(new File(dir,p.getString("raw"))));
                inventory.put(ClassroomFigureReview.inventory(p));bytes.add(ClassroomStore.bytes(file));
            }
            JSONObject value;
            boolean reuse=false;for(int n=0;n<analysed.length();n++)reuse|=requested(analysed.getJSONObject(n),drafts.getJSONObject(i).getJSONArray("image_requests"));
            if(inventory.length()==0&&!reuse)value=drafts.getJSONObject(i);
            else{
                JSONArray allowed=new JSONArray(analysed.toString());for(int n=0;n<inventory.length();n++)allowed.put(inventory.getJSONObject(n));
                JSONArray block=groups.getJSONArray(i);
                String prompt="只核实并完善第 "+(i+1)+(contentBatchSize==1?" 段笔记。":" 内容组笔记（原文第 "+(i*contentBatchSize+1)+" 至 "+Math.min(blocks.length(),(i+1)*contentBatchSize)+" 段）。")+"当前主体来源id："+ids(block)+"。允许引用已提供的同课真实来源，不因分块边界删掉有用内容；跨段重复在后续全课整稿中合并。"
                        +"保留题目和改卷线索、原话条件及不确定性。只修订下面指定初稿，不要依据最后一条历史回复另写一段，不重述其它段。"+assignmentRules()
                        +"本次图片依次对应以下file原值，编号可能不连续，禁止重新编号："+inventory
                        +"。此前已提供的图片保持在原消息位置。仅可引用本次或此前实际提供的file，不得把请求过但未附的图片当作已看到。"+ClassroomSummary.schema(true,affairsSchema)+ClassroomFigureReview.schema(state.optInt("figure_policy"))
                        +"\n安排原话核验用原始字幕：\n"+ClassroomSummary.uploadChunk(blockInput(block,new JSONArray()))
                        +"\n本次唯一要修订的初稿：\n"+drafts.getJSONObject(i)+"\n"+refinementScope(block,affairsSchema);
                value=json("final-"+i,prompt,bytes,result->{
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
        JSONObject result=assemble(parts,analysed,true);state.put("final",result);save();ClassroomStore.write(new File(dir,"summary.json"),result);return result;
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
                +"明确行动须按动作及对象分别提取，不因措辞相同或相邻教学内容而合并不同对象，也不因未规定提交时间或方式而漏掉；只保留原文明确要求，不自行升级成提交作业。";
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
        if(!state.has("editor_plan")){
            status("AI 全课主题编排",0,1);
            JSONArray index=new JSONArray();
            for(int p=0;p<parts.length();p++)for(String collection:collections(affairsSchema)){JSONArray notes=parts.getJSONObject(p).getJSONArray(collection);for(int n=0;n<notes.length();n++){
                JSONObject note=notes.getJSONObject(n),row=new JSONObject().put("ref",reference(p,collection,n,affairsSchema)).put("title",note.getString("title")).put("sources",note.getJSONArray("sources"));
                if(affairsSchema>0)row.put("collection",collection);index.put(row);
            }}
            String prompt="全部分段提取与核图已完成，现在编排一篇与普通模式相同的全课笔记，不向用户展示分段流水账。"
                    +"逐条对照历史中完整的提取与核图正文，索引只用于引用，不能仅凭标题分组。同一事实即使措辞和标题不同也应归入同一主题；不同题目条件、评分例外和相反结论不可误并。"
                    +(state.optInt("note_strength",1)==3
                            ?"同一知识点的重复提取必须放入同一主题，合并跨段续讲；以老师讲授方向为准，可按知识依赖重排为精简讲义，保留不同题目条件、代码、纠错和评分细节，不能按输入段号设章节。"
                            :"同一知识点的重复提取必须放入同一主题，合并跨段续讲，按课程讲授顺序组织；保留不同题目条件、代码、纠错和评分细节，不能按输入段号设章节。")
                    +(affairsSchema==0?"只输出紧凑JSON {\"overview\":\"全课统一重点概览\",\"sections\":[{\"title\":\"主题\",\"refs\":[\"0:0\"]}]}。":"只输出紧凑JSON {\"overview\":\"知识重点概览\",\"sections\":[{\"title\":\"主题\",\"collection\":\"notes\",\"refs\":[\"0:notes:0\"]}]}。collection只可为notes或affairs，表示本主题按真实正文确定的最终类别，可纠正初稿分类；ref里的原集合只用于准确定位，不限制最终类别。")
                    +(affairsSchema==0?"overview用少量完整句说明全课核心，只用文字不附图，不逐段复述、不写第几段或未提及的类别。refs是下面真实索引，每条必须恰好使用一次，不得遗漏、重复或编造；本轮不生成笔记正文。没有教学笔记则sections=[]，overview为空。":"overview仅概括知识，只用文字不附图；没有知识时overview为空，课程事务仍须编排。每条notes及affairs索引必须恰好使用一次，不得遗漏、重复或编造；本轮不生成正文。相同事实即使分别来自初稿notes和affairs，也须合入同一正确类别的主题，保留独特事实、完整考核依据、范围和不确定性；不同知识与课程事务仍各自成节，不在同一节混写。")
                    +"概览只交代少量知识主线，不列整套题号、答案或逐项公式。编排须覆盖全课并统一后续指代，局部‘未提及’结论不能覆盖其它段已有信息。"
                    +"\n已完成笔记索引：\n"+index;
            state.put("editor_plan",json("edit-plan",prompt,Collections.emptyList(),value->validatePlan(value,parts,affairsSchema)));save();
        }
        JSONObject plan=state.getJSONObject("editor_plan");JSONArray sections=plan.getJSONArray("sections"),edited=state.optJSONArray("editor_notes");
        if(edited==null){edited=new JSONArray();state.put("editor_notes",edited);}
        for(int i=state.optInt("editor_section_done",edited.length());i<sections.length();){
            status("AI 全课主题整稿",i,sections.length());
            int end=editorBatchEnd(parts,sections,i,state.optInt("editor_batch_size",1),state.optInt("editor_batch_chars",LEGACY_EDITOR_BATCH_CHARS)),count=end-i;
            boolean batch=count>1;
            JSONObject section=sections.getJSONObject(i);String collection=affairsSchema==0?"notes":section.getString("collection");JSONArray inputs=new JSONArray(),topics=new JSONArray();Set<Integer> sources=new HashSet<>();
            for(int s=i;s<end;s++){
                JSONObject current=sections.getJSONObject(s);JSONArray topicInputs=new JSONArray(),refs=current.getJSONArray("refs");
                for(int n=0;n<refs.length();n++){JSONObject note=noteAt(parts,refs.getString(n));inputs.put(note);topicInputs.put(note);JSONArray ids=note.getJSONArray("sources");for(int k=0;k<ids.length();k++)sources.add(ids.getInt(k));}
                topics.put(new JSONObject().put("title",current.getString("title")).put("collection",affairsSchema==0?"notes":current.getString("collection")).put("inputs",topicInputs));
            }
            JSONArray segments=new JSONArray(),all=full.getJSONArray("segments");for(int n=0;n<all.length();n++)if(sources.contains(all.getJSONObject(n).getInt("id")))segments.put(all.getJSONObject(n));
            String prompt="全课整稿主题 "+(i+1)+"/"+sections.length()+"："+section.getString("title")+"。"
                    +(batch?"本次连续处理 "+count+" 个原计划主题，主题清单及各自提取稿见末尾。保留各主题的独立标题，不把所有主题揉成一个大段；仅同义主题可合并去重。":"把下面同主题提取稿合为一节完整笔记，")
                    +"消除重复叙述、重复截图和‘图片核对’流水账。保持普通模式的精炼组织，不因内部多轮而增加重复概览、分段编号、无关闲聊或未提及事项的说明。"
                    +"保留每个有来源的独特事实、题目数字与条件、代码、纠错、明确不考/会考以及改卷给分细节；相同事实只写一次，不可为缩短而删除这些细节。不改变原话确定性。"
                    +"先核对本主题的题干、公式、定义域和证明是否自洽，再成稿；补齐已知条件可推出的关键步骤和最终结果，不能以‘化简即得’掩盖缺失计算。"
                    +"已核实的识别错误直接采用正确结果，不复述误识别词、核图过程或相互冲突的旧草稿。无关闲聊、轶事和无信息的尾段不保留。"
                    +(batch&&affairsSchema>0?"每个主题按实际内容独立归入notes知识或affairs课程事务，可纠正初稿分类；知识保留解法与例题，事务保留规则、条件与例外，禁止将事务混入知识。具体行动保持在assignments，不重复抄录。":affairsSchema>0&&collection.equals("notes")
                            ?"本主题以知识、方法与例题为主；若提取稿实际只有评分或考试等事务，应纠正到affairs，不为维持初稿类别伪写知识。具体行动保持在assignments，不重复抄录。"
                            :affairsSchema>0?"本主题只保留有学生使用价值的课程事务，不重讲解法或题目答案；同一规则的分值、条件和例外合在一处，保留真实不确定性，不列无关的未提供字段清单。":"")
                    +"与历史中本轮已整稿主题比较，重复事实只在所属主题完整讲一次；关联用主题名称，不用‘上一节／下一节’等重排后易失效的指代，不承诺尚未发布的任务清单。"
                    +(state.optInt("note_strength",1)==3
                            ?"按3档生成精简讲义，积极重组但压紧表达；保留自学需要的过程。原题无法确定时可换用同主线、同类型及相近难度的完整题目，标题标为‘补充例题’，给出必要推导和结论，不冒充老师原题或考核要求。删掉无关叙事与重复，不堆砌标题或小结，不扩写通用教材。"
                            :"沿用普通单篇总结的详略，正文不扩写成教程，不超过本主题提取稿正文的合计长度；重复较多时应明显缩短。")
                    +scope(segments)+"sources汇集实际使用的来源；图片只能使用已实际分析且支持本主题的原file，历史图片不重新上传。"
                    +(state.optInt("figure_policy")>0?"图注仅描述实际可见内容，不能把字幕讲过的公式或证明当作图片已显示；已排除的低置信度、空白、仅标题或遮挡图片不得重新引用。实际图像分析已在历史原消息中，最终图片准入表（included=false不得引用）："+figureAdmission(state.optJSONArray("figure_reviews")):"")
                    +(batch?"只返回一个JSON，overview为空，"+(affairsSchema==0?"notes":"notes与affairs合计")+"返回1至"+count+"项，覆盖本组全部主题；仅同义主题可合并，assignments必须=[]。"+ClassroomSummary.topicBatchSchema(affairsSchema)
                            :(affairsSchema==0?"只返回一个JSON，overview为空，notes恰好一项，assignments=[]。":"只返回一个JSON，overview为空，按实际内容放入notes或affairs，合计恰好一项；另一内容集合及assignments为空数组，不把事务并入学习笔记。")+ClassroomSummary.topicSchema(affairsSchema,collection))
                    +(batch?"\n本组主题清单及提取稿：\n"+topics:"\n本主题提取稿：\n"+inputs);
            JSONObject value=json("edit-note-"+i,prompt,Collections.emptyList(),result->{
                ClassroomSummary.validateBlock(result,blockInput(full.getJSONArray("segments"),analysed),affairsSchema);
                if(state.optInt("figure_policy")>0)ClassroomFigureReview.filter(result,state.optJSONArray("figure_reviews"));
                if(batch)validateTopicBatch(result,affairsSchema,count);else topicCollection(result,affairsSchema);
            });
            recordScopeWarnings("edit-note-"+i,value,segments);
            for(String actualCollection:collections(affairsSchema)){
                JSONArray rows=value.getJSONArray(actualCollection);
                for(int n=0;n<rows.length();n++){JSONObject note=new JSONObject(rows.getJSONObject(n).toString());if(affairsSchema>0)note.put("collection",actualCollection);edited.put(note);}
            }
            state.put("editor_section_done",end);save();i=end;
            ClassroomStore.write(new File(dir,"summary.json"),assemble(parts,analysed,false));
        }
    }
    static int editorBatchEnd(JSONArray parts,JSONArray sections,int start,int batchSize,int batchChars)throws Exception{
        int end=start,limit=Math.max(1,batchSize);long chars=0;
        while(end<sections.length()&&end-start<limit){
            long next=0;JSONArray refs=sections.getJSONObject(end).getJSONArray("refs");
            for(int n=0;n<refs.length();n++)next+=noteAt(parts,refs.getString(n)).getString("text").length();
            if(end>start&&chars+next>batchChars)break;
            chars+=next;end++;
        }
        return end;
    }
    static JSONArray figureAdmission(JSONArray reviews)throws Exception{
        JSONArray result=new JSONArray();if(reviews!=null)for(int i=0;i<reviews.length();i++){
            JSONObject row=reviews.getJSONObject(i);result.put(new JSONObject().put("file",row.getString("file")).put("included",row.optBoolean("included")));
        }
        return result;
    }
    static void validateTopicBatch(JSONObject value,int affairsSchema,int count)throws Exception{
        int total=value.getJSONArray("notes").length()+(affairsSchema==0?0:value.getJSONArray("affairs").length());
        if(total<1||total>count||value.getJSONArray("assignments").length()!=0)throw new IOException("整稿批次须返回1至"+count+"节知识或课程事务，具体安排保留在安排集合");
    }
    static String topicCollection(JSONObject value,int affairsSchema)throws Exception{
        int notes=value.getJSONArray("notes").length(),affairs=affairsSchema==0?0:value.getJSONArray("affairs").length();
        if(notes+affairs!=1||value.getJSONArray("assignments").length()!=0)throw new IOException("整稿主题须返回一节知识或课程事务，具体安排保留在安排集合");
        return notes==1?"notes":"affairs";
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
        return new ClassroomJsonStage(dir,state,conversation,control).generate(stage,prompt,images,validator,
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
