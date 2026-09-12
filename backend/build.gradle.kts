plugins {
    // Applied in subprojects; declared here with `apply false` for consistent versions.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.protobuf) apply false
}

allprojects {
    group = "com.moviecatalogue"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Docker Desktop on Windows rejects the Docker API version negotiated by
        // default, returning a spurious HTTP 400 that makes Testcontainers report
        // "Could not find a valid Docker environment". docker-java reads the
        // `api.version` system property (DefaultDockerClientConfig.CONFIG_KEYS), so
        // pinning it to a version the daemon accepts fixes it. Override or disable
        // via -PdockerApiVersion=<ver|""> (empty string opts out, e.g. on Linux CI).
        val dockerApiVersion = (project.findProperty("dockerApiVersion") as String?) ?: "1.44"
        if (dockerApiVersion.isNotBlank()) {
            systemProperty("api.version", dockerApiVersion)
        }
    }
}
