package com.kassa.habit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Jalali
import com.kassa.habit.data.Store
import com.kassa.habit.data.fa
import com.kassa.habit.data.fmtMonth
import com.kassa.habit.ui.theme.G
import java.time.LocalDate

val WeekHeads = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/** How one day is painted. */
data class DayPaint(val bg: Color = Color.Transparent, val fg: Color? = null, val ring: Color? = null)

/**
 * A Persian month, Saturday first. Days after today cannot be tapped.
 */
@Composable
fun JalaliMonth(
    year: Int,
    month: Int,
    onPick: (LocalDate) -> Unit,
    paint: @Composable (LocalDate) -> DayPaint,
) {
    val c = G.c
    val today = Store.today
    val length = Jalali.monthLength(year, month)
    val offset = Jalali.weekColumn(Jalali.toGregorian(year, month, 1))
    val rows = (offset + length + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WeekHeads.forEach { h ->
                Box(Modifier.weight(1f).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                    T(h, 12, FontWeight.SemiBold, c.muted)
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                        if (day in 1..length) {
                            val date = Jalali.toGregorian(year, month, day)
                            val future = date.isAfter(today)
                            val p = paint(date)
                            val ring = p.ring ?: if (date == today) c.accent else null
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape).background(p.bg)
                                    .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                                    .clickable(enabled = !future) { onPick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                T(
                                    day.fa(), 14, if (date == today) FontWeight.Bold else FontWeight.Medium,
                                    if (future) c.muted.copy(alpha = 0.4f) else p.fg ?: c.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A month with its own previous / next header. */
@Composable
fun MonthPager(initial: LocalDate, onPick: (LocalDate) -> Unit, paint: @Composable (LocalDate) -> DayPaint) {
    val start = Jalali.toJalali(initial)
    var year by remember { mutableIntStateOf(start.y) }
    var month by remember { mutableIntStateOf(start.m) }
    val now = Jalali.toJalali(Store.today)
    val atNow = year > now.y || (year == now.y && month >= now.m)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Pager(
            fmtMonth(year, month),
            onPrevious = { if (month == 1) { month = 12; year-- } else month-- },
            onNext = if (atNow) null else ({ if (month == 12) { month = 1; year++ } else month++ }),
        )
        JalaliMonth(year, month, onPick, paint)
    }
}

/** Picks a past day (or today) from the Persian calendar. */
@Composable
fun DateDialog(selected: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = G.c
    DialogCard(onDismiss) {
        MonthPager(selected, onPick) { date ->
            if (date == selected) DayPaint(bg = c.primary, fg = c.onPrimary)
            else if (Store.data.isClosed(date)) DayPaint(bg = c.goodBg, fg = c.good)
            else DayPaint()
        }
        GhostButton("امروز", { onPick(Store.today) }, Modifier.fillMaxWidth(), color = c.accent)
        GhostButton("انصراف", onDismiss, Modifier.fillMaxWidth())
    }
}
