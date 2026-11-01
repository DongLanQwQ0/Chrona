package com.donglan.chrona.classroom;

import org.json.JSONException;
import org.json.JSONObject;

/** Shared syntax parsing only; never repairs, reshapes or completes a reply. */
public final class ClassroomJsonObject {
    private ClassroomJsonObject() { }
    public static JSONObject parseObject(String value)throws JSONException {
        String text=value.trim();
        if(text.startsWith("```")){int start=text.indexOf('\n'),end=text.lastIndexOf("```");if(start>=0&&end>start)text=text.substring(start+1,end).trim();}
        return new JSONObject(text);
    }
}
