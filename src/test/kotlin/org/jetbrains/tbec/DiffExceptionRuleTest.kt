package org.jetbrains.tbec

import org.jetbrains.tbec.DiffExceptionRule.Companion.PatternType
import org.jetbrains.tbec.DiffExceptionRule.Companion.PatternType.*
import org.jetbrains.tbec.DiffKind.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class DiffExceptionRuleParseTest {
    @Test fun root(): Unit = check("<>", emptySet(), "<>", STRICT)
    @Test fun simple(): Unit = check("<>/some/other", emptySet(), "<>/some/other", STRICT)
    @Test fun noRootSimple(): Unit = check("some/other", emptySet(), "<>/some/other", STRICT)
    @Test fun startNoRoot(): Unit = check("some/dir/**", emptySet(), "<>/some/dir/", STARTS_WITH)
    @Test fun patternStart(): Unit = check("<T>/dir/**", setOf(TIMESTAMP), "<>/dir/", STARTS_WITH)
    @Test fun rootEnd(): Unit = check("<>**/maven-metadata.xml", setOf(), "/maven-metadata.xml", ENDS_WITH)
    @Test fun rootContains(): Unit = check("<>**/maven-metadata.xml.**", setOf(), "/maven-metadata.xml.", CONTAINS)
    @Test fun noRootContains(): Unit = check("**/dir/some**", emptySet(), "/dir/some", CONTAINS)

    @Test fun allShort(): Unit = check("<ME,EM,FD,DF,T,H>/dir/file",
        setOf(MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH),
        "<>/dir/file", STRICT)

    @Test fun allLong(): Unit = check("<MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH>/dir/file",
        setOf(MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH),
        "<>/dir/file", STRICT)

    private fun check(pattern: String,
              kinds: Set<DiffKind>,
              path: String,
              patternType: PatternType) {
        val rule = DiffExceptionRule.parseExceptionRulePattern(pattern)
        assertEquals(pattern, rule.pattern, "pattern = $pattern in $rule")
        assertEquals(kinds, rule.kinds, "kinds = $pattern in $rule")
        assertEquals(path, rule.path, "path = $pattern in $rule")
        assertEquals(patternType, rule.patternType, "patternType = $pattern in $rule")
    }
}