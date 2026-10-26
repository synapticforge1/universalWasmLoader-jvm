# Overview — universalWasmLoader-jvm

What it is, decided, and why. State and open items live in [open-work.md](open-work.md) only.
Rewritten 2026-10-26 (memory conformance; the narrative it replaces: `git show 0af04cb:cmem/overview.md`;
the old `CLAUDE.md` body it absorbed: `git show 0af04cb:CLAUDE.md`).

## What it is

The **JVM (Java/Kotlin) port** of the Universal WASM Loader. It loads a `.wasm` module, auto-detects
the companion `.wit` file, applies the Canonical ABI, and returns a handle keyed by camelCase WIT
export names — the JVM equivalent of the reference loader, universalWasmLoader-js, whose
`SPEC.md` (v3.0.0) is the one spec text; this repo links it and keeps no copy (since 2026-10-26).

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
3. No options objects, no ABI flags, no explicit WIT paths — zero configuration.
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
└── WitParser.kt             # regex WIT parser, no dependencies (internal)
src/test/kotlin/.../LoaderTest.kt    # 27 tests: SPEC §8, two §10, one fixture-stamp check
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
- `suspend wasmImport(source, callbacks): ModuleHandle` — also `http(s)://` (fetches `.wasm`, then a
  best-effort `.wit` via `HttpClient`). ⚠️ Masks HTTP errors — [open-work.md](open-work.md).
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

## SPEC §10 — producer model (2026-07-05, `1e1ecb8`)

- **§10.1** — a reactor's `_initialize` is called once after instantiation, before any other export.
  A behaviour change since `v0.1.2`: README Breaking Changes row `_next release_`.
- **§10.2** — Chicory's first-party `WasiPreview1` (`WasiOptions.builder().inheritSystem()`) supplies
  `wasi_snapshot_preview1`; its host functions are merged with the `env` ones into one `ImportValues`,
  so a `modc` library that calls `console.log` loads on a plain JVM.

## Tests

`./gradlew check` runs the 27 tests (and the API snapshot check). Read counts from
`build/test-results/test/*.xml`. **27 / 27 on 2026-10-26.**

- **Fixtures = the stamped reference set (workspace H7 / C11, 2026-10-26)**, copied unmodified from
  universalWasmLoader-js `36aa661` (on its `origin/main`): the five `*_50` `.wasm`/`.wit` pairs,
  `init_check.wat`/`.wasm` and `fixtures.json`, generated by wasmtk `modc` at `4a5c3cf668d`
  (`v2.0.3-64-g4a5c3cf`). Copy the stamp, never regenerate it: `describe` is not reproducible across
  clones (loader-dart's finding). Before this, 7 of 11 files differed from the set (all five
  `*_50.wasm`, `booleans_50.wit`, `imports_50.wit`); the tests passed on both.
- **`fixtures match the stamp in fixtures json`** checks every listed sha256 and refuses any
  `.wasm`/`.wit`/`.wat` the stamp does not list; inverted 2026-10-26 (a one-byte change and a stray
  file → "bytes differ", "not in the stamp").
- Raw mode (no `.wit`) returns `Long`; tests normalize with `(… as Number).toInt()`.

## Build and release

- **Version source of truth:** the `version = "X.Y.Z"` line in `build.gradle.kts`; nothing else holds it.
- **Bump through the gate:** `./gradlew bump [-Pkind=minor|major]` → `kotlin scripts/compat.main.kts
  bump <kind>`; it refuses a bump below the floor ([compat-gate.md](compat-gate.md)).
- **Release (owner action):** commit the bump, `./gradlew release` (tags `v<version>`, force-pushes the
  tag) → `publish.yml`: clone the tag + fetch tags → JDK 24+ → `kotlin` → `./gradlew check` →
  compat `selftest` + `release` → `publishAllPublicationsToMavenCentralRepository` (vanniktech,
  Central Portal, GPG in-memory signing) → `release/v<version>` branch → `gh release create`.
  `workflow_dispatch` exists, but GitHub reads it from the workflow file IN the chosen ref: no tag up
  to `v0.1.2` has it (added in `9f2c6d5`, one commit after the `v0.1.2` tag — measured 2026-10-26,
  "Workflow does not exist or does not have a workflow_dispatch trigger in this tag"). It works for
  `v0.2.0` and later tags only. ⚠️ Never dispatch on `main`: it publishes `main`'s version without
  the tag checks (and `0.1.2` already exists on Central). `ci.yml`
  runs `check` + the self-test on push to `main` and on PRs (added 2026-10-26).
- ⚠️ **Central Portal does not auto-publish**: the upload lands `VALIDATED` and needs the **Publish**
  button at central.sonatype.com → Deployments (this held `0.1.2`). `automaticRelease = true` in
  `publishToMavenCentral(...)` would remove the click (owner call, not set).
- **Publishing path (workspace H20, recorded 2026-10-26):** a Central Portal **user token** + a GPG key,
  held in four repository secrets — NOT trusted publishing / OIDC, so no policy pins GitHub owner or
  repository ids and the org move needed no policy change. `0.1.2` was published 2026-06-24 by a
  `workflow_dispatch` on `main` (`9f2c6d5`, run 28141421397 — release steps skipped), not by its
  tag. **0.2.0 is the first release from the org** (tag `v0.2.0` = `6812998`, run 40406408422 green
  2026-10-26: the secrets work after the move).
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

## Development notes

- **Chicory upgrades:** re-check the `HostFunction` constructor, `Memory` read/write and
  `Instance.builder` against the release notes.
- `Memory.readByteArray(ptr, len)` (in `Abi.kt`) reads byte by byte; replace it if Chicory gains a
  bulk read.
