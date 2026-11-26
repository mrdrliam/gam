package com.kassa.habit

import android.app.Application
import com.kassa.habit.data.Store
import com.kassa.habit.notify.Reminders

class HabitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reminders.ensureChannel(this)
        Reminders.schedule(this)
    }
}
