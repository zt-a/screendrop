package com.screendrop.storage

/**
 * Cleans a client-supplied filename into a safe display name.
 * The result is never used as a path: storage always uses opaque internal IDs.
 */
object FilenameSanitizer {

    /** Characters that never appear in a display name. */
    private val forbidden = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    fun sanitize(raw: String?, fallback: String = "file"): String {
        if (raw.isNullOrBlank()) return fallback
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString(base.length) {
            for (c in base) {
                if (c.isISOControl() || c in forbidden) continue
                append(c)
            }
        }.trim().trimEnd('.', ' ').trim()
        val bounded = cleaned.take(MAX_NAME_LENGTH)
        return bounded.ifBlank { fallback }
    }

    fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    const val MAX_NAME_LENGTH = 255
}
