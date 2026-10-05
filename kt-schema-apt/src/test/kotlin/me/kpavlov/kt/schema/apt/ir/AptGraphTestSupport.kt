package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.apt.JavaSources
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import org.intellij.lang.annotations.Language
import java.io.StringWriter
import java.nio.file.Files
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/** Compiles [sources] and returns the graph introspected from the type named [root]. */
internal fun graph(
    root: String,
    @Language("java")
    vararg sources: String,
): TypeGraph {
    val compiler =
        ToolProvider.getSystemJavaCompiler()
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
