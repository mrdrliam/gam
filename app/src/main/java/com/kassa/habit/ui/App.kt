package com.kassa.habit.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kassa.habit.data.Badge
import com.kassa.habit.data.Badges
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.DeepLink
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Level
import com.kassa.habit.data.Levels
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Points
import com.kassa.habit.data.Store
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.countLabel
import com.kassa.habit.data.fa
import com.kassa.habit.data.timesOn
import com.kassa.habit.data.weekStart
import com.kassa.habit.ui.theme.G
import com.kassa.habit.ui.theme.HabitTheme
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Page { JOURNAL, LESSONS, SETTINGS }

/** Everything about what is on screen right now. */
@Stable
class AppState {
    // set on every composition of the root
    lateinit var snackbar: SnackbarHostState
    lateinit var scope: CoroutineScope
    lateinit var haptic: HapticFeedback
    var requestNotif: () -> Unit = {}

    var tab by mutableIntStateOf(0)

    /** The day shown on the first tab; null follows today. */
    var pickedDate by mutableStateOf<LocalDate?>(null)
    val date: LocalDate get() = pickedDate ?: Store.today

    /** The week shown in the report; null follows the current week. */
    var pickedWeek by mutableStateOf<LocalDate?>(null)

    var detailId by mutableStateOf<String?>(null)
    var page by mutableStateOf<Page?>(null)

    var quickLog by mutableStateOf(false)
    /** The "new behaviour" list is open, on this kind. */
    var adding by mutableStateOf<Kind?>(null)
    /** The habit form is open with this draft. */
    var editing by mutableStateOf<Habit?>(null)
    /** The details sheet of one log. */
    var detailsLogId by mutableStateOf<String?>(null)
    var skipFor by mutableStateOf<Pair<String, LocalDate>?>(null)
    var closeDayFor by mutableStateOf<LocalDate?>(null)
    var dayEdit by mutableStateOf<Pair<String, LocalDate>?>(null)
    /** The breathing pause is open; the value is the behaviour, or "" to choose one there. */
    var pause by mutableStateOf<String?>(null)

    val hasOverlay: Boolean
        get() = quickLog || adding != null || editing != null || detailsLogId != null || skipFor != null ||
            closeDayFor != null || dayEdit != null || pause != null

    fun closeOverlays() {
        quickLog = false
        adding = null
        editing = null
        detailsLogId = null
        skipFor = null
        closeDayFor = null
        dayEdit = null
        pause = null
    }

    fun showDay(date: LocalDate) {
        pickedDate = if (date >= Store.today) null else date
    }

    fun toast(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, action, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    private fun allDone(date: LocalDate): Boolean {
        val due = Store.data.activeHabits.filter { it.kind == Kind.BUILD && it.scheduledOn(date) }
        return due.isNotEmpty() && due.all {
            buildStatus(Store.data, it, date, Store.today).let { s -> s == DayStatus.DONE || s == DayStatus.TINY }
        }
    }

    /** Done / tiny / cleared for a good habit. For a counted habit, done means "one more time". */
    fun setStatus(habit: Habit, date: LocalDate, type: LogType?) {
        val wasAllDone = allDone(date)
        val oneMore = habit.countedOn(date) && (type == LogType.DONE || type == LogType.TINY)
        val undo = Store.setStatus(habit.id, date, type)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val times = timesOn(Store.data, habit.id, date)
        val target = habit.targetOn(date)
        val reached = oneMore && times == target
        val message = when {
            reached -> "هدف «${habit.title}» کامل شد 🎯"
            oneMore -> "«${habit.title}» · ${countLabel(times, target)}"
            type == LogType.DONE -> "آفرین! «${habit.title}» انجام شد · +${Points.DONE.fa()}"
            type == LogType.TINY -> "همین قدم کوچک هم حساب است · +${Points.TINY.fa()}"
            else -> "ثبت «${habit.title}» برداشته شد"
        }
        if (!wasAllDone && allDone(date)) {
            Celebrate.burst()
            toast("همه‌ی عادت‌های ${if (date == Store.today) "امروز" else "آن روز"} کامل شد 🎉", "بازگردانی", undo)
        } else {
            if (reached) Celebrate.burst()
            toast(message, "بازگردانی", undo)
        }
    }

    /** Takes back one time of a counted habit. */
    fun removeOne(habit: Habit, date: LocalDate) {
        val undo = Store.removeLastTime(habit.id, date) ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        toast("یک بار از «${habit.title}» برداشته شد", "بازگردانی", undo)
    }

    fun resist(habit: Habit, date: LocalDate) {
        val log = Store.addLog(habit.id, date, LogType.RESIST)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Celebrate.burst()
        toast("جلوی خودت را گرفتی! 💪", "افزودن جزئیات") { detailsLogId = log.id }
    }

    /** A slip is saved at once; the sheet that follows is only for understanding it. */
    fun slip(habit: Habit, date: LocalDate) {
        detailsLogId = Store.addLog(habit.id, date, LogType.SLIP).id
    }
}

@Composable
fun HabitRoot() {
    val app = remember { AppState() }
    HabitTheme(Store.data.settings.theme) {
        app.snackbar = remember { SnackbarHostState() }
        app.scope = rememberCoroutineScope()
        app.haptic = LocalHapticFeedback.current
        app.requestNotif = rememberNotificationRequest()
        when {
            // nothing else is composed while locked, so no sheet or dialog can float above the lock
            AppLock.locked -> LockScreen()
            !Store.data.settings.onboarded && Store.data.habits.isEmpty() -> Onboarding(app)
            else -> AppContent(app)
        }
    }
}

/** Asks for the notification permission; if the system will no longer ask, opens the app's settings page. */
@Composable
private fun rememberNotificationRequest(): () -> Unit {
    val ctx = LocalContext.current
    val activity = LocalActivity.current
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Store.onResume() }
    return {
        val canAsk = Build.VERSION.SDK_INT >= 33 && (!asked ||
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true)
        if (canAsk) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun AppContent(app: AppState) {
    val c = G.c
    val holder = rememberSaveableStateHolder()

    // keep "today" right while the app stays open past the start of a new day
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            Store.tick()
        }
    }

    val link = Store.pendingLink
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        app.closeOverlays()
        app.detailId = null
        app.page = null
        when (link) {
            DeepLink.QUICK_LOG -> app.quickLog = true
            DeepLink.PAUSE -> app.pause = ""
            DeepLink.CLOSE_DAY -> {
                app.tab = 0
                app.pickedDate = null
                app.closeDayFor = Store.today
            }
            DeepLink.REVIEW -> {
                app.tab = 2
                app.pickedWeek = weekStart(Store.today)
            }
        }
        Store.pendingLink = null
    }

    BackHandler(enabled = app.detailId == null && app.page == null && app.tab != 0) { app.tab = 0 }
    BackHandler(enabled = app.detailId != null) { app.detailId = null }
    BackHandler(enabled = app.page != null) { app.page = null }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barH = 66.dp
    val pad = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = barH + bottom + 40.dp)
    val fullPad = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 24.dp)

    // the page being closed keeps showing while it slides away
    var shownDetail by remember { mutableStateOf<String?>(null) }
    if (app.detailId != null) shownDetail = app.detailId
    var shownPage by remember { mutableStateOf(Page.SETTINGS) }
    app.page?.let { shownPage = it }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Crossfade(app.tab, label = "tabs") { tab ->
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> TodayScreen(app, pad)
                    1 -> HabitsScreen(app, pad)
                    2 -> ReportScreen(app, pad)
                    else -> MeScreen(app, pad)
                }
            }
        }

        // scrolled content must not run under the clock and battery icons
        Box(Modifier.fillMaxWidth().height(top).background(c.bg))

        Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(app, barH) }

        FullPage(app.detailId != null) {
            val habit = Store.data.habit(shownDetail)
            if (habit != null) DetailScreen(app, habit, fullPad) else app.detailId = null
        }
        FullPage(app.page != null) {
            when (shownPage) {
                Page.JOURNAL -> JournalPage(app, fullPad)
                Page.LESSONS -> LessonsPage(app, fullPad)
                Page.SETTINGS -> SettingsPage(app, fullPad)
            }
        }

        Sheets(app)

        SnackbarHost(
            app.snackbar,
            Modifier.align(Alignment.BottomCenter).padding(
                bottom = (if (app.detailId != null || app.page != null) 16.dp else barH + 30.dp) + bottom,
                start = 16.dp, end = 16.dp,
            ),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = if (c.dark) c.surface2 else c.ink,
                contentColor = if (c.dark) c.ink else c.bg,
                actionColor = c.accent2,
            )
        }

        app.pause?.let { PauseScreen(app, it) }

        ConfettiOverlay()
        Rewards(app)
    }
}

@Composable
private fun FullPage(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
        // swallows taps so nothing underneath reacts
        val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.fillMaxSize().background(G.c.bg).pointerInput(Unit) { }) {
            content()
            Box(Modifier.fillMaxWidth().height(status).background(G.c.bg))
        }
    }
}

@Composable
private fun Sheets(app: AppState) {
    if (app.quickLog) QuickLogSheet(app) { app.quickLog = false }
    app.adding?.let { AddSheet(app, it) { app.adding = null } }
    app.editing?.let { HabitFormSheet(app, it) { app.editing = null } }
    app.dayEdit?.let { (habitId, date) ->
        val habit = Store.data.habit(habitId)
        if (habit != null) DaySheet(app, habit, date) { app.dayEdit = null } else app.dayEdit = null
    }
    app.closeDayFor?.let { CloseDaySheet(app, it) { app.closeDayFor = null } }
    app.skipFor?.let { (habitId, date) ->
        val habit = Store.data.habit(habitId)
        if (habit != null) SkipSheet(app, habit, date) { app.skipFor = null } else app.skipFor = null
    }
    // last, so it opens above the sheet it was reached from
    app.detailsLogId?.let { id ->
        val log = Store.data.logs.firstOrNull { it.id == id }
        if (log != null) LogDetailsSheet(app, log) { app.detailsLogId = null } else app.detailsLogId = null
    }
}

/** Celebrates new badges and levels once, when nothing else is in the way. */
@Composable
private fun Rewards(app: AppState) {
    val data = Store.data
    val today = Store.today
    val earned: List<Pair<String, Any>> = remember(data.habits, data.logs, data.days, data.reviews, today) {
        val level = Levels.of(Points.total(data))
        val badges: List<Pair<String, Any>> = Badges.all(data, today).filter { it.earned }.map { it.id to it }
        if (level.number > 1) badges + ("level_${level.number}" to level) else badges
    }
    val fresh = earned.filter { it.first !in data.settings.seenBadges }
    if (fresh.isEmpty() || app.hasOverlay) return
    LaunchedEffect(fresh.map { it.first }) { Celebrate.burst() }
    val dismiss = { Store.updateSettings { s -> s.copy(seenBadges = s.seenBadges + fresh.map { it.first }) } }
    DialogCard(dismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("دستاورد تازه 🎉", 18, FontWeight.Bold)
            fresh.take(4).forEach { (_, reward) ->
                val (emoji, title, text) = when (reward) {
                    is Badge -> Triple(reward.emoji, reward.title, reward.how)
                    is Level -> Triple("⭐", "سطح ${reward.number.fa()}: ${reward.title}", "با ثبت‌هایت به سطح تازه رسیدی")
                    else -> Triple("🎉", "", "")
                }
                T(emoji, 40)
                T(title, 16, FontWeight.Bold, align = TextAlign.Center)
                T(text, 13, color = G.c.muted, align = TextAlign.Center)
            }
            if (fresh.size > 4) T("و ${(fresh.size - 4).fa()} دستاورد دیگر", 13, color = G.c.muted)
            PrimaryButton("ادامه می‌دهم", onClick = dismiss)
        }
    }
}

private data class TabDef(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabDef("امروز", Icons.Filled.Home),
    TabDef("رفتارها", Icons.AutoMirrored.Filled.List),
    TabDef("گزارش", Icons.Filled.DateRange),
    TabDef("من", Icons.Filled.Person),
)

@Composable
private fun BottomBar(app: AppState, barH: Dp) {
    val c = G.c
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(c.surface).border(1.dp, c.line, RectangleShape).navigationBarsPadding().height(barH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(app, 0, Modifier.weight(1f))
            TabItem(app, 1, Modifier.weight(1f))
            Box(Modifier.weight(1f))
            TabItem(app, 2, Modifier.weight(1f))
            TabItem(app, 3, Modifier.weight(1f))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .shadow(10.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accent2, Color(0xFFE8963C))))
                .border(3.dp, c.bg, CircleShape)
                .clickable { app.quickLog = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "ثبت سریع", tint = Color(0xFF2B1A04), modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun TabItem(app: AppState, index: Int, modifier: Modifier) {
    val c = G.c
    val tab = Tabs[index]
    val selected = app.tab == index
    val color = if (selected) c.primary else c.muted
    Column(
        modifier.fillMaxSize().clickable {
            if (index == 0 && selected) app.pickedDate = null
            app.tab = index
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, null, tint = color, modifier = Modifier.size(25.dp))
        T(tab.label, 11, if (selected) FontWeight.Bold else FontWeight.Medium, color)
    }
}
