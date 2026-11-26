package com.kassa.habit.notify

import com.kassa.habit.data.AppData
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.logicalDate
import com.kassa.habit.data.weekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

sealed class Reminder {
    abstract val at: LocalDateTime

    data class ForHabit(val habit: Habit, override val at: LocalDateTime, val date: LocalDate) : Reminder()
    data class Evening(override val at: LocalDateTime, val date: LocalDate) : Reminder()
    data class WeeklyReview(override val at: LocalDateTime, val week: LocalDate) : Reminder()
}

/** Pure planning of reminders; no Android here, so it can be unit-tested. */
object ReminderPlan {

    private fun atMinute(day: LocalDate, minute: Int) = day.atTime(minute / 60, minute % 60)

    /** Everything planned on calendar day [day], whether or not it is still needed. */
    private fun planned(data: AppData, day: LocalDate): List<Reminder> {
        val s = data.settings
        if (!s.notify) return emptyList()
        val out = ArrayList<Reminder>()
        for (h in data.activeHabits) {
            val minute = h.remindAt ?: continue
            val at = atMinute(day, minute)
            // a reminder after midnight belongs to the evening before
            val date = logicalDate(at, s.dayStartHour)
            if (h.scheduledOn(date)) out.add(Reminder.ForHabit(h, at, date))
        }
        if (data.activeHabits.isNotEmpty()) {
            s.eveningAt?.let { val at = atMinute(day, it); out.add(Reminder.Evening(at, logicalDate(at, s.dayStartHour))) }
            s.reviewAt?.let {
                val at = atMinute(day, it)
                val date = logicalDate(at, s.dayStartHour)
                if (date.dayOfWeek == DayOfWeek.FRIDAY) out.add(Reminder.WeeklyReview(at, weekStart(date)))
            }
        }
        return out
    }

    /** Is there still something to do? A counted habit stays open until its target is reached. */
    fun isOpen(status: DayStatus): Boolean =
        status == DayStatus.PENDING || status == DayStatus.OFF || status == DayStatus.PARTIAL

    /** Reminders that were already taken care of are dropped. */
    fun stillNeeded(data: AppData, r: Reminder): Boolean = when (r) {
        is Reminder.ForHabit -> {
            val h = data.habit(r.habit.id)
            h != null && !h.archived &&
                (h.kind == Kind.QUIT || isOpen(buildStatus(data, h, r.date, r.date)))
        }
        is Reminder.Evening -> !data.isClosed(r.date)
        is Reminder.WeeklyReview -> r.week !in data.reviews
    }

    /** The next moment anything is planned, strictly after [after]; null if nothing is. */
    fun next(data: AppData, after: LocalDateTime): LocalDateTime? {
        for (i in 0L..8L) {
            val day = after.toLocalDate().plusDays(i)
            val first = planned(data, day).map { it.at }.filter { it.isAfter(after) }.minOrNull()
            if (first != null) return first
        }
        return null
    }

    /** What should be shown for the stretch ([after], [until]]. */
    fun due(data: AppData, after: LocalDateTime, until: LocalDateTime): List<Reminder> =
        generateSequence(after.toLocalDate()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(until.toLocalDate()) }
            .flatMap { planned(data, it) }
            .filter { it.at.isAfter(after) && !it.at.isAfter(until) && stillNeeded(data, it) }
            .toList()
}
