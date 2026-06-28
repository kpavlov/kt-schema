@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kt-schema"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":kt-schema-annotations",
    ":kt-schema-json",
    ":kt-schema-ksp",
    ":kt-schema-ksp-json",
    ":kt-schema-ksp-processor",
    ":ksp-integration-tests",
    ":docs",
)
