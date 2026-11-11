package com.screendrop.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer

/**
 * Server Lifecycle Manager: starts/stops the embedded Ktor (CIO) engine.
 * Binding is 0.0.0.0 so the server is reachable from any local interface.
 */
class ScreenDropServer(
    private val core: ServerCore,
    private val port: Int = 8080
) {
    @Volatile
    private var engine: ApplicationEngine? = null

    val isRunning: Boolean get() = engine != null

    /** Returns false if the engine could not bind (e.g. port is taken). */
    fun start(): Boolean {
        if (engine != null) return true
        return try {
            val e = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                screendropModule(core)
            }
            e.start(wait = false)
            engine = e
            true
        } catch (t: Throwable) {
            engine = null
            false
        }
    }

    fun stop() {
        engine?.let { e ->
            runCatching { e.stop(500, 1500) }
        }
        engine = null
    }
}
