package me.kpavlov.kt.schema.generator.core

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConfigTest {
    @Test
    fun `loads annotation names from properties`() {
        Config.descriptionAnnotationNames.shouldNotBeEmpty()

        // Should include default annotations (lowercase)
        Config.descriptionAnnotationNames shouldContain "description"
        Config.descriptionAnnotationNames shouldContain "serialdescription"
        Config.descriptionAnnotationNames shouldContain "llmdescription"
        Config.descriptionAnnotationNames shouldContain "jsonpropertydescription"
        Config.descriptionAnnotationNames shouldContain "jsonclassdescription"
        Config.descriptionAnnotationNames shouldContain "p"
    }

    @Test
    fun `loads value attributes from properties`() {
        Config.descriptionValueAttributes.shouldNotBeEmpty()

        // Should include default attributes (lowercase)
        Config.descriptionValueAttributes shouldContain "value"
        Config.descriptionValueAttributes shouldContain "description"
    }

    @Test
    fun `all values are normalized to lowercase`() {
        Config.descriptionAnnotationNames.all { it == it.lowercase() } shouldBe true
        Config.descriptionValueAttributes.all { it == it.lowercase() } shouldBe true
    }

    @Test
    fun `loads ignore annotation names from properties`() {
        Config.ignoreAnnotationNames shouldContain "schemaignore"
        Config.ignoreAnnotationNames shouldContain "serialschemaignore"
        Config.ignoreAnnotationNames shouldContain "jsonignoretype"
        Config.ignoreAnnotationNames shouldContain "jsonignore"
    }

    @Test
    fun `loads name override annotation names from properties`() {
        Config.nameAnnotationNames shouldContain "kotlinx.serialization.SerialName"
        Config.nameAnnotationNames shouldContain "com.fasterxml.jackson.annotation.JsonProperty"
        Config.nameAnnotationNames shouldContain "com.fasterxml.jackson.annotation.JsonTypeName"
    }
}
