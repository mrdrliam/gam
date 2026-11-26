package com.kassa.habit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.AllDays
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.LogType
import com.kassa.habit.data.MIN_FOR_PATTERN
import com.kassa.habit.data.QuitStatus
import com.kassa.habit.data.Store
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.cleanRun
import com.kassa.habit.data.countBetween
import com.kassa.habit.data.fa
import com.kassa.habit.data.fmtClock
import com.kassa.habit.data.fmtDate
import com.kassa.habit.data.habitStreak
import com.kassa.habit.data.habitStrength
import com.kassa.habit.data.insightsFor
import com.kassa.habit.data.quitStatus
import com.kassa.habit.data.weekStart
import com.kassa.habit.ui.theme.G

private const val HISTORY_PAGE = 20

@Composable
fun DetailScreen(app: AppState, h: Habit, pad: PaddingValues) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    val build = h.kind == Kind.BUILD
    val insights = remember(data, today, h.id) { insightsFor(data, h, today) }
    var shown by remember(h.id) { androidx.compose.runtime.mutableIntStateOf(HISTORY_PAGE) }
    val history = remember(data, h.id) { data.logsOf(h.id).sortedWith(compareByDescending<com.kassa.habit.data.Log> { it.date }.thenByDescending { it.at }) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "برگشت", { app.detailId = null })
                T("${h.emoji} ${h.title}", 19, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
                RoundIcon(Icons.Filled.Edit, "ویرایش", { app.editing = h })
            }
        }
        if (h.why.isNotBlank()) item { NoteCard("❤️", "چرا برایم مهم است", h.why) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (build) {
                    val streak = remember(data, today) { habitStreak(data, h, today) }
                    val strength = remember(data, today) { habitStrength(data, h, today) }
                    val total = history.count { it.type == LogType.DONE || it.type == LogType.TINY }
                    Stat("قدرت عادت", "${strength.fa()}٪", Tone.Good, Modifier.weight(1f))
                    Stat("زنجیره", streak.current.fa(), Tone.Warn, Modifier.weight(1f))
                    Stat(if (h.target > 1) "کل دفعات" else "کل انجام", total.fa(), Tone.Calm, Modifier.weight(1f))
                } else {
                    val clean = remember(data, today) { cleanRun(data, h, today) }
                    val week = weekStart(today)
                    val slips = countBetween(data, h.id, week, today, LogType.SLIP)
                    val resists = history.count { it.type == LogType.RESIST }
                    Stat("روز پاک", clean.current.fa(), Tone.Good, Modifier.weight(1f))
                    Stat("این هفته", slips.fa(), Tone.Slip, Modifier.weight(1f))
                    Stat("کل مقاومت", resists.fa(), Tone.Calm, Modifier.weight(1f))
                }
            }
        }

        if (!build && !h.archived) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("🌊 وسوسه دارم · مکث", tone = Tone.Calm) { app.pause = h.id }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("✋ مقاومت کردم", Tone.Good, Modifier.weight(1f)) { app.resist(h, today) }
                    ActionButton("اتفاق افتاد", Tone.Slip, Modifier.weight(1f)) { app.slip(h, today) }
                }
            }
        }

        item { PlanCard(app, h) }

        item {
            GCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonthPager(today, { app.dayEdit = h.id to it }) { date ->
                        if (build) when (buildStatus(data, h, date, today)) {
                            DayStatus.DONE -> DayPaint(bg = c.good, fg = c.surface)
                            DayStatus.TINY, DayStatus.PARTIAL -> DayPaint(bg = c.calmBg, fg = c.calm)
                            DayStatus.SKIPPED -> DayPaint(bg = c.surface2, fg = c.muted)
                            DayStatus.MISSED -> DayPaint(bg = c.warnBg, fg = c.warn)
                            else -> DayPaint()
                        } else when (quitStatus(data, h, date, today)) {
                            QuitStatus.SLIPPED -> DayPaint(bg = c.slipBg, fg = c.slip)
                            QuitStatus.CLEAN -> DayPaint(bg = c.good, fg = c.surface)
                            QuitStatus.RESISTED -> DayPaint(bg = c.goodBg, fg = c.good)
                            else -> DayPaint()
                        }
                    }
                    ChipLegend(
                        if (build) listOf(
                            "کامل" to Tone.Good, (if (h.target > 1) "کمتر از هدف" else "نسخه‌ی کوچک") to Tone.Calm,
                            "جا مانده" to Tone.Warn, "امروز نه" to Tone.Neutral,
                        )
                        else listOf("پاک / مقاومت" to Tone.Good, "اتفاق افتاده" to Tone.Slip)
                    )
                    T("برای ثبت یا اصلاح، روی هر روز بزن.", 12, color = c.muted)
                }
            }
        }

        item { SectionTitle("الگوهای تو", Modifier.padding(top = 6.dp)) }
        if (insights.isEmpty()) item {
            val slips = history.count { it.type == (if (build) LogType.SKIP else LogType.SLIP) }
            NoteCard(
                "🔍", "هنوز زود است",
                if (!build && slips < MIN_FOR_PATTERN)
                    "بعد از دست‌کم ${MIN_FOR_PATTERN.fa()} ثبت با جزئیات، الگوها اینجا دیده می‌شوند (تا حالا ${slips.fa()} ثبت)."
                else "هنوز الگوی روشنی دیده نمی‌شود. با ادامه‌ی ثبت و نوشتن زمینه‌ها، تصویر روشن‌تر می‌شود.",
            )
        }
        items(insights) { insight ->
            GCard {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(insight.emoji, 22)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(insight.text, 14, FontWeight.SemiBold)
                        insight.tip?.let { T("💡 $it", 13, color = c.ink2) }
                    }
                }
            }
        }
        if (insights.isNotEmpty()) item {
            T("بر پایه‌ی ۶۰ روز گذشته. این‌ها هم‌زمانی‌اند، نه لزوماً علت.", 12, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }

        if (history.isNotEmpty()) {
            item { SectionTitle("تاریخچه", Modifier.padding(top = 6.dp)) }
            items(history.take(shown), key = { it.id }) { log -> LogRow(log) { app.detailsLogId = log.id } }
            if (history.size > shown) item {
                GhostButton("بیشتر", { shown += HISTORY_PAGE }, Modifier.fillMaxWidth())
            }
        }

        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    if (h.archived) "بازگرداندن از بایگانی" else "بایگانی (تاریخچه می‌ماند)",
                    {
                        Store.setArchived(h.id, !h.archived)
                        app.detailId = null
                        app.toast(if (h.archived) "به فهرست برگشت" else "بایگانی شد")
                    },
                    Modifier.fillMaxWidth(),
                )
                GhostButton("حذف کامل", { confirmDelete = true }, Modifier.fillMaxWidth(), color = c.slip)
            }
        }
    }

    if (confirmDelete) ConfirmDialog(
        "«${h.title}» حذف شود؟",
        "همه‌ی ثبت‌های آن (${history.size.fa()} مورد) و امتیازهایشان هم پاک می‌شود. اگر فقط نمی‌خواهی دنبالش کنی، بایگانی کن.",
        "بله، حذف شود", { confirmDelete = false },
    ) {
        confirmDelete = false
        app.detailId = null
        val undo = Store.deleteHabit(h.id)
        app.toast("حذف شد", "بازگردانی", undo)
    }
}

@Composable
private fun Stat(label: String, value: String, tone: Tone, modifier: Modifier = Modifier) {
    GCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(value, 22, FontWeight.Bold, toneFg(tone))
            T(label, 11, color = G.c.muted, align = TextAlign.Center)
        }
    }
}

@Composable
fun ChipLegend(items: List<Pair<String, Tone>>) {
    ChipFlow { items.forEach { (label, tone) -> Pill(label, tone) } }
}

@Composable
private fun PlanCard(app: AppState, h: Habit) {
    val c = G.c
    val linked = Store.data.habit(h.replacementId)
    val days = h.schedule.lastOrNull()?.days ?: AllDays
    val rows = buildList {
        if (h.kind == Kind.BUILD) {
            if (h.cue.isNotBlank()) add("کی و کجا" to h.cue)
            if (h.target > 1) add("هدف روزانه" to "${h.target.fa()} بار در روز")
            else if (h.tiny.isNotBlank()) add("نسخه‌ی کوچک" to h.tiny)
            add("روزها" to if (days == AllDays) "هر روز" else days.sorted().joinToString("، ") { WeekHeads[it] })
        } else {
            if (h.cue.isNotBlank()) add("موقعیت پرخطر" to h.cue)
            if (h.plan.isNotBlank()) add("اگر، آنگاه" to h.plan)
            if (h.replacement.isNotBlank()) add("به‌جایش" to h.replacement)
            h.weeklyLimit?.let { add("هدف هفتگی" to if (it == 0) "هیچ بار" else "حداکثر ${it.fa()} بار") }
        }
        h.remindAt?.let { add("یادآور" to fmtClock(it)) }
        add("از" to fmtDate(h.created, year = true))
    }
    GCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("برنامه‌ی من", 15, FontWeight.Bold)
            rows.forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(label, 13, color = c.muted)
                    T(value, 13, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }
            if (linked != null) {
                Divider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("جایگزین خوب: ${linked.emoji} ${linked.title}", 13, FontWeight.SemiBold, c.good, Modifier.weight(1f))
                    ActionButton("دیدن", Tone.Good) { app.detailId = linked.id }
                }
            }
        }
    }
}
