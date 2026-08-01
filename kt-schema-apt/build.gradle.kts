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
                "me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi",
            ),
        )
    }

    dependencies {
        implementation(project(":kt-schema-annotations"))
        implementation(project(":kt-schema-generator-json"))
        // javac runs this annotation processor with no consumer-provided logging binding, so
        // kotlin-logging (pulled in transitively) needs one bundled here.
        runtimeOnly(libs.slf4j.simple)

        testImplementation(libs.junit.jupiter.params)
        testImplementation(libs.kotest.assertions.core)
        testImplementation(libs.kotest.assertions.json)
        testImplementation(libs.kotlin.test)
    }
}
