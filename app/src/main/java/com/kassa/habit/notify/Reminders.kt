package com.kassa.habit.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kassa.habit.MainActivity
import com.kassa.habit.R
import com.kassa.habit.data.Kind
import com.kassa.habit.data.LogType
import com.kassa.habit.data.Store
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.fa
import com.kassa.habit.data.timesOn
import com.kassa.habit.widget.HabitWidget
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One inexact alarm at a time, set for the next planned reminder. When it fires, everything that is
 * still needed gets a notification and the following alarm is armed. No exact-alarm permission needed.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val PREFS = "habit_alarm"
    private const val ARMED = "armed"
    private const val FIRED = "fired"
    /** An alarm later than this is no longer worth showing. */
    private const val LATE_LIMIT_MS = 2 * 60 * 60 * 1000L

    const val ACTION_DONE = "com.kassa.habit.DONE"
    const val EXTRA_HABIT = "habit"
    const val EXTRA_DATE = "date"

    private const val ID_EVENING = 1
    private const val ID_REVIEW = 2
    private fun habitNotifId(habitId: String) = 1000 + (habitId.hashCode() and 0x0FFFFFFF)

    fun ensureChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "یادآوری‌ها", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "یادآوری عادت‌ها، بستن روز و مرور هفتگی"
            }
        )
    }

    fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun time(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    /** (Re)arms the next alarm, or cancels it if nothing is planned. Safe to call any time. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pi = alarmIntent(ctx)
        val now = System.currentTimeMillis()
        val armed = prefs.getLong(ARMED, 0)
        // an alarm that is due but has not arrived yet (inexact alarms can be late) must not be thrown away
        val overdue = Store.data.settings.notify && armed in 1..now && armed > prefs.getLong(FIRED, 0) && now - armed < LATE_LIMIT_MS
        val next = if (overdue) armed else ReminderPlan.next(Store.data, time(now))?.let(::millis)
        if (next == null) {
            am.cancel(pi)
            prefs.edit().remove(ARMED).apply()
            return
        }
        prefs.edit().putLong(ARMED, next).apply()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    fun fire(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val armed = prefs.getLong(ARMED, now)
        val since = maxOf(prefs.getLong(FIRED, 0), minOf(armed, now) - 1, now - LATE_LIMIT_MS)
        val due = ReminderPlan.due(Store.data, time(since), time(now))
        prefs.edit().putLong(FIRED, now).remove(ARMED).apply()
        if (canNotify(ctx)) {
            ensureChannel(ctx)
            due.forEach { post(ctx, it) }
        }
        schedule(ctx)
    }

    private fun post(ctx: Context, r: Reminder) {
        // with the app lock on, nothing about the behaviour is written on the lock screen
        val private = Store.data.settings.appLock
        val (id, n) = when (r) {
            is Reminder.ForHabit -> habitNotifId(r.habit.id) to habitNotification(ctx, r, private)
            is Reminder.Evening -> ID_EVENING to base(ctx, "close")
                .setContentTitle("وقت بستن روز 🌙")
                .setContentText("یک دقیقه: امروز چطور گذشت؟").build()
            is Reminder.WeeklyReview -> ID_REVIEW to base(ctx, "review")
                .setContentTitle("مرور هفتگی 🔍")
                .setContentText("ببین این هفته چقدر بهتر شدی و یک تغییر برای هفته‌ی بعد انتخاب کن.").build()
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun habitNotification(ctx: Context, r: Reminder.ForHabit, private: Boolean): Notification {
        val h = r.habit
        val b = base(ctx, if (h.kind == Kind.QUIT) "pause" else null)
        if (private) return b.setContentTitle("یادآوری").setContentText("یک یادآوری داری").build()
        if (h.kind == Kind.BUILD) {
            val counted = h.countedOn(r.date)
            val progress = if (!counted) ""
            else "${timesOn(Store.data, h.id, r.date).fa()} از ${h.targetOn(r.date).fa()}"
            val text = listOf(progress, h.cue, if (counted || h.tiny.isBlank()) "" else "حداقل: ${h.tiny}")
                .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "وقتش رسیده" }
            val done = PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, NotifActionReceiver::class.java).setAction(ACTION_DONE)
                    .setData(Uri.parse("habit://done/${h.id}/${r.date}"))
                    .putExtra(EXTRA_HABIT, h.id).putExtra(EXTRA_DATE, r.date.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.setContentTitle("${h.emoji} ${h.title}").setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .addAction(0, if (counted) "+۱ ثبت کن" else "✓ انجام شد", done)
        } else {
            val text = h.plan.ifBlank { h.replacement.ifBlank { "حواست باشد؛ تو از پسش برمی‌آیی." } }
            b.setContentTitle("حواست باشد: ${h.title}").setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        return b.build()
    }

    private fun base(ctx: Context, link: String?): NotificationCompat.Builder {
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (link != null) open.data = Uri.parse("habit://$link")
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF2F2A63.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx, link?.hashCode() ?: 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
    }

    /** Removes notifications that were dealt with inside the app. */
    fun clearHandled(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val active = nm.activeNotifications.map { it.id }.toSet()
        if (active.isEmpty()) return
        val data = Store.data
        val today = Store.today
        if (data.isClosed(today)) nm.cancel(ID_EVENING)
        if (com.kassa.habit.data.weekStart(today) in data.reviews) nm.cancel(ID_REVIEW)
        for (h in data.habits) {
            val id = habitNotifId(h.id)
            if (id !in active) continue
            val handled = h.archived || (h.kind == Kind.BUILD && !ReminderPlan.isOpen(buildStatus(data, h, today, today)))
            if (handled) nm.cancel(id)
        }
    }

    internal fun markDone(ctx: Context, habitId: String, date: LocalDate) {
        val h = Store.data.habit(habitId)
        if (h != null) Store.setStatus(habitId, date, LogType.DONE)
        ctx.getSystemService(NotificationManager::class.java).cancel(habitNotifId(habitId))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Store.tick()
        Reminders.fire(context)
        HabitWidget.updateAll(context)
    }
}

class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION_DONE) return
        val habit = intent.getStringExtra(Reminders.EXTRA_HABIT) ?: return
        val date = runCatching { LocalDate.parse(intent.getStringExtra(Reminders.EXTRA_DATE)) }.getOrNull() ?: return
        Store.init(context)
        Store.tick()
        Reminders.markDone(context, habit, date)
        Store.flush()
    }
}

/** Alarms are lost on reboot and shift when the clock or time zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Store.tick()
        Reminders.schedule(context)
        HabitWidget.updateAll(context)
    }
}
