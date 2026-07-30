// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.BaseIntrospectionContext
import me.kpavlov.kt.schema.generator.core.ir.Introspections
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types

/**
 * Introspection context for the Java annotation-processor (JSR 269) front end.
 *
 * Supports Java records with primitive/boxed/String components and nested record
 * references. Reference types are treated as non-nullable/required: Java records have
 * no notion of optionality/default values, so every component is required.
 *
 * @author Konstantin Pavlov
 */
@OptIn(InternalSchemaGeneratorApi::class)
internal class AptIntrospectionContext(
    private val types: Types,
) : BaseIntrospectionContext<TypeMirror>() {
    override fun toRef(type: TypeMirror): TypeRef {
        primitiveKindFor(type)?.let { return TypeRef.Inline(PrimitiveNode(it)) }

        return requireNotNull(handleRecord(type)) {
            "Unsupported type for kt-schema-apt (only records, primitives and String are supported): $type"
        }
    }

    private fun primitiveKindFor(type: TypeMirror): PrimitiveKind? =
        when (type.kind) {
            TypeKind.BOOLEAN -> PrimitiveKind.BOOLEAN
            TypeKind.INT, TypeKind.SHORT, TypeKind.BYTE -> PrimitiveKind.INT
            TypeKind.LONG -> PrimitiveKind.LONG
            TypeKind.FLOAT -> PrimitiveKind.FLOAT
            TypeKind.DOUBLE -> PrimitiveKind.DOUBLE
            TypeKind.DECLARED -> boxedPrimitiveKindFor(type)
            else -> null
        }

    private fun boxedPrimitiveKindFor(type: TypeMirror): PrimitiveKind? =
        when (asTypeElement(type)?.qualifiedName?.toString()) {
            "java.lang.String" -> PrimitiveKind.STRING
            "java.lang.Boolean" -> PrimitiveKind.BOOLEAN
            "java.lang.Integer", "java.lang.Short", "java.lang.Byte" -> PrimitiveKind.INT
            "java.lang.Long" -> PrimitiveKind.LONG
            "java.lang.Float" -> PrimitiveKind.FLOAT
            "java.lang.Double" -> PrimitiveKind.DOUBLE
            else -> null
        }

    private fun asTypeElement(type: TypeMirror): TypeElement? = types.asElement(type) as? TypeElement

    private fun handleRecord(type: TypeMirror): TypeRef? {
        val element = asTypeElement(type)
        if (element == null || element.kind != ElementKind.RECORD) return null

        val id = TypeId(element.qualifiedName.toString())

        withCycleDetection(type, id) {
            val props = ArrayList<Property>()
            val required = LinkedHashSet<String>()

            element.recordComponents.forEach { component ->
                val name = component.simpleName.toString()
                required += name
                props +=
                    Property(
                        name = name,
                        type = toRef(component.asType()),
                        description = extractDescription(fieldFor(element, name) ?: component),
                    )
            }

            ObjectNode(
                name = element.qualifiedName.toString(),
                properties = props,
                required = required,
                description = extractDescription(element),
            )
        }

        return TypeRef.Ref(id)
    }

    /**
     * Record component annotations propagate to the backing field (among other targets),
     * so the field is the most reliable place to read them back from regardless of which
     * Java targets the annotation declares `@Target` for.
     */
    private fun fieldFor(
        type: TypeElement,
        name: String,
    ): VariableElement? =
        type.enclosedElements
            .filterIsInstance<VariableElement>()
            .firstOrNull { it.kind == ElementKind.FIELD && it.simpleName.contentEquals(name) }

    private fun extractDescription(element: Element): String? =
        element.annotationMirrors.firstNotNullOfOrNull { mirror ->
            val annotationElement = mirror.annotationType.asElement() as TypeElement
            val args =
                mirror.elementValues.entries.map { (attribute, value) ->
                    attribute.simpleName.toString() to value.value
                }
            Introspections.getDescriptionFromAnnotation(
                simpleName = annotationElement.simpleName.toString(),
                qualifiedName = annotationElement.qualifiedName.toString(),
                annotationArguments = args,
            )
        }
}
