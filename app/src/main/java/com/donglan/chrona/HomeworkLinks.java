package com.donglan.chrona;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds per-assignment destinations only from complete, trusted school activity URLs. */
final class HomeworkLinks {
    static final HomeworkLinks NONE = new HomeworkLinks(null, null);
    final String mobile, desktop;
    private HomeworkLinks(String mobile, String desktop) { this.mobile=mobile; this.desktop=desktop; }
    private static final Pattern URL = Pattern.compile("https://(?:courses|mcourses)\\.zju\\.edu\\.cn/[^\\s<>]+" );
    private static final Pattern DESKTOP_PATH = Pattern.compile("/course/([0-9]+)/learning-activity(?:/full-screen)?");
    private static final Pattern MOBILE_PATH = Pattern.compile("/course/([0-9]+)/homework/([0-9]+)");
    private static final Pattern ACTIVITY = Pattern.compile("(?:^|[?&])activityId=([0-9]+)(?:&|$)");

    static HomeworkLinks fromText(String text) {
        Matcher matcher=URL.matcher(text==null?"":text);
        while(matcher.find()) { HomeworkLinks links=fromUrl(matcher.group(),null); if(links.mobile!=null)return links; }
        return NONE;
    }
    static HomeworkLinks fromUrl(String url, String expectedCourse) {
        if(url==null)return NONE;
        final URI uri;
        try { uri=URI.create(url); } catch(IllegalArgumentException invalid){return NONE;}
        if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getPort()!=-1)return NONE;
        String course=null,activity=null;
        Matcher path=DESKTOP_PATH.matcher(uri.getPath()==null?"":uri.getPath());
        if("courses.zju.edu.cn".equals(uri.getHost()) && path.matches()) {
            course=path.group(1);
            String fragment=uri.getFragment();
            if(fragment!=null && fragment.matches("/[0-9]+"))activity=fragment.substring(1);
            else if(fragment!=null) {
                Matcher value=ACTIVITY.matcher(fragment);
                if(value.find())activity=value.group(1);
            }
        } else if("mcourses.zju.edu.cn".equals(uri.getHost())) {
            path=MOBILE_PATH.matcher(uri.getPath()==null?"":uri.getPath());
            if(path.matches()){course=path.group(1);activity=path.group(2);}
        }
        if(course==null || activity==null || (expectedCourse!=null && !expectedCourse.equals(course)))return NONE;
        return new HomeworkLinks("https://mcourses.zju.edu.cn/course/"+course+"/homework/"+activity,
                "https://courses.zju.edu.cn/course/"+course+"/learning-activity#/"+activity);
    }
}
