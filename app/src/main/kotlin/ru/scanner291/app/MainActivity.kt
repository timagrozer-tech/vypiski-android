package ru.scanner291.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.scanner291.designsystem.ScannerTheme

/**
 * Единственная Activity. SplashScreen не удерживается (ТЗ v3.0): первый кадр — сразу «Главная».
 * Тема и Dynamic Color читаются из настроек (DataStore); до первого чтения — тёмная по умолчанию.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            ScannerTheme(mode = prefs.theme.toThemeMode(), dynamicColor = prefs.dynamicColor) {
                ScannerApp()
            }
        }
    }
}
