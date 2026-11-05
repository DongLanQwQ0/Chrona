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
                +"明确要求学生执行的具体行动仍只放assignments，不在affairs或notes重复。混合段落按事实拆开，保留有用的信息及决定含义的条件。"
                +"同一行动的条件与可选做法合为一项，不把‘或另一种方法’拆成额外必做任务；只提到未发布的行动清单、内部字段名或本段未提及的类别，不应成为正文说明。"
                +"同一规则合并重复表述。重要评分情景保留对象、分值、作答方式及触发后果，旁枝举例可精简；教师指令的动作与方向按原文语境表达。"
                +"事务中的题目或程序仅保留资料已明确的内容；未明确的题干、公式或代码不为解释评分而重建，用已知题目主题定位并直接记录老师明确的书写动作或要求。"
                +"考试、评分与任务的适用对象、时间和范围直接写进对应句子，往届信息直接注明往届；后文修正或澄清更新前面的理解。只有影响实际理解或行动的具体缺项才局部指出，其余含混闲话省略。"
                +"正文只呈现课程事项及可用信息，表达紧凑，按主题集中，不附加免责声明、审校结论或对读者的泛泛提醒。"
                +"无知识内容时overview为空、notes=[]；无事务时affairs=[]，无行动时assignments=[]，不创建空知识章节。";
    }
}
