import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt)
}

dependencies {
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.detekt.compose.rules)
}

detekt {
    // Все правила detekt, включая выключенные по умолчанию, плюс ktlint (formatting) и Compose rules.
    allRules = true
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "app/src/main/java",
            "app/src/test/java",
            "build.gradle.kts",
            "settings.gradle.kts",
            "app/build.gradle.kts",
        ),
    )
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
