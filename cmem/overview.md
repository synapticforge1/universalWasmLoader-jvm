# Overview — universalWasmLoader-jvm

What it is, decided, and why. State and open items live in [open-work.md](open-work.md) only.
Rewritten 2026-10-26 (memory conformance; the narrative it replaces: `git show 3dbc95b:cmem/overview.md`;
the old `CLAUDE.md` body it absorbed: `git show 3dbc95b:CLAUDE.md`; pre-purge id `0af04cb`).

## What it is

The **JVM (Java/Kotlin) port** of the Universal WASM Loader. It loads a `.wasm` module, auto-detects
the companion `.wit` file, applies the Canonical ABI, and returns a handle keyed by camelCase WIT
export names — the JVM equivalent of the reference loader, universalWasmLoader-js, whose
`SPEC.md` (v4.0.0 since 0.4.0, 2026-11-08; v3.0.0 through 0.3.0) is the one spec
text; this repo links it and keeps no copy (since 2026-10-26).

- **Language:** Kotlin, consumable from any JVM language. **Requires Java 24+** (bytecode target 24,
  JDK 25 toolchain; the README's old "Java 17+" was wrong, fixed 2026-10-26).
- **WASM runtime:** **Chicory 1.0.0** (`runtime`, `wasm`, `wasi`) — pure Java, no native code, so it
  runs on every JVM platform. Rejected: `wasmtime-java` (JNI, a native library per platform).
- **Other dependency:** `kotlinx-coroutines-core` 1.9.0 (`wasmImport`, `InstancePool`).
- **Toolchain:** Gradle 9.x (Kotlin DSL, wrapper committed), Kotlin 2.2.0, JUnit 5.

## Repository home and coordinates

- **Repo:** `https://github.com/synapticforge1/universalWasmLoader-jvm` — moved from the personal
  account `jrmarcum` to the org, verified 2026-10-20 (the old URL answers 301). POM `url` / `scm` name
  the org since 2026-10-20; already-published POMs keep `jrmarcum` (immutable). Org Actions policy:
  "Allow all actions and reusable workflows" (owner, 2026-10-20). Repository secrets came across.
- **Maven Central coordinates: `io.github.jrmarcum:universal-wasm-loader-jvm`; Kotlin package
  `com.jrmarcum.universalwasmloader`.** 🔒 **Owner, 2026-10-26: both STAY.** The Central Portal
  namespace `io.github.jrmarcum` is verified against the `jrmarcum` GitHub account, not against where
  the repo lives. A move to `io.github.synapticforge1` (groupId + package, as 0.2.0) was built the
  same day, 26/26 tests green, and reverted before any commit. Do not reopen it. The portal is
  **central.sonatype.com** (the retired OSSRH hosts `oss.sonatype.org` / `s01.oss.sonatype.org` are
  not it). The POM developer `id` is `jrmarcum` (it names the person).

## Design principles (binding; from the old `CLAUDE.md`, see `VISION.md` for the cross-project directives)

1. `wasmLoad(path)` is synchronous — no coroutine for local file loading.
2. Both `m.call("fn", args…)` and `wasmLoad<T>(path)` must work.
3. No options objects, no ABI flags, no explicit WIT paths — zero configuration. **One exception,
   from the SPEC itself:** SPEC 4.0.0 §1 / §11 (the owner's decision, 2026-11-07) requires an
   `options` argument for the fetch time limit, so `wasmImport` has a 3-argument overload taking
   `LoadOptions(timeout)` (2026-11-08). The 2-argument form and every other entry point stay
   zero-configuration, and the defaults are the SPEC's. 🔒 **Owner, 2026-11-08: "I confirm the
   exception."**
4. Version pinning `@N` follows the C SONAME convention, not Maven versioning.
5. `createSingleton` and `InstancePool` share the same minimal signature.
6. All project memory lives in `cmem/` (committed, portable); nothing in machine-local stores
   except the gitignored `cmem/local/`.

## Source layout

```
src/main/kotlin/com/jrmarcum/universalwasmloader/
├── UniversalWasmLoader.kt   # public API, ModuleHandle, string-return decode, load/instantiate helpers
├── Abi.kt                   # encode/decode, MemRef, StringAbi, buildEnvFunctions (env HostFunctions)
├── Callbacks.kt             # host import callbacks, .on(camelName) { … } with 0–3 args
└── WitParser.kt             # strict WIT parser (SPEC 4.0.0 §2, a port of -js's wit-parser.js; internal)
src/test/kotlin/.../LoaderTest.kt    # 62 tests: SPEC §8 incl. Refusals, two §10, wasmImport over HTTP, raw mode, fixture stamp
src/test/resources/tests/            # the stamped reference fixtures + fixtures.json (see Tests)
api/universal-wasm-loader-jvm.api    # the public API snapshot (compat gate Rule 1)
api/v0.1.2.api                       # the released surface of v0.1.2, generated from the tag
scripts/compat.main.kts              # compatibility gate + version bump (replaced bump.sh)
```

## Public API (the `api/` snapshot is the authoritative list)

- `wasmLoad(path, callbacks = Callbacks()): ModuleHandle` — **synchronous**, file or classpath only;
  `.wasm` → `.wit` auto-detect; `@N` suffix asserts an exported i32 global `version` == N (found by
  iterating the export section for `"version"` with `ExternalType.GLOBAL`).
- `wasmLoad<T>(path, callbacks): T` — reified inline; a `java.lang.reflect.Proxy` over interface `T`,
  each method dispatching to `call(method.name, …)`. ⚠️ Being inline-reified it is NOT in the ABI
  snapshot; the previous-tag tests guard it ([compat-gate.md](compat-gate.md)).
- `suspend wasmImport(source, callbacks)` and `suspend wasmImport(source, callbacks, options:
  LoadOptions)` — also `http(s)://` via `HttpClient.sendAsync(...).await()`. SPEC 4.0.0 (see
  § SPEC 4.0.0 below): the `.wit` is fetched FIRST; 404 / 410 = absent (raw), any other non-2xx →
  `IOException("wasmImport: HTTP <status> for <url>")`, before the `.wasm` is requested. The `.wasm`
  fetch throws the same on any non-2xx (since 2026-10-26; before, the error page reached Chicory as
  a `MalformedException`). One request per file, never retried.
  **Time limit:** `LoadOptions(timeout = 30 s)` per fetch, request to LAST BYTE, by
  `withTimeoutOrNull` around the whole exchange (`null` = no limit; zero / negative refused) →
  `HttpTimeoutException("wasmImport: <url> did not complete within the time limit of <n> ms")`.
  Connecting is limited to `HTTP_CONNECT_TIMEOUT` 10 s, or to the timeout if shorter (the 🔒
  owner's 10 s / 30 s of 2026-10-26, "30s sounds good"; SPEC §11.1 names this split as allowed).
  The 0.3.0 limit ended when the response STARTED, so a stalled body waited forever. That gap is
  closed now and tested. **Cancellation:** the coroutine's own; `await()` cancels the JDK future,
  which aborts the exchange. An already-cancelled coroutine fetches nothing (`ensureActive()`, and
  `withContext` checks too). Stall tests carry `@Timeout(10)` so a lost limit fails the suite.
- `createSingleton(path, callbacks): () -> ModuleHandle` — Kotlin `lazy {}`.
- `InstancePool(path, callbacks, size = 4)` — `size` independent instances; `Semaphore` +
  `ArrayBlockingQueue`; `acquire()`, `release(h)`, `run(fn)` (releases even on throw).
- `ModuleHandle.call(name, vararg args)`, `.bind(name): BoundFunction`, `.isSameInstance(other)`.
- `Callbacks().on("camelName") { … }` — keys are camelCase WIT import names.

**Names (critical):** exports are looked up by **camelCase** (`is-positive` → `isPositive`), which is
also the JVM API name — no snake_case step (unlike -rs). Chicory's `env` import field is underscore
(`env-mul` → `env_mul`); the callback key is camelCase (`envMul`). Never look an export up by its
kebab or snake form.

## ABI

`s32`↔Int, `s64`↔Long, `f32`↔Float, `f64`↔Double, `bool`↔(`!= 0`); strings UTF-8. The string ABI
is detected at instantiation from the export section (`detectStringAbi`):

- **CANONICAL** (`cabi_realloc` exported) — params: `cabi_realloc(0,0,1,len)` → write → `(ptr,len)`.
  Returns (SPEC 3.0.0, callee-allocated): the export returns an i32 `retArea`; read the LE
  `(ptr,len)` pair there, decode, then call `cabi_post_<camelName>(retArea)` if exported. Aligned
  2026-06-15 in `a434c6d` (it replaced the 2.x caller-allocated out-parameter).
- **WASIC** (`__malloc` exported) — params via `__malloc(len)`; returns read from the i32 globals
  `__str_ret_ptr` / `__str_ret_len` after a void call. Legacy fallback; no current fixture uses it.
- **NONE** — numerics and bool work; strings throw.

Import string params (WASM → host) are decoded via `MemRef.current`, set to `instance.memory()`
right after `Instance.build()` (memory exists only after instantiation).

### Raw mode (no `.wit`) — typed by the export's wasm signature (2026-10-26, shipped in 0.3.0)

SPEC §63 falls back to raw exports when there is no `.wit`. Until 2026-10-26 raw mode encoded by
the JVM argument type and returned every result as a raw `Long` — measured with a probe on the
fixtures: `multiply(2.5, 4.0)` returned `4621819117588971520` (the bits of 10.0), Int args to f64
params gave `0`, a `String` arg silently became `0`, a missing arg / unknown export threw
`ArrayIndexOutOfBounds` / `NullPointerException`. Only integer functions worked; every release up
to 0.2.1 has it. Now, as JS raw `WebAssembly.Exports`:

- `instance.exportType(name)` gives `params()` / `returns()`; resolved ONCE per export and cached
  per handle (`ModuleHandle.rawExports`), so a call is one map lookup + a conversion per argument.
- Args: any `Number` converts to the parameter type (`toInt` / `toLong` / `toFloat` / `toDouble`),
  `Boolean` → 1 / 0; anything else, a wrong count or an unknown export → `IllegalArgumentException`.
- Results: i32 → `Int`, i64 → `Long`, f32 → `Float`, f64 → `Double`; several results → a `List`.
- A module that imports host functions and has no `.wit` fails with `IllegalStateException` naming
  the missing `.wit` (Chicory's `UnlinkableException` as the cause).
- **Speed (owner: "we do not want to unwittingly slow a user's code execution down"), measured
  2026-10-26** with a throwaway probe (2,000,000 calls × 5 after warm-up, median ns/call): raw
  `add` 63–132 old → 44–54 new, raw `multiply` 64–110 old → 55–57 new (the old path repeated the
  export lookup on every call). The WIT path is untouched; its readings wandered 77–124 ns across
  runs of the SAME old code, so single-run differences there are noise.

## SPEC 4.0.0 (workspace C29; released in 0.4.0, 2026-11-08)

The owner's decisions of 2026-11-07 (loader-js 2.0.0, `-js/SPEC.md` § 2, § 4, § 8 Refusals, § 11).
It is a break: each rule refuses something 3.0.0 accepted. **Measured on 0.3.0 first** (by reading
the code and the 0.3.0 tests): a `.wit` 500 / timeout / IOException → raw, silently (tested as
intended behaviour). The regex parser read any unknown type as `s32`, dropped a parameter written
without a comma (`mapNotNull`), and gave an empty world for garbage. A missing callback → `null` →
`0`. A callback's value was coerced (`as? Number ?: 0`). Typed calls coerced too (`toInt()` on 2.7,
a missing argument → `0`, `toString()` on a string param). `cleanPath.replace(".wasm", ".wit")`
replaced EVERY occurrence and kept a URL's query. Now:

1. **§2 absent vs failed:** `.wit` read BEFORE the `.wasm`. Absent = no file and no classpath
   resource, or HTTP 404 / 410, or a path not ending in `.wasm` (only the suffix is replaced; a
   URL's query and fragment are dropped). Otherwise: unreadable → `IOException("cannot read .wit
   …")`; not UTF-8 or malformed → `IllegalArgumentException("invalid .wit '<path>': <fault>")`.
2. **§2 strict parsing:** `WitParser` is a port of `wit-parser.js` 2.0.0, same grammar and messages
   (`WitSyntaxException`, internal). `packageName` is `""` when absent (was `"unknown"`). Param names
   stay kebab (the JVM API never exposed them as camelCase). A WIT export the module lacks fails the
   load after instantiation (`IllegalStateException`, naming it).
3. **§4 host callbacks:** checked at load, before the `.wasm` is read. A `string`-result import →
   `UnsupportedOperationException`. All missing callbacks are named at once → `IllegalArgumentException`.
   The callback's value is checked by kind (`Abi.mismatch`) → `HostCallbackException` (internal,
   an `IllegalStateException`) naming the callback, import, result type and value. A `Future` /
   `CompletionStage` / coroutine `Job` (incl. `Deferred`) is refused for every import. A void
   import ignores the value. Chicory passes both this exception and a callback's own exception out
   of `apply` unchanged (measured: the tests catch the exact classes).
4. **§4 typed calls:** count and kind checked first → `IllegalArgumentException("'add' parameter b
   is s32, got Long 2147483648")`. Domains: `s32` = `Int` / `Short` / `Byte` (a `Long` is the s64
   kind, refused even in range); `s64` = those or `Long`; `f32` / `f64` = `Float` or `Double` (an
   `Int` refused); `bool` = `Boolean`; `string` = `String`. There are no kind-specific accessors, so
   the result-kind half is n/a. The WIT export is now one map lookup (the backlog item of 0.3.0).
5. **§11:** above, under Public API. `createSingleton` / `InstancePool` are file-only, so they take
   no options (n/a, as -c / -dotnet / -go).

**The check, § 8 Refusals:** every row is a test (http rows on the JDK `HttpServer`, counting
requests). JVM stand-ins for the JS values: `undefined` / `null` → `null`; `2 ** 31` and `17n` →
`Long`; an `async` callback → a `CompletableFuture`. Tests 40 → 62, all green. **Inverted:** 8
mutants in one run (`.wit` 500 → absent; limit ignored; missing-callback check off; argument check
off; return check off; unknown type → `s32`; export check off; unreadable `.wit` → absent). Exactly
14 tests went red, each in its mutant's group, and none outside them. Gate: floor **major**
(`SPEC.md v3.0.0 → v4.0.0` + 8 added members). v0.3.0's tests do not compile against the new code
(they call the removed internals `HTTP_RESPONSE_TIMEOUT` / `loadFromUrl(responseTimeout=)`), and a
major floor does not require them. **Released as 0.4.0** (pre-1.0 Rule 6 minor; 🔒 owner: "yes
release 0.4.0"), § Release history. Post-purge ids: surface `c1c6160`, behaviour `8b0db4f`, merge
`a8bade1`.

## SPEC §10 — producer model (2026-07-05, `1e1ecb8`)

- **§10.1** — a reactor's `_initialize` is called once after instantiation, before any other export.
  A behaviour change since `v0.1.2`, shipped in 0.2.0: the README Breaking Changes row `0.2.0`.
- **§10.2** — Chicory's first-party `WasiPreview1` (`WasiOptions.builder().inheritSystem()`) supplies
  `wasi_snapshot_preview1`; its host functions are merged with the `env` ones into one `ImportValues`,
  so a `modc` library that calls `console.log` loads on a plain JVM.

## Tests

`./gradlew check` runs the 62 tests (and the API snapshot check). Read counts from
`build/test-results/test/*.xml`. **62 / 62 on 2026-11-08** (40 / 40 on 2026-10-26; the SPEC 4.0.0
refusals added 22, § SPEC 4.0.0).

- **Fixtures = the stamped reference set (workspace H7 / C11, 2026-10-26)**, copied unmodified from
  universalWasmLoader-js `36aa661` (on its `origin/main`): the five `*_50` `.wasm`/`.wit` pairs,
  `init_check.wat`/`.wasm` and `fixtures.json`, generated by wasmtk `modc` at `4a5c3cf668d`
  (`v2.0.3-64-g4a5c3cf`). Copy the stamp, never regenerate it: `describe` is not reproducible across
  clones (loader-dart's finding). Before this, 7 of 11 files differed from the set (all five
  `*_50.wasm`, `booleans_50.wit`, `imports_50.wit`); the tests passed on both.
- **`fixtures match the stamp in fixtures json`** checks every listed sha256 and refuses any
  `.wasm`/`.wit`/`.wat` the stamp does not list; inverted 2026-10-26 (a one-byte change and a stray
  file → "bytes differ", "not in the stamp").
- Raw mode (no `.wit`) returns `Int` for an i32 since 0.3.0. One leftover `(… as Number).toInt()`
  from before that (the §10.1 test) still works either way.

## Build and release

- **Version source of truth:** the `version = "X.Y.Z"` line in `build.gradle.kts`; nothing else holds it.
- **Bump through the gate:** `./gradlew bump [-Pkind=minor|major]` → `kotlin scripts/compat.main.kts
  bump <kind>`; it refuses a bump below the floor ([compat-gate.md](compat-gate.md)).
- **Release (owner action):** commit the bump, `./gradlew release` (tags `v<version>`, force-pushes the
  tag) → `publish.yml`, **three jobs since 2026-11-08** (workspace C30; 🔒 owner: "yes use the
  C30 split workflow"):
  `checks` {contents: read}: tokenless clone of the tag, required at `github.sha` → pinned JDK
  25.0.5+8 + Kotlin 2.2.0 (version + sha256) → `./gradlew check` → compat `selftest` + `release`.
  `publish` {contents: read}: fresh runner, tokenless clone, refuses unless the tag is still
  `github.sha` → pinned JDK → `./gradlew -x test publishAllPublicationsToMavenCentralRepository`
  (vanniktech `com.vanniktech.maven.publish` **0.37.0**, Central Portal, GPG in-memory signing).
  The four secrets live in that one step's env.
  `release` {contents: write}: no checkout, no build; `git ls-remote` asserts the tag →
  `gh api` creates `release/v<version>` → `gh release create`.
  Before the split (through 0.3.0) ONE `contents: write` job ran everything, with the token in the
  clone's remote URL and unpinned downloads (the JDK was "latest 25"). `gradle-wrapper.properties`
  now pins the Gradle distribution (`distributionSha256Sum`, 9.5.1, checked against a download
  2026-11-08). `--dry-run` of the publish command: no `:test`; `signMavenPublication` and the upload
  are in the graph. **First run: the 0.4.0 release, run 42512825706, all three jobs green**
  (2026-11-08). The pinned Gradle checksum had already passed in `ci.yml` run 42512645800.
  `workflow_dispatch` exists, but GitHub reads it from the workflow file IN the chosen ref: no tag up
  to `v0.1.2` has it (added in `9f2c6d5`, one commit after the `v0.1.2` tag — measured 2026-10-26,
  "Workflow does not exist or does not have a workflow_dispatch trigger in this tag"). It works for
  `v0.2.0` and later tags only. ⚠️ Never dispatch on `main`: it publishes `main`'s version without
  the tag checks (and `0.1.2` already exists on Central). `ci.yml`
  runs `check` + the self-test on push to `main` and on PRs (added 2026-10-26).
- 🔒 **Automatic publishing after the tag (owner, 2026-10-26; first release: 0.2.1):**
  `publishToMavenCentral(automaticRelease = true)`, so a validated upload publishes itself — no manual
  Publish click at central.sonatype.com (0.1.2 and 0.2.0 needed one). `--dry-run` shows both
  `publishAllPublicationsToMavenCentralRepository` (what CI runs) and `publishAndReleaseToMavenCentral`
  include `enableAutomaticMavenCentralPublishing` and `signMavenPublication`.
- **Plugin 0.29.0 → 0.37.0 (2026-10-26):** 0.29.0's `publishToMavenCentral` called Gradle's deprecated
  `StartParameter.isConfigurationCacheRequested` (removed in Gradle 10) — the build's only deprecation
  warning (`--warning-mode all --stacktrace`); 0.37.0 prints none. From 0.34 `SonatypeHost` is gone
  (the Central Portal is the only target), so the call takes no host. The generated POM is unchanged
  (coordinates, developer, scm, dependencies).
- **Publishing path (workspace H20, recorded 2026-10-26):** a Central Portal **user token** + a GPG key,
  held in four repository secrets — NOT trusted publishing / OIDC, so no policy pins GitHub owner or
  repository ids and the org move needed no policy change. `0.1.2` was published 2026-06-24 by a
  `workflow_dispatch` on `main` (`9f2c6d5`, run 28141421397 — release steps skipped), not by its
  tag. **0.2.0 is the first release from the org** (tag `v0.2.0` = `6812998`, run 40406408422 green
  2026-10-26: the secrets work after the move; published on the Portal by the owner the same day).
  It is also the first release through the compatibility gate ([compat-gate.md](compat-gate.md)).
- **Workflows are `run:`-only by choice** (the old `jrmarcum` account allowed only its own actions;
  the org allows all since 2026-10-20). Changing the proven release path wants a `workflow_dispatch`
  test first.

### Credentials (✅ set up 2026-06-24; reference for re-keying)

| GitHub secret | → `ORG_GRADLE_PROJECT_…` | What it is |
| --- | --- | --- |
| `MAVEN_CENTRAL_USERNAME` | `mavenCentralUsername` | Central Portal user-token NAME |
| `MAVEN_CENTRAL_PASSWORD` | `mavenCentralPassword` | Central Portal user-token SECRET |
| `GPG_PRIVATE_KEY` | `signingInMemoryKey` | ASCII-armored private key, whole block |
| `GPG_PASSPHRASE` | `signingInMemoryKeyPassword` | its passphrase |

- The first release failed `Invalid token` until the two `MAVEN_CENTRAL_*` secrets held the **two bare
  values** of the token — not the `<server>` XML, not the base64 `user:pass` blob (the plugin encodes
  it, so the blob double-encodes) — as **Repository** secrets. Regenerating a token revokes the old one.
- **GPG key used for `0.1.2`:** RSA-4096, uid = the maintainer's name and address, fingerprint
  `E4D45440A37F73383B38842FFF9D45E2AFFFFB6D`, public key on `keyserver.ubuntu.com` and
  `keys.openpgp.org`. The private half lives only in the secrets.
- Namespace: `io.github.jrmarcum`, verified on the Central Portal via the `jrmarcum` GitHub account.

## Release history (Maven Central `io.github.jrmarcum:universal-wasm-loader-jvm`)

Commit ids in this table are POST-purge (H19, 2026-11-08); the pre-purge ids cited elsewhere are in
[history-map-2026-11-08.md](history-map-2026-11-08.md).

| Version | Tag → commit | What it carried | How it was published | On `repo1` (read 2026-11-08) |
| --- | --- | --- | --- | --- |
| 0.1.0, 0.1.1 | `322e1d9`, `da2777a` | early builds (2026-05-20) | never reached Central | absent |
| 0.1.2 | `19a3757` | first Central release; SPEC 3.0.0 string returns (`86e755b`) | 2026-06-24 by a `workflow_dispatch` on `main` (`e8c12ff`, run 28141421397), manual Publish click | live |
| 0.2.0 | `ef3846b` | SPEC §10 (`47c7813`; `_initialize` Breaking row); workspace conformance (`01ef84f`) | run 40406408422 (first from the org, first through the gate); Publish clicked by the owner | live |
| 0.2.1 | `5ce187e` | `wasmImport` reports a `.wasm` HTTP error (`01a07ae`); plugin 0.37.0, no deprecation; automatic publishing | run 40408929155, published itself | live |
| 0.3.0 | `cf53474` | raw mode typed by the wasm signature (`d2db3ec`); `wasmImport` timeouts 10 s / 30 s (`fe91d4b`); two Breaking rows | run 40411608731, published itself | live (`<latest>`) |
| 0.4.0 | `264e6f3` | **SPEC 4.0.0** (`c1c6160` surface + `8b0db4f` behaviour, merge `a8bade1`); the C30 three-job workflow (`ec7398c`, merge `5e6c48d`); first release after the H19 purge | run 42512825706 (🔒 owner: "yes release 0.4.0"), the first run of the split workflow: `checks` / `publish` / `release` all green; published itself; GitHub Release + `release/v0.4.0` = `264e6f3` | publishing (the owner, 2026-11-08); POM still 404 and `<latest>0.3.0</latest>` at the last read that day |

Every release from 0.2.0 on went through the compatibility gate locally (`bump`) and on the runner
(`release`). Through 0.3.0 the previous tag's tests were green against the new library; for 0.4.0
the floor was major, so they were not required (they do not compile against it).

## Development notes

- **Chicory upgrades:** re-check the `HostFunction` constructor, `Memory` read/write and
  `Instance.builder` against the release notes.
- `Memory.readByteArray(ptr, len)` (in `Abi.kt`) reads byte by byte; replace it if Chicory gains a
  bulk read.
