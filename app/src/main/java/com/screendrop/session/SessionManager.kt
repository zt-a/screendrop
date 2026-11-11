package com.screendrop.session

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

enum class SessionRejection { VALID, NO_TOKEN, INVALID, CLOSED, EXPIRED }

/**
 * Session Manager: one active session at a time, cryptographic random token,
 * idle-timeout expiry, manual close invalidates the token immediately.
 *
 * The token is the only authorization credential for uploads. It is never
 * derived from time, IP or counters and is never logged.
 */
class SessionManager(
    val idleTimeoutMs: Long = 30 * 60 * 1000L,
    private val timeSource: () -> Long = System::currentTimeMillis,
    private val tokenEntropyBytes: Int = 32
) {
    private val random = SecureRandom()

    data class Session(
        val id: String,
        val token: String,
        val createdAt: Long,
        @Volatile var lastActivity: Long,
        @Volatile var closed: Boolean
    )

    @Volatile
    private var current: Session?

    val currentSession: Session? get() = current

    init {
        current = null
    }

    /** Creates a new session and invalidates the previous one. */
    fun createNew(): Session {
        invalidateCurrent()
        val bytes = ByteArray(tokenEntropyBytes).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val now = timeSource()
        val session = Session(
            id = UUID.randomUUID().toString(),
            token = token,
            createdAt = now,
            lastActivity = now,
            closed = false
        )
        current = session
        return session
    }

    /**
     * Validates a token in constant time. Refreshes the idle timer on success.
     */
    fun check(token: String?): Pair<Session?, SessionRejection> {
        val session = current ?: return null to SessionRejection.NO_TOKEN
        if (token.isNullOrEmpty()) return null to SessionRejection.NO_TOKEN
        if (!MessageDigest.isEqual(token.toByteArray(), session.token.toByteArray())) {
            return null to SessionRejection.INVALID
        }
        if (session.closed) return null to SessionRejection.CLOSED
        val now = timeSource()
        if (now - session.lastActivity > idleTimeoutMs) {
            session.closed = true
            return null to SessionRejection.EXPIRED
        }
        session.lastActivity = now
        return session to SessionRejection.VALID
    }

    /** Closes the active session; the old token stops working immediately. */
    fun invalidateCurrent() {
        current?.closed = true
        current = null
    }
}
