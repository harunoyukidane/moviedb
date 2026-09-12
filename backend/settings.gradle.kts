pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "movie-catalogue"

include("contracts")
include("media")
include("catalogue-service")
include("people-service")
