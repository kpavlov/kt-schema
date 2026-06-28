plugins {
    `dokka-convention`
    `kotlin-multiplatform-convention`
    `publishing-convention`
}

dokka {
    dokkaSourceSets.configureEach {
    }
}

kotlin {
    compilerOptions {
        optIn.set(
            listOf(
                "kotlinx.schema.generator.core.InternalSchemaGeneratorApi",
            ),
        )
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(project(":kt-schema-annotations"))
                implementation(libs.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":kt-schema-annotations"))
            }
        }

        jvmMain {
            dependencies {
                implementation(kotlin("reflect"))
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.junit.jupiter.params)
                implementation(libs.mockk)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}
