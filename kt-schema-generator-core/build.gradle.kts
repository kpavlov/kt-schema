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
                "me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi",
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
                implementation(dependencies.platform(libs.jackson.bom))
                implementation(libs.jackson.annotations)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}
