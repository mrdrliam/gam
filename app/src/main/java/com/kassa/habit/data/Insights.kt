package com.kassa.habit.data

import java.time.LocalDate
import kotlin.math.roundToInt

// ------------------------------------------------------------------ weekly report

data class BuildWeek(
    val habit: Habit,
    /** Seven days, Saturday first. */
    val days: List<DayStatus>,
    /** The decided days' progress added up (see [buildProgress]). */
    val score: Double,
    /** Planned days that are already decided (today only counts once it is finished). */
    val planned: Int,
    /** Times done per day, Saturday first. */
    val times: List<Int>,
    /** Counted several times a day during this week. */
    val counted: Boolean,
    /** Times per day, over the days of this week that have begun. */
    val average: Double,
    /** The time of day it mostly happens, when that is clear. */
    val usualTime: TimeBand?,
) {
    val total: Int get() = times.sum()

    /** 0..100, or null when nothing was due yet. */
    val percent: Int?
        get() = if (planned == 0) null else (score * 100 / planned).roundToInt().coerceAtMost(100)
}

data class QuitWeek(
    val habit: Habit,
    val slipsPerDay: List<Int>,
    val days: List<QuitStatus>,
    val slips: Int,
    val resists: Int,
    /** Slips on the same days of the previous week, or null when there is nothing fair to compare with. */
    val previousSlips: Int?,
    /** This week's slips on the compared days. */
    val comparedSlips: Int,
) {
    val resistRate: Int? get() = if (slips + resists == 0) null else (resists * 100.0 / (slips + resists)).roundToInt()
}

data class WeekReport(
    val start: LocalDate,
    /** Days of this week that have already begun (7 for a past week). */
    val elapsed: Int,
    val points: Int,
    /** Points of the same days of the previous week (reviews included once the week is complete). */
    val previousPoints: Int?,
    /** This week's points on the compared days. */
    val comparedPoints: Int,
    /** How many days, from Saturday, are compared: those that are over or already closed. */
    val comparedDays: Int,
    val closedDays: Int,
    val build: List<BuildWeek>,
    val quit: List<QuitWeek>,
) {
    val buildPercent: Int?
        get() {
            val planned = build.sumOf { it.planned }
            return if (planned == 0) null
            else (build.sumOf { it.score } * 100 / planned).roundToInt().coerceAtMost(100)
        }
    val slips: Int get() = quit.sumOf { it.slips }
    val resists: Int get() = quit.sumOf { it.resists }
}

fun weekReport(data: AppData, start: LocalDate, today: LocalDate): WeekReport {
    val end = start.plusDays(6)
    val dates = datesBetween(start, end)
    val elapsed = dates.count { !it.isAfter(today) }
    // an unfinished today would be set against a whole day of last week, so it waits until it is closed
    val unfinishedToday = today in dates && !data.isClosed(today)
    val compared = if (unfinishedToday) elapsed - 1 else elapsed
    val comparedEnd = start.plusDays((compared - 1).toLong())
    val prevStart = start.minusDays(7)
    val prevEnd = prevStart.plusDays((compared - 1).toLong())

    val shown = data.habits.filter { h -> dates.any { h.activeOn(it) && !it.isAfter(today) } }

    val build = shown.filter { it.kind == Kind.BUILD }.map { h ->
        val statuses = dates.map { buildStatus(data, h, it, today) }
        var score = 0.0
        var planned = 0
        dates.forEachIndexed { i, d ->
            if (stillCounting(data, h, d, today)) return@forEachIndexed
            val s = statuses[i]
            if (s == DayStatus.DONE || s == DayStatus.TINY || s == DayStatus.PARTIAL) score += buildProgress(data, h, d)
            if (h.scheduledOn(d) && s != DayStatus.PENDING && s != DayStatus.OFF) planned++
        }
        val times = dates.map { if (it.isAfter(today)) 0 else timesOn(data, h.id, it) }
        val begun = dates.filter { !it.isAfter(today) && h.activeOn(it) }
        BuildWeek(
            habit = h,
            days = statuses,
            score = score,
            planned = planned,
            times = times,
            counted = begun.any { h.countedOn(it) },
            average = if (begun.isEmpty()) 0.0 else times.sum().toDouble() / begun.size,
            usualTime = usualTime(data.logsOf(h.id).filter { it.isTime && it.date in dates }),
        )
    }

    val quit = shown.filter { it.kind == Kind.QUIT }.map { h ->
        QuitWeek(
            habit = h,
            slipsPerDay = dates.map { countOn(data, h.id, it, LogType.SLIP) },
            days = dates.map { quitStatus(data, h, it, today) },
            slips = countBetween(data, h.id, start, end, LogType.SLIP),
            resists = countBetween(data, h.id, start, end, LogType.RESIST),
            previousSlips = if (compared > 0 && !h.created.isAfter(prevStart))
                countBetween(data, h.id, prevStart, prevEnd, LogType.SLIP) else null,
            comparedSlips = countBetween(data, h.id, start, comparedEnd, LogType.SLIP),
        )
    }

    val firstDay = data.habits.minOfOrNull { it.created }
    return WeekReport(
        start = start,
        elapsed = elapsed,
        points = if (elapsed == 0) 0 else Points.between(data, start, end),
        previousPoints = if (compared > 0 && firstDay != null && !firstDay.isAfter(prevStart))
            Points.between(data, prevStart, prevEnd) else null,
        comparedPoints = if (compared > 0) Points.between(data, start, comparedEnd) else 0,
        comparedDays = compared,
        closedDays = dates.count { data.isClosed(it) },
        build = build,
        quit = quit,
    )
}

// ------------------------------------------------------------------ patterns

/** Fewer records than this and no pattern is shown: it would mostly be chance. */
const val MIN_FOR_PATTERN = 5

enum class TimeBand(val label: String, val sample: Int) {
    MORNING("صبح", 9 * 60),
    NOON("بعدازظهر", 14 * 60),
    EVENING("عصر", 19 * 60),
    NIGHT("شب", 22 * 60),
    LATE("نیمه‌شب", 60);

    companion object {
        fun of(minute: Int): TimeBand? = when {
            minute < 0 -> null
            minute < 5 * 60 -> LATE
            minute < 12 * 60 -> MORNING
            minute < 17 * 60 -> NOON
            minute < 21 * 60 -> EVENING
            else -> NIGHT
        }
    }
}

val MoodFaces = listOf("😞", "😕", "😐", "🙂", "😄")

/** The time of day most records fall in, when there are enough of them to say so. */
fun usualTime(logs: List<Log>): TimeBand? = top(logs.map { setOfNotNull(TimeBand.of(it.minute)) }, 0.4)?.value

data class Insight(val emoji: String, val text: String, val tip: String? = null)

private data class Top<T>(val value: T, val count: Int, val total: Int) {
    val percent: Int get() = (count * 100.0 / total).roundToInt()
}

/** The most common value, if it is frequent enough to be worth saying. [total] counts records that have data. */
private fun <T> top(values: List<Set<T>>, minShare: Double): Top<T>? {
    val known = values.filter { it.isNotEmpty() }
    if (known.size < MIN_FOR_PATTERN) return null
    val best = known.flatten().groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
    if (best.value < 3 || best.value.toDouble() / known.size < minShare) return null
    return Top(best.key, best.value, known.size)
}

private fun share(t: Top<*>) = "${t.count.fa()} بار از ${t.total.fa()} بار (${t.percent.fa()}٪)"

/**
 * Patterns seen in the last [days] days. They are co-occurrences ("seen together with"),
 * not proof of a cause, and the wording keeps it that way.
 */
fun insightsFor(data: AppData, h: Habit, today: LocalDate, days: Long = 60): List<Insight> {
    val from = today.minusDays(days - 1)
    val logs = data.logsOf(h.id).filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
    return if (h.kind == Kind.QUIT) quitInsights(logs) else buildInsights(data, h, logs, from, today)
}

private fun quitInsights(logs: List<Log>): List<Insight> {
    val slips = logs.filter { it.type == LogType.SLIP }
    val out = ArrayList<Insight>()
    top(slips.map { it.triggers }, 0.4)?.let {
        out.add(Insight("🎯", "${share(it)} هم‌زمان با «${it.value}» ثبت شده.", Coach.tipFor(it.value)))
    }
    top(slips.map { setOfNotNull(TimeBand.of(it.minute)) }, 0.4)?.let {
        out.add(Insight("🕒", "بیشتر ${it.value.label} رخ داده: ${share(it)}.", "قبل از این ساعت یک یادآور بگذار تا برنامه‌ات جلوی چشمت باشد."))
    }
    top(slips.map { setOf(Jalali.weekdayName(it.date)) }, 0.3)?.let {
        out.add(Insight("📆", "${it.value} روز حساس‌تری است: ${share(it)}.", null))
    }
    top(slips.map { setOfNotNull(it.place.ifBlank { null }) }, 0.5)?.let {
        out.add(Insight("📍", "مکان پرتکرار «${it.value}» است: ${share(it)}.", "می‌توانی چیزی را در آن محیط تغییر بدهی تا کار سخت‌تر شود؟"))
    }
    top(slips.map { setOfNotNull(it.people.ifBlank { null }) }, 0.5)?.let {
        out.add(Insight("👥", "همراه پرتکرار «${it.value}» است: ${share(it)}.", null))
    }
    val moods = slips.filter { it.mood > 0 }
    if (moods.size >= MIN_FOR_PATTERN) {
        val low = moods.count { it.mood <= 2 }
        if (low * 2 >= moods.size) out.add(
            Insight("🌧️", "${low.fa()} بار از ${moods.size.fa()} بار حالت خوب نبوده.", "وقتی حالت پایین است، اول به خودِ حال رسیدگی کن: استراحت، آب، قدم‌زدن یا حرف زدن با کسی.")
        )
    }
    val resists = logs.count { it.type == LogType.RESIST }
    if (resists >= 3 && slips.size + resists >= MIN_FOR_PATTERN) {
        val rate = (resists * 100.0 / (slips.size + resists)).roundToInt()
        out.add(Insight("💪", "در ${rate.fa()}٪ موقعیت‌های ثبت‌شده مقاومت کرده‌ای.", null))
    }
    return out
}

private fun buildInsights(data: AppData, h: Habit, logs: List<Log>, from: LocalDate, today: LocalDate): List<Insight> {
    val out = ArrayList<Insight>()
    val dates = datesBetween(maxOf(from, h.created), today).filter { h.scheduledOn(it) }
    val results = dates.mapNotNull { d ->
        when (buildStatus(data, h, d, today)) {
            DayStatus.DONE, DayStatus.TINY, DayStatus.PARTIAL -> d to true
            DayStatus.MISSED, DayStatus.SKIPPED -> d to false
            else -> null
        }
    }
    val worst = results.groupBy { Jalali.weekdayName(it.first) }
        .mapValues { (_, v) -> v.count { !it.second } to v.size }
        .filter { (_, c) -> c.first >= 3 && c.first * 2 >= c.second }
        .maxByOrNull { it.value.first.toDouble() / it.value.second }
    if (worst != null) out.add(
        Insight(
            "📆", "${worst.key}‌ها بیشتر جا می‌ماند: ${worst.value.first.fa()} از ${worst.value.second.fa()} بار.",
            "برای این روز نسخه‌ی کوچک‌تر یا ساعت دیگری در نظر بگیر.",
        )
    )
    top(logs.filter { it.type == LogType.SKIP }.map { it.triggers }, 0.4)?.let {
        out.add(Insight("🚧", "پرتکرارترین مانع «${it.value}» است: ${share(it)}.", Coach.tipFor(it.value)))
    }
    val good = results.filter { (data.days[it.first]?.mood ?: 0) >= 4 }
    val bad = results.filter { (data.days[it.first]?.mood ?: 0) in 1..2 }
    if (good.size >= MIN_FOR_PATTERN && bad.size >= MIN_FOR_PATTERN) {
        val g = good.count { it.second } * 100 / good.size
        val b = bad.count { it.second } * 100 / bad.size
        if (g - b >= 25) out.add(
            Insight("🙂", "در روزهای با حال خوب ${g.fa()}٪ و در روزهای سخت ${b.fa()}٪ انجام شده.", "برای روزهای سخت، همان نسخه‌ی کوچک کافی است.")
        )
    }
    if (h.target > 1) {
        top(logs.filter { it.isTime }.map { setOfNotNull(TimeBand.of(it.minute)) }, 0.4)?.let {
            out.add(Insight("🕒", "بیشتر ${it.value.label} انجامش می‌دهی: ${share(it)}.", "برای بقیه‌ی روز یک نشانه‌ی دم دست یا یادآور بگذار."))
        }
    }
    val tiny = logs.count { it.type == LogType.TINY }
    if (tiny >= 3) out.add(Insight("👣", "${tiny.fa()} بار با نسخه‌ی کوچک زنجیره را نگه داشتی. این یعنی انضباط.", null))
    return out
}
