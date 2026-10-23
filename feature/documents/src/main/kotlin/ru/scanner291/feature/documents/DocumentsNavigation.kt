package ru.scanner291.feature.documents

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable data object DocumentsRoute
@Serializable data class ViewerRoute(val docId: String)

fun NavGraphBuilder.documentsScreens(onBack: () -> Unit) {
    composable<DocumentsRoute> { DocumentsScreen() }
    composable<ViewerRoute> { ViewerScreen(it.toRoute<ViewerRoute>().docId, onBack) }
}
