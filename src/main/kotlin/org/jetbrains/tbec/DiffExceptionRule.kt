package org.jetbrains.tbec

class DiffExceptionRule private constructor (
    val pattern: String,
    val kinds: Set<DiffKind>, // Empty set means all kinds
    val path: String,
    val isWildcardPath: Boolean
) {
    companion object {
        fun parseExceptionRulePattern(pattern: String): DiffExceptionRule {
            val kinds: Set<DiffKind>
            val path: String
            val isWildcardPath: Boolean
            
            val pathPattern: String
            if (pattern.startsWith("<")) {
                pathPattern = Checker.ROOT + pattern.substringAfterLast(">")
                val kindsStr = pattern.substringAfter("<").substringBeforeLast(">").trim()
                kinds = if (kindsStr.isEmpty()) {
                    emptySet()
                } else {
                    kindsStr.split(",").map { parseKind(it, pattern) }.toSet()
                }
            } else {
                kinds = emptySet()
                pathPattern = "${Checker.ROOT}/$pattern"
            }

            if (pathPattern.endsWith("**")) {
                isWildcardPath = true
                path = pathPattern.substringBeforeLast("**")
            } else {
                isWildcardPath = false
                path = pathPattern
            }
            
            return DiffExceptionRule(pattern, kinds, path, isWildcardPath)
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
        return if (isWildcardPath) {
            path.startsWith(this.path)
        } else {
            path == this.path
        }
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
        return "DiffExceptionRule(pattern='$pattern', kinds=$kinds, path='$path', isWildcardPath=$isWildcardPath)"
    }
}