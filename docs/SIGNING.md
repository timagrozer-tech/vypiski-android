# Подпись Release APK

Ключи подписи **никогда** не хранятся в репозитории (`*.jks`, `*.keystore`, `keystore.properties`
в `.gitignore`).

## 1. Создать ключ (один раз, на своём компьютере)

```bash
keytool -genkeypair -v -keystore scanner291-release.jks -alias scanner291 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=291 Scanner, O=В/ч 65384, C=RU"
```

Сохраните `.jks` и пароли в надёжном месте. **Потеря ключа = невозможность обновить
установленное приложение** (придётся удалять и ставить заново, документы на телефоне при
удалении теряются, если их не экспортировали).

## 2. Локальная сборка

```bash
cp keystore.properties.example keystore.properties   # заполнить путь и пароли
./gradlew :app:assembleRelease
```

Без `keystore.properties` Release собирается неподписанным — такой APK не установится.

## 3. Сборка в GitHub Actions

Settings → Secrets and variables → Actions → New repository secret:

| Секрет | Значение |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 scanner291-release.jks` |
| `SIGNING_STORE_PASSWORD` | пароль хранилища |
| `SIGNING_KEY_ALIAS` | `scanner291` |
| `SIGNING_KEY_PASSWORD` | пароль ключа |

Затем Actions → «Android CI» → Run workflow. Задача `release` восстановит keystore во
временный каталог раннера, соберёт APK, проверит подпись `apksigner verify` и удалит keystore.
