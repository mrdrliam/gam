package com.kassa.habit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.AllDays
import com.kassa.habit.data.DaysChange
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Store
import com.kassa.habit.data.Template
import com.kassa.habit.data.Templates
import com.kassa.habit.data.fa
import com.kassa.habit.data.fmtClock
import com.kassa.habit.notify.Reminders
import com.kassa.habit.ui.theme.G

private const val SOFT_LIMIT = 4

// ------------------------------------------------------------------ choose what to add

@Composable
fun AddSheet(app: AppState, startKind: Kind, onDismiss: () -> Unit) {
    val c = G.c
    var kind by remember { mutableStateOf(startKind) }
    GSheet("رفتار تازه", onDismiss) { close ->
        Segmented(listOf("می‌خواهم کم کنم", "می‌خواهم بسازم"), if (kind == Kind.QUIT) 0 else 1, { kind = if (it == 0) Kind.QUIT else Kind.BUILD })
        val taken = Store.data.activeHabits.map { it.title }.toSet()
        val count = Store.data.activeHabits.count { it.kind == kind }
        if (count >= SOFT_LIMIT) NoteCard(
            "🐢", "آرام‌تر، ماندگارتر",
            "الان ${count.fa()} مورد داری. تمرکز روی چند رفتار معمولاً نتیجه‌ی بهتری از شروع همه با هم دارد.",
        )
        PrimaryButton("✏️ خودم می‌نویسم") {
            close()
            app.editing = Store.newHabit(kind, "", if (kind == Kind.BUILD) "🎯" else "😟")
        }
        T("یا از این‌ها شروع کن:", 13, FontWeight.SemiBold, c.ink2)
        Templates.all.filter { it.kind == kind && it.title !in taken }.forEach { t ->
            TemplateRow(t, selected = false) {
                close()
                app.editing = Store.fromTemplate(t)
            }
        }
    }
}

@Composable
fun TemplateRow(t: Template, selected: Boolean, onClick: () -> Unit) {
    val c = G.c
    GCard(onClick = onClick, bg = if (selected) c.calmBg else c.surface) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiBadge(t.emoji, if (selected) Tone.Calm else Tone.Neutral, 40.dp)
            Column(Modifier.weight(1f)) {
                T(t.title, 15, FontWeight.SemiBold)
                val sub = listOfNotNull(
                    if (t.target > 1) "روزی ${t.target.fa()} بار" else null, t.why.ifBlank { t.cue }.ifBlank { null },
                ).joinToString(" · ")
                if (sub.isNotBlank()) T(sub, 12, color = c.muted, maxLines = 1)
            }
            if (selected) T("✓", 18, FontWeight.Bold, c.calm)
        }
    }
}

// ------------------------------------------------------------------ the form

@Composable
fun HabitFormSheet(app: AppState, draft: Habit, onDismiss: () -> Unit) {
    val c = G.c
    val ctx = LocalContext.current
    val data = Store.data
    val existing = data.habit(draft.id)
    val build = draft.kind == Kind.BUILD

    var title by remember(draft.id) { mutableStateOf(draft.title) }
    var emoji by remember(draft.id) { mutableStateOf(draft.emoji) }
    var why by remember(draft.id) { mutableStateOf(draft.why) }
    var cue by remember(draft.id) { mutableStateOf(draft.cue) }
    var tiny by remember(draft.id) { mutableStateOf(draft.tiny) }
    var target by remember(draft.id) { mutableIntStateOf(draft.target) }
    var plan by remember(draft.id) { mutableStateOf(draft.plan) }
    var replacement by remember(draft.id) { mutableStateOf(draft.replacement) }
    var replacementId by remember(draft.id) { mutableStateOf(draft.replacementId) }
    var days by remember(draft.id) { mutableStateOf(draft.schedule.lastOrNull()?.days ?: AllDays) }
    var limit by remember(draft.id) { mutableStateOf(draft.weeklyLimit) }
    var remindAt by remember(draft.id) { mutableStateOf(draft.remindAt) }
    var tried by remember(draft.id) { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    // a ready-made pairing: offer to create the good habit that replaces this behaviour
    val paired = remember(draft.id) {
        val template = Templates.all.firstOrNull { it.kind == Kind.QUIT && it.title == draft.title }
        Templates.byKey(template?.pairedWith)
    }
    var createPaired by remember(draft.id) { mutableStateOf(false) }

    val sheetTitle = if (existing != null) "ویرایش" else if (build) "عادت خوب تازه" else "رفتاری که کم می‌کنم"

    GSheet(sheetTitle, onDismiss) { close ->
        GField(
            "نام", title, { title = it.take(60) },
            placeholder = if (build) "مثلاً مطالعه" else "مثلاً حرف زدن بدون فکر",
            error = tried && title.isBlank(), hint = if (tried && title.isBlank()) "یک نام بنویس" else null,
        )
        EmojiPicker(emoji) { emoji = it }
        GField("چرا برایم مهم است؟", why, { why = it }, placeholder = "دلیلی که روزهای سخت یادت بیاورد", singleLine = false)

        if (build) {
            GField("کی و کجا؟", cue, { cue = it }, placeholder = "بعد از … ، در …")
            TargetPicker(target) { target = it }
            // for a counted habit one single time already is the small version
            if (target == 1) GField("نسخه‌ی کوچک", tiny, { tiny = it.take(40) }, placeholder = "مثلاً ۲ صفحه", hint = "برای روزهای سخت؛ آن‌قدر آسان که نشود نه گفت")
        } else {
            GField("موقعیت پرخطر", cue, { cue = it }, placeholder = "معمولاً کی پیش می‌آید؟")
            GField("برنامه‌ی «اگر، آنگاه»", plan, { plan = it }, placeholder = "اگر …، آنگاه …", singleLine = false)
            GField("به‌جایش چه می‌کنم؟", replacement, { replacement = it }, placeholder = "یک رفتار جایگزین آسان")

            val goods = data.activeHabits.filter { it.kind == Kind.BUILD }
            val canCreate = existing == null && paired != null && goods.none { it.title == paired.title }
            if (goods.isNotEmpty() || canCreate) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("عادت خوبی که جایش را می‌گیرد", 13, FontWeight.SemiBold, c.ink2)
                ChipFlow {
                    goods.forEach { h ->
                        GChip("${h.emoji} ${h.title}", replacementId == h.id, {
                            replacementId = if (replacementId == h.id) null else h.id
                            createPaired = false
                        })
                    }
                    if (canCreate) GChip("+ بساز: ${paired!!.title}", createPaired, {
                        createPaired = !createPaired
                        if (createPaired) replacementId = null
                    })
                }
            }
        }

        if (build) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("در چه روزهایی؟", 13, FontWeight.SemiBold, c.ink2)
            DayPicker(days) { days = it }
        }

        if (!build) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("هدف هفتگی: حداکثر چند بار؟", 13, FontWeight.SemiBold, c.ink2)
            ChipFlow {
                GChip("بدون سقف", limit == null, { limit = null })
                listOf(0, 1, 2, 3, 5, 7, 10).forEach { n ->
                    GChip(if (n == 0) "هیچ" else n.fa(), limit == n, { limit = n })
                }
            }
            T("هدفِ کمی بهتر از هفته‌ی قبل، شدنی‌تر از «هیچ‌وقت» است.", 12, color = c.muted)
        }

        GCard {
            SwitchRow(
                "یادآور",
                if (build) "برای انجام عادت" else "پیش از ساعت پرخطر، برنامه‌ات را نشان می‌دهد",
                remindAt != null,
            ) { on ->
                if (on) {
                    remindAt = 18 * 60
                    pickTime = true
                    if (!Reminders.canNotify(ctx)) app.requestNotif()
                } else remindAt = null
            }
            remindAt?.let { m ->
                Divider()
                Row(Modifier.fillMaxWidth().clickable { pickTime = true }.padding(16.dp)) {
                    T("ساعت", 14, modifier = Modifier.weight(1f))
                    T(fmtClock(m), 15, FontWeight.Bold, c.accent)
                }
                // a behaviour has no planned days of its own: they only say when to remind
                if (!build) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("در چه روزهایی؟", 13, FontWeight.SemiBold, c.ink2)
                    DayPicker(days) { days = it }
                }
            }
        }

        PrimaryButton(if (existing != null) "ذخیره" else "اضافه کن") {
            if (title.isBlank()) {
                tried = true
                app.toast("نام رفتار خالی است")
                return@PrimaryButton
            }
            if (days.isEmpty() && (build || remindAt != null)) {
                app.toast("دست‌کم یک روز را انتخاب کن")
                return@PrimaryButton
            }
            var linked = replacementId
            if (createPaired && paired != null) {
                val good = Store.fromTemplate(paired)
                Store.saveHabit(good)
                linked = good.id
            }
            if (days.isEmpty()) days = AllDays
            // changing the days starts today, so earlier days keep the plan they had
            val old = draft.schedule
            val goal = if (build) target else 1
            val schedule = when {
                existing == null -> listOf(DaysChange(draft.created, days, goal))
                old.lastOrNull()?.let { it.days == days && it.target == goal } == true -> old
                else -> old.filter { it.from.isBefore(Store.today) } + DaysChange(Store.today, days, goal)
            }
            // a replacement written in words is filled in from the linked habit when left empty
            val words = replacement.trim().ifBlank { Store.data.habit(linked)?.title.orEmpty() }
            Store.saveHabit(
                draft.copy(
                    title = title.trim(), emoji = emoji, why = why.trim(), cue = cue.trim(),
                    tiny = if (build) tiny.trim() else "", plan = if (build) "" else plan.trim(),
                    replacement = if (build) "" else words, replacementId = if (build) null else linked,
                    schedule = schedule, weeklyLimit = if (build) null else limit, remindAt = remindAt,
                )
            )
            close()
            if (existing == null) app.toast("اضافه شد. قدم اول را برداشتی 🌱")
        }
    }

    if (pickTime) TimeDialog("ساعت یادآور", remindAt ?: 18 * 60, { pickTime = false }) {
        remindAt = it
        pickTime = false
    }
}

private const val MAX_TARGET = 50

/** Once a day, or counted several times a day towards a target. */
@Composable
private fun TargetPicker(target: Int, onChange: (Int) -> Unit) {
    val c = G.c
    // the number survives switching to "once" and back
    var several by remember { mutableIntStateOf(if (target > 1) target else 3) }
    fun set(n: Int) {
        several = n.coerceIn(2, MAX_TARGET)
        onChange(several)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T("چند بار در روز؟", 13, FontWeight.SemiBold, c.ink2)
        Segmented(listOf("یک بار", "چند بار"), if (target > 1) 1 else 0, { onChange(if (it == 0) 1 else several) })
        if (target > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("−", Tone.Neutral, Modifier.width(56.dp)) { set(target - 1) }
                T("هدف: ${target.fa()} بار در روز", 15, FontWeight.Bold, modifier = Modifier.weight(1f), align = TextAlign.Center)
                ActionButton("+", Tone.Calm, Modifier.width(56.dp)) { set(target + 1) }
            }
            T("هر بار که انجامش دادی «+۱» بزن؛ مثلاً برای هر لیوان آب.", 12, color = c.muted)
        }
    }
}

@Composable
private fun DayPicker(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val c = G.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WeekHeads.forEachIndexed { i, label ->
            val on = i in selected
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (on) c.primary else c.surface2)
                    .clickable { onChange(if (on) selected - i else selected + i) },
                contentAlignment = Alignment.Center,
            ) { T(label, 15, FontWeight.Bold, if (on) c.onPrimary else c.muted) }
        }
    }
}

@Composable
private fun EmojiPicker(selected: String, onPick: (String) -> Unit) {
    val c = G.c
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EmojiBadge(selected, Tone.Calm)
            T("نشانه", 13, FontWeight.SemiBold, c.ink2, Modifier.weight(1f))
            T(if (open) "بستن" else "تغییر", 13, FontWeight.SemiBold, c.accent, Modifier.padding(6.dp))
        }
        if (open) ChipFlow {
            Templates.emojis.forEach { e ->
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(c.surface2)
                        .border(2.dp, if (e == selected) c.calm else Color.Transparent, CircleShape)
                        .clickable { onPick(e); open = false },
                    contentAlignment = Alignment.Center,
                ) { T(e, 22) }
            }
        }
    }
}
