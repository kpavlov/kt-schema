plugins {
    `dokka-convention`
    `kotlin-jvm-convention`
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

    dependencies {
        implementation(project(":kt-schema-ksp-json"))
        implementation(libs.ksp.api)

        testImplementation(libs.junit.jupiter.params)
        testImplementation(libs.kotest.assertions.core)
        testImplementation(libs.kotlin.test)
        testImplementation(libs.mockk)
    }
}
