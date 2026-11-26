package com.kassa.habit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Coach
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Log
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Points
import com.kassa.habit.data.Store
import com.kassa.habit.data.TimeBand
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.countLabel
import com.kassa.habit.data.countOn
import com.kassa.habit.data.dayLabel
import com.kassa.habit.data.fa
import com.kassa.habit.data.fmtClock
import com.kassa.habit.data.timesOn
import com.kassa.habit.ui.theme.G
import java.time.LocalDate

fun logLabel(type: LogType): String = when (type) {
    LogType.DONE -> "انجام شد"
    LogType.TINY -> "نسخه‌ی کوچک"
    LogType.SKIP -> "امروز نه"
    LogType.SLIP -> "اتفاق افتاد"
    LogType.RESIST -> "مقاومت کردم"
}

fun logTone(type: LogType): Tone = when (type) {
    LogType.DONE -> Tone.Good
    LogType.TINY -> Tone.Calm
    LogType.SKIP -> Tone.Neutral
    LogType.SLIP -> Tone.Slip
    LogType.RESIST -> Tone.Good
}

/** Suggestions plus whatever the user typed before, most used first. */
private fun suggestions(base: List<String>, used: List<String>): List<String> {
    val counts = used.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
    return (base + counts.keys).distinct().sortedByDescending { counts[it] ?: 0 }
}

// ------------------------------------------------------------------ quick log

@Composable
fun QuickLogSheet(app: AppState, onDismiss: () -> Unit) {
    val data = Store.data
    val today = Store.today
    GSheet("ثبت سریع", onDismiss) { close ->
        val habits = data.activeHabits
        if (habits.any { it.kind == Kind.QUIT }) PrimaryButton("🌊 وسوسه دارم · ۹۰ ثانیه مکث", tone = Tone.Calm) {
            close()
            app.pause = ""
        }
        // what can be reduced comes first: it is what needs catching in the moment
        habits.sortedBy { if (it.kind == Kind.QUIT) 0 else 1 }.forEach { h ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EmojiBadge(h.emoji, Tone.Neutral, 38.dp)
                    T(h.title, 15, FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    if (h.kind == Kind.BUILD) {
                        val s = buildStatus(data, h, today, today)
                        if (h.countedOn(today)) {
                            Pill(
                                countLabel(timesOn(data, h.id, today), h.targetOn(today)),
                                if (s == DayStatus.DONE) Tone.Good else Tone.Neutral,
                            )
                            ActionButton("+۱", Tone.Good) { close(); app.setStatus(h, today, LogType.DONE) }
                        } else if (s == DayStatus.DONE || s == DayStatus.TINY) Pill("✓ انجام شده", Tone.Good)
                        else ActionButton("✓ انجام شد", Tone.Good) { close(); app.setStatus(h, today, LogType.DONE) }
                    }
                }
                if (h.kind == Kind.QUIT) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("✋ مقاومت کردم", Tone.Good, Modifier.weight(1f)) { close(); app.resist(h, today) }
                    ActionButton("اتفاق افتاد", Tone.Slip, Modifier.weight(1f)) { close(); app.slip(h, today) }
                }
            }
            Divider()
        }
        GhostButton("+ رفتار یا عادت تازه", { close(); app.adding = Kind.QUIT }, Modifier.fillMaxWidth(), color = G.c.accent)
    }
}

// ------------------------------------------------------------------ details of one log

@Composable
fun LogDetailsSheet(app: AppState, log: Log, onDismiss: () -> Unit) {
    val c = G.c
    val data = Store.data
    val habit = data.habit(log.habitId) ?: return
    val slip = log.type == LogType.SLIP
    val skip = log.type == LogType.SKIP

    var mood by remember(log.id) { mutableIntStateOf(log.mood) }
    var triggers by remember(log.id) { mutableStateOf(log.triggers) }
    var place by remember(log.id) { mutableStateOf(log.place) }
    var people by remember(log.id) { mutableStateOf(log.people) }
    var note by remember(log.id) { mutableStateOf(log.note) }
    var nextPlan by remember(log.id) { mutableStateOf(log.nextPlan) }
    var minute by remember(log.id) { mutableIntStateOf(log.minute) }
    var keepPlan by remember(log.id) { mutableStateOf(false) }

    val title = when (log.type) {
        LogType.SLIP -> "ثبت شد؛ آفرین که صادق بودی"
        LogType.RESIST -> "عالی! چه چیزی کمک کرد؟"
        else -> "جزئیات ثبت"
    }

    GSheet(title, onDismiss) { close ->
        T("${habit.emoji} ${habit.title} · ${logLabel(log.type)} · ${dayLabel(log.date, Store.today)}" + if (minute >= 0) " · ${fmtClock(minute)}" else "", 13, color = c.muted)
        if (slip) T("همه‌چیز اختیاری است. هر چه بیشتر بنویسی، الگوی خودت را بهتر می‌شناسی.", 13, color = c.ink2)

        if (log.type == LogType.SLIP || log.type == LogType.RESIST || skip) {
            val all = data.logs.filter { (it.type == LogType.SKIP) == skip }.flatMap { it.triggers }
            TagPicker(
                if (skip) "چه چیزی مانع شد؟" else "چه چیزی زمینه‌اش بود؟",
                suggestions(if (skip) Coach.skipReasons else Coach.triggers, all),
                triggers, { triggers = it },
            )
        }
        MoodPicker("حالت چطور بود؟", mood) { mood = it }

        if (!skip && log.type != LogType.DONE && log.type != LogType.TINY) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("چه وقتی بود؟", 13, FontWeight.SemiBold, c.ink2)
                ChipFlow {
                    TimeBand.entries.forEach { band ->
                        GChip(band.label, TimeBand.of(minute) == band, { if (TimeBand.of(minute) != band) minute = band.sample })
                    }
                }
            }
            TagPicker("کجا بودی؟", suggestions(Coach.places, data.logs.map { it.place }), setOfNotNull(place.ifBlank { null }),
                { place = it.firstOrNull().orEmpty() }, multi = false)
            TagPicker("با چه کسی؟", suggestions(Coach.companions, data.logs.map { it.people }), setOfNotNull(people.ifBlank { null }),
                { people = it.firstOrNull().orEmpty() }, multi = false)
        }

        GField("یادداشت", note, { note = it }, placeholder = if (slip) "قبلش چه شد؟" else "هر چه دوست داری", singleLine = false)

        if (slip) {
            val tip = triggers.firstNotNullOfOrNull { Coach.tipFor(it) }
            if (habit.plan.isNotBlank()) NoteCard("🗺️", "برنامه‌ای که داشتی", habit.plan)
            if (tip != null) NoteCard("💡", "پیشنهاد", tip)
            GField("دفعه‌ی بعد چه می‌کنم؟", nextPlan, { nextPlan = it }, placeholder = "اگر …، آنگاه …", singleLine = false)
            if (nextPlan.isNotBlank() && nextPlan.trim() != habit.plan) SwitchRow(
                "برنامه‌ی اصلی من شود", "روی کارت و یادآور این رفتار نشان داده می‌شود", keepPlan,
            ) { keepPlan = it }
        }
        if (log.type == LogType.RESIST) {
            GField("چه کاری جواب داد؟", nextPlan, { nextPlan = it }, placeholder = "مثلاً یک نفس عمیق کشیدم", singleLine = false)
        }

        PrimaryButton("ذخیره") {
            Store.updateLog(
                log.copy(
                    mood = mood, triggers = triggers, place = place, people = people,
                    note = note.trim(), nextPlan = nextPlan.trim(), minute = minute,
                )
            )
            if (keepPlan && nextPlan.isNotBlank()) {
                Store.data.habit(habit.id)?.let { Store.saveHabit(it.copy(plan = nextPlan.trim())) }
            }
            close()
        }
        GhostButton("حذف این ثبت", {
            val undo = Store.removeLog(log.id)
            close()
            app.toast("ثبت حذف شد", "بازگردانی", undo)
        }, Modifier.fillMaxWidth(), color = c.slip)
    }
}

// ------------------------------------------------------------------ skip

@Composable
fun SkipSheet(app: AppState, habit: Habit, date: LocalDate, onDismiss: () -> Unit) {
    var reasons by remember { mutableStateOf(emptySet<String>()) }
    GSheet("امروز نه · ${habit.title}", onDismiss) { close ->
        T("اشکالی ندارد. دانستن دلیلش کمک می‌کند دفعه‌ی بعد آسان‌تر شود.", 13, color = G.c.ink2)
        val used = Store.data.logs.filter { it.type == LogType.SKIP }.flatMap { it.triggers }
        TagPicker("چه چیزی مانع شد؟", suggestions(Coach.skipReasons, used), reasons, { reasons = it })
        reasons.firstNotNullOfOrNull { Coach.tipFor(it) }?.let { NoteCard("💡", "پیشنهاد", it) }
        if (habit.tiny.isNotBlank() && !habit.countedOn(date)) PrimaryButton("به‌جایش: ${habit.tiny}", tone = Tone.Calm) {
            close()
            app.setStatus(habit, date, LogType.TINY)
        }
        PrimaryButton("ثبت «امروز نه»") {
            val undo = Store.setStatus(habit.id, date, LogType.SKIP, reasons)
            close()
            app.toast("ثبت شد. فردا فرصت تازه‌ای است.", "بازگردانی", undo)
        }
    }
}

// ------------------------------------------------------------------ close the day

@Composable
fun CloseDaySheet(app: AppState, date: LocalDate, onDismiss: () -> Unit) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    val old = data.days[date]
    var mood by remember(date) { mutableIntStateOf(old?.mood ?: 0) }
    var win by remember(date) { mutableStateOf(old?.win.orEmpty()) }
    var note by remember(date) { mutableStateOf(old?.note.orEmpty()) }

    GSheet("بستن ${if (date == today) "امروز" else dayLabel(date, today)}", onDismiss) { close ->
        val build = data.habits.filter { it.kind == Kind.BUILD && it.listedOn(date, today) && it.scheduledOn(date) }
        val waiting = build.filter {
            buildStatus(data, it, date, today).let { s ->
                s == DayStatus.PENDING || s == DayStatus.MISSED || s == DayStatus.PARTIAL
            }
        }
        if (waiting.isNotEmpty()) {
            T("این‌ها هنوز کامل نشده‌اند:", 13, FontWeight.SemiBold, c.ink2)
            waiting.forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val counted = h.countedOn(date)
                    val times = timesOn(data, h.id, date)
                    Column(Modifier.weight(1f)) {
                        T("${h.emoji} ${h.title}", 14, maxLines = 1)
                        if (counted) T("${times.fa()} از ${h.targetOn(date).fa()}", 12, color = c.muted)
                    }
                    if (counted) {
                        ActionButton("+۱", Tone.Good) { Store.setStatus(h.id, date, LogType.DONE) }
                        if (times > 0) ActionButton("−۱", Tone.Neutral) { Store.removeLastTime(h.id, date) }
                    } else {
                        ActionButton("✓ شد", Tone.Good) { Store.setStatus(h.id, date, LogType.DONE) }
                        if (h.tiny.isNotBlank()) ActionButton("کوچک", Tone.Calm) { Store.setStatus(h.id, date, LogType.TINY) }
                    }
                }
            }
        }

        val quit = data.habits.filter { it.kind == Kind.QUIT && it.listedOn(date, today) }
        if (quit.isNotEmpty()) {
            T("چیزی از قلم نیفتاده؟", 13, FontWeight.SemiBold, c.ink2)
            quit.forEach { h ->
                val slips = countOn(data, h.id, date, LogType.SLIP)
                val resists = countOn(data, h.id, date, LogType.RESIST)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("${h.emoji} ${h.title}", 14, modifier = Modifier.weight(1f), maxLines = 1)
                        if (resists > 0) Pill("${resists.fa()} مقاومت", Tone.Good)
                        Pill(if (slips == 0) "بدون لغزش" else "${slips.fa()} بار", if (slips == 0) Tone.Good else Tone.Slip)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("+ مقاومت", Tone.Good, Modifier.weight(1f)) { Store.addLog(h.id, date, LogType.RESIST) }
                        ActionButton("+ اتفاق افتاد", Tone.Slip, Modifier.weight(1f)) { app.slip(h, date) }
                    }
                }
            }
        }

        MoodPicker("حال کلی این روز", mood) { mood = it }
        GField("یک موفقیت، هر چند کوچک", win, { win = it }, placeholder = "امروز از چه چیزی راضی‌ام؟", singleLine = false)
        GField("یادداشت", note, { note = it }, placeholder = "اختیاری", singleLine = false)

        val first = old?.closed != true
        PrimaryButton(if (first) "روزم را می‌بندم · +${Points.CLOSE_DAY.fa()}" else "ذخیره") {
            Store.closeDay(date, mood, win, note)
            close()
            if (first) {
                Celebrate.burst()
                app.toast("روز بسته شد. امتیاز این روز: ${Points.ofDay(Store.data, date).fa()} 🌙")
            }
        }
        if (!first) GhostButton("باز کردن دوباره‌ی روز", { Store.reopenDay(date); close() }, Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------ one habit on one day

/** Opened from a habit's calendar: see and fix what happened on a day. */
@Composable
fun DaySheet(app: AppState, habit: Habit, date: LocalDate, onDismiss: () -> Unit) {
    val c = G.c
    val data = Store.data
    val logs = data.logsOn(habit.id, date).sortedBy { it.at }
    GSheet("${habit.emoji} ${dayLabel(date, Store.today)}", onDismiss) { _ ->
        if (!habit.activeOn(date)) {
            T(
                if (date.isBefore(habit.created)) "این رفتار آن روز هنوز اضافه نشده بود." else "این رفتار آن روز در بایگانی بود.",
                14, color = c.ink2,
            )
            return@GSheet
        }
        if (habit.countedOn(date)) {
            val times = timesOn(data, habit.id, date)
            val skipped = logs.any { it.type == LogType.SKIP }
            T("${times.fa()} بار · هدف ${habit.targetOn(date).fa()}", 14, FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("+۱", Tone.Good, Modifier.weight(1f)) { Store.setStatus(habit.id, date, LogType.DONE) }
                if (times > 0) ActionButton("−۱", Tone.Neutral, Modifier.weight(1f)) { Store.removeLastTime(habit.id, date) }
                else ActionButton("امروز نه", Tone.Neutral, Modifier.weight(1f), filled = skipped) {
                    Store.setStatus(habit.id, date, if (skipped) null else LogType.SKIP)
                }
            }
        } else if (habit.kind == Kind.BUILD) {
            val current = logs.firstOrNull()?.type
            T(if (current == null) "ثبتی ندارد" else "وضعیت: ${logLabel(current)}", 14, FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(LogType.DONE, LogType.TINY, LogType.SKIP).forEach { type ->
                    ActionButton(logLabel(type), logTone(type), Modifier.weight(1f), filled = current == type) {
                        Store.setStatus(habit.id, date, if (current == type) null else type)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("+ مقاومت", Tone.Good, Modifier.weight(1f)) { Store.addLog(habit.id, date, LogType.RESIST) }
                ActionButton("+ اتفاق افتاد", Tone.Slip, Modifier.weight(1f)) { app.slip(habit, date) }
            }
            if (logs.isEmpty()) T(
                if (data.isClosed(date)) "روز پاک ✨" else "ثبتی ندارد. اگر پاک بوده، روز را ببند.",
                14, color = c.ink2,
            )
        }
        logs.forEach { LogRow(it, showDate = false) { app.detailsLogId = it.id } }
        if (!data.isClosed(date)) GhostButton("بستن این روز", { app.closeDayFor = date; app.dayEdit = null }, Modifier.fillMaxWidth().padding(top = 4.dp), color = c.accent)
    }
}

@Composable
fun LogRow(log: Log, showDate: Boolean = true, title: String? = null, onClick: () -> Unit) {
    val c = G.c
    GCard(onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(logLabel(log.type), logTone(log.type))
                T(
                    listOfNotNull(
                        title,
                        if (showDate) dayLabel(log.date, Store.today) else null,
                        if (log.minute >= 0) fmtClock(log.minute) else null,
                    ).joinToString(" · "),
                    12, color = c.muted, modifier = Modifier.weight(1f), maxLines = 1,
                )
                T("ویرایش", 12, FontWeight.SemiBold, c.accent)
            }
            val context = (log.triggers.toList() + listOf(log.place, log.people)).filter { it.isNotBlank() }
            if (context.isNotEmpty()) T(context.joinToString("، "), 12, color = c.ink2)
            if (log.note.isNotBlank()) T(log.note, 13)
            if (log.nextPlan.isNotBlank()) T("🗺️ ${log.nextPlan}", 13, color = c.calm)
        }
    }
}
