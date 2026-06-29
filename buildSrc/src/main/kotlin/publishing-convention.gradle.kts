/**
 * Convention plugin to enable and configure publishing with Maven Publish.
 * - Configures Maven Central publishing with automatic release
 * - Sets up POM metadata for all publications
 * - Enables signing when GPG keys are available
 * - Automatically publishes sources and documentation (KDoc)
 */
plugins {
    id("com.vanniktech.maven.publish")
    signing
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "project"
            url = uri(rootProject.layout.buildDirectory.dir("project-repo"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)

    pom {
        name = providers.gradleProperty("POM_NAME").orElse(project.name).get()
        description =
            providers
                .gradleProperty("POM_DESCRIPTION")
                .orElse(project.description ?: project.name)
                .get()
        url =
            providers
                .gradleProperty("POM_URL")
                .orElse("https://github.com/kpavlov/kt-schema")
                .get()
        inceptionYear = "2026"

        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
                comments = "kt-schema is an independent fork of kotlinx-schema, originally developed by\n" +
                    "JetBrains s.r.o. and contributors, and distributed under the Apache License,\n" +
                    "Version 2.0. See the LICENSE and NOTICE files for attribution information."
            }
        }

        developers {
            developer {
                id = "kpavlov"
                name = "Konstantin Pavlov"
                email = "mail@kpavlov.me"
                organization = "kpavlov"
                organizationUrl = "https://github.com/kpavlov"
            }
        }

        scm {
            url = "https://github.com/kpavlov/kt-schema"
            connection = "scm:git:https://github.com/kpavlov/kt-schema.git"
            developerConnection = "scm:git:ssh://git@github.com/kpavlov/kt-schema.git"
        }
    }
}

afterEvaluate {
    signing {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
        mavenPublishing.signAllPublications()
        isRequired = !signingKey.isNullOrBlank() // don't fail if no key
    }
}
