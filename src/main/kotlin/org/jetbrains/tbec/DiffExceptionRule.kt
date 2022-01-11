package org.jetbrains.tbec

import org.jetbrains.tbec.Checker.Companion.ROOT
import org.jetbrains.tbec.DiffExceptionRule.Companion.PatternType.*

class DiffExceptionRule private constructor (
    val pattern: String,
    val kinds: Set<DiffKind>, // Empty set means all kinds
    val path: String,
    val patternType: PatternType,
    val flaky: Boolean
) {
    companion object {
        enum class PatternType {
            STRICT,
            STARTS_WITH,
            ENDS_WITH,
            CONTAINS
        }

        fun parseExceptionRulePattern(pattern: String, replaces: Map<String, String> = emptyMap()): DiffExceptionRule {
            val kinds: Set<DiffKind>
            val flaky: Boolean

            var pathPattern: String
            if (pattern.startsWith("<")) {
                pathPattern = pattern.substringAfterLast(">")

                val kindAndFlakyStr = pattern.substringAfter("<").substringBeforeLast(">").trim()
                val kindAndFlakyParts = kindAndFlakyStr.split(":").map { it.trim() }.filter { it.isNotEmpty() }
                val kindParts = kindAndFlakyParts.filter { part -> !part.uppercase().let { it == "F" || it == "FLAKY" } }

                flaky = kindParts.size < kindAndFlakyParts.size
                kinds = kindParts.map { parseKind(it, pattern) }.toSet()
            } else {
                pathPattern = pattern
                kinds = emptySet()
                flaky = false
            }

            val isStartWildcard = if (pathPattern.startsWith("**")) {
                pathPattern = pathPattern.substringAfter("**")
                true
            } else {
                pathPattern = when {
                    pathPattern.isEmpty() -> ROOT
                    pathPattern.startsWith("/") -> "$ROOT$pathPattern"
                    else -> "$ROOT/$pathPattern"
                }
                false
            }

            val isEndWildcard = if (pathPattern.endsWith("**")) {
                pathPattern = pathPattern.substringBeforeLast("**")
                true
            } else {
                false
            }

            for ((variable, value) in replaces) {
                pathPattern = pathPattern.replace("{$variable}", value)
            }

            val path = pathPattern
            if (path.contains("**")) {
                throw CheckerException("There shouldn't be ** in the middle of the pattern: '$pattern'")
            }

            val patternType = when {
                isStartWildcard && isEndWildcard -> CONTAINS
                !isStartWildcard && isEndWildcard -> STARTS_WITH
                isStartWildcard && !isEndWildcard -> ENDS_WITH
                else -> STRICT
            }
            
            return DiffExceptionRule(pattern, kinds, path, patternType, flaky)
        }
        
        private fun parseKind(kindStr: String, pattern: String): DiffKind {
            return when (kindStr.trim().uppercase()) {
                DiffKind.MISSING_EXIST.name, "ME" -> DiffKind.MISSING_EXIST
                DiffKind.EXIST_MISSING.name, "EM" -> DiffKind.EXIST_MISSING
                DiffKind.FILE_DIR.name, "FD" -> DiffKind.FILE_DIR
                DiffKind.DIR_FILE.name, "DF" -> DiffKind.DIR_FILE
                DiffKind.TIMESTAMP.name, "T" -> DiffKind.TIMESTAMP
                DiffKind.HASH.name, "H" -> DiffKind.HASH
                else -> throw CheckerException("Can't parse '$kindStr' in '$pattern'")
            }
        }
    }

    fun match(kind: DiffKind, path: String): Boolean {
        if (kinds.isNotEmpty() && kind !in kinds) return false
        return when (patternType) {
            STRICT -> path == this.path
            STARTS_WITH -> path.startsWith(this.path)
            ENDS_WITH -> path.endsWith(this.path)
            CONTAINS -> path.contains(this.path)
        }
    }

    fun matchChild(kind: DiffKind, path: String): Boolean {
        if (kind != DiffKind.HASH) return false

        // Don't check rule kinds as any kind can affect parent hash
        return (patternType == STARTS_WITH || patternType == STRICT) && this.path.startsWith("$path/")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DiffExceptionRule
        if (pattern != other.pattern) return false
        return true
    }

    override fun hashCode(): Int {
        return pattern.hashCode()
    }

    override fun toString(): String {
        return "DiffExceptionRule(pattern='$pattern', kinds=$kinds, path='$path', patternType=$patternType)"
    }
}