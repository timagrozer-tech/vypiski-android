package ru.scanner291.feature.editor

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable data class EditorRoute(val docId: String)
@Serializable data class OcrRoute(val docId: String)
@Serializable data class FioRoute(val docId: String)

fun NavGraphBuilder.editorScreens(onBack: () -> Unit) {
    composable<EditorRoute> { DocumentScopedScreen(DocScreen.Editor, it.toRoute<EditorRoute>().docId, onBack) }
    composable<OcrRoute> { DocumentScopedScreen(DocScreen.Ocr, it.toRoute<OcrRoute>().docId, onBack) }
    composable<FioRoute> { DocumentScopedScreen(DocScreen.Fio, it.toRoute<FioRoute>().docId, onBack) }
}
