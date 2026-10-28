package com.kbi.appcleaner

import android.content.Context

/** 휴대폰 안에만 저장되는 설정 (외부로 전송하지 않음) */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kbi_app_cleaner", Context.MODE_PRIVATE)

    /** "내 앱"으로 지정해 보호하는 앱들 */
    var protectedApps: Set<String>
        get() = sp.getStringSet("protected", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("protected", v).apply()

    /** "폰 빌려주기 전 기억하기" 때의 앱 목록 */
    var snapshotApps: Set<String>
        get() = sp.getStringSet("snapshot", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("snapshot", v).apply()

    var snapshotTime: Long
        get() = sp.getLong("snapshot_time", 0L)
        set(v) = sp.edit().putLong("snapshot_time", v).apply()

    val hasSnapshot get() = snapshotTime > 0L

    /** 새로 깔린 앱 기간: 0 = 기억한 시점 이후, 그 외 = 일 수 */
    var newPeriodDays: Int
        get() = sp.getInt("new_period", 7)
        set(v) = sp.edit().putInt("new_period", v).apply()
}
