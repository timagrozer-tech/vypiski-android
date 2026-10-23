# 291 Сканер

Полностью офлайн-сканер документов для Android 10+. Kotlin · Jetpack Compose · Material 3 ·
CameraX · OpenCV · Tesseract (rus+eng) · PdfBox-Android · Room · WorkManager.

Без интернета, облака, регистрации, рекламы и телеметрии: разрешения `INTERNET` в приложении нет,
и сборка падает, если оно появится.

## Состояние
См. [`docs/STATUS.md`](docs/STATUS.md) и журнал [`docs/PROGRESS.md`](docs/PROGRESS.md).
Сейчас: ТЗ v3.0, этап 1 из 23 (аудит) завершён, Debug APK собирается в CI. Приложение пока не сканирует.

## Сборка
```bash
./gradlew :core:domain:test          # быстрые тесты бизнес-логики
./gradlew :app:verifyNoNetwork       # офлайн-гарантии
./gradlew :app:assembleDebug         # APK: app/build/outputs/apk/debug/
```
Нужны JDK 17 и Android SDK (compileSdk 36). Без Android SDK тесты бизнес-логики можно прогнать так:
`bash tools/localtest/run.sh`.

CI: `.github/workflows/android.yml` — тесты, проверки, Debug APK как артефакт.
Подпись Release — [`docs/SIGNING.md`](docs/SIGNING.md). Установка — [`docs/INSTALL.md`](docs/INSTALL.md).

## Документы
- [Аудит v3.0](docs/AUDIT.md) · [Архитектура](docs/ARCHITECTURE.md) · [ADR](docs/ADR.md) · [План с критериями](docs/PLAN.md)
- [Лицензии](docs/LICENSES.md) · [Ручной чек-лист](docs/QA_CHECKLIST.md)
