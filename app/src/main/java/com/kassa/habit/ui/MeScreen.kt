package com.kassa.habit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Badge
import com.kassa.habit.data.Badges
import com.kassa.habit.data.Coach
import com.kassa.habit.data.Levels
import com.kassa.habit.data.LogType
import com.kassa.habit.data.MoodFaces
import com.kassa.habit.data.Points
import com.kassa.habit.data.Store
import com.kassa.habit.data.dayLabel
import com.kassa.habit.data.fa
import com.kassa.habit.data.presenceStreak
import com.kassa.habit.ui.theme.G

@Composable
fun MeScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    val total = remember(data) { Points.total(data) }
    val level = Levels.of(total)
    val badges = remember(data, today) { Badges.all(data, today) }
    val presence = remember(data, today) { presenceStreak(data, today) }
    var selected by remember { mutableStateOf<Badge?>(null) }
    val gold = Color(0xFFFFC77A)
    val soft = Color.White.copy(alpha = 0.76f)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { T("مسیر من", 24, FontWeight.Bold) }
        item {
            Column(
                Modifier.fillMaxWidth().heroBackground().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T("سطح ${level.number.fa()}", 13, color = soft)
                T(level.title, 28, FontWeight.Bold, gold)
                T("${total.fa()} امتیاز", 16, FontWeight.SemiBold, Color.White)
                GProgress(level.progress(total), color = gold, track = Color.White.copy(alpha = 0.16f))
                T(level.next?.let { "${(it - total).fa()} امتیاز تا «${Levels.of(it).title}»" } ?: "به بالاترین سطح رسیده‌ای", 12, color = soft)
                T("بهترین حضور پیوسته: ${presence.best.fa()} روز", 12, color = soft)
            }
        }

        item { SectionTitle("نشان‌ها · ${badges.count { it.earned }.fa()} از ${badges.size.fa()}", Modifier.padding(top = 6.dp)) }
        items(badges.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    GCard(Modifier.weight(1f), onClick = { selected = b }, bg = if (b.earned) c.calmBg else c.surface) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            T(b.emoji, 28, modifier = Modifier.alpha(if (b.earned) 1f else 0.3f))
                            T(b.title, 12, FontWeight.SemiBold, if (b.earned) c.ink else c.muted, align = TextAlign.Center, maxLines = 1)
                            if (b.earned) T("✓", 12, FontWeight.Bold, c.good)
                            else if (b.goal > 1) T("${b.progress.fa()} از ${b.goal.fa()}", 11, color = c.muted)
                            else T("—", 11, color = c.muted)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        item { Spacer(Modifier.padding(2.dp)) }
        item { MenuRow("📔", "دفترچه‌ی من", "موفقیت‌ها، یادداشت‌ها و ثبت‌ها") { app.page = Page.JOURNAL } }
        item { MenuRow("🎓", "درس‌های کوتاه", "اصول عادت‌سازی و خویشتن‌داری") { app.page = Page.LESSONS } }
        item { MenuRow("⚙️", "تنظیمات", "یادآورها، قفل، پشتیبان‌گیری") { app.page = Page.SETTINGS } }
    }

    selected?.let { b ->
        DialogCard({ selected = null }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(b.emoji, 44)
                T(b.title, 17, FontWeight.Bold)
                T(b.how, 14, color = c.ink2, align = TextAlign.Center)
                if (b.earned) Pill("به دست آمده", Tone.Good)
                else if (b.goal > 1) {
                    GProgress(b.progress.toFloat() / b.goal)
                    T("${b.progress.fa()} از ${b.goal.fa()}", 12, color = c.muted)
                }
                GhostButton("باشه", { selected = null }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MenuRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    GCard(onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiBadge(emoji, Tone.Neutral)
            Column(Modifier.weight(1f)) {
                T(title, 15, FontWeight.SemiBold)
                T(subtitle, 12, color = G.c.muted)
            }
        }
    }
}

@Composable
fun PageHeader(app: AppState, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "برگشت", { app.page = null })
        T(title, 20, FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ journal

private const val JOURNAL_PAGE = 14

@Composable
fun JournalPage(app: AppState, pad: PaddingValues) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    var shown by remember { mutableIntStateOf(JOURNAL_PAGE) }
    val days = remember(data) {
        (data.logs.map { it.date } + data.days.values.filter { it.closed }.map { it.date }).distinct().sortedDescending()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader(app, "دفترچه‌ی من") }
        if (days.isEmpty()) item { EmptyState("📔", "دفترچه خالی است", "ثبت‌ها و موفقیت‌های روزانه‌ات اینجا جمع می‌شوند.") }
        days.take(shown).forEach { date ->
            val note = data.days[date]
            item(key = "day$date") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(
                        dayLabel(date, today) + (note?.mood?.takeIf { it > 0 }?.let { "  ${MoodFaces[it - 1]}" } ?: ""),
                        action = "${Points.ofDay(data, date).fa()} امتیاز",
                    ) { app.showDay(date); app.tab = 0; app.page = null }
                    if (!note?.win.isNullOrBlank()) NoteCard("🏅", "موفقیت", note!!.win)
                    if (!note?.note.isNullOrBlank()) NoteCard("📝", "یادداشت", note!!.note)
                }
            }
            // plain "done" ticks without any words are summed up, so what was written stays visible
            val logs = data.logs.filter { it.date == date }.sortedByDescending { it.at }
            val (plain, detailed) = logs.partition {
                (it.type == LogType.DONE || it.type == LogType.TINY) && it.note.isBlank() && it.mood == 0
            }
            if (plain.isNotEmpty()) item(key = "plain$date") {
                T(
                    "✓ " + plain.groupBy { it.habitId }.mapNotNull { (id, times) ->
                        data.habit(id)?.title?.let { if (times.size > 1) "$it (${times.size.fa()} بار)" else it }
                    }.joinToString("، "),
                    13, color = c.good, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            items(detailed, key = { it.id }) { log ->
                LogRow(log, showDate = false, title = data.habit(log.habitId)?.title) { app.detailsLogId = log.id }
            }
        }
        if (days.size > shown) item { GhostButton("روزهای قبل‌تر", { shown += JOURNAL_PAGE }, Modifier.fillMaxWidth()) }
    }
}

// ------------------------------------------------------------------ lessons

@Composable
fun LessonsPage(app: AppState, pad: PaddingValues) {
    val c = G.c
    var open by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader(app, "درس‌های کوتاه") }
        items(Coach.lessons.size) { i ->
            val lesson = Coach.lessons[i]
            GCard(onClick = { open = if (open == i) -1 else i }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        T(lesson.emoji, 22)
                        T(lesson.title, 15, FontWeight.Bold, modifier = Modifier.weight(1f))
                        T(if (open == i) "−" else "+", 20, FontWeight.Bold, c.accent)
                    }
                    if (open == i) T(lesson.body, 14, color = c.ink2)
                }
            }
        }
        item {
            Box(Modifier.padding(8.dp)) {
                T("این برنامه جای روان‌شناس یا درمانگر را نمی‌گیرد. اگر رفتاری زندگی‌ات را سخت کرده، کمک گرفتن از متخصص کار درستی است.", 12, color = c.muted)
            }
        }
    }
}
