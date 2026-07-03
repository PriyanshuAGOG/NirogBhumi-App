package com.nirogbhumi.app.update

import android.content.Context
import android.content.SharedPreferences

/** Local-only update-system state: channel choice, last check time, dismissed version. */
object UpdatePrefs {
    private const val PREFS = "nirog_updates"
    private const val KEY_CHANNEL = "channel"
    private const val KEY_LAST_CHECK_AT = "last_check_at"
    private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    private const val KEY_PENDING_APK_PATH = "pending_apk_path"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun channel(context: Context): UpdateChannelOption =
        UpdateChannelOption.fromId(prefs(context).getString(KEY_CHANNEL, null))

    fun setChannel(context: Context, channel: UpdateChannelOption) {
        prefs(context).edit().putString(KEY_CHANNEL, channel.id).apply()
    }

    fun lastCheckAtMillis(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK_AT, 0L)

    fun recordCheckNow(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
    }

    // "Later" dismisses a specific version, not updates forever - a newer
    // release past this one still prompts normally, and force-updates never
    // consult this at all.
    fun dismissedVersionCode(context: Context): Int = prefs(context).getInt(KEY_DISMISSED_VERSION_CODE, 0)

    fun dismissVersion(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_DISMISSED_VERSION_CODE, versionCode).apply()
    }

    fun pendingApkPath(context: Context): String? = prefs(context).getString(KEY_PENDING_APK_PATH, null)

    fun setPendingApkPath(context: Context, path: String?) {
        prefs(context).edit().putString(KEY_PENDING_APK_PATH, path).apply()
    }
}
