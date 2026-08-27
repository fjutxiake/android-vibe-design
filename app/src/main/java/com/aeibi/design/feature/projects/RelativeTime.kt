package com.aeibi.design.feature.projects

/**
 * 项目时间的相对时间分类。纯数据,不持文案;由 UI 层按语言偏好解析为字符串。
 */
sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class MinutesAgo(val count: Long) : RelativeTime

    data class HoursAgo(val count: Long) : RelativeTime

    data class DaysAgo(val count: Long) : RelativeTime

    data class AbsoluteDate(val epochMillis: Long) : RelativeTime
}

/** 将时间戳归类为相对时间档位;日期档的格式化由 UI 层按当前语言完成。 */
fun relativeTimeOf(epochMillis: Long, now: Long = System.currentTimeMillis()): RelativeTime {
    val diff = now - epochMillis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> RelativeTime.JustNow
        diff < hour -> RelativeTime.MinutesAgo(diff / minute)
        diff < day -> RelativeTime.HoursAgo(diff / hour)
        diff < 7 * day -> RelativeTime.DaysAgo(diff / day)
        else -> RelativeTime.AbsoluteDate(epochMillis)
    }
}
