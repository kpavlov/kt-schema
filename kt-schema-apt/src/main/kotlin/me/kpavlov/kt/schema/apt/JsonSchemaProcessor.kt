// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt

import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.apt.ir.AptClassIntrospector
import me.kpavlov.kt.schema.generator.json.JsonSchemaConfig
import me.kpavlov.kt.schema.generator.json.TypeGraphToJsonSchemaTransformer
import me.kpavlov.kt.schema.json.JsonSchema
import java.io.IOException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.FilerException
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * JSR 269 annotation processor generating JSON Schema resources for `@Schema`-annotated
 * Java types, reusing the shared schema IR and JSON Schema transformer from
 * `kt-schema-generator-core`/`kt-schema-generator-json` — the same pipeline the KSP
 * processor uses, so output shape ($id/$defs/$ref/nullability) stays identical.
 *
 * Supports `"*"` annotation types so javac invokes it in every round: this lets the
 * [ROOT_PACKAGE_OPTION] scanning pick up types even when no `@Schema` annotation is
 * present in the compilation. The processor never claims annotations (always returns
 * `false`) and deduplicates by type name, so it coexists safely with other processors.
 *
 * @author Konstantin Pavlov
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(
    JsonSchemaProcessor.ROOT_PACKAGE_OPTION,
)
public class JsonSchemaProcessor : AbstractProcessor() {
    //region Processor state

    private val processedTypes = mutableSetOf<String>()

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    /**
     * Shared across roots so nested types referenced by several roots are introspected
     * once; `processingEnv` is only available after `init()`, hence the lazy delegate.
     */
    private val introspector by lazy { AptClassIntrospector(processingEnv) }

    private val transformer =
        TypeGraphToJsonSchemaTransformer(
            // build JsonSchemaConfig upon Strict config, matching kt-schema-ksp's ClassSchemaStrategy
            config =
                with(JsonSchemaConfig.Strict) {
                    JsonSchemaConfig(
                        respectDefaultPresence = false,
                        requireNullableFields = requireNullableFields,
                        useUnionTypes = useUnionTypes,
                        useNullableField = useNullableField,
                        includePolymorphicDiscriminator = includePolymorphicDiscriminator,
                        includeOpenAPIPolymorphicDiscriminator = includeOpenAPIPolymorphicDiscriminator,
                    )
                },
        )

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = false
        }

    //endregion

    //region Processing

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment,
    ): Boolean {
        if (roundEnv.processingOver()) return false

        candidateTypes(roundEnv)
            .filter { processedTypes.add(it.qualifiedName.toString()) }
            .forEach(::processType)

        return false
    }

    /**
     * Types annotated with `@Schema`, plus — when [ROOT_PACKAGE_OPTION] is configured —
     * every record, class or interface declared under that package, so consumers don't
     * have to annotate every type individually.
     */
    private fun candidateTypes(roundEnv: RoundEnvironment): Set<TypeElement> {
        val annotated = roundEnv.getElementsAnnotatedWith(Schema::class.java).filterIsInstance<TypeElement>()

        val rootPackage = processingEnv.options[ROOT_PACKAGE_OPTION]
        val underRootPackage =
            if (rootPackage.isNullOrBlank()) {
                emptyList()
            } else {
                roundEnv.rootElements
                    .filterIsInstance<TypeElement>()
                    .filter { it.isSupported() }
                    .filter { it.isUnderPackage(rootPackage) }
            }

        return (annotated + underRootPackage).toSet()
    }

    private fun TypeElement.isSupported(): Boolean =
        kind == ElementKind.RECORD || kind == ElementKind.CLASS || kind == ElementKind.INTERFACE

    private fun TypeElement.isUnderPackage(rootPackage: String): Boolean {
        val packageName = processingEnv.elementUtils.getPackageOf(this).qualifiedName.toString()
        return packageName == rootPackage || packageName.startsWith("$rootPackage.")
    }

    //endregion

    //region Resource writing

    private fun processType(type: TypeElement) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val graph = introspector.introspect(type)
            val schema = transformer.transform(graph, type.qualifiedName.toString())
            writeSchemaResource(type, json.encodeToString(JsonSchema.serializer(), schema))
        } catch (e: Exception) {
            reportError(type, "Failed to generate JSON Schema: ${e.message}")
        }
    }

    private fun writeSchemaResource(
        source: TypeElement,
        jsonString: String,
    ) {
        val relativePath = source.qualifiedName.toString().replace('.', '/') + ".json"
        val path = "META-INF/kt-schema/schemas/$relativePath"
        try {
            val file =
                processingEnv.filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    path,
                    source,
                )
            file.openWriter().use { it.write(jsonString) }
        } catch (e: FilerException) {
            reportError(source, "Failed to create JSON Schema resource $path: ${e.message}")
        } catch (e: IOException) {
            reportError(source, "Failed to write JSON Schema resource $path: ${e.message}")
        }
    }

    private fun reportError(
        type: TypeElement,
        message: String,
    ) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message, type)
    }

    //endregion

    //region Public constants

    public companion object {
        /**
         * Processor option (`-A<name>=<value>`) that, when set, processes every top-level
         * record, class or interface declared under the given package (and its sub-packages)
         * in addition to types annotated with `@Schema`. Lets consumers skip annotating
         * every type individually.
         */
        public const val ROOT_PACKAGE_OPTION: String = "me.kpavlov.kt.schema.rootPackage"
    }

    //endregion
}
