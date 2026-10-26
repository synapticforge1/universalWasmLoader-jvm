> **⚠️ PORTABLE PROJECT MEMORY LIVES IN `cmem/`** — start at [`cmem/INDEX.md`](cmem/INDEX.md).
> This file is a pointer only (the old 15 KB body: `git show 0af04cb:CLAUDE.md`, absorbed into
> `cmem/overview.md` on 2026-10-26).

> **At session start, and before each new work item, read `../../cmem/workspace-correspondence.md`**
> (the one ledger of inter-project items: from · to · subject · open/closed): every row whose "to"
> names this repo, and every row whose subject touches what you are about to change. Then read the
> letter it points to in `../../cmem/handoffs.md`. Answer in this repo's own `cmem/`, then write the
> commit (or "declined: why") into the row's "full text" cell — never flip a row's status; the
> creator verifies and closes. The workspace session (`../../cmem/INDEX.md`) reviews across the
> sibling repos and reports there; nothing is ever written into this repo from outside. (owner
> directives, 2026-09-20 and 2026-10-26)

> When saving new project memory, write it into the matching `cmem/` topic file (and refresh its
> pointer in `cmem/INDEX.md`). The **"update the project memory"** and **"look for code issues"**
> triggers are defined in `cmem/INDEX.md` and are binding.

# universalWasmLoader-jvm

The JVM (Kotlin) port of the Universal WASM Loader, on Chicory (pure Java, no native code). Maven
Central `io.github.jrmarcum:universal-wasm-loader-jvm`, package `com.jrmarcum.universalwasmloader`
(🔒 both stay, owner 2026-10-26). The spec is universalWasmLoader-js's `SPEC.md` (v3.0.0); no local copy.
State and open items: [`cmem/open-work.md`](cmem/open-work.md).

## What bites in the first five minutes

- **The gate is `./gradlew check`** (tests + the `api/` snapshot check) and
  `kotlin scripts/compat.main.kts selftest`. Read counts from `build/test-results/test/*.xml`.
- **Versions move only through `./gradlew bump`** (the compatibility gate; it refuses below the
  floor). A public API change re-baselines first, alone: `./gradlew updateLegacyAbi`.
- **Committed scripts are Kotlin `.main.kts`**; no shell scripts, no heredocs — content via `Write`/`Edit`.
- **Fixtures are the stamped set from universalWasmLoader-js** — never edit or regenerate them here.
- **Nothing is pushed, tagged or published unless the owner says so** (`./gradlew release` publishes).
- **No secrets, no personal information, no machine-specific paths** in anything committed
  ([`cmem/working-rules.md`](cmem/working-rules.md)).
- Exports are looked up by **camelCase** (`is-positive` → `isPositive`), never kebab or snake.
