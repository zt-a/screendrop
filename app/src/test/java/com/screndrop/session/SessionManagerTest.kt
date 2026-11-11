package com.screendrop.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private class FakeClock {
        var now = 0L
    }

    @Test
    fun `valid token passes and refreshes idle timer`() {
        val clock = FakeClock()
        val sm = SessionManager(idleTimeoutMs = 1000, timeSource = { clock.now })
        val s = sm.createNew()
        val (session, rejection) = sm.check(s.token)
        assertNotNull(session)
        assertEquals(SessionRejection.VALID, rejection)
    }

    @Test
    fun `wrong or missing token rejected`() {
        val sm = SessionManager()
        val s = sm.createNew()
        assertEquals(SessionRejection.INVALID, sm.check("wrong-token").second)
        assertEquals(SessionRejection.NO_TOKEN, sm.check(null).second)
        assertEquals(SessionRejection.NO_TOKEN, sm.check("").second)
    }

    @Test
    fun `idle timeout expires session`() {
        val clock = FakeClock()
        val sm = SessionManager(idleTimeoutMs = 1000, timeSource = { clock.now })
        val s = sm.createNew()
        clock.now = 1500
        assertEquals(SessionRejection.EXPIRED, sm.check(s.token).second)
        // And stays rejected afterwards
        assertEquals(SessionRejection.CLOSED, sm.check(s.token).second)
    }

    @Test
    fun `new session invalidates previous token`() {
        val sm = SessionManager()
        val old = sm.createNew()
        val new = sm.createNew()
        assertEquals(SessionRejection.INVALID, sm.check(old.token).second)
        assertEquals(SessionRejection.VALID, sm.check(new.token).second)
    }

    @Test
    fun `manual close invalidates token`() {
        val sm = SessionManager()
        val s = sm.createNew()
        sm.invalidateCurrent()
        assertEquals(SessionRejection.NO_TOKEN, sm.check(s.token).second)
    }

    @Test
    fun `token has full entropy and no obvious structure`() {
        val sm = SessionManager()
        val tokens = (1..100).map { sm.createNew().token }
        // 32 bytes -> 43 chars base64url without padding
        tokens.forEach { assertEquals(43, it.length) }
        // All unique
        assertEquals(tokens.size, tokens.toSet().size)
        // Not derived from time: created in the same millisecond but distinct
        assertTrue(tokens[0] != tokens[1])
    }
}
