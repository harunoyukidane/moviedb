pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            // The kotlin.spring (allopen) and kotlin.jpa (noarg) companion
            // plugins live inside kotlin-gradle-plugin. Map them explicitly so
            // resolution never depends on separate plugin-marker artifacts.
            if (requested.id.id.startsWith("org.jetbrains.kotlin.")) {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "movie-catalogue"

include("contracts")
include("catalogue-service")
include("people-service")
