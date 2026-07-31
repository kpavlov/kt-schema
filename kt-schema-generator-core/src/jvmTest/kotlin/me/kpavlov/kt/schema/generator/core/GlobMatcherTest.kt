package me.kpavlov.kt.schema.generator.core

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobMatcherTest {

    //region globToRegex

    @ParameterizedTest(name = "glob `{0}` on `{1}` → {2}")
    @MethodSource("globMatchCases")
    fun `globToRegex matches correctly`(
        glob: String,
        input: String,
        expected: Boolean,
    ) {
        globToRegex(glob).matches(input) shouldBe expected
    }

    fun globMatchCases() =
        listOf(
            // single star — matches within one dot-segment only
            Arguments.of("com.example.*", "com.example.Foo", true),
            Arguments.of("com.example.*", "com.example.FooBar", true),
            Arguments.of("com.example.*", "com.example.sub.Foo", false),
            Arguments.of("com.example.*", "com.other.Foo", false),
            // double star — matches across dots
            Arguments.of("com.example.**", "com.example.Foo", true),
            Arguments.of("com.example.**", "com.example.sub.Foo", true),
            Arguments.of("com.example.**", "com.example.a.b.c.Foo", true),
            Arguments.of("com.example.**", "com.other.Foo", false),
            // suffix glob
            Arguments.of("**.*Dto", "com.example.UserDto", true),
            Arguments.of("**.*Dto", "com.example.sub.OrderDto", true),
            Arguments.of("**.*Dto", "com.example.UserService", false),
            // question mark — single non-dot character
            Arguments.of("com.example.Fo?", "com.example.Foo", true),
            Arguments.of("com.example.Fo?", "com.example.For", true),
            Arguments.of("com.example.Fo?", "com.example.Fo", false),
            Arguments.of("com.example.Fo?", "com.example.Fooo", false),
            Arguments.of("com.example.Fo?", "com.example.F.o", false),
            // exact match
            Arguments.of("com.example.MyClass", "com.example.MyClass", true),
            Arguments.of("com.example.MyClass", "com.example.MyClassX", false),
            Arguments.of("com.example.MyClass", "com.other.MyClass", false),
        )

    //endregion

    //region parseGlobPatterns

    @ParameterizedTest(name = "`{0}` → {1}")
    @MethodSource("parsePatternsCases")
    fun `parseGlobPatterns splits and trims tokens`(
        value: String?,
        expected: List<String>,
    ) {
        parseGlobPatterns(value) shouldBe expected
    }

    fun parsePatternsCases() =
        listOf(
            Arguments.of(null, emptyList<String>()),
            Arguments.of("", emptyList<String>()),
            Arguments.of("   ", emptyList<String>()),
            Arguments.of("com.example.**", listOf("com.example.**")),
            Arguments.of("  com.example.*  ", listOf("com.example.*")),
            Arguments.of("**.*Dto, **.*Service", listOf("**.*Dto", "**.*Service")),
            Arguments.of("**.*Dto;**.*Service", listOf("**.*Dto", "**.*Service")),
            Arguments.of("**.*Dto, ; **.*Service", listOf("**.*Dto", "**.*Service")),
        )

    //endregion

    //region GlobMatcher

    data class MatcherCase(
        val description: String,
        val name: String,
        val include: List<String>,
        val exclude: List<String>,
        val expectedMatches: Boolean,
        val expectedInclude: Boolean,
        val expectedExclude: Boolean,
    ) {
        override fun toString() = description
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matcherCases")
    fun `GlobMatcher filters names correctly`(case: MatcherCase) {
        val matcher = GlobMatcher(case.include, case.exclude)
        assertSoftly(matcher) {
            matches(case.name) shouldBe case.expectedMatches
            matchesInclude(case.name) shouldBe case.expectedInclude
            matchesExclude(case.name) shouldBe case.expectedExclude
        }
    }

    fun matcherCases() =
        listOf(
            MatcherCase(
                description = "no patterns includes everything",
                name = "com.example.Foo",
                include = emptyList(),
                exclude = emptyList(),
                expectedMatches = true,
                expectedInclude = false,
                expectedExclude = false,
            ),
            MatcherCase(
                description = "include match includes the name",
                name = "com.example.Foo",
                include = listOf("com.example.*"),
                exclude = emptyList(),
                expectedMatches = true,
                expectedInclude = true,
                expectedExclude = false,
            ),
            MatcherCase(
                description = "include non-match excludes the name",
                name = "com.other.Foo",
                include = listOf("com.example.*"),
                exclude = emptyList(),
                expectedMatches = false,
                expectedInclude = false,
                expectedExclude = false,
            ),
            MatcherCase(
                description = "exclude match drops an included name",
                name = "com.example.internal.Foo",
                include = listOf("com.example.**"),
                exclude = listOf("**.internal.**"),
                expectedMatches = false,
                expectedInclude = true,
                expectedExclude = true,
            ),
            MatcherCase(
                description = "exclude without include still drops",
                name = "com.example.Foo",
                include = emptyList(),
                exclude = listOf("com.example.Foo"),
                expectedMatches = false,
                expectedInclude = false,
                expectedExclude = true,
            ),
            MatcherCase(
                description = "multiple include patterns - any match includes",
                name = "com.example.OrderService",
                include = listOf("**.*Dto", "**.*Service"),
                exclude = emptyList(),
                expectedMatches = true,
                expectedInclude = true,
                expectedExclude = false,
            ),
        )

    //endregion
}
