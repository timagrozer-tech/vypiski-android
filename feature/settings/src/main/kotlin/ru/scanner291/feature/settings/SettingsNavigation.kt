package ru.scanner291.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object SettingsRoute
@Serializable data object WatermarkRoute

fun NavGraphBuilder.settingsScreens(onOpenWatermark: () -> Unit, onBack: () -> Unit) {
    composable<SettingsRoute> { SettingsRouteScreen(onOpenWatermark = onOpenWatermark) }
    composable<WatermarkRoute> { WatermarkRouteScreen(onBack = onBack) }
}
