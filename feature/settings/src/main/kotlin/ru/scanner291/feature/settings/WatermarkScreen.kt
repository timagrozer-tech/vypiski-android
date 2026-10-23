package ru.scanner291.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.watermark.WatermarkSettings
import ru.scanner291.core.common.watermark.WatermarkSpec
import ru.scanner291.designsystem.AndroidTextMeasurer
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.SamplePages
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.SliderRow
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.ToggleRow
import ru.scanner291.designsystem.Type
import ru.scanner291.designsystem.WatermarkPreview
import ru.scanner291.designsystem.placementStatus
import ru.scanner291.designsystem.planSample
import kotlin.math.roundToInt

@Composable
internal fun WatermarkRouteScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.prefs.collectAsStateWithLifecycle()
    WatermarkScreen(p.watermark, vm::setWatermark, vm::resetWatermark, onBack)
}

enum class SampleKind(val label: String) { Regular("Обычный лист"), Dense("Текст до края") }

/**
 * Настройки водяного знака «В/ч 65384». Текст и расположение фиксированы (ТЗ): меняются размер,
 * прозрачность и отступ. Предпросмотр строится тем же планировщиком, что и экспорт; второй образец
 * показывает поведение при нехватке места (страница расширяется полем слева).
 */
@Composable
fun WatermarkScreen(
    settings: WatermarkSettings,
    onChange: (WatermarkSettings) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sample by rememberSaveable { mutableStateOf(SampleKind.Regular) }
    val blocks = if (sample == SampleKind.Regular) SamplePages.regular else SamplePages.dense
    val placement = remember(settings, blocks) { planSample(blocks, settings, AndroidTextMeasurer) }
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Водяной знак", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).padding(bottom = Space.s8)) {
            GlassCard(padding = Space.s1) {
                ToggleRow("Добавлять водяной знак", settings.enabled, { onChange(settings.copy(enabled = it)) }, description = "При экспорте в PDF, JPG и PNG. Оригиналы не меняются")
            }
            Spacer(Modifier.height(Space.s4))
            GlassCard {
                Text("Текст", style = Type.caption, color = ScannerTheme.colors.textSecondary)
                Text(WatermarkSpec.TEXT, style = Type.titleMd, color = ScannerTheme.colors.text)
                Text("Вертикально, слева в нижнем свободном поле. Текст и расположение не меняются.", style = Type.body, color = ScannerTheme.colors.textSecondary)
            }
            Spacer(Modifier.height(Space.s4))
            ScanSegmented(SampleKind.entries, sample, { it.label }, { sample = it })
            Spacer(Modifier.height(Space.s3))
            GlassCard {
                WatermarkPreview(settings, Modifier.fillMaxWidth(), blocks)
                Spacer(Modifier.height(Space.s3))
                if (settings.enabled) {
                    val (status, text) = placementStatus(placement)
                    StatusLine(status, text)
                } else {
                    Text("Водяной знак выключен", style = Type.caption, color = ScannerTheme.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(Space.s4))
            GlassCard(padding = Space.s1) {
                SliderRow("Размер", settings.fontPt, { onChange(settings.copy(fontPt = it.roundToInt().toFloat())) }, 6f..36f, { "${it.roundToInt()} pt" }, steps = 29)
                SliderRow("Прозрачность", settings.opacity, { onChange(settings.copy(opacity = (it * 20).roundToInt() / 20f)) }, 0.05f..1f, { "${(it * 100).roundToInt()}%" }, steps = 18)
                SliderRow("Отступ", settings.marginMm, { onChange(settings.copy(marginMm = it.roundToInt().toFloat())) }, 2f..30f, { "${it.roundToInt()} мм" }, steps = 27)
            }
            Spacer(Modifier.height(Space.s4))
            ScanButton("Восстановить стандартные", onReset, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
    }
}
