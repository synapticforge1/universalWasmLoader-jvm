package com.jrmarcum.universalwasmloader

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Reference test suite — mirrors SPEC.md §8.
 * Fixtures are loaded from src/test/resources/tests/.
 */
class LoaderTest {

    // Resolve a test fixture path from the classpath resource root.
    private fun fixture(name: String): String = "tests/$name"

    // ---------------------------------------------------------------------------
    // math_50 — numeric round-trip (s32, f64)
    // ---------------------------------------------------------------------------

    @Test
    fun `math_50 add`() {
        val m = wasmLoad(fixture("math_50.wasm"))
        assertEquals(7, m.call("add", 3, 4))
    }

    @Test
    fun `math_50 multiply`() {
        val m = wasmLoad(fixture("math_50.wasm"))
        assertEquals(10.0, m.call("multiply", 2.5, 4.0))
    }

    @Test
    fun `math_50 square`() {
        val m = wasmLoad(fixture("math_50.wasm"))
        assertEquals(25, m.call("square", 5))
    }

    // ---------------------------------------------------------------------------
    // booleans_50 — bool normalization (i32 ↔ boolean)
    // ---------------------------------------------------------------------------

    @Test
    fun `booleans_50 isPositive true`() {
        val m = wasmLoad(fixture("booleans_50.wasm"))
        assertEquals(true, m.call("isPositive", 1.0))
    }

    @Test
    fun `booleans_50 isPositive false`() {
        val m = wasmLoad(fixture("booleans_50.wasm"))
        assertEquals(false, m.call("isPositive", -1.0))
    }

    @Test
    fun `booleans_50 inRange true`() {
        val m = wasmLoad(fixture("booleans_50.wasm"))
        assertEquals(true, m.call("inRange", 5.0, 0.0, 10.0))
    }

    @Test
    fun `booleans_50 inRange false`() {
        val m = wasmLoad(fixture("booleans_50.wasm"))
        assertEquals(false, m.call("inRange", 11.0, 0.0, 10.0))
    }

    @Test
    fun `booleans_50 isEven true`() {
        val m = wasmLoad(fixture("booleans_50.wasm"))
        assertEquals(true, m.call("isEven", 4))
    }

    @Test
    fun `booleans_50 isEven false`() {
        val m = wasmLoad(fixture("booleans_50.wasm"))
        assertEquals(false, m.call("isEven", 3))
    }

    // ---------------------------------------------------------------------------
    // strings_50 — string params + returns via Canonical ABI
    // ---------------------------------------------------------------------------

    @Test
    fun `strings_50 greet`() {
        val m = wasmLoad(fixture("strings_50.wasm"))
        assertEquals("Hello, World!", m.call("greet", "World"))
    }

    @Test
    fun `strings_50 shout`() {
        val m = wasmLoad(fixture("strings_50.wasm"))
        assertEquals("hihi", m.call("shout", "hi"))
    }

    @Test
    fun `strings_50 strLen`() {
        val m = wasmLoad(fixture("strings_50.wasm"))
        assertEquals(5, m.call("strLen", "hello"))
    }

    // ---------------------------------------------------------------------------
    // imports_50 — host import callbacks
    // ---------------------------------------------------------------------------

    // SPEC 4.0.0 §4: every WIT import needs its callback, so both are always given.
    private fun importsCallbacks(envAdd: (Any?, Any?) -> Any? = { a, b -> (a as Int) + (b as Int) }) = Callbacks()
        .on("envMul") { a, b -> (a as Double) * (b as Double) }
        .on("envAdd", envAdd)

    @Test
    fun `imports_50 scale`() {
        val m = wasmLoad(fixture("imports_50.wasm"), importsCallbacks())
        assertEquals(12.0, m.call("scale", 3.0, 4.0))
    }

    @Test
    fun `imports_50 combine`() {
        val m = wasmLoad(fixture("imports_50.wasm"), importsCallbacks())
        assertEquals(17, m.call("combine", 10, 7))
    }

    // ---------------------------------------------------------------------------
    // bind — extract individual exports as standalone callables
    // ---------------------------------------------------------------------------

    @Test
    fun `bind wraps a single export`() {
        val m   = wasmLoad(fixture("math_50.wasm"))
        val add = m.bind("add")
        assertEquals(7, add(3, 4))
    }

    @Test
    fun `bind multiple exports from the same handle`() {
        val m        = wasmLoad(fixture("math_50.wasm"))
        val add      = m.bind("add")
        val multiply = m.bind("multiply")
        assertEquals(7, add(3, 4))
        assertEquals(10.0, multiply(2.5, 4.0))
    }

    // ---------------------------------------------------------------------------
    // Typed interface — Java dynamic proxy delegation
    // ---------------------------------------------------------------------------

    interface MathModule {
        fun add(a: Int, b: Int): Int
        fun multiply(a: Double, b: Double): Double
        fun square(x: Int): Int
    }

    @Test
    fun `typed interface delegates all exports`() {
        val math = wasmLoad<MathModule>(fixture("math_50.wasm"))
        assertEquals(7, math.add(3, 4))
        assertEquals(10.0, math.multiply(2.5, 4.0))
        assertEquals(25, math.square(5))
    }

    // ---------------------------------------------------------------------------
    // Instance lifecycle — singleton and pool
    // ---------------------------------------------------------------------------

    @Test
    fun `createSingleton returns same instance on repeated calls`() {
        val getMod = createSingleton(fixture("math_50.wasm"))
        val first  = getMod()
        val second = getMod()
        assertTrue(first.isSameInstance(second))
    }

    @Test
    fun `InstancePool run returns correct result`() = runTest {
        val pool   = InstancePool(fixture("math_50.wasm"), size = 2)
        val result = pool.run { m -> m.call("add", 3, 4) }
        assertEquals(7, result)
    }

    @Test
    fun `InstancePool run releases on exception`() = runTest {
        val pool = InstancePool(fixture("math_50.wasm"), size = 1)
        try {
            pool.run { _ -> throw RuntimeException("test error") }
        } catch (_: RuntimeException) {}
        // If the instance was not released the next call would deadlock.
        val result = pool.run { m -> m.call("add", 1, 2) }
        assertEquals(3, result)
    }

    @Test
    fun `InstancePool two concurrent runs both complete`() = runTest {
        val pool = InstancePool(fixture("math_50.wasm"), size = 2)
        var r1: Any? = null
        var r2: Any? = null
        coroutineScope {
            val job1 = launch { r1 = pool.run { m -> m.call("add", 3, 4) } }
            val job2 = launch { r2 = pool.run { m -> m.call("square", 5) } }
            job1.join(); job2.join()
        }
        assertEquals(7, r1)
        assertEquals(25, r2)
    }

    // ---------------------------------------------------------------------------
    // WIT parser unit tests
    // ---------------------------------------------------------------------------

    @Test
    fun `WitParser kebabToCamel basic`() {
        assertEquals("isPositive", WitParser.kebabToCamel("is-positive"))
        assertEquals("strLen", WitParser.kebabToCamel("str-len"))
        assertEquals("add", WitParser.kebabToCamel("add"))
    }

    @Test
    fun `WitParser parses math_50 wit`() {
        val src = """
            package local:math-50;
            world math-50 {
              export add: func(a: s32, b: s32) -> s32;
              export multiply: func(a: f64, b: f64) -> f64;
              export square: func(x: s32) -> s32;
            }
        """.trimIndent()
        val doc = WitParser.parse(src)
        assertEquals(3, doc.exports.size)
        assertEquals("add", doc.exports[0].camelName)
        assertEquals("multiply", doc.exports[1].camelName)
        assertEquals("square", doc.exports[2].camelName)
        assertEquals("s32", doc.exports[0].result)
        assertEquals("f64", doc.exports[1].result)
    }

    @Test
    fun `WitParser parses imports`() {
        val src = """
            package local:imports-50;
            world imports-50 {
              import env-mul: func(a: f64, b: f64) -> f64;
              import env-add: func(a: s32, b: s32) -> s32;
              export scale: func(v: f64, factor: f64) -> f64;
            }
        """.trimIndent()
        val doc = WitParser.parse(src)
        assertEquals(2, doc.imports.size)
        assertEquals("env-mul", doc.imports[0].name)
        assertEquals("envMul", doc.imports[0].camelName)
        assertEquals(1, doc.exports.size)
        assertEquals("scale", doc.exports[0].camelName)
    }

    // ---------------------------------------------------------------------------
    // SPEC §10 — producer model (loader capabilities)
    // ---------------------------------------------------------------------------

    @Test
    fun `SPEC 10_1 initialize called after instantiation`() {
        // init_check.wasm has no companion .wit (raw-exports path); its `_initialize`
        // sets a global to 99 that `getValue` reads — 99 proves the loader invoked it.
        val m = wasmLoad(fixture("init_check.wasm"))
        // Raw-mode (no .wit) returns a Long; normalize before comparing.
        assertEquals(99, (m.call("getValue") as Number).toInt())
    }

    @Test
    fun `SPEC 10_2 wasi io library loads`() {
        // wasi_io_50 is a modc library that calls console.log → imports fd_write. It
        // only instantiates because the loader provides the minimal WASI-P1 shim.
        val m = wasmLoad(fixture("wasi_io_50.wasm"))
        assertEquals(42, m.call("logAndDouble", 21))
    }

    // ---------------------------------------------------------------------------
    // wasmImport over HTTP (SPEC 4.0.0 §2, §11) — the .wit is fetched first: 404 /
    // 410 is absent (raw exports), any other failure fails the load before the .wasm
    // is requested; an HTTP error on the .wasm is reported as one. A local JDK
    // HttpServer serves fixed status codes and counts requests per path.
    // ---------------------------------------------------------------------------

    /** [stall]: paths that accept the request and never answer, until [close]. */
    private class FixtureServer(
        routes: Map<String, Pair<Int, ByteArray>>,
        stall: Set<String> = emptySet(),
    ) : AutoCloseable {
        val hits = java.util.concurrent.ConcurrentHashMap<String, Int>()
        private val release = java.util.concurrent.CountDownLatch(1)
        private val pool = java.util.concurrent.Executors.newCachedThreadPool()
        private val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0
        ).apply {
            executor = pool
            createContext("/") { ex ->
                val path = ex.requestURI.path
                hits.merge(path, 1, Int::plus)
                if (path in stall) { release.await(); ex.close(); return@createContext }
                val (status, body) = routes[path] ?: (404 to ByteArray(0))
                ex.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
                if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
                ex.close()
            }
            start()
        }
        val base = "http://127.0.0.1:${server.address.port}"
        override fun close() {
            release.countDown()
            server.stop(0)
            pool.shutdownNow()
        }
    }

    private fun bytes(name: String) = java.io.File("src/test/resources/tests/$name").readBytes()

    @Test
    fun `wasmImport reports an HTTP 404 on the wasm`() {
        FixtureServer(emptyMap()).use { srv ->
            val e = assertThrows(java.io.IOException::class.java) {
                kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm") }
            }
            assertEquals("wasmImport: HTTP 404 for ${srv.base}/math_50.wasm", e.message)
            assertEquals(1, srv.hits["/math_50.wasm"])
            assertEquals(1, srv.hits["/math_50.wit"], "the .wit (404: absent) is asked first, once")
        }
    }

    @Test
    fun `wasmImport reports an HTTP 500 on the wasm`() {
        FixtureServer(mapOf("/math_50.wasm" to (500 to "boom".toByteArray()))).use { srv ->
            val e = assertThrows(java.io.IOException::class.java) {
                kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm") }
            }
            assertEquals("wasmImport: HTTP 500 for ${srv.base}/math_50.wasm", e.message)
            assertEquals(1, srv.hits["/math_50.wasm"])
        }
    }

    @Test
    fun `wasmImport loads wasm and wit over HTTP`() {
        val routes = mapOf(
            "/math_50.wasm" to (200 to bytes("math_50.wasm")),
            "/math_50.wit" to (200 to bytes("math_50.wit")),
        )
        FixtureServer(routes).use { srv ->
            val m = kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm") }
            assertEquals(7, m.call("add", 3, 4)) // WIT path: s32 → Int
            assertEquals(1, srv.hits["/math_50.wasm"])
            assertEquals(1, srv.hits["/math_50.wit"])
        }
    }

    @Test
    fun `wasmImport defaults to a 30 s limit per fetch and a 10 s connect limit`() {
        assertEquals(java.time.Duration.ofSeconds(30), LoadOptions().timeout)
        assertEquals(java.time.Duration.ofSeconds(30), LoadOptions.DEFAULT_TIMEOUT)
        assertEquals(java.time.Duration.ofSeconds(10), HTTP_CONNECT_TIMEOUT)
        assertNull(LoadOptions(null).timeout) // no limit
    }

    @Test
    fun `LoadOptions refuses a zero or negative limit (SPEC 11_1)`() {
        for (d in listOf(java.time.Duration.ZERO, java.time.Duration.ofMillis(-1))) {
            val e = assertThrows(IllegalArgumentException::class.java) { LoadOptions(d) }
            assertTrue(e.message!!.contains("must be positive"), e.message)
        }
    }

    private fun shortLimit() = LoadOptions(java.time.Duration.ofMillis(300))

    @Test
    @org.junit.jupiter.api.Timeout(10) // a lost time limit must fail the suite, not hang it
    fun `wasmImport times out on a wasm that never answers`() {
        FixtureServer(emptyMap(), stall = setOf("/math_50.wasm")).use { srv ->
            val t0 = System.nanoTime()
            val e = assertThrows(java.net.http.HttpTimeoutException::class.java) {
                kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm", Callbacks(), shortLimit()) }
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertEquals("wasmImport: ${srv.base}/math_50.wasm did not complete within the time limit of 300 ms", e.message)
            assertTrue(ms < 5_000, "gave up after $ms ms")
            assertEquals(1, srv.hits["/math_50.wasm"], "one request, never retried")
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    fun `wasmImport times out on a wit that never answers, before the wasm is requested`() {
        FixtureServer(mapOf("/math_50.wasm" to (200 to bytes("math_50.wasm"))), stall = setOf("/math_50.wit")).use { srv ->
            val e = assertThrows(java.net.http.HttpTimeoutException::class.java) {
                kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm", Callbacks(), shortLimit()) }
            }
            assertEquals("wasmImport: ${srv.base}/math_50.wit did not complete within the time limit of 300 ms", e.message)
            assertEquals(1, srv.hits["/math_50.wit"])
            assertNull(srv.hits["/math_50.wasm"], "no .wasm request after the .wit failed")
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    fun `wasmImport limits a body that stalls after the headers`() {
        // The 3.0.0-era JDK request timeout ended when the response STARTED; the limit now runs
        // to the last byte. The handler sends headers for 10 bytes, then nothing.
        val release = java.util.concurrent.CountDownLatch(1)
        val server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0
        )
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.createContext("/") { ex ->
            if (ex.requestURI.path.endsWith(".wit")) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
            ex.sendResponseHeaders(200, 10)
            ex.responseBody.write(byteArrayOf(0, 'a'.code.toByte()))
            ex.responseBody.flush()
            release.await()
            ex.close()
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/math_50.wasm"
            val e = assertThrows(java.net.http.HttpTimeoutException::class.java) {
                kotlinx.coroutines.runBlocking { wasmImport(url, Callbacks(), shortLimit()) }
            }
            assertTrue(e.message!!.contains("$url did not complete"), e.message)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    @Test
    fun `wasmImport fails on a wit that answers 500, before the wasm is requested`() {
        val routes = mapOf(
            "/math_50.wasm" to (200 to bytes("math_50.wasm")),
            "/math_50.wit" to (500 to ByteArray(0)),
        )
        FixtureServer(routes).use { srv ->
            val e = assertThrows(java.io.IOException::class.java) {
                kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm") }
            }
            assertEquals("wasmImport: HTTP 500 for ${srv.base}/math_50.wit", e.message)
            assertEquals(1, srv.hits["/math_50.wit"])
            assertNull(srv.hits["/math_50.wasm"])
        }
    }

    @Test
    fun `wasmImport loads raw exports when the wit is 404 or 410`() {
        for (witStatus in listOf(404, 410)) {
            val routes = mapOf(
                "/math_50.wasm" to (200 to bytes("math_50.wasm")),
                "/math_50.wit" to (witStatus to ByteArray(0)),
            )
            FixtureServer(routes).use { srv ->
                val m = kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm") }
                assertEquals(7, m.call("add", 3, 4), "wit $witStatus")
                assertEquals(8.0, m.call("multiply", 2, 4), "raw mode converts (wit $witStatus)") // no WIT: raw
            }
        }
    }

    @Test
    fun `wasmImport drops the query and fragment from the wit URL`() {
        val routes = mapOf(
            "/math_50.wasm" to (200 to bytes("math_50.wasm")),
            "/math_50.wit" to (200 to bytes("math_50.wit")),
        )
        FixtureServer(routes).use { srv ->
            val m = kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/math_50.wasm?v=1#x") }
            assertThrows(IllegalArgumentException::class.java) { m.call("multiply", 2, 4) } // typed: WIT read
            assertEquals(1, srv.hits["/math_50.wit"])
            assertEquals(1, srv.hits["/math_50.wasm"], "the .wasm is not fetched again as its own .wit")
        }
    }

    @Test
    fun `wasmImport over http refuses missing callbacks before the wasm is requested`() {
        val routes = mapOf(
            "/imports_50.wasm" to (200 to bytes("imports_50.wasm")),
            "/imports_50.wit" to (200 to bytes("imports_50.wit")),
        )
        FixtureServer(routes).use { srv ->
            val e = assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { wasmImport("${srv.base}/imports_50.wasm") }
            }
            assertTrue(e.message!!.contains("envMul") && e.message!!.contains("envAdd"), e.message)
            assertNull(srv.hits["/imports_50.wasm"])
        }
    }

    @Test
    fun `wasmImport with an already-cancelled coroutine fetches nothing (SPEC 11_2)`() {
        FixtureServer(mapOf("/math_50.wasm" to (200 to bytes("math_50.wasm")))).use { srv ->
            assertThrows(kotlinx.coroutines.CancellationException::class.java) {
                kotlinx.coroutines.runBlocking {
                    coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                    wasmImport("${srv.base}/math_50.wasm")
                }
            }
            assertEquals(emptyMap<String, Int>(), srv.hits.toMap())
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    fun `cancelling the coroutine cancels a stalled fetch (SPEC 11_2)`() {
        FixtureServer(emptyMap(), stall = setOf("/math_50.wit")).use { srv ->
            val t0 = System.nanoTime()
            kotlinx.coroutines.runBlocking {
                val load = async(kotlinx.coroutines.Dispatchers.Default) {
                    wasmImport("${srv.base}/math_50.wasm", Callbacks(), LoadOptions(null)) // no time limit
                }
                while (srv.hits["/math_50.wit"] == null) kotlinx.coroutines.delay(10)
                load.cancel()
                assertThrows(kotlinx.coroutines.CancellationException::class.java) { kotlinx.coroutines.runBlocking { load.await() } }
            }
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000)
            assertNull(srv.hits["/math_50.wasm"])
        }
    }

    // ---------------------------------------------------------------------------
    // SPEC 4.0.0 §8 "Refusals" — each row of the table, plus the local-file halves
    // of §2 (absent versus failed, strict parsing, a WIT export the module lacks)
    // ---------------------------------------------------------------------------

    /** A temp dir holding fixture copies (name → fixture) and written files (name → text). */
    private fun dirWith(copies: Map<String, String> = emptyMap(), texts: Map<String, String> = emptyMap()): java.io.File {
        val dir = java.nio.file.Files.createTempDirectory("uwl-refusal").toFile()
        for ((name, from) in copies) java.io.File("src/test/resources/tests/$from").copyTo(java.io.File(dir, name))
        for ((name, text) in texts) java.io.File(dir, name).writeText(text)
        return dir
    }

    private inline fun <reified E : Throwable> refused(crossinline block: () -> Unit): String =
        assertThrows(E::class.java) { block() }.message!!

    @Test
    fun `refuses imports_50 with no host callbacks, naming both`() {
        val msg = refused<IllegalArgumentException> { wasmLoad(fixture("imports_50.wasm")) }
        assertTrue(msg.contains("envMul (env-mul)") && msg.contains("envAdd (env-add)"), msg)
    }

    @Test
    fun `refuses imports_50 with only envMul, naming envAdd`() {
        val msg = refused<IllegalArgumentException> {
            wasmLoad(fixture("imports_50.wasm"), Callbacks().on("envMul") { a, b -> (a as Double) * (b as Double) })
        }
        assertTrue(msg.contains("envAdd (env-add)") && !msg.contains("envMul"), msg)
    }

    @Test
    fun `refuses a host callback value that is not the import's result kind`() {
        // The JVM rows of SPEC §8: JS's undefined / null → null, 2.9, 2 ** 31 and 17n → Long,
        // true, "17". Each is refused when WASM calls env-add, and reaches combine's caller.
        val bad = listOf<Any?>(null, 2.9, 2147483648L, true, "17", 17L)
        for (value in bad) {
            val m = wasmLoad(fixture("imports_50.wasm"), importsCallbacks { _, _ -> value })
            val msg = refused<IllegalStateException> { m.call("combine", 10, 7) }
            assertTrue(msg.contains("'envAdd'") && msg.contains("env-add") && msg.contains("must return s32"), msg)
            assertTrue(msg.contains(Abi.describe(value)), "names the value: $msg")
        }
    }

    @Test
    fun `refuses a host callback that returns a Future`() {
        val cbs = importsCallbacks()
            .on("envMul") { _, _ -> java.util.concurrent.CompletableFuture.completedFuture(12.0) }
        val m = wasmLoad(fixture("imports_50.wasm"), cbs)
        val msg = refused<IllegalStateException> { m.call("scale", 3.0, 4.0) }
        assertTrue(msg.contains("'envMul'") && msg.contains("Future"), msg)
    }

    @Test
    fun `a host callback's own exception reaches the export's caller unchanged`() {
        class Boom : RuntimeException("boom")
        val m = wasmLoad(fixture("imports_50.wasm"), importsCallbacks { _, _ -> throw Boom() })
        assertThrows(Boom::class.java) { m.call("combine", 10, 7) }
    }

    @Test
    fun `a void import ignores its callback's value`() {
        val wit = "world w {\n  import env-note: func(x: s32);\n  export add: func(a: s32, b: s32) -> s32;\n}\n"
        // math_50.wasm does not import env-note: the WIT import is checked for a callback,
        // and the unused host function is ignored at link time.
        val dir = dirWith(mapOf("m.wasm" to "math_50.wasm"), mapOf("m.wit" to wit))
        val m = wasmLoad(java.io.File(dir, "m.wasm").path, Callbacks().on("envNote") { _ -> "anything" })
        assertEquals(7, m.call("add", 3, 4))
    }

    @Test
    fun `refuses an import with a string result before the wasm is read`() {
        val wit = bytes("imports_50.wit").toString(Charsets.UTF_8)
            .replace(Regex("""import env-mul: func\(([^)]*)\) -> f64"""), "import env-mul: func($1) -> string")
        assertTrue(wit.contains("-> string"), "the fixture .wit changed shape: $wit")
        val dir = dirWith(texts = mapOf("imports_50.wit" to wit)) // no .wasm at all
        val msg = refused<UnsupportedOperationException> {
            wasmLoad(java.io.File(dir, "imports_50.wasm").path, importsCallbacks())
        }
        assertTrue(msg.contains("env-mul") && msg.contains("string result"), msg)
    }

    @Test
    fun `refuses typed calls with a wrong argument kind or count`() {
        val m = wasmLoad(fixture("math_50.wasm"))
        for ((args, names) in listOf(
            arrayOf<Any?>("x", 1) to "parameter a is s32, got String \"x\"",
            arrayOf<Any?>(2.7, 1) to "parameter a is s32, got Double 2.7",
            arrayOf<Any?>(1, 2147483648L) to "parameter b is s32, got Long 2147483648",
            arrayOf<Any?>(1) to "takes 2 arguments (a: s32, b: s32), got 1",
        )) {
            val msg = refused<IllegalArgumentException> { m.call("add", *args) }
            assertEquals("'add' $names", msg)
        }
        assertEquals("'multiply' parameter a is f64, got Int 2",
            refused<IllegalArgumentException> { m.call("multiply", 2, 4.0) })
    }

    @Test
    fun `refuses greet with 42 and isEven with a String or a Boolean`() {
        assertTrue(refused<IllegalArgumentException> { wasmLoad(fixture("strings_50.wasm")).call("greet", 42) }
            .startsWith("'greet' parameter"))
        assertTrue(refused<IllegalArgumentException> { wasmLoad(fixture("booleans_50.wasm")).call("isEven", "4") }
            .startsWith("'isEven' parameter"))
        assertTrue(refused<IllegalArgumentException> { wasmLoad(fixture("booleans_50.wasm")).call("isEven", true) }
            .contains("is s32, got Boolean true"))
    }

    @Test
    fun `refuses math_50 beside a wit that does not parse or uses u32`() {
        val good = bytes("math_50.wit").toString(Charsets.UTF_8)
        for ((wit, fault) in listOf(
            good.replace("func(a: s32, b: s32)", "func(a: s32 b: s32)") to "malformed parameter \"a: s32 b: s32\"",
            good.replace("-> s32", "-> u32") to "unsupported WIT type \"u32\"",
            "" to "it is empty",
            "$good\nexport stray: func();" to "expected one \"world",
            good.replace("export square", "export add") to "duplicate export add",
            "package local:m;\nworld m { export add: func(a: s32, b: s32) -> s32 }" to "unterminated WIT statement",
        )) {
            val dir = dirWith(mapOf("math_50.wasm" to "math_50.wasm"), mapOf("math_50.wit" to wit))
            val path = java.io.File(dir, "math_50.wit").path
            val msg = refused<IllegalArgumentException> { wasmLoad(java.io.File(dir, "math_50.wasm").path) }
            assertTrue(msg.startsWith("invalid .wit '$path': ") && msg.contains(fault), msg)
        }
    }

    @Test
    fun `refuses a wit that is not UTF-8`() {
        val dir = dirWith(mapOf("math_50.wasm" to "math_50.wasm"))
        java.io.File(dir, "math_50.wit").writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        assertTrue(refused<IllegalArgumentException> { wasmLoad(java.io.File(dir, "math_50.wasm").path) }
            .endsWith("not valid UTF-8"))
    }

    @Test
    fun `refuses a wit export the module does not export`() {
        val wit = bytes("math_50.wit").toString(Charsets.UTF_8).replace("}", "  export cube: func(x: s32) -> s32;\n}")
        val dir = dirWith(mapOf("math_50.wasm" to "math_50.wasm"), mapOf("math_50.wit" to wit))
        val msg = refused<IllegalStateException> { wasmLoad(java.io.File(dir, "math_50.wasm").path) }
        assertTrue(msg.contains("exports cube (cube)") && msg.contains("does not export"), msg)
    }

    @Test
    fun `refuses a wit that exists but cannot be read`() {
        val dir = dirWith(mapOf("math_50.wasm" to "math_50.wasm"))
        java.io.File(dir, "math_50.wit").mkdir() // a directory: present, unreadable as a file
        val msg = refused<java.io.IOException> { wasmLoad(java.io.File(dir, "math_50.wasm").path) }
        assertTrue(msg.startsWith("cannot read .wit '"), msg)
    }

    @Test
    fun `a path that does not end in wasm has no companion wit`() {
        val dir = dirWith(mapOf("math_50.bin" to "math_50.wasm", "math_50.wit" to "math_50.wit"))
        val m = wasmLoad(java.io.File(dir, "math_50.bin").path)
        assertEquals(8.0, m.call("multiply", 2, 4)) // raw mode: the .wit beside it is not read
        assertEquals("a.wasm.d/b.wit", witPathFor("a.wasm.d/b.wasm")) // only the suffix is replaced
    }

    @Test
    fun `WitParser accepts comments, a trailing comma and no package`() {
        val doc = WitParser.parse("// c\nworld w { // c\n export f: func(a: s32,) -> bool; // c\n}\n")
        assertEquals("", doc.packageName)
        assertEquals(listOf(WitParam("a", "s32")), doc.exports.single().params)
        assertEquals("bool", doc.exports.single().result)
    }

    // ---------------------------------------------------------------------------
    // Raw mode (no .wit) — arguments and results follow the export's own wasm
    // signature, as JS raw WebAssembly.Exports do; what cannot convert throws.
    // ---------------------------------------------------------------------------

    /** A copy of a fixture alone in a temp dir, so no companion .wit is found. */
    private fun rawCopy(name: String): String {
        val dir = java.nio.file.Files.createTempDirectory("uwl-raw").toFile().apply { deleteOnExit() }
        val f = java.io.File(dir, name)
        java.io.File("src/test/resources/tests/$name").copyTo(f)
        f.deleteOnExit()
        return f.path
    }

    @Test
    fun `raw mode decodes results by the export's result type`() {
        val m = wasmLoad(rawCopy("math_50.wasm"))
        assertEquals(7, m.call("add", 3, 4))              // i32 → Int
        assertEquals(10.0, m.call("multiply", 2.5, 4.0))  // f64 → Double, not its bit pattern
        assertEquals(1, wasmLoad(rawCopy("booleans_50.wasm")).call("isPositive", 1.0)) // wasm bool is i32
    }

    @Test
    fun `raw mode converts arguments to the export's parameter types`() {
        val m = wasmLoad(rawCopy("math_50.wasm"))
        assertEquals(8.0, m.call("multiply", 2, 4))       // Int args to f64 params
        assertEquals(7, m.call("add", 3L, 4L))            // Long args to i32 params
    }

    @Test
    fun `raw mode refuses an argument that cannot convert`() {
        val m = wasmLoad(rawCopy("math_50.wasm"))
        val e = assertThrows(IllegalArgumentException::class.java) { m.call("add", "3", 4) }
        assertTrue(e.message!!.contains("argument 1 of 'add'"), e.message)
        assertTrue(e.message!!.contains(".wit"), e.message)
    }

    @Test
    fun `raw mode refuses a wrong argument count`() {
        val m = wasmLoad(rawCopy("math_50.wasm"))
        val e = assertThrows(IllegalArgumentException::class.java) { m.call("add", 3) }
        assertEquals("'add' takes 2 arguments, got 1", e.message)
    }

    @Test
    fun `raw mode refuses an unknown export`() {
        val m = wasmLoad(rawCopy("math_50.wasm"))
        val e = assertThrows(IllegalArgumentException::class.java) { m.call("nope") }
        assertTrue(e.message!!.startsWith("no exported function 'nope'"), e.message)
    }

    @Test
    fun `raw mode names the missing wit when the module imports host functions`() {
        val e = assertThrows(IllegalStateException::class.java) {
            wasmLoad(rawCopy("imports_50.wasm"), Callbacks().on("envMul") { a, b -> (a as Double) * (b as Double) })
        }
        assertTrue(e.message!!.contains("no companion .wit"), e.message)
        assertTrue(e.message!!.contains("env_mul"), e.message)
    }

    // ---------------------------------------------------------------------------
    // SPEC §7 — the fixtures are the stamped reference set, unmodified
    // ---------------------------------------------------------------------------

    @Test
    fun `fixtures match the stamp in fixtures json`() {
        // The committed bytes (Gradle runs tests from the project dir), not the build copy.
        // Same rule as -js's check-fixtures.ts: every stamped file present with its sha256,
        // and no .wasm / .wit / .wat that the stamp does not list. Re-copy from
        // universalWasmLoader-js on failure; never edit a fixture by hand.
        val dir = java.io.File("src/test/resources/tests")
        val stamp = java.io.File(dir, "fixtures.json").readText()
        val filesBlock = Regex("\"files\"\\s*:\\s*\\{([^}]*)\\}").find(stamp)?.groupValues?.get(1)
            ?: fail("fixtures.json has no \"files\" object")
        val expected = Regex("\"([^\"]+)\"\\s*:\\s*\"([0-9a-f]{64})\"").findAll(filesBlock)
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue(expected.isNotEmpty(), "fixtures.json lists no files")

        val problems = mutableListOf<String>()
        for ((name, hash) in expected) {
            val f = java.io.File(dir, name)
            if (!f.exists()) { problems += "$name: missing"; continue }
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes())
                .joinToString("") { "%02x".format(it) }
            if (actual != hash) problems += "$name: bytes differ from the stamp"
        }
        dir.listFiles()!!.map { it.name }
            .filter { Regex("\\.(wasm|wit|wat)$").containsMatchIn(it) && it !in expected }
            .forEach { problems += "$it: not in the stamp" }
        assertEquals(emptyList<String>(), problems)
    }
}
