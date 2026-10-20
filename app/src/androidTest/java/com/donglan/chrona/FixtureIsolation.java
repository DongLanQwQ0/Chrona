package com.donglan.chrona;

import android.content.Context;
import android.os.Build;

/** Write fixtures may only run in the disposable v33 emulator application. */
final class FixtureIsolation {
    private FixtureIsolation() { }

    static void require(Context context) {
        if (!"com.donglan.chrona.followup.validation.v33".equals(context.getPackageName())
                || !("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE))
                || !(Build.MODEL.startsWith("sdk_gphone") || Build.MODEL.startsWith("Android SDK built for"))
                || !(Build.FINGERPRINT.contains("google/") || Build.FINGERPRINT.contains("generic/")
                || Build.FINGERPRINT.startsWith("Android/sdk_phone64_")))
            throw new IllegalStateException("Write fixture requires the isolated v33 official emulator");
        ZjuSettingsStore school = new ZjuSettingsStore(context);
        if (school.enabled() || !((!school.configured() && school.account().isEmpty())
                || "00000000-0000-0000-0000-000000000036".equals(school.account())))
            throw new IllegalStateException("Write fixture refuses a real school account");
        WebDavSettingsStore sync = new WebDavSettingsStore(context);
        if (sync.configured() || sync.automatic())
            throw new IllegalStateException("Write fixture refuses external sync");
        if (LanAccessService.running() || LanAccessService.starting())
            throw new IllegalStateException("Write fixture refuses an existing LAN session");
    }
}
