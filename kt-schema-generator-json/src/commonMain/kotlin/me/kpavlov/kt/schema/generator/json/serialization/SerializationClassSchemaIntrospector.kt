package me.kpavlov.kt.schema.generator.json.serialization

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.generator.core.defaultOpaqueTypeNames
import me.kpavlov.kt.schema.generator.core.ir.DescriptionExtractor
import me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.json.SerialDescription

/**
 * Introspects kotlinx.serialization descriptors into Schema IR.
 *
 * This introspector uses [SerializationIntrospectionContext] to convert
 * kotlinx.serialization [SerialDescriptor] instances into the Schema IR type system.
 *
 * @property json The Json configuration used to extract discriminator settings for polymorphic types.
 *                Defaults to a Json instance with encodeDefaults = false.
 */
public class SerializationClassSchemaIntrospector(
    override val config: Config = Config(),
    private val json: Json =
        Json {
            encodeDefaults = false
            classDiscriminator = "type"
            classDiscriminatorMode = kotlinx.serialization.json.ClassDiscriminatorMode.ALL_JSON_OBJECTS
        },
) : SchemaIntrospector<SerialDescriptor, SerializationClassSchemaIntrospector.Config> {
    public data class Config(
        val descriptionExtractor: DescriptionExtractor =
            DescriptionExtractor { annotations ->
                annotations.filterIsInstance<SerialDescription>().firstOrNull()?.value
            },
        /**
         * Serial names of types that should be treated as opaque JSON values (mapped to [AnyNode]).
         *
         * Add entries here for custom types whose serialization descriptors are incompatible with
         * the standard processing pipeline (e.g., custom sealed types with runtime-generated
         * polymorphic descriptors). The schema for each entry will be `{}` (accepts any value).
         *
         * Note: `kotlin.Any` and `java.lang.Object` are always treated as opaque regardless of
         * this set. Setting this to an empty set disables only the configurable opaque types,
         * not the built-in Any/Object handling.
         *
         * Defaults to [defaultOpaqueTypeNames] — the single source of truth shared with the
         * reflection and KSP paths. Build on it when extending:
         * `opaqueSerialNames = defaultOpaqueTypeNames() + myCustomTypes`. For the built-in
         * kotlinx.serialization.json types the serial name equals the fully qualified class name.
         */
        val opaqueSerialNames: Set<String> = defaultOpaqueTypeNames(),
    )

    /**
     * Introspects a serial descriptor into a [TypeGraph].
     *
     * @param root The root serial descriptor to introspect
     * @return A TypeGraph containing the root type reference and all discovered type nodes
     */
    public override fun introspect(root: SerialDescriptor): TypeGraph {
        val context = SerializationIntrospectionContext(json, config)
        val rootRef = context.toRef(root)
        return TypeGraph(root = rootRef, nodes = context.nodes)
    }
}
