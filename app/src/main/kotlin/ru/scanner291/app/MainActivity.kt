package ru.scanner291.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.scanner291.core.storage.StartupRecovery
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.ScreenBox
import ru.scanner291.designsystem.ThemeMode
import javax.inject.Inject

/**
 * Единственная Activity (Compose). Навигация по 13 экранам — этап 4 (ТЗ v3.0);
 * сейчас экран честно сообщает, что это каркас, и не показывает неработающих кнопок.
 * SplashScreen не удерживается: первый кадр рисуется сразу.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var startupRecovery: StartupRecovery

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerTheme(mode = ThemeMode.SYSTEM) {
                val recovered by startupRecovery.recoveredOps.collectAsStateWithLifecycle()
                StageOneScreen(recovered)
            }
        }
    }
}

@Composable
private fun StageOneScreen(recoveredOps: Int) {
    val c = ScannerTheme.colors
    val t = ScannerTheme.type
    ScreenBox(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = t.display, color = c.text)
            Text(stringResource(R.string.app_tagline), style = t.body, color = c.textSecondary)
            GlassCard {
                Text(stringResource(R.string.build_stage_title), style = t.titleMd, color = c.text)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.build_stage_body), style = t.body, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.offline_badge), style = t.label, color = c.success)
                if (recoveredOps > 0) {
                    Text(stringResource(R.string.recovered_ops, recoveredOps), style = t.caption, color = c.warning)
                }
                Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = t.caption, color = c.textSecondary)
            }
        }
    }
}
