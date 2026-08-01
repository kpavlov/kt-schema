package me.kpavlov.kt.schema.generator.core

import io.kotest.assertions.assertSoftly
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
        assertSoftly(Config.ignoreAnnotationNames) {
            shouldContain("schemaignore")
            shouldContain("serialschemaignore")
            shouldContain("jsonignoretype")
            shouldContain("jsonignore")
        }
    }

    @Test
    fun `loads name override annotation names from properties`() {
        assertSoftly(Config.nameAnnotationNames) {
            shouldContain("kotlinx.serialization.SerialName")
            shouldContain("com.fasterxml.jackson.annotation.JsonProperty")
            shouldContain("com.fasterxml.jackson.annotation.JsonTypeName")
        }
    }

    @Test
    fun `loads nullable annotation names from properties`() {
        Config.nullableAnnotationNames shouldContain "nullable"
    }

    @Test
    fun `loads optional annotation names from properties`() {
        Config.optionalAnnotationNames shouldContain "nullable"
    }

    @Test
    fun `loads nullable type name patterns from properties preserving case`() {
        Config.nullableTypeNamePatterns shouldContain "*Opt"
    }
}
