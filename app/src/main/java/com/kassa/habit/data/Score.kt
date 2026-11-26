package com.kassa.habit.data

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Points are always derived from what was logged, never stored, so undoing or deleting a log
 * takes its points back. There are no negative points: logging a slip honestly earns a little.
 */
object Points {
    const val DONE = 10
    const val TINY = 6
    const val RESIST = 15
    const val AWARE = 2
    const val AWARE_REFLECTED = 2
    const val CLOSE_DAY = 5
    const val CLEAN = 8
    const val REVIEW = 25

    private const val MAX_RESIST_PER_DAY = 5

    /** Points of one day, without the weekly review. */
    fun ofDay(data: AppData, date: LocalDate): Int {
        var sum = 0
        val closed = data.isClosed(date)
        if (closed) sum += CLOSE_DAY
        for (h in data.habits) {
            val logs = data.logsOn(h.id, date)
            when (h.kind) {
                Kind.BUILD -> sum += buildPoints(h, date, logs)
                Kind.QUIT -> {
                    sum += logs.count { it.type == LogType.RESIST }.coerceAtMost(MAX_RESIST_PER_DAY) * RESIST
                    val slips = logs.filter { it.type == LogType.SLIP }
                    // honesty earns something once a day, always less than a clean day does
                    if (slips.isNotEmpty()) sum += AWARE + if (slips.any { it.reflected }) AWARE_REFLECTED else 0
                    if (closed && slips.isEmpty() && h.activeOn(date)) sum += CLEAN
                }
            }
        }
        return sum
    }

    private fun buildPoints(h: Habit, date: LocalDate, logs: List<Log>): Int {
        if (h.countedOn(date)) {
            val target = h.targetOn(date)
            val times = logs.count { it.isTime }.coerceAtMost(target)
            // in proportion to the target, full points when it is reached; the first time always counts
            return if (times == 0) 0 else (DONE * times.toDouble() / target).roundToInt().coerceAtLeast(1)
        }
        return when {
            logs.any { it.type == LogType.DONE } -> DONE
            logs.any { it.type == LogType.TINY } -> TINY
            else -> 0
        }
    }

    fun between(data: AppData, from: LocalDate, to: LocalDate): Int {
        val days = datesBetween(from, to).sumOf { ofDay(data, it) }
        val reviews = data.reviews.keys.count { !it.isBefore(from) && !it.isAfter(to) }
        return days + reviews * REVIEW
    }

    fun total(data: AppData): Int {
        val dates = HashSet<LocalDate>()
        data.logs.forEach { dates.add(it.date) }
        data.days.values.forEach { if (it.closed) dates.add(it.date) }
        return dates.sumOf { ofDay(data, it) } + data.reviews.size * REVIEW
    }
}

data class Level(val number: Int, val title: String, val from: Int, val next: Int?) {
    fun progress(points: Int): Float =
        if (next == null) 1f else ((points - from).toFloat() / (next - from)).coerceIn(0f, 1f)
}

object Levels {
    private val table = listOf(
        0 to "آغاز راه",
        150 to "قدم اول",
        400 to "در مسیر",
        900 to "پیوسته",
        1700 to "منضبط",
        3000 to "استوار",
        5000 to "خویشتن‌دار",
        8000 to "استادِ خویشتن",
    )

    fun of(points: Int): Level {
        val i = table.indexOfLast { points >= it.first }.coerceAtLeast(0)
        return Level(i + 1, table[i].second, table[i].first, table.getOrNull(i + 1)?.first)
    }
}

data class Badge(val id: String, val emoji: String, val title: String, val how: String, val progress: Int, val goal: Int) {
    val earned: Boolean get() = progress >= goal
}

object Badges {
    fun all(data: AppData, today: LocalDate): List<Badge> {
        // completed habit-days: a counted habit counts once the day's target is reached
        val done = data.habits.filter { it.kind == Kind.BUILD }.sumOf { h ->
            data.logsOf(h.id).filter { it.isTime }.groupBy { it.date }.count { (date, times) -> times.size >= h.targetOn(date) }
        }
        val resist = data.logs.count { it.type == LogType.RESIST }
        val aware = data.logs.count { it.type == LogType.SLIP && it.reflected }
        val presence = presenceStreak(data, today).best
        val strength = data.habits.filter { it.kind == Kind.BUILD }
            .maxOfOrNull { habitStrength(data, it, it.archivedOn?.minusDays(1) ?: today) } ?: 0
        val clean = data.habits.filter { it.kind == Kind.QUIT }
            .maxOfOrNull { cleanRun(data, it, it.archivedOn?.minusDays(1) ?: today).best } ?: 0
        val reviews = data.reviews.size
        fun badge(id: String, emoji: String, title: String, how: String, value: Int, goal: Int) =
            Badge(id, emoji, title, how, value.coerceAtMost(goal), goal)
        return listOf(
            badge("first_log", "🌱", "اولین قدم", "اولین ثبت", data.logs.size, 1),
            badge("resist_1", "✋", "اولین مقاومت", "یک بار جلوی خودت را بگیر", resist, 1),
            badge("review_1", "🔍", "نگاه به خود", "اولین مرور هفتگی", reviews, 1),
            badge("presence_7", "📅", "یک هفته حضور", "۷ روز پیوسته سر بزن", presence, 7),
            badge("aware_10", "🧠", "خودآگاه", "۱۰ لغزش را با دلیل یا برنامه ثبت کن", aware, 10),
            badge("resist_10", "🔥", "اراده‌ی بیدار", "۱۰ بار مقاومت", resist, 10),
            badge("done_25", "💪", "پشتکار", "۲۵ بار کامل‌کردن عادت خوب", done, 25),
            badge("clean_7", "✨", "هفت روز پاک", "۷ روز پاک تأییدشده در یک رفتار", clean, 7),
            badge("better_week", "📉", "هفته‌ی بهتر", "یک رفتار را از هفته‌ی قبل کمتر کن", if (hadBetterWeek(data, today)) 1 else 0, 1),
            badge("comeback", "🔄", "بازگشت", "بعد از چند روز دوری، برگرد", if (everComeBack(data)) 1 else 0, 1),
            badge("strength_60", "🌳", "ریشه دواند", "قدرت یک عادت را به ۶۰ برسان", strength, 60),
            badge("review_4", "🗓️", "یک ماه بازنگری", "۴ مرور هفتگی", reviews, 4),
            badge("presence_30", "🏔️", "سی روز استواری", "۳۰ روز پیوسته سر بزن", presence, 30),
            badge("done_100", "🏆", "صد بار", "۱۰۰ بار کامل‌کردن عادت خوب", done, 100),
            badge("resist_50", "🛡️", "سپر پولادین", "۵۰ بار مقاومت", resist, 50),
        )
    }

    /** A finished week with fewer slips than the one before, both of them checked on at least 4 days. */
    private fun hadBetterWeek(data: AppData, today: LocalDate): Boolean {
        val current = weekStart(today)
        return data.habits.filter { it.kind == Kind.QUIT }.any { h ->
            var week = weekStart(h.created).plusDays(7)
            // the first week to compare against must be one the habit existed in from its start
            if (weekStart(h.created) != h.created) week = week.plusDays(7)
            var found = false
            while (!found && week.isBefore(current) && h.activeOn(week.plusDays(6))) {
                val prev = week.minusDays(7)
                fun closed(s: LocalDate) = datesBetween(s, s.plusDays(6)).count { data.isClosed(it) }
                fun slips(s: LocalDate) = countBetween(data, h.id, s, s.plusDays(6), LogType.SLIP)
                found = closed(week) >= 4 && closed(prev) >= 4 && slips(week) < slips(prev)
                week = week.plusDays(7)
            }
            found
        }
    }
}
