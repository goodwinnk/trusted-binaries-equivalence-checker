package org.jetbrains.tbec

class ParseMapException(message: String): Exception(message)

fun String.parseMap(): Map<String, String> {
    val trimmed = trim()
    if (trimmed.isEmpty()) return emptyMap()

    val parts = trimmed
        .replace("\\,", "\\<comma>")
        .split(",")
        .map { it.replace("\\<comma>", ",").trim() }

    return parts.filter { it.isNotEmpty() }.associate { part ->
        val keyValueStr = part
            .replace("\\=", "\\<equal>")
            .split("=")
            .map { it.replace("\\<equal>", "=") }
        if (keyValueStr.size != 2) throw ParseMapException("Can't parse key values from '$this', too many '=' in '$part'")

        keyValueStr[0] to keyValueStr[1]
    }
}