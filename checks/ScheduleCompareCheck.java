package com.donglan.chrona;

/** Tests the production navigation boundary without creating a WebView or using a device. */
public final class ScheduleCompareCheck {
    private static int checks;
    private static void check(String url, boolean expected) {
        if (ScheduleCompareActivity.isSite(url) != expected) throw new AssertionError(url);
        checks++;
    }
    public static void main(String[] arguments) {
        check(ScheduleCompareActivity.SITE, true);
        check("https://111.228.3.50/tongge", true);
        check("https://111.228.3.50:443/tongge/?invite=abc#local", true);
        check("https://111.228.3.50/tongge/help.html", true);
        check("http://111.228.3.50/tongge/", false);
        check("https://111.228.3.50:444/tongge/", false);
        check("https://111.228.3.50.attacker.example/tongge/", false);
        check("https://user@111.228.3.50/tongge/", false);
        check("https://111.228.3.50/tongge-other/", false);
        check("https://111.228.3.50/tongge/../other/", false);
        check("javascript:alert(1)", false);
        check("file:///tongge/", false);
        check("https://111.228.3.50/tongge/ bad", false);
        check(null, false);
        System.out.println("Schedule compare navigation checks passed: " + checks);
    }
}
