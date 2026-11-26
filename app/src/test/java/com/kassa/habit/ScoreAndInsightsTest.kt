package com.kassa.habit

import com.kassa.habit.data.Badges
import com.kassa.habit.data.Coach
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Levels
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Points
import com.kassa.habit.data.Review
import com.kassa.habit.data.Templates
import com.kassa.habit.data.TimeBand
import com.kassa.habit.data.insightsFor
import com.kassa.habit.data.weekReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreAndInsightsTest {

    @Test
    fun dayPoints() {
        val d = data(
            good(), bad(),
            logs = listOf(log("g", 0, LogType.DONE), log("q", 0, LogType.RESIST), log("q", 1, LogType.SLIP, setOf("خستگی"))),
            days = closed(0, 1),
        )
        // closed + done + resist + clean
        assertEquals(5 + 10 + 15 + 8, Points.ofDay(d, day(0)))
        // closed + honest slip with a reason, no clean bonus
        assertEquals(5 + 2 + 2, Points.ofDay(d, day(1)))
    }

    @Test
    fun repeatedTapsAreCapped() {
        val d = data(bad(), logs = (1..20).map { log("q", 0, LogType.RESIST) } + (1..20).map { log("q", 0, LogType.SLIP) })
        assertEquals(5 * 15 + 2, Points.ofDay(d, day(0)))
    }

    @Test
    fun slippingNeverBeatsStayingClean() {
        val clean = data(bad(), days = closed(0))
        val slipped = data(bad(), logs = (1..3).map { log("q", 0, LogType.SLIP, setOf("استرس")) }, days = closed(0))
        assertTrue(Points.ofDay(slipped, day(0)) <= Points.ofDay(clean, day(0)))
    }

    @Test
    fun totalIncludesReviewsAndFollowsDeletes() {
        val l = log("g", 0, LogType.DONE)
        val d = data(good(), logs = listOf(l)).copy(reviews = mapOf(SAT to Review(SAT, change = "x")))
        assertEquals(35, Points.total(d))
        assertEquals(25, Points.total(d.copy(logs = emptyList())))
    }

    @Test
    fun levels() {
        assertEquals(1, Levels.of(0).number)
        assertEquals(2, Levels.of(150).number)
        assertNull(Levels.of(100000).next)
        assertEquals(0.5f, Levels.of(75).progress(75), 0.001f)
    }

    @Test
    fun badgesComeFromTheLogs() {
        assertTrue(Badges.all(data(good()), day(0)).none { it.earned })
        val d = data(bad(), logs = listOf(log("q", 0, LogType.RESIST)))
        val earned = Badges.all(d, day(0)).filter { it.earned }.map { it.id }.toSet()
        assertEquals(setOf("first_log", "resist_1"), earned)
    }

    @Test
    fun betterWeekNeedsBothWeeksCheckedOn() {
        val logs = listOf(log("q", 0, LogType.SLIP), log("q", 1, LogType.SLIP), log("q", 8, LogType.SLIP))
        fun has(days: Map<java.time.LocalDate, com.kassa.habit.data.DayNote>) =
            Badges.all(data(bad(), logs = logs, days = days), day(15)).first { it.id == "better_week" }.earned
        assertTrue(has(closed(0, 1, 2, 3, 7, 8, 9, 10)))
        assertFalse(has(closed(0, 1, 2, 3, 7)))
    }

    @Test
    fun partWeekIsComparedWithTheSamePartOfLastWeek() {
        val d = data(
            bad(),
            logs = listOf(
                log("q", 0, LogType.SLIP), log("q", 1, LogType.SLIP), log("q", 6, LogType.SLIP), // last week
                log("q", 7, LogType.SLIP), log("q", 8, LogType.RESIST),
            ),
        )
        val r = weekReport(d, day(7), day(8))
        assertEquals(2, r.elapsed)
        val q = r.quit.single()
        assertEquals(1, q.slips)
        assertEquals(1, q.resists)
        // today is still open, so only Saturday is set against last Saturday
        assertEquals(1, r.comparedDays)
        assertEquals(1, q.previousSlips)
        assertEquals(1, q.comparedSlips)
        assertEquals(50, q.resistRate)
        // once today is closed it joins: last week's first two days had two slips
        val after = weekReport(d.copy(days = closed(8)), day(7), day(8))
        assertEquals(2, after.comparedDays)
        assertEquals(2, after.quit.single().previousSlips)
    }

    @Test
    fun nothingToCompareOnAnOpenSaturday() {
        val r = weekReport(data(bad()), day(7), day(7))
        assertNull(r.quit.single().previousSlips)
        assertNull(r.previousPoints)
    }

    @Test
    fun noComparisonForANewHabit() {
        val r = weekReport(data(bad(created = day(2))), SAT, day(4))
        assertNull(r.quit.single().previousSlips)
        assertNull(r.previousPoints)
    }

    @Test
    fun habitPercentIgnoresTodayUntilItIsLogged() {
        val d = data(good(), logs = listOf(log("g", 0, LogType.DONE), log("g", 1, LogType.TINY)))
        assertEquals(85, weekReport(d, SAT, day(2)).build.single().percent)
        assertEquals(57, weekReport(d, SAT, day(3)).build.single().percent)
    }

    @Test
    fun patternsNeedEnoughRecords() {
        val few = data(bad(), logs = (0..3).map { log("q", it, LogType.SLIP, setOf("خستگی")) })
        assertTrue(insightsFor(few, bad(), day(10)).isEmpty())
    }

    @Test
    fun commonTriggerAndTimeAreFound() {
        val logs = (0..5).map { log("q", it, LogType.SLIP, setOf("خستگی"), minute = 22 * 60) } +
            log("q", 6, LogType.SLIP, setOf("استرس"), minute = 9 * 60)
        val found = insightsFor(data(bad(), logs = logs), bad(), day(10))
        val trigger = found.first { "خستگی" in it.text }
        assertTrue("۶ بار از ۷ بار" in trigger.text)
        assertNotNull(trigger.tip)
        assertTrue(found.any { TimeBand.NIGHT.label in it.text })
    }

    @Test
    fun scatteredRecordsShowNoPattern() {
        val triggers = listOf("خستگی", "استرس", "عجله", "تنهایی", "گرسنگی", "ناراحتی")
        val logs = triggers.mapIndexed { i, t -> log("q", i * 3, LogType.SLIP, setOf(t), minute = -1) }
        assertTrue(insightsFor(data(bad(), logs = logs), bad(), day(20)).none { "هم‌زمان" in it.text })
    }

    @Test
    fun coachWelcomesBackAndNeverBlames() {
        val d = data(good(), logs = listOf(log("g", 0, LogType.DONE)))
        assertEquals("خوش برگشتی", Coach.today(d, day(5), 10).title)
        val slipped = data(bad().copy(plan = "آب می‌خورم"), logs = listOf(log("q", 1, LogType.SLIP)))
        assertTrue("آب می‌خورم" in Coach.today(slipped, day(1), 10).text)
    }

    @Test
    fun templatesAreConsistent() {
        assertEquals(Templates.all.size, Templates.all.map { it.key }.toSet().size)
        assertEquals(Templates.all.size, Templates.all.map { it.title }.toSet().size)
        Templates.all.mapNotNull { it.pairedWith }.forEach { assertEquals(Kind.BUILD, Templates.byKey(it)?.kind) }
    }
}
