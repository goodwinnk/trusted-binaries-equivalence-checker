package org.jetbrains.tbec

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.sources.PropertiesValueSource
import com.github.ajalt.clikt.sources.ValueSource
import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

class CheckerCommand : CliktCommandWithFile() {
    private val algo: String by option(help = "Hashing algorithm, md5 and sha256 are supported, md5 is default").default("md5")
    private val exceptions: String by option(
        help = "Comma separated list of paths that are allowed to be different.\n" +
                "'<>' can be used as root, '/' should be used as path separator.\n" +
                "Empty by default.").default("")

    private val progress: Boolean by option(help = "Print progress, false by default").flag(default = false)
    private val noWarnings: Boolean by option(help = "Show muted warnings in the output, false by default").flag(default = false)

    private val left: Path by argument().path(mustExist = true)
    private val right: Path by argument().path(mustExist = true)

    override fun run() {
        val exceptionsList = exceptions.split(",")

        val progressListener: (String) -> Unit = if (progress) { message -> println(message) } else { _ -> /* do nothing */ }

        val checkerExceptions = exceptionsList.map {
            if (it.startsWith(Checker.ROOT)) it else "${Checker.ROOT}/$it"
        }.toSet()

        try {
            val checker = Checker(checkerExceptions, hashAlgo = algo, progress = progressListener)
            checker.check(left, right)
            val errors = checker.errors
            val warnings = checker.warnings

            if (!this.noWarnings && warnings.isNotEmpty()) {
                println("Warnings:")
                println(warnings.joinToString("\n") { "\t$it" })
            }

            if (errors.isNotEmpty()) {
                System.err.println(errors.joinToString("\n"))
                exitProcess(1)
            }
        } catch (ex: CheckerException) {
            System.err.println(ex.message)
            exitProcess(1)
        }
    }
}

fun main(args: Array<String>) {
    CheckerCommand().main(args)
}

abstract class CliktCommandWithFile(name: String? = null, filePath: String = "settings.properties") :
    CliktCommand(name = name) {
    init {
        context {
            valueSource = PropertiesValueSource.from(
                File(filePath),
                getKey = { _, option ->
                    ValueSource.name(option)
                }
            )
        }
    }
}