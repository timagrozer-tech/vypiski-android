package ru.scanner291.core.testing

import java.io.File
import java.nio.file.Files

/** Временный каталог для тестов хранилища; удаляется после блока. */
inline fun <T> withTempDir(prefix: String = "scanner291-test", block: (File) -> T): T {
    val dir = Files.createTempDirectory(prefix).toFile()
    try {
        return block(dir)
    } finally {
        dir.deleteRecursively()
    }
}
