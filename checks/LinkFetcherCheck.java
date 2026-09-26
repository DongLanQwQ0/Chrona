package com.donglan.chrona.web;

public final class LinkFetcherCheck {
    public static void main(String[] args) {
        String text = LinkFetcher.toText("<html><title>A &amp; B</title><body>"
                + "One &#x4E2D; &quot;two&quot; <script>discard</script> end &unknown;"
                + "</body></html>");
        if (!text.contains("A & B") || !text.contains("One \u4E2D \"two\" end")
                || text.contains("discard") || text.contains("&unknown;")) {
            throw new AssertionError(text);
        }
        System.out.println("LinkFetcherCheck passed");
    }
}
