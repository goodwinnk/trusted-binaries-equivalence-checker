package org.jetbrains.tbec

import net.lingala.zip4j.ZipFile
import org.apache.commons.codec.digest.DigestUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectory
import kotlin.io.path.extension
import kotlin.streams.toList
import kotlin.system.exitProcess

fun main() {
    val version = "1.6.20-dev-491"
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

    val checker = Checker(checkerExceptions, hashAlgo = algo, version = version, progress = progress)
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

class TempDir(private val prefix: String) {
    private var pathInternal: Path? = null

    val path : Path get() {
        return pathInternal ?: Files.createTempDirectory(prefix).also {
            pathInternal = it
        }
    }

    fun delete() {
        val dir = pathInternal?.toFile() ?: return
        if (!dir.exists()) return
        dir.deleteRecursively()
    }
}

class Checker(
    private val exceptions: Set<String>,
    val hashAlgo: String,
    val version: String? = null,
    val progress: (String) -> Unit = {}
) {
    private enum class DiffKind {
        MISSING_EXIST,
        EXIST_MISSING,
        FILE_DIR,
        DIR_FILE,
        TIMESTAMP,
        HASH
    }

    companion object {
        private val zipExtension = setOf("zip", "jar")
        const val ROOT = "<>"
    }

    private val _errors: MutableList<String> = mutableListOf()
    private val _warnings: MutableList<String> = mutableListOf()
    private var tempDir = TempDir("eq-checker")

    val errors: List<String> get() = _errors
    val warnings: List<String> get() = _warnings

    fun check(left: Path, right: Path) {
        try {
            _errors.clear()
            _warnings.clear()
            Files.createTempDirectory("tmpDirPrefix")

            check(left, right, ROOT)
        } finally {
            tempDir.delete()
        }
    }

    private fun report(kind: DiffKind, path: String, message: String? = null): Boolean {
        @Suppress("NAME_SHADOWING") val message = message ?: when (kind) {
            DiffKind.MISSING_EXIST -> "missing -> exist"
            DiffKind.EXIST_MISSING -> "exist <- missing"
            DiffKind.FILE_DIR -> "file != directory"
            DiffKind.DIR_FILE -> "directory != file"
            DiffKind.TIMESTAMP -> "timestamps"
            DiffKind.HASH -> "hashes"
        }

        val full = "$path: $message"
        return if (path !in exceptions) {
            progress("ERROR: $full")
            _errors.add(full)
            true
        } else {
            progress("WARN: $full")
            _warnings.add(full)
            false
        }
    }

    private fun check(left: Path, right: Path, path: String) {
        val leftExist = Files.exists(left)
        val rightExist = Files.exists(right)

        if (!leftExist && !rightExist) return
        if (!leftExist) {
            report(DiffKind.MISSING_EXIST, path)
            return
        }
        if (!rightExist) {
            report(DiffKind.EXIST_MISSING, path)
            return
        }

        val isLeftDirectory = Files.isDirectory(left)
        val isRightDirectory = Files.isDirectory(right)

        if (isLeftDirectory != isRightDirectory) {
            if (isLeftDirectory) {
                report(DiffKind.DIR_FILE, path)
            } else {
                report(DiffKind.FILE_DIR, path)
            }
            return
        }

        if (isLeftDirectory) {
            checkDirectories(left, right, path)
        } else {
            checkFiles(left, right, path)
        }
    }

    private fun checkDirectories(left: Path, right: Path, path: String) {
        check(Files.isDirectory(left) && Files.isDirectory(right)) {
            "Should be directories: `$left` `$right` `$path`"
        }

        progress("Enter: $path")

        val leftChildren = Files.list(left).map { left.relativize(it) }.toList().toSet()
        val rightChildren = Files.list(right).map { right.relativize(it) }.toList().toSet()

        for (leftChild in leftChildren) {
            val nextPath = "$path/${leftChild.toString().replace("\\", "/")}"

            if (!rightChildren.contains(leftChild)) {
                report(DiffKind.EXIST_MISSING, nextPath, "exist <- missing")
            } else {
                check(left.resolve(leftChild), right.resolve(leftChild), nextPath)
            }
        }

        for (rightChild in rightChildren) {
            val nextPath = "$path/${rightChild.toString().replace("\\", "/")}"

            if (!leftChildren.contains(rightChild)) {
                report(DiffKind.MISSING_EXIST, nextPath, "missing <- exist")
            }
        }
    }

    private fun checkFiles(left: Path, right: Path, path: String) {
        check(Files.isRegularFile(left) && Files.isRegularFile(right)) {
            "Should be directories: `$left` `$right` `$path`"
        }

        progress("File: $path")

        val leftAttributes = Files.readAttributes(left, BasicFileAttributes::class.java)
        val rightAttributes = Files.readAttributes(right, BasicFileAttributes::class.java)
        if (leftAttributes.creationTime() != rightAttributes.creationTime()) {
            report(DiffKind.TIMESTAMP, path, "${leftAttributes.creationTime()} != ${rightAttributes.creationTime()}")
        }

        val leftHash = left.hash(hashAlgo)
        val rightHash = right.hash(hashAlgo)

        if (leftHash != rightHash) {
            if (report(DiffKind.HASH, path, "$leftHash != $rightHash")) {
                return
            }

            if (left.extension in zipExtension) {
                val fileDirPath = Files.createDirectories(tempDir.path.resolve(escapePath(path)))

                progress("Unzipping: $path -> $fileDirPath")

                val leftZipPath = fileDirPath.resolve("left").createDirectory()
                ZipFile(left.toFile()).extractAll(leftZipPath.toAbsolutePath().toString())

                val rightZipPath = fileDirPath.resolve("right")
                ZipFile(right.toFile()).extractAll(rightZipPath.createDirectory().toAbsolutePath().toString())

                check(leftZipPath, rightZipPath, path)
            }
        }
    }
}

fun escapePath(path: String) = path.replace("<", "_").replace(">", "_")

fun Path.hash(algo: String): String =
    Files.newInputStream(this).use { fileInputStream ->
        when (algo) {
            "sha256" -> DigestUtils.sha256Hex(fileInputStream)
            "md5" -> DigestUtils.md5Hex(fileInputStream)
            else -> exitWithError("Unknown hash algorithm '$algo' is used . md5 and sha256 are supported.")
        }
    }

fun exitWithError(error: String, exitCode: Int = 1): Nothing {
    System.err.println(error)
    exitProcess(exitCode)
}
