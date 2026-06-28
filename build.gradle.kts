import dev.detekt.gradle.extensions.FailOnSeverity

plugins {
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

dependencies {
    kover(project(":kt-schema-annotations"))
    kover(project(":kt-schema-ksp"))
    kover(project(":kt-schema-ksp-json"))
    kover(project(":kt-schema-json"))
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply {
        plugin("dev.detekt")
    }

    detekt {
        config = files("$rootDir/detekt.yml")
        buildUponDefaultConfig = true
        failOnSeverity.set(FailOnSeverity.Warning)
    }
}

kover {
    reports {
        filters {
            includes.classes("me.kpavlov.kt.schema.*")
            excludes.classes("me.kpavlov.kt.schema.ksp.ir.*", "*Test") // tested indirectly
        }
        total {
            xml {}
            log {
            }
            verify {
                rule {
                    minBound(70)
                }
            }
        }
    }
}
