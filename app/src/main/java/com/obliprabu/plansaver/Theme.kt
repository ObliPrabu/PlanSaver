package com.obliprabu.plansaver

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

/// Shown as a visible "Theme: Auto / Light / Dark" picker on the form screen
/// (see MainActivity's PlanSaverApp) - some OEM skins (e.g. Vivo's OriginOS)
/// don't reliably forward their own "Dark theme" toggle to `isSystemInDarkTheme()`,
/// so a manual override is the only sure way to see/use dark mode on those
/// devices rather than just trusting the system setting.
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// Blue/teal brand - "save money, stay connected" - distinct from the sibling
// Menalith app's indigo/teal RPG palette and from BuddyVerse's purple.
private val PlanSaverBlueLight = Color(0xFF1565C0)
private val PlanSaverTealLight = Color(0xFF00897B)
private val PlanSaverBlueDark = Color(0xFF90CAF9)
private val PlanSaverTealDark = Color(0xFF80CBC4)

private val LightColors = lightColorScheme(primary = PlanSaverBlueLight, secondary = PlanSaverTealLight)
private val DarkColors = darkColorScheme(primary = PlanSaverBlueDark, secondary = PlanSaverTealDark)

// The "live deal" / "multi-line price found online" highlight was orange
// before dark mode existed (MaterialTheme.colorScheme.tertiary, which under
// the plain default Material3 scheme rendered as a warm tan/orange). Dynamic
// (Material You) color derives tertiary from the device wallpaper instead,
// so that badge could silently become any hue - pin it to a literal orange
// instead, independent of colorScheme, matching the iOS build's Color.orange
// for the same badge.
private val DealOrangeLight = Color(0xFFEF6C00)
private val DealOrangeDark = Color(0xFFFFB74D)

private val LocalDarkTheme = staticCompositionLocalOf { false }

/// The fixed "live deal" highlight color for the current effective theme
/// (light/dark/system-resolved) - see the comment on DealOrangeLight above.
val MaterialTheme.dealColor: Color
    @Composable get() = if (LocalDarkTheme.current) DealOrangeDark else DealOrangeLight

/// Every text style [f] times bigger, so the whole app is easier to read.
private fun scaledTypography(f: Float): Typography {
    fun TextStyle.big() = copy(fontSize = fontSize * f, lineHeight = lineHeight * f)
    val t = Typography()
    return t.copy(displayLarge = t.displayLarge.big(), displayMedium = t.displayMedium.big(), displaySmall = t.displaySmall.big(),
        headlineLarge = t.headlineLarge.big(), headlineMedium = t.headlineMedium.big(), headlineSmall = t.headlineSmall.big(),
        titleLarge = t.titleLarge.big(), titleMedium = t.titleMedium.big(), titleSmall = t.titleSmall.big(),
        bodyLarge = t.bodyLarge.big(), bodyMedium = t.bodyMedium.big(), bodySmall = t.bodySmall.big(),
        labelLarge = t.labelLarge.big(), labelMedium = t.labelMedium.big(), labelSmall = t.labelSmall.big())
}

/// SYSTEM follows `isSystemInDarkTheme()`; LIGHT/DARK force it regardless of
/// the system setting (the visible override - see ThemeMode's doc comment).
/// On Android 12+ (API 31+) it prefers the device's own wallpaper-derived
/// dynamic color (Material You) over the fixed brand palette, same as most
/// modern Android apps; older devices, and the dealColor badge above, use
/// the fixed blue/teal/orange colors instead.
@Composable
fun PlanSaverTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, typography = scaledTypography(1.25f), content = content)
    }
}
