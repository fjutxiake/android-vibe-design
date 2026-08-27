package com.aeibi.design.feature.preview

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.routing
import java.net.URI
import java.nio.file.Path

class LocalStaticFileServer {
    private var server: EmbeddedServer<*, *>? = null

    suspend fun start(rootDirectory: Path, port: Int, fallbackFile: String? = null): URI {
        check(server == null) { "Static file server is already running" }

        val newServer = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            routing {
                staticFiles("/", rootDirectory.toFile()) {
                    default(fallbackFile)
                }
            }
        }
        newServer.start(wait = false)
        val actualPort = newServer.engine.resolvedConnectors().single().port
        server = newServer
        return URI("http://127.0.0.1:$actualPort/")
    }

    fun stop(gracePeriodMillis: Long = 0, timeoutMillis: Long = 2_000) {
        try {
            server?.stop(gracePeriodMillis, timeoutMillis)
        } finally {
            server = null
        }
    }
}
