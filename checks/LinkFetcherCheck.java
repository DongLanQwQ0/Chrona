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
        LinkFetcher.FetchResult blocked = LinkFetcher.extractPage("https://mp.weixin.qq.com/s/example",
                "<html><body>环境异常，请完成验证</body></html>");
        if (!blocked.text.isEmpty() || blocked.warning == null) throw new AssertionError("verification page accepted");
        if (!LinkFetcher.extractPage("https://mp.weixin.qq.com/s/example",
                "<title>环境异常</title><div id='js_content'></div>").text.isEmpty())
            throw new AssertionError("empty article accepted");
        if (!LinkFetcher.extractPage("https://mp.weixin.qq.com/mp/wappoc_appmsgcaptcha",
                "<div id='js_content'>请验证</div>").text.isEmpty())
            throw new AssertionError("captcha URL accepted");
        if (!LinkFetcher.extractPage("https://mp.weixin.qq.com/s/example",
                "<div data-id='js_content'>错误元素</div>").text.isEmpty())
            throw new AssertionError("data-id accepted");
        LinkFetcher.FetchResult article = LinkFetcher.extractPage("https://mp.weixin.qq.com/s/example",
                "<title>会议通知</title><div id='js_content'><p>明天18点开会</p><div>带上电脑</div></div><div>关注公众号</div>");
        if (!article.text.contains("明天18点开会") || !article.text.contains("带上电脑")
                || article.text.contains("关注公众号") || article.warning != null)
            throw new AssertionError(article.text);
        LinkFetcher.FetchResult normal = LinkFetcher.extractPage("https://example.com/news",
                "<p>这里讨论环境异常，但这是正常文章</p>");
        if (normal.text.isEmpty()) throw new AssertionError("false positive outside WeChat");
        if (!LinkFetcher.onlyLinks("https://mp.weixin.qq.com/s/example\n")
                || LinkFetcher.onlyLinks("明天开会 https://example.com/news"))
            throw new AssertionError("link-only detection");
        System.out.println("LinkFetcherCheck passed");
    }
}
