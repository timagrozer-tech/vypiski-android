package ru.scanner291.feature.documents

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object DocumentsRoute
@Serializable data class ViewerRoute(val docId: String)

fun NavGraphBuilder.documentsScreens(
    onOpenDocument: (String) -> Unit,
    onProcess: (docId: String, pageIndex: Int) -> Unit,
    onBack: () -> Unit,
) {
    composable<DocumentsRoute> { DocumentsRouteScreen(onOpenDocument) }
    composable<ViewerRoute> { ViewerRouteScreen(onProcess, onBack) }
}
