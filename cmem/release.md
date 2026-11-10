# release.md — how a release runs, and the Release History

🔒 The one place releases are recorded (owner, 2026-11-10: "releases should be recorded in one place
in every project; … release.md should be the file name with the newest first in the 'Release
History' with a summary of what changed in each one" — workspace letter H33, decision D3). The
uniform shape it follows is the workspace's `../../../cmem/release-process.md` § 5 (slots U1–U12).
Build facts, the publishing plugin and the credentials stay in [overview.md](overview.md) § Build
and release; the gate in [compat-gate.md](compat-gate.md).

## How a release runs (since 2026-11-10)

🔒 **The owner's order (2026-11-10, D1): "the CI to check first, then push the tag, and then create
the release page from the tag, then publish the package to the registry."** The owner's word
"release" = "push" (memory, commit, merge, push `main`), then this (`working-rules.md` § Owner
commands). Nothing is tagged or published from the owner's machine.

**Local — the owner's go and level (U1–U4):**

1. **U1** The owner gives the go and the level (patch / minor / major); there is no default.
2. **U2** Everything is merged into `main` first, the memory included.
3. **U3** `./gradlew bump -Pkind=<level>` (→ `kotlin scripts/compat.main.kts bump <level>`): it
   refuses a dirty tree (untracked files count), a stale `api/` snapshot, and a level below the
   compatibility floor. For a patch or minor floor it runs the previous tag's tests against this
   tree. It stamps the README `_next release_` row, then **commits `bump to vX.Y.Z` itself**.
4. **U4** `./gradlew release` (→ `kotlin scripts/release.main.kts`; `-PdryRun` for a rehearsal).
   It checks a clean tree, that the branch is `main` and a fast-forward of the freshly fetched
   `origin/main`, that tag `vX.Y.Z` is absent locally and on origin, and that Maven Central does
   not serve the version (POM 404). It runs a cold gate: `gradlew clean check`, `compat release`,
   and both self-tests. Then it **pushes `main` only** and reads it back with `git ls-remote`.
   **No tag, no `-f`** — the old Gradle task's `git tag -f` and forced tag push are gone.

**CI — `.github/workflows/publish.yml` ("Release"), on every push to `main` (U5–U9):**

| job | permission | does |
| --- | --- | --- |
| `checks` (first) | contents: read | Token-less clone of `github.sha`. Runs `check`, the compat self-test and `release` mode (floor + previous tag's tests), and the release script's self-test. "Is this a release?": the version has no tag on GitHub |
| `tag` | contents: write | Only when `checks` passed and the version is new. `github-release.main.kts tag`: the annotated tag on `github.sha` by REST, read back. A tag elsewhere is refused (a published tag never moves); one already at `github.sha` = done |
| `release` | contents: write | `github-release.main.kts release`: the page FROM the tag in H32's order (tag peels → an existing page is done → POST with generated notes and the Maven Central link on top → read back → peel again). No `release/<tag>` branch (workspace C45) |
| `publish` (last) | contents: read | Skips the upload when `repo1` already serves the version (re-run safe). Otherwise `./gradlew -x test publishAllPublicationsToMavenCentralRepository`, with the four secrets in that one step. Then the **fatal read-back**: the POM, jar, `.asc` and sources jar answer 200 (polled up to 60 min), and the jar's signature gives GnuPG `VALIDSIG` for the pinned fingerprint `E4D45440A37F73383B38842FFF9D45E2AFFFFB6D` |

Top-level `permissions: {}`; a concurrency group per ref that never cancels; 0 `uses:`; no GitHub
CLI (H30); the JDK, Kotlin compiler and Gradle distribution pinned by version and sha256 (U9). A
tag pushed with the job's `GITHUB_TOKEN` starts no new run, so all four jobs run in the one run, by
`needs:`. A push of `main` whose version is already tagged runs `checks` only.
⚠️ **The version line in `build.gradle.kts` ARMS a release:** a push of `main` that carries an
untagged version releases it. `ci.yml` now runs on pull requests only.

**After (U10–U12):** read the release back as a user (resolve
`io.github.jrmarcum:universal-wasm-loader-jvm:<version>` from Central in a scratch project and load
a fixture). Record it below. On a failure, "Re-run failed jobs": each job skips what is done; never
a new tag for the same code, never a moved tag.

**Proof of the new flow (2026-11-10, before landing; best-practices 7.22):**
- **The local half, rehearsed end to end.** A scratch clone used a scratch bare repository as
  `origin`. `bump patch` passed the gate (floor patch; v0.4.2's 62 tests green) and committed
  `bump to v0.4.3`, the version line only, leaving a clean tree. `release --dry-run` passed every
  guard and the cold gate. The real run pushed `main` and read it back, and **no tag appeared on
  the bare origin**. A second run was refused ("nothing to push").
- **Measured refusals:** `bump` with no level; `bump` on a dirty tree.
- **The CI half, read-only** (dry runs on the real API, no token): `tag v0.4.2` at its commit →
  nothing written; at 0.4.1's commit → refused; `tag v0.4.3` → would create; `release v0.4.3`
  before its tag → refused. The self-test has 29 checks; the mutant "an existing tag elsewhere
  may be overwritten" was caught.
- One defect found by the rehearsal and fixed: a script started by the `kotlin` launcher on
  Windows could not start another `kotlin` (the launcher's quoted `JAVA_OPTS` broke the nested
  `kotlin.bat`). Children now start without the launcher's variables.
- **UNMEASURED on GitHub** until the next push of `main` (`checks` only) and the next release
  (`tag` → `release` → `publish` and the signature read-back).

## Release History (newest first)

Commit ids are POST-purge (H19, 2026-11-08); pre-purge ids cited elsewhere are in
[history-map-2026-11-08.md](history-map-2026-11-08.md). Each release from 0.2.0 on went through the
compatibility gate locally (`bump`) and on the runner (`release`).

### 0.4.2 — 2026-11-09 · tag `v0.4.2` → `598d321` · live

**What changed:** no library change (patch). The release script made the GitHub Release page in
the workspace's H32 order: the page check before the branch, a time limit to the last byte on every
request, and the Maven Central link on top of the notes (`8ae7f61`). Gate: floor patch; v0.4.1's
62 tests green.
**Run** 42668202944 (owner: "… push and release"): all three jobs green (the split workflow, tag
pushed by `./gradlew release`). Page `v0.4.2` by `github-actions[bot]`, 17:51:50Z, not a draft,
Maven link on top of the generated notes `v0.4.1...v0.4.2`. `release/v0.4.2` = `598d321`; the tag
did not move. **Central:** `<latest>0.4.2</latest>`; POM, jar, `.asc` and sources 200 minutes
after the run.

### 0.4.1 — 2026-11-09 · tag `v0.4.1` → `f08fb7e` · live

**What changed:** no library change (patch). The workflow's `release` job made the branch and the
GitHub Release through GitHub's REST API, from a Kotlin script, instead of the GitHub CLI
(workspace H30; `bf7737b`). Gate: floor patch; v0.4.0's 62 tests green.
**Run** 42640047554 (owner: "… push then release"): all three jobs green. The first REST-made page,
`v0.4.1` by `github-actions[bot]`, not a draft; `release/v0.4.1` = `f08fb7e`; the tag did not
move. **Central:** live (read 2026-11-09).

### 0.4.0 — 2026-11-08 · tag `v0.4.0` → `264e6f3` · live

**What changed:** **SPEC 4.0.0** (minor under pre-1.0 Rule 6; floor major). Only an absent `.wit`
means raw exports; WIT is parsed strictly; every WIT import needs a host callback whose value is
checked; typed calls check count and kinds with no coercion; `LoadOptions(timeout)`, 30 s to the
last byte, cancellable. Surface `c1c6160` + behaviour `8b0db4f` (merge `a8bade1`); README Breaking
Changes row 0.4.0. Also the three-job publish workflow (workspace C30, `ec7398c`); the first
release after the H19 history purge.
**Run** 42512825706 (owner: "yes release 0.4.0"): `checks` / `publish` / `release` all green.
**Central:** live (`lastUpdated` 20261108191302).

### 0.3.0 — 2026-10-26 · tag `v0.3.0` → `cf53474` · live

**What changed:** raw mode (no `.wit`) is typed by each export's wasm signature (`d2db3ec`), and
`wasmImport` got timeouts of 10 s to connect and 30 s for the response (`fe91d4b`). Two Breaking
Changes rows. **Run** 40411608731; it published itself.

### 0.2.1 — 2026-10-26 · tag `v0.2.1` → `5ce187e` · live

**What changed:** `wasmImport` reports an HTTP error on the `.wasm` (`01a07ae`); publishing plugin
0.37.0 with no deprecation warnings; automatic publishing after the upload. **Run** 40408929155;
it published itself (the first release that did).

### 0.2.0 — 2026-10-26 · tag `v0.2.0` → `ef3846b` · live

**What changed:** SPEC § 10, so `_initialize` is called and a minimal WASI shim is provided
(`47c7813`; Breaking row); workspace conformance (`01ef84f`). The first release from the
`synapticforge1` org and the first through the compatibility gate. **Run** 40406408422; the owner
clicked Publish on the Portal.

### 0.1.2 — 2026-06-24 · tag `v0.1.2` → `19a3757` · live

**What changed:** the first Maven Central release; SPEC 3.0.0 string returns (`86e755b`).
Published by a `workflow_dispatch` on `main` (`e8c12ff`, run 28141421397), with a manual Publish
click.

### 0.1.0, 0.1.1 — 2026-05-20 · tags `v0.1.0` → `322e1d9`, `v0.1.1` → `da2777a`

Early builds; never reached Central; no Release pages (owner: only new tags get pages).
