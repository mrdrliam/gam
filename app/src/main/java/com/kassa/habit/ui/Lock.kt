package com.kassa.habit.ui

import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kassa.habit.data.Store
import com.kassa.habit.ui.theme.HeroDeep
import com.kassa.habit.ui.theme.HeroMid
import com.kassa.habit.ui.theme.HeroTop

/**
 * App lock. Off by default; when it is on, the app asks for the fingerprint / face / screen lock
 * of the phone when it is opened, and again after it has been away for longer than the grace time.
 */
object AppLock {
    var locked by mutableStateOf(false)
        private set

    private var everUnlocked = false
    private var leftAt = 0L

    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Pure rule, kept separate so it can be unit-tested. */
    fun shouldLock(enabled: Boolean, everUnlocked: Boolean, awayMs: Long, graceSec: Int): Boolean =
        enabled && (!everUnlocked || awayMs > graceSec * 1000L)

    fun onStart() {
        val away = SystemClock.elapsedRealtime() - leftAt
        if (shouldLock(Store.data.settings.appLock, everUnlocked, away, Store.data.settings.lockGraceSec)) locked = true
    }

    fun onStop() {
        // leaving while the lock screen is up must not restart the grace time
        if (!locked) leftAt = SystemClock.elapsedRealtime()
    }

    fun unlock() {
        locked = false
        everUnlocked = true
    }

    /** Turning the lock on is only allowed when the phone itself has a lock to fall back on. */
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFail: () -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTH) // with a screen-lock fallback there is no negative button
            .build()
        prompt.authenticate(info)
    }
}

/** Shown instead of the app while it is locked. */
@Composable
fun LockScreen() {
    val activity = LocalActivity.current as? FragmentActivity
    var failed by remember { mutableStateOf(false) }
    val ask = {
        if (activity != null) {
            failed = false
            // the phone's own lock was removed in the meantime: there is nothing left to check against
            if (!AppLock.isAvailable(activity)) AppLock.unlock()
            else AppLock.authenticate(activity, "باز کردن گام", onSuccess = { AppLock.unlock() }, onFail = { failed = true })
        }
    }
    LaunchedEffect(Unit) { ask() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(HeroTop, HeroMid, HeroDeep))), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("🔒", 48)
            T("گام", 28, FontWeight.Bold, Color.White)
            T(
                if (failed) "قفل باز نشد. دوباره تلاش کن." else "برای دیدن اطلاعات، قفل را باز کن.",
                14, color = Color.White.copy(alpha = 0.8f), align = TextAlign.Center,
            )
            PrimaryButton("باز کردن قفل", tone = Tone.Calm) { ask() }
        }
    }
}
