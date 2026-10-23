package ru.scanner291.feature.scanner

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable data object CameraRoute
@Serializable data class ProcessingRoute(val docId: String)

fun NavGraphBuilder.scannerScreens(onBack: () -> Unit) {
    composable<CameraRoute> { CameraRouteScreen(onBack = onBack) }
    composable<ProcessingRoute> { entry ->
        ProcessingScreen(docId = entry.toRoute<ProcessingRoute>().docId, onBack = onBack)
    }
}
