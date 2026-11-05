package com.donglan.chrona.classroom;

import org.json.JSONObject;

/** Editorial freedom, not a target word count. Level one preserves the established summary. */
public final class ClassroomNoteStrength {
    public static final int DEFAULT=3;
    private ClassroomNoteStrength() { }
    public static int validate(int level){
        if(level<1||level>3)throw new IllegalArgumentException("笔记整理强度必须为 1、2 或 3");
        return level;
    }
    public static String label(int level){
        return switch(validate(level)){case 1->"原有整理";case 2->"顺畅笔记";default->"精简讲义";};
    }
    /** An unfinished conversation always keeps its original style and sent prefix. */
    public static int frozen(JSONObject run,int preferred){
        validate(preferred);
        return run!=null&&run.has("conversation")&&!run.has("final")
                ?validate(run.optInt("note_strength",1)):preferred;
    }
    public static String instructions(int level){
        validate(level);
        if(level==1)return "";
        String common="\n笔记整理强度："+level+"档。强度表示编排与去冗余的自由度，不表示增加篇幅。"
                +"以现有完整单篇总结的知识覆盖、关键例子与必要过程为基线，不压缩成简短提纲。"
                +"各档都保留自学所需的条件、关键过程和解释，以及有用的考试、评分和任务信息；精简重复与闲话，不删必要讲解。"
                +"以下风格只用于提取笔记与最终成稿，更正文稿仍逐条保留内容，不作摘要。";
        return common+(level==2
                ?"合并分散讲述并调整衔接，以更顺畅的文字覆盖同等实质内容；减少口语和转述，不额外扩写背景教材。"
                :"成品是一篇精简、结构化、可以直接自学的课程讲义。以老师实际讲授的内容与重点为方向，允许按知识依赖积极重排、合并跨段内容并剔除无关叙事。"
                 +"直接解释知识，减少‘老师说/老师演示’等转述；相同事实只讲一次，不为每句增设标题、小结或复习建议。"
                 +"老师确定知识脉络、重点和教学目标，AI自主组织具体教学细节；不要求跟随课堂的措辞、讲解顺序、具体推导或解法。可以选择更清楚的可靠解法，自行补充必要解释、过渡、推导、代码或完整例题，让读者直接自学；补充围绕本课主线，保持简洁，不扩写无关教材。"
                 +"保留所采用题目的完整条件，不为求解方便偷换题设；可用同主线、同类型及相近难度的补充例题替换不利于讲清方法的课堂例题，不限于原题无法确定的情况。替换或自行构造的题目标为‘补充例题’，不冒充老师原题，不与同机制原题重复展开。课程事务以教师实际说过的内容为准。"
                 +"若保留一种例题，应能直接用于学习；无法确定原题时优先换为完整补充例题，已有完整同类例题则省去这段残缺叙述，不写‘有一道题但无法复原’的空话。"
                 +"同一道题将题干、错误方法及原因、正确解法集中讲一次；同一证明保留一条完整逻辑，不再叠加课堂口述版、书面版和重复步骤小结。"
                 +"解释、推导和示例与采用的完整条件一致，说明关键步骤为什么成立；指出错误时说明真正失效的前提。需要引入本课以外的工具时补足简短依据，让读者能接着学，不用‘化简即得’或仅报结果跳过必要过程。"
                 +"概览只勾勒知识主线，不逐题堆公式和答案；正文只留帮助理解的必要解释，去掉轶事、谐音梗、人物生平、问答复述以及无信息尾段。"
                 +"比转述式总结更凝练，不把自学目标理解为长篇扩写，不堆砌背景、反复定义或重复总结，不机械规定压缩比例。保持整理深度，压紧表达。");
    }
}
