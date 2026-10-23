package ru.scanner291.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.camera.CameraPreview
import ru.scanner291.core.camera.CameraStatus
import ru.scanner291.core.camera.rememberCameraPreviewController
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.FioToggle
import ru.scanner291.designsystem.GlassLevel
import ru.scanner291.designsystem.GlassSurface
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Type

@Composable
internal fun CameraRouteScreen(onBack: () -> Unit, vm: ScannerViewModel = hiltViewModel()) {
    val nameByFio by vm.nameByFio.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; asked = true }
    CameraScreen(
        permissionGranted = granted,
        permissionDenied = asked && !granted,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        nameByFio = nameByFio,
        onNameByFioChange = vm::setNameByFio,
        onBack = onBack,
    )
}

/**
 * Экран камеры: фон `camera-bg`, превью на весь экран, панели `glass-strong` только сверху и снизу.
 * Этап 4: превью и галочка «Назвать файл по ФИО». Съёмка, рамка документа, вспышка и режимы — этап 5–6;
 * до тех пор кнопки затвора нет, а внизу честно написано, что съёмка ещё недоступна.
 */
@Composable
fun CameraScreen(
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    nameByFio: Boolean,
    onNameByFioChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val c = ScannerTheme.colors
    Box(Modifier.fillMaxSize().background(c.cameraBg)) {
        if (permissionGranted) {
            val controller = rememberCameraPreviewController()
            CameraPreview(controller, Modifier.fillMaxSize())
            when (controller.status) {
                CameraStatus.NoCamera -> CameraMessage("Камера не найдена", "Документы можно будет импортировать из файлов.")
                CameraStatus.Failed -> CameraMessage("Камера недоступна", "Возможно, её использует другое приложение.")
                else -> Unit
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.padding(Space.s4)) {
                    EmptyState(
                        icon = Icons.Rounded.PhotoCamera,
                        title = "Нужен доступ к камере",
                        text = if (permissionDenied) {
                            "Разрешение не выдано. Его можно включить в настройках Android → Приложения → 291 Сканер."
                        } else "Снимки остаются на устройстве и никуда не отправляются.",
                        action = { ScanButton("Разрешить доступ", onRequestPermission) },
                    )
                }
            }
        }
        // Верхняя панель
        GlassSurface(
            Modifier.fillMaxWidth().align(Alignment.TopCenter),
            level = GlassLevel.Bar, shape = RoundedCornerShape(0.dp),
        ) {
            Box(Modifier.statusBarsPadding().fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.padding(Space.s1)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
            }
        }
        // Нижняя панель
        GlassSurface(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            level = GlassLevel.Bar, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.navigationBarsPadding().padding(vertical = Space.s2)) {
                FioToggle(checked = nameByFio, onChange = onNameByFioChange, onCamera = true)
                Text(
                    "Съёмка появится в следующей сборке (этап 5).",
                    style = Type.caption, color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                )
            }
        }
    }
}

@Composable
private fun CameraMessage(title: String, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.padding(Space.s4)) {
            EmptyState(icon = Icons.Rounded.NoPhotography, title = title, text = text)
        }
    }
}
