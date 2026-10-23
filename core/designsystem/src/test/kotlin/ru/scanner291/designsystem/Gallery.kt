package ru.scanner291.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Галерея компонентов для снимков экрана (Roborazzi) — визуальная проверка обеих тем. */
@Composable
fun ComponentGallery() {
    ScreenBox {
        Column(Modifier.fillMaxWidth().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            ScanTopBar(title = "291 Сканер", large = true, display = true)
            ScanHero(onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                QuickAction(Icons.Rounded.FileOpen, "Импорт", {}, Modifier.weight(1f))
                QuickAction(Icons.Rounded.PictureAsPdf, "PDF-инструменты", {}, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                ToolTile(Icons.AutoMirrored.Rounded.CallMerge, "Объединить", "Несколько PDF в один", {}, Modifier.weight(1f))
                ToolTile(Icons.AutoMirrored.Rounded.CallSplit, "Разделить", "По страницам или диапазонам", {}, Modifier.weight(1f))
            }
            GlassCard(padding = 0.dp) {
                DocumentRow("Иванов И.И. – Договор", "Сегодня, 14:32 · 3 стр.", null, {}, highlight = "иванов")
                RowDivider()
                DocumentRow("Справка о доходах", "Вчера, 10:15 · 1 стр.", null, {}, selectionMode = true, selected = true)
                RowDivider()
                FolderRow("Договоры", 12, {}, {})
                RowDivider()
                SettingsRow(Icons.Rounded.WaterDrop, "Водяной знак", "В/ч 65384 (вкл.)", {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                ScanChip("Документ", selected = true, onClick = {})
                ScanChip("Паспорт", selected = false, onClick = {})
                ScanChip("Чек", selected = false, onClick = {}, leadingIcon = Icons.Rounded.Description)
            }
            ScanSegmented(listOf("PDF", "JPG", "PNG"), "PDF", { it }, {})
            ScanTextField("", {}, placeholder = "Поиск по документам", leadingIcon = Icons.Rounded.Search)
            ScanTextField("Иванов Иван", {}, placeholder = "Название", suffix = ".pdf", isError = true, supportingText = "Файл с таким именем есть")
            GlassCard(padding = 0.dp) {
                ToggleRow("Определять ФИО", checked = true, onCheckedChange = {}, description = "Из текста документа, без интернета")
                FioToggle(checked = true, onChange = {})
                SliderRow("Прозрачность", 0.2f, {}, 0f..1f, { "${(it * 100).toInt()}%" })
            }
            FioCard(
                enabled = true, useFio = true, fio = FioUi.NeedsChoice("Петров Пётр Петрович", 2, 74),
                fileName = "Скан 2026-10-23 14-28", extension = "pdf",
                onToggle = {}, onUseFioChange = {}, onPickCandidate = {}, onFileNameChange = {},
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                StatusLine(Status.SUCCESS, "ФИО определено · Уверенность: 96%")
                StatusLine(Status.WARNING, "2 варианта ФИО")
                StatusLine(Status.DANGER, "ФИО не найдено")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                PageThumb(null, 1, selected = true, dragging = false, onClick = {}, onLongClick = {}, modifier = Modifier.width(104.dp))
                PageThumb(null, 2, selected = false, dragging = false, onClick = {}, onLongClick = {}, modifier = Modifier.width(104.dp))
                AddPageCell({}, Modifier.width(104.dp))
            }
            ScanButton("Добавить страницу", {}, variant = ButtonVariant.Tonal, icon = Icons.Rounded.Add)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                ScanButton("Копировать", {}, variant = ButtonVariant.Secondary)
                ScanButton("Все ›", {}, variant = ButtonVariant.Ghost)
                ScanButton("Удалить", {}, variant = ButtonVariant.Danger)
            }
            ScanButton("Сохранить все (5)", {}, Modifier.fillMaxWidth())
            ScanButton("Сохранить", {}, Modifier.fillMaxWidth(), enabled = false)
            Spacer(Modifier.height(Space.s2))
            ScanBottomNav(selected = Destination.Home, onSelect = {})
        }
    }
}
