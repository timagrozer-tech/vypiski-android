package ru.scanner291.feature.documents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.text.RussianPlural
import ru.scanner291.core.database.PageEntity
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.Radius
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Type

@Composable
internal fun ViewerRouteScreen(onProcess: (String, Int) -> Unit, onBack: () -> Unit, vm: ViewerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    ViewerScreen(state, vm::image, { i -> onProcess(vm.docId, i) }, onBack)
}

/** Просмотр документа: страницы-оригиналы сверху вниз (уменьшенные для экрана, файл не меняется). */
@Composable
fun ViewerScreen(
    state: ViewerState,
    image: suspend (PageEntity) -> ImageBitmap?,
    onProcess: (pageIndex: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        when (state) {
            ViewerState.Loading -> ScanTopBar(title = "Просмотр", onBack = onBack)
            ViewerState.NotFound -> {
                ScanTopBar(title = "Просмотр", onBack = onBack)
                EmptyState(Icons.Rounded.PictureAsPdf, "Документ не найден", "Возможно, он удалён или перемещён в корзину.")
            }
            is ViewerState.Ready -> {
                val pages = state.doc.orderedPages
                ScanTopBar(title = state.doc.doc.title, onBack = onBack) {
                    IconButton(onClick = { onProcess(0) }) {
                        Icon(Icons.Rounded.Crop, contentDescription = "Обработать: углы листа и улучшение", tint = ScannerTheme.colors.accentText)
                    }
                }
                Text(
                    RussianPlural.format(pages.size.toLong(), "страница", "страницы", "страниц"),
                    style = Type.body, color = ScannerTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.s4),
                )
                LazyColumn(
                    contentPadding = PaddingValues(Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Space.s4),
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { i, p -> PageView(p, i + 1, image) { onProcess(i) } }
                }
            }
        }
    }
}

@Composable
private fun PageView(page: PageEntity, number: Int, image: suspend (PageEntity) -> ImageBitmap?, onClick: () -> Unit) {
    val c = ScannerTheme.colors
    // Ключ — вся страница: после сохранения рецепта (углы, фильтр) изображение пересчитывается.
    val bmp by produceState<ImageBitmap?>(null, page) { value = image(page) }
    val shape = RoundedCornerShape(Radius.md)
    val ratio = bmp?.let { it.width.toFloat() / it.height }
        ?: if (page.width > 0 && page.height > 0) page.width.toFloat() / page.height else 3f / 4f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(shape).background(c.paper).border(1.dp, c.glassStroke, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Страница $number, открыть обработку" },
        ) {
            bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize()) }
        }
        Text("$number", style = Type.captionSm, color = c.textSecondary, modifier = Modifier.padding(top = Space.s1))
    }
}

