package com.kassa.habit

import com.kassa.habit.data.AllDays
import com.kassa.habit.data.Badges
import com.kassa.habit.data.Codec
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.DaysChange
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Log
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Points
import com.kassa.habit.data.Templates
import com.kassa.habit.data.TimeBand
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.habitStreak
import com.kassa.habit.data.habitStrength
import com.kassa.habit.data.insightsFor
import com.kassa.habit.data.replacedByStatus
import com.kassa.habit.data.timesOn
import com.kassa.habit.data.weekReport
import com.kassa.habit.notify.ReminderPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Habits that happen several times a day: glasses of water, pauses before speaking. */
class CountedHabitTest {

    private fun water(target: Int = 8): Habit = good().copy(schedule = listOf(DaysChange(SAT, AllDays, target)))

    private fun times(offset: Int, n: Int, minute: Int = 14 * 60): List<Log> =
        List(n) { log("g", offset, LogType.DONE, minute = minute) }

    @Test
    fun everyTimeIsCountedAndTheTargetCompletesTheDay() {
        val h = water(3)
        fun status(n: Int) = buildStatus(data(h, logs = times(0, n)), h, day(0), day(0))
        assertEquals(DayStatus.PENDING, status(0))
        assertEquals(DayStatus.PARTIAL, status(1))
        assertEquals(DayStatus.DONE, status(3))
        assertEquals(DayStatus.DONE, status(5))
        assertEquals(5, timesOn(data(h, logs = times(0, 5)), "g", day(0)))
    }

    @Test
    fun oneMoreTimeNeverReplacesEarlierOnes() {
        val dayLogs = times(0, 3) + log("g", 0, LogType.SKIP)
        assertEquals(listOf(LogType.SKIP), replacedByStatus(dayLogs, counted = true).map { it.type })
        // a once-a-day habit still has a single status
        assertEquals(4, replacedByStatus(dayLogs, counted = false).size)
    }

    @Test
    fun pointsGrowWithTheCountAndStopAtTheTarget() {
        val h = water(8)
        fun points(n: Int) = Points.ofDay(data(h, logs = times(0, n)), day(0))
        assertEquals(0, points(0))
        assertEquals(1, points(1))
        assertEquals(5, points(4))
        assertEquals(10, points(8))
        assertEquals(10, points(30))
        assertEquals(1, Points.ofDay(data(water(50), logs = times(0, 1)), day(0)))
    }

    @Test
    fun oneTimeKeepsTheStreak() {
        val h = water()
        val d = data(h, logs = times(0, 8) + times(1, 1) + times(2, 2))
        assertEquals(3, habitStreak(d, h, day(3)).current)
    }

    @Test
    fun countingTodayNeverLowersTheStrength() {
        val h = water()
        val past = (0..9).flatMap { times(it, 8) }
        val before = habitStrength(data(h, logs = past), h, day(10))
        var last = before
        for (n in 1..8) {
            val now = habitStrength(data(h, logs = past + times(10, n)), h, day(10))
            assertTrue("after $n", now >= last)
            last = now
        }
        assertTrue(last > before)
        // once the day is over, a weak count weighs as a weak day
        assertTrue(habitStrength(data(h, logs = past + times(10, 1)), h, day(11)) < before)
    }

    @Test
    fun weekWaitsForAnUnfinishedTodayAndShowsCounts() {
        val h = water(4)
        val d = data(h, logs = times(0, 4) + times(1, 2) + times(2, 1))
        val open = weekReport(d, SAT, day(2)).build.single()
        assertEquals(75, open.percent) // (1 + 0.5) / 2
        assertEquals(7, open.total)
        assertEquals(7.0 / 3, open.average, 0.001)
        assertTrue(open.counted)
        assertEquals(listOf(4, 2, 1, 0, 0, 0, 0), open.times)
        assertEquals(TimeBand.NOON, open.usualTime)
        // closing the day makes today part of the result
        val closedDay = weekReport(d.copy(days = closed(2)), SAT, day(2)).build.single()
        assertEquals(58, closedDay.percent) // (1 + 0.5 + 0.25) / 3
    }

    @Test
    fun usualTimeShowsAsAPattern() {
        val h = water()
        val d = data(h, logs = times(0, 6, minute = 9 * 60))
        assertTrue(insightsFor(d, h, day(1)).any { TimeBand.MORNING.label in it.text })
    }

    @Test
    fun changingTheTargetKeepsHistory() {
        val h = good().copy(schedule = listOf(DaysChange(SAT, AllDays), DaysChange(day(2), AllDays, 3)))
        val d = data(h, logs = times(0, 1) + times(2, 1))
        assertEquals(DayStatus.DONE, buildStatus(d, h, day(0), day(3)))
        assertEquals(DayStatus.PARTIAL, buildStatus(d, h, day(2), day(3)))
        assertEquals(3, h.target)
        assertEquals(h, Codec.decode(Codec.encode(d)).habits.single())
        assertEquals(3, h.archive(day(3)).restore(day(8)).targetOn(day(8)))
    }

    @Test
    fun badgesCountCompletedDaysNotTaps() {
        val h = water(8)
        val d = data(h, logs = times(0, 8) + times(1, 8) + times(2, 7))
        assertEquals(2, Badges.all(d, day(3)).first { it.id == "done_25" }.progress)
    }

    @Test
    fun reminderStaysUntilTheTargetIsReached() {
        val h = water(2).copy(remindAt = 18 * 60)
        val from = day(0).atTime(17, 59)
        val until = day(0).atTime(18, 0)
        assertEquals(1, ReminderPlan.due(data(h, logs = times(0, 1)), from, until).size)
        assertTrue(ReminderPlan.due(data(h, logs = times(0, 2)), from, until).isEmpty())
    }

    @Test
    fun waterAndPauseStartAsCountedHabits() {
        assertEquals(8, Templates.byKey("water")!!.target)
        assertEquals(3, Templates.byKey("pause")!!.target)
        assertFalse(Templates.byKey("sleep")!!.target > 1)
    }
}
