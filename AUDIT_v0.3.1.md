# Аудит v0.3.0 → исправления v0.3.1

Дата: 2026-11-03. APK/AAB не собирались.

## Среда проверки

- Android SDK и Google Maven **недоступны** в среде аудита (сетевая политика отклоняет `dl.google.com`, `maven.google.com`, `repo1.maven.org`). Поэтому **Android-компиляция не выполнена**.
- Выполнено: компиляция чистых Kotlin-файлов (`BurstDenoiser.kt`, `CameraPolicy.kt`) и тестов компилятором Kotlin 2.0.21 (из дистрибутива Gradle 8.14.3) + запуск на JVM с JUnit-совместимыми заглушками: **21/21 тест прошёл**.
- Синтаксическая проверка Android-файлов (`MainActivity.kt`, `BurstCaptureController.kt`, `GridOverlay.kt`): синтаксических ошибок нет; остальные сообщения — неразрешённые Android/CameraX-типы из-за отсутствия SDK.
- Версии API сверены с официальными release notes CameraX: 1.6.2 — актуальный стабильный выпуск (26.08.2026).
- Для проверки на GitHub добавлен ручной workflow `verify-sources.yml`: `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug` — **без упаковки APK**.

## Найденные проблемы и статус

| # | Приоритет | Проблема | Исправление |
|---|---|---|---|
| 1 | ~~P0 сборка~~ **ошибка аудита** | ~~`ExtensionSessionConfig` появился только в 1.7.0-alpha02~~. **Поправка 0.3.7:** по сигнатурам `camera-extensions/api/1.6.0-rc01.txt` класс и `bindToLifecycle(…, SessionConfig)` есть в 1.6 (с 1.6.0-beta01) — исходный код v0.3 здесь компилировался бы. Вывод был сделан по пересказу release notes, без сверки сигнатур | Замена на `getExtensionEnabledCameraSelector()` тоже корректна в 1.6 и оставлена (проще сочетается с `ImageAnalysis`); в 1.7 этот метод помечен `@Deprecated` — при обновлении перейти на `ExtensionSessionConfig` |
| 2 | ~~P0 сборка (риск)~~ **P2, предупреждение** | `android { kotlinOptions {} }` устарел с Kotlin 2.0. **Уточнение 0.3.9:** по исходнику KGP v2.2.20 (`KotlinAndroidPlugin.kt`, KT-77288) это `DeprecationLevel.WARNING` — исходный код v0.3 собирался бы с предупреждением | `kotlin { compilerOptions { jvmTarget = JVM_17 } }` |
| 3 | P0 сборка (риск) | `Preview.surfaceProvider = …` (синтетическое свойство зависит от наличия геттера); `OutputFileResults.imageFormat`; устаревший `QualitySelector.getSupportedQualities` | `setSurfaceProvider()`; DNG определяется по MIME, который мы сами задали; `Recorder.getVideoCapabilities(info).getSupportedQualities(DynamicRange.SDR)` |
| 4 | P0 медиа | Ориентация снимка бралась только из поворота экрана. При выключенном автоповороте альбомный снимок сохранялся как портретный | `OrientationEventListener` → `targetRotation` для ImageCapture/VideoCapture, с гистерезисом 10° (`CameraPolicy.surfaceRotationFor`) |
| 5 | P0 СТЕК ×3 | Поворот телефона во время серии менял размер кадров → `denoise` падал с непонятной ошибкой | Поворот не обновляется, пока идёт съёмка; явная проверка размеров кадров |
| 6 | P0 СТЕК ×3 | `cancel()` вызывал `shutdownNow()`: CameraX мог не доставить колбэк с `ImageProxy` в остановленный executor (утечка буфера камеры), прерывание потока во время записи MediaStore | Executor останавливается только когда нет кадра «в полёте»; без прерываний; гарантированное удаление `IS_PENDING`; `OutOfMemoryError` обрабатывается |
| 7 | P0 видео | При сворачивании CameraX завершает запись с `ERROR_SOURCE_INACTIVE` (ON_STOP наступает до `onStop`), файл при этом **сохранён**, но приложение показывало «Ошибка видео» и не давало его открыть | `CameraPolicy.isVideoKept`: SOURCE_INACTIVE / лимиты размера и длительности / нехватка места → «ролик сохранён» |
| 8 | P0 вспышка | Фонарик гас при каждом rebind, кнопка показывала ON; в видео был бессмысленный AUTO; у фронтальной камеры без вспышки кнопка была активна | Восстановление torch после bind; в видео OFF↔ON; `hasFlashUnit()` блокирует кнопку |
| 9 | P1 жизненный цикл | Поворот экрана сбрасывал объектив, режим видео, расширение, СТЕК ×3, качество видео | `onSaveInstanceState`; расширение восстанавливается после готовности `ExtensionsManager` |
| 10 | P1 разрешения | После отказа в камере не было пути повторить запрос; разрешение из настроек не подхватывалось | Повторный запрос по кнопке «СНЯТЬ»; инициализация в `onStart` |
| 11 | P1 запуск | Превью ждало инициализации OEM Extensions | Превью открывается сразу, расширения подгружаются параллельно |
| 12 | P1 PRO | Открытие диалога PRO сразу выключало HDR/Night, даже при «Отмена»; PRO можно было открыть во время таймера/съёмки | Расширение выключается только при «Применить»; блокировка во время съёмки; вспышка отключена при ручной экспозиции |
| 13 | P2 EV | После rebind ползунок EV всегда показывал 0, хотя камера могла сохранить индекс | Отображается реальный `exposureCompensationIndex` |
| 14 | P2 | Toast после уничтожения Activity; нет `DATE_TAKEN` у СТЕК-файлов; версия в диагностике захардкожена; уровень HW только числом | Защита `message()`; `DATE_TAKEN`; `BuildConfig.VERSION_NAME`; имена уровней + наличие вспышки |
| 15 | P2 | `enableEdgeToEdge()` вместо устаревших `window.statusBarColor` (игнорируются на targetSdk 35) | Сделано |

## Сохранено без изменений функциональности

JPEG/RAW+JPEG/Ultra HDR, OEM HDR/Night/Bokeh, видео с выбором качества, зум, фокус касанием, EV, PRO ISO/выдержка, таймер, сетка, сохранение настроек, диагностика, СТЕК ×3 (алгоритм `BurstDenoiser` не менялся). Автосборка при push не включена.

## Сверка с сигнатурами API (0.3.7)

`tools/api-check/check-apis.sh` проверяет все 93 вызова AndroidX CameraX по официальным файлам публичного API (`androidx/androidx`, `camera/*/api/1.6.0-rc01.txt`; API релиз-кандидата совпадает с 1.6.x). Результат: **93 из 93 найдены**. Попутно подтверждено:
- пункт 3 был нужен: у `Preview` в 1.6 нет `getSurfaceProvider()`, поэтому запись `it.surfaceProvider = …` из v0.3 не компилировалась бы;
- `QualitySelector.getSupportedQualities(CameraInfo)` в 1.6 помечен `@Deprecated` — замена оправдана;
- `OutputFileResults.getImageFormat()` в 1.6 есть (определение DNG по MIME тоже корректно);
- `enableEdgeToEdge`, `RequestPermission`, `ContextCompat`, `ViewCompat`, `WindowInsetsCompat.Type.displayCutout()` есть в activity 1.10 / core 1.16.

Для CameraX 1.7.0-rc01: `getExtensionEnabledCameraSelector` — `@Deprecated`; `ImageProxy.PlaneProxy` теперь наследует `androidx.camera.common.ImagePlane` (методы `getRowStride/getPixelStride` переехали в родителя — проверить при обновлении).

## Сверка платформы и инструментов сборки (0.3.9)

- **Android framework:** `tools/api-check/check-framework-apis.py` — 89 из 89 вызовов найдены в официальных сигнатурах AOSP `android16-release` (`frameworks/base/core/api/current.txt`, API 36) и модуля MediaProvider (`MediaStore`). Значения констант, на которые опирается чистый код, совпадают: `CONTROL_AWB_MODE_*` = 0…8, `INFO_SUPPORTED_HARDWARE_LEVEL_*` = 0…4, `ImageFormat.JPEG` = 256, `RAW_SENSOR` = 32, клавиши громкости 24/25, `KEYCODE_CAMERA` = 27. Ограничение: файл не содержит уровни «added in» и не проверяет, в каком классе найден метод; всё используемое — API ≤ 29 (minSdk), это дополнительно проверит lint в Verify.
- **AGP 8.10.x:** минимум Gradle 8.11.1, JDK 17, максимум API 36 (release notes AGP 8.10) — конфигурация проекта совпадает.
- **Kotlin 2.2 ↔ AGP:** 7.3.1–8.10 (таблица «Kotlin support» Android) — AGP 8.10.1 на верхней границе.
- **Gradle 8.11.1 на JDK 17:** поддерживается (таблица совместимости Gradle).
- **DSL `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`:** `KotlinAndroidProjectExtension.compilerOptions: KotlinJvmCompilerOptions`, `JvmTarget.JVM_17` — есть в KGP v2.2.20 (исходники и API-дамп плагина).

Остаётся непроверенным без настоящей сборки: разрешение зависимостей из Google Maven, ресурсы/манифест (aapt2), D8/R8 и lint.

## Остаточные риски (не проверено)

- Полная Android-компиляция: запустить вручную **Verify sources (no APK)** в GitHub Actions.
- Наличие `OUTPUT_FORMAT_RAW_JPEG` и двухфайлового `takePicture(raw, jpeg, …)` подтверждается только сборкой с CameraX 1.6.2.
- Поведение OEM Extensions, RAW, Ultra HDR, 4K и дополнительных объективов на REVVL 7 Pro / Android 16 неизвестно до теста на телефоне.
- Фронтальная камера: зеркалирование итогового JPEG и СТЕК ×3 не настраивается.
- Видео при повороте телефона во время записи сохраняет ориентацию начала ролика (ограничение CameraX).
