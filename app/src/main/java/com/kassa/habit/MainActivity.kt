package com.kassa.habit

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.kassa.habit.data.DeepLink
import com.kassa.habit.data.Store
import com.kassa.habit.notify.Reminders
import com.kassa.habit.ui.AppLock
import com.kassa.habit.ui.HabitRoot
import com.kassa.habit.widget.HabitWidget

// FragmentActivity (a ComponentActivity) is what the fingerprint prompt needs.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { HabitRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Widget, launcher shortcuts and notifications open a screen with habit://<name>. */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "habit") return
        Store.pendingLink = when (data.host) {
            "log" -> DeepLink.QUICK_LOG
            "pause" -> DeepLink.PAUSE
            "close" -> DeepLink.CLOSE_DAY
            "review" -> DeepLink.REVIEW
            else -> null
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }

    override fun onResume() {
        super.onResume()
        applyPrivacyFlag()
        Store.onResume()
        // re-arm on every visit: some battery managers drop alarms
        Reminders.schedule(this)
        HabitWidget.updateAll(this)
    }

    override fun onPause() {
        applyPrivacyFlag()
        super.onPause()
    }

    /**
     * With the lock on, the app's picture is hidden in the recent-apps list and screenshots are blocked.
     * Debug builds are exempt so the app can still be screenshotted while it is being developed.
     */
    private fun applyPrivacyFlag() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (Store.data.settings.appLock && !debuggable) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
