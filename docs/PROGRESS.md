# Журнал разработки

Читать первым в каждой новой сессии. Последняя запись — сверху.

## 2026-10-23 — Этап 1 завершён (проверки сборки — в CI)

**Сделано на этапе 1**
- Модуль `:app` (его не было, хотя он подключён в `settings.gradle.kts` — Gradle не сконфигурировался бы):
  `build.gradle.kts`, манифест, `Scanner291App` (откат незавершённых операций и очистка временных
  файлов при старте), `MainActivity` (заставка + честный экран «каркас этапа 1»), ресурсы, значок,
  правила R8, ABI-сплиты + universal APK.
- Офлайн-гарантии: `INTERNET` удаляется в манифесте; `verifyNoInternet<Variant>` проверяет
  объединённый манифест и подключена к `assemble`; `verifyNoNetwork` = манифесты + запрещённые SDK.
- Резервное копирование отключено: `allowBackup=false`, `backup_rules.xml` (API 29–30),
  `data_extraction_rules.xml` (API 31+).
- OCR-модели `rus/eng/osd` (tessdata_fast @ 8741641, Apache-2.0) встроены в
  `core/ocr/src/main/assets/tessdata`, суммы в `core/ocr/tessdata.sha256`, задача `verifyTessdata`
  перед каждой сборкой (ADR-0011).
- Подпись Release из `keystore.properties` или переменных окружения; ключей в репозитории нет.
- CI `.github/workflows/android.yml`: domain-тесты, verifyTessdata, unit-тесты, verifyNoNetwork,
  lint, Debug APK + SHA256SUMS; инструментальные тесты на эмуляторе без сети; ручной Release
  с подписью из секретов. Dependabot.
- Документы: ADR-0011, ADR-0012, `LICENSES.md`, `SIGNING.md`, `INSTALL.md`, `QA_CHECKLIST.md`,
  `STATUS.md`, `README.md`.
- Исправлено: корневая задача `verifyNoNetworkDependencies` помечена несовместимой с
  configuration cache (иначе падала бы при включённом кэше).

**Проверено фактически (2026-10-23, в среде разработки)**
- `tools/localtest/run.sh`: 71 JVM-тест `:core:domain` — пройдено 71, упало 0.
- Все модули из `settings.gradle.kts` существуют; 17 XML и 2 YAML разбираются без ошибок.
- `INTERNET` не объявлен ни в одном манифесте; в Kotlin-коде нет сетевых классов.
- SHA-256 моделей совпадают с `tessdata.sha256`.
- Регулярное выражение `verifyNoInternet` и разбор `tessdata.sha256` проверены на образцах.

**НЕ проверено (нужен CI или телефон)**
- Gradle-конфигурация и компиляция Android-модулей: в среде разработки закрыты Maven Central и
  Google Maven (HTTP 403 от прокси), Android SDK отсутствует. Первый запуск CI покажет ошибки,
  если они есть.
- Внешний вид заставки и экрана, установка APK.

**Известные ограничения**
- Код `:app` и `:core:designsystem` ни разу не компилировался.
- `OperationJournal.recover` при откате удаляет выходы операции. Для операций переноса
  (корзина) нужен ROLL_FORWARD — заложить на этапе 6, иначе возможна потеря.
- Репозиторий не инициализирован как git и никуда не загружен — для CI его нужно разместить на GitHub.

**Следующий шаг — этап 2:** навигация по 12 экранам + окно сохранения по ФИО, Compose UI-тест
маршрутов, состояние галочки «Назвать файл по ФИО» в DataStore.

## 2026-10-23 — предыдущая сессия (до журнала)
Созданы `ARCHITECTURE.md`, `ADR.md` (0001–0010), `PLAN.md`, модули `:core:*` (сборочные файлы),
`:core:domain` с ФИО, именованием, водяным знаком, геометрией, автозатвором, CER/WER, хранилищем
и 71 тестом, `:core:designsystem` (токены и компоненты), локальный прогон тестов без Gradle.
