package ru.scanner291.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.scanner291.core.camera.CameraController
import ru.scanner291.core.camera.CameraPreview
import ru.scanner291.core.camera.CameraStatus
import ru.scanner291.core.camera.FlashMode
import ru.scanner291.core.camera.FrameResult
import ru.scanner291.core.common.geometry.QuadTransforms
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.scanner291.core.camera.next
import ru.scanner291.core.camera.rememberCameraController
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.text.RussianPlural
import ru.scanner291.core.database.PageEntity
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.FioToggle
import ru.scanner291.designsystem.GlassLevel
import ru.scanner291.designsystem.GlassSurface
import ru.scanner291.designsystem.Radius
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanChip
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.ShutterButton
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type

@Composable
internal fun CameraRouteScreen(onBack: () -> Unit, onDone: (String) -> Unit, vm: ScannerViewModel = hiltViewModel()) {
    val nameByFio by vm.nameByFio.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val auto by vm.autoCapture.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val autoShot by vm.autoShot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; asked = true }
    LaunchedEffect(error) { if (error != null) { delay(4_000); vm.clearError() } }

    CameraScreen(
        permissionGranted = granted,
        permissionDenied = asked && !granted,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        nameByFio = nameByFio,
        onNameByFioChange = vm::setNameByFio,
        mode = mode,
        onModeChange = vm::setMode,
        session = session,
        busy = busy,
        error = error,
        onShutter = { controller -> vm.onShutter { file -> controller.takePicture(file) } },
        autoCapture = auto,
        onAutoCaptureChange = vm::setAutoCapture,
        frame = frame,
        autoShot = autoShot,
        onFrame = vm::onFrame,
        onRetake = vm::retakeLast,
        onDone = { session.docId?.let(onDone) },
        thumbnail = vm::thumbnail,
        onBack = onBack,
    )
}

private fun flashIcon(m: FlashMode): ImageVector = when (m) {
    FlashMode.Off -> Icons.Rounded.FlashOff
    FlashMode.Auto -> Icons.Rounded.FlashAuto
    FlashMode.On -> Icons.Rounded.FlashOn
    FlashMode.Torch -> Icons.Rounded.FlashlightOn
}

/**
 * Экран сканирования (README CameraControls): фон `camera-bg`, превью на весь экран, панели
 * `glass-strong` только сверху и снизу. Сверху — «Назад», вспышка, режимы; снизу — галочка
 * «Назвать файл по ФИО», миниатюра последнего снимка со счётчиком, затвор, «Готово».
 * Каждый снимок сразу сохраняется как неизменяемый оригинал — сворачивание или сбой его не теряют.
 */
@Composable
fun CameraScreen(
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    nameByFio: Boolean,
    onNameByFioChange: (Boolean) -> Unit,
    mode: ScanMode,
    onModeChange: (ScanMode) -> Unit,
    session: SessionUi,
    busy: Boolean,
    error: String?,
    onShutter: (CameraController) -> Unit,
    autoCapture: Boolean,
    onAutoCaptureChange: (Boolean) -> Unit,
    frame: FrameUi?,
    autoShot: Int,
    onFrame: (FrameResult) -> Unit,
    onRetake: () -> Unit,
    onDone: () -> Unit,
    thumbnail: suspend (PageEntity) -> ImageBitmap?,
    onBack: () -> Unit,
) {
    val c = ScannerTheme.colors
    Box(Modifier.fillMaxSize().background(c.cameraBg)) {
        val controller = if (permissionGranted) rememberCameraController(onFrame = onFrame) else null
        // Автоснимок: стабилизатор разрешил — нажимаем затвор (каждое новое значение счётчика — один снимок).
        // Номер последнего обработанного автоснимка переживает пересоздание экрана — без лишних снимков.
        var handledShot by rememberSaveable { mutableIntStateOf(autoShot) }
        LaunchedEffect(autoShot) {
            if (autoShot > handledShot) { handledShot = autoShot; controller?.let(onShutter) }
        }
        if (controller != null) {
            CameraPreview(controller, Modifier.fillMaxSize())
            if (controller.analysisActive) DocumentOverlay(frame, Modifier.fillMaxSize())
            when (controller.status) {
                CameraStatus.NoCamera -> CameraMessage("Камера не найдена", "Документы можно импортировать из файлов на главном экране.")
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

        // Верхняя панель: назад, вспышка, режимы.
        GlassSurface(Modifier.fillMaxWidth().align(Alignment.TopCenter), level = GlassLevel.Bar, shape = RoundedCornerShape(0.dp)) {
            Column(Modifier.statusBarsPadding().fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(Space.s1)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    if (controller != null && controller.analysisActive) {
                        ScanChip("Авто", selected = autoCapture, onClick = { onAutoCaptureChange(!autoCapture) }, checkmark = true)
                    }
                    if (controller != null && controller.hasFlash) {
                        IconButton(onClick = { controller.changeFlash(controller.flash.next()) }) {
                            Icon(flashIcon(controller.flash), contentDescription = "Вспышка: ${controller.flash.label}", tint = Color.White)
                        }
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    items(ScanMode.entries, key = { it.name }) { m ->
                        ScanChip(m.title, selected = m == mode, onClick = { onModeChange(m) })
                    }
                }
            }
        }

        // Нижняя панель.
        GlassSurface(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            level = GlassLevel.Bar, shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        ) {
            Column(Modifier.navigationBarsPadding().padding(vertical = Space.s2)) {
                if (error != null) StatusLine(Status.DANGER, error, Modifier.padding(horizontal = Space.s4))
                else frame?.hint?.let {
                    Text(it, style = Type.label, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally).padding(Space.s1))
                }
                FioToggle(checked = nameByFio, onChange = onNameByFioChange, onCamera = true)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        session.lastPage?.let { LastScan(it, session.pageCount, thumbnail, onDone) }
                    }
                    ShutterButton(
                        progress = frame?.progress,
                        onClick = { controller?.let(onShutter) },
                        enabled = controller?.status == CameraStatus.Running && !busy,
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (session.pageCount > 0) ScanButton("Готово", onDone, variant = ButtonVariant.Tonal)
                    }
                }
                if (session.pageCount > 0) {
                    Text(
                        "Переснять последнюю",
                        style = Type.label, color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(Radius.sm))
                            .clickable(role = Role.Button, enabled = !busy, onClick = onRetake).padding(Space.s3),
                    )
                }
            }
        }
    }
}

/** Миниатюра последнего снимка со счётчиком страниц; нажатие — к документу. */
@Composable
private fun LastScan(page: PageEntity, count: Int, thumbnail: suspend (PageEntity) -> ImageBitmap?, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, page.id) { value = thumbnail(page) }
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        Modifier.size(width = 52.dp, height = 68.dp).clip(shape).background(Color.White).border(2.dp, Color.White, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Последний снимок, ${RussianPlural.format(count.toLong(), "страница", "страницы", "страниц")}" },
    ) {
        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(CircleShape).background(ScannerTheme.colors.accent),
            contentAlignment = Alignment.Center,
        ) { Text("$count", style = Type.captionSm, color = Color.White) }
    }
}

/**
 * Рамка найденного документа поверх превью: линия 2 dp `accent`, заливка 8 %, уголки — без свечения.
 * Координаты кадра проецируются на экран так же, как превью (FILL_CENTER, общий формат 4:3).
 */
@Composable
private fun DocumentOverlay(frame: FrameUi?, modifier: Modifier) {
    val q = frame?.quad ?: return
    val accent = ScannerTheme.colors.accent
    Canvas(modifier) {
        val pts = q.points.map {
            QuadTransforms.fillCenter(it, frame.frameW.toDouble(), frame.frameH.toDouble(), size.width.toDouble(), size.height.toDouble())
        }.map { Offset(it.x.toFloat(), it.y.toFloat()) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
        drawPath(path, accent.copy(alpha = 0.08f))
        drawPath(path, accent, style = Stroke(width = 2.dp.toPx()))
        pts.forEach { drawCircle(accent, radius = 5.dp.toPx(), center = it) }
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
