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
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.RecordComponentElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types

/**
 * Introspection context for the Java annotation-processor (JSR 269) front end.
 *
 * Supports Java records, plain classes and interfaces with primitive/boxed/String fields
 * and nested references. Reference types are treated as non-nullable/required: Java has
 * no notion of optionality/default values, so every property is required.
 *
 * @author Konstantin Pavlov
 */
@OptIn(InternalSchemaGeneratorApi::class)
@Suppress("TooManyFunctions")
internal class AptIntrospectionContext(
    private val types: Types,
) : BaseIntrospectionContext<TypeMirror>() {
    //region Type conversion

    override fun toRef(type: TypeMirror): TypeRef {
        primitiveKindFor(type)?.let { return TypeRef.Inline(PrimitiveNode(it)) }

        return handleRecord(type)
            ?: handleClass(type)
            ?: handleInterface(type)
            ?: error(
                "Unsupported type for kt-schema-apt " +
                    "(only records, classes, interfaces, primitives and String are supported): $type",
            )
    }

    private fun primitiveKindFor(type: TypeMirror): PrimitiveKind? =
        when (type.kind) {
            TypeKind.BOOLEAN -> PrimitiveKind.BOOLEAN
            TypeKind.INT, TypeKind.SHORT, TypeKind.BYTE -> PrimitiveKind.INT
            TypeKind.LONG -> PrimitiveKind.LONG
            TypeKind.FLOAT -> PrimitiveKind.FLOAT
            TypeKind.DOUBLE -> PrimitiveKind.DOUBLE
            TypeKind.CHAR -> PrimitiveKind.STRING
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
            "java.lang.Character" -> PrimitiveKind.STRING
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
                props += toProperty(name, component.asType(), recordComponentDescription(component, element))
            }

            objectNode(element, props, required)
        }

        return TypeRef.Ref(id)
    }

    private fun handleClass(type: TypeMirror): TypeRef? {
        val element = asTypeElement(type)
        if (element == null || element.kind != ElementKind.CLASS) return null

        val id = TypeId(element.qualifiedName.toString())

        withCycleDetection(type, id) {
            val props = ArrayList<Property>()
            val required = LinkedHashSet<String>()

            element.enclosedElements
                .filterIsInstance<VariableElement>()
                .filter { it.kind == ElementKind.FIELD && !it.modifiers.contains(Modifier.STATIC) }
                .forEach { field ->
                    val name = field.simpleName.toString()
                    required += name
                    props += toProperty(name, field.asType(), extractDescription(field))
                }

            objectNode(element, props, required)
        }

        return TypeRef.Ref(id)
    }

    private fun handleInterface(type: TypeMirror): TypeRef? {
        val element = asTypeElement(type)
        if (element == null || element.kind != ElementKind.INTERFACE) return null

        val id = TypeId(element.qualifiedName.toString())

        withCycleDetection(type, id) {
            val props = ArrayList<Property>()
            val required = LinkedHashSet<String>()

            element.enclosedElements
                .filterIsInstance<ExecutableElement>()
                .filter { it.kind == ElementKind.METHOD }
                .filter { !it.modifiers.contains(Modifier.STATIC) }
                .filter { it.parameters.isEmpty() && it.returnType.kind != TypeKind.VOID }
                .forEach { method ->
                    val name = propertyName(method.simpleName.toString())
                    required += name
                    props += toProperty(name, method.returnType, extractDescription(method))
                }

            objectNode(element, props, required)
        }

        return TypeRef.Ref(id)
    }

    /**
     * Maps a no-arg accessor method to a property name using the JavaBeans convention:
     * `getName()` → `name`, `isActive()` → `active`, and a bare `name()` accessor is kept as-is.
     */
    private fun propertyName(methodName: String): String =
        when {
            methodName.length > GET_PREFIX.length &&
                methodName.startsWith(GET_PREFIX) &&
                methodName[GET_PREFIX.length].isUpperCase() ->
                decapitalize(methodName.substring(GET_PREFIX.length))

            methodName.length > IS_PREFIX.length &&
                methodName.startsWith(IS_PREFIX) &&
                methodName[IS_PREFIX.length].isUpperCase() ->
                decapitalize(methodName.substring(IS_PREFIX.length))

            else -> methodName
        }

    /**
     * Applies JavaBeans decapitalization to a property suffix: `Name` → `name`, while
     * preserving all-uppercase acronyms such as `URL` or `OK` and leaving mixed-case
     * suffixes such as `urlPath` untouched.
     */
    private fun decapitalize(name: String): String =
        if (name.length > 1 && name[0].isUpperCase() && name[1].isUpperCase()) {
            name
        } else {
            name.replaceFirstChar { it.lowercase() }
        }

    private companion object {
        const val GET_PREFIX: String = "get"
        const val IS_PREFIX: String = "is"
    }

    //endregion

    //region Property conversion

    private fun toProperty(
        name: String,
        type: TypeMirror,
        description: String?,
    ): Property =
        Property(
            name = name,
            type = toRef(type),
            description = description,
        )

    private fun objectNode(
        element: TypeElement,
        properties: List<Property>,
        required: Set<String>,
    ): ObjectNode =
        ObjectNode(
            name = element.qualifiedName.toString(),
            properties = properties,
            required = required,
            description = extractDescription(element),
        )

    //endregion

    //region Annotation helpers

    /**
     * Resolves a record component's description from its annotation targets, in order of
     * precedence: the record component itself, its accessor, then the backing field.
     */
    private fun recordComponentDescription(
        component: RecordComponentElement,
        type: TypeElement,
    ): String? =
        extractDescription(component)
            ?: extractDescription(component.accessor)
            ?: fieldFor(type, component.simpleName.toString())?.let(::extractDescription)

    /**
     * Record component annotations propagate to the backing field (among other targets),
     * so the field is used as a last-resort description target for annotations whose
     * `@Target` does not cover the record component or its accessor.
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

    //endregion
}
