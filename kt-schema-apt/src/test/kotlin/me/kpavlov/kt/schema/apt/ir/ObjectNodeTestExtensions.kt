package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.isPresenceRequired

/** Names of the properties that must be present under the base presence rule. */
internal fun ObjectNode.requiredNames(): Set<String> =
    properties.filter { it.isPresenceRequired() }.mapTo(LinkedHashSet()) { it.name }
