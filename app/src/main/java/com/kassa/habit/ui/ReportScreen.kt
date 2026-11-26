package com.kassa.habit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.BuildWeek
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Insight
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Points
import com.kassa.habit.data.QuitStatus
import com.kassa.habit.data.QuitWeek
import com.kassa.habit.data.Review
import com.kassa.habit.data.Store
import com.kassa.habit.data.WeekReport
import com.kassa.habit.data.fa
import com.kassa.habit.data.fmtDecimal
import com.kassa.habit.data.fmtWeek
import com.kassa.habit.data.insightsFor
import com.kassa.habit.data.weekReport
import com.kassa.habit.data.weekStart
import com.kassa.habit.ui.theme.G
import java.time.LocalDate

@Composable
fun ReportScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    val current = weekStart(today)
    val week = app.pickedWeek?.takeIf { it < current } ?: current
    val report = remember(data, today, week) { weekReport(data, week, today) }
    val first = data.habits.minOfOrNull { it.created }
    // patterns come from the habits' last two months, not just this week
    val patterns: List<Pair<Habit, Insight>> = remember(data, today) {
        data.activeHabits.sortedBy { if (it.kind == Kind.QUIT) 0 else 1 }
            .flatMap { h -> insightsFor(data, h, today).take(2).map { h to it } }
            .take(5)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { T("گزارش هفتگی", 24, FontWeight.Bold) }
        item {
            Pager(
                fmtWeek(week),
                onPrevious = if (first != null && week > weekStart(first)) ({ app.pickedWeek = week.minusDays(7) }) else null,
                onNext = if (week < current) ({ app.pickedWeek = week.plusDays(7).takeIf { it < current } }) else null,
                subtitle = when (week) {
                    current -> "این هفته"
                    current.minusDays(7) -> "هفته‌ی قبل"
                    else -> null
                },
            )
        }

        if (report.build.isEmpty() && report.quit.isEmpty()) {
            item { EmptyState("📊", "هنوز چیزی برای گزارش نیست", "بعد از اضافه‌کردن رفتارها و چند ثبت، اینجا پیشرفتت را می‌بینی.") }
        } else {
            item { Summary(report) }
            if (report.quit.isNotEmpty()) {
                item { SectionTitle("در حال کم‌کردن", Modifier.padding(top = 6.dp)) }
                items(report.quit, key = { it.habit.id }) { QuitWeekCard(app, it, report.elapsed, report.comparedDays) }
            }
            if (report.build.isNotEmpty()) {
                item { SectionTitle("عادت‌های خوب", Modifier.padding(top = 6.dp)) }
                item { BuildWeekCard(app, report.build) }
            }
            if (week == current && patterns.isNotEmpty()) {
                item { SectionTitle("شناخت الگوها", Modifier.padding(top = 6.dp)) }
                items(patterns) { (habit, p) ->
                    GCard {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            T(p.emoji, 22)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                T("${habit.emoji} ${habit.title}", 12, FontWeight.SemiBold, c.muted)
                                T(p.text, 13, FontWeight.SemiBold)
                                p.tip?.let { T("💡 $it", 12, color = c.ink2) }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("مرور هفتگی", Modifier.padding(top = 6.dp)) }
            item { ReviewCard(app, week) }
        }
    }
}

@Composable
private fun Summary(r: WeekReport) {
    val gold = Color(0xFFFFC77A)
    val soft = Color.White.copy(alpha = 0.78f)
    Column(Modifier.fillMaxWidth().heroBackground().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            T("امتیاز هفته", 12, color = soft)
            T(r.points.fa(), 36, FontWeight.Bold, gold)
            r.previousPoints?.let { prev ->
                val diff = r.comparedPoints - prev
                val span = comparedSpan(r.comparedDays)
                T(
                    when {
                        diff > 0 -> "▲ ${diff.fa()} بیشتر از $span"
                        diff < 0 -> "▼ ${(-diff).fa()} کمتر از $span"
                        else -> "برابر با $span"
                    },
                    13, FontWeight.SemiBold, Color.White,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat("اجرای عادت‌ها", r.buildPercent?.let { "${it.fa()}٪" } ?: "—", Modifier.weight(1f))
            HeroStat("مقاومت", r.resists.fa(), Modifier.weight(1f))
            HeroStat("لغزش", r.slips.fa(), Modifier.weight(1f))
            HeroStat("روز بسته", "${r.closedDays.fa()} از ${r.elapsed.fa()}", Modifier.weight(1f))
        }
        T(encouragement(r), 13, FontWeight.SemiBold, gold)
    }
}

private fun encouragement(r: WeekReport): String {
    val fewer = r.quit.filter { it.previousSlips != null && it.comparedSlips < it.previousSlips }
    val percent = r.buildPercent
    return when {
        r.elapsed > 0 && r.closedDays * 2 < r.elapsed -> "هر شب روزت را ببند تا تصویرِ دقیق‌تری ببینی."
        fewer.isNotEmpty() -> "«${fewer.first().habit.title}» کمتر شده. این پیشرفتِ واقعی است 👏"
        percent != null && percent >= 80 -> "اجرای عادت‌ها عالی بوده. همین ریتم را نگه دار."
        r.resists > r.slips && r.resists > 0 -> "بیشتر وقت‌ها جلوی خودت را گرفته‌ای 💪"
        else -> "هر هفته یک تغییر کوچک؛ همین کافی است."
    }
}

private fun comparedSpan(days: Int) = if (days < 7) "همان روزهای هفته‌ی قبل" else "هفته‌ی قبل"

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(value, 18, FontWeight.Bold, Color.White, maxLines = 1)
        T(label, 10, color = Color.White.copy(alpha = 0.75f), align = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun QuitWeekCard(app: AppState, w: QuitWeek, elapsed: Int, comparedDays: Int) {
    val c = G.c
    val h = w.habit
    GCard(onClick = { app.detailId = h.id }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("${h.emoji} ${h.title}", 15, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                w.previousSlips?.let { prev ->
                    val diff = w.comparedSlips - prev
                    Pill(
                        when {
                            diff < 0 -> "▼ ${(-diff).fa()} کمتر"
                            diff > 0 -> "▲ ${diff.fa()} بیشتر"
                            else -> "بدون تغییر"
                        },
                        if (diff < 0) Tone.Good else if (diff > 0) Tone.Slip else Tone.Neutral,
                    )
                }
            }
            // slips per day, Saturday first
            val peak = (w.slipsPerDay.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                w.slipsPerDay.forEachIndexed { i, n ->
                    val status = w.days[i]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        T(
                            when {
                                n > 0 -> n.fa()
                                status == QuitStatus.CLEAN -> "✓"
                                else -> " "
                            },
                            11, FontWeight.Bold, if (n > 0) c.slip else c.good,
                        )
                        Box(
                            Modifier.fillMaxWidth().height(if (n > 0) (8 + 40 * n / peak).dp else 8.dp).clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (status) {
                                        QuitStatus.SLIPPED -> c.slip
                                        QuitStatus.CLEAN -> c.good
                                        QuitStatus.RESISTED -> c.goodBg
                                        else -> c.surface2
                                    }
                                )
                        )
                        T(WeekHeads[i], 10, color = c.muted)
                    }
                }
            }
            val parts = listOfNotNull(
                "${w.slips.fa()} بار",
                "${w.resists.fa()} مقاومت",
                w.resistRate?.let { "${it.fa()}٪ موفق در مقاومت" },
            )
            T(parts.joinToString(" · "), 13, color = c.ink2)
            w.previousSlips?.let {
                T("${comparedSpan(comparedDays)}: ${it.fa()} بار", 12, color = c.muted)
            }
            h.weeklyLimit?.let { limit ->
                val ok = w.slips <= limit
                T(
                    if (!ok) "از سقف هفته (${limit.fa()}) ${(w.slips - limit).fa()} بار بیشتر شد"
                    else if (elapsed < 7) "تا اینجا در محدوده‌ی هدف · ${(limit - w.slips).fa()} بار تا سقف"
                    else "هدف هفته برآورده شد ✓",
                    12, FontWeight.SemiBold, if (ok) c.good else c.slip,
                )
            }
        }
    }
}

@Composable
private fun BuildWeekCard(app: AppState, rows: List<BuildWeek>) {
    val c = G.c
    GCard {
        Column(Modifier.padding(vertical = 6.dp)) {
            rows.forEachIndexed { index, w ->
                if (index > 0) Divider()
                Column(
                    Modifier.fillMaxWidth().clickable { app.detailId = w.habit.id }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("${w.habit.emoji} ${w.habit.title}", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                        T(w.percent?.let { "${it.fa()}٪" } ?: "—", 15, FontWeight.Bold, if ((w.percent ?: 0) >= 70) c.good else c.ink2)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        w.days.forEachIndexed { i, s ->
                            val bg = when (s) {
                                DayStatus.DONE -> c.good
                                DayStatus.TINY, DayStatus.PARTIAL -> c.goodBg
                                DayStatus.MISSED -> c.warnBg
                                DayStatus.SKIPPED -> c.surface2
                                else -> Color.Transparent
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(bg)
                                    .border(1.dp, if (s == DayStatus.PENDING) c.accent else c.line, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                T(
                                    WeekHeads[i], 11, FontWeight.SemiBold,
                                    if (s == DayStatus.DONE) c.surface else if (s == DayStatus.OFF) c.muted.copy(alpha = 0.5f) else c.ink2,
                                )
                            }
                            if (w.counted) T(if (w.times[i] > 0) w.times[i].fa() else "·", 12, FontWeight.Bold, if (w.times[i] > 0) c.good else c.muted)
                            }
                        }
                    }
                    if (w.counted) T(
                        listOfNotNull(
                            "این هفته ${w.total.fa()} بار",
                            "میانگین روزی ${fmtDecimal(w.average)}",
                            w.usualTime?.let { "بیشتر ${it.label}" },
                        ).joinToString(" · "),
                        12, color = c.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(app: AppState, week: LocalDate) {
    val c = G.c
    val saved = Store.data.reviews[week]
    var editing by remember(week, saved) { mutableStateOf(false) }

    if (saved != null && !editing) {
        GCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved.rating > 0) T("حس من از هفته: ${com.kassa.habit.data.MoodFaces[saved.rating - 1]}", 14, FontWeight.SemiBold)
                listOf("✅ چه چیزی جواب داد" to saved.wentWell, "🚧 چه چیزی مانع شد" to saved.obstacle, "🎯 تغییر هفته‌ی بعد" to saved.change)
                    .filter { it.second.isNotBlank() }
                    .forEach { (label, text) ->
                        Column {
                            T(label, 12, FontWeight.SemiBold, c.muted)
                            T(text, 14)
                        }
                    }
                GhostButton("ویرایش مرور", { editing = true }, Modifier.fillMaxWidth())
            }
        }
        return
    }

    var wentWell by remember(week, saved) { mutableStateOf(saved?.wentWell.orEmpty()) }
    var obstacle by remember(week, saved) { mutableStateOf(saved?.obstacle.orEmpty()) }
    var change by remember(week, saved) { mutableStateOf(saved?.change.orEmpty()) }
    var rating by remember(week, saved) { mutableIntStateOf(saved?.rating ?: 0) }

    GCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("چند دقیقه با خودت. صادقانه و بدون سرزنش.", 13, color = c.ink2)
            MoodPicker("در کل، حست از این هفته؟", rating) { rating = it }
            GField("✅ چه چیزی جواب داد؟", wentWell, { wentWell = it }, placeholder = "کجا از خودت راضی بودی؟", singleLine = false)
            GField("🚧 چه چیزی مانع شد؟", obstacle, { obstacle = it }, placeholder = "کدام موقعیت سخت‌تر بود؟", singleLine = false)
            GField("🎯 فقط یک تغییر برای هفته‌ی بعد", change, { change = it }, placeholder = "کوچک و مشخص", singleLine = false)
            PrimaryButton(if (saved == null) "ثبت مرور · +${Points.REVIEW.fa()}" else "ذخیره") {
                if (wentWell.isBlank() && obstacle.isBlank() && change.isBlank()) {
                    app.toast("دست‌کم به یکی از سه سؤال جواب بده")
                    return@PrimaryButton
                }
                Store.saveReview(Review(week, wentWell.trim(), obstacle.trim(), change.trim(), rating))
                editing = false
                if (saved == null) {
                    Celebrate.burst()
                    app.toast("مرور ثبت شد. تمرکزت بالای صفحه‌ی امروز می‌ماند 🎯")
                }
            }
            if (saved != null) GhostButton("انصراف", { editing = false }, Modifier.fillMaxWidth())
        }
    }
}
