# 291 Сканер — архитектура

Версия документа: 1.0 (Этап 1). Целевая платформа: Android 10+ (API 29), targetSdk 36.

## 1. Цели и ограничения

| Требование | Как обеспечено архитектурно |
|---|---|
| Полностью офлайн | В манифесте нет `INTERNET`; CI-проверка `verifyNoNetwork` падает, если разрешение появилось в объединённом манифесте любого варианта сборки; нет сетевых SDK в зависимостях (проверка по списку запрещённых групп). |
| Оригиналы неизменны | Хранилище «append-only» для оригиналов: файл пишется один раз, затем помечается read-only; все правки — в отдельных производных файлах (`processed/`, `export/`). Редактирование описывается «рецептом» (JSON) — поэтому любое изменение обратимо. |
| Нет потери документов | Атомарная запись (temp → fsync → rename), журнал операций (WAL-подобный) с восстановлением при старте, корзина вместо удаления, Room в режиме WAL. |
| Нет перезаписи | Все имена проходят через `UniqueNameResolver`; перезапись возможна только по явному подтверждению пользователя (флаг `OverwritePolicy.CONFIRMED`). |
| Производительность | Изображения обрабатываются потоково постранично, ограниченные очереди (`Semaphore`/`limitedParallelism`), миниатюры отдельно, PDF пишется постранично с освобождением памяти. |
| Проверяемость | Вся бизнес-логика без Android-зависимостей — в чистых Kotlin/JVM-модулях `:core:common`, `:core:intelligence`, `:core:quality`, покрыта unit-тестами. |

## 2. Модули (ТЗ v3.0, ч. XX)

Сборка: `build-logic` с convention-плагинами `scanner291.*` (по образцу Now in Android),
версии — `gradle/libs.versions.toml` (ADR-0013). DI — Hilt (ADR-0014).

**Чистые Kotlin/JVM-модули** (без Android API, тесты без эмулятора, `tools/localtest/run.sh`):

| Модуль | Содержимое |
|---|---|
| `:core:common` | модели и рецепт правок, геометрия четырёхугольника и гомография, планировщик водяного знака, атомарная запись, неизменяемые оригиналы, журнал операций |
| `:core:intelligence` | ФИО (падежи, инициалы, несколько лиц, уверенность), имена файлов и `(2)/(3)`, пакетное именование; далее — реквизиты документов |
| `:core:quality` | метрики кадра и автозатвор; CER/WER; далее — Quality Gate |
| `:core:testing` | общие помощники тестов |

**Android-библиотеки:**

| Модуль | Назначение | Этап наполнения |
|---|---|---|
| `:core:designsystem` | токены и компоненты Liquid Glass | 3 |
| `:core:storage` | раскладка файлов, восстановление при старте (Hilt), SAF, DataStore | 2 → 14 |
| `:core:database` | Room: документы, страницы, теги, FTS OCR, журнал | 14 |
| `:core:security` | Keystore, хранилище, биометрия, проверка URI | 19 |
| `:core:camera` | CameraX | 5 |
| `:core:imageprocessing` | OpenCV: границы, перспектива, базовые фильтры | 6–7, 9 |
| `:core:hybrid` | HybridDocumentEnhancer (Hybrid HD Pro) | 8 |
| `:core:ocr` | Tesseract, встроенные модели | 10 |
| `:core:pdf` | PdfBox-Android, рендер водяного знака | 12–13 |
| `:core:batch` | Smart Batch Engine (WorkManager + Hilt) | 15 |
| `:core:performance` | Adaptive Performance | 20 |

**Фичи** (`scanner291.android.feature` = Compose + Hilt + ViewModel + навигация):
`home`, `scanner`, `editor`, `documents`, `search`, `export`, `settings`, `batch`, `diagnostics`.
Модуль, у которого ещё нет кода, содержит только сборочный файл — экранов-заглушек нет.

Граф (стрелка — «зависит от»): `app → feature:* → core:designsystem, core:common`;
`core:intelligence, core:quality → core:common`; Android-модули `core:* → core:common`;
`core:hybrid → core:imageprocessing`; `core:batch → core:storage`; `core:camera → core:quality`.
Фичи друг от друга не зависят; циклов нет.

## 3. Слои внутри фич

`Screen (Compose) → ViewModel (StateFlow<UiState>) → UseCase/Orchestrator → Repository/Engine`.
Однонаправленный поток данных: UI шлёт `Intent`, ViewModel публикует неизменяемый `UiState`.
DI — ручной `AppContainer` (без Hilt: меньше kapt/ksp-нагрузки и магии; см. ADR-0006).

## 4. Хранение

```
filesDir/
  docs/<docId>/
     original/<pageId>.jpg        — оригинал, read-only, никогда не меняется
     processed/<pageId>.jpg       — результат конвейера по рецепту (можно пересоздать)
     thumbs/<pageId>.webp         — миниатюра
     recipe/<pageId>.json         — рецепт обработки: углы, поворот, фильтр, яркость, контраст,
                                    аннотации, закрашивания — источник обратимости
  vault/                          — зашифрованные документы (AES-GCM, ключ в Keystore)
  journal/                        — журнал незавершённых операций
  trash/<docId>/                  — корзина (30 дней)
cacheDir/work/                    — временные файлы, чистятся при старте и по завершении
```

Экспорт пользователю — только через Storage Access Framework (`ACTION_CREATE_DOCUMENT` /
`ACTION_OPEN_DOCUMENT_TREE`), без разрешений на общее хранилище.

Room: `documents`, `pages`, `folders`, `tags`, `document_tags`, `ocr_pages` (+ FTS4 `ocr_fts`),
`fio_results`, `operations` (журнал), `export_history` (защита от двойного водяного знака).
База исключена из резервного копирования (`dataExtractionRules`), как и `docs/` и `vault/`.

## 5. Конвейер сканирования

```
CameraX ImageAnalysis (640px, YUV) ─► EdgeDetector ─► AutoCaptureStabilizer (domain)
                                          │                        │ стабильно N кадров,
                                          ▼                        ▼ резкость ок, бликов нет
                                  оверлей углов           ImageCapture (полное разрешение)
                                                                   │
           original/<id>.jpg (атомарно, read-only) ◄───────────────┘
                     │
                     ▼  WorkManager / корутина с ограниченной очередью
  ориентация (EXIF) → углы (авто или ручные) → warpPerspective → тени → шум → контраст/свет
  → фильтр → processed/<id>.jpg + thumbs/<id>.webp
                     │
                     ▼ (фон)
                OCR (Tesseract rus+eng, OSD-ориентация) → ocr_pages + FTS
                     │
                     ▼
                FioExtractor (domain) → fio_results → предложение имени
```

## 6. Экспорт и водяной знак

Водяной знак «В/ч 65384» никогда не «впекается» в хранимые изображения. Он рисуется только в
момент экспорта поверх копии страницы. Поэтому повторный экспорт не может нанести его дважды:
источник экспорта — всегда `processed/` без знака. Размещение рассчитывает
`WatermarkPlanner` (domain) по карте занятости нижней левой области страницы; если места нет —
страница расширяется полем слева, пользователь видит это в предпросмотре.

## 7. Надёжность

* `AtomicFileWriter` (domain): запись во временный файл в том же каталоге → `fsync` → `rename`.
* `OperationJournal` (domain-логика, data-хранение): каждая многошаговая операция
  (сохранение, экспорт, перемещение в корзину, пакет) записывает намерение до начала и
  отметку завершения после. При старте `RecoveryCoordinator` откатывает или доводит
  незавершённые операции.
* Перед записью — проверка свободного места (оценка размера × 1.5).
* Все долгие задачи — отменяемые (`Job`/`WorkManager`), пакеты продолжаются с места остановки.

## 8. Безопасность

Подробно — `docs/SECURITY.md`. Кратко: нет сети; Keystore AES-256-GCM для защищённого
хранилища; `BiometricPrompt` по выбору; `FLAG_SECURE` для экранов защищённого хранилища;
проверка импортируемых файлов по сигнатуре и размеру; только `content://` URI от SAF
с проверкой authority; отключено резервное копирование чувствительных данных.

## 9. Что проверяется где

| Слой | Где проверяется |
|---|---|
| `:core:common`, `:core:intelligence`, `:core:quality` | unit-тесты JVM — запускаются и в CI, и локально без Android SDK |
| `:core:database` | Robolectric-free unit-тесты на in-memory Room — инструментальные тесты в CI-эмуляторе |
| `:core:imageprocessing`, `:core:hybrid`, `:core:ocr`, `:core:pdf` | инструментальные тесты на эмуляторе в CI с эталонными изображениями из `testdata/` |
| UI | Compose UI-тесты (эмулятор), ручной чек-лист `docs/QA_CHECKLIST.md` |
| Офлайн | Gradle-задача `verifyNoNetwork` + тест, проверяющий отсутствие `INTERNET` в `PackageInfo` |
