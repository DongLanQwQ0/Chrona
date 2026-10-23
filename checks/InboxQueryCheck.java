import com.donglan.chrona.data.InboxQuery;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class InboxQueryCheck {
    private static int searchChecks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        searchChecks++;
    }
    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static void plan(String name, int status, String category, boolean oldest) {
        InboxQuery query = new InboxQuery(status, category, "");
        String[] args = new String[query.arguments.length];
        for (int i = 0; i < args.length; i++) args[i] = encode(query.arguments[i]);
        System.out.println(name + "\t" + encode(query.selection) + "\t"
                + String.join(",", args) + "\t" + encode(query.orderBy(oldest)));
    }
    public static void main(String[] args) {
        check(new InboxQuery(0, null, " ÄBC ").matchesSearch("äBc", null), "Unicode case-insensitive search");
        check(new InboxQuery(0, null, "100%_\\").matchesSearch(null, "100%_\\ archive"), "Literal wildcard search/link body");
        check(!new InboxQuery(0, null, "100%_").matchesSearch("100hello", null), "Wildcards must not expand");
        check(!new InboxQuery(0, null, "query").matchesSearch(null, null), "Null text must not match");
        check(new InboxQuery(0, null, null).matchesSearch(null, null), "Null query matches all");
        check(new InboxQuery(0, null, " \t\n\u3000\u00a0 ").matchesSearch(null, null), "Unicode whitespace query matches all");
        check(new InboxQuery(0, null, " ＡＢＣ　１２ ").keyword.equals("abc 12"), "NFKC query and canonical whitespace");
        check(new InboxQuery(0, null, "ＡＢＣ １２").matchesSearch("abc", "１２"), "Fullwidth query and cross-field terms");
        check(new InboxQuery(0, null, "abc 12").matchesSearch("ＡＢＣ", "１２"), "Fullwidth fields normalize");
        check(new InboxQuery(0, null, "数学 作业").matchesSearch("数学课通知", null, "提交作业", null), "Chinese cross-field AND");
        check(new InboxQuery(0, null, "数学 作业").matchesSearch(null, "数学", null, "作业"), "Link body and description participate");
        check(new InboxQuery(0, null, "math report").matchesSearch(null, null, "REPORT Math", null), "Term order is independent");
        check(new InboxQuery(0, null, "math report").matchesSearch("math\nsubmit report", null), "Terms may have intervening text");
        check(new InboxQuery(0, null, "math math").matchesSearch("math", null), "Duplicate terms require no duplicate text");
        check(new InboxQuery(0, null, "数学\t作业\n周五").matchesSearch("数学", "周五", "作业", null), "Tabs and newlines split terms");
        check(!new InboxQuery(0, null, "数学 作业").matchesSearch("数学", null, "考试", null), "All terms required");
        check(!new InboxQuery(0, null, "abc").matchesSearch("a", "bc", null, null), "A single term cannot span fields");
        check(new InboxQuery(0, null, "% _ \\").matchesSearch("100%", "_", "\\", null), "Literal special characters across fields");
        check(!new InboxQuery(0, null, "% _ \\").matchesSearch("100%", "_", null, null), "Backslash is a required literal term");
        check(new InboxQuery(0, null, "é").matchesSearch("e\u0301", null), "Canonical accents normalize");
        System.err.println("Inbox search: " + searchChecks + " Java checks passed");
        plan("all", 0, null, false);
        plan("oldest", 0, null, true);
        plan("pending", 1, null, false);
        plan("processing", 2, null, false);
        plan("failed", 3, null, false);
        plan("published_event", 4, "event", false);
        plan("published_task", 4, "task", false);
        plan("event", 0, "event", false);
    }
}
