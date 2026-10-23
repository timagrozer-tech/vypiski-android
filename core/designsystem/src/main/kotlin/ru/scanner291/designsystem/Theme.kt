package ru.scanner291.designsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Включено ли «стекло» (полупрозрачные слои). Решение принимается один раз — в теме. */
val LocalGlassEnabled = staticCompositionLocalOf { true }

/** Системная настройка «Убрать анимацию»: переходы мгновенные, без масштаба, стекло выключено. */
val LocalReducedMotion = staticCompositionLocalOf { false }

object ScannerTheme {
    val colors: ScannerColors @Composable get() = LocalScannerColors.current
    val type: ScannerType get() = Type
    val glassEnabled: Boolean @Composable get() = LocalGlassEnabled.current
    val reducedMotion: Boolean @Composable get() = LocalReducedMotion.current
}

/**
 * Решение о стекле (README дизайн-системы, «Стекло в Compose»): только Android 12+,
 * не в режиме энергосбережения и не при выключенной анимации. Чистая функция — покрыта тестом.
 */
fun glassAllowed(sdkInt: Int, powerSave: Boolean, reducedMotion: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.S && !powerSave && !reducedMotion

/**
 * Тема «291 Сканер». Тёмная — основная, светлая — полноценная вторая.
 * Dynamic Color — только по явному выбору пользователя (Android 12+), по умолчанию палитра фиксирована.
 */
@Composable
fun ScannerTheme(
    mode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val c = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion(context)
    val powerSave = rememberPowerSaveMode(context)
    val glass = glassAllowed(Build.VERSION.SDK_INT, powerSave, reducedMotion)

    val base = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.onAccentSoft,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.field, onSurfaceVariant = c.textSecondary, outline = c.glassStroke, outlineVariant = c.separator,
        error = c.danger, scrim = c.scrim, secondary = c.accentText, tertiary = c.success,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        surfaceContainerLow = c.bgSecondary, surfaceContainerLowest = c.bg,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.onAccentSoft,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.field, onSurfaceVariant = c.textSecondary, outline = c.glassStroke, outlineVariant = c.separator,
        error = c.danger, scrim = c.scrim, secondary = c.accentText, tertiary = c.success,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        surfaceContainerLow = c.bgSecondary, surfaceContainerLowest = c.bg,
    )
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else base

    CompositionLocalProvider(
        LocalScannerColors provides c,
        LocalGlassEnabled provides glass,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTypography, content = content)
    }
}

/** Типографика Material 3, сопоставленная со шкалой дизайн-системы. */
private val MaterialTypography = Typography(
    displaySmall = Type.display,
    headlineMedium = Type.headline,
    titleLarge = Type.titleLg,
    titleMedium = Type.titleMd,
    titleSmall = Type.titleSm,
    bodyLarge = Type.bodyLg,
    bodyMedium = Type.body,
    labelLarge = Type.label,
    labelMedium = Type.caption,
    labelSmall = Type.captionSm,
    bodySmall = Type.caption,
)

@Composable
private fun rememberReducedMotion(context: Context): Boolean = remember(context) {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

/** Режим энергосбережения с подпиской на изменения — стекло отключается сразу, без перезапуска. */
@Composable
private fun rememberPowerSaveMode(context: Context): Boolean {
    val pm = remember(context) { context.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { powerSave = pm?.isPowerSaveMode == true }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return powerSave
}

/** Фон экрана `bg`. */
@Composable
fun ScreenBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(ScannerTheme.colors.bg), content = content)
}
