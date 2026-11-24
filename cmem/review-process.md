# review-process.md — this repo's slots in the review standard

Last verified: 2026-11-24. The standard is the workspace's `../../../workspace/cmem/review-process.md` (final,
2026-11-17; letter H37): it is not restated here. This file fills its slots (§ 12) for universalWasmLoader-jvm.

## How a review runs here

- **The words:** "review" runs the five required classes in order — basic, hardening, licensing, security,
  privacy — after the optional efficiency review; "<class> review" runs one ([working-rules.md](working-rules.md)).
- **The loop** is § 9 of the standard: one agent per class over the full scope since the last release, triage,
  fixes per class on a local branch `review-<version>`, re-reviews of the fixes only, ending at the first
  empty re-review; then the full suite once on every platform that has a machine ([platforms.md](platforms.md)).
- **Each reviewer's brief** is its class's item list, word for word, plus the scope, nothing else; it also
  gets `cmem/STATE.md`. A reviewer never commits, pushes or releases. Its scratch folder sits under the main
  session's scratchpad and is removed when its round is triaged (Tooling rule 15).
- **Issues** are kept in `cmem/review-issues.md`, gitignored (`scripts/check-privacy.main.kts` fails if it is
  ever tracked). Rejected findings are listed there with the reason.
- **The release gate:** `./gradlew release` refuses unless that file holds a line `- Review complete: <commit>`,
  that commit is an ancestor of HEAD, and only `cmem/`, `README.md` and the version line changed since
  ([release.md](release.md) § How a release runs).

## The slots

- **Scope:** `src/main/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `scripts/`, `.github/workflows/`,
  `README.md`, `LICENSE`, `api/`; the published artifacts (jar, sources jar, javadoc jar, POM) as Central
  serves them. The test suite and fixtures are in scope for the basic review's test-adequacy item only.
- **Private-data paths** (never given to a reviewer, never tracked): `cmem/local/`, `cmem/review-issues.md`
  (the issues themselves are given to re-reviewers by the main agent), `.claude/`, the root `CLAUDE.md`.
- **Reference results:** `./gradlew check` → 62 tests, 0 failures, 0 errors (`build/test-results/test/*.xml`,
  2026-11-24); the API snapshot `api/universal-wasm-loader-jvm.api` unchanged since 0.4.0; SPEC 4.0.0 § 8's
  reference suite and Refusals table, each row a test in `LoaderTest.kt`; the fixtures equal to the stamp in
  `src/test/resources/tests/fixtures.json` (a test).
- **Support matrix and platform notes:** [platforms.md](platforms.md) (tier 1: per engine).
- **Licence inventory and notices:** the project's licence is `LICENSE` at the root and the POM `<licenses>`
  field in `build.gradle.kts`. The dependencies are in `build.gradle.kts` (`dependencies { }`); the inventory is
  the resolved runtime classpath, `./gradlew dependencies --configuration runtimeClasspath`. The fixtures come
  from universalWasmLoader-js (stamped); `math.wasm` at the root has no recorded origin ([open-work.md](open-work.md)
  O8). No `NOTICE` file exists today; whether one is owed is the licensing review's to decide.
- **Outputs that can carry user facts:** the library writes no files. Error messages name the paths and URLs
  the caller passed (`.wit`, `.wasm`), and their HTTP status. A loaded module's WASI calls reach the process's
  standard streams, and WASI is built with `inheritSystem()` (`Host.kt`): the guest is given the environment
  and arguments too. That is a candidate for the security and privacy reviews.
- **Gate scripts** (run locally; never repeated on GitHub): `./gradlew check`; `kotlin scripts/compat.main.kts
  selftest` and `release`; `kotlin scripts/github-release.main.kts selftest`; `kotlin scripts/check-host.main.kts`
  (and `selftest`); `kotlin scripts/check-privacy.main.kts` (and `selftest`); `kotlin scripts/check-cmem.main.kts`.
  On demand only: `./gradlew hostSelfCheck`.

## Items that do not apply here, with the reason

- **Basic — "Cross-implementation agreement":** applies; the other loaders are compared through SPEC 4.0.0 §
  8's shared fixtures and Refusals table, not by running them here.
- **Hardening — "Fuzzing of each binary and text parser":** the binary parser is Chicory's (a dependency); this
  repo's own text parser is `WitParser.kt`, which is in scope.
- **Hardening — "Memory behavior … across FFI"** and **Security — "Exploitable memory corruption"** and
  **"Native library loading":** no FFI, no native code and no native library (Chicory is pure Java).
- **Security — "Sandbox boundary (runtimes)":** the runtime is Chicory; what applies is this loader's own host
  calls (`Abi.kt`: memory reads for string arguments and results) and the WASI configuration (`Host.kt`).
- **Privacy — "Asset metadata":** no images or documents are shipped.
- **Privacy — "Third-party data":** the library processes no one's files but the caller's modules.
