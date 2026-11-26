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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Coach
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Jalali
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Levels
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Points
import com.kassa.habit.data.Store
import com.kassa.habit.data.buildProgress
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.cleanRun
import com.kassa.habit.data.countLabel
import com.kassa.habit.data.countOn
import com.kassa.habit.data.dayLabel
import com.kassa.habit.data.fa
import com.kassa.habit.data.fmtDate
import com.kassa.habit.data.habitStreak
import com.kassa.habit.data.presenceStreak
import com.kassa.habit.data.timesOn
import com.kassa.habit.data.weekStart
import com.kassa.habit.ui.theme.G
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun TodayScreen(app: AppState, pad: PaddingValues) {
    val data = Store.data
    val today = Store.today
    val date = app.date
    val isToday = date == today
    var picking by remember { mutableStateOf(false) }

    val build = data.habits.filter { h ->
        h.kind == Kind.BUILD && ((h.listedOn(date, today) && h.scheduledOn(date)) || data.logsOn(h.id, date).isNotEmpty())
    }
    val quit = data.habits.filter { h ->
        h.kind == Kind.QUIT && (h.listedOn(date, today) || data.logsOn(h.id, date).isNotEmpty())
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header(date, today) { picking = true } }
        item { WeekStrip(date, today) { app.showDay(it) } }

        if (Store.loadFailed) item {
            GCard(bg = G.c.slipBg) {
                T("فایل داده‌ها خوانده نشد. یک نسخه از آن روی گوشی نگه داشته شده است؛ اگر پشتیبان داری، از تنظیمات بازیابی کن.", 13, FontWeight.SemiBold, G.c.slip, Modifier.padding(14.dp))
            }
        }

        if (!isToday) item {
            GCard(bg = G.c.warnBg, onClick = { app.pickedDate = null }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("در حال ثبت برای ${dayLabel(date, today)}", 13, FontWeight.SemiBold, G.c.warn, Modifier.weight(1f))
                    T("بازگشت به امروز", 13, FontWeight.Bold, G.c.accent)
                }
            }
        }

        item { Hero(date, build) }

        if (data.activeHabits.isEmpty()) {
            item {
                EmptyState(
                    "🌱", "اولین رفتارت را اضافه کن",
                    "یک عادت خوب که می‌خواهی بسازی، یا رفتاری که می‌خواهی کم‌ترش کنی.",
                    "افزودن رفتار",
                ) { app.adding = Kind.QUIT }
            }
        }

        if (!isToday && build.isEmpty() && quit.isEmpty() && data.activeHabits.isNotEmpty()) item {
            EmptyState("🗓️", "برای این روز چیزی نیست", "رفتارهایت بعد از این تاریخ اضافه شده‌اند یا آن روز در برنامه نبوده‌اند.")
        }

        if (isToday) {
            // the newest review wins: this week's if already written, otherwise last week's
            val focus = data.reviews[weekStart(today)]?.change.orEmpty()
                .ifBlank { data.reviews[weekStart(today).minusDays(7)]?.change.orEmpty() }
            if (focus.isNotBlank()) item { NoteCard("🎯", "تمرکز این هفته", focus) }
            if (data.activeHabits.isNotEmpty()) item {
                val message = Coach.today(data, today, Store.hour)
                NoteCard(message.emoji, message.title, message.text)
            }
            reviewDue(today)?.let { week ->
                item {
                    GCard(bg = G.c.calmBg, onClick = { app.pickedWeek = week; app.tab = 2 }) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("🔍", 26)
                            Column(Modifier.weight(1f)) {
                                T("وقت مرور هفتگی است", 14, FontWeight.Bold)
                                T("سه سؤال کوتاه · +${Points.REVIEW.fa()} امتیاز", 12, color = G.c.ink2)
                            }
                            T("شروع", 13, FontWeight.Bold, G.c.accent)
                        }
                    }
                }
            }
        }

        if (build.isNotEmpty()) {
            item { SectionTitle("عادت‌های خوب", Modifier.padding(top = 6.dp), "+ عادت") { app.adding = Kind.BUILD } }
            items(build, key = { "b" + it.id }) { BuildCard(app, it, date) }
        }

        if (quit.isNotEmpty()) {
            item { SectionTitle("در حال کم‌کردن", Modifier.padding(top = 6.dp), "+ رفتار") { app.adding = Kind.QUIT } }
            if (isToday) item {
                GCard(bg = G.c.calmBg, onClick = { app.pause = "" }) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("🌊", 26)
                        Column(Modifier.weight(1f)) {
                            T("وسوسه دارم", 15, FontWeight.Bold, G.c.calm)
                            T("پیش از هر کاری، ۹۰ ثانیه مکث کن", 12, color = G.c.ink2)
                        }
                        T("مکث", 14, FontWeight.Bold, G.c.calm)
                    }
                }
            }
            items(quit, key = { "q" + it.id }) { QuitCard(app, it, date) }
        }

        if (build.isNotEmpty() || quit.isNotEmpty()) item { CloseDayCard(app, date) }
    }

    if (picking) DateDialog(date, { picking = false }) { app.showDay(it); picking = false }
}

/** The week whose review is waiting: this week on Friday, last week on Saturday. */
private fun reviewDue(today: LocalDate): LocalDate? {
    val week = when (today.dayOfWeek) {
        DayOfWeek.FRIDAY -> weekStart(today)
        DayOfWeek.SATURDAY -> weekStart(today).minusDays(7)
        else -> return null
    }
    val data = Store.data
    val hasSomething = data.logs.any { !it.date.isBefore(week) && !it.date.isAfter(week.plusDays(6)) }
    return week.takeIf { hasSomething && it !in data.reviews }
}

@Composable
private fun Header(date: LocalDate, today: LocalDate, onCalendar: () -> Unit) {
    val c = G.c
    val name = Store.data.settings.name
    val greeting = when (Store.hour) {
        in 5..11 -> "صبح بخیر"
        in 12..16 -> "روز بخیر"
        in 17..20 -> "عصر بخیر"
        else -> "شب بخیر"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(if (name.isBlank()) "$greeting 👋" else "$greeting، $name 👋", 20, FontWeight.Bold, maxLines = 1)
            T((if (date == today) "امروز · " else "") + fmtDate(date, weekday = true, year = true), 13, color = c.muted)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).clickable(onClick = onCalendar),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.DateRange, "تقویم", tint = c.primary) }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = G.c
    val start = weekStart(selected)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0..6) {
            val d = start.plusDays(i.toLong())
            val future = d.isAfter(today)
            val sel = d == selected
            val closed = Store.data.isClosed(d)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (sel) c.primary else c.surface)
                    .border(1.dp, if (d == today && !sel) c.accent else c.line, RoundedCornerShape(16.dp))
                    .clickable(enabled = !future) { onPick(d) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fg = if (sel) c.onPrimary else if (future) c.muted.copy(alpha = 0.45f) else c.ink
                T(WeekHeads[i], 11, color = if (sel) c.onPrimary.copy(alpha = 0.75f) else c.muted)
                T(Jalali.toJalali(d).d.fa(), 15, FontWeight.Bold, fg)
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (closed) (if (sel) c.accent2 else c.good) else Color.Transparent))
            }
        }
    }
}

@Composable
private fun Hero(date: LocalDate, build: List<Habit>) {
    val data = Store.data
    val today = Store.today
    val total = remember(data) { Points.total(data) }
    val level = Levels.of(total)
    val presence = remember(data, today) { presenceStreak(data, today) }
    val done = build.count { buildStatus(data, it, date, today).let { s -> s == DayStatus.DONE || s == DayStatus.TINY } }
    val gold = Color(0xFFFFC77A)
    Column(Modifier.fillMaxWidth().heroBackground().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // the ring also moves with every single time of a counted habit
            val ring = build.sumOf {
                if (buildStatus(data, it, date, today) == DayStatus.PARTIAL) buildProgress(data, it, date)
                else if (buildStatus(data, it, date, today).let { s -> s == DayStatus.DONE || s == DayStatus.TINY }) 1.0 else 0.0
            }
            Ring(if (build.isEmpty()) 0f else (ring / build.size).toFloat(), 92.dp, 9.dp, Color.White.copy(alpha = 0.16f), gold) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(if (build.isEmpty()) "—" else "${done.fa()} از ${build.size.fa()}", 17, FontWeight.Bold, Color.White)
                    T("عادت", 11, color = Color.White.copy(alpha = 0.75f))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(if (date == today) "امتیاز امروز" else "امتیاز این روز", 12, color = Color.White.copy(alpha = 0.75f))
                T(Points.ofDay(data, date).fa(), 34, FontWeight.Bold, gold)
                T(
                    if (presence.current > 0) "🔥 ${presence.current.fa()} روز حضور پیوسته" else "هر روز یک ثبت کوچک",
                    13, FontWeight.SemiBold, Color.White,
                )
                if (presence.atRisk) T("امروز سر زدن زنجیره را نگه می‌دارد", 11, color = gold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                T("سطح ${level.number.fa()} · ${level.title}", 13, FontWeight.Bold, Color.White)
                T(
                    level.next?.let { "${(it - total).fa()} تا سطح بعد" } ?: "بالاترین سطح",
                    12, color = Color.White.copy(alpha = 0.75f),
                )
            }
            GProgress(level.progress(total), color = gold, track = Color.White.copy(alpha = 0.16f))
        }
    }
}

@Composable
fun NoteCard(emoji: String, title: String, text: String) {
    GCard {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T(emoji, 24)
            Column(Modifier.weight(1f)) {
                T(title, 14, FontWeight.Bold)
                T(text, 13, color = G.c.ink2)
            }
        }
    }
}

@Composable
private fun BuildCard(app: AppState, h: Habit, date: LocalDate) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    val status = buildStatus(data, h, date, today)
    val streak = remember(data, today) { habitStreak(data, h, today) }
    val done = status == DayStatus.DONE || status == DayStatus.TINY
    val open = status == DayStatus.PENDING || status == DayStatus.MISSED || status == DayStatus.OFF
    val counted = h.countedOn(date)
    val times = timesOn(data, h.id, date)
    val target = h.targetOn(date)

    GCard(onClick = { app.detailId = h.id }, bg = if (done) c.goodBg else c.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmojiBadge(h.emoji, if (done) Tone.Good else Tone.Neutral)
                Column(Modifier.weight(1f)) {
                    T(h.title, 15, FontWeight.SemiBold, maxLines = 1)
                    val sub = when (status) {
                        DayStatus.TINY -> "با نسخه‌ی کوچک انجام شد"
                        DayStatus.DONE -> if (counted) "هدف این روز کامل شد" else "انجام شد"
                        DayStatus.SKIPPED -> "امروز نه" + data.logsOn(h.id, date).flatMap { it.triggers }
                            .joinToString("، ").let { if (it.isBlank()) "" else " · $it" }
                        else -> h.cue.ifBlank { h.why }
                    }
                    if (sub.isNotBlank()) T(sub, 12, color = c.muted, maxLines = 1)
                    if (streak.current > 1) T(
                        "🔥 ${streak.current.fa()} بار پیوسته" + if (streak.atRisk) " · امروز از دستش نده" else "",
                        12, FontWeight.SemiBold, if (streak.atRisk) c.warn else c.accent, maxLines = 1,
                    )
                }
                if (counted) Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (done) c.good else c.calmBg)
                        .clickable(onClickLabel = "یک بار دیگر") { app.setStatus(h, date, LogType.DONE) },
                    contentAlignment = Alignment.Center,
                ) { T("+۱", 18, FontWeight.Bold, if (done) (if (c.dark) Color(0xFF0F0E18) else Color.White) else c.calm) }
                else Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (done) c.good else Color.Transparent)
                        .border(2.dp, if (done) c.good else c.muted.copy(alpha = 0.5f), CircleShape)
                        .clickable { app.setStatus(h, date, if (done) null else LogType.DONE) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check, if (done) "برداشتن ثبت" else "انجام شد",
                        tint = if (done) (if (c.dark) Color(0xFF0F0E18) else Color.White) else c.muted.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            if (counted) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GProgress(
                    times.toFloat() / target, Modifier.weight(1f), color = if (done) c.good else c.calm, height = 9.dp,
                )
                T(countLabel(times, target), 14, FontWeight.Bold, if (done) c.good else c.ink, maxLines = 1)
                if (times > 0) ActionButton("−۱", Tone.Neutral) { app.removeOne(h, date) }
                else if (status != DayStatus.SKIPPED) ActionButton("امروز نه", Tone.Neutral) { app.skipFor = h.id to date }
            }
            else if (open) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (h.tiny.isNotBlank()) ActionButton("👣 ${h.tiny}", Tone.Calm, Modifier.weight(1f)) { app.setStatus(h, date, LogType.TINY) }
                ActionButton("امروز نه", Tone.Neutral, if (h.tiny.isBlank()) Modifier.weight(1f) else Modifier) { app.skipFor = h.id to date }
            }
            if (status == DayStatus.SKIPPED) ActionButton("برداشتن «امروز نه»", Tone.Neutral, Modifier.fillMaxWidth()) { app.setStatus(h, date, null) }
        }
    }
}

@Composable
private fun QuitCard(app: AppState, h: Habit, date: LocalDate) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    val slips = countOn(data, h.id, date, LogType.SLIP)
    val resists = countOn(data, h.id, date, LogType.RESIST)
    val clean = remember(data, today) { cleanRun(data, h, today) }
    GCard(onClick = { app.detailId = h.id }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmojiBadge(h.emoji, if (slips > 0) Tone.Slip else if (resists > 0) Tone.Good else Tone.Neutral)
                Column(Modifier.weight(1f)) {
                    T(h.title, 15, FontWeight.SemiBold, maxLines = 1)
                    val hint = if (h.replacement.isNotBlank()) "به‌جایش: ${h.replacement}" else h.plan
                    if (hint.isNotBlank()) T(hint, 12, color = c.muted, maxLines = 1)
                    if (clean.current > 0) T("✨ ${clean.current.fa()} روز پاک", 12, FontWeight.SemiBold, c.good)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (resists > 0) Pill("${resists.fa()} مقاومت", Tone.Good)
                    if (slips > 0) Pill("${slips.fa()} بار", Tone.Slip)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("✋ مقاومت کردم", Tone.Good, Modifier.weight(1f)) { app.resist(h, date) }
                ActionButton("اتفاق افتاد", Tone.Slip, Modifier.weight(1f)) { app.slip(h, date) }
            }
        }
    }
}

@Composable
private fun CloseDayCard(app: AppState, date: LocalDate) {
    val c = G.c
    val note = Store.data.days[date]
    if (note?.closed == true) {
        GCard(bg = c.goodBg, onClick = { app.closeDayFor = date }) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("✅", 24)
                Column(Modifier.weight(1f)) {
                    T("این روز بسته شده", 14, FontWeight.Bold, c.good)
                    if (note.win.isNotBlank()) T("موفقیت: ${note.win}", 12, color = c.ink2, maxLines = 2)
                }
                T("ویرایش", 13, FontWeight.Bold, c.accent)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton("🌙 بستن روز") { app.closeDayFor = date }
            T("روزهای پاک فقط وقتی روز را ببندی حساب می‌شوند", 12, color = c.muted)
        }
    }
}
