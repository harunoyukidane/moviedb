plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(libs.kotlin.reflect)
    // Adds JPEG/PNG (native) + WebP decoding to ImageIO for content validation.
    implementation(libs.imageio.webp)
    implementation(libs.slf4j.api)
    // minio's default okhttp 5.x transitive is compiled with Kotlin 2.2 metadata,
    // unreadable by this project's 1.9.25 compiler; pin the 4.x line it also supports.
    implementation(libs.minio) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.minio)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
