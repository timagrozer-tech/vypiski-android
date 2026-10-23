package ru.scanner291.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Токены дизайн-системы «291 Сканер» (Premium Liquid Glass + Modern Android).
 * Источник: project/tokens.json дизайн-системы пользователя, синхронизировано 2026-10-23.
 */
@Immutable
data class ScannerColors(
    val bg: Color,
    val bgSecondary: Color,
    val surface: Color,
    val field: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassStroke: Color,
    val glassHighlight: Color,
    val separator: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentText: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val onAccentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val highlight: Color,
    val focusRing: Color,
    val paper: Color,
    val watermarkInk: Color,
    val cameraBg: Color,
    val scrim: Color,
    val isDark: Boolean,
)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, kotlin.math.round(a * 255).toInt())

val DarkColors = ScannerColors(
    bg = Color(0xFF080B12), bgSecondary = Color(0xFF111722), surface = Color(0xFF1A2230), field = Color(0xFF111722),
    glass = rgba(26, 34, 48, 0.72f), glassStrong = rgba(26, 34, 48, 0.9f),
    glassStroke = rgba(255, 255, 255, 0.08f), glassHighlight = rgba(255, 255, 255, 0.06f),
    separator = rgba(255, 255, 255, 0.07f),
    text = Color(0xFFF5F7FA), textSecondary = Color(0xFF9BA8BA),
    accent = Color(0xFF1683FF), accentPressed = Color(0xFF0F6FE0), accentText = Color(0xFF3D96FF),
    accentSoft = Color(0xFF12305A), onAccent = Color.White, onAccentSoft = Color(0xFFD6E8FF),
    success = Color(0xFF32D17C), warning = Color(0xFFFFC233), danger = Color(0xFFFF5A5F),
    highlight = rgba(255, 194, 51, 0.28f), focusRing = Color(0xFF7CB8FF),
    paper = Color.White, watermarkInk = Color(0xFF6B7280), cameraBg = Color.Black, scrim = rgba(0, 0, 0, 0.55f),
    isDark = true,
)

val LightColors = ScannerColors(
    bg = Color(0xFFF5F7FB), bgSecondary = Color(0xFFECEFF5), surface = Color.White, field = Color(0xFFF1F4F9),
    glass = rgba(255, 255, 255, 0.78f), glassStrong = rgba(255, 255, 255, 0.92f),
    glassStroke = rgba(17, 24, 39, 0.08f), glassHighlight = rgba(255, 255, 255, 0.9f),
    separator = rgba(17, 24, 39, 0.07f),
    text = Color(0xFF111827), textSecondary = Color(0xFF64748B),
    accent = Color(0xFF007AFF), accentPressed = Color(0xFF0062CC), accentText = Color(0xFF0062CC),
    accentSoft = Color(0xFFDCEBFF), onAccent = Color.White, onAccentSoft = Color(0xFF0062CC),
    success = Color(0xFF15803D), warning = Color(0xFFB45309), danger = Color(0xFFDC2626),
    highlight = rgba(250, 204, 21, 0.4f), focusRing = Color(0xFF0062CC),
    paper = Color.White, watermarkInk = Color(0xFF6B7280), cameraBg = Color.Black, scrim = rgba(17, 24, 39, 0.4f),
    isDark = false,
)

@Immutable
data class ScannerType(
    val display: TextStyle, val headline: TextStyle, val titleLg: TextStyle, val titleMd: TextStyle, val titleSm: TextStyle,
    val bodyLg: TextStyle, val body: TextStyle, val ocr: TextStyle, val label: TextStyle, val caption: TextStyle, val captionSm: TextStyle,
)

private val Sans = FontFamily.Default // системная гарнитура (Roboto/Noto Sans), полная кириллица, офлайн

val Type = ScannerType(
    display = TextStyle(fontFamily = Sans, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headline = TextStyle(fontFamily = Sans, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLg = TextStyle(fontFamily = Sans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMd = TextStyle(fontFamily = Sans, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSm = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLg = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    body = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    ocr = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    caption = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    captionSm = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

object Space {
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 20.dp; val s6 = 24.dp; val s8 = 32.dp; val s10 = 40.dp
}

object Radius {
    val xs = 8.dp; val sm = 12.dp; val md = 16.dp; val lg = 20.dp; val xl = 28.dp; val full = 999.dp
}

object Size {
    val touch = 48.dp; val icon = 24.dp; val iconLg = 32.dp; val nav = 68.dp; val shutter = 76.dp; val rowThumb = 44.dp
}

object Motion {
    const val PRESS_MS = 120
    const val STATE_MS = 200
    const val SCREEN_MS = 280
    const val SHEET_MS = 320
    const val PRESS_SCALE = 0.96f
    const val SHUTTER_PRESS_SCALE = 0.92f
}

/**
 * Тени (shadow-1, shadow-2, shadow-accent). В Compose тень задаётся высотой и цветом;
 * значения подобраны так, чтобы радиус размытия совпадал с токенами (24/40/28 px).
 */
object Elevation {
    val card = 8.dp
    val floating = 16.dp
    val accent = 12.dp
}

/**
 * Таблица «имя токена → цвет» для сверки с tokens.json дизайн-системы (тест TokensTest).
 * Имена — как в дизайн-системе.
 */
fun ScannerColors.byToken(): Map<String, Color> = mapOf(
    "bg" to bg, "bg-secondary" to bgSecondary, "surface" to surface, "field" to field,
    "glass" to glass, "glass-strong" to glassStrong, "glass-stroke" to glassStroke, "glass-highlight" to glassHighlight,
    "separator" to separator, "text" to text, "text-secondary" to textSecondary,
    "accent" to accent, "accent-pressed" to accentPressed, "accent-text" to accentText, "accent-soft" to accentSoft,
    "on-accent" to onAccent, "on-accent-soft" to onAccentSoft,
    "success" to success, "warning" to warning, "danger" to danger, "highlight" to highlight, "focus-ring" to focusRing,
    "paper" to paper, "watermark-ink" to watermarkInk, "camera-bg" to cameraBg, "scrim" to scrim,
)

const val OPACITY_WATERMARK = 0.2f
const val OPACITY_DISABLED = 0.38f

val LocalScannerColors = staticCompositionLocalOf { DarkColors }
