package ru.scanner291.feature.search

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object SearchRoute

fun NavGraphBuilder.searchScreen(onOpenDocument: (String) -> Unit = {}) {
    composable<SearchRoute> { SearchRouteScreen(onOpenDocument) }
}
