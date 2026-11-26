package com.kassa.habit

import com.kassa.habit.data.AllDays
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.DaysChange
import com.kassa.habit.data.LogType
import com.kassa.habit.data.QuitStatus
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.cleanRun
import com.kassa.habit.data.habitStreak
import com.kassa.habit.data.habitStrength
import com.kassa.habit.data.logicalDate
import com.kassa.habit.data.presenceStreak
import com.kassa.habit.data.quitStatus
import com.kassa.habit.data.streakOf
import com.kassa.habit.data.weekStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CalcTest {

    @Test
    fun lateNightBelongsToTheEveningBefore() {
        assertEquals(day(0), logicalDate(LocalDateTime.of(2026, 11, 22, 1, 30), 3))
        assertEquals(day(1), logicalDate(LocalDateTime.of(2026, 11, 22, 3, 0), 3))
        assertEquals(day(1), logicalDate(LocalDateTime.of(2026, 11, 22, 0, 10), 0))
    }

    @Test
    fun weeksStartOnSaturday() {
        for (i in 0..6) assertEquals(SAT, weekStart(day(i)))
        assertEquals(day(7), weekStart(day(7)))
    }

    @Test
    fun oneMissKeepsTheStreakTwoBreakIt() {
        assertEquals(3, streakOf(listOf(true, true, false, true)).current)
        assertTrue(streakOf(listOf(true, true, false)).atRisk)
        val broken = streakOf(listOf(true, true, false, false, true))
        assertEquals(1, broken.current)
        assertEquals(2, broken.best)
        assertFalse(streakOf(listOf(false, false)).atRisk)
    }

    @Test
    fun unfinishedTodayIsNotAMiss() {
        val h = good()
        val d = data(h, logs = listOf(log("g", 0, LogType.DONE), log("g", 1, LogType.TINY)))
        assertEquals(DayStatus.PENDING, buildStatus(d, h, day(2), day(2)))
        assertEquals(2, habitStreak(d, h, day(2)).current)
        assertFalse(habitStreak(d, h, day(2)).atRisk)
        // the next day it has become a miss
        assertEquals(DayStatus.MISSED, buildStatus(d, h, day(2), day(3)))
        assertTrue(habitStreak(d, h, day(3)).atRisk)
    }

    @Test
    fun unplannedDaysDoNotCount() {
        // Saturdays and Mondays only
        val h = good().copy(schedule = listOf(DaysChange(SAT, setOf(0, 2))))
        val d = data(h, logs = listOf(log("g", 0, LogType.DONE), log("g", 2, LogType.DONE)))
        assertEquals(DayStatus.OFF, buildStatus(d, h, day(1), day(5)))
        assertEquals(2, habitStreak(d, h, day(5)).current)
        assertFalse(habitStreak(d, h, day(5)).atRisk)
    }

    @Test
    fun changingTheDaysDoesNotRewriteThePast() {
        val h = good().copy(schedule = listOf(DaysChange(SAT, AllDays), DaysChange(day(7), setOf(0))))
        assertTrue(h.scheduledOn(day(3)))
        assertFalse(h.scheduledOn(day(10)))
        assertTrue(h.scheduledOn(day(14)))
    }

    @Test
    fun strengthGrowsAndTinyCountsForLess() {
        val h = good()
        fun strength(type: LogType) = habitStrength(data(h, logs = (0..9).map { log("g", it, type) }), h, day(9))
        assertTrue(strength(LogType.DONE) in 55..58) // 1 - 0.92^10
        assertTrue(strength(LogType.TINY) in 38..41)
        assertEquals(0, strength(LogType.SKIP))
    }

    @Test
    fun silenceIsNotAClean_day() {
        val h = bad()
        val d = data(h, logs = listOf(log("q", 1, LogType.RESIST), log("q", 2, LogType.SLIP)), days = closed(0, 2))
        assertEquals(QuitStatus.CLEAN, quitStatus(d, h, day(0), day(4)))
        assertEquals(QuitStatus.RESISTED, quitStatus(d, h, day(1), day(4)))
        assertEquals(QuitStatus.SLIPPED, quitStatus(d, h, day(2), day(4)))
        assertEquals(QuitStatus.UNKNOWN, quitStatus(d, h, day(3), day(4)))
        assertEquals(QuitStatus.OFF, quitStatus(d, h, day(-1), day(4)))
    }

    @Test
    fun cleanRunSurvivesUncheckedDaysButNotASlip() {
        val h = bad()
        val d = data(h, logs = listOf(log("q", 1, LogType.SLIP)), days = closed(0, 2, 4, 5))
        val run = cleanRun(d, h, day(6))
        assertEquals(3, run.current)
        assertEquals(3, run.best)
    }

    @Test
    fun presenceCountsLogsAndClosedDays() {
        val d = data(good(), logs = listOf(log("g", 0, LogType.DONE)), days = closed(1, 3))
        assertEquals(3, presenceStreak(d, day(4)).current) // today is still open: not a miss
        assertEquals(0, presenceStreak(d, day(6)).current)
    }

    @Test
    fun timeInTheArchiveIsAPauseNotAMiss() {
        val archived = good().archive(day(1))
        assertTrue(archived.activeOn(day(1))) // today still counts
        assertFalse(archived.listedOn(day(1), day(1)))
        assertTrue(archived.listedOn(day(0), day(1)))
        val back = archived.restore(day(10))
        assertFalse(back.archived)
        assertFalse(back.activeOn(day(5)))
        assertTrue(back.scheduledOn(day(10)))
        val d = data(back, logs = listOf(log("g", 0, LogType.DONE), log("g", 1, LogType.DONE), log("g", 10, LogType.DONE)))
        assertEquals(3, habitStreak(d, back, day(10)).current)
        // restoring on the same day leaves no trace
        assertEquals(good(), good().archive(day(1)).restore(day(1)))
    }

    @Test
    fun archivedHabitStopsCounting() {
        val h = good().copy(archivedOn = day(3))
        assertTrue(h.scheduledOn(day(2)))
        assertFalse(h.scheduledOn(day(3)))
        assertEquals(DayStatus.OFF, buildStatus(data(h), h, day(5), day(6)))
    }
}
