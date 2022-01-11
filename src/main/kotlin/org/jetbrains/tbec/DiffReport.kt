package org.jetbrains.tbec

class DiffReport(val path: String, val kind: DiffKind, val message: String)

val DiffReport.fullMessage: String
    get() {
        val kindStr = "<$kind>"
        return "${path.replace(Checker.ROOT, kindStr)} - $message"
    }