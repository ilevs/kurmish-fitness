# Курмыш Фитнес

Оффлайн Android-тренер: ввод тренировок текстом и голосом в свободной форме, дневник, подбор цикла и подсказки, что и как тренировать.

Документация: [docs/](docs/README.md) — обучение, практические инструкции, справочник и объяснения.

## Свежий APK

[kurmish-fitness.apk](https://github.com/ilevs/kurmish-fitness/releases/download/latest/kurmish-fitness.apk): подписанная сборка из `main`, обновляется после каждого push и ставится поверх предыдущей.
Debug-APK лежит в артефактах запуска во вкладке Actions. Стабильные версии публикуются по тегу `v*` во вкладке Releases.

## Проверки

```
./gradlew detekt            # detekt allRules + ktlint (ktlint_official) + Compose rules
./gradlew :app:lintDebug    # Android Lint, все проверки, warnings = errors
./gradlew :app:testDebugUnitTest
```
Kotlin-компилятор тоже собирает с `allWarningsAsErrors`. Автоисправление форматирования: `./gradlew detekt --auto-correct`.

## Подпись release

Ключ создан workflow `Init signing key` прямо в GitHub и хранится только в Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
Не удаляй и не перезаписывай эти секреты: с другим ключом обновление не встанет поверх установленного приложения.
