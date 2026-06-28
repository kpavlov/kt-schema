plugins {
    kotlin("plugin.serialization")
    `dokka-convention`
    `kotlin-multiplatform-convention`
    `publishing-convention`
}

dokka {
    dokkaSourceSets.configureEach {
    }
}

kotlin {

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(project(":kt-schema-annotations"))
                api(project(":kt-schema-ksp"))
                api(project(":kt-schema-json"))
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
                implementation(libs.kotlin.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.junit.pioneer)
                implementation(libs.junit.jupiter.params)
                implementation(libs.mockk)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }

    compilerOptions {
        optIn.set(
            listOf(
                "kotlinx.serialization.ExperimentalSerializationApi",
                "kotlinx.schema.generator.core.InternalSchemaGeneratorApi",
            ),
        )
    }
}
