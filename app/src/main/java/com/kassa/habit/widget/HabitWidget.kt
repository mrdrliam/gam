package com.kassa.habit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kassa.habit.MainActivity
import com.kassa.habit.R
import com.kassa.habit.data.AppData
import com.kassa.habit.data.DayStatus
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Points
import com.kassa.habit.data.Store
import com.kassa.habit.data.buildStatus
import com.kassa.habit.data.fa
import java.time.LocalDate

/** Home-screen widget: today at a glance (counts only, no names) and one tap to log or pause. */
class HabitWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        Store.tick()
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun summary(data: AppData, today: LocalDate): String {
            if (data.activeHabits.isEmpty()) return "اولین رفتارت را اضافه کن"
            val due = data.activeHabits.filter { it.kind == Kind.BUILD && it.scheduledOn(today) }
            val done = due.count {
                buildStatus(data, it, today, today).let { s -> s == DayStatus.DONE || s == DayStatus.TINY }
            }
            val habits = if (due.isEmpty()) "امروز عادتی در برنامه نیست" else "${done.fa()} از ${due.size.fa()} عادت انجام شد"
            return "$habits · ${Points.ofDay(data, today).fa()} امتیاز"
        }

        private fun open(context: Context, link: String?, code: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (link != null) i.data = Uri.parse("habit://$link")
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun views(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_main).apply {
            setTextViewText(R.id.widget_summary, summary(Store.data, Store.today))
            setOnClickPendingIntent(R.id.widget_root, open(context, null, 100))
            setOnClickPendingIntent(R.id.widget_log, open(context, "log", 101))
            setOnClickPendingIntent(R.id.widget_pause, open(context, "pause", 102))
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, HabitWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }
    }
}
