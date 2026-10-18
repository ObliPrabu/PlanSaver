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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

// Blue/teal brand - "save money, stay connected" - distinct from the sibling
// Menalith app's indigo/teal RPG palette and from BuddyVerse's purple.
private val PlanSaverBlueLight = Color(0xFF1565C0)
private val PlanSaverTealLight = Color(0xFF00897B)
private val PlanSaverBlueDark = Color(0xFF90CAF9)
private val PlanSaverTealDark = Color(0xFF80CBC4)

private val LightColors = lightColorScheme(primary = PlanSaverBlueLight, secondary = PlanSaverTealLight)
private val DarkColors = darkColorScheme(primary = PlanSaverBlueDark, secondary = PlanSaverTealDark)

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

/// Light/dark follows the system setting (`isSystemInDarkTheme`). On Android
/// 12+ (API 31+) it prefers the device's own wallpaper-derived dynamic color
/// (Material You) over the fixed brand palette, same as most modern Android
/// apps; older devices fall back to the fixed blue/teal palette above.
@Composable
fun PlanSaverTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = scaledTypography(1.25f), content = content)
}
