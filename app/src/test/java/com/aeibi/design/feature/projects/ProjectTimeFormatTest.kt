package com.aeibi.design.feature.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTimeFormatTest {

    private val now = 1_000_000_000_000L

    @Test
    fun justNow() = assertEquals("刚刚", formatRelativeTime(now - 30_000L, now))

    @Test
    fun minutesAgo() = assertEquals("5 分钟前", formatRelativeTime(now - 5 * 60_000L, now))

    @Test
    fun hoursAgo() = assertEquals("3 小时前", formatRelativeTime(now - 3 * 3_600_000L, now))

    @Test
    fun daysAgo() = assertEquals("2 天前", formatRelativeTime(now - 2 * 86_400_000L, now))
}
