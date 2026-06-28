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
    implementation(libs.kotlinx.serialization.json)

    // Third-party annotation libraries for testing description extraction
    implementation(libs.koog.agents.tools)
    implementation(libs.jackson.annotations)
    implementation(libs.langchain4j.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.pioneer)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(project(":kt-schema-ksp-json"))

    // KSP processor
    ksp(project(":kt-schema-ksp-processor"))
}

ksp {
    arg("kotlinx.schema.withSchemaObject", "true")
    arg("kotlinx.schema.visibility", "")
}

// KSP generates sources to build/generated/ksp/main/kotlin
// This is automatically added to the source sets by KSP plugin
kotlin.sourceSets.main {
    kotlin.srcDir("build/generated/ksp/main/kotlin")
}
