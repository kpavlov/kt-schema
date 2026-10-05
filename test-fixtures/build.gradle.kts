plugins {
    kotlin("jvm")
}

// Sample code for tests only. Applying any publishing plugin (including `publishing-convention`) fails the build.
plugins.withId("maven-publish") {
    error("$name is a test fixture and must never be published to Maven Central")
}

kotlin {
    jvmToolchain(17)
}
