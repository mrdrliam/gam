package com.kassa.habit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Store
import com.kassa.habit.data.cleanRun
import com.kassa.habit.data.countBetween
import com.kassa.habit.data.countLabel
import com.kassa.habit.data.fa
import com.kassa.habit.data.habitStreak
import com.kassa.habit.data.habitStrength
import com.kassa.habit.data.timesOn
import com.kassa.habit.data.weekStart
import com.kassa.habit.ui.theme.G

@Composable
fun HabitsScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val data = Store.data
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val list = when (mode) {
        0 -> data.activeHabits.filter { it.kind == Kind.BUILD }
        1 -> data.activeHabits.filter { it.kind == Kind.QUIT }
        else -> data.habits.filter { it.archived }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { T("رفتارهای من", 24, FontWeight.Bold) }
        item { Segmented(listOf("عادت‌های خوب", "در حال کم‌کردن", "بایگانی"), mode, { mode = it }) }
        if (mode < 2) item {
            PrimaryButton(if (mode == 0) "+ عادت خوب تازه" else "+ رفتاری که می‌خواهم کم کنم") {
                app.adding = if (mode == 0) Kind.BUILD else Kind.QUIT
            }
        }
        if (list.isEmpty()) item {
            when (mode) {
                0 -> EmptyState("🌱", "هنوز عادتی نساخته‌ای", "با یک کار کوچک شروع کن که هر روز شدنی باشد.")
                1 -> EmptyState("🔍", "چیزی در این فهرست نیست", "رفتاری را که دوست نداری اینجا بیاور تا اول خوب بشناسی‌اش.")
                else -> EmptyState("🗂️", "بایگانی خالی است", "رفتارهایی که کنار می‌گذاری، با تاریخچه‌شان اینجا می‌مانند.")
            }
        }
        items(list, key = { it.id }) { HabitCard(app, it) }
        if (mode == 1 && list.isNotEmpty()) item {
            T("برای دیدن الگوها روی هر رفتار بزن.", 12, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun HabitCard(app: AppState, h: Habit) {
    val c = G.c
    val data = Store.data
    val today = Store.today
    GCard(onClick = { app.detailId = h.id }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiBadge(h.emoji, if (h.kind == Kind.BUILD) Tone.Good else Tone.Calm)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(h.title, 15, FontWeight.SemiBold, maxLines = 1)
                if (h.archived) {
                    T("در بایگانی", 12, color = c.muted)
                } else if (h.kind == Kind.BUILD) {
                    val streak = remember(data, today) { habitStreak(data, h, today) }
                    T("زنجیره ${streak.current.fa()} · بهترین ${streak.best.fa()}", 12, color = c.muted)
                    if (h.target > 1) T(
                        "امروز ${countLabel(timesOn(data, h.id, today), h.target)}", 12, FontWeight.SemiBold, c.calm,
                    )
                } else {
                    val week = weekStart(today)
                    val slips = countBetween(data, h.id, week, today, LogType.SLIP)
                    val resists = countBetween(data, h.id, week, today, LogType.RESIST)
                    T("این هفته: ${slips.fa()} بار · ${resists.fa()} مقاومت", 12, color = c.muted)
                    h.weeklyLimit?.let {
                        T(
                            if (slips <= it) "در محدوده‌ی هدف (حداکثر ${it.fa()})" else "بیشتر از هدف هفته (${it.fa()})",
                            12, FontWeight.SemiBold, if (slips <= it) c.good else c.slip,
                        )
                    }
                }
            }
            if (!h.archived) {
                if (h.kind == Kind.BUILD) {
                    val strength = remember(data, today) { habitStrength(data, h, today) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(strength / 100f, 52.dp, 6.dp, c.surface2, c.good) {
                            T(strength.fa(), 14, FontWeight.Bold)
                        }
                        T("قدرت", 10, color = c.muted)
                    }
                } else {
                    val clean = remember(data, today) { cleanRun(data, h, today) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(clean.current.fa(), 22, FontWeight.Bold, c.good)
                        T("روز پاک", 10, color = c.muted)
                    }
                }
            }
        }
    }
}
