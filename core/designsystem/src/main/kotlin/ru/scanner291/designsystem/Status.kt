package ru.scanner291.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class Status { SUCCESS, WARNING, DANGER }

internal fun statusVisual(c: ScannerColors, s: Status): Pair<ImageVector, Color> = when (s) {
    Status.SUCCESS -> Icons.Rounded.CheckCircle to c.success
    Status.WARNING -> Icons.Rounded.Error to c.warning
    Status.DANGER -> Icons.Rounded.Cancel to c.danger
}

/** Статус всегда с иконкой: цвет никогда не единственный носитель смысла. */
@Composable
fun StatusLine(status: Status, text: String, modifier: Modifier = Modifier) {
    val (icon, color) = statusVisual(ScannerTheme.colors, status)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.s2))
        Text(text, style = Type.caption, color = color)
    }
}

/**
 * Что показать на карточке ФИО. Модель дизайн-системы, не зависящая от извлечения:
 * преобразование из FioResult (core:intelligence) делает фича.
 */
sealed interface FioUi {
    /** Уверенность ≥ 90 %: подставляется в имя. */
    data class Found(val name: String, val confidencePercent: Int) : FioUi
    /** 60–89 % или несколько кандидатов: имя не меняется до выбора пользователя. */
    data class NeedsChoice(val best: String?, val candidates: Int, val confidencePercent: Int) : FioUi
    /** Не найдено или < 60 %: остаётся исходное имя. */
    data object NotFound : FioUi
}

/** Статус и текст для карточки ФИО (README FioCard). Чистая функция — покрыта тестом. */
fun fioStatus(fio: FioUi): Pair<Status, String> = when (fio) {
    is FioUi.Found -> Status.SUCCESS to "ФИО определено · Уверенность: ${fio.confidencePercent}%"
    is FioUi.NeedsChoice -> Status.WARNING to if (fio.candidates > 1) {
        "Проверьте ФИО · ${variantsText(fio.candidates)}"
    } else {
        "Проверьте ФИО · Уверенность: ${fio.confidencePercent}%"
    }
    FioUi.NotFound -> Status.DANGER to "ФИО не найдено"
}

private fun variantsText(n: Int): String = "$n " + ru.scanner291.core.common.text.RussianPlural.choose(n.toLong(), "вариант", "варианта", "вариантов")

/**
 * Карточка «Умное название файла»: переключатель функции, флажок «Назвать файл по ФИО»,
 * статус с иконкой, выбор кандидата и всегда видимое редактируемое имя файла.
 * Сомнительный результат не применяется без подтверждения — это решает вызывающий код,
 * карточка лишь не подставляет имя сама.
 */
@Composable
fun FioCard(
    enabled: Boolean,
    useFio: Boolean,
    fio: FioUi,
    fileName: String,
    extension: String,
    onToggle: (Boolean) -> Unit,
    onUseFioChange: (Boolean) -> Unit,
    onPickCandidate: () -> Unit,
    onFileNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ScannerTheme.colors
    GlassCard(modifier.fillMaxWidth(), padding = 0.dp) {
        ToggleRow(title = "Умное название файла", checked = enabled, onCheckedChange = onToggle)
        if (enabled) {
            FioToggle(checked = useFio, onChange = onUseFioChange)
            Column(Modifier.padding(horizontal = Space.s5, vertical = Space.s2)) {
                if (useFio) {
                    val (status, text) = fioStatus(fio)
                    StatusLine(status, text)
                    val shown = when (fio) {
                        is FioUi.Found -> fio.name
                        is FioUi.NeedsChoice -> fio.best
                        FioUi.NotFound -> null
                    }
                    if (shown != null) {
                        Spacer(Modifier.height(Space.s2))
                        Text(shown, style = Type.titleMd, color = c.text)
                    }
                    if (fio !is FioUi.NotFound) {
                        Spacer(Modifier.height(Space.s2))
                        ScanButton("Выбрать другое ФИО", onPickCandidate, variant = ButtonVariant.Secondary)
                    }
                    Spacer(Modifier.height(Space.s3))
                }
                Text("Название файла", style = Type.caption, color = c.textSecondary)
                Spacer(Modifier.height(Space.s1))
                ScanTextField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    placeholder = "Название",
                    suffix = ".$extension",
                    clearable = false,
                )
                Spacer(Modifier.height(Space.s4))
            }
        }
    }
}
