import com.donglan.chrona.data.InboxQuery;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class InboxQueryCheck {
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
        if (!new InboxQuery(0, null, " ÄBC ").matchesSearch("äBc", null))
            throw new AssertionError("Unicode case-insensitive search");
        if (!new InboxQuery(0, null, "100%_\\").matchesSearch(null, "100%_\\ archive"))
            throw new AssertionError("Literal wildcard search/link body");
        if (new InboxQuery(0, null, "100%_").matchesSearch("100hello", null))
            throw new AssertionError("Wildcards must not expand");
        if (new InboxQuery(0, null, "query").matchesSearch(null, null))
            throw new AssertionError("Null text must not match");
        plan("all", 0, null, false);
        plan("oldest", 0, null, true);
        plan("pending", 1, null, false);
        plan("processing", 2, null, false);
        plan("failed", 3, null, false);
        plan("published_event", 4, "event", false);
        plan("published_note", 4, "note", false);
        plan("note", 0, "note", false);
    }
}
