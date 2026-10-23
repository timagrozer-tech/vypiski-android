package ru.scanner291.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.watermark.WatermarkSpec
import ru.scanner291.core.storage.prefs.ThemeChoice
import ru.scanner291.core.storage.prefs.UserPreferences
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.FioToggle
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.RowDivider
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.SectionTitle
import ru.scanner291.designsystem.SettingsRow
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.ToggleRow
import ru.scanner291.designsystem.Type

@Composable
internal fun SettingsRouteScreen(onOpenWatermark: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.prefs.collectAsStateWithLifecycle()
    SettingsScreen(
        prefs = p,
        onTheme = vm::setTheme,
        onDynamicColor = vm::setDynamicColor,
        onSmartNaming = vm::setSmartNaming,
        onNameByFio = vm::setNameByFio,
        onOpenWatermark = onOpenWatermark,
    )
}

fun themeLabel(t: ThemeChoice) = when (t) {
    ThemeChoice.SYSTEM -> "Как в системе"
    ThemeChoice.DARK -> "Тёмная"
    ThemeChoice.LIGHT -> "Светлая"
}

/** Значение пункта «Водяной знак» — текущее состояние, а не описание раздела. */
fun watermarkValue(p: UserPreferences) = "${WatermarkSpec.TEXT} (${if (p.watermark.enabled) "вкл." else "выкл."})"

@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    onTheme: (ThemeChoice) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSmartNaming: (Boolean) -> Unit,
    onNameByFio: (Boolean) -> Unit,
    onOpenWatermark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var about by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.s10 * 2)) {
        ScanTopBar(title = "Настройки", large = true)
        Column(Modifier.padding(horizontal = Space.s4)) {
            SectionTitle("Названия файлов")
            Spacer(Modifier.height(Space.s2))
            GlassCard(padding = Space.s1) {
                ToggleRow("Умное название файла", prefs.smartNaming, onSmartNaming, description = "Определять ФИО из текста документа, без интернета")
                if (prefs.smartNaming) FioToggle(prefs.nameByFio, onNameByFio)
            }
            Spacer(Modifier.height(Space.s6))
            SectionTitle("Документы")
            Spacer(Modifier.height(Space.s2))
            GlassCard(padding = Space.s1) {
                SettingsRow(Icons.Rounded.WaterDrop, "Водяной знак", watermarkValue(prefs), onOpenWatermark)
            }
            Spacer(Modifier.height(Space.s6))
            SectionTitle("Интерфейс")
            Spacer(Modifier.height(Space.s2))
            GlassCard {
                Text("Тема", style = Type.caption, color = ScannerTheme.colors.textSecondary)
                Spacer(Modifier.height(Space.s2))
                ScanSegmented(ThemeChoice.entries, prefs.theme, ::themeLabel, onTheme)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(Space.s3))
                GlassCard(padding = Space.s1) {
                    ToggleRow("Цвета обоев (Dynamic Color)", prefs.dynamicColor, onDynamicColor, description = "По умолчанию — фирменная палитра")
                }
            }
            Spacer(Modifier.height(Space.s6))
            GlassCard(padding = Space.s1) {
                SettingsRow(Icons.Rounded.Info, "О приложении", "Лицензии, офлайн-режим", onClick = { about = true })
            }
        }
    }
    if (about) AboutDialog(onDismiss = { about = false })
}

/** Сведения о приложении и лицензиях сторонних компонентов (см. docs/LICENSES.md). */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { ScanButton("Закрыть", onDismiss, variant = ButtonVariant.Ghost) },
        title = { Text("291 Сканер", style = Type.titleLg) },
        text = {
            Column {
                Text("Работает полностью без интернета: у приложения нет разрешения на доступ к сети, документы и распознанный текст не покидают устройство.", style = Type.body)
                Spacer(Modifier.height(Space.s3))
                Text("Сторонние компоненты", style = Type.titleSm)
                Licenses.forEach { (name, license) -> Text("• $name — $license", style = Type.body) }
            }
        },
    )
}

internal val Licenses = listOf(
    "AndroidX, Jetpack Compose, CameraX, Room, WorkManager" to "Apache-2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache-2.0",
    "Hilt / Dagger" to "Apache-2.0",
    "OpenCV" to "Apache-2.0",
    "Tesseract4Android (Tesseract, Leptonica)" to "Apache-2.0, BSD-2",
    "Модели tessdata_fast (rus, eng, osd)" to "Apache-2.0",
    "PdfBox-Android" to "Apache-2.0",
)
