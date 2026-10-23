# Матрица автоматических проверок (этап 21)

Где проверяется каждое критическое требование ТЗ. «CI» — каждая сборка (unit + Robolectric);
«Эмулятор» — задача `instrumented` (запуск вручную: Actions → Android CI → Run workflow, сеть эмулятора выключена);
«Лаборатория» — Python-копии алгоритмов в `tools/*-lab` (цифры в README лабораторий).

| Требование | Проверка | Где |
|---|---|---|
| Нет INTERNET, сетевых SDK | `verifyNoNetwork` (объединённый манифест + зависимости) | CI |
| Экспортируется только экран запуска, без резервного копирования | `verifyManifestSecurityRelease` | CI |
| Оригиналы не меняются (байты, только чтение, SHA-256) | DocumentRepositoryTest, HealthCenterTest (до/после исправлений), ProcessDeathTest | CI |
| Атомарная запись, без перезаписи, уникальные имена «(2)» | SafeStorage-тесты, NamingTest, PdfEngineTest.neverOverwritesOrReplacesInput | CI |
| Убийство процесса посреди операции | ProcessDeathTest (документ на месте, частичный результат откатывается) | CI |
| Работа без сети (авиарежим) | Тесты на эмуляторе с `-no-network`; приложение не запрашивает INTERNET | Эмулятор |
| Поиск листа ≥ 90 % (IoU ≥ 0.9) | DetectorAccuracyTest: 60/60, средний IoU 0.984, 0 ложных | Эмулятор |
| Обработка: повторяемость, тени, Hybrid сохраняет печать | PageRendererTest; лаборатория (фон ±26.9 → ±2.3; печать ≥ 0.98) | Эмулятор, Лаборатория |
| ≥ 30 пресетов, различимость, воспроизводимость | PresetsTest, lab_bases.py (55 пар ≥ 3.83) | CI, Лаборатория |
| OCR rus/eng, CER/WER | reports/ocr-metrics.md (CER 1.7–1.8 % после обработки) | Лаборатория |
| Текст OCR не попадает в logcat | OcrServiceTest | CI |
| PDF: страниц столько же, текст ищется, кириллица | PdfEngineTest | CI |
| Закрашивание удаляет и пиксели, и текст | PdfEngineTest.redactedTextIsNotInPdf | CI |
| Водяной знак «В/ч 65384»: место, поворот, 20 %, без перекрытия, без повтора | WatermarkRendererTest | CI |
| ФИО и сведения — только предложения | FioExtractorTest, DocumentAnalyzerTest (52), FactsLogicTest | CI |
| Пакет: ошибка 1 из 30 не теряет 29; отмена; повтор | BatchEngineTest | CI |
| Quality Gate: пороги, три вердикта, не блокирует | QualityGateTest | CI |
| Миграции БД без потери данных (v1 → v5) | MigrationTest | CI |
| Папки, теги, корзина, дубликаты | DocumentManagerDaoTest | CI |
| Шифрование хранилища | SecureVaultTest | CI |
| Экраны открываются, навигация | NavigationTest, AppScreenshotTest (снимки → ветка scanner291-screenshots) | CI |

Не автоматизировано (проверяет пользователь на телефоне, `docs/QA_CHECKLIST.md`): реальная камера,
вспышка и фокус, автоснимок на живом кадре, биометрия, печать, скорость на конкретном устройстве.
