package org.jetbrains.tbec

enum class DiffKind {
    MISSING_EXIST,
    EXIST_MISSING,
    FILE_DIR,
    DIR_FILE,
    TIMESTAMP,
    HASH
}