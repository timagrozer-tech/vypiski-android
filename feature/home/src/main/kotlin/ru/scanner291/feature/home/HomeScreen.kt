package ru.scanner291.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.QuickAction
import ru.scanner291.designsystem.ScanHero
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.SectionTitle
import ru.scanner291.designsystem.Space

/**
 * Главный экран — открывается сразу, без заставки. Единственный крупный акцент — «Сканировать».
 * «Импорт» появится вместе с сохранением документов (этап 5): до этого плитки нет.
 */
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenPdfTools: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.s10 * 2),
    ) {
        ScanTopBar(title = "291 Сканер", large = true, display = true)
        Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            ScanHero(onClick = onScan)
            Spacer(Modifier.height(Space.s1))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                QuickAction(Icons.Rounded.Folder, "Мои документы", onOpenDocuments, Modifier.weight(1f))
                QuickAction(Icons.Rounded.PictureAsPdf, "PDF-инструменты", onOpenPdfTools, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                QuickAction(Icons.Rounded.Search, "Поиск", onOpenSearch, Modifier.weight(1f))
                QuickAction(Icons.Rounded.LibraryAddCheck, "Пакетное сохранение", onOpenBatch, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.s3))
            SectionTitle("Недавние документы")
            GlassCard {
                EmptyState(
                    icon = Icons.Rounded.Description,
                    title = "Документов пока нет",
                    text = "Отсканированные документы появятся здесь. Всё хранится только на этом устройстве.",
                )
            }
        }
    }
}
