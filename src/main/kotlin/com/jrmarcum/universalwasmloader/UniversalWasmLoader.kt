package com.jrmarcum.universalwasmloader

import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasi.WasiOptions
import com.dylibso.chicory.wasi.WasiPreview1
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.ExternalType
import com.dylibso.chicory.wasm.types.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue

// ---------------------------------------------------------------------------
// BoundFunction — a callable wrapping a named export
// ---------------------------------------------------------------------------

class BoundFunction internal constructor(
    private val handle: ModuleHandle,
    private val name: String
) {
    operator fun invoke(vararg args: Any?): Any? = handle.call(name, *args)
}

// ---------------------------------------------------------------------------
// ModuleHandle — the handle returned by every load/import call
// ---------------------------------------------------------------------------

class ModuleHandle internal constructor(
    internal val instance: Instance,
    internal val witDoc: WitDocument?,
    internal val stringAbi: StringAbi
) {
    /**
     * Call a WASM export by its camelCase name with JVM-typed arguments.
     * In WIT mode, ABI encoding and decoding are applied automatically.
     * Without a WIT file, calls the raw WASM export with type-inferred encoding.
     */
    fun call(name: String, vararg args: Any?): Any? =
        if (witDoc == null) callRaw(name, args) else callWithAbi(name, args)

    // Raw mode: each export's wasm signature is resolved once, on its first call, and
    // cached — a call then costs one map lookup and a conversion per argument, so typing
    // raw mode does not slow a hot call loop down (measured: cmem/overview.md § ABI).
    private class RawExport(
        val fn: com.dylibso.chicory.runtime.ExportFunction,
        val params: Array<ValueType>,
        val results: Array<ValueType>,
    )

    private val rawExports = java.util.concurrent.ConcurrentHashMap<String, RawExport>()

    private fun rawExport(name: String): RawExport = rawExports.getOrPut(name) {
        val export = findExportByName(instance, name)
        require(export != null && export.exportType() == ExternalType.FUNCTION) {
            "no exported function '$name'; the module exports: ${exportedFunctionNames(instance)}"
        }
        val type = instance.exportType(name)
        RawExport(instance.export(name), type.params().toTypedArray(), type.returns().toTypedArray())
    }

    private fun callRaw(name: String, args: Array<out Any?>): Any? {
        val ex = rawExport(name)
        require(args.size == ex.params.size) { "'$name' takes ${ex.params.size} arguments, got ${args.size}" }
        val wasmArgs = LongArray(args.size) { i -> Abi.encodeRawArg(name, i, ex.params[i], args[i]) }
        val result = ex.fn.apply(*wasmArgs)
        return when (ex.results.size) {
            0 -> null
            1 -> Abi.decodeRawValue(ex.results[0], result[0])
            else -> List(ex.results.size) { Abi.decodeRawValue(ex.results[it], result[it]) } // multi-value
        }
    }

    // The WIT exports by camelCase name: one map lookup per call (3.0.0 searched the list).
    private val witExports: Map<String, WitFunction> = witDoc?.exports?.associateBy { it.camelName } ?: emptyMap()

    private fun callWithAbi(name: String, args: Array<out Any?>): Any? {
        val fn = witExports[name]
            ?: throw IllegalArgumentException(
                "Export '$name' not found in WIT. Available: ${witDoc!!.exports.map { it.camelName }}"
            )

        // SPEC 4.0.0 §4 — check the count and every argument's kind before calling WASM, never
        // coerce (3.0.0 made add(2.7, 1) = 3, add(1) = 1 and greet(42) = "Hello, 42!").
        if (args.size != fn.params.size) {
            throw IllegalArgumentException(
                "'$name' takes ${fn.params.size} argument${if (fn.params.size == 1) "" else "s"} " +
                    "(${fn.params.joinToString(", ") { "${it.name}: ${it.type}" }}), got ${args.size}"
            )
        }
        for ((i, param) in fn.params.withIndex()) {
            Abi.mismatch(param.type, args[i])?.let {
                throw IllegalArgumentException("'$name' parameter ${param.name} is ${param.type}, got $it")
            }
        }

        val wasmFn   = instance.export(fn.camelName)
        val wasmArgs = mutableListOf<Long>()
        for ((i, param) in fn.params.withIndex()) {
            wasmArgs.addAll(Abi.encodeArg(instance, param.type, args[i], stringAbi))
        }

        return if (fn.result == "string") {
            decodeStringReturn(fn.camelName, wasmFn, wasmArgs)
        } else {
            val result = wasmFn.apply(*wasmArgs.toLongArray())
            Abi.decodeResult(fn.result, result)
        }
    }

    private fun decodeStringReturn(
        name: String,
        wasmFn: com.dylibso.chicory.runtime.ExportFunction,
        wasmArgs: MutableList<Long>
    ): String = when (stringAbi) {
        StringAbi.CANONICAL -> {
            // SPEC 3.0.0 — callee-allocated return. The export returns a single i32
            // pointer (retArea) to a callee-allocated [ptr, len] pair. Read the pair,
            // decode the bytes, then call the paired cabi_post_<name>(retArea) export
            // (if present) so the module can free the allocation.
            val retArea = wasmFn.apply(*wasmArgs.toLongArray())[0].toInt()
            val raw = instance.memory().readBytes(retArea, 8)
            val bb  = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val str = String(instance.memory().readBytes(bb.getInt(0), bb.getInt(4)), Charsets.UTF_8)
            findExportByName(instance, "cabi_post_$name")?.let {
                instance.export("cabi_post_$name").apply(retArea.toLong())
            }
            str
        }
        StringAbi.WASIC -> {
            // Call normally; read ptr/len from the module's exported globals.
            wasmFn.apply(*wasmArgs.toLongArray())
            val ptrExport = findExportByName(instance, "__str_ret_ptr")
                ?: error("Missing __str_ret_ptr export in wasic module")
            val lenExport = findExportByName(instance, "__str_ret_len")
                ?: error("Missing __str_ret_len export in wasic module")
            val ptr = instance.global(ptrExport.index()).getValue().toInt()
            val len = instance.global(lenExport.index()).getValue().toInt()
            String(instance.memory().readBytes(ptr, len), Charsets.UTF_8)
        }
        StringAbi.NONE ->
            throw IllegalStateException("Module has no string return mechanism")
    }

    /**
     * Bind a named export as a standalone callable.
     * Approximates JavaScript destructuring: val add = m.bind("add"); add(3, 4)
     */
    fun bind(name: String): BoundFunction = BoundFunction(this, name)

    /** True if this handle is the same object instance as [other]. */
    fun isSameInstance(other: ModuleHandle): Boolean = this === other
}

// ---------------------------------------------------------------------------
// wasmLoad — synchronous file loader (primary entry point)
// ---------------------------------------------------------------------------

/**
 * Load a `.wasm` file synchronously. Auto-detects the companion `.wit` file
 * and applies the Canonical ABI. Supports `@N` version pinning.
 *
 * @param path  File path to the `.wasm` file. Append `@N` to pin to a major version.
 * @param callbacks  Host functions called by the WASM module. Flat camelCase keys.
 */
fun wasmLoad(path: String, callbacks: Callbacks = Callbacks()): ModuleHandle {
    val (cleanPath, version) = parseVersionSuffix(path)
    // SPEC 4.0.0 §2 — the .wit is read (and checked) before the .wasm. Only an absent .wit
    // means raw exports; an unreadable or malformed one fails the load.
    val witDoc = witPathFor(cleanPath)?.let { witPath ->
        readLocalWit(witPath)?.let { parseWitAt(witPath, it).also { doc -> checkImports(doc, callbacks, witPath) } }
    }
    val wasmBytes = loadBytes(cleanPath)
    return instantiateFromBytes(wasmBytes, witDoc, callbacks, cleanPath, version)
}

/**
 * Typed interface variant. Wraps the module handle in a JVM dynamic proxy
 * that implements interface [T], delegating each method call to [ModuleHandle.call].
 *
 * @param path  File path to the `.wasm` file. Append `@N` to pin to a major version.
 * @param callbacks  Host functions called by the WASM module.
 */
inline fun <reified T : Any> wasmLoad(path: String, callbacks: Callbacks = Callbacks()): T {
    val handle = wasmLoad(path, callbacks)
    @Suppress("UNCHECKED_CAST")
    return java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, method, args ->
        handle.call(method.name, *(args ?: emptyArray()))
    } as T
}

// ---------------------------------------------------------------------------
// wasmImport — async file + URL loader
// ---------------------------------------------------------------------------

/**
 * Options for [wasmImport] (SPEC 4.0.0 §11).
 *
 * @property timeout  The time limit for each http(s) fetch — the `.wit`, then the `.wasm` —
 *   from the request to the last byte of the body. Default 30 s; `null` means no limit. Zero
 *   or negative is refused. Connecting is also limited to 10 s (or [timeout], if shorter).
 *   Local files are read without a limit.
 *
 * Cancellation is the coroutine's own: cancelling the coroutine that runs [wasmImport]
 * cancels the load and the request in flight; an already-cancelled one fetches nothing.
 */
class LoadOptions(val timeout: Duration? = DEFAULT_TIMEOUT) {
    init {
        require(timeout == null || (!timeout.isNegative && !timeout.isZero)) {
            "LoadOptions.timeout must be positive (or null for no limit), got $timeout"
        }
    }

    companion object {
        /** The default time limit per fetch: 30 s. */
        @JvmField val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}

/**
 * Load a `.wasm` file or URL asynchronously. Use this when loading from HTTP/HTTPS
 * or when already inside a coroutine scope. For local files, prefer [wasmLoad].
 * Uses the default [LoadOptions] (a 30 s limit per fetch).
 *
 * @param source  File path or `http(s)://` URL. Append `@N` to pin to a major version.
 * @param callbacks  Host functions called by the WASM module.
 */
suspend fun wasmImport(source: String, callbacks: Callbacks = Callbacks()): ModuleHandle =
    wasmImport(source, callbacks, LoadOptions())

/**
 * [wasmImport] with explicit [options] (SPEC 4.0.0 §11: the time limit per fetch).
 * Cancel the calling coroutine to cancel the load.
 */
suspend fun wasmImport(source: String, callbacks: Callbacks, options: LoadOptions): ModuleHandle =
    withContext(Dispatchers.IO) {
        ensureActive() // an already-cancelled load reads and fetches nothing
        if (source.startsWith("http://") || source.startsWith("https://")) {
            loadFromUrl(source, callbacks, options)
        } else {
            wasmLoad(source, callbacks)
        }
    }

// The connect phase has its own limit (owner, 2026-10-26: 10 s); the whole fetch, connect
// included, is held to LoadOptions.timeout (SPEC 4.0.0 §11, which allows the split).
internal val HTTP_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)

internal suspend fun loadFromUrl(
    source: String,
    callbacks: Callbacks,
    options: LoadOptions = LoadOptions(),
): ModuleHandle {
    val (cleanUrl, version) = parseVersionSuffix(source)
    val limit = options.timeout
    val connect = if (limit != null && limit < HTTP_CONNECT_TIMEOUT) limit else HTTP_CONNECT_TIMEOUT
    val httpClient = HttpClient.newBuilder().connectTimeout(connect).build()

    // SPEC 4.0.0 §2 — the .wit first: 404 / 410 means absent (raw exports); every other
    // outcome — a status, a network error, the time limit — fails the load before the .wasm
    // is requested. 3.0.0 fell back to raw exports on all of them, silently.
    val witUrl = witUrlFor(cleanUrl)
    val witDoc = witUrl?.let { url ->
        fetchBytes(httpClient, url, limit, absentOk = true)?.let { bytes ->
            parseWitAt(url, decodeWit(url, bytes)).also { checkImports(it, callbacks, url) }
        }
    }

    // An HTTP error on the .wasm is reported as one (a 404 / 500 error page used to reach
    // Chicory as module bytes and surface as a MalformedException).
    val wasmBytes = fetchBytes(httpClient, cleanUrl, limit, absentOk = false)!!
    return instantiateFromBytes(wasmBytes, witDoc, callbacks, cleanUrl, version)
}

/**
 * One request (never retried), held to [limit] from the request to the last byte. Returns
 * null for a 404 / 410 when [absentOk]; any other non-2xx status throws `IOException` naming
 * the status and URL. Coroutine cancellation cancels the request and propagates unchanged.
 */
private suspend fun fetchBytes(client: HttpClient, url: String, limit: Duration?, absentOk: Boolean): ByteArray? {
    val request = HttpRequest.newBuilder().uri(URI.create(url)).build()
    val send: suspend () -> HttpResponse<ByteArray> = {
        try {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.net.http.HttpConnectTimeoutException) {
            throw HttpTimeoutException("wasmImport: could not connect within $HTTP_CONNECT_TIMEOUT to $url")
        } catch (e: java.io.IOException) {
            throw java.io.IOException("wasmImport: fetching $url failed: ${e.message ?: e::class.simpleName}", e)
        }
    }
    val resp = if (limit == null) send() else {
        withTimeoutOrNull(limit.toMillis()) { send() }
            ?: throw HttpTimeoutException("wasmImport: $url did not complete within the time limit of ${limit.toMillis()} ms")
    }
    val status = resp.statusCode()
    if (absentOk && (status == 404 || status == 410)) return null
    if (status !in 200..299) throw java.io.IOException("wasmImport: HTTP $status for $url")
    return resp.body()
}

// ---------------------------------------------------------------------------
// createSingleton — load once, reuse forever
// ---------------------------------------------------------------------------

/**
 * Returns an accessor that loads the WASM instance on the first call and caches it.
 * All callers share the same [ModuleHandle].
 */
fun createSingleton(path: String, callbacks: Callbacks = Callbacks()): () -> ModuleHandle {
    val lazy = lazy { wasmLoad(path, callbacks) }
    return { lazy.value }
}

// ---------------------------------------------------------------------------
// InstancePool — pre-instantiated pool for concurrent workloads
// ---------------------------------------------------------------------------

/**
 * Pre-instantiates [size] independent WASM instances. Each instance has its own
 * linear memory. Callers acquire an instance, use it, and release it back.
 *
 * @param path  File path to the `.wasm` file.
 * @param callbacks  Host functions called by the WASM module.
 * @param size  Number of independent instances to pre-instantiate (default: 4).
 */
class InstancePool(
    path: String,
    callbacks: Callbacks = Callbacks(),
    size: Int = 4
) {
    private val pool      = ArrayBlockingQueue<ModuleHandle>(size)
    private val semaphore = Semaphore(size)

    init {
        repeat(size) { pool.add(wasmLoad(path, callbacks)) }
    }

    /** Acquire an instance. Suspends if all instances are in use. */
    suspend fun acquire(): ModuleHandle {
        semaphore.acquire()
        return pool.poll()!!
    }

    /** Return an instance to the pool. */
    fun release(handle: ModuleHandle) {
        pool.offer(handle)
        semaphore.release()
    }

    /**
     * Acquire an instance, invoke [fn], then release — even if [fn] throws.
     * This is the preferred way to use the pool.
     */
    suspend fun <T> run(fn: suspend (ModuleHandle) -> T): T =
        semaphore.withPermit {
            val handle = pool.poll()!!
            try {
                fn(handle)
            } finally {
                pool.offer(handle)
            }
        }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private val VERSION_SUFFIX = Regex("""@(\d+)$""")

internal fun parseVersionSuffix(path: String): Pair<String, Int?> {
    val match = VERSION_SUFFIX.find(path) ?: return Pair(path, null)
    return Pair(path.substring(0, match.range.first), match.groupValues[1].toInt())
}

internal fun loadBytes(path: String): ByteArray {
    val file = File(path)
    if (file.exists()) return file.readBytes()
    val resource = path.trimStart('/', '\\').let { "/$it" }
    return (ModuleHandle::class.java.getResourceAsStream(resource)
        ?: ModuleHandle::class.java.getResourceAsStream(path)
        ?: throw java.io.FileNotFoundException("WASM file not found: $path"))
        .use { it.readBytes() }
}

/** The companion `.wit` of a path ending in `.wasm` (only that suffix is replaced), else null. */
internal fun witPathFor(path: String): String? =
    if (path.endsWith(".wasm")) path.dropLast(".wasm".length) + ".wit" else null

/** The companion `.wit` URL: the path's `.wasm` suffix replaced, query and fragment dropped. */
internal fun witUrlFor(url: String): String? {
    val uri = URI.create(url)
    val path = uri.rawPath ?: return null
    if (!path.endsWith(".wasm")) return null
    return "${uri.scheme}://${uri.rawAuthority}${path.dropLast(".wasm".length)}.wit"
}

/**
 * Read a local `.wit` (file system, then classpath). Null only when it is ABSENT (no such file
 * and no such resource); a `.wit` that exists but cannot be read throws `IOException` naming it.
 */
internal fun readLocalWit(path: String): String? {
    val file = File(path)
    val bytes = if (file.exists()) {
        try {
            file.readBytes()
        } catch (e: java.io.IOException) {
            throw java.io.IOException("cannot read .wit '$path': ${e.message}", e)
        }
    } else {
        val resource = path.trimStart('/', '\\').let { "/$it" }
        val stream = ModuleHandle::class.java.getResourceAsStream(resource)
            ?: ModuleHandle::class.java.getResourceAsStream(path)
            ?: return null
        try {
            stream.use { it.readBytes() }
        } catch (e: java.io.IOException) {
            throw java.io.IOException("cannot read .wit '$path': ${e.message}", e)
        }
    }
    return decodeWit(path, bytes)
}

/** Strict UTF-8: a `.wit` that is not valid UTF-8 does not parse. */
private fun decodeWit(where: String, bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (e: java.nio.charset.CharacterCodingException) {
    throw IllegalArgumentException("invalid .wit '$where': not valid UTF-8", e)
}

/** Parse strictly (SPEC 4.0.0 §2), naming the `.wit` in the error. */
private fun parseWitAt(where: String, src: String): WitDocument = try {
    WitParser.parse(src)
} catch (e: WitSyntaxException) {
    throw IllegalArgumentException("invalid .wit '$where': ${e.message}", e)
}

/**
 * SPEC 4.0.0 §4, at load and before the `.wasm` is read: an import with a `string` result is
 * not supported by this ABI profile, and every WIT import needs a host callback — all the
 * missing ones are named at once (3.0.0 loaded and the import returned 0 when WASM called it).
 */
private fun checkImports(doc: WitDocument, callbacks: Callbacks, where: String) {
    doc.imports.firstOrNull { it.result == "string" }?.let {
        throw UnsupportedOperationException(
            "'$where' imports ${it.name} with a string result, which this ABI profile does not support"
        )
    }
    val missing = doc.imports.filter { !callbacks.has(it.camelName) }
    if (missing.isNotEmpty()) {
        throw IllegalArgumentException(
            "missing host callback${if (missing.size == 1) "" else "s"} for the WIT imports of '$where': " +
                missing.joinToString(", ") { "${it.camelName} (${it.name})" }
        )
    }
}

// Scan the export section for an export with the given name (no throws).
private fun findExportByName(instance: Instance, name: String): com.dylibso.chicory.wasm.types.Export? {
    val section = instance.module().exportSection()
    for (i in 0 until section.exportCount()) {
        val export = section.getExport(i)
        if (export.name() == name) return export
    }
    return null
}

private fun exportedFunctionNames(instance: Instance): List<String> {
    val section = instance.module().exportSection()
    return (0 until section.exportCount()).map { section.getExport(it) }
        .filter { it.exportType() == ExternalType.FUNCTION }
        .map { it.name() }
}

// Detect which string ABI the module uses based on its exports.
private fun detectStringAbi(instance: Instance): StringAbi {
    val section = instance.module().exportSection()
    var hasCanonical = false
    var hasWasic = false
    for (i in 0 until section.exportCount()) {
        when (section.getExport(i).name()) {
            "cabi_realloc" -> hasCanonical = true
            "__malloc"     -> hasWasic = true
        }
    }
    return when {
        hasCanonical -> StringAbi.CANONICAL
        hasWasic     -> StringAbi.WASIC
        else         -> StringAbi.NONE
    }
}

private fun instantiateFromBytes(
    wasmBytes: ByteArray,
    witDoc: WitDocument?,
    callbacks: Callbacks,
    pathForError: String,
    version: Int?
): ModuleHandle {
    val wasmModule = Parser.parse(wasmBytes)
    val memRef     = MemRef()

    // SPEC §10.2 — provide a minimal WASI Preview 1 (chicory-wasi, stdio inherited)
    // so an I/O-using library — e.g. a `wasmtk modc` lib that calls console.log →
    // fd_write — instantiates here. Pure-compute modules import none of it.
    val wasi = WasiPreview1.builder()
        .withOptions(WasiOptions.builder().inheritSystem().build())
        .build()
    val importBuilder = ImportValues.builder()
    importBuilder.addFunction(*wasi.toHostFunctions())
    if (witDoc != null && witDoc.imports.isNotEmpty()) {
        for (fn in Abi.buildEnvFunctions(witDoc.imports, callbacks, memRef)) {
            importBuilder.addFunction(fn)
        }
    }
    val importValues = importBuilder.build()

    // Host callbacks are wired from the .wit's import list; without a .wit a module that
    // imports host functions cannot link. Say so, instead of only "unknown import".
    val instance = try {
        Instance.builder(wasmModule)
            .withImportValues(importValues)
            .build()
    } catch (e: com.dylibso.chicory.wasm.UnlinkableException) {
        if (witDoc != null) throw e
        throw IllegalStateException(
            "'$pathForError' has no companion .wit, so its host imports cannot be wired to callbacks: ${e.message}", e
        )
    }

    memRef.current = instance.memory()

    // SPEC 4.0.0 §2 — a WIT export the module does not export fails the load, naming it
    // (3.0.0 loaded, and the call failed later inside Chicory).
    if (witDoc != null) {
        val absent = witDoc.exports.filter { findExportByName(instance, it.camelName)?.exportType() != ExternalType.FUNCTION }
        if (absent.isNotEmpty()) {
            throw IllegalStateException(
                "'$pathForError': its .wit exports ${absent.joinToString(", ") { "${it.name} (${it.camelName})" }}, " +
                    "which the module does not export as a function; it exports: ${exportedFunctionNames(instance)}"
            )
        }
    }

    // SPEC §10.1 — call the reactor initializer once, before any other export.
    // No-op for modules that don't export `_initialize`.
    if (findExportByName(instance, "_initialize") != null) {
        instance.export("_initialize").apply()
    }

    val stringAbi = detectStringAbi(instance)

    if (version != null) {
        assertVersion(instance, pathForError, version)
    }

    return ModuleHandle(instance, witDoc, stringAbi)
}

private fun assertVersion(instance: Instance, path: String, expected: Int) {
    val export = findExportByName(instance, "version")
        ?: throw IllegalStateException(
            "Module '$path' does not export a 'version' global. Requested @$expected."
        )
    if (export.exportType() != ExternalType.GLOBAL) {
        throw IllegalStateException(
            "Module '$path': 'version' export is not a global. Requested @$expected."
        )
    }
    val actual = instance.global(export.index()).getValue().toInt()
    check(actual == expected) {
        "Version mismatch for '$path': requested @$expected but module exports version=$actual"
    }
}
