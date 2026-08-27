package com.aeibi.design.feature.preview

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalStaticFileServerTest {

    private lateinit var root: Path
    private lateinit var server: LocalStaticFileServer

    @Before
    fun setUp() {
        root = Files.createTempDirectory("static-server-test")
        Files.writeString(root.resolve("index.html"), "<h1>Vibe Design</h1>")
        Files.writeString(root.resolve("style.css"), "body { color: teal; }")
        Files.writeString(root.resolve("app.js"), "console.log('ready')")
        Files.createDirectories(root.resolve("assets"))
        Files.writeString(root.resolve("assets/page.html"), "<p>Nested page</p>")
        server = LocalStaticFileServer()
    }

    @After
    fun tearDown() {
        server.stop()
        root.toFile().deleteRecursively()
    }

    @Test
    fun servesStaticFilesAndDirectoryIndex() = runBlocking {
        val endpoint = server.start(root, 0)

        assertResponse(endpoint, "/", 200, "text/html", "<h1>Vibe Design</h1>")
        assertResponse(endpoint, "/style.css", 200, "text/css", "body { color: teal; }")
        assertResponse(endpoint, "/app.js", 200, "text/javascript", "console.log('ready')")
        assertResponse(endpoint, "/assets/page.html?preview=true", 200, "text/html", "<p>Nested page</p>")
        assertTrue(endpoint.toString().startsWith("http://127.0.0.1:"))
        assertTrue(endpoint.port > 0)
    }

    @Test
    fun optionalFallbackMatchesTryFiles() = runBlocking {
        val endpoint = server.start(root, 0, "index.html")

        assertResponse(endpoint, "/projects/42", 200, "text/html", "<h1>Vibe Design</h1>")
        assertResponse(endpoint, "/assets/missing.js", 200, "text/html", "<h1>Vibe Design</h1>")
    }

    @Test
    fun noFallbackReturnsNotFound() = runBlocking {
        assertResponse(server.start(root, 0), "/missing.html", 404)
    }

    @Test
    fun doesNotValidateRootOrFallbackAtStartup() = runBlocking {
        val endpoint = server.start(root.resolve("does-not-exist"), 0, "index.html")

        assertResponse(endpoint, "/", 404)
    }

    @Test
    fun failsWhenPortIsOccupied() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { occupiedSocket ->
            val error = runCatching { server.start(root, occupiedSocket.localPort) }.exceptionOrNull()

            assertNotNull(error)
        }
    }

    @Test
    fun stopIsRepeatableAndAllowsRestart() = runBlocking {
        server.start(root, 0)

        server.stop()
        server.stop()

        assertTrue(server.start(root, 0).port > 0)
    }

    private fun assertResponse(
        endpoint: URI,
        path: String,
        expectedStatus: Int,
        expectedContentType: String? = null,
        expectedBody: String? = null
    ) {
        val connection = openConnection(endpoint, path)
        try {
            assertEquals(expectedStatus, connection.responseCode)
            if (expectedContentType != null) {
                assertEquals(expectedContentType, connection.contentType.substringBefore(';'))
            }
            if (expectedBody != null) {
                assertEquals(expectedBody, connection.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(endpoint: URI, path: String): HttpURLConnection =
        (endpoint.resolve(path).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 2_000
            readTimeout = 2_000
            instanceFollowRedirects = false
        }
}
