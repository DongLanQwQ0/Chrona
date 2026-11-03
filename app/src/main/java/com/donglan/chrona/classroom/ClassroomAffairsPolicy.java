package com.donglan.chrona.classroom;

import org.json.JSONObject;

/** The response contract belongs to the saved conversation, not today's defaults. */
public final class ClassroomAffairsPolicy {
    public static final int VERSION=1;
    private ClassroomAffairsPolicy() { }
    public static int frozen(JSONObject run){
        return run!=null&&run.has("conversation")&&!run.has("final")?run.optInt("affairs_schema",0):VERSION;
    }
    static String instructions(int version){
        return version==0?"":"\n成品严格分离学习内容与课程事务：overview仅概述所讲知识，notes仅保存概念、推导、公式、例题、代码与教学纠错。"
                +"affairs保存非行动型课程事务，包括评分与改卷依据、考试时间/形式/范围/题型/分值、明确会考或不考、透题线索、出勤、课堂规则及资源，以及其他有实际意义的非学习内容；不收入无关闲聊。"
                +"题目解法、教材答案与公式纠错仍归notes；相关评分细则和考试线索归affairs，可引用同一字幕但不重复同一事实。"
                +"概览不混入考试、评分或行动。一般解题策略、本次讲题答案、AI文风评价、人物生平和讲课结束过程均不作课程事务。"
                +"明确要求学生执行的具体行动仍只放assignments，不在affairs或notes重复。混合段落按事实拆开，知识与考核依据分别保留原条件、细节、来源和不确定性，不能遗漏细微提示。"
                +"同一行动的条件与可选做法合为一项，不把‘或另一种方法’拆成额外必做任务；只提到未发布的行动清单、内部字段名或本段未提及的类别，不应成为正文说明。"
                +"评分细则按同一规则合并表达一次，所有数字、条件、例外、纠正及语气强度仍完整保留；课堂答题要求标明其课程范围，不误写成普遍数学定理。"
                +"考核章、节、题号或范围的原始转写缺损时，只标明相应疑点；修正文稿和初稿的改写不能作为独立证据，将不明边界抬高为明确范围。‘只考／不考’须保留当时讨论的知识或题型范围，不扩大成整场考试的绝对限定。"
                +"教师具体评分要求即使不同于常见建议，也须保留其语境和原方向，不按常识改成相反指令。音近术语未确认时保留可靠部分，不另造数学命题或加分条件。点名的考查对象、要求动作、方向、条件和例外逐项保留，不能用‘经常考’等泛化结论替代。"
                +"处理肯定、否定和禁止要求时，先定位每个谓词及其作用对象，结合前后句判断指代；相邻的‘不要’不能跨句套到另一动作上。派生稿与原话冲突时，只保留原话能够支持的指令，不以惯常建议补齐。"
                +"转写含混的术语若不是理解可靠事实所必需，应省去该术语而保留已确认的要求；不能先写出猜测命题再加‘不确定’作为保留依据。无实际使用价值的人物、人数和批改轶事省去，真正影响得分的条件及例外保留。"
                +"无知识内容时overview为空、notes=[]；无事务时affairs=[]，无行动时assignments=[]，不创建空知识章节。";
    }
}
