package me.kpavlov.kt.schema.ksp

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import io.mockk.every
import io.mockk.mockk

internal fun ksName(text: String): KSName =
    mockk {
        every { asString() } returns text
    }

/** Mocks an annotation of the class [annotationClassName] with the given arguments. */
internal fun annotationMock(
    annotationClassName: String,
    vararg annotationArguments: KSValueArgument,
): KSAnnotation {
    val annotationClass =
        mockk<KSDeclaration> {
            every { simpleName } returns ksName(annotationClassName.substringAfterLast('.'))
            every { qualifiedName } returns ksName(annotationClassName)
        }
    val resolvedType =
        mockk<KSType> {
            every { declaration } returns annotationClass
        }
    val typeReference =
        mockk<KSTypeReference> {
            every { resolve() } returns resolvedType
        }
    return mockk {
        every { annotationType } returns typeReference
        every { arguments } returns annotationArguments.toList()
    }
}
