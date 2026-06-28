import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("plugin.serialization")
    kotlin("jvm") apply true
    alias(libs.plugins.kover) apply false
    `dokka-convention`
    alias(libs.plugins.knit)
}

dependencies {
    implementation(project(":kt-schema-annotations"))
    implementation(project(":kt-schema-ksp-json"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotest.assertions.json)

    dokka(project(":kt-schema-annotations"))
    dokka(project(":kt-schema-ksp"))
    dokka(project(":kt-schema-ksp-json"))
    dokka(project(":kt-schema-json"))
}

detekt {
    ignoreFailures = true
}

dokka {
    moduleName.set("kt-schema")

    pluginsConfiguration.html {
        footerMessage = "Copyright © 2025 kpavlov"
    }

    dokkaPublications.html {
        outputDirectory = layout.projectDirectory.dir("public/apidocs")
    }
}

knit {
    rootDir = project.rootDir
    files =
        fileTree(project.rootDir) {
            include("README.md")
            include("kt-schema-json/README.md")
            include("docs/*.md")
        }
    defaultLineSeparator = "\n"
    siteRoot = "https://kpavlov.github.io/kt-schema/"
    moduleDocs = "public/apidocs"
}

// Only run knitCheck and knit when explicitly requested, not as part of build/check
afterEvaluate {
    tasks.named("check") {
        if (gradle.startParameter.taskNames.none { it.contains("knit") }) {
            setDependsOn(
                dependsOn.filter {
                    val name =
                        when (it) {
                            is String -> it
                            is Task -> it.name
                            is TaskProvider<*> -> it.name
                            else -> it.toString()
                        }
                    !name.contains("knit")
                },
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi = ExplicitApiMode.Disabled
    compilerOptions {
        allWarningsAsErrors = false
        jvmTarget = JvmTarget.JVM_17
        javaParameters = true
        jvmDefault = JvmDefaultMode.ENABLE
    }

    sourceSets {
        main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
        }
    }
}
