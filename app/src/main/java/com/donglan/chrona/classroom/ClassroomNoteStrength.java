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
                +"各档都保留题干条件、推导关键步骤、纠错、不确定性，以及评分、考试、扣分、期限和细微考核提示；不能为简洁删掉这些信息。"
                +"以下风格只用于提取笔记与最终成稿，更正文稿仍逐条保留内容，不作摘要。";
        return common+(level==2
                ?"合并分散讲述并调整衔接，以更顺畅的文字覆盖同等实质内容；减少口语和转述，不额外扩写背景教材。"
                :"成品是一篇精简、结构化、可以直接自学的课程讲义。以老师实际讲授的内容与重点为方向，允许按知识依赖积极重排、合并跨段内容并剔除无关叙事。"
                 +"直接解释知识，减少‘老师说/老师演示’等转述；相同事实只讲一次，不为每句增设标题、小结或复习建议。"
                 +"保留理解所需的条件、推导、代码或例子；按已知题干补齐必要演算和结果，让读者能复现。原题无法确定时，可按老师讲授的知识主线换用同类型补充例题，题干完整、过程清楚、结论自洽，标题标为‘补充例题’即可；不猜补原题、不编造考核承诺。"
                 +"同一道题将题干、错误方法及原因、正确解法集中讲一次；同一证明保留一条完整逻辑，不再叠加课堂口述版、书面版和重复步骤小结。"
                 +"概览只勾勒知识主线，不逐题堆公式和答案；正文只留帮助理解的必要解释，去掉轶事、谐音梗、人物生平、问答复述以及无信息尾段。"
                 +"比转述式总结更凝练，不把自学目标理解为长篇扩写，不堆砌背景、反复定义或重复总结，不机械规定压缩比例。保持整理深度，压紧表达。");
    }
}
