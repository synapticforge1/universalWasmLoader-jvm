package com.jrmarcum.universalwasmloader

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.time.Duration

/**
 * Host.kt — every call this library makes to the operating system, and nothing else.
 *
 * Tier 1 of the workspace's testing tiers (owner, 2026-11-19; letter H40): the loader runs on Chicory, pure
 * Java, so what it computes is tested once per engine; only the OS edge can differ per platform, and it lives
 * here: reading a local file or a classpath resource, the HTTP transport, and the standard streams and
 * environment that WASI inherits. `scripts/check-host.main.kts` fails when any other source file calls those
 * APIs. [selfCheck] tries each operation on the machine it runs on (`./gradlew hostSelfCheck`, on demand only).
 */
internal object Host {

    /**
     * The bytes at [path]: the file system first, then the classpath. Null only when it is ABSENT (no such
     * file and no such resource). A path that exists but cannot be read (a directory, no permission) throws
     * the `IOException` as it came.
     */
    fun readLocal(path: String): ByteArray? {
        val file = java.io.File(path)
        if (file.exists()) return file.readBytes()
        val resource = path.trimStart('/', '\\').let { "/$it" }
        val stream = Host::class.java.getResourceAsStream(resource)
            ?: Host::class.java.getResourceAsStream(path)
            ?: return null
        return stream.use { it.readBytes() }
    }

    /** One HTTP client, connecting within [connect]. */
    fun http(connect: Duration): Http = Http(java.net.http.HttpClient.newBuilder().connectTimeout(connect).build())

    class Http internal constructor(private val client: java.net.http.HttpClient) {
        /**
         * One GET (never retried): the status and the whole body. A connect timeout becomes an
         * `HttpTimeoutException` naming the URL; any other I/O failure an `IOException` naming it. Coroutine
         * cancellation cancels the exchange and propagates unchanged.
         */
        suspend fun get(url: String): Pair<Int, ByteArray> {
            val request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).build()
            val response = try {
                client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray()).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.net.http.HttpConnectTimeoutException) {
                throw java.net.http.HttpTimeoutException("wasmImport: could not connect within $HTTP_CONNECT_TIMEOUT to $url")
            } catch (e: java.io.IOException) {
                throw java.io.IOException("wasmImport: fetching $url failed: ${e.message ?: e::class.simpleName}", e)
            }
            return response.statusCode() to response.body()
        }
    }

    /** The error for a fetch that ran past its time limit (an `HttpTimeoutException`, so an `IOException`). */
    fun timeout(url: String, limitMs: Long): java.io.IOException =
        java.net.http.HttpTimeoutException("wasmImport: $url did not complete within the time limit of $limitMs ms")

    /**
     * SPEC § 10.2: a minimal WASI Preview 1 (chicory-wasi) that INHERITS this process's standard streams,
     * environment and arguments, so an I/O-using library (`console.log` → `fd_write`) instantiates.
     */
    fun wasiHostFunctions(): Array<HostFunction> =
        WasiPreview1.builder()
            .withOptions(WasiOptions.builder().inheritSystem().build())
            .build()
            .toHostFunctions()

    /**
     * The on-demand self-check (H40): each of Host's operations tried on this machine. One line per check,
     * `ok <name>` or `FAIL <name>: <why>`. It writes only under a fresh temporary folder, which it removes,
     * and it stops the loopback server it starts.
     */
    fun selfCheck(): List<String> {
        val out = mutableListOf<String>()
        fun check(name: String, body: () -> Boolean) {
            out += try {
                if (body()) "ok $name" else "FAIL $name: wrong result"
            } catch (t: Throwable) {
                "FAIL $name: ${t::class.simpleName}: ${t.message}"
            }
        }

        val dir = java.nio.file.Files.createTempDirectory("uwl-host-selfcheck-").toFile()
        try {
            val file = java.io.File(dir, "probe.bin").apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
            check("readLocal reads a file") { readLocal(file.path)?.contentEquals(byteArrayOf(0, 1, 2, 3)) == true }
            check("readLocal: a missing path is absent (null)") { readLocal(java.io.File(dir, "none.bin").path) == null }
            check("readLocal: a directory is present but unreadable (IOException)") {
                try { readLocal(dir.path); false } catch (_: java.io.IOException) { true }
            }
            check("readLocal reads a classpath resource") {
                (readLocal("com/jrmarcum/universalwasmloader/Host.class")?.size ?: 0) > 0
            }
            check("WASI host functions build (inherited stdio, environment)") { wasiHostFunctions().isNotEmpty() }

            val server = com.sun.net.httpserver.HttpServer.create(
                java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0
            )
            server.createContext("/") { ex ->
                val ok = ex.requestURI.path == "/present"
                val body = if (ok) "ok".toByteArray() else ByteArray(0)
                ex.sendResponseHeaders(if (ok) 200 else 404, if (body.isEmpty()) -1 else body.size.toLong())
                if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
                ex.close()
            }
            server.start()
            try {
                val base = "http://127.0.0.1:${server.address.port}"
                val client = http(Duration.ofSeconds(5))
                check("HTTP GET 200 with the body") {
                    runBlocking { client.get("$base/present") }.let { it.first == 200 && String(it.second) == "ok" }
                }
                check("HTTP GET 404 reported as a status") { runBlocking { client.get("$base/absent") }.first == 404 }
            } finally {
                server.stop(0)
            }
            check("HTTP to a closed port fails with a named IOException") {
                val closed = java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
                try {
                    runBlocking { http(Duration.ofSeconds(5)).get("http://127.0.0.1:$closed/x") }
                    false
                } catch (e: java.io.IOException) {
                    e.message!!.startsWith("wasmImport: fetching ")
                }
            }
        } finally {
            dir.deleteRecursively()
        }
        check("the temporary folder was removed") { !dir.exists() }
        return out
    }
}
