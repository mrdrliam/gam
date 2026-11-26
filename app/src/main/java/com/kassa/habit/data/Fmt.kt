package com.kassa.habit.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Latin digits -> Persian digits. */
fun String.fa(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
    return sb.toString()
}

fun Int.fa(): String = toString().fa()

/** 3.75 -> «۳٫۸», 4.0 -> «۴». */
fun fmtDecimal(x: Double): String =
    String.format(java.util.Locale.US, "%.1f", x).removeSuffix(".0").replace('.', '٫').fa()

/** «۳ از ۸»; past the target «۹ بار (هدف ۸)». */
fun countLabel(times: Int, target: Int): String =
    if (times > target) "${times.fa()} بار (هدف ${target.fa()})" else "${times.fa()} از ${target.fa()}"

fun fmtDate(d: LocalDate, weekday: Boolean = false, year: Boolean = false): String {
    val j = Jalali.toJalali(d)
    val sb = StringBuilder()
    if (weekday) sb.append(Jalali.weekdayName(d)).append(' ')
    sb.append(j.d).append(' ').append(Jalali.monthNames[j.m - 1])
    if (year) sb.append(' ').append(j.y)
    return sb.toString().fa()
}

fun fmtMonth(y: Int, m: Int): String = "${Jalali.monthNames[m - 1]} $y".fa()

/** «۱ تا ۷ آذر» or «۲۹ آبان تا ۵ آذر» for the week starting at [start]. */
fun fmtWeek(start: LocalDate): String {
    val end = start.plusDays(6)
    val a = Jalali.toJalali(start)
    val b = Jalali.toJalali(end)
    return if (a.m == b.m) "${a.d} تا ${b.d} ${Jalali.monthNames[b.m - 1]}".fa()
    else "${a.d} ${Jalali.monthNames[a.m - 1]} تا ${b.d} ${Jalali.monthNames[b.m - 1]}".fa()
}

/** «امروز»، «دیروز» or the date. */
fun dayLabel(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(d, today)) {
    0L -> "امروز"
    1L -> "دیروز"
    else -> fmtDate(d, weekday = true)
}

/** Minutes of day -> «۲۱:۳۰». */
fun fmtClock(minute: Int): String = "%02d:%02d".format(java.util.Locale.US, minute / 60, minute % 60).fa()
