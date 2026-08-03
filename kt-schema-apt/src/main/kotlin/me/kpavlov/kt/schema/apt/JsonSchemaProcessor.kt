// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt

import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.apt.ir.AptClassIntrospector
import me.kpavlov.kt.schema.generator.core.GlobMatcher
import me.kpavlov.kt.schema.generator.core.parseGlobPatterns
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
 * [ROOT_PACKAGE_OPTION]/[INCLUDE_OPTION] scanning pick up types even when no `@Schema`
 * annotation is present in the compilation. The processor never claims annotations (always returns
 * `false`) and deduplicates by type name, so it coexists safely with other processors.
 *
 * @author Konstantin Pavlov
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(
    JsonSchemaProcessor.ROOT_PACKAGE_OPTION,
    JsonSchemaProcessor.INCLUDE_OPTION,
    JsonSchemaProcessor.EXCLUDE_OPTION,
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
     * Discovers the types to process, mirroring the KSP processor's filtering:
     *
     * 1. Only records, classes, interfaces and enums are considered.
     * 2. When [ROOT_PACKAGE_OPTION] is set, only types under that package (and its
     *    sub-packages) are considered; otherwise the whole module is scanned.
     * 3. A type is selected when it is annotated with `@Schema` or matches at least one
     *    [INCLUDE_OPTION] glob pattern.
     * 4. A type matching any [EXCLUDE_OPTION] glob pattern is dropped, even when selected
     *    by `@Schema` or an include pattern.
     */
    private fun candidateTypes(roundEnv: RoundEnvironment): Set<TypeElement> {
        val rootPackage = processingEnv.options[ROOT_PACKAGE_OPTION]?.trim()?.takeIf { it.isNotEmpty() }
        val globMatcher =
            GlobMatcher(
                includePatterns = parseGlobPatterns(processingEnv.options[INCLUDE_OPTION]),
                excludePatterns = parseGlobPatterns(processingEnv.options[EXCLUDE_OPTION]),
            )

        val annotated = roundEnv.getElementsAnnotatedWith(Schema::class.java).filterIsInstance<TypeElement>()
        val fromRoots = roundEnv.rootElements.filterIsInstance<TypeElement>()

        return (annotated + fromRoots)
            .asSequence()
            .filter { it.isSupported() }
            .filter { rootPackage == null || it.isUnderPackage(rootPackage) }
            .filter { it.isAnnotatedWithSchema() || globMatcher.matchesInclude(it.qualifiedName.toString()) }
            .filterNot { globMatcher.matchesExclude(it.qualifiedName.toString()) }
            .toSet()
    }

    private fun TypeElement.isSupported(): Boolean =
        kind == ElementKind.RECORD ||
            kind == ElementKind.CLASS ||
            kind == ElementKind.INTERFACE ||
            kind == ElementKind.ENUM

    private fun TypeElement.isUnderPackage(rootPackage: String): Boolean {
        val packageName =
            processingEnv.elementUtils
                .getPackageOf(this)
                .qualifiedName
                .toString()
        return packageName == rootPackage || packageName.startsWith("$rootPackage.")
    }

    private fun TypeElement.isAnnotatedWithSchema(): Boolean =
        annotationMirrors.any { mirror ->
            (mirror.annotationType.asElement() as? TypeElement)?.qualifiedName?.toString() ==
                Schema::class.qualifiedName
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
         * Processor option (`-A<name>=<value>`) that, when set, restricts discovery to types
         * declared under the given package (and its sub-packages). When absent, the whole
         * module is scanned. Applies to `@Schema`-annotated types and include-glob matches alike.
         */
        public const val ROOT_PACKAGE_OPTION: String = "me.kpavlov.kt.schema.rootPackage"

        /**
         * Processor option (`-A<name>=<value>`) with comma/semicolon-separated glob patterns.
         * A type not annotated with `@Schema` is processed only when it matches at least one
         * include pattern. Glob syntax: `*` matches any sequence of non-`.` characters,
         * `**` any sequence including `.`, `?` a single non-`.` character.
         */
        public const val INCLUDE_OPTION: String = "me.kpavlov.kt.schema.include"

        /**
         * Processor option (`-A<name>=<value>`) with comma/semicolon-separated glob patterns.
         * A type matching any exclude pattern is dropped, even when it is `@Schema`-annotated
         * or matches an include pattern. Glob syntax matches [INCLUDE_OPTION].
         */
        public const val EXCLUDE_OPTION: String = "me.kpavlov.kt.schema.exclude"
    }

    //endregion
}
