package ru.scanner291.feature.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.export.Align
import ru.scanner291.core.common.export.Paragraph
import ru.scanner291.core.common.export.TextLayout
import ru.scanner291.core.database.PageEntity
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type

enum class ViewerTab { Image, Word }

@Composable
internal fun ViewerRouteScreen(onProcess: (String, Int) -> Unit, onBack: () -> Unit, onText: (String) -> Unit, onExport: (String) -> Unit, vm: ViewerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val texts by vm.texts.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busyPage.collectAsStateWithLifecycle()
    val docx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { it?.let(vm::saveDocx) }
    ViewerScreen(
        state, texts, message, busy, vm::image,
        ViewerActions(
            onBack = onBack, onRotate = vm::rotate, onEdit = { i -> onProcess(vm.docId, i) },
            onExtract = vm::extract, onShare = { onExport(vm.docId) }, onAllText = { onText(vm.docId) },
            onDocx = { title -> docx.launch("$title.docx") }, onMessageShown = vm::clearMessage,
        ),
    )
}

data class ViewerActions(
    val onBack: () -> Unit = {}, val onRotate: (PageEntity) -> Unit = {}, val onEdit: (Int) -> Unit = {},
    val onExtract: (PageEntity, () -> Unit) -> Unit = { _, _ -> }, val onShare: () -> Unit = {}, val onAllText: () -> Unit = {},
    val onDocx: (String) -> Unit = {}, val onMessageShown: () -> Unit = {},
)

/**
 * Просмотр результата (2026-10-23): «Образ» — обработанная страница на всю ширину, листание свайпом,
 * масштаб щипком (двойное касание — сброс); «Word» — распознанный текст с абзацами и выравниванием,
 * сомнительные слова подсвечены. Внизу — «Поворот», «Редактировать», «Извлечь текст», «Поделиться», «Ещё».
 */
@Composable
fun ViewerScreen(
    state: ViewerState,
    texts: Map<String, PageWord>,
    message: String?,
    busyPage: String?,
    image: suspend (PageEntity) -> ImageBitmap?,
    a: ViewerActions,
) {
    val c = ScannerTheme.colors
    var tab by rememberSaveable { mutableStateOf(ViewerTab.Image) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        when (state) {
            ViewerState.Loading -> Header(tab, { tab = it }, null, a.onBack)
            ViewerState.NotFound -> {
                Header(tab, { tab = it }, null, a.onBack)
                EmptyState(Icons.Rounded.PictureAsPdf, stringResource(R.string.viewer_not_found), stringResource(R.string.viewer_not_found_hint))
            }
            is ViewerState.Ready -> {
                val pages = state.doc.orderedPages
                val pager = rememberPagerState { pages.size }
                Header(tab, { tab = it }, stringResource(R.string.viewer_counter, pager.currentPage + 1, pages.size), a.onBack)
                message?.let { StatusLine(Status.SUCCESS, it, Modifier.padding(horizontal = Space.s4)) }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { pages[it].id }) { i ->
                    val p = pages[i]
                    Crossfade(tab, label = "viewerTab") { t ->
                        when (t) {
                            ViewerTab.Image -> ZoomablePage(p, i + 1, image)
                            ViewerTab.Word -> WordPage(texts[p.id], busyPage == p.id) { a.onExtract(p) {} }
                        }
                    }
                }
                val current = pages.getOrNull(pager.currentPage)
                BottomBar(
                    onRotate = { current?.let(a.onRotate) },
                    onEdit = { a.onEdit(pager.currentPage) },
                    onExtract = { current?.let { p -> a.onExtract(p) { tab = ViewerTab.Word } } },
                    onShare = a.onShare,
                    onAllText = a.onAllText,
                    onDocx = { a.onDocx(state.doc.doc.title) },
                    onExport = a.onShare,
                )
            }
        }
    }
}

@Composable
private fun Header(tab: ViewerTab, onTab: (ViewerTab) -> Unit, counter: String?, onBack: () -> Unit) {
    val c = ScannerTheme.colors
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.s2, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.viewer_back), tint = c.text) }
        val labels = mapOf(ViewerTab.Image to stringResource(R.string.viewer_tab_image), ViewerTab.Word to stringResource(R.string.viewer_tab_word))
        ScanSegmented(ViewerTab.entries, tab, { labels.getValue(it) }, onTab, Modifier.weight(1f))
        Text(counter ?: "", style = Type.titleSm, color = c.text, modifier = Modifier.padding(horizontal = Space.s3))
    }
}

/** Страница на всю ширину с масштабом щипком (1–5×) и сдвигом; двойное касание — исходный масштаб. */
@Composable
private fun ZoomablePage(p: PageEntity, number: Int, image: suspend (PageEntity) -> ImageBitmap?) {
    val bmp by produceState<ImageBitmap?>(null, p) { value = image(p) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val ts = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val desc = stringResource(R.string.viewer_page_image, number)
    Box(
        Modifier.fillMaxSize().background(Color.White)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
            .transformable(ts, canPan = { scale > 1f })
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null) CircularProgressIndicator(color = ScannerTheme.colors.accent)
        else Image(b, null, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
    }
}

/** Текст страницы: абзацы с выравниванием, сомнительные слова — жёлтый фон. Покрыто тестом (annotated). */
fun annotated(p: Paragraph, doubtful: Color): AnnotatedString = buildAnnotatedString {
    val keepLines = p.align == Align.CENTER || p.align == Align.RIGHT
    p.lines.forEachIndexed { li, line ->
        if (li > 0) append(if (keepLines) "\n" else " ")
        line.words.forEachIndexed { wi, w ->
            if (wi > 0) append(" ")
            if (w.confidence < TextLayout.LOW_CONFIDENCE) withStyle(SpanStyle(background = doubtful)) { append(w.text) } else append(w.text)
        }
    }
}

@Composable
private fun WordPage(word: PageWord?, busy: Boolean, onRecognize: () -> Unit) {
    val c = ScannerTheme.colors
    if (word == null) {
        Column(Modifier.fillMaxSize().padding(Space.s6), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.viewer_word_empty), style = Type.bodyLg, color = c.text, textAlign = TextAlign.Center)
            if (busy) Row(Modifier.padding(top = Space.s4), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = Space.s2), color = c.accent)
                Text(stringResource(R.string.viewer_word_recognizing), color = c.textSecondary)
            } else ScanButton(stringResource(R.string.viewer_word_recognize), onRecognize, Modifier.padding(top = Space.s4))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(Color.White), contentPadding = PaddingValues(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        item { Text(stringResource(if (word.edited) R.string.viewer_word_edited else R.string.viewer_word_disclaimer), style = Type.caption, color = Color(0xFF6B7280)) }
        items(word.paragraphs) { p ->
            Text(
                annotated(p, Color(0xFFFFE58A)), style = Type.ocr, color = Color(0xFF111111), modifier = Modifier.fillMaxWidth(),
                textAlign = when (p.align) { Align.CENTER -> TextAlign.Center; Align.RIGHT -> TextAlign.End; Align.JUSTIFY -> TextAlign.Justify; Align.LEFT -> TextAlign.Start },
            )
        }
    }
}

@Composable
private fun BottomBar(onRotate: () -> Unit, onEdit: () -> Unit, onExtract: () -> Unit, onShare: () -> Unit, onAllText: () -> Unit, onDocx: () -> Unit, onExport: () -> Unit) {
    val c = ScannerTheme.colors
    var more by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(vertical = Space.s2), horizontalArrangement = Arrangement.SpaceEvenly) {
        BarButton(Icons.Rounded.RotateRight, stringResource(R.string.viewer_action_rotate), onRotate)
        BarButton(Icons.Rounded.Edit, stringResource(R.string.viewer_action_edit), onEdit)
        BarButton(Icons.Rounded.TextFields, stringResource(R.string.viewer_action_extract), onExtract)
        BarButton(Icons.Rounded.Share, stringResource(R.string.viewer_action_share), onShare)
        Box {
            BarButton(Icons.Rounded.MoreVert, stringResource(R.string.viewer_action_more)) { more = true }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.viewer_more_docx)) }, { more = false; onDocx() })
                DropdownMenuItem({ Text(stringResource(R.string.viewer_more_text)) }, { more = false; onAllText() })
                DropdownMenuItem({ Text(stringResource(R.string.viewer_more_export)) }, { more = false; onExport() })
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = ScannerTheme.colors
    TextButton(onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = c.text)
            Text(label, style = Type.captionSm, color = c.text, maxLines = 1)
        }
    }
}

