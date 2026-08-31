plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * Deliberately a plain Kotlin/JVM module with no Android dependencies.
 *
 * Everything here — SMS parsing, duplicate detection, category learning,
 * budget and analytics arithmetic — is the logic most likely to be wrong and
 * most expensive to get wrong, so it is kept runnable on a plain JVM where it
 * can be tested exhaustively and quickly without an emulator.
 */
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
