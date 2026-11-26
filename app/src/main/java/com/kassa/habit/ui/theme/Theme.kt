package com.kassa.habit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.kassa.habit.R

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/**
 * Calm indigo with a warm amber accent. A slip is coral rather than alarm red: it is information, not a verdict.
 */
@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val line: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val accent2: Color,
    val good: Color,
    val goodBg: Color,
    val slip: Color,
    val slipBg: Color,
    val warn: Color,
    val warnBg: Color,
    val calm: Color,
    val calmBg: Color,
)

val HeroTop = Color(0xFF4A4194)
val HeroMid = Color(0xFF2F2A63)
val HeroDeep = Color(0xFF1C1940)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF6F4F1),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFECE8),
    ink = Color(0xFF1F1C33),
    ink2 = Color(0xFF52506A),
    muted = Color(0xFF84829A),
    line = Color(0x141F1C33),
    primary = Color(0xFF3A3382),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFC9741F),
    accent2 = Color(0xFFFFC77A),
    good = Color(0xFF1E8A66),
    goodBg = Color(0xFFE0F3EB),
    slip = Color(0xFFC4533D),
    slipBg = Color(0xFFFBE8E3),
    warn = Color(0xFFA86B00),
    warnBg = Color(0xFFFDF0D8),
    calm = Color(0xFF4A4FB0),
    calmBg = Color(0xFFE9EAFB),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0F0E18),
    surface = Color(0xFF191827),
    surface2 = Color(0xFF242236),
    ink = Color(0xFFF1EFF8),
    ink2 = Color(0xFFBDBAD0),
    muted = Color(0xFF8885A0),
    line = Color(0x1FFFFFFF),
    primary = Color(0xFFB9B2FF),
    onPrimary = Color(0xFF17143A),
    accent = Color(0xFFF2B366),
    accent2 = Color(0xFFFFC77A),
    good = Color(0xFF55CFA4),
    goodBg = Color(0x2955CFA4),
    slip = Color(0xFFF08B76),
    slipBg = Color(0x29F08B76),
    warn = Color(0xFFEBB24F),
    warnBg = Color(0x29EBB24F),
    calm = Color(0xFFA5A9F5),
    calmBg = Color(0x29A5A9F5),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object G {
    val c: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

private fun vazirTypography(): Typography {
    val base = Typography()
    fun TextStyle.v() = copy(fontFamily = Vazir)
    return Typography(
        displayLarge = base.displayLarge.v(), displayMedium = base.displayMedium.v(), displaySmall = base.displaySmall.v(),
        headlineLarge = base.headlineLarge.v(), headlineMedium = base.headlineMedium.v(), headlineSmall = base.headlineSmall.v(),
        titleLarge = base.titleLarge.v(), titleMedium = base.titleMedium.v(), titleSmall = base.titleSmall.v(),
        bodyLarge = base.bodyLarge.v(), bodyMedium = base.bodyMedium.v(), bodySmall = base.bodySmall.v(),
        labelLarge = base.labelLarge.v(), labelMedium = base.labelMedium.v(), labelSmall = base.labelSmall.v(),
    )
}

/** [mode]: 0 = follow system, 1 = light, 2 = dark. The whole app is right-to-left. */
@Composable
fun HabitTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = p.primary, onPrimary = p.onPrimary, background = p.bg, surface = p.surface, onSurface = p.ink)
    } else {
        lightColorScheme(primary = p.primary, onPrimary = p.onPrimary, background = p.bg, surface = p.surface, onSurface = p.ink)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, typography = vazirTypography(), content = content)
    }
}
