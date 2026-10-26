# The compatibility gate (workspace letter H6, built 2026-10-26)

🔒 Binding (owner, 2026-09-20; workspace `../../../cmem/layout-and-packaging.md` § "The compatibility
gate"): "backward compatible" is a gate result, never a judgment made at bump time. Modelled on
loader-dart's and loader-dotnet's gates (`../../universalWasmLoader-{dart,dotnet}/cmem/compat-gate.md`).
One file: `scripts/compat.main.kts` (Kotlin script; workspace Tooling rule 1).

## What runs where

| Rule | File | Runs in |
| --- | --- | --- |
| 1, the SURFACE | `api/universal-wasm-loader-jvm.api` (committed), from KGP 2.2's ABI validation | `checkLegacyAbi` inside `./gradlew check` (local + CI); `updateLegacyAbi` re-baselines |
| 2, the FLOOR | `compat.main.kts` `floorOf` | `bump` (refuses below it), `report`, `release` (CI on the tag, blocking, before publish) |
| 3, BEHAVIOUR | `compat.main.kts` `previousTests` | same places; required green unless the floor is major |
| 4, deprecate first | README `### ⚠️ Breaking Changes` | a major floor needs a `\| _next release_ \|` row; `bump` stamps the version into it; `release` needs the version's row |
| 5, re-baseline alone | the STALE refusal | an intended API change runs `updateLegacyAbi` in its own commit, with the reason |
| 6, pre-1.0 | `problemsFor` | 0.y.z: a minor may stand in for a major floor; the row is still required |
| self-test | `kotlin scripts/compat.main.kts selftest` (30 checks) | `ci.yml` and `publish.yml`; no git, no build, no network |

**The surface:** the binary API as the Kotlin Gradle plugin dumps it (one line per public class and
member, JVM descriptors) plus the SPEC version the README declares (`` `SPEC.md` … **vX.Y.Z** ``;
for a tag older than that line, `Version:` in the tag's own `SPEC.md`). 83 lines on 2026-10-26.
⚠️ **Blind spot:** inline-reified `wasmLoad<T>` has no binary entry point, so it is not in the dump;
Rule 3 guards it (old tests that call it must still compile and pass). Default-argument VALUES are
invisible too.

**Why KGP's validator:** the standalone `org.jetbrains.kotlinx.binary-compatibility-validator`
0.18.1 fails on this build ("Unsupported class file major version 68": its ASM cannot read JVM 24
classes). KGP 2.2's `abiValidation` (experimental, `@OptIn(ExperimentalAbiValidation::class)`)
works, but does NOT hook `check` by itself — `build.gradle.kts` adds `check.dependsOn(checkLegacyAbi)`
(measured with `check --dry-run`, 2026-10-26).

**The released surface:** `git show <tag>:api/universal-wasm-loader-jvm.api`. **The `v0.1.2`
exception:** that tag predates the snapshot, so `api/v0.1.2.api` was generated ONCE from a worktree of
the tag (its `src/`, the current `build.gradle.kts`) on 2026-10-26 and committed; it equals today's
snapshot line for line (SPEC §10 changed behaviour, not API). Every later tag carries its own file.

**The floor:** a key per class header and per member; removed or changed → major, added → minor,
nothing → patch; the SPEC version moves it by its own level (lowered → major). It errs high: a class
header that only gains `open` counts as changed.

**Previous-tag tests:** a `git worktree` of the tag in the system temp dir; its `src/main` and build
files (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, the wrapper)
replaced by the current ones; its `src/test` (tests AND fixtures) untouched; `gradlew test`. Counts
read from the JUnit XML: green needs exit 0, at least one test, 0 failures + errors. The worktree is
always removed. `v0.1.2`: **24 run, 0 failed** against the current library (2026-10-26).

**Consumers (Rule 3, second half):** no known Maven artifact depends on this one; nothing to run.

## The port of `scripts/bump.sh` (workspace H15), proved 2026-10-26

Same inputs, each on a fresh full clone with the working tree copied over: stdout, the `❌` stderr
line, exit code and the written `build.gradle.kts` compared.

| Input | Result |
| --- | --- |
| (no arg), `patch`, `minor`, `major` | identical: same success line, exit 0, same file (0.1.3 / 0.2.0 / 1.0.0) |
| `bogus` | identical: same `❌ bump: unknown release kind "bogus" …`, exit 1, file untouched |
| no `version =` line | both exit 1, file untouched; `bump.sh` printed NOTHING (under `pipefail`, `grep` finding no line killed it before its own message), the port prints the message `bump.sh` intended |

The one difference is that defect, fixed. The port adds the gate's own refusals, which `bump.sh` never
had (and so cannot be compared). `bump.sh` was removed after the proof.

## Inverted end to end, 2026-10-26 (a scratch clone per mutant; nothing in the repo broken)

| Mutant | Command | Result |
| --- | --- | --- |
| bool result decode inverted in `Abi.kt` (surface unchanged) | `bump patch` | refused: floor patch, previous-tag tests RED, 24 run / 6 failed |
| `isSameInstance` removed, NOT re-baselined | `bump patch` | refused: snapshot STALE |
| same, re-baselined | `bump patch` | refused: below the floor (major), the removed member named |
| same | `bump minor` | ALLOWED → 0.2.0 (0.x exemption, `_next release_` row present) |
| same, the row removed | `bump minor` | refused: the floor is major, no row |
| `loaderName()` added, re-baselined | `bump patch` | refused: floor minor ("added: … loaderName") |
| README SPEC `v3.0.0` → `v3.1.0` | `bump patch` | refused: floor minor |

Self-test inverted too: `allowed = kind >= Level.PATCH` → 3 of 30 checks fail, the three that guard
the floor.

**CI half: measured 2026-10-26.** `ci.yml` run 40406036278 on `6812998` green (kotlin setup,
`check`, self-test); `publish.yml` run 40406408422 on tag `v0.2.0` green — the shallow tag fetch,
`check`, the gate's `selftest` + `release` (`v0.1.2` → `0.2.0`, minor) on the runner, then the
upload. Whether the runner had `kotlin` preinstalled or used the download fallback was not read.

## Traps met building it

- **Kotlin comments nest.** `` `api/*.api` `` in a KDoc opened a nested comment that ran to the end
  of the file: the script compiled to nothing, exited 0 and printed nothing (best-practices L1).
- **`cmd` did not find `gradlew.bat` in its working directory** (this environment sets
  `NoDefaultCurrentDirectoryInExePath`); the wrapper is now called by absolute path. The first
  version reported that as a STALE snapshot — a verdict from a check that never ran (L2).
- **Redirected JVM output uses the Windows code page**: `✅` / `❌` / `→` became `?`. The script sets
  UTF-8 on both streams, as bash wrote them.
- **A worktree's gitdir stays on the repo's drive.** On this machine that drive is exFAT (no
  ownership), so git refuses to run INSIDE a temp worktree ("dubious ownership"). The gate only runs
  git in the repo itself (the temp worktree runs Gradle only), so it is unaffected; the proof
  harnesses used full clones instead.
