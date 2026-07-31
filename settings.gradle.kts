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
    ":kt-schema-generator-core",
    ":kt-schema-generator-json",
    ":kt-schema-ksp",
    ":kt-schema-apt",
    ":ksp-integration-tests",
    ":apt-integration-tests",
    ":docs",
)
