# release.md — how a release runs, and the Release History

Last verified: 2026-11-24. The one place releases are recorded (owner, 2026-11-10; letter H33, D3): newest
first, a summary of what changed in each. The uniform shape is the workspace's
`../../../workspace/cmem/release-process.md` § 5 (U1–U12, amended by the review standard, letter H37). Build
facts and credentials are in [overview.md](overview.md); the gate in [compat-gate.md](compat-gate.md).

## How a release runs (since 2026-11-24)

🔒 **The owner (2026-11-17): every gate runs locally; "We want the CI to verify the version, then tag, create
the release page and publish, and skip any version already published."** The word "release" = "push", then
this ([working-rules.md](working-rules.md) § Owner commands). Nothing is tagged or published from this machine.

**Local — the owner's go and level (U1–U4):**

1. **U1** The owner gives the go and the level (patch / minor / major); there is no default.
2. **The review set** has run on this code (letter H37, [review-process.md](review-process.md)): the gitignored
   `cmem/review-issues.md` holds `- Review complete: <commit>`.
3. **U2** Everything is merged into `main` first, the memory included.
4. **U3** `./gradlew bump -Pkind=<level>` refuses a dirty tree (untracked files count), a stale `api/`
   snapshot and a level below the floor; for a patch or minor floor it runs the previous tag's tests. It
   stamps the README `_next release_` row, then **commits `bump to vX.Y.Z` itself**.
5. **U4** `./gradlew release` (`-PdryRun` to rehearse; `scripts/release.main.kts`), in order:
   - **the review gate** — the review line is present, its commit is an ancestor of HEAD, and since it only
     `cmem/`, `README.md` and the version line changed;
   - a clean tree; `main`, a fast-forward of the freshly fetched `origin/main`; the tag new locally and on
     origin; Maven Central without the version (POM 404);
   - **every gate, cold and locally** — `gradlew clean check`, `compat release`, `check-host`, `check-privacy`,
     `check-cmem` and the scripts' self-tests ([working-rules.md](working-rules.md) § The gate);
   - then it **pushes `main` only** and reads it back with `git ls-remote`. No tag, no `-f`.

**CI — `.github/workflows/publish.yml` ("Release"), on every push to `main`, ONLY PUBLISHES:**

- **`version`** (contents: read): a token-less clone of `github.sha`; reads the one `version = "X.Y.Z"` line and
  asks GitHub whether tag `vX.Y.Z` exists. No build, no test, no toolchain.
- **`tag`** (contents: write; only for a new version): `github-release.main.kts tag` makes the annotated tag on
  `github.sha` by REST and reads it back. A tag elsewhere is refused; one already at `github.sha` = done.
- **`release`** (contents: write): `github-release.main.kts release`, the page FROM the tag in H32's order
  (tag peels → an existing page is done → POST with generated notes and the Maven Central link on top → read
  back → peel again). No `release/<tag>` branch (C45).
- **`publish`** (contents: read): skips when `repo1` serves the version. Otherwise
  `./gradlew -x test publishAllPublicationsToMavenCentralRepository`, the four secrets in that one step. Then
  the **fatal read-back**: POM, jar, `.asc` and sources 200 (polled up to 60 min), and the jar's signature
  gives GnuPG `VALIDSIG` for the pinned fingerprint `E4D45440A37F73383B38842FFF9D45E2AFFFFB6D`.
- Top-level `permissions: {}`; a concurrency group per ref that never cancels; 0 `uses:`; no GitHub CLI; the
  JDK, Kotlin and Gradle pinned by version and sha256. A tag pushed with `GITHUB_TOKEN` starts no new run, so
  the four jobs run in one run, by `needs:`. A push of an already-tagged version runs `version` only.
- ⚠️ **The version line ARMS a release:** a push of `main` carrying an untagged version is released.

**After (U10–U12):** read the release back as a user (resolve the version from Central in a scratch project and
load a fixture). Record it below, with a `Tested on: <OS and version> (<platform ID>)` line per platform run
([platforms.md](platforms.md)). On a failure, "Re-run failed jobs": each job skips what is done; never a new tag
for the same code, never a moved tag.

How this flow was proven: [archive/release-flow-proofs.md](archive/release-flow-proofs.md).

## Release History (newest first)

Commit ids are POST-purge (H19, 2026-11-08); pre-purge ids cited elsewhere are in
[archive/history-map-2026-11-08.md](archive/history-map-2026-11-08.md). Each release from 0.2.0 on went through the
compatibility gate locally (`bump`) and on the runner (`release`).

### 0.4.3 — 2026-11-10 · tag `v0.4.3` → `5554109` · live

**What changed:** no library change (patch). This is the first release in the owner's order
(workspace H33): `bump` takes a required level and commits itself; `./gradlew release` pushes
`main` only; CI checks, then tags, then makes the page, then publishes, with a fatal read-back and
a signature check. The page is made from the tag with no `release/<tag>` branch (C45). H34's
owner-commands section is committed. Gate: floor patch; v0.4.2's 62 tests green (in `bump`, the
local cold gate and CI). Owner: "release", level patch (asked; H34).
**Local:** `./gradlew bump -Pkind=patch` → commit `5554109` "bump to v0.4.3" (version line only).
`./gradlew release` passed its guards and the cold gate, pushed `main` and read it back (`ls-remote`
`main` = `5554109`, no tag).
**Run** 42911973493: `checks` → `tag` → `release` → `publish`, all green. **Tag:** made by CI,
annotated (object `a1fddbe`, tagger `github-actions[bot]`, 22:12:30Z), peels to `5554109`. **Page:**
`v0.4.3` by `github-actions[bot]`, 22:12:46Z, not a draft, the Maven Central link on top of the
generated notes; `releases/latest` = `v0.4.3`; no `release/v0.4.3` branch. **Central:** the
publish job's fatal read-back saw the POM, jar, `.asc` and sources jar answer 200, and the jar's
signature gave `VALIDSIG` for `E4D45440…AFFFFB6D`. `<latest>0.4.3</latest>` (read after the run;
POM 200 at 17:24 local, about 12 min after the tag). **As a user (U10):** in a scratch folder a
`.main.kts` resolved `io.github.jrmarcum:universal-wasm-loader-jvm:0.4.3` from repo1. `math_50`
`add(3, 4)` = 7 and `multiply(2.5, 4.0)` = 10.0; `add(2.7, 1)` was refused ("'add' parameter a is
s32, got Double 2.7"); `imports_50` `scale` = 12.0, and loading it with no callbacks was refused,
naming both.

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
