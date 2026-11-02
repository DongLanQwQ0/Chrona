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
        JSONArray drafts=state.optJSONArray("drafts");if(drafts==null){drafts=new JSONArray();state.put("drafts",drafts);}
        JSONObject catalogue=full.getJSONObject("image_catalogue");
        for(int i=drafts.length();i<blocks.length();i++){
            status("AI 分段整理与核图计划",i,blocks.length());
            JSONArray block=blocks.getJSONArray(i);
            String prompt=(i==0&&state.has("correction_reused_from")?"课程："+lesson.course+"；课堂："+lesson.title+"。以下复用已完成的修正文稿，按本轮要求重新整理笔记。":"")
                    +"更正文稿已全部完成。现在只整理第 "+(i+1)+" 段。"+scope(block)
                    +"历史仅用于理解语境，不得把最后一段或其它段当作本段。只提取本段事实，不写全课总结；同一内容不跨段重述。"
                    +(affairsSchema==0?"本段没有教学内容或明确安排时，overview为空字符串，notes和assignments为空数组；不能用闲聊或‘未提及评分规则’填充。":"本段无教学内容时overview为空且notes=[]；非行动事务进入affairs，具体行动只进入assignments，三者均无内容时返回空数组，不用闲聊填充。")
                    +assignmentRules()+ClassroomSummary.draftSchema(catalogue,imageLimit,true,affairsSchema)
                    +"\n本次要提取的更正文稿（历史原消息保持不变，此处仅追加定位）：\n"+corrected.getString(i)
                    +"\n安排原话核验用原始字幕（只作evidence逐字定位，列全引文覆盖的sources）：\n"+ClassroomSummary.uploadChunk(blockInput(block,new JSONArray()))
                    +"\n全课PPT可用目录（只表示时间分布，不是图片内容）："+catalogue;
            drafts.put(json("draft-"+i,prompt,Collections.emptyList(),value->{ClassroomSummary.validateBlock(value,blockInput(block,new JSONArray()),affairsSchema);ClassroomSummary.validateAssignmentEvidence(value,block);ClassroomSummary.validateImageAssessment(value,catalogue);}));save();
        }
        if(!state.has("selected")){
            JSONArray requests=new JSONArray();for(int i=0;i<drafts.length();i++){JSONArray rows=drafts.getJSONObject(i).getJSONArray("image_requests");for(int n=0;n<rows.length();n++)requests.put(rows.getJSONObject(n));}
            JSONArray picked=ClassroomSummary.selectRequested(candidates.getJSONArray("images"),requests,imageLimit),selected=new JSONArray();
            for(int i=0;i<picked.length();i++){JSONObject p=picked.getJSONObject(i);JSONObject row=new JSONObject().put("file","image-"+p.getInt("index")+".jpg").put("second",p.getDouble("second")).put("raw",p.getString("raw"));if(p.optJSONObject("quality")!=null)row.put("quality",p.getJSONObject("quality"));selected.put(row);}
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
                inventory.put(ClassroomFigureReview.inventory(p));bytes.add(ClassroomStore.bytes(file));
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
            parts.put(value);save();ClassroomStore.write(new File(dir,"summary.json"),assemble(parts,analysed,false));
        }
        edit(parts,analysed);
        editAssignments(parts);
        JSONObject result=assemble(parts,analysed,true);state.put("final",result);save();ClassroomStore.write(new File(dir,"summary.json"),result);return result;
    }
    private static String scope(JSONArray block)throws Exception{
        return "本次允许的字幕来源id仅为："+ids(block)+"；sources不得引用这个集合之外的编号。";
    }
    static String refinementScope(JSONArray block,int affairsSchema)throws Exception{
        return "当前核图块的真实来源id："+ids(block)+"。"+(affairsSchema>0?"notes与affairs的每条":"notes的每条")
                +"都必须讲述本块真实事实，sources至少含一条对应的本块id。前文只可在同条中作真实对照，不单独输出前文回顾或其它块条目；不得为了过关虚挂本块id。"
                +"这是分块核图，不是全课整稿；跨块重排与合并留给后续整稿阶段。逐条检查事实归属：完全属于前文的条目从本次返回集合中移除，不改历史已提交条目，前文不会因本次不重写而丢失。"
                +"同一例题或证明跨块时，本次只补本块新增步骤、条件或纠错，前文只作理解所需对照，不重新完整讲前一块；仍返回本阶段完整JSON对象，不返回局部补丁。"
                +"本块没有相应内容时返回空集合，不用历史内容或常识填充。assignments仍只引用本块并逐字核验原话。schema中的来源编号示例只表示结构，不是本块的实际来源。";
    }
    private static String assignmentRules(){
        return "课堂安排必须是老师明确布置的具体任务，不能把知识点、一般编程建议、自拟复习事项转成安排。"
                +"每项assignments除text、sources外还必须带evidence，逐字摘录原始字幕中表达该要求的最短连续原话，不用更正文稿的改写，不拼接非连续片段。"
                +"sources须列全引文覆盖的每条来源，不可跳过中间字幕；只摘具体行动要求，不带入无关分数或背景段落。没有真实要求就返回[]。";
    }
    static void validateRefinement(JSONObject part,JSONObject lecture,JSONArray block)throws Exception{
        validateRefinement(part,lecture,block,ClassroomAffairsPolicy.VERSION);
    }
    static void validateRefinement(JSONObject part,JSONObject lecture,JSONArray block,int affairsSchema)throws Exception{
        ClassroomSummary.validateBlock(part,lecture,affairsSchema);
        Set<Integer> current=new HashSet<>();int first=Integer.MAX_VALUE;
        for(int i=0;i<block.length();i++){int id=block.getJSONObject(i).getInt("id");current.add(id);first=Math.min(first,id);}
        for(String collection:collections(part.has("affairs")?ClassroomAffairsPolicy.VERSION:affairsSchema)){
            JSONArray notes=part.getJSONArray(collection);
            for(int n=0;n<notes.length();n++){
                JSONArray refs=notes.getJSONObject(n).getJSONArray("sources"),safeRefs=new JSONArray();boolean anchored=false;
                for(int k=0;k<refs.length();k++){int id=refs.getInt(k);safeRefs.put(id);if(current.contains(id))anchored=true;else if(id>=first)throw new IOException("核图来源错误："+collection+"["+n+"].sources引用了本块之后的字幕："+id);}
                if(!anchored)throw new IOException("核图来源错误："+collection+"["+n+"].sources="+safeRefs+"；缺少本块真实来源，不可只重写前文");
            }
        }
        JSONArray tasks=part.getJSONArray("assignments");for(int i=0;i<tasks.length();i++){
            JSONArray refs=tasks.getJSONObject(i).getJSONArray("sources");for(int n=0;n<refs.length();n++)if(!current.contains(refs.getInt(n)))throw new IOException("核图来源错误：assignments["+i+"].sources引用了本块之外的字幕："+refs.getInt(n));
        }
        ClassroomSummary.validateAssignmentEvidence(part,block);
    }
    /** Only references are planned globally; prose is edited one topic at a time. */
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
                    +(affairsSchema==0?"只输出紧凑JSON {\"overview\":\"全课统一重点概览\",\"sections\":[{\"title\":\"主题\",\"refs\":[\"0:0\"]}]}。":"只输出紧凑JSON {\"overview\":\"知识重点概览\",\"sections\":[{\"title\":\"主题\",\"collection\":\"notes\",\"refs\":[\"0:notes:0\"]}]}。collection只可为notes或affairs，且必须与每条ref的原集合一致，禁止跨类别合并。")
                    +(affairsSchema==0?"overview用少量完整句说明全课核心，只用文字不附图，不逐段复述、不写第几段或未提及的类别。refs是下面真实索引，每条必须恰好使用一次，不得遗漏、重复或编造；本轮不生成笔记正文。没有教学笔记则sections=[]，overview为空。":"overview仅概括知识，只用文字不附图；没有notes时overview为空，affairs仍须编排。每条notes及affairs索引必须恰好使用一次，不得遗漏、重复或编造；本轮不生成正文。相同主题只在同一类别内合并，保留完整考核依据、范围和不确定性。")
                    +"概览只交代少量知识主线，不列整套题号、答案或逐项公式。编排须覆盖全课并统一后续指代，局部‘未提及’结论不能覆盖其它段已有信息。"
                    +"\n已完成笔记索引：\n"+index;
            state.put("editor_plan",json("edit-plan",prompt,Collections.emptyList(),value->validatePlan(value,parts,affairsSchema)));save();
        }
        JSONObject plan=state.getJSONObject("editor_plan");JSONArray sections=plan.getJSONArray("sections"),edited=state.optJSONArray("editor_notes");
        if(edited==null){edited=new JSONArray();state.put("editor_notes",edited);}
        for(int i=edited.length();i<sections.length();i++){
            status("AI 全课主题整稿",i,sections.length());
            JSONObject section=sections.getJSONObject(i);String collection=affairsSchema==0?"notes":section.getString("collection");JSONArray inputs=new JSONArray();Set<Integer> sources=new HashSet<>();
            JSONArray refs=section.getJSONArray("refs");
            for(int n=0;n<refs.length();n++){JSONObject note=noteAt(parts,refs.getString(n));inputs.put(note);JSONArray ids=note.getJSONArray("sources");for(int k=0;k<ids.length();k++)sources.add(ids.getInt(k));}
            JSONArray segments=new JSONArray(),all=full.getJSONArray("segments");for(int n=0;n<all.length();n++)if(sources.contains(all.getJSONObject(n).getInt("id")))segments.put(all.getJSONObject(n));
            String prompt="全课整稿主题 "+(i+1)+"/"+sections.length()+"："+section.getString("title")+"。"
                    +"把下面同主题提取稿合为一节完整笔记，消除重复叙述、重复截图和‘图片核对’流水账。保持普通模式的精炼组织，不因内部多轮而增加重复概览、分段编号、无关闲聊或未提及事项的说明。"
                    +"保留每个有来源的独特事实、题目数字与条件、代码、纠错、明确不考/会考以及改卷给分细节；相同事实只写一次，不可为缩短而删除这些细节。不改变原话确定性。"
                    +"先核对本主题的题干、公式、定义域和证明是否自洽，再成稿；补齐已知条件可推出的关键步骤和最终结果，不能以‘化简即得’掩盖缺失计算。"
                    +"已核实的识别错误直接采用正确结果，不复述误识别词、核图过程或相互冲突的旧草稿。无关闲聊、轶事和无信息的尾段不保留。"
                    +(affairsSchema>0&&collection.equals("notes")
                            ?"本主题只讲知识、方法与例题；评分、考试和具体行动已有各自类别，不能再次抄入知识正文。"
                            :affairsSchema>0?"本主题只保留有学生使用价值的课程事务，不重讲解法或题目答案；同一规则的分值、条件和例外合在一处，保留真实不确定性，不列无关的未提供字段清单。":"")
                    +"与历史中本轮已整稿主题比较，重复事实只在所属主题完整讲一次；关联用主题名称，不用‘上一节／下一节’等重排后易失效的指代，不承诺尚未发布的任务清单。"
                    +(state.optInt("note_strength",1)==3
                            ?"按3档生成精简讲义，积极重组但压紧表达；保留自学需要的过程，仅补资料可支持且必要的解释，删掉无关叙事与重复，不堆砌标题或小结，不扩写通用教材。"
                            :"沿用普通单篇总结的详略，正文不扩写成教程，不超过本主题提取稿正文的合计长度；重复较多时应明显缩短。")
                    +scope(segments)+"sources汇集实际使用的来源；图片只能使用已实际分析且支持本主题的原file，历史图片不重新上传。"
                    +(state.optInt("figure_policy")>0?"图注仅描述实际可见内容，不能把字幕讲过的公式或证明当作图片已显示；已排除的低置信度、空白、仅标题或遮挡图片不得重新引用。保存的图像核对："+state.optJSONArray("figure_reviews"):"")
                    +(affairsSchema==0?"只返回一个JSON，overview为空，notes恰好一项，assignments=[]。":"只返回一个JSON，overview为空，"+collection+"恰好一项，另一内容集合及assignments为空数组。保持本主题原类别，不把事务并入学习笔记。")+ClassroomSummary.topicSchema(affairsSchema,collection)
                    +"\n本主题提取稿：\n"+inputs;
            JSONObject value=json("edit-note-"+i,prompt,Collections.emptyList(),result->{
                ClassroomSummary.validateBlock(result,blockInput(segments,analysed),affairsSchema);
                if(state.optInt("figure_policy")>0)ClassroomFigureReview.filter(result,state.optJSONArray("figure_reviews"));
                if(result.getJSONArray(collection).length()!=1||affairsSchema>0&&result.getJSONArray(collection.equals("notes")?"affairs":"notes").length()!=0||result.getJSONArray("assignments").length()!=0||!result.getString("overview").isBlank())throw new IOException("整稿主题必须仅返回原类别的一节内容，不重复概览或安排");
            });
            JSONObject note=new JSONObject(value.getJSONArray(collection).getJSONObject(0).toString());if(affairsSchema>0)note.put("collection",collection);
            edited.put(note);save();
            ClassroomStore.write(new File(dir,"summary.json"),assemble(parts,analysed,false));
        }
    }
    static void validatePlan(JSONObject plan,JSONArray parts)throws Exception{
        validatePlan(plan,parts,ClassroomAffairsPolicy.VERSION);
    }
    static void validatePlan(JSONObject plan,JSONArray parts,int affairsSchema)throws Exception{
        String overview=plan.getString("overview");Set<String> remaining=new HashSet<>();
        boolean teaching=false;
        for(int p=0;p<parts.length();p++)for(String collection:collections(affairsSchema))for(int n=0;n<parts.getJSONObject(p).getJSONArray(collection).length();n++){remaining.add(reference(p,collection,n,affairsSchema));teaching|=collection.equals("notes");}
        if(teaching&&overview.isBlank())throw new IOException("整稿缺少全课重点概览");
        if(affairsSchema>0&&!teaching&&!overview.isBlank())throw new IOException("无教学笔记时知识概览必须为空");
        if(!ClassroomDocument.inlineImages(overview).isEmpty())throw new IOException("整稿概览只允许文字，配图须在主题笔记中核对");
        JSONArray sections=plan.getJSONArray("sections");Map<String,Integer> topicOwners=new HashMap<>();
        for(int i=0;i<sections.length();i++){
            JSONObject section=sections.getJSONObject(i);if(section.getString("title").isBlank())throw new IOException("整稿主题标题为空");
            String collection=affairsSchema==0?"notes":section.getString("collection");
            if(!Arrays.asList(collections(affairsSchema)).contains(collection))throw new IOException("整稿主题类别无效："+collection);
            JSONArray refs=section.getJSONArray("refs");if(refs.length()==0)throw new IOException("整稿主题缺少原笔记");
            for(int n=0;n<refs.length();n++){
                String ref=refs.getString(n);if(!remaining.remove(ref))throw new IOException("整稿引用了重复或不存在的笔记："+ref);
                if(affairsSchema>0&&!ref.split(":")[1].equals(collection))throw new IOException("整稿主题混合了学习笔记与课程事务："+ref);
                String topic=collection+":"+noteAt(parts,ref).getString("title").replaceAll("\\s+","");Integer owner=topicOwners.putIfAbsent(topic,i);
                if(owner!=null&&owner!=i)throw new IOException("同名主题被拆成重复章节："+topic);
            }
        }
        if(!remaining.isEmpty())throw new IOException("整稿遗漏了已提取笔记："+remaining);
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
                .put("candidate_images",candidates.getJSONArray("images").length()).put("image_audit",state.optJSONObject("image_audit"));
    }
    private JSONObject json(String stage,String prompt,List<byte[]> images,ClassroomJsonStage.Validator validator)throws Exception{
        return new ClassroomJsonStage(dir,state,conversation,control).generate(stage,prompt,images,validator,
                "-user-retry-","用户授权继续当前板块。上一条回复未通过校验：",null);
    }
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
