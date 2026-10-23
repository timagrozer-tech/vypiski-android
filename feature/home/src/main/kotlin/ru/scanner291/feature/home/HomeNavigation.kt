package ru.scanner291.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

fun NavGraphBuilder.homeScreen(
    onScan: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenPdfTools: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBatch: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(onScan, onOpenDocuments, onOpenPdfTools, onOpenSearch, onOpenBatch)
    }
}
