package ru.scanner291.feature.scanner

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object CameraRoute
@Serializable data class ProcessingRoute(val docId: String, val pageIndex: Int = 0)

fun NavGraphBuilder.scannerScreens(onBack: () -> Unit, onDocumentReady: (String) -> Unit) {
    composable<CameraRoute> { CameraRouteScreen(onBack = onBack, onDone = onDocumentReady) }
    composable<ProcessingRoute> { ProcessingRouteScreen(onBack = onBack) }
}
