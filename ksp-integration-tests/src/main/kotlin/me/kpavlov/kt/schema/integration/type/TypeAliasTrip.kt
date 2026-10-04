package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

typealias AliasedUserId = String
typealias OptionalAliasedUserId = String?
typealias AliasedTags<T> = List<T>
typealias StringKeyed<V> = Map<String, V>
typealias NestedStringKeyed<X> = StringKeyed<X>
typealias AliasedCallback = (String) -> Unit

@Schema
data class TypeAliasTrip(
    val id: AliasedUserId,
    val optionalId: OptionalAliasedUserId,
    val tags: AliasedTags<Int>,
    val scoresByName: StringKeyed<Int>,
    val nestedScores: NestedStringKeyed<Int>,
    val nullableScores: StringKeyed<Int?>,
)

@Schema
data class StarProjectedAliasTrip(
    val nested: AliasedTags<AliasedTags<*>>,
)

@Schema
data class JvmCollectionsTrip(
    val scores: HashMap<String, Int>,
    val names: ArrayList<String>,
    val unique: LinkedHashSet<Int>,
    val hashed: HashSet<String>,
    val linked: LinkedHashMap<String, Int>,
    val initial: Char,
)

@Schema
data class FunctionTypeAliasTrip(
    val callback: AliasedCallback,
)

@Schema
data class DirectFunctionTypeTrip(
    val callback: (String) -> Unit,
)

@Schema(withSchemaObject = false)
data class AnnotationOptOutTrip(
    val name: String,
)

@Schema
data class PlatformTypesTrip(
    val path: java.nio.file.Path,
    val range: IntRange,
)
