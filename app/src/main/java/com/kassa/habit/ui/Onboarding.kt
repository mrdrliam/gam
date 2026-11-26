package com.kassa.habit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Store
import com.kassa.habit.data.Templates
import com.kassa.habit.data.fa
import com.kassa.habit.notify.Reminders
import com.kassa.habit.ui.theme.G

/** First run: a short welcome, what to reduce, and what to build in its place. */
@Composable
fun Onboarding(app: AppState) {
    val c = G.c
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    // template keys, comma separated so they survive a configuration change
    var quitKeys by rememberSaveable { mutableStateOf("") }
    var buildKeys by rememberSaveable { mutableStateOf("") }
    val quit = quitKeys.split(',').filter { it.isNotEmpty() }
    val build = buildKeys.split(',').filter { it.isNotEmpty() }
    fun List<String>.toggled(key: String) = (if (key in this) this - key else this + key).joinToString(",")

    BackHandler(enabled = step > 0) { step-- }

    fun finish() {
        val goods = build.mapNotNull { Templates.byKey(it) }.map { it.key to Store.fromTemplate(it) }
        goods.forEach { Store.saveHabit(it.second) }
        quit.mapNotNull { Templates.byKey(it) }.forEach { t ->
            val linked = goods.firstOrNull { it.first == t.pairedWith }?.second?.id
            Store.saveHabit(Store.fromTemplate(t).copy(replacementId = linked))
        }
        Store.updateSettings { it.copy(onboarded = true, name = name.trim()) }
        if (!Reminders.canNotify(ctx)) app.requestNotif()
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        key(step) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> {
                        item {
                            Column(
                                Modifier.fillMaxWidth().heroBackground().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                T("🌱", 52)
                                T("گام", 32, FontWeight.Bold, Color(0xFFFFC77A))
                                T("هر روز کمی بهتر از دیروز", 15, color = Color.White)
                            }
                        }
                        item { NoteCard("🔍", "بشناس", "هر وقت رفتاری که دوست نداری پیش آمد، با یک لمس ثبتش کن و ببین کی و چرا رخ می‌دهد.") }
                        item { NoteCard("🔁", "جایگزین کن", "برای هر رفتار یک برنامه و یک عادت خوب جایگزین بگذار و وقت وسوسه مکث کن.") }
                        item { NoteCard("📈", "پایش کن", "هر هفته پیشرفتت را ببین، امتیاز بگیر و فقط یک چیز را بهتر کن.") }
                        item { GField("چه صدایت کنم؟", name, { name = it.take(30) }, placeholder = "نامت (اختیاری)") }
                    }
                    1 -> {
                        item {
                            T("می‌خواهی کدام رفتار را کم کنی؟", 22, FontWeight.Bold)
                            T("یکی دو مورد کافی است. بعداً می‌توانی تغییرشان دهی یا مورد دلخواه بنویسی.", 13, color = c.muted)
                        }
                        items(Templates.all.filter { it.kind == Kind.QUIT }) { t ->
                            TemplateRow(t, t.key in quit) {
                                quitKeys = quit.toggled(t.key)
                                // offer the matching good habit along with it
                                val pair = t.pairedWith
                                if (pair != null) {
                                    val stillWanted = quit.any { it != t.key && Templates.byKey(it)?.pairedWith == pair }
                                    buildKeys = if (t.key !in quit) (build + pair).distinct().joinToString(",")
                                    else if (!stillWanted) (build - pair).joinToString(",") else buildKeys
                                }
                            }
                        }
                    }
                    else -> {
                        item {
                            T("کدام عادت‌های خوب را بسازیم؟", 22, FontWeight.Bold)
                            T("جایگزین‌های پیشنهادی از قبل انتخاب شده‌اند. با دو یا سه عادت شروع کن.", 13, color = c.muted)
                        }
                        if (build.size > 3) item {
                            NoteCard("🐢", "${build.size.fa()} عادت انتخاب شده", "شروع سبک ماندگارتر است؛ اگر می‌توانی کمترش کن.")
                        }
                        val picked = quit.mapNotNull { Templates.byKey(it)?.pairedWith }.toSet()
                        items(Templates.all.filter { it.kind == Kind.BUILD }.sortedBy { if (it.key in picked) 0 else 1 }, key = { it.key }) { t ->
                            TemplateRow(t, t.key in build) { buildKeys = build.toggled(t.key) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) GhostButton("قبلی", { step-- })
            Box(Modifier.weight(1f)) {
                PrimaryButton(
                    when {
                        step == 0 -> "شروع کنیم"
                        step == 1 -> if (quit.isEmpty()) "فعلاً هیچ‌کدام" else "ادامه"
                        else -> "بزن بریم 🚀"
                    }
                ) { if (step < 2) step++ else finish() }
            }
        }
    }
}
