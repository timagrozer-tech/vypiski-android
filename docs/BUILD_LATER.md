# Будущая сборка (выполнять только по отдельной команде)

Сборки **не запускаются автоматически**: оба workflow запускаются только вручную.

## Android (APK)
GitHub → Actions → **Build Vypiski APK** → Run workflow → ветка. Берётся новейший `Vypiski-Android-Stage-*-project.zip`,
JDK 17, Android SDK 35, Gradle 8.9. Подпись — постоянный отладочный ключ `app/vypiski-debug.keystore` (обновления
ставятся поверх). Проверка: в манифесте нет `INTERNET`.

## Windows 10/11 (установщик и переносная версия)
GitHub → Actions → **Build Vypiski for Windows** → Run workflow → ветка. Runner `windows-latest`, Node 20,
Electron 37.2.6, electron-builder 26: `Vypiski-Setup-<версия>.exe` и `Vypiski-Portable-<версия>.exe`.
Страница берётся из того же архива проекта.

## Перед сборкой
1. `tests/run_all.sh` — все проверки проходят.
2. Версии согласованы: `app/build.gradle` (versionCode/versionName), `NativeBridge.version()`, `<meta name="vypiski-version">`,
   `desktop/package.json`.
3. После сборки — проверка на устройстве: импорт папки и ZIP, фоновый импорт с выключенным экраном, создание выписки
   (SHA-256 до/после совпадает), «Поделиться», тёмная тема; на Windows — установка, перетаскивание папки, сохранение,
   печать в PDF, отсутствие сетевых подключений.
