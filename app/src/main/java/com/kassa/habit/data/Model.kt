package com.kassa.habit.data

import java.time.LocalDate

/** BUILD: a good habit to grow. QUIT: a behaviour to do less of. */
enum class Kind { BUILD, QUIT }

/** DONE / TINY / SKIP belong to BUILD habits, SLIP / RESIST to QUIT behaviours. */
enum class LogType { DONE, TINY, SKIP, SLIP, RESIST }

val BuildStatusTypes = setOf(LogType.DONE, LogType.TINY, LogType.SKIP)

/** Week columns: 0 = Saturday … 6 = Friday. */
val AllDays: Set<Int> = (0..6).toSet()

/**
 * The days a habit is planned on and how many times a day, valid from [from] until the next change
 * (so editing never rewrites history).
 */
data class DaysChange(val from: LocalDate, val days: Set<Int>, val target: Int = 1)

data class Habit(
    val id: String,
    val kind: Kind,
    val title: String,
    val emoji: String,
    val created: LocalDate,
    /** Why this matters to me. */
    val why: String = "",
    /** BUILD: after what / where. QUIT: the risky situation. */
    val cue: String = "",
    /** BUILD: the smallest version that still counts. */
    val tiny: String = "",
    /** QUIT: the if-then plan. */
    val plan: String = "",
    /** QUIT: what I do instead. */
    val replacement: String = "",
    /** QUIT: the good habit that replaces it, if one is tracked. */
    val replacementId: String? = null,
    val schedule: List<DaysChange> = listOf(DaysChange(created, AllDays)),
    /** QUIT: aim for at most this many slips per week. */
    val weeklyLimit: Int? = null,
    /** Minute of day for a reminder. */
    val remindAt: Int? = null,
    val archivedOn: LocalDate? = null,
) {
    val archived: Boolean get() = archivedOn != null

    /** How many times a day it is aimed for now; 1 = a once-a-day habit. */
    val target: Int get() = schedule.lastOrNull()?.target ?: 1

    private fun planOn(date: LocalDate): DaysChange? =
        schedule.lastOrNull { !it.from.isAfter(date) } ?: schedule.firstOrNull()

    fun daysOn(date: LocalDate): Set<Int> = planOn(date)?.days ?: AllDays

    fun targetOn(date: LocalDate): Int = planOn(date)?.target ?: 1

    /** Counted several times a day (a glass of water, a pause before speaking) rather than ticked once. */
    fun countedOn(date: LocalDate): Boolean = kind == Kind.BUILD && targetOn(date) > 1

    /** Does the habit exist on [date]: created, not archived then, not within an earlier pause? */
    fun activeOn(date: LocalDate): Boolean =
        !date.isBefore(created) && (archivedOn == null || date.isBefore(archivedOn)) && daysOn(date).isNotEmpty()

    fun scheduledOn(date: LocalDate): Boolean = activeOn(date) && Jalali.weekColumn(date) in daysOn(date)

    /** Is it listed for [date]? An archived habit leaves today at once but stays on the days before. */
    fun listedOn(date: LocalDate, today: LocalDate): Boolean = activeOn(date) && !(archived && !date.isBefore(today))

    /** Today still counts for points and history. */
    fun archive(today: LocalDate): Habit = copy(archivedOn = today.plusDays(1))

    /** Back from the archive. The days spent there become a pause (no planned days), not missed days. */
    fun restore(today: LocalDate): Habit {
        val from = archivedOn ?: return this
        if (from.isAfter(today)) return copy(archivedOn = null)
        val days = schedule.lastOrNull()?.days ?: AllDays
        return copy(
            archivedOn = null,
            schedule = schedule.filter { it.from.isBefore(from) } + DaysChange(from, emptySet()) + DaysChange(today, days, target),
        )
    }
}

data class Log(
    val id: String,
    val habitId: String,
    /** The logical day (see [logicalDate]): 1 a.m. still belongs to the evening before. */
    val date: LocalDate,
    /** When it was written down. */
    val at: Long,
    /** Minute of day when it happened; -1 if unknown (logged for an earlier day). */
    val minute: Int,
    val type: LogType,
    /** 0 = not given, 1 (very bad) … 5 (very good). */
    val mood: Int = 0,
    /** Triggers of a slip / resist, or the reasons for a skip. */
    val triggers: Set<String> = emptySet(),
    val place: String = "",
    val people: String = "",
    val note: String = "",
    /** What I will do next time. */
    val nextPlan: String = "",
) {
    val reflected: Boolean get() = triggers.isNotEmpty() || nextPlan.isNotBlank()
}

/** The evening check-in of one day. [closed] confirms that the day's logs are complete. */
data class DayNote(
    val date: LocalDate,
    val closed: Boolean = false,
    val mood: Int = 0,
    val win: String = "",
    val note: String = "",
)

/** Weekly review, keyed by the Saturday the week starts on. */
data class Review(
    val week: LocalDate,
    val wentWell: String = "",
    val obstacle: String = "",
    /** The one change for next week; shown as "focus of the week". */
    val change: String = "",
    val rating: Int = 0,
    val at: Long = 0,
)

data class Settings(
    val name: String = "",
    /** 0 = follow system, 1 = light, 2 = dark. */
    val theme: Int = 0,
    /** Hour at which a new day begins (0..6). */
    val dayStartHour: Int = 3,
    val notify: Boolean = true,
    /** Evening check-in reminder (minute of day), null = off. */
    val eveningAt: Int? = 21 * 60 + 30,
    /** Friday weekly review reminder (minute of day), null = off. */
    val reviewAt: Int? = 20 * 60,
    val appLock: Boolean = false,
    val lockGraceSec: Int = 60,
    val onboarded: Boolean = false,
    /** Badges already celebrated. */
    val seenBadges: Set<String> = emptySet(),
)

data class AppData(
    val habits: List<Habit> = emptyList(),
    val logs: List<Log> = emptyList(),
    val days: Map<LocalDate, DayNote> = emptyMap(),
    val reviews: Map<LocalDate, Review> = emptyMap(),
    val settings: Settings = Settings(),
) {
    private val byHabit: Map<String, List<Log>> by lazy { logs.groupBy { it.habitId } }
    private val byHabitDay: Map<Pair<String, LocalDate>, List<Log>> by lazy { logs.groupBy { it.habitId to it.date } }

    val activeHabits: List<Habit> get() = habits.filter { !it.archived }

    fun habit(id: String?): Habit? = if (id == null) null else habits.firstOrNull { it.id == id }
    fun logsOf(habitId: String): List<Log> = byHabit[habitId].orEmpty()
    fun logsOn(habitId: String, date: LocalDate): List<Log> = byHabitDay[habitId to date].orEmpty()
    fun isClosed(date: LocalDate): Boolean = days[date]?.closed == true
}
