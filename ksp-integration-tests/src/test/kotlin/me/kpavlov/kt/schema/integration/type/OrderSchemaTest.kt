package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for Order schema generation - complex nested structures.
 */
class OrderSchemaTest {
    @Suppress("LongMethod")
    @Test
    fun `generates complete nested schema with all types`() {
        val schema = Order::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Order",
              "description": "An order placed by a customer containing multiple items.",
              "type": "object",
              "properties": {
                "id": {
                  "type": "string",
                  "description": "Unique order identifier"
                },
                "customer": {
                  "$ref": "#/$defs/Person",
                  "description": "The customer who placed the order"
                },
                "shippingAddress": {
                  "$ref": "#/$defs/Address",
                  "description": "Destination address for shipment"
                },
                "items": {
                  "type": "array",
                  "description": "List of items included in the order",
                  "items": {
                    "$ref": "#/$defs/Product"
                  }
                },
                "status": {
                  "$ref": "#/$defs/Status",
                  "description": "Current status of the order"
                }
              },
              "additionalProperties": false,
              "required": [
                "id",
                "customer",
                "shippingAddress",
                "items",
                "status"
              ],
              "$defs": {
                "Person": {
                  "type": "object",
                  "description": "A person with a first and last name and age.",
                  "properties": {
                    "firstName": {
                      "type": "string",
                      "description": "Given name of the person"
                    },
                    "lastName": {
                      "type": "string",
                      "description": "Family name of the person"
                    },
                    "age": {
                      "type": "integer",
                      "description": "Age of the person in years"
                    }
                  },
                  "required": [
                    "firstName",
                    "lastName",
                    "age"
                  ],
                  "additionalProperties": false
                },
                "Address": {
                  "type": "object",
                  "description": "A postal address for deliveries and billing.",
                  "properties": {
                    "street": {
                      "type": "string",
                      "description": "Street address, including house number"
                    },
                    "city": {
                      "type": "string",
                      "description": "City or town name"
                    },
                    "zipCode": {
                      "type": "string",
                      "description": "Postal or ZIP code"
                    },
                    "country": {
                      "type": "string",
                      "description": "Two-letter ISO country code; defaults to US"
                    }
                  },
                  "required": [
                    "street",
                    "city",
                    "zipCode",
                    "country"
                  ],
                  "additionalProperties": false
                },
                "Product": {
                  "type": "object",
                  "description": "A purchasable product with pricing and inventory info.",
                  "properties": {
                    "id": {
                      "type": "integer",
                      "description": "Unique identifier for the product"
                    },
                    "name": {
                      "type": "string",
                      "description": "Human-readable product name"
                    },
                    "description": {
                      "type": [
                        "string",
                        "null"
                      ],
                      "description": "Optional detailed description of the product"
                    },
                    "price": {
                      "type": "number",
                      "description": "Unit price expressed as a decimal number"
                    },
                    "inStock": {
                      "type": "boolean",
                      "description": "Whether the product is currently in stock"
                    },
                    "tags": {
                      "type": "array",
                      "description": "List of tags for categorization and search",
                      "items": {
                        "type": "string"
                      }
                    }
                  },
                  "required": [
                    "id",
                    "name",
                    "description",
                    "price",
                    "inStock",
                    "tags"
                  ],
                  "additionalProperties": false
                },
                "Status": {
                  "type": "string",
                  "description": "Current lifecycle status of an entity.",
                  "enum": [
                    "ACTIVE",
                    "INACTIVE",
                    "PENDING"
                  ]
                }
              }
            } 
            """.trimIndent()
    }
}
