package com.hydrafit.app.core.llm

import java.io.File

/**
 * Moves [source] onto [target] without ever leaving [target] missing. The direct rename is tried
 * first; if it fails, the existing target is moved aside and restored when the second rename also
 * fails, so a failed replace cannot destroy a working file.
 */
internal fun replaceFileKeepingOld(
    source: File,
    target: File,
    rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
): Boolean {
    if (rename(source, target)) return true
    if (!target.exists()) return false
    val backup = File(target.parentFile, "${target.name}.bak")
    backup.delete()
    if (!rename(target, backup)) return false
    if (rename(source, target)) {
        backup.delete()
        return true
    }
    rename(backup, target)
    return false
}
