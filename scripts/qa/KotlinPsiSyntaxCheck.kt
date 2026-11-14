import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File
import kotlin.system.exitProcess

/** Kotlin compiler PSI syntax only. Does NOT resolve Android/Compose types or build APKs. */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Pass source directories or Kotlin files" }
    val disposable = Disposer.newDisposable()
    var files = 0
    var errors = 0
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project, false)
        val paths = args.flatMap { path -> File(path).let { file -> if (file.isDirectory) file.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() else listOf(file) } }.distinct()
        for (file in paths) {
            val tree = factory.createFile(file.name, file.readText())
            files++
            for (error in PsiTreeUtil.collectElementsOfType(tree, PsiErrorElement::class.java)) {
                val line = tree.text.take(error.textOffset).count { it == '\n' } + 1
                System.err.println("${file.path}:$line: ${error.errorDescription}")
                errors++
            }
        }
        println("Kotlin compiler PSI: $files files, $errors syntax errors (not Android type checking)")
    } finally { Disposer.dispose(disposable) }
    if (errors != 0) exitProcess(1)
}
