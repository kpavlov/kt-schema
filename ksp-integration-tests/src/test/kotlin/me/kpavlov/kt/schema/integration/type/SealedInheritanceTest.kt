package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class SealedInheritanceTest {
    @Test
    fun `should include inherited properties from sealed base in KSP`() {
        val schema = SealedBase::class.jsonSchemaString

        // SubclassA should have baseProp
        schema shouldEqualJson
            $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "SealedBase",
                "type": "object",
                "additionalProperties": false,
                "oneOf": [
                    {
                        "$ref": "#/$defs/SubclassA"
                    },
                    {
                        "$ref": "#/$defs/SubclassB"
                    }
                ],
                "$defs": {
                    "SubclassA": {
                        "type": "object",
                        "properties": {
                            "type": {
                                "type": "string",
                                "const": "SubclassA"
                            },
                            "propA": {
                                "type": "integer",
                                "description": "A's property"
                            },
                            "baseProp": {
                                "type": "string",
                                "description": "Base property"
                            }
                        },
                        "required": [
                            "type",
                            "propA",
                            "baseProp"
                        ],
                        "additionalProperties": false
                    },
                    "SubclassB": {
                        "type": "object",
                        "properties": {
                            "type": {
                                "type": "string",
                                "const": "SubclassB"
                            },
                            "baseProp": {
                                "type": "string",
                                "description": "Base property"
                            }
                        },
                        "required": [
                            "type",
                            "baseProp"
                        ],
                        "additionalProperties": false
                    }
                }
            }
            """.trimIndent()
    }
}
