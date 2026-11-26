package com.kassa.habit.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The day a moment belongs to when days begin at [dayStartHour]. */
fun logicalDate(now: LocalDateTime, dayStartHour: Int): LocalDate = now.minusHours(dayStartHour.toLong()).toLocalDate()

/** The Saturday of the week containing [d]. */
fun weekStart(d: LocalDate): LocalDate = d.minusDays(Jalali.weekColumn(d).toLong())

/** Every date from [from] to [to], both included; empty if [from] is later. */
fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    val n = ChronoUnit.DAYS.between(from, to)
    return if (n < 0) emptyList() else (0..n).map { from.plusDays(it) }
}

// ------------------------------------------------------------------ good habits

/** PARTIAL: a counted habit that was done, but fewer times than its target. */
enum class DayStatus { DONE, TINY, PARTIAL, SKIPPED, MISSED, PENDING, OFF }

val Log.isTime: Boolean get() = type == LogType.DONE || type == LogType.TINY

/** How many times a good habit was done on [date]. */
fun timesOn(data: AppData, habitId: String, date: LocalDate): Int = data.logsOn(habitId, date).count { it.isTime }

fun buildStatus(data: AppData, h: Habit, date: LocalDate, today: LocalDate): DayStatus {
    val logs = data.logsOn(h.id, date)
    val counted = h.countedOn(date)
    val times = logs.count { it.isTime }
    return when {
        counted && times >= h.targetOn(date) -> DayStatus.DONE
        counted && times > 0 -> DayStatus.PARTIAL
        logs.any { it.type == LogType.DONE } -> DayStatus.DONE
        logs.any { it.type == LogType.TINY } -> DayStatus.TINY
        logs.any { it.type == LogType.SKIP } -> DayStatus.SKIPPED
        !h.scheduledOn(date) || date.isAfter(today) -> DayStatus.OFF
        date == today -> DayStatus.PENDING
        else -> DayStatus.MISSED
    }
}

/** How much of a day was done, 0..1. */
fun buildProgress(data: AppData, h: Habit, date: LocalDate): Double {
    val logs = data.logsOn(h.id, date)
    return when {
        h.countedOn(date) -> (logs.count { it.isTime }.toDouble() / h.targetOn(date)).coerceAtMost(1.0)
        logs.any { it.type == LogType.DONE } -> 1.0
        logs.any { it.type == LogType.TINY } -> 0.7
        else -> 0.0
    }
}

/** A count that can still grow: today, not closed yet, below its target. It is not judged before that. */
fun stillCounting(data: AppData, h: Habit, date: LocalDate, today: LocalDate): Boolean =
    date == today && !data.isClosed(date) && buildStatus(data, h, date, today) == DayStatus.PARTIAL

/**
 * The records a new status takes the place of. A counted habit keeps every earlier time;
 * only its "not today" is dropped.
 */
fun replacedByStatus(dayLogs: List<Log>, counted: Boolean): List<Log> =
    dayLogs.filter { if (counted) it.type == LogType.SKIP else it.type in BuildStatusTypes }

data class Streak(val current: Int, val best: Int, val atRisk: Boolean)

/**
 * "Never miss twice": one missed day does not reset the chain, two in a row do.
 * [outcomes] is oldest first: true = done, false = missed.
 */
fun streakOf(outcomes: List<Boolean>): Streak {
    var count = 0
    var best = 0
    var misses = 0
    for (done in outcomes) {
        if (done) {
            count++
            misses = 0
            if (count > best) best = count
        } else {
            misses++
            if (misses >= 2) count = 0
        }
    }
    return Streak(count, best, atRisk = count > 0 && misses == 1)
}

fun habitStreak(data: AppData, h: Habit, today: LocalDate): Streak = streakOf(
    datesBetween(h.created, today).mapNotNull {
        when (buildStatus(data, h, it, today)) {
            DayStatus.DONE, DayStatus.TINY, DayStatus.PARTIAL -> true
            DayStatus.MISSED, DayStatus.SKIPPED -> if (h.scheduledOn(it)) false else null
            DayStatus.PENDING, DayStatus.OFF -> null
        }
    }
)

private const val STRENGTH_ALPHA = 0.08

/** "Habit strength" 0..100: an exponential average over planned days, so recent days weigh more. */
fun habitStrength(data: AppData, h: Habit, today: LocalDate): Int {
    var s = 0.0
    for (d in datesBetween(h.created, today)) {
        val status = buildStatus(data, h, d, today)
        val value = when (status) {
            DayStatus.DONE, DayStatus.TINY, DayStatus.PARTIAL -> buildProgress(data, h, d)
            DayStatus.MISSED, DayStatus.SKIPPED -> if (h.scheduledOn(d)) 0.0 else continue
            DayStatus.PENDING, DayStatus.OFF -> continue
        }
        val next = s * (1 - STRENGTH_ALPHA) + value * STRENGTH_ALPHA
        // a count still in progress, or an extra one outside the plan, can only add
        val onlyAdds = status == DayStatus.PARTIAL && (!h.scheduledOn(d) || stillCounting(data, h, d, today))
        s = if (onlyAdds) maxOf(s, next) else next
    }
    return (s * 100).roundToInt()
}

// ------------------------------------------------------------------ behaviours to reduce

/** CLEAN needs a closed day: a day nobody checked is UNKNOWN, not clean. */
enum class QuitStatus { SLIPPED, CLEAN, RESISTED, UNKNOWN, OFF }

fun quitStatus(data: AppData, h: Habit, date: LocalDate, today: LocalDate): QuitStatus {
    val logs = data.logsOn(h.id, date)
    return when {
        logs.any { it.type == LogType.SLIP } -> QuitStatus.SLIPPED
        !h.activeOn(date) || date.isAfter(today) -> QuitStatus.OFF
        data.isClosed(date) -> QuitStatus.CLEAN
        logs.any { it.type == LogType.RESIST } -> QuitStatus.RESISTED
        else -> QuitStatus.UNKNOWN
    }
}

/**
 * Confirmed clean days since the last slip. Days that were never closed neither count nor break the run,
 * so forgetting to check in is not punished and silence is not rewarded.
 */
fun cleanRun(data: AppData, h: Habit, today: LocalDate): Streak {
    var run = 0
    var best = 0
    for (d in datesBetween(h.created, today)) {
        when (quitStatus(data, h, d, today)) {
            QuitStatus.SLIPPED -> run = 0
            QuitStatus.CLEAN -> {
                run++
                if (run > best) best = run
            }
            else -> {}
        }
    }
    return Streak(run, best, false)
}

fun countOn(data: AppData, habitId: String, date: LocalDate, type: LogType): Int =
    data.logsOn(habitId, date).count { it.type == type }

fun countBetween(data: AppData, habitId: String, from: LocalDate, to: LocalDate, type: LogType): Int =
    data.logsOf(habitId).count { it.type == type && !it.date.isBefore(from) && !it.date.isAfter(to) }

// ------------------------------------------------------------------ presence

private fun activityDays(data: AppData): Set<LocalDate> =
    data.logs.mapTo(HashSet()) { it.date }.apply { data.days.values.forEach { if (it.closed) add(it.date) } }

/** Consecutive days of showing up (any log or a closed day), with the same one-day forgiveness. */
fun presenceStreak(data: AppData, today: LocalDate): Streak {
    val active = activityDays(data)
    val first = active.minOrNull() ?: return Streak(0, 0, false)
    return streakOf(datesBetween(first, today).filter { it != today || it in active }.map { it in active })
}

fun lastActivity(data: AppData, upTo: LocalDate): LocalDate? =
    activityDays(data).filter { !it.isAfter(upTo) }.maxOrNull()

fun everComeBack(data: AppData): Boolean {
    val days = activityDays(data).sorted()
    return days.zipWithNext().any { (a, b) -> ChronoUnit.DAYS.between(a, b) >= 4 }
}
