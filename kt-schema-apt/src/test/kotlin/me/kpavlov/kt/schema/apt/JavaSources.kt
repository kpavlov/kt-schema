package me.kpavlov.kt.schema.apt

import java.net.URI
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject

/**
 * Builds in-memory [JavaFileObject]s for compiler tests, inferring the fully qualified
 * name from the source so `javac` can process sources written as strings.
 */
internal object JavaSources {
    fun of(code: String): JavaFileObject {
        val pkg = Regex("""package\s+(\S+);""").find(code)?.groupValues?.get(1) ?: ""
        val cls =
            Regex("""(?:public\s+)?(?:record|class|interface|enum)\s+(\w+)""").find(code)?.groupValues?.get(1)
                ?: error("Cannot infer class name from source:\n$code")
        val fqn = "$pkg.$cls"
        return object : SimpleJavaFileObject(
            URI.create("string:///${fqn.replace('.', '/')}${JavaFileObject.Kind.SOURCE.extension}"),
            JavaFileObject.Kind.SOURCE,
        ) {
            override fun getCharContent(ignoreEncodingErrors: Boolean) = code
        }
    }
}
