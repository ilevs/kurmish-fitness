# Курмыш Фитнес

Оффлайн Android-тренер: ввод тренировок текстом и голосом, планы по методике Дмитрия Смирнова.

## Свежий APK

[kurmish-fitness-debug.apk](https://github.com/ilevs/kurmish-fitness/releases/download/latest/kurmish-fitness-debug.apk) — собирается из `main` после каждого push.
Подписанный release-APK (`kurmish-fitness.apk`) появляется там же, когда в Secrets заданы ключи подписи (см. ниже).
Стабильные версии: `git tag v1.0.0 && git push --tags`, APK будет во вкладке Releases.

## Проверки

```
./gradlew detekt            # detekt allRules + ktlint (ktlint_official) + Compose rules
./gradlew :app:lintDebug    # Android Lint, все проверки, warnings = errors
./gradlew :app:testDebugUnitTest
```
Kotlin-компилятор тоже собирает с `allWarningsAsErrors`. Автоисправление форматирования: `./gradlew detekt --auto-correct`.

## Подпись release

```
keytool -genkeypair -v -keystore release.jks -alias fitness -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks
```
Settings → Secrets and variables → Actions: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Ключ храни у себя: без него обновление поверх установленного приложения не встанет.
