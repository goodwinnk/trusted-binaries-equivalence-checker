package org.jetbrains.tbec

import org.jetbrains.tbec.DiffKind.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class DiffExceptionRuleParseTest {
    @Test
    fun goodParseTest() {
        check("<>", emptySet(), "<>", false)
        check("<>/some/other", emptySet(), "<>/some/other", false)
        check("some/dir/**", emptySet(), "<>/some/dir/", true)
        check("<T>/dir/**", setOf(TIMESTAMP), "<>/dir/", true)

        check("<ME,EM,FD,DF,T,H>/dir/file",
            setOf(MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH),
            "<>/dir/file", false)

        check("<MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH>/dir/file",
            setOf(MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH),
            "<>/dir/file", false)
    }

    private fun check(pattern: String,
              kinds: Set<DiffKind>,
              path: String,
              isWildcardPath: Boolean) {
        val rule = DiffExceptionRule.parseExceptionRulePattern(pattern)
        assertEquals(pattern, rule.pattern, "pattern = $pattern in $rule")
        assertEquals(kinds, rule.kinds, "kinds = $pattern in $rule")
        assertEquals(path, rule.path, "path = $pattern in $rule")
        assertEquals(isWildcardPath, rule.isWildcardPath, "isWildcardPath = $pattern in $rule")
    }
}