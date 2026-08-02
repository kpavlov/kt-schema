plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.annotations)
    implementation(libs.jspecify)
    annotationProcessor(project(":kt-schema-apt"))

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jackson.databind)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Ame.kpavlov.kt.schema.rootPackage=me.kpavlov.kt.schema.apt.integration",
            "-Ame.kpavlov.kt.schema.include=me.kpavlov.kt.schema.apt.integration.**",
        ),
    )
}
