package org.jetbrains.tbec

open class Reporter(
    private val exceptionsPatterns: List<String> = listOf("<T>"),
    private val patternReplacesStr: String = ""
) {
    private val exceptionRules: List<DiffExceptionRule> = run {
        val replaces = try {
            patternReplacesStr.parseMap()
        } catch (ex: ParseMapException) {
            throw CheckerException("Can't parse replaces", ex)
        }
        exceptionsPatterns.map { DiffExceptionRule.parseExceptionRulePattern(it, replaces) }
    }

    private val _usedExceptions: MutableSet<String> = mutableSetOf()
    private val _errors: MutableList<String> = mutableListOf()
    private val _warnings: MutableList<String> = mutableListOf()

    val errors: List<String> get() = ArrayList(_errors)
    val warnings: List<String> get() = ArrayList(_warnings)
    val unusedExceptions: List<String> get() = exceptionRules.asSequence()
        .filter { !it.flaky }
        .filter { it.pattern !in _usedExceptions }
        .map { it.pattern }
        .toList()

    private fun findExceptionPattern(kind: DiffKind, path: String): String? {
        return exceptionRules.find { it.match(kind, path) }?.pattern ?:
            exceptionRules.find { it.matchChild(kind, path) }?.pattern
    }

    private fun processReport(report: DiffReport) {
        val pattern = findExceptionPattern(report.kind, report.path)
        if (pattern != null) {
            _usedExceptions.add(pattern)
        }
        if (pattern != null) {
            _warnings.add(report.fullMessage)
        } else {
            _errors.add(report.fullMessage)
        }
    }

    fun processReports(reports: List<DiffReport>) {
        _errors.clear()
        _warnings.clear()
        _usedExceptions.clear()

        val sorted = reports.sortedBy { it.path } // Implicitly sorts by path inclusion

        for (report in sorted) {
            processReport(report)
        }
    }
}

