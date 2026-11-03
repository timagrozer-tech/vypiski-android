# Offline check (без Android SDK, без APK)

`./check.sh` — для сред без доступа к Google Maven. Делает две вещи:

1. Компилирует чистые модули (`BurstDenoiser`, `CameraPolicy`, `CameraText`, `ManualExposure`, `SessionPlanner`, `CameraUiState`) и все unit-тесты и запускает их на JVM с JUnit-совместимыми заглушками.
2. Проверяет типы **всех** Kotlin-файлов приложения против Java-заглушек того подмножества Android/CameraX API, которое использует проект (`gen_android_stubs.py`).

Ограничение: заглушки написаны вручную по документации. Зелёный результат значит, что модули согласованы между собой, но **не** доказывает совместимость с реальными SDK 36 и CameraX 1.6.2. Настоящая проверка — ручной workflow **Verify sources (no APK)**. При использовании нового API добавь его в `gen_android_stubs.py`.

`./bench.sh [путь/к/BurstDenoiser.kt]` — синтетический A/B алгоритма СТЕК (шум, сдвиг, движение, дрейф экспозиции, скорость) на JVM. Не является замером на телефоне.

Требования: Java 17+, `kotlin-compiler-embeddable` (входит в дистрибутив Gradle; путь задаётся `KOTLIN_LIB`, по умолчанию `/opt/gradle/lib`).
