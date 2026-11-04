plugins {
    kotlin("jvm")
    alias(libs.plugins.google.ksp)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(project(":kt-schema-annotations"))
    implementation(project(":test-fixtures"))
    implementation(libs.kotlinx.serialization.json)

    // Third-party annotation libraries for testing description extraction
    implementation(libs.koog.agents.tools)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.langchain4j.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.pioneer)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(project(":kt-schema-generator-json"))
    ksp(project(":kt-schema-ksp"))
    kspTest(project(":kt-schema-ksp"))
}

ksp {
    arg("me.kpavlov.kt.schema.withSchemaObject", "true")
    arg("me.kpavlov.kt.schema.visibility", "")
}

// The test source set is processed with the lenient config, see LenientFunctions.kt
tasks.withType<com.google.devtools.ksp.gradle.KspAATask>().configureEach {
    if (name == "kspTestKotlin") {
        kspConfig.processorOptions.put("me.kpavlov.kt.schema.config", "lenient")
    }
}

// KSP generates sources to build/generated/ksp/<sourceSet>/kotlin
// This is automatically added to the source sets by KSP plugin
kotlin.sourceSets.main {
    kotlin.srcDir("build/generated/ksp/main/kotlin")
}
kotlin.sourceSets.test {
    kotlin.srcDir("build/generated/ksp/test/kotlin")
}
