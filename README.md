# universalWasmLoader-jvm

A lightweight WebAssembly loader for JVM languages. Works with **Java 24+**, **Kotlin**, **Scala**, **Clojure**, and any other JVM language — without any native code or platform-specific setup.

Built on [Chicory](https://github.com/dylibso/chicory) — a pure-Java WebAssembly runtime (its only
other dependency is `kotlinx-coroutines-core`). Conforms to the cross-language
[`SPEC.md`](https://github.com/synapticforge1/universalWasmLoader-js/blob/main/SPEC.md) **v4.0.0**,
kept in the reference implementation, universalWasmLoader-js.

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.jrmarcum:universal-wasm-loader-jvm:0.4.0")
}
```

Or `pom.xml`:

```xml
<dependency>
    <groupId>io.github.jrmarcum</groupId>
    <artifactId>universal-wasm-loader-jvm</artifactId>
    <version>0.4.0</version>
</dependency>
```

## Usage

### Basic — dynamic call

```kotlin
import com.jrmarcum.universalwasmloader.*

val m = wasmLoad("math.wasm")
println(m.call("add", 3, 4))          // 7
println(m.call("multiply", 2.5, 4.0)) // 10.0
```

### Bind individual exports

```kotlin
val m   = wasmLoad("math.wasm")
val add = m.bind("add")
println(add(3, 4))   // 7
```

### Typed interface

Wrap the module in any JVM interface. Method names must match the camelCase WIT export names.

```kotlin
interface MathModule {
    fun add(a: Int, b: Int): Int
    fun square(x: Int): Int
}

val math = wasmLoad<MathModule>("math.wasm")
println(math.add(3, 4))    // 7
println(math.square(5))    // 25
```

### Version pinning

Append `@N` to assert the module's exported `version` global. Throws a descriptive error on mismatch — the same convention C shared libraries use with SONAME major versioning.

```kotlin
val m = wasmLoad("math.wasm@1")   // throws if version global != 1
```

### With host import callbacks

When your WASM module calls back into JVM code, pass host functions as a `Callbacks` builder.
Keys are the camelCase WIT import names (`env-mul` → `envMul`).

```kotlin
val cbs = Callbacks()
    .on("envMul") { a, b -> (a as Double) * (b as Double) }
    .on("envAdd") { a, b -> (a as Int) + (b as Int) }

val m = wasmLoad("imports.wasm", cbs)
println(m.call("scale", 3.0, 4.0))    // 12.0
```

Every WIT `import` needs its callback: a missing one fails the load with an
`IllegalArgumentException` naming all the missing ones. A callback must return its import's WIT
result kind (`Int` for `s32`, `Long` for `s64`, `Double` / `Float` for `f64` / `f32`, `Boolean`
for `bool`). Any other value, a `null`, or a `Future` / `Deferred` throws an
`IllegalStateException` that names the callback, the import, the type and the value. The exception
reaches the caller of the export. A void import ignores the value. An import with a `string`
result is not supported and fails the load.

### Async loading (URLs)

Use `wasmImport` inside a coroutine to load from HTTP/HTTPS or when already in a suspend context.

```kotlin
import java.time.Duration
import kotlinx.coroutines.runBlocking

val m = runBlocking { wasmImport("https://example.com/math.wasm") }
println(m.call("add", 3, 4))

// A different time limit per fetch (default 30 s; null = no limit):
val m2 = runBlocking { wasmImport("https://example.com/math.wasm", Callbacks(), LoadOptions(Duration.ofSeconds(5))) }
```

To cancel a load, cancel the coroutine that runs `wasmImport`. The request in flight is cancelled
with it.

### Singleton — load once, reuse forever

```kotlin
val getMath = createSingleton("math.wasm")
val first   = getMath()
val second  = getMath()
println(first.isSameInstance(second))   // true
```

### Instance pool — concurrent workloads

Pre-instantiates N independent WASM instances (each with its own linear memory).

```kotlin
import kotlinx.coroutines.runBlocking

val pool   = InstancePool("math.wasm", size = 4)
val result = runBlocking { pool.run { m -> m.call("add", 3, 4) } }
println(result)   // 7
```

| Method | Description |
| --- | --- |
| `acquire()` | Check out an instance. Suspends if all are in use. |
| `release(handle)` | Return an instance to the pool. |
| `run(fn)` | Acquire, call `fn`, release — even on exception. |

## How It Works

1. Finds the companion `.wit` by replacing the path's `.wasm` suffix with `.wit` (a URL's query
   and fragment are dropped; a path that does not end in `.wasm` has no `.wit`), and reads it
   **first** (file system or classpath, or over http(s))
2. Parses it strictly and checks that every WIT import has a host callback
3. Reads the `.wasm`, instantiates it, and checks that every WIT export exists
4. Applies the Canonical ABI (wasmtime) — bool and string types are handled automatically
5. Returns an ABI-translated `ModuleHandle` keyed by camelCase WIT export names

**Absent versus failed (SPEC 4.0.0 §2).** Only an **absent** `.wit` means raw exports: no such file
or classpath resource, or an http(s) answer of 404 or 410. Any other outcome fails the load before
the `.wasm` is read: another HTTP status, a network error, the time limit, an unreadable file, or a
`.wit` that does not parse. Parse failures include a type outside `s32 s64 f32 f64 bool string`
(`u32` is not read as `s32`), a parameter without `name: type`, a duplicate name, or text outside
the one `world`.

**Typed calls check their arguments (SPEC 4.0.0 §4).** A wrong count, or a value of another kind,
throws `IllegalArgumentException` naming the export and the parameter. Values are never coerced:
`add(2.7, 1)`, `add(1, 2147483648L)`, `multiply(2, 4)` and `greet(42)` are refused.

If no `.wit` exists, the module's raw exports are used, typed by the export's own wasm signature
(as JS raw `WebAssembly.Exports`). Numbers and booleans are converted to each parameter's type
(`2` → `2.0` for an f64). Results come back as `Int` (i32), `Long` (i64), `Float` (f32) or `Double`
(f64). Strings and host callbacks need the `.wit`; without it they throw a clear error.

## API Reference

### `wasmLoad(path, callbacks?)`

| Parameter | Type | Description |
| --- | --- | --- |
| `path` | `String` | Path to the `.wasm` file (file system or classpath). Append `@N` to pin to a major version. |
| `callbacks` | `Callbacks` | Host functions the WASM module calls into JVM code. |

Returns `ModuleHandle`.

### `wasmLoad<T>(path, callbacks?)`

Same as above but wraps the handle in a JDK dynamic proxy implementing interface `T`.

### `wasmImport(source, callbacks?)` / `wasmImport(source, callbacks, options)`

Suspend function. Same as `wasmLoad` but also accepts `http://`/`https://` URLs. Each file is
requested once, the `.wit` first. An HTTP error on either file throws an `IOException` naming the
status and URL; on the `.wit`, 404 and 410 mean "absent" (raw exports) instead.

`LoadOptions(timeout)` limits each fetch from the request to the last byte of the body (SPEC 4.0.0
§11). The default is 30 s; `null` means no limit; zero or negative is refused. Connecting is also
limited to 10 s, or to the timeout if that is shorter. A fetch that runs past the limit throws
`java.net.http.HttpTimeoutException` (an `IOException`) naming the URL and the limit. Local files
are read without a limit. `createSingleton` and `InstancePool` load local files only, so they take
no options.

### `ModuleHandle`

| Method | Description |
| --- | --- |
| `call(name, vararg args)` | Call a WASM export by camelCase name. Returns `Any?`. |
| `bind(name)` | Returns a standalone `(vararg Any?) -> Any?` callable. |
| `isSameInstance(other)` | Identity check (used to verify `createSingleton` caching). |

### Type mapping

| WIT type | JVM → WASM | WASM → JVM |
| --- | --- | --- |
| `s32` | `Int` (or `Short` / `Byte`) → i32 | i32 → `Int` |
| `s64` | `Long` (or `Int` / `Short` / `Byte`) → i64 | i64 → `Long` |
| `f32` | `Float` or `Double` → f32 | f32 → `Float` |
| `f64` | `Double` or `Float` → f64 | f64 → `Double` |
| `bool` | `Boolean` → `1`/`0` | `!= 0` → `Boolean` |
| `string` | UTF-8 encode → `cabi_realloc` → `(ptr, len)` | i32 return ptr → read `[ptr, len]` → UTF-8 decode → `cabi_post_<name>` (SPEC 3.0.0) |

See [SPEC.md](https://github.com/synapticforge1/universalWasmLoader-js/blob/main/SPEC.md) (in
universalWasmLoader-js) for the full cross-language conformance specification.

## Versioning

Semantic Versioning 2.0.0. Before 1.0.0 a breaking change may ship as a minor version, but it is
always listed below. Every release is checked against the previous one by a compatibility gate (the
public API snapshot in `api/`, the SPEC version, and the previous release's tests run against the new
library).

### ⚠️ Breaking Changes

| Version | What changed | How to migrate |
| --- | --- | --- |
| 0.4.0 | **SPEC 4.0.0** (behaviour; the API only gains `LoadOptions` and a `wasmImport` overload). (1) Only an absent `.wit` means raw exports. A `.wit` that answers anything but 200 / 404 / 410, times out or cannot be read now fails the load (it used to fall back to raw exports), and the `.wit` is read before the `.wasm`. (2) The `.wit` is parsed strictly: an unsupported type (`u32` was read as `s32`), a malformed parameter (`a: s32 b: s32` dropped `b`), a duplicate, or a WIT export the module lacks fails the load. (3) Every WIT import needs a callback, checked at load, and an import with a `string` result is refused. A callback's value must be its WIT result kind (`null` was `0`, `2.9` was `2`), and a `Future` is refused. (4) Typed calls check the count and kinds, with no coercion (`add(2.7, 1)` was `3`, `add(1)` was `1`, `greet(42)` was `"Hello, 42!"`). (5) `wasmImport`'s limit is 30 s per fetch, request to last byte (it was 30 s until the response started, so a stalled body waited forever). | Register a callback for every WIT import, returning the exact kind. Pass `Int` to `s32` and `Double` to `f64` (`2.0`, not `2`). Fix any `.wit` the loader now refuses. Serve the `.wit` with 200 or 404. Pass `LoadOptions(...)` for another limit. |
| 0.3.0 | Raw mode (no `.wit`) follows each export's wasm signature: an i32 result is an `Int` (was a `Long`), f32 / f64 results are `Float` / `Double` (were their raw bit patterns), arguments convert to the parameter types, and an argument that cannot convert (a `String`, `null`), a wrong argument count, or an unknown export throws `IllegalArgumentException` (were silently `0`, an `ArrayIndexOutOfBoundsException`, a `NullPointerException`). | Drop casts like `as Long` on i32 results (`(r as Number).toInt()` works before and after); remove any decoding of float bit patterns. |
| 0.3.0 | `wasmImport` over HTTP has fixed timeouts: 10 s to connect, 30 s until the server starts answering. A `.wasm` request that exceeds them throws `java.net.http.HttpTimeoutException` (it used to wait forever); a `.wit` request that exceeds them falls back to raw exports (SPEC §63). | Serve modules from a server that answers within 30 s; catch `IOException` (which `HttpTimeoutException` extends) where a load may fail. |
| 0.2.0 | A module's exported `_initialize` (SPEC §10.1) is now called automatically, once, right after instantiation (behaviour, not API). | Remove any manual call to `_initialize`; calling it again initializes the module twice. |

## Building from Source

```sh
./gradlew build      # compile + test
./gradlew test       # run tests only
```

Requires JDK 24+ (the build targets JVM 24 with a JDK 25 toolchain). The Gradle wrapper (`./gradlew`)
downloads the correct Gradle version automatically.

## Releasing (Maven Central)

The single source of truth for the version is the `version = "X.Y.Z"` line in `build.gradle.kts`.

**1. Bump the version** (patch/minor/major; patch is the default). The bump runs the compatibility
gate and refuses a bump below the floor it computes:

```sh
./gradlew bump                 # patch:  0.1.2 -> 0.1.3
./gradlew bump -Pkind=minor    # minor:  0.1.2 -> 0.2.0
./gradlew bump -Pkind=major    # major:  0.1.2 -> 1.0.0
# or directly:  kotlin scripts/compat.main.kts bump [patch|minor|major]
# floor only:   kotlin scripts/compat.main.kts report
```

A public API change re-baselines the snapshot first, in its own commit: `./gradlew updateLegacyAbi`.
Requires the `kotlin` command-line compiler on `PATH`.

**2. Commit, then tag + push** to trigger CI:

```sh
git commit -am "bump version to v0.1.3"
./gradlew release              # tags v<version> and force-pushes the tag
```

Pushing the `v*` tag runs `.github/workflows/publish.yml`. It has three jobs, each with only the
privilege it needs. `checks` (read-only, no secrets) runs the tests and the compatibility gate
against the previous tag. `publish` (read-only, on a fresh runner) checks the tag still points at
the tested commit, then publishes the signed artifacts to **Maven Central** (Sonatype Central
Portal) via the `com.vanniktech.maven.publish` plugin; it is the only job that sees the secrets.
`release` (write access, no build) creates the `release/<tag>` branch and the GitHub Release. The
JDK, the Kotlin compiler and the Gradle distribution are pinned by version and SHA-256.

> **`run:`-only workflow.** The publish workflow uses **no third-party `uses:` steps**: checkout is
> `git clone`, the JDK is installed via `run:`, and the committed Gradle wrapper drives the build.
> This started as a requirement, because an earlier Actions policy blocked third-party actions. The
> current org allows all actions, so the `run:`-only form is now kept by choice.

### Owner setup required before the first release

| GitHub secret | Gradle property (`ORG_GRADLE_PROJECT_…`) | What it is |
| --- | --- | --- |
| `MAVEN_CENTRAL_USERNAME` | `mavenCentralUsername` | Central Portal **user token** name |
| `MAVEN_CENTRAL_PASSWORD` | `mavenCentralPassword` | Central Portal **user token** secret |
| `GPG_PRIVATE_KEY` | `signingInMemoryKey` | ASCII-armored GPG **private** key block |
| `GPG_PASSPHRASE` | `signingInMemoryKeyPassword` | passphrase for that GPG key |

Prerequisites (one-time):

1. A **Sonatype Central Portal** account (central.sonatype.com); generate a **user token** for the
   two `MAVEN_CENTRAL_*` secrets.
2. **Verify the `io.github.jrmarcum` namespace** on the Central Portal (links the `jrmarcum` GitHub
   account). The groupId `io.github.jrmarcum` cannot publish until the namespace shows *Verified*.
3. A **GPG key** whose **public** key is published to a keyserver
   (`gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`); export the private key with
   `gpg --armor --export-secret-keys <KEYID>` into `GPG_PRIVATE_KEY`.

## License

See [LICENSE](./LICENSE).
