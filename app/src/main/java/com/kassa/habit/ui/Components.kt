package com.kassa.habit.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kassa.habit.data.MoodFaces
import com.kassa.habit.data.fa
import com.kassa.habit.ui.theme.G
import com.kassa.habit.ui.theme.HeroDeep
import com.kassa.habit.ui.theme.HeroMid
import com.kassa.habit.ui.theme.HeroTop
import com.kassa.habit.ui.theme.Vazir
import kotlinx.coroutines.launch
import kotlin.random.Random

// ------------------------------------------------------------------ text

@Composable
fun T(
    text: String,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal,
    color: Color = G.c.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = Vazir,
        lineHeight = (size * 1.6f).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

enum class Tone { Good, Slip, Warn, Calm, Neutral }

@Composable
fun toneFg(t: Tone): Color = when (t) {
    Tone.Good -> G.c.good
    Tone.Slip -> G.c.slip
    Tone.Warn -> G.c.warn
    Tone.Calm -> G.c.calm
    Tone.Neutral -> G.c.ink2
}

@Composable
fun toneBg(t: Tone): Color = when (t) {
    Tone.Good -> G.c.goodBg
    Tone.Slip -> G.c.slipBg
    Tone.Warn -> G.c.warnBg
    Tone.Calm -> G.c.calmBg
    Tone.Neutral -> G.c.surface2
}

@Composable
fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(toneBg(tone)).padding(horizontal = 10.dp, vertical = 2.dp)) {
        T(text, 11, FontWeight.SemiBold, toneFg(tone), maxLines = 1)
    }
}

// ------------------------------------------------------------------ surfaces

@Composable
fun GCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bg: Color = G.c.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = G.c
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (!c.dark) Modifier.shadow(2.dp, shape, ambientColor = Color(0x221F1C33), spotColor = Color(0x221F1C33))
                else Modifier
            )
            .clip(shape)
            .background(bg.compositeOver(c.surface))
            .border(1.dp, c.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(G.c.line))
}

/** The deep indigo header card, with a warm "sunrise" glow and soft rings. */
fun Modifier.heroBackground(radius: Dp = 28.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .drawBehind {
        drawRect(Brush.linearGradient(listOf(HeroTop, HeroMid, HeroDeep), start = Offset(size.width, 0f), end = Offset(0f, size.height)))
        val sun = Offset(size.width * 0.08f, size.height * 1.02f)
        drawRect(Brush.radialGradient(listOf(Color(0x55FFC77A), Color.Transparent), center = sun, radius = size.width * 0.7f))
        for (i in 1..4) {
            drawCircle(Color.White.copy(alpha = 0.05f), radius = i * 38.dp.toPx(), center = sun, style = Stroke(1.dp.toPx()))
        }
    }

// ------------------------------------------------------------------ progress

@Composable
fun Ring(progress: Float, size: Dp, stroke: Dp, track: Color, color: Color, content: @Composable () -> Unit = {}) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
            if (p > 0.004f) drawArc(color, -90f, 360f * p, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun GProgress(progress: Float, modifier: Modifier = Modifier, color: Color = G.c.good, height: Dp = 7.dp, track: Color = G.c.surface2) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun EmojiBadge(emoji: String, tone: Tone, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(toneBg(tone)), contentAlignment = Alignment.Center) {
        T(emoji, (size.value * 0.45f).toInt())
    }
}

// ------------------------------------------------------------------ titles and empty states

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        T(title, 16, FontWeight.Bold)
        if (action != null) {
            T(
                action, 13, FontWeight.SemiBold, G.c.accent,
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    GCard {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T(emoji, 36)
            T(title, 16, FontWeight.Bold, align = TextAlign.Center)
            T(text, 13, color = G.c.muted, align = TextAlign.Center)
            if (action != null) PrimaryButton(action, Modifier.padding(top = 6.dp), onClick = onAction)
        }
    }
}

// ------------------------------------------------------------------ buttons

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, tone: Tone? = null, onClick: () -> Unit) {
    val c = G.c
    val bg = if (tone != null) toneBg(tone) else c.primary
    val fg = if (tone != null) toneFg(tone) else c.onPrimary
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) bg else c.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 15, FontWeight.Bold, if (enabled) fg else c.muted, align = TextAlign.Center) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = G.c.ink2) {
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, G.c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 14, FontWeight.SemiBold, color, maxLines = 1) }
}

/** A small tinted button used on rows ("done", "resisted", "it happened"). */
@Composable
fun ActionButton(text: String, tone: Tone, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val c = G.c
    val bg = if (filled) toneFg(tone) else toneBg(tone)
    val fg = if (filled) (if (c.dark) Color(0xFF10101A) else Color.White) else toneFg(tone)
    Box(
        modifier.heightIn(min = 42.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 13, FontWeight.Bold, fg, maxLines = 1) }
}

@Composable
fun GChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = G.c
    val bg by animateColorAsState(if (selected) c.primary else c.surface2, tween(180), label = "chip")
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
    ) { T(text, 13, FontWeight.SemiBold, if (selected) c.onPrimary else c.ink2, maxLines = 1) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

/**
 * Suggestions as chips plus a field for anything else. With [multi] false at most one value is kept.
 */
@Composable
fun TagPicker(
    label: String,
    suggestions: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    multi: Boolean = true,
    custom: Boolean = true,
) {
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    fun toggle(v: String) = onChange(
        when {
            v in selected -> selected - v
            multi -> selected + v
            else -> setOf(v)
        }
    )
    fun addTyped() {
        val v = text.trim()
        if (v.isNotEmpty() && v !in selected) toggle(v)
        text = ""
        typing = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(label, 13, FontWeight.SemiBold, G.c.ink2)
        ChipFlow {
            (suggestions + selected).distinct().forEach { v -> GChip(v, v in selected, { toggle(v) }) }
            if (custom && !typing) GChip("+ دیگر", false, { typing = true })
        }
        if (typing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GField("", text, { text = it.take(30) }, Modifier.weight(1f), placeholder = "بنویس…", imeAction = ImeAction.Done, onDone = ::addTyped)
                ActionButton("افزودن", Tone.Calm, onClick = ::addTyped)
            }
        }
    }
}

@Composable
fun MoodPicker(label: String, mood: Int, onChange: (Int) -> Unit) {
    val c = G.c
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(label, 13, FontWeight.SemiBold, c.ink2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MoodFaces.forEachIndexed { i, face ->
                val sel = mood == i + 1
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(if (sel) c.calmBg else c.surface2)
                        .border(2.dp, if (sel) c.calm else Color.Transparent, CircleShape)
                        .clickable { onChange(if (sel) 0 else i + 1) },
                    contentAlignment = Alignment.Center,
                ) { T(face, 24) }
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = G.c
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) c.surface else Color.Transparent, tween(180), label = "seg")
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { T(label, 13, if (sel) FontWeight.Bold else FontWeight.Medium, if (sel) c.ink else c.muted, maxLines = 1) }
        }
    }
}

// ------------------------------------------------------------------ fields

@Composable
fun GField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: Boolean = false,
    hint: String? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    onDone: (() -> Unit)? = null,
) {
    val c = G.c
    val shape = RoundedCornerShape(16.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) T(label, 13, FontWeight.SemiBold, c.ink2, Modifier.padding(horizontal = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            textStyle = TextStyle(fontFamily = Vazir, fontSize = 15.sp, color = c.ink, lineHeight = 24.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = if (onDone != null) androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() })
            else androidx.compose.foundation.text.KeyboardActions.Default,
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(shape).background(c.surface2)
                        .border(1.dp, if (error) c.slip else Color.Transparent, shape)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    if (value.isEmpty()) T(placeholder, 15, color = c.muted.copy(alpha = 0.8f))
                    inner()
                }
            },
        )
        if (hint != null) T(hint, 12, FontWeight.Medium, if (error) c.slip else c.muted, Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = G.c
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 15, FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) T(subtitle, 12, color = c.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.good, checkedThumbColor = Color.White, checkedBorderColor = c.good,
                uncheckedTrackColor = c.surface2, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.line,
            ),
        )
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
fun DialogCard(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = G.c
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DialogCard(onDismiss) {
        T(title, 17, FontWeight.Bold)
        T(text, 14, color = G.c.ink2)
        PrimaryButton(confirm, tone = Tone.Slip, onClick = onConfirm)
        GhostButton("انصراف", onDismiss, Modifier.fillMaxWidth())
    }
}

/** A plain hour / minute picker with Persian digits. */
@Composable
fun TimeDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var hour by remember { mutableIntStateOf(initial / 60) }
    var minute by remember { mutableIntStateOf(initial % 60) }
    DialogCard(onDismiss) {
        T(title, 16, FontWeight.Bold, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        // a clock reads left to right even in a right-to-left layout
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Stepper(hour, { hour = (hour + 1) % 24 }, { hour = (hour + 23) % 24 }, "ساعت")
                T(":", 34, FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
                Stepper(minute, { minute = (minute / 5 * 5 + 5) % 60 }, { minute = ((minute + 4) / 5 * 5 + 55) % 60 }, "دقیقه")
            }
        }
        PrimaryButton("تأیید") { onPick(hour * 60 + minute) }
        GhostButton("انصراف", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Stepper(value: Int, onUp: () -> Unit, onDown: () -> Unit, label: String) {
    val c = G.c
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundIcon(Icons.Filled.KeyboardArrowUp, "$label بیشتر", onUp)
        T(value.toString().padStart(2, '0').fa(), 34, FontWeight.Bold, c.ink, Modifier.padding(vertical = 4.dp))
        RoundIcon(Icons.Filled.KeyboardArrowDown, "$label کمتر", onDown)
    }
}

@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tint: Color = G.c.ink2) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(G.c.surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint) }
}

/** "previous | title | next" for weeks and months. In right-to-left, previous is on the right. */
@Composable
fun Pager(title: String, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, subtitle: String? = null, onTitle: (() -> Unit)? = null) {
    val c = G.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // auto-mirrored icons flip in right-to-left: "previous" sits on the right and points right
        if (onPrevious != null) RoundIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "قبلی", onPrevious)
        else Box(Modifier.size(42.dp))
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).then(if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(title, 16, FontWeight.Bold, align = TextAlign.Center)
            if (subtitle != null) T(subtitle, 12, color = c.muted)
        }
        if (onNext != null) RoundIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "بعدی", onNext)
        else Box(Modifier.size(42.dp))
    }
}

// ------------------------------------------------------------------ sheet

/**
 * Bottom sheet that is closed only on purpose (X, back, or a button inside it), so a half-written
 * note is never lost to an accidental swipe. Its content scrolls and stays above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val c = G.c
    val allowHide = remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || allowHide.value },
    )
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        allowHide.value = true
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = state,
        sheetGesturesEnabled = false,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = c.surface,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(title, 18, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
                RoundIcon(Icons.Filled.Close, "بستن", close)
            }
        },
    ) {
        androidx.activity.compose.BackHandler(onBack = close)
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(close) }
    }
}

// ------------------------------------------------------------------ celebration

object Celebrate {
    var tick by mutableIntStateOf(0)
        private set

    fun burst() {
        tick++
    }
}

private class Particle(val angle: Float, val speed: Float, val spin: Float, val color: Color, val w: Float, val h: Float, val delay: Float)

@Composable
fun ConfettiOverlay() {
    // only bursts requested while this screen is alive are shown
    val first = remember { Celebrate.tick }
    val t = Celebrate.tick
    if (t == first) return
    key(t) { ConfettiBurst() }
}

@Composable
private fun ConfettiBurst() {
    val c = G.c
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2000, easing = LinearEasing)) }
    val colors = remember(c) { listOf(c.accent2, c.accent, c.good, c.calm, c.slip) }
    val particles = remember {
        List(60) {
            Particle(
                angle = Random.nextFloat() * 120f + 210f, // upward fan
                speed = Random.nextFloat() * 900f + 700f,
                spin = Random.nextFloat() * 720f - 360f,
                color = colors[Random.nextInt(colors.size)],
                w = Random.nextFloat() * 8f + 6f,
                h = Random.nextFloat() * 5f + 8f,
                delay = Random.nextFloat() * 0.08f,
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val origin = Offset(size.width / 2f, size.height * 0.62f)
        for (pt in particles) {
            val tt = ((p - pt.delay) * 2.0f).coerceAtLeast(0f)
            if (tt <= 0f) continue
            val rad = Math.toRadians(pt.angle.toDouble())
            val x = origin.x + (Math.cos(rad) * pt.speed * tt).toFloat()
            val y = origin.y + (Math.sin(rad) * pt.speed * tt).toFloat() + 1500f * tt * tt
            val alpha = (1f - (p - 0.55f).coerceAtLeast(0f) / 0.45f).coerceIn(0f, 1f)
            rotate(pt.spin * tt, Offset(x, y)) {
                drawRect(pt.color.copy(alpha = alpha), Offset(x - pt.w / 2, y - pt.h / 2), Size(pt.w, pt.h))
            }
        }
    }
}
