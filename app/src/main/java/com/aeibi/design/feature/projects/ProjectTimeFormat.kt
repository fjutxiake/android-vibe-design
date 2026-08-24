package com.aeibi.design.feature.projects

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRelativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - epochMillis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 7 * day -> "${diff / day} 天前"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(epochMillis))
    }
}
