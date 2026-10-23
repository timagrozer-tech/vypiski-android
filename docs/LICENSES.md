# Сторонние компоненты и лицензии

Правило проекта: только лицензии, допускающие закрытое распространение без раскрытия кода
приложения (Apache-2.0, MIT, BSD, OFL для шрифтов). **Запрещены**: AGPL/GPL (iText 7, MuPDF,
Ghostscript), компоненты, загружающие модели или данные из сети.

| Компонент | Версия | Лицензия | Где используется | Статус |
|---|---|---|---|---|
| Kotlin stdlib, kotlinx.coroutines | 2.2.0 / 1.10.2 | Apache-2.0 | везде | подключено |
| AndroidX (Compose BOM, Activity, Lifecycle, Navigation, CameraX, Room, WorkManager, DataStore, Biometric, SplashScreen, ExifInterface, DocumentFile) | см. `gradle/libs.versions.toml` | Apache-2.0 | `:app`, `:core:*` | подключено |
| OpenCV (`org.opencv:opencv`) | 4.12.0 | Apache-2.0 | `:core:imaging` | подключено, код — этап 4 |
| Tesseract4Android (`cz.adaptech.tesseract4android:tesseract4android`, JitPack) | 4.9.0 | Apache-2.0 (Tesseract — Apache-2.0, Leptonica — BSD-2) | `:core:ocr` | подключено, код — этап 7 |
| tessdata_fast: `rus`, `eng`, `osd` | коммит `8741641` | Apache-2.0, текст: `licenses/tessdata_fast-LICENSE.txt` | `core/ocr/src/main/assets/tessdata` | встроено |
| PdfBox-Android (`com.tom-roush:pdfbox-android`) | 2.0.27.0 | Apache-2.0 | `:core:pdf` | подключено, код — этап 5 |
| Noto Sans (шрифт с кириллицей для текстового слоя PDF) | — | SIL OFL-1.1 | `:core:pdf` assets | план, этап 5 |
| JUnit 4 | 4.13.2 | EPL-1.0 (только тесты, в APK не входит) | тесты | подключено |

Перед релизом (этап 16): `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`,
сверка с этой таблицей; экран «Настройки → О приложении → Лицензии» показывает тот же список.
