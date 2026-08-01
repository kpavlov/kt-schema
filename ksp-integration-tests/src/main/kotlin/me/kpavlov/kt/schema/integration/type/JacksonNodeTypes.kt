package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.BooleanNode
import tools.jackson.databind.node.DoubleNode
import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.LongNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode
import tools.jackson.databind.node.ValueNode

@Schema
data class JacksonNodeTypes(
    val jsonNode: JsonNode,
    val jsonNodeOpt: JsonNode?,
    val objectNode: ObjectNode,
    val objectNodeOpt: ObjectNode?,
    val arrayNode: ArrayNode,
    val arrayNodeOpt: ArrayNode?,
    val valueNode: ValueNode,
    val stringNode: StringNode,
    val stringNodeOpt: StringNode?,
    val booleanNode: BooleanNode,
    val intNode: IntNode,
    val longNode: LongNode,
    val doubleNode: DoubleNode,
    val nullNode: NullNode,
)
