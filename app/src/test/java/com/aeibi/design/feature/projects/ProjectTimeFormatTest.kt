package com.aeibi.design.feature.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTimeFormatTest {

    private val now = 1_000_000_000_000L

    @Test
    fun justNow() = assertEquals(RelativeTime.JustNow, relativeTimeOf(now - 30_000L, now))

    @Test
    fun minutesAgo() = assertEquals(RelativeTime.MinutesAgo(5), relativeTimeOf(now - 5 * 60_000L, now))

    @Test
    fun hoursAgo() = assertEquals(RelativeTime.HoursAgo(3), relativeTimeOf(now - 3 * 3_600_000L, now))

    @Test
    fun daysAgo() = assertEquals(RelativeTime.DaysAgo(2), relativeTimeOf(now - 2 * 86_400_000L, now))

    @Test
    fun olderThanAWeek_fallsBackToAbsoluteDate() = assertEquals(
        RelativeTime.AbsoluteDate(now - 8 * 86_400_000L),
        relativeTimeOf(now - 8 * 86_400_000L, now)
    )
}
