package com.donglan.chrona.classroom;

import org.json.JSONException;
import org.json.JSONObject;

/** Shared syntax parsing; preserve literal backslashes, never reshape or complete a reply. */
public final class ClassroomJsonObject {
    private ClassroomJsonObject() { }
    public static JSONObject parseObject(String value)throws JSONException {
        String text=value.trim();
        if(text.startsWith("```")){int start=text.indexOf('\n'),end=text.lastIndexOf("```");if(start>=0&&end>start)text=text.substring(start+1,end).trim();}
        return new JSONObject(preserveUnknownEscapes(text));
    }
    /** Android's permissive JSON parser otherwise silently discards \ in unknown escapes. */
    private static String preserveUnknownEscapes(String text){
        StringBuilder out=new StringBuilder(text.length());boolean quoted=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='"'){quoted=!quoted;out.append(c);continue;}
            if(quoted&&c=='\\'&&i+1<text.length()){
                char next=text.charAt(++i);
                if("\"\\/bfnrtu".indexOf(next)<0)out.append('\\');
                out.append('\\').append(next);
            }else out.append(c);
        }
        return out.toString();
    }
}
