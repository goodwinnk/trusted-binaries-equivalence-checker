package org.jetbrains.tbec

import org.jetbrains.tbec.Checker.Companion.ROOT
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class DiffExceptionRuleTest {
    @Test
    fun matchStrict() {
        check("<>/some/dir/file", "<>/some/dir/file", true)
        check("<>/some/dir/file", "<>a/some/dir/file", false)
        check("<>/some/dir/file", "<>/some/dir/file.a", false)

        check("<>", "<>", true)
    }

    @Test
    fun matchContains() {
        check("<>**/metadata.xml**", "<>/metadata.xml", true)
        check("<>**/metadata.xml**", "<>/some/path/metadata.xml", true)
        check("<>**/metadata.xml**", "<>/some/path/metadata.xml.other", true)
        check("<>**/metadata.xml**", "<>/metadata.xml/other", true)
        check("<>**/metadata.xml**", "<>/metadata.lmx", false)
    }

    @Test
    fun matchStart() {
        check("<>/some/dir/file**", "<>/some/dir/file", true)
        check("<>/some/dir/file**", "<>/some/dir/file.a", true)
        check("<>/some/dir/file**", "<>a/some/dir/file", false)
        check("<>/some/dir/file**", "<>/some/dir/file/other/more", true)
        check("<>/**", "<>/any/path", true)
    }

    @Test
    fun matchEnd() {
        check("<>**/metadata.xml", "<>/any/path/metadata.xml", true)
        check("<>**/metadata.xml", "<>/any/path/metadata.xml.", false)
        check("<>**/some/metadata.xml", "<>/metadata.xml", false)
    }

    private fun check(pattern: String, path: String, expect: Boolean) {
        val kind = DiffKind.HASH

        assertEquals(expect, DiffExceptionRule.parseExceptionRulePattern(pattern).match(kind, path),
            "pattern=$pattern path=$path, kind=$kind")

        if (pattern.startsWith(ROOT) && pattern != ROOT) {
            check(pattern.substringAfter(ROOT), path, expect)
        }
    }
}