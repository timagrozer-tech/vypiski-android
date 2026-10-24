package ru.scanner291.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.text.documentSubtitle
import ru.scanner291.core.database.DocumentSummary
import ru.scanner291.designsystem.BottomBarInset
import ru.scanner291.designsystem.CompactAction
import ru.scanner291.designsystem.DocumentCard
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.ScanHero
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.SectionTitle
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Type

/** Поддерживаемые типы в системном диалоге; реальный формат всё равно проверяется по сигнатуре. */
private val IMPORT_MIME = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf")

@Composable
internal fun HomeRouteScreen(
    onScan: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenPdfTools: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBatch: () -> Unit,
    onOpenDocument: (String) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.import(uris) }
    LaunchedEffect(event) {
        (event as? ImportEvent.Open)?.let { onOpenDocument(it.docId); vm.consumeEvent() }
    }
    HomeScreen(
        recent = recent,
        importing = importing,
        message = message,
        onDismissMessage = vm::clearMessage,
        onScan = onScan,
        onImport = { picker.launch(IMPORT_MIME) },
        onOpenDocuments = onOpenDocuments,
        onOpenPdfTools = onOpenPdfTools,
        onOpenSearch = onOpenSearch,
        onOpenBatch = onOpenBatch,
        onOpenDocument = onOpenDocument,
        thumbnail = vm::thumbnail,
    )
}

/** Главный экран — открывается сразу, без заставки. Единственный крупный акцент — «Сканировать». */
@Composable
fun HomeScreen(
    recent: List<DocumentSummary>?,
    importing: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenPdfTools: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBatch: () -> Unit,
    onOpenDocument: (String) -> Unit,
    thumbnail: suspend (String?) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val c = ScannerTheme.colors
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = BottomBarInset)) {
        ScanTopBar(title = "291 Сканер", large = true, display = true)
        Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            ScanHero(onClick = onScan)
            // Три действия в ряд; «Документы» и «Поиск» — в нижней панели, здесь не повторяются
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                CompactAction(Icons.Rounded.FileOpen, "Импорт", onImport, Modifier.weight(1f))
                CompactAction(Icons.Rounded.PictureAsPdf, "Работа с PDF", onOpenPdfTools, Modifier.weight(1f))
                CompactAction(Icons.Rounded.LibraryAddCheck, "Пакет", onOpenBatch, Modifier.weight(1f))
            }
            if (importing) {
                Text("Импорт файлов…", style = Type.caption, color = c.textSecondary)
                LinearProgressIndicator(color = c.accent, trackColor = c.field, modifier = Modifier.fillMaxWidth())
            }
            if (message != null) {
                GlassCard(onClick = onDismissMessage) {
                    Text(message, style = Type.body, color = c.text)
                    Text("Нажмите, чтобы скрыть", style = Type.caption, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(Space.s2))
            SectionTitle("Недавние документы", action = if (!recent.isNullOrEmpty()) "Все ›" else null, onAction = onOpenDocuments)
            if (recent.isNullOrEmpty()) {
                GlassCard {
                    EmptyState(
                        icon = Icons.Rounded.Description,
                        title = "Документов пока нет",
                        text = "Отсканированные документы появятся здесь. Всё хранится только на этом устройстве.",
                    )
                }
            } else {
                recent.forEach { d ->
                    key(d.doc.id) {
                        val bmp by produceState<ImageBitmap?>(null, d.firstPageId, d.doc.updatedAt) { value = thumbnail(d.firstPageId) }
                        DocumentCard(d.doc.title, documentSubtitle(d.doc.updatedAt, d.pageCount), bmp, onClick = { onOpenDocument(d.doc.id) }, favorite = d.doc.favorite)
                    }
                }
            }
        }
    }
}
