package ru.scanner291.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Type
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.scanner291.designsystem.ScannerTheme

/**
 * Единственная Activity. SplashScreen не удерживается (ТЗ v3.0): первый кадр — сразу «Главная».
 * Тема и Dynamic Color читаются из настроек (DataStore); до первого чтения — тёмная по умолчанию.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    /** Блокировка (этап 19): экран закрыт до подтверждения; повторно — после 30 с в фоне. */
    private var locked by mutableStateOf(true)
    private var backgroundAt by mutableLongStateOf(0L)

    override fun onStop() { super.onStop(); backgroundAt = System.currentTimeMillis() }

    override fun onStart() {
        super.onStart()
        if (backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > LOCK_AFTER_MS) locked = true
    }

    private fun unlock() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("291 Сканер")
            .setSubtitle("Подтвердите, что это вы")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            ScannerTheme(mode = prefs.theme.toThemeMode(), dynamicColor = prefs.dynamicColor) {
                // Блокировка включена, но устройство не может проверить владельца — не запираем пользователя
                val lockActive = prefs.appLock && locked && canAuthenticate()
                if (!lockActive) ScannerApp()
                else {
                    LaunchedEffect(Unit) { unlock() }
                    LockScreen(::unlock)
                }
            }
        }
    }

    private fun canAuthenticate() = BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    companion object {
        const val LOCK_AFTER_MS = 30_000L
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s4), modifier = Modifier.padding(Space.s6)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = ru.scanner291.designsystem.ScannerTheme.colors.accent)
            Text("Документы защищены", style = Type.titleSm, color = ru.scanner291.designsystem.ScannerTheme.colors.text)
            ScanButton("Разблокировать", onUnlock)
        }
    }
}
