package org.jetbrains.tbec

import org.jetbrains.tbec.DiffExceptionRule.Companion.PatternType
import org.jetbrains.tbec.DiffExceptionRule.Companion.PatternType.*
import org.jetbrains.tbec.DiffKind.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DiffExceptionRuleParseTest {
    @Test fun root(): Unit = check("<>", "<>", emptySet(), STRICT)
    @Test fun simple(): Unit = check("<>/some/other", "<>/some/other", emptySet(), STRICT)
    @Test fun noRootSimple(): Unit = check("some/other", "<>/some/other", emptySet(), STRICT)
    @Test fun startNoRoot(): Unit = check("some/dir/**", "<>/some/dir/", emptySet(), STARTS_WITH)
    @Test fun patternStart(): Unit = check("<T>/dir/**", "<>/dir/", setOf(TIMESTAMP), STARTS_WITH)
    @Test fun rootEnd(): Unit = check("<>**/maven-metadata.xml", "/maven-metadata.xml", setOf(), ENDS_WITH)
    @Test fun rootContains(): Unit = check("<>**/maven-metadata.xml.**", "/maven-metadata.xml.", setOf(), CONTAINS)
    @Test fun noRootContains(): Unit = check("**/dir/some**", "/dir/some", emptySet(), CONTAINS)

    @Test fun allShort(): Unit = check(
        "<ME,EM,FD,F,DF,T,H>/dir/file",
        "<>/dir/file",
        setOf(MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH), STRICT,
        flaky = true
    )

    @Test fun allLong(): Unit = check(
        "<MISSING_EXIST, EXIST_MISSING, FLAKY, FILE_DIR, DIR_FILE, TIMESTAMP, HASH>/dir/file",
        "<>/dir/file",
        setOf(MISSING_EXIST, EXIST_MISSING, FILE_DIR, DIR_FILE, TIMESTAMP, HASH), STRICT,
        flaky = true
    )

    @Test fun replaces(): Unit = check(
        "<>/some/{version}/some-{version}/file", "<>/some/12/some-12/file", replaces = mapOf("version" to "12")
    )

    private fun check(
        pattern: String,
        path: String,
        kinds: Set<DiffKind> = setOf(),
        patternType: PatternType = STRICT,
        flaky: Boolean = false,
        replaces: Map<String, String>? = null
    ) {
        val rule = if (replaces == null) {
            DiffExceptionRule.parseExceptionRulePattern(pattern)
        } else {
            DiffExceptionRule.parseExceptionRulePattern(pattern, replaces)
        }

        assertEquals(pattern, rule.pattern, "pattern = $pattern in $rule")
        assertEquals(kinds, rule.kinds, "kinds = $kinds in $rule")
        assertEquals(path, rule.path, "path = $path in $rule")
        assertEquals(patternType, rule.patternType, "patternType = $patternType in $rule")
        assertEquals(flaky, rule.flaky, "flaky = $flaky in $rule")
    }
}