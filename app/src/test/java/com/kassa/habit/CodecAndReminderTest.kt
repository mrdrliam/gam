package com.kassa.habit

import com.kassa.habit.data.Codec
import com.kassa.habit.data.DayNote
import com.kassa.habit.data.DaysChange
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Review
import com.kassa.habit.data.Settings
import com.kassa.habit.notify.Reminder
import com.kassa.habit.notify.ReminderPlan
import com.kassa.habit.ui.AppLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecAndReminderTest {

    @Test
    fun everythingSurvivesARoundTrip() {
        val habit = bad().copy(
            why = "سلامتی", plan = "اگر …", replacement = "آب", replacementId = "g", weeklyLimit = 0, remindAt = 0,
            schedule = listOf(DaysChange(SAT, setOf(0, 6)), DaysChange(day(3), setOf(1))), archivedOn = day(9),
        )
        val original = data(
            good(), habit,
            logs = listOf(
                log("g", 0, LogType.DONE),
                log("q", 1, LogType.SLIP, setOf("خستگی", "عجله"), minute = -1, plan = "نفس")
                    .copy(mood = 2, place = "خانه", people = "تنها", note = "\"نقل قول\"\nخط دوم"),
            ),
            days = mapOf(SAT to DayNote(SAT, closed = false, mood = 4, win = "برد", note = "n")),
        ).copy(
            reviews = mapOf(SAT to Review(SAT, "a", "b", "c", 5, 123)),
            settings = Settings(
                name = "علی", theme = 2, dayStartHour = 0, notify = false, eveningAt = null, reviewAt = 0,
                appLock = true, lockGraceSec = 90, onboarded = true, seenBadges = setOf("first_log"),
            ),
        )
        assertEquals(original, Codec.decode(Codec.encode(original)))
    }

    @Test
    fun foreignOrDamagedFilesAreRefused() {
        assertThrows(Exception::class.java) { Codec.decode("not json") }
        assertThrows(Exception::class.java) { Codec.decode("{\"habits\":[],\"logs\":[]}") }
        assertThrows(Exception::class.java) { Codec.decode("{\"app\":\"habit\",\"v\":99,\"habits\":[],\"logs\":[]}") }
        assertThrows(Exception::class.java) { Codec.decode(Codec.encode(data(good())).dropLast(10)) }
    }

    @Test
    fun logsWithoutAHabitAreDropped() {
        val d = data(good(), logs = listOf(log("gone", 0, LogType.DONE)))
        assertTrue(Codec.decode(Codec.encode(d)).logs.isEmpty())
    }

    @Test
    fun nextAlarmIsTheEarliestPlannedTime() {
        val d = data(good().copy(remindAt = 18 * 60))
        assertEquals(day(0).atTime(18, 0), ReminderPlan.next(d, day(0).atTime(9, 0)))
        // after the habit comes the evening check-in
        assertEquals(day(0).atTime(21, 30), ReminderPlan.next(d, day(0).atTime(18, 0)))
        assertEquals(day(1).atTime(18, 0), ReminderPlan.next(d, day(0).atTime(21, 30)))
    }

    @Test
    fun nothingIsPlannedWhenOffOrEmpty() {
        assertNull(ReminderPlan.next(data(), day(0).atTime(9, 0)))
        val off = data(good().copy(remindAt = 600)).copy(settings = Settings(notify = false))
        assertNull(ReminderPlan.next(off, day(0).atTime(9, 0)))
    }

    @Test
    fun finishedThingsAreNotReminded() {
        val h = good().copy(remindAt = 18 * 60)
        val from = day(0).atTime(17, 59)
        val until = day(0).atTime(22, 0)
        assertEquals(2, ReminderPlan.due(data(h), from, until).size)
        val handled = data(h, logs = listOf(log("g", 0, LogType.DONE)), days = closed(0))
        assertTrue(ReminderPlan.due(handled, from, until).isEmpty())
    }

    @Test
    fun afterMidnightReminderBelongsToTheDayBefore() {
        val h = good().copy(remindAt = 60, schedule = listOf(DaysChange(SAT, setOf(0)))) // Saturday at 01:00
        val d = data(h).copy(settings = Settings(eveningAt = null, reviewAt = null))
        // Saturday's reminder rings in the first hour of Sunday
        val due = ReminderPlan.due(d, day(1).atTime(0, 30), day(1).atTime(1, 5)).single() as Reminder.ForHabit
        assertEquals(day(0), due.date)
    }

    @Test
    fun reviewIsRemindedOnFridayUntilWritten() {
        val d = data(good()).copy(settings = Settings(eveningAt = null))
        assertEquals(day(6).atTime(20, 0), ReminderPlan.next(d, day(0).atTime(9, 0)))
        val from = day(6).atTime(19, 59)
        val until = day(6).atTime(20, 0)
        assertEquals(SAT, (ReminderPlan.due(d, from, until).single() as Reminder.WeeklyReview).week)
        val written = d.copy(reviews = mapOf(SAT to Review(SAT, change = "x")))
        assertTrue(ReminderPlan.due(written, from, until).isEmpty())
    }

    @Test
    fun lockRule() {
        assertFalse(AppLock.shouldLock(false, false, 0, 60))
        assertTrue(AppLock.shouldLock(true, false, 0, 60))
        assertFalse(AppLock.shouldLock(true, true, 59_000, 60))
        assertTrue(AppLock.shouldLock(true, true, 61_000, 60))
    }
}
