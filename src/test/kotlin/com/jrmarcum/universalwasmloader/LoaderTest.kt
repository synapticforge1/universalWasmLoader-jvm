package com.jrmarcum.universalwasmloader

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

    @Test
    fun `imports_50 scale`() {
        val cbs = Callbacks()
            .on("envMul") { a, b -> (a as Double) * (b as Double) }
        val m = wasmLoad(fixture("imports_50.wasm"), cbs)
        assertEquals(12.0, m.call("scale", 3.0, 4.0))
    }

    @Test
    fun `imports_50 combine`() {
        val cbs = Callbacks()
            .on("envAdd") { a, b -> (a as Int) + (b as Int) }
        val m = wasmLoad(fixture("imports_50.wasm"), cbs)
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
