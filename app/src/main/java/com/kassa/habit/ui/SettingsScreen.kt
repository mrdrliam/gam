package com.kassa.habit.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kassa.habit.data.Jalali
import com.kassa.habit.data.Store
import com.kassa.habit.data.fa
import com.kassa.habit.data.fmtClock
import com.kassa.habit.notify.Reminders
import com.kassa.habit.ui.theme.G

private enum class TimeFor { EVENING, REVIEW }

@Composable
fun SettingsPage(app: AppState, pad: PaddingValues) {
    val c = G.c
    val ctx = LocalContext.current
    val s = Store.data.settings
    val granted = Store.resumeTick >= 0 && Reminders.canNotify(ctx)

    var picking by remember { mutableStateOf<TimeFor?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var name by remember(s.name) { mutableStateOf(s.name) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val stream = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
                stream.use { it.write(Store.exportJson().toByteArray(Charsets.UTF_8)) }
                app.toast("نسخه‌ی پشتیبان ذخیره شد")
            } catch (_: Exception) {
                app.toast("ذخیره‌ی فایل انجام نشد")
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                pendingImport = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (_: Exception) {
                app.toast("خواندن فایل انجام نشد")
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader(app, "تنظیمات") }

        item {
            SettingsCard("من") {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GField("", name, { name = it.take(30) }, Modifier.weight(1f), placeholder = "نامت (اختیاری)")
                    if (name.trim() != s.name) ActionButton("ذخیره", Tone.Calm) {
                        Store.updateSettings { it.copy(name = name.trim()) }
                    }
                }
            }
        }

        item {
            SettingsCard("یادآوری‌ها") {
                SwitchRow("یادآورها", "عادت‌ها، بستن روز و مرور هفتگی", s.notify) { on ->
                    Store.updateSettings { it.copy(notify = on) }
                    if (on && !Reminders.canNotify(ctx)) app.requestNotif()
                }
                if (s.notify) {
                    if (!granted) {
                        Divider()
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T("بدون اجازه‌ی اعلان، یادآوری نمی‌رسد.", 13, FontWeight.SemiBold, c.warn, Modifier.weight(1f))
                            ActionButton("فعال‌سازی", Tone.Warn) { app.requestNotif() }
                        }
                    }
                    Divider()
                    SwitchRow("بستن روز", s.eveningAt?.let { "هر شب ساعت ${fmtClock(it)}" } ?: "خاموش", s.eveningAt != null) { on ->
                        if (on) picking = TimeFor.EVENING else Store.updateSettings { it.copy(eveningAt = null) }
                    }
                    s.eveningAt?.let { ValueRow("تغییر ساعت", fmtClock(it)) { picking = TimeFor.EVENING } }
                    Divider()
                    SwitchRow("مرور هفتگی", s.reviewAt?.let { "جمعه‌ها ساعت ${fmtClock(it)}" } ?: "خاموش", s.reviewAt != null) { on ->
                        if (on) picking = TimeFor.REVIEW else Store.updateSettings { it.copy(reviewAt = null) }
                    }
                    s.reviewAt?.let { ValueRow("تغییر ساعت", fmtClock(it)) { picking = TimeFor.REVIEW } }
                    Divider()
                    T(
                        "یادآورها ممکن است چند دقیقه دیرتر برسند؛ برخی گوشی‌ها برای صرفه‌جویی باتری آن‌ها را عقب می‌اندازند.",
                        12, color = c.muted, modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        item {
            SettingsCard("پایان روز") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("ثبت‌های بعد از نیمه‌شب تا این ساعت برای روز قبل حساب می‌شوند.", 13, color = c.ink2)
                    ChipFlow {
                        (0..6).forEach { h ->
                            GChip(if (h == 0) "نیمه‌شب" else "${h.fa()} بامداد", s.dayStartHour == h, {
                                Store.updateSettings { it.copy(dayStartHour = h) }
                            })
                        }
                    }
                    T("هفته از ${Jalali.weekdayName(com.kassa.habit.data.weekStart(Store.today))} شروع می‌شود.", 12, color = c.muted)
                }
            }
        }

        item {
            SettingsCard("ظاهر") {
                Column(Modifier.padding(14.dp)) {
                    Segmented(listOf("مثل گوشی", "روشن", "تیره"), s.theme, { i -> Store.updateSettings { it.copy(theme = i) } })
                }
            }
        }

        item {
            SettingsCard("حریم خصوصی") {
                val activity = LocalActivity.current as? FragmentActivity
                SwitchRow("قفل برنامه", "اثر انگشت، چهره یا قفل گوشی", s.appLock) { on ->
                    if (activity == null) return@SwitchRow
                    if (on && !AppLock.isAvailable(activity)) {
                        app.toast("اول برای گوشی قفل صفحه بگذار")
                        return@SwitchRow
                    }
                    // turning it on or off both need the owner of the phone
                    if (!AppLock.isAvailable(activity)) {
                        Store.updateSettings { it.copy(appLock = false) }
                        return@SwitchRow
                    }
                    AppLock.authenticate(activity, if (on) "فعال‌کردن قفل" else "خاموش‌کردن قفل", onSuccess = {
                        if (on) AppLock.unlock()
                        Store.updateSettings { it.copy(appLock = on) }
                    })
                }
                Divider()
                T(
                    "همه‌ی داده‌ها فقط روی همین گوشی می‌ماند؛ برنامه اجازه‌ی اینترنت ندارد و در پشتیبان ابری اندروید هم قرار نمی‌گیرد.",
                    12, color = c.muted, modifier = Modifier.padding(14.dp),
                )
            }
        }

        item {
            SettingsCard("پشتیبان‌گیری") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("هر چند وقت یک نسخه بگیر تا با حذف برنامه یا گم‌شدن گوشی تاریخچه‌ات از دست نرود. فایل پشتیبان رمز ندارد؛ جای امن نگهش دار.", 13, color = c.ink2)
                    PrimaryButton("ذخیره‌ی پشتیبان") {
                        val j = Jalali.toJalali(Store.today)
                        export.launch("habit-backup-%d-%02d-%02d.json".format(java.util.Locale.US, j.y, j.m, j.d))
                    }
                    GhostButton("بازیابی از فایل", { import.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
                    GhostButton("پاک‌کردن همه‌ی داده‌ها", { confirmClear = true }, Modifier.fillMaxWidth(), color = c.slip)
                }
            }
        }

        item {
            T("این برنامه جای روان‌شناس یا درمانگر را نمی‌گیرد.", 12, color = c.muted, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }

    picking?.let { which ->
        val initial = if (which == TimeFor.EVENING) s.eveningAt ?: (21 * 60 + 30) else s.reviewAt ?: (20 * 60)
        TimeDialog(if (which == TimeFor.EVENING) "ساعت بستن روز" else "ساعت مرور (جمعه)", initial, { picking = null }) { m ->
            Store.updateSettings { if (which == TimeFor.EVENING) it.copy(eveningAt = m) else it.copy(reviewAt = m) }
            picking = null
        }
    }

    pendingImport?.let { text ->
        ConfirmDialog(
            "بازیابی از فایل؟", "داده‌های فعلیِ برنامه با محتوای این فایل جایگزین می‌شود.", "بله، جایگزین شود",
            { pendingImport = null },
        ) {
            pendingImport = null
            app.toast(if (Store.importJson(text)) "بازیابی انجام شد" else "این فایل پشتیبانِ معتبر نیست")
        }
    }

    if (confirmClear) ConfirmDialog(
        "همه‌ی داده‌ها پاک شود؟", "همه‌ی رفتارها، ثبت‌ها، مرورها و امتیازها برای همیشه حذف می‌شوند.", "بله، پاک شود",
        { confirmClear = false },
    ) {
        confirmClear = false
        Store.clearAll()
        app.toast("پاک شد")
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        GCard(content = content)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        T(label, 13, color = G.c.muted, modifier = Modifier.weight(1f))
        T(value, 14, FontWeight.Bold, G.c.accent)
    }
}
