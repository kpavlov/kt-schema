package me.kpavlov.kt.schema.apt.ir

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.apt.JavaSources
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.io.StringWriter
import java.nio.file.Files
import java.util.stream.Stream
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AptClassIntrospectorTest {
    //region test cases

    @Test
    fun `should introspect plain class instance fields and exclude static fields`() {
        val graph =
            graph(
                root = "com.example.Company",
                javaClass(
                    "com.example",
                    "Company",
                    """
                        private String name;
                        private int founded;
                        private boolean active;
                        private static final String VERSION = "1.0";
                        public Company(String name, int founded, boolean active) {
                            this.name = name;
                            this.founded = founded;
                            this.active = active;
                        }
                    """.trimIndent(),
                ),
            )

        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        assertSoftly(rootRef) {
            id.value shouldBe "com.example.Company"
            nullable shouldBe false
        }

        val companyNode = graph.nodes.getValue(rootRef.id).shouldBeInstanceOf<ObjectNode>()
        assertSoftly(companyNode) {
            required.shouldContainExactlyInAnyOrder(setOf("name", "founded", "active"))
            val props = properties.associateBy { it.name }
            props.keys.shouldContainExactlyInAnyOrder(setOf("name", "founded", "active"))
            props.getValue("name").type.shouldBePrimitive(PrimitiveKind.STRING)
            props.getValue("founded").type.shouldBePrimitive(PrimitiveKind.INT)
            props.getValue("active").type.shouldBePrimitive(PrimitiveKind.BOOLEAN)
        }
    }

    @ParameterizedTest(name = "should map {0} to {1}")
    @CsvSource(
        "byte, INT",
        "short, INT",
        "int, INT",
        "long, LONG",
        "float, FLOAT",
        "double, DOUBLE",
        "boolean, BOOLEAN",
        "char, STRING",
        "java.lang.String, STRING",
        "java.lang.Byte, INT",
        "java.lang.Short, INT",
        "java.lang.Integer, INT",
        "java.lang.Long, LONG",
        "java.lang.Float, FLOAT",
        "java.lang.Double, DOUBLE",
        "java.lang.Boolean, BOOLEAN",
        "java.lang.Character, STRING",
    )
    fun `should map java scalar types to primitive kinds`(
        javaType: String,
        expectedKind: String,
    ) {
        val graph =
            graph(
                root = "com.example.Scalars",
                javaClass(
                    "com.example",
                    "Scalars",
                    """
                        private $javaType value;
                        public Scalars($javaType value) {
                            this.value = value;
                        }
                    """.trimIndent(),
                ),
            )

        graph.rootNode().properties.single().type.shouldBePrimitive(PrimitiveKind.valueOf(expectedKind))
    }

    @Test
    fun `should introspect nested object field as ref with object node registered in graph`() {
        val graph =
            graph(
                root = "com.example.Person",
                javaClass(
                    "com.example",
                    "Address",
                    """
                        public String city;
                        public String street;
                    """.trimIndent(),
                ),
                javaClass(
                    "com.example",
                    "Person",
                    """
                        public String name;
                        public Address address;
                    """.trimIndent(),
                ),
            )

        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val personNode = graph.nodes.getValue(rootRef.id).shouldBeInstanceOf<ObjectNode>()
        personNode.properties.map { it.name }.shouldContainExactlyInAnyOrder(setOf("name", "address"))

        personNode.properties.first { it.name == "address" }.type.shouldBeInstanceOf<TypeRef.Ref> { ref ->
            ref.id.value shouldBe "com.example.Address"
            ref.nullable shouldBe false
        }

        graph.nodes.getValue(TypeId("com.example.Address")).shouldBeInstanceOf<ObjectNode> { addressNode ->
            addressNode.properties.map { it.name }.shouldContainExactlyInAnyOrder(setOf("city", "street"))
        }
    }

    @Test
    fun `should introspect java lang Object field as inline AnyNode`() {
        val graph =
            graph(
                root = "com.example.Wrapper",
                javaClass("com.example", "Wrapper", "public Object payload;"),
            )

        graph.rootNode().properties.single().type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<AnyNode>()
            inline.nullable shouldBe false
        }

        // java.lang.Object must not create a named node in the graph
        graph.nodes.keys.none { it.value == "java.lang.Object" } shouldBe true
    }

    @Test
    fun `should introspect list set and collection fields as inline list nodes`() {
        val graph =
            graph(
                root = "com.example.Bundle",
                javaClass(
                    "com.example",
                    "Bundle",
                    """
                        public java.util.List<String> names;
                        public java.util.Set<Integer> scores;
                        public java.util.Collection<Double> ratios;
                    """.trimIndent(),
                ),
            )

        val props = graph.rootNode().properties.associateBy { it.name }

        assertSoftly(props) {
            getValue("names").type.shouldBeList { element ->
                element.shouldBePrimitive(PrimitiveKind.STRING)
            }
            getValue("scores").type.shouldBeList { element ->
                element.shouldBePrimitive(PrimitiveKind.INT)
            }
            getValue("ratios").type.shouldBeList { element ->
                element.shouldBePrimitive(PrimitiveKind.DOUBLE)
            }
        }
    }

    @Test
    fun `should introspect map field as inline map node with key and value types`() {
        val graph =
            graph(
                root = "com.example.Attributes",
                javaClass(
                    "com.example",
                    "Attributes",
                    "public java.util.Map<String, Integer> attributes;",
                ),
            )

        graph.rootNode().properties.single().type.shouldBeMap(
            key = { it.shouldBePrimitive(PrimitiveKind.STRING) },
            value = { it.shouldBePrimitive(PrimitiveKind.INT) },
        )
    }

    @Test
    fun `should introspect array fields as inline list nodes with component type`() {
        val graph =
            graph(
                root = "com.example.ArraysHolder",
                javaClass(
                    "com.example",
                    "ArraysHolder",
                    """
                        public String[] names;
                        public int[] counts;
                        public Integer[] boxed;
                        public double[][] matrix;
                    """.trimIndent(),
                ),
            )

        val props = graph.rootNode().properties.associateBy { it.name }

        assertSoftly(props) {
            getValue("names").type.shouldBeList { it.shouldBePrimitive(PrimitiveKind.STRING) }
            getValue("counts").type.shouldBeList { it.shouldBePrimitive(PrimitiveKind.INT) }
            getValue("boxed").type.shouldBeList { it.shouldBePrimitive(PrimitiveKind.INT) }
            getValue("matrix").type.shouldBeList { nested ->
                nested.shouldBeList { it.shouldBePrimitive(PrimitiveKind.DOUBLE) }
            }
        }
    }

    @Test
    fun `should introspect nested collections`() {
        val graph =
            graph(
                root = "com.example.Nested",
                javaClass(
                    "com.example",
                    "Nested",
                    """
                        public java.util.List<java.util.List<String>> matrix;
                        public java.util.Map<String, java.util.List<Integer>> grouped;
                        public java.util.List<java.util.Map<String, java.lang.Boolean>> flags;
                    """.trimIndent(),
                ),
            )

        val props = graph.rootNode().properties.associateBy { it.name }

        assertSoftly(props) {
            getValue("matrix").type.shouldBeList { nested ->
                nested.shouldBeList { it.shouldBePrimitive(PrimitiveKind.STRING) }
            }
            getValue("grouped").type.shouldBeMap(
                key = { it.shouldBePrimitive(PrimitiveKind.STRING) },
                value = { value -> value.shouldBeList { it.shouldBePrimitive(PrimitiveKind.INT) } },
            )
            getValue("flags").type.shouldBeList { element ->
                element.shouldBeMap(
                    key = { it.shouldBePrimitive(PrimitiveKind.STRING) },
                    value = { it.shouldBePrimitive(PrimitiveKind.BOOLEAN) },
                )
            }
        }
    }

    @ParameterizedTest(name = "should introspect {0} of nested objects with ref element")
    @MethodSource("objectContainerFields")
    fun `should introspect container of nested objects with ref element`(
        @Suppress("UnusedParameter") kind: String,
        fieldDeclaration: String,
        assertFieldType: (TypeRef) -> Unit,
    ) {
        val graph =
            graph(
                root = "com.example.Catalog",
                javaClass("com.example", "Address", "public String city;"),
                javaClass("com.example", "Catalog", "public $fieldDeclaration;"),
            )

        val fieldType = graph.rootNode().properties.single().type
        assertFieldType(fieldType)

        graph.nodes.keys.any { it.value == "com.example.Address" } shouldBe true
    }

    @Test
    fun `should introspect record components as object node properties`() {
        val graph =
            graph(
                root = "com.example.Person",
                javaRecord("com.example", "Person(String name, int age)"),
            )

        val node = graph.rootNode()
        assertSoftly(node) {
            required.shouldContainExactlyInAnyOrder(setOf("name", "age"))
            val props = properties.associateBy { it.name }
            props.getValue("name").type.shouldBePrimitive(PrimitiveKind.STRING)
            props.getValue("age").type.shouldBePrimitive(PrimitiveKind.INT)
        }
    }

    @Test
    fun `should introspect upper bounded type variable via its bound`() {
        val graph =
            graph(
                root = "com.example.Box",
                javaRecord("com.example", "Box<T extends Number>(T value)"),
            )

        graph.rootNode().properties.single().type.shouldBeInstanceOf<TypeRef.Ref> { ref ->
            ref.id.value shouldBe "java.lang.Number"
        }

        graph.nodes.keys.any { it.value == "java.lang.Number" } shouldBe true
    }

    @Test
    fun `should introspect unbounded type variable as inline AnyNode`() {
        val graph =
            graph(
                root = "com.example.Box",
                javaRecord("com.example", "Box<T>(T value)"),
            )

        graph.rootNode().properties.single().type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<AnyNode>()
        }
    }

    @Test
    fun `should treat Nullable-annotated field as nullable and exclude it from required`() {
        val graph =
            graph(
                root = "com.example.Contact",
                """
                    package com.example;

                    public @interface Nullable {}
                """.trimIndent(),
                javaClass(
                    "com.example",
                    "Contact",
                    """
                        public String name;
                        @Nullable
                        public String phone;
                    """.trimIndent(),
                ),
            )

        val node = graph.rootNode()
        assertSoftly(node) {
            required.shouldContainExactlyInAnyOrder(setOf("name"))
            val props = properties.associateBy { it.name }
            props.getValue("phone").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
                inline.node.shouldBeInstanceOf<PrimitiveNode> { prim -> prim.kind shouldBe PrimitiveKind.STRING }
                inline.nullable shouldBe true
            }
        }
    }

    @Test
    fun `should treat Opt-suffixed type name as nullable but keep field required by default`() {
        val graph =
            graph(
                root = "com.example.Contact",
                javaClass("com.example", "EmailOpt", "public String value;"),
                javaClass(
                    "com.example",
                    "Contact",
                    """
                        public String name;
                        public EmailOpt email;
                    """.trimIndent(),
                ),
            )

        val node = graph.rootNode()
        assertSoftly(node) {
            // No default `introspector.optional.type.names` pattern — matching a nullable-by-convention
            // type name doesn't by itself exclude the property from `required`.
            required.shouldContainExactlyInAnyOrder(setOf("name", "email"))
            val props = properties.associateBy { it.name }
            props.getValue("email").type.shouldBeInstanceOf<TypeRef.Ref> { ref ->
                ref.id.value shouldBe "com.example.EmailOpt"
                ref.nullable shouldBe true
            }
        }
    }

    //endregion

    //region helpers

    private fun objectContainerFields(): Stream<Arguments> =
        Stream.of(
            Arguments.of(
                "list",
                "java.util.List<Address> addresses",
                { field: TypeRef ->
                    field.shouldBeList { element ->
                        element.shouldBeRefTo("com.example.Address")
                    }
                },
            ),
            Arguments.of(
                "collection",
                "java.util.Collection<Address> addresses",
                { field: TypeRef ->
                    field.shouldBeList { element ->
                        element.shouldBeRefTo("com.example.Address")
                    }
                },
            ),
            Arguments.of(
                "map",
                "java.util.Map<String, Address> byCity",
                { field: TypeRef ->
                    field.shouldBeMap(
                        key = { it.shouldBePrimitive(PrimitiveKind.STRING) },
                        value = { it.shouldBeRefTo("com.example.Address") },
                    )
                },
            ),
        )

    private fun graph(
        root: String,
        vararg sources: String,
    ): TypeGraph {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("No system Java compiler available — run on JDK, not JRE")

        val rootDir = Files.createTempDirectory("kt-schema-apt-test")
        val outputDir = rootDir.resolve("classes").also { Files.createDirectories(it) }
        try {
            val diagnostics = DiagnosticCollector<JavaFileObject>()
            val sourceFiles = sources.map(JavaSources::of)

            val processor =
                object : AbstractProcessor() {
                    var capturedGraph: TypeGraph? = null

                    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

                    override fun getSupportedAnnotationTypes(): MutableSet<String> = mutableSetOf("*")

                    override fun process(
                        annotations: MutableSet<out TypeElement>,
                        roundEnv: RoundEnvironment,
                    ): Boolean {
                        if (capturedGraph == null) {
                            val element =
                                roundEnv.rootElements
                                    .filterIsInstance<TypeElement>()
                                    .firstOrNull { it.qualifiedName.contentEquals(root) }
                            if (element != null) {
                                // Introspect while the JSR 269 round is active so the elements
                                // and processing environment stay valid.
                                capturedGraph = AptClassIntrospector(processingEnv).introspect(element)
                            }
                        }
                        return false
                    }
                }

            val writer = StringWriter()
            val task =
                compiler.getTask(
                    writer,
                    null,
                    diagnostics,
                    listOf("-d", outputDir.toFile().absolutePath),
                    null,
                    sourceFiles,
                )
            task.setProcessors(listOf(processor))

            val success = task.call()
            if (!success) {
                val messages = diagnostics.diagnostics.joinToString("\n") { it.toString() }
                error("Compilation failed:\n$messages\nCompiler output:\n$writer")
            }

            return processor.capturedGraph ?: error("Type $root not found in compilation")
        } finally {
            rootDir.toFile().deleteRecursively()
        }
    }

    private fun javaClass(
        packageName: String,
        className: String,
        members: String,
    ): String = """
        package $packageName;

        public class $className {
        $members
        }
    """.trimIndent()

    private fun javaRecord(
        packageName: String,
        declaration: String,
    ): String = """
        package $packageName;

        public record $declaration {}
    """.trimIndent()

    private fun TypeGraph.rootNode(): ObjectNode =
        root.shouldBeInstanceOf<TypeRef.Ref>()
            .let { nodes.getValue(it.id) }
            .shouldBeInstanceOf<ObjectNode>()

    private fun TypeRef.shouldBePrimitive(kind: PrimitiveKind) {
        shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                prim.kind shouldBe kind
            }
        }
    }

    private fun TypeRef.shouldBeList(element: (TypeRef) -> Unit) {
        shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<ListNode> { list ->
                element(list.element)
            }
        }
    }

    private fun TypeRef.shouldBeMap(
        key: (TypeRef) -> Unit,
        value: (TypeRef) -> Unit,
    ) {
        shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<MapNode> { map ->
                key(map.key)
                value(map.value)
            }
        }
    }

    private fun TypeRef.shouldBeRefTo(fqn: String) {
        shouldBeInstanceOf<TypeRef.Ref> { ref ->
            ref.id.value shouldBe fqn
        }
    }

    //endregion
}
