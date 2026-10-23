package ru.scanner291.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.scanner291.designsystem.Destination
import ru.scanner291.designsystem.Radius
import ru.scanner291.designsystem.ScanBottomNav
import ru.scanner291.designsystem.ScreenBox
import ru.scanner291.designsystem.backdropBlur
import ru.scanner291.designsystem.backdropSource
import ru.scanner291.designsystem.rememberBackdrop
import ru.scanner291.feature.batch.BatchSaveRoute
import ru.scanner291.feature.batch.batchSaveScreen
import ru.scanner291.feature.documents.DocumentsRoute
import ru.scanner291.feature.documents.documentsScreens
import ru.scanner291.feature.editor.editorScreens
import ru.scanner291.feature.export.PdfToolsRoute
import ru.scanner291.feature.export.pdfToolsScreen
import ru.scanner291.feature.home.HomeRoute
import ru.scanner291.feature.home.homeScreen
import ru.scanner291.feature.scanner.CameraRoute
import ru.scanner291.feature.scanner.scannerScreens
import ru.scanner291.feature.search.SearchRoute
import ru.scanner291.feature.search.searchScreen
import ru.scanner291.feature.settings.SettingsRoute
import ru.scanner291.feature.settings.WatermarkRoute
import ru.scanner291.feature.settings.settingsScreens

/** Корневой раздел нижней навигации для текущего экрана; null — вложенный экран (панели нет). */
internal fun NavDestination?.topLevel(): Destination? = when {
    this == null -> null
    hasRoute(HomeRoute::class) -> Destination.Home
    hasRoute(DocumentsRoute::class) -> Destination.Documents
    hasRoute(SearchRoute::class) -> Destination.Search
    hasRoute(SettingsRoute::class) -> Destination.Settings
    else -> null
}

internal fun NavHostController.navigateTopLevel(d: Destination) {
    val route: Any = when (d) {
        Destination.Home -> HomeRoute
        Destination.Documents -> DocumentsRoute
        Destination.Search -> SearchRoute
        Destination.Settings -> SettingsRoute
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Навигация по 13 экранам ТЗ v3.0: Главная, Камера, Обработка, Редактор страниц, OCR, ФИО,
 * Мои документы, Просмотр, Поиск, PDF-инструменты, Водяной знак, Настройки, Пакетное сохранение.
 * Старт — сразу «Главная», без заставки.
 */
@Composable
fun ScannerApp(navController: NavHostController = rememberNavController()) {
    val entry by navController.currentBackStackEntryAsState()
    val top = entry?.destination.topLevel()
    val backdrop = rememberBackdrop()
    val back: () -> Unit = { navController.popBackStack() }
    ScreenBox(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize().backdropSource(backdrop),
        ) {
            homeScreen(
                onScan = { navController.navigate(CameraRoute) },
                onOpenDocuments = { navController.navigateTopLevel(Destination.Documents) },
                onOpenPdfTools = { navController.navigate(PdfToolsRoute) },
                onOpenSearch = { navController.navigateTopLevel(Destination.Search) },
                onOpenBatch = { navController.navigate(BatchSaveRoute) },
            )
            scannerScreens(onBack = back)
            editorScreens(onBack = back)
            documentsScreens(onBack = back)
            searchScreen()
            pdfToolsScreen(onBack = back)
            settingsScreens(onOpenWatermark = { navController.navigate(WatermarkRoute) }, onBack = back)
            batchSaveScreen(onBack = back)
        }
        if (top != null) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                ScanBottomNav(
                    selected = top,
                    onSelect = { navController.navigateTopLevel(it) },
                    modifier = Modifier.backdropBlur(backdrop, RoundedCornerShape(Radius.xl)),
                )
            }
        }
    }
}
