package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import me.kpavlov.kt.schema.ksp.ksName
import kotlin.test.Test

class NullabilityTest {
    @Test
    fun `should not fail on a typealias that does not stand for a class`() {
        // Given: an alias that resolves to a non-class declaration
        val unresolved =
            mockk<KSDeclaration> {
                every { simpleName } returns ksName("Unresolved")
            }
        val aliasedType = mockk<KSType> { every { declaration } returns unresolved }
        val alias =
            mockk<KSTypeAlias> {
                every { type } returns mockk { every { resolve() } returns aliasedType }
            }
        val type = mockk<KSType> { every { declaration } returns alias }

        // When / Then
        type.isNullableByTypeName() shouldBe false
        type.isOptionalByTypeName() shouldBe false
    }
}
