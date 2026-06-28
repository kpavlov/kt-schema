plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    alias(libs.plugins.google.ksp)
}

val ktSchemaVersion = project.properties["ktSchemaVersion"]

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }

    jvm()

    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(dependencies.platform(libs.ktor.bom))
                implementation(libs.koog.agents.tools)
                implementation(libs.kotlinx.serialization.json)
                implementation("me.kpavlov:kt-schema-annotations:$ktSchemaVersion")

                implementation(libs.mcp.kotlin.server)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.cors)
                implementation(libs.slf4j.simple)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
            }
        }

        jvmMain {
            dependencies {
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks.named("compileKotlinJs") {
    dependsOn("kspCommonMainKotlinMetadata")
}

// Configure KSP arguments
ksp {
    arg("me.kpavlov.kt.schema.withSchemaObject", "true")
    arg("me.kpavlov.kt.schema.visibility", "internal")
}

// Add KSP processor for common target
dependencies {
    add("kspCommonMainMetadata", "me.kpavlov:kt-schema-ksp:$ktSchemaVersion")
}
