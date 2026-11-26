package com.kassa.habit.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Store
import com.kassa.habit.data.fa
import com.kassa.habit.ui.theme.G
import androidx.core.view.WindowCompat
import com.kassa.habit.ui.theme.HeroDeep
import com.kassa.habit.ui.theme.HeroMid
import com.kassa.habit.ui.theme.HeroTop
import kotlinx.coroutines.delay

private const val PAUSE_SECONDS = 90
private const val IN_MS = 4000
private const val OUT_MS = 6000

private val Gold = Color(0xFFFFC77A)
private val Soft = Color.White.copy(alpha = 0.78f)

/** A full-screen breathing pause between an urge and an action. */
@Composable
fun PauseScreen(app: AppState, habitId: String) {
    val quit = Store.data.activeHabits.filter { it.kind == Kind.QUIT }
    var chosen by remember(habitId) { mutableStateOf(habitId.ifBlank { quit.singleOrNull()?.id.orEmpty() }) }
    val habit = Store.data.habit(chosen)
    val close = { app.pause = null }
    BackHandler(onBack = close)
    LightSystemBarIcons()

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(HeroTop, HeroMid, HeroDeep)))
            .pointerInput(Unit) { },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = close),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "بستن", tint = Color.White) }
            }
            if (habit == null && quit.isNotEmpty()) {
                T("وسوسه‌ی چه چیزی؟", 20, FontWeight.Bold, Color.White)
                quit.forEach { h ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.12f))
                            .clickable { chosen = h.id }.padding(16.dp),
                    ) { T("${h.emoji}  ${h.title}", 16, FontWeight.SemiBold, Color.White) }
                }
            } else {
                Breathing(app, habit, close)
            }
        }
    }
}

/** The pause screen is dark in both themes, so the clock and battery icons turn light while it is open. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    val dark = G.c.dark
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !dark
            controller?.isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun Breathing(app: AppState, habit: Habit?, close: () -> Unit) {
    var left by remember { mutableIntStateOf(PAUSE_SECONDS) }
    var inhale by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            inhale = true
            delay(IN_MS.toLong())
            inhale = false
            delay(OUT_MS.toLong())
        }
    }
    // the screen should not switch off in the middle of a pause
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val pulse = rememberInfiniteTransition(label = "breath")
    val scale by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = IN_MS + OUT_MS
                0.6f at 0 using FastOutSlowInEasing
                1f at IN_MS using FastOutSlowInEasing
                0.6f at IN_MS + OUT_MS
            },
            RepeatMode.Restart,
        ),
        label = "scale",
    )

    T(if (habit == null) "مکث" else "${habit.emoji} ${habit.title}", 18, FontWeight.Bold, Color.White, align = TextAlign.Center)
    Box(Modifier.size(186.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(186.dp).scale(scale).clip(CircleShape).background(Gold.copy(alpha = 0.20f)))
        Box(Modifier.size(142.dp).scale(scale).clip(CircleShape).background(Gold.copy(alpha = 0.28f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(if (inhale) "دم…" else "بازدم…", 24, FontWeight.Bold, Color.White)
            T(if (left > 0) "${left.fa()} ثانیه" else "✓", 14, color = Soft)
        }
    }
    T(
        when {
            left > 60 -> "لازم نیست بجنگی. فقط ببین که میلش هست."
            left > 30 -> "وسوسه مثل موج است: بالا می‌رود و فروکش می‌کند."
            left > 0 -> "آدمی که می‌خواهی باشی الان چه می‌کند؟"
            else -> "مکث کردی. حالا خودت انتخاب می‌کنی."
        },
        15, FontWeight.SemiBold, Gold, align = TextAlign.Center,
    )

    if (habit != null) {
        listOf("چرا برایم مهم است" to habit.why, "برنامه‌ی من" to habit.plan, "به‌جایش" to habit.replacement)
            .filter { it.second.isNotBlank() }
            .forEach { (label, text) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f)).padding(14.dp)) {
                    T(label, 12, color = Soft)
                    T(text, 15, FontWeight.SemiBold, Color.White)
                }
            }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Gold)
                .clickable { close(); app.resist(habit, Store.today) }.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { T("✋ رد شد؛ مقاومت کردم", 16, FontWeight.Bold, Color(0xFF2B1A04)) }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f))
                .clickable { close(); app.slip(habit, Store.today) }.padding(14.dp),
            contentAlignment = Alignment.Center,
        ) { T("انجامش دادم، ثبتش می‌کنم", 14, FontWeight.SemiBold, Soft) }
    } else {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Gold).clickable(onClick = close).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { T("برگشت", 16, FontWeight.Bold, Color(0xFF2B1A04)) }
    }
}
