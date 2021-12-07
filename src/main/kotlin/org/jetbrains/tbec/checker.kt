package org.jetbrains.tbec

import net.lingala.zip4j.ZipFile
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.tbec.DiffKind.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectory
import kotlin.io.path.extension
import kotlin.streams.toList

class TempDir(private val prefix: String) {
    private var pathInternal: Path? = null

    val path : Path
        get() {
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

class CheckerException(message: String): Exception(message)

class Checker(
    val exceptionsPatterns: List<String>,
    val hashAlgo: String,
    val progress: (String) -> Unit = {}
) {
    companion object {
        private val zipExtension = setOf("zip", "jar")
        const val ROOT = "<>"
    }

    private val exceptionRules = exceptionsPatterns.map { DiffExceptionRule.parseExceptionRulePattern(it) }

    private val _errors: MutableList<String> = mutableListOf()
    private val _warnings: MutableList<String> = mutableListOf()
    private val _usedExceptions: MutableSet<String> = mutableSetOf()
    private var tempDir = TempDir("eq-checker")

    val errors: List<String> get() = _errors
    val warnings: List<String> get() = _warnings
    val unusedExceptions: List<String> get() = exceptionsPatterns.filterNot { it in _usedExceptions }

    fun check(left: Path, right: Path) {
        try {
            _errors.clear()
            _warnings.clear()
            _usedExceptions.clear()

            Files.createTempDirectory("tmpDirPrefix")

            check(left, right, ROOT)
        } finally {
            tempDir.delete()
        }
    }

    private fun findExceptionPattern(kind: DiffKind, path: String): String? {
        return exceptionRules.find { it.match(kind, path) }?.pattern
    }

    private fun report(kind: DiffKind, path: String, message: String? = null): Boolean {
        if (path == ROOT && kind == TIMESTAMP) {
            return false
        }

        @Suppress("NAME_SHADOWING") val message = message ?: when (kind) {
            MISSING_EXIST -> "missing -> exist"
            EXIST_MISSING -> "exist <- missing"
            FILE_DIR -> "file != directory"
            DIR_FILE -> "directory != file"
            TIMESTAMP -> "timestamps"
            HASH -> "hashes"
        }

        val full = "$path: $message"
        val exceptionRulePattern = findExceptionPattern(kind, path)

        return if (exceptionRulePattern == null) {
            progress("ERROR: $full")
            _errors.add(full)
            true
        } else {
            progress("WARN: $full")
            _usedExceptions.add(exceptionRulePattern)
            _warnings.add(full)
            false
        }
    }

    private fun check(left: Path, right: Path, path: String) {
        val leftExist = Files.exists(left)
        val rightExist = Files.exists(right)

        if (!leftExist && !rightExist) return
        if (!leftExist) {
            report(MISSING_EXIST, path)
            return
        }
        if (!rightExist) {
            report(EXIST_MISSING, path)
            return
        }

        val isLeftDirectory = Files.isDirectory(left)
        val isRightDirectory = Files.isDirectory(right)

        if (isLeftDirectory != isRightDirectory) {
            if (isLeftDirectory) {
                report(DIR_FILE, path)
            } else {
                report(FILE_DIR, path)
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
                report(EXIST_MISSING, nextPath, "exist <- missing")
            } else {
                check(left.resolve(leftChild), right.resolve(leftChild), nextPath)
            }
        }

        for (rightChild in rightChildren) {
            val nextPath = "$path/${rightChild.toString().replace("\\", "/")}"

            if (!leftChildren.contains(rightChild)) {
                report(MISSING_EXIST, nextPath, "missing <- exist")
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
            report(TIMESTAMP, path, "${leftAttributes.creationTime()} != ${rightAttributes.creationTime()}")
        }

        val leftHash = left.hash(hashAlgo)
        val rightHash = right.hash(hashAlgo)

        if (leftHash != rightHash) {
            if (report(HASH, path, "$leftHash != $rightHash")) {
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

private fun escapePath(path: String) = path.replace("<", "_").replace(">", "_")

private fun Path.hash(algo: String): String =
    Files.newInputStream(this).use { fileInputStream ->
        when (algo) {
            "sha256" -> DigestUtils.sha256Hex(fileInputStream)
            "md5" -> DigestUtils.md5Hex(fileInputStream)
            else -> throw CheckerException("Unknown hash algorithm '$algo' is used . md5 and sha256 are supported.")
        }
    }