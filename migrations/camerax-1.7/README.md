# Переход на CameraX 1.7 — подготовлен, НЕ применён

Основной код остаётся на **CameraX 1.6.2** (стабильная). На 3 ноября 2026 у 1.7 есть только **1.7.0-rc01** (21.10.2026). Патч `camerax-1.7.patch` готов к применению после выхода стабильной 1.7.0.

## Что меняет патч

| Было (1.6) | Стало (1.7) | Почему |
|---|---|---|
| `ExtensionsManager.getExtensionEnabledCameraSelector()` + `bindToLifecycle(…, useCases)` | `ExtensionSessionConfig(mode, manager, preview, capture)` + `bindToLifecycle(…, SessionConfig)` | в 1.7 первый помечен `@Deprecated` |
| `Camera2CameraControl.from(control).setCaptureRequestOptions(bundle)` | `control.applyCamera2InteropAsync { clearAllCaptureRequestOptions(); setCaptureRequestOption(…) }` | в 1.7 класс `@Deprecated` (Camera2Interop V2) |
| `Camera2CameraInfo.from(info).cameraId`, собственный `CameraFilter` | `Camera2Interop.getCameraId(info)`, `Camera2Interop.getCameraSelectorFromCameraId(id)` | в 1.7 класс `@Deprecated` |
| `@OptIn(ExperimentalCamera2Interop::class)` | не нужен | новый interop стабильный |

**Важная разница семантики:** `setCaptureRequestOptions` заменял набор целиком, а `applyCamera2InteropAsync` по документации обновляет ключи *инкрементально*. Поэтому блок начинается с `clearAllCaptureRequestOptions()` — по документации всё в одном блоке применяется атомарно, одним обновлением повторяющегося запроса. Без этой строки после «Вернуть авто» ручная ISO осталась бы в запросе.

## Проверено (без сборки)

- Все 95 используемых вызовов CameraX есть в сигнатурах `camera/*/api/1.7.0-rc01.txt`; ни один не помечен `@Deprecated`.
- 97 unit-тестов, проверка типов приложения и androidTest (заглушки дополнены новыми API).
- По release notes 1.7.0-alpha02 требование compileSdk 37 **снято** для модулей камеры (осталось только для camera-compose, который не используется), т.е. AGP 8.10.1 / compileSdk 36 должны подойти.

## Не проверено

- 1.7.0-rc01 тянет `androidx.lifecycle:lifecycle-runtime:2.10.0` — его требования к compileSdk/AGP покажет только сборка.
- Поведение `applyCamera2InteropAsync` на REVVL 7 Pro (PRO-режим) — только на телефоне.

## Как применить (когда выйдет стабильная 1.7.0)

```bash
git apply migrations/camerax-1.7/camerax-1.7.patch
# проверь, что в app/build.gradle.kts стоит актуальная стабильная версия (в патче: "1.7.0")
tools/offline-check/check.sh
tools/api-check/check-apis.sh <androidx-checkout> 1.7.0   # когда появится api/1.7.0.txt
```

Затем вручную запусти **Verify sources (no APK)** и пройди раздел 2d (PRO) из `TEST_PLAN.md` на телефоне. Для проверки до релиза можно поставить `1.7.0-rc01`.
