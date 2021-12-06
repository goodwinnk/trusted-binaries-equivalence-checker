package org.jetbrains.tbec

import java.nio.file.Path
import kotlin.system.exitProcess

fun main() {
    val path1 = "kotlin-compiler-1.6.20-dev-491-cache.zip"
    val path2 = "kotlin-compiler-1.6.20-dev-491-clean.zip"
    val algo = "md5"
    val verbose = true
    val exceptions = listOf(
        "<>",
        "kotlinc/lib/kotlin-test-js.jar",
        "kotlinc/lib/kotlin-stdlib-js.jar",
        "kotlinc/lib/kotlin-test-js.jar/default/ir/files.knf",
        "kotlinc/lib/kotlin-stdlib-js.jar/default/ir/files.knf"
    )

    val file1 = Path.of(path1)
    val file2 = Path.of(path2)

    val progress: (String) -> Unit = if (verbose) { message -> println(message) } else { _ -> /* do nothing */ }

    val checkerExceptions = exceptions.map {
        if (it.startsWith(Checker.ROOT)) it else "${Checker.ROOT}/$it"
    }.toSet()

    val checker = Checker(checkerExceptions, hashAlgo = algo, progress = progress)
    checker.check(file1, file2)
    val errors = checker.errors
    val warnings = checker.warnings

    if (warnings.isNotEmpty()) {
        println("Warnings:")
        println(warnings.joinToString("\n") { "\t$it" })
    }

    if (errors.isNotEmpty()) {
        System.err.println(errors.joinToString("\n"))
        exitProcess(1)
    }
}

fun exitWithError(error: String, exitCode: Int = 1): Nothing {
    System.err.println(error)
    exitProcess(exitCode)
}
