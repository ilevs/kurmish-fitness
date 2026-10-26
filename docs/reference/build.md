# Сборка и проверки

| Что | Где |
|---|---|
| CI | `.github/workflows/android.yml` |
| Линтеры | `./gradlew detekt` (detekt со всеми правилами, ktlint, Compose rules), `./gradlew :app:lintDebug` (Android Lint, предупреждения = ошибки) |
| Тесты | `./gradlew :app:testDebugUnitTest` |
| Свежий APK из `main` | [kurmish-fitness.apk](https://github.com/ilevs/kurmish-fitness/releases/download/latest/kurmish-fitness.apk) |
| Версии | тег `v*` создаёт релиз во вкладке Releases |

Пакеты:

- `com.kurmish.fitness.core` — чистый Kotlin без Android: разбор текста, расчёты, каталог циклов, советы, хранилище. Покрыт тестами в `app/src/test/java/com/kurmish/fitness/core`.
- `com.kurmish.fitness.ui` — экраны Compose и модель.
- `com.kurmish.fitness.ui.voice` — распознавание и синтез речи.
