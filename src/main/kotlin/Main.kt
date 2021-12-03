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

    private fun addError(path: String, message: String) {
        val full = "$path: $message"
        progress("ERROR: $full")
        _errors.add(full)
    }

    private fun addWarning(path: String, message: String) {
        val full = "$path: $message"
        progress("WARN: $full")
        _warnings.add(full)
    }

    private fun check(left: Path, right: Path, path: String) {
        val leftExist = Files.exists(left)
        val rightExist = Files.exists(right)

        if (!leftExist && !rightExist) return
        if (!leftExist) {
            if (path !in exceptions) {
                addError(path,"missing -> exist")
            }
            return
        }
        if (!rightExist) {
            if (path !in exceptions) {
                addError(path, "exist <- missing")
            }
            return
        }

        val isLeftDirectory = Files.isDirectory(left)
        val isRightDirectory = Files.isDirectory(right)

        if (isLeftDirectory != isRightDirectory) {
            if (isLeftDirectory) {
                addError(path, "directory != file")
            } else {
                addError(path, "file != directory")
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
                if (nextPath !in exceptions) {
                    addError(nextPath, "exist <- missing")
                }
            } else {
                check(left.resolve(leftChild), right.resolve(leftChild), nextPath)
            }
        }

        for (rightChild in rightChildren) {
            val nextPath = "$path/${rightChild.toString().replace("\\", "/")}"

            if (!leftChildren.contains(rightChild)) {
                if (nextPath !in exceptions) {
                    addError(nextPath, "missing <- exist")
                }
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
            addWarning(path, "${leftAttributes.creationTime()} != ${rightAttributes.creationTime()}")
        }

        val leftHash = left.hash(hashAlgo)
        val rightHash = right.hash(hashAlgo)


        if (leftHash != rightHash) {
            if (path !in exceptions) {
                addError(path, "$leftHash != $rightHash")
                return
            }

            addWarning(path, "$leftHash != $rightHash")

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
