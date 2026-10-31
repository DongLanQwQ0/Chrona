package com.donglan.chrona;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.ClassroomNoteStrength;

/** The same substantial classroom excerpt at three editorial strengths. */
public final class ClassroomNoteStrengthActivity extends Activity {
    private ScrollView viewport;
    private static final String ORIGINAL="""
            今天接着讲条件语句和逻辑运算。大家先看这个程序：if (a > 0) 后面没有大括号，下一行是 printf(\"A\")，再下一行是 printf(\"B\")。有同学以为两行缩进一样，就都属于 if，这不对。C 不看缩进；没有大括号时，if 只管紧接着的一条语句。所以 a 是负数时，A 不输出，但 B 仍然输出。要让两句都受条件控制，就用大括号括起来。平时写程序我建议加上，免得以后插入一句代码时忘了。

            再看嵌套的情况：if (a > 0) 里面写 if (a > 10)，满足就输出 A，然后写 else 输出 B。这个 else 到底属于哪一个 if？它总是跟最近的、还没有 else 的 if 配对，不能靠你把它缩进到哪里来决定。a 等于 5 的时候，外面的条件成立，里面不成立，所以输出 B；a 是负数，外面的条件都不成立，里面的 if 和 else 根本不会执行。想把 else 配给外面那个 if，就给里面的语句明确加大括号。这种程序要沿着条件判断走，不要看排版猜。

            有同学在作业里写 if (a = 3)，以为是在问 a 是不是 3。一个等号是赋值，两个等号才是比较。这里先把 3 放进 a，整个赋值表达式的值也是 3，非零就当作真，所以会进入分支。如果赋的是 0，就不会进入，但 a 也已经被改掉了。编译器可能警告你，不一定报错。看到警告要检查含义，不要因为能运行就认为程序符合本意。

            现在判断 a 在 1 到 5 之间，注意今天这道题要求严格大于 1、严格小于 5，不含两个端点。数学上可以写 1 < a < 5，C 里这样写就不对：先算 1 < a，结果只有 0 或 1，再拿这个结果与 5 比较，当然总为真。应该写 a > 1 && a < 5。两个条件都成立才成立，一个条件满足就行则用 ||，不要跟按位运算的 &、| 混起来。

            最后看 b != 0 && a / b == 3。逻辑与从左到右计算；如果 b 已经是 0，左边为假，整体不可能为真，右边就不算了，这叫短路。把 a / b == 3 写在前面，再判断 b != 0，就不能保护除法。逻辑或也会短路，左边为真时右边不再计算。今天先把这些程序一步一步走清楚，不要只记一个结果。

            关于实验填空，题目要求一行，就不能拆成多行提交；输出格式也要按题目给的来，别自己加提示语。类似的条件判断会考，尤其要分清等号、分支归属和边界。下次课前把这几段代码的执行过程过一遍。现在网站有点打不开了，我刷新一下；大家收拾东西，今天先到这里。
            """;
    private static final String[] RESULTS={
            """
            条件语句与嵌套分支
            if 没有大括号时只控制紧随其后的一条语句，与缩进无关。示例中 if (a > 0) 只控制输出 A，后面的输出 B 不受控制；a 为负数时仍输出 B。要共同控制两句，应使用大括号；新增语句时也应检查控制范围。

            嵌套 if 的 else 与最近的、尚未匹配 else 的 if 配对。外层判断 a > 0，内层判断 a > 10，内层成立输出 A，否则输出 B：a = 5 时输出 B；a 为负数时不进入外层，内层两个分支都不执行。要让 else 属于外层，须用大括号明确内部范围。

            赋值与比较
            a = 3 是赋值，表达式值为 3，在 if 中按真处理，同时改写 a；赋 0 则按假处理。比较应写 a == 3。编译器可能只警告而不报错，能运行不代表语义正确。

            范围判断与逻辑短路
            题目要求 1 < a < 5，不含端点；C 应写 a > 1 && a < 5。直接写 1 < a < 5 会先得到 0 或 1，再与 5 比较，结果恒真。&& 表示同时满足，|| 表示至少一个满足，不应混用按位运算符 &、|。

            b != 0 && a / b == 3 先检查除数。&& 左侧为假时不计算右侧，因此 b = 0 时不会进行除法；颠倒顺序就失去保护。|| 左侧为真时也不计算右侧。

            考核与安排
            实验填空要求一行就须一行提交，输出格式按题面，不自行加提示语。类似条件判断会考，重点是等号、分支归属与边界。下次课前复习上述代码的执行过程。
            """,
            """
            条件语句：先确定控制范围
            C 依据语法而非缩进确定分支。无大括号的 if 只控制下一条语句，因此示例中 a 为负数时不输出 A，仍输出 B。多条语句共同受控时必须用大括号，修改程序时同样要检查范围。

            else 匹配最近的未配对 if。外层 a > 0、内层 a > 10，内层真时输出 A、假时输出 B：a = 5 输出 B；负数不进入外层，两种输出都不执行。改变配对关系需要大括号，调整缩进无效。

            表达式：区分赋值、比较与条件组合
            if (a = 3) 会修改 a，并以赋值结果 3 判真；赋 0 则判假。判断相等使用 ==。编译警告可能提示这种合法但违背本意的写法，应核对语义。

            严格区间 1 < a < 5 应写 a > 1 && a < 5，不能丢掉不含端点的条件。C 的连写比较先算出 0 或 1，再判断是否小于 5，因而恒真。&& 要求两边均真，|| 要求至少一边为真，与按位 &、| 不同。

            短路：把保护条件放在危险运算前
            b != 0 && a / b == 3 从左向右求值；左侧为假，右侧不执行，避免除零。先做除法再判 b 非零无法保护。|| 则在左侧为真时跳过右侧。

            考核与安排
            条件判断会考：注意等号、分支归属和区间边界。实验一行填空、一行提交；输出严格按题面，不加提示语。下次课前复习代码执行过程。
            """,
            """
            分支控制与条件表达式

            1. 先划清语句归属
            无大括号的 if 只控制下一条语句，缩进不改变语义。if (a > 0) 只控制输出 A，之后的 B 仍执行，所以负数也输出 B；控制多句应加大括号。
            嵌套时 else 归最近的未配对 if。外层 a > 0、内层 a > 10：a = 5 走内层 else，输出 B；负数不进外层，A、B 都不输出。改变归属须加大括号，不能只改缩进。

            2. 把数学条件翻译成 C 表达式
            相等用 ==；= 会赋值并产生结果。if (a = 3) 修改 a 且判真，赋 0 则判假；编译器可能只警告，仍须检查程序本意。
            严格区间 (1, 5) 写作 a > 1 && a < 5。1 < a < 5 会先得到 0 或 1，再与 5 比较，恒真。&& 表示同时满足，|| 表示至少一个满足，勿混用按位 &、|。

            3. 用短路保护运算
            b != 0 && a / b == 3：先检查 b；为零时左侧为假，右侧跳过，避免除零。交换顺序会先执行除法，保护失效。|| 在左侧为真时跳过右侧。

            考核与安排
            条件判断会考，重点是等号、分支归属、边界。实验要求一行就一行提交，输出遵循题面、不加提示语；下次课前复习上述代码执行过程。
            """
    };
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);
        LinearLayout root=SettingsPageLayout.content(this);
        SettingsPageLayout.header(this,root,"笔记整理强度");
        section(root,"示例原文",ORIGINAL);
        for(int i=0;i<RESULTS.length;i++)section(root,(i+1)+"档 · "+ClassroomNoteStrength.label(i+1),RESULTS[i]);
        SettingsPageLayout.show(this,root,state==null);
        viewport=(ScrollView)root.getParent();
        if(state!=null)viewport.post(()->viewport.scrollTo(0,state.getInt("scroll_y")));
    }
    private void section(LinearLayout root,String heading,String text){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(16),dp(16),dp(16));UiStyle.card(card);
        TextView title=new TextView(this);title.setText(heading);title.setTextSize(18);UiStyle.title(title);card.addView(title);
        TextView body=new TextView(this);body.setText(text.strip());body.setTextSize(16);body.setTextIsSelectable(true);UiStyle.muted(body);
        body.setLineSpacing(dp(3),1f);UiStyle.addSpaced(card,body,8,0);UiStyle.addSpaced(root,card,0,8);
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("scroll_y",viewport.getScrollY());super.onSaveInstanceState(state);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
