package org.jetbrains.tbec

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class ReplacesKtTest {
    @Test
    fun parseMapOk() {
        check("", mapOf())
        check("a=b", mapOf("a" to "b"))
        check("a=b, c=d", mapOf("a" to "b", "c" to "d"))
        check("a\\,a=b\\,b,c\\=c=d\\=d", mapOf("a,a" to "b,b", "c=c" to "d=d"))
    }

    @Test
    fun parseMapEmpty() {
        check("a=", mapOf("a" to ""))
        check("=b", mapOf("" to "b"))
        check("=", mapOf("" to ""))
        check("a=,b=", mapOf("a" to "", "b" to ""))
        check(",,", mapOf())
    }

    @Test
    fun replaces() {
        check("a=b,a=c", mapOf("a" to "c"))
    }

    @Test
    fun parseMapFail() {
        checkFail("a=b=c")
        checkFail("a=b,c==d")
    }

    private fun check(str: String, expect: Map<String, String>) {
        assertEquals(expect, str.parseMap())
    }

    private fun checkFail(str: String) {
        assertThrows(ParseMapException::class.java) { str.parseMap() }
    }
}