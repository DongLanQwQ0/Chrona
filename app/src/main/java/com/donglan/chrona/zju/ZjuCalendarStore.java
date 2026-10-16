package com.donglan.chrona.zju;

import android.content.Context;

/** Calendar settings contain no identity, session, or school account data. */
public final class ZjuCalendarStore {
    private ZjuCalendarStore() { }
    public static ZjuCalendarConfig load(Context context) {
        String raw=context.getSharedPreferences("zju_calendar",Context.MODE_PRIVATE).getString("config",null);
        return raw==null?ZjuCalendarConfig.ordinary():ZjuCalendarConfig.parse(raw);
    }
    public static void save(Context context,ZjuCalendarConfig config) {
        config.validate();if(!context.getSharedPreferences("zju_calendar",Context.MODE_PRIVATE).edit().putString("config",config.json().toString()).commit())throw new IllegalStateException("无法保存校历");
    }
    public static String exportJson(Context context){return load(context).json().toString();}
    public static void importJson(Context context,String raw){save(context,ZjuCalendarConfig.parse(raw));}
}
