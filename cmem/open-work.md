# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-11-08):** **SPEC 4.0.0 done on branch `spec-4.0.0`, merged to local `main`, NOT
pushed, bumped or released** (workspace C29). Gate: 62 / 62 tests, API snapshot re-baselined
(additive: `LoadOptions` + a `wasmImport` overload), compat floor major, version decision **0.4.0**.
Record: [overview.md](overview.md) § SPEC 4.0.0. Released: **0.3.0** (tag `v0.3.0` = `33eb176`,
SPEC 3.0.0); `origin/main` = `a194253`. Every release, its contents and run: [overview.md](overview.md)
§ Release history.

## Owner actions (🗓️)

- **Release 0.4.0 (SPEC 4.0.0)?** Push `main`, then `./gradlew bump -Pkind=minor` (the gate allows
  a minor at a major floor before 1.0: Rule 6, `_next release_` row present), commit, `./gradlew
  release`. Each step is the owner's.
- **Confirm the `LoadOptions` exception to design principle 3** ("no options objects"). SPEC 4.0.0
  §1 / §11 requires the options argument ([overview.md](overview.md) § Design principles).
- **H19, history purge — a measured finding, not yet acted on.** Tags `v0.1.0` / `v0.1.1` (and 13
  other commits up to `f3fba2d`, 2026-06-17) track `.gradle/9.5.1/executionHistory/executionHistory.bin`,
  which holds the owner's project path and user profile ([working-rules.md](working-rules.md) §
  History). The workspace's H25 scan said "no purge needed"; that was wrong. A purge as loader-dart
  did (`filter-branch --index-filter` over `--branches --tags`, dropping `.gradle/` and `build/`
  from those commits; map old → new; force-push with lease; `gc --prune=now`) **changes the tagged
  trees of `v0.1.0` and `v0.1.1`** — rsxtk's owner-approved exception, not the H19 default. Before
  deciding, check: whether those two tags carry GitHub Releases (immutable releases refuse the tag
  move, as binaryen-ts found); and whether a tag push re-runs `publish.yml`. Its trigger is `v*`
  tags, and Central refuses an existing version, so a re-run should fail harmlessly; that is not
  measured. Maven Central's 0.1.x artifacts (sources jar, not `.gradle/`) are unaffected either way.
  Decide: purge (and push), or leave as is.
- **C30, release-workflow privilege (record-only, reasoned, not measured).** `publish.yml` has no
  `id-token: write`, no `concurrency:` and no `bun fmt`, so C30 (1)–(3) do not apply as written.
  The same hazard exists here in another form. ONE job holds a `contents: write` `GITHUB_TOKEN`,
  written into the clone's remote URL (so `.git/config` holds it). The same job runs third-party
  code: Gradle and its plugins (KGP, vanniktech), the test suite on Chicory / JUnit, the previous
  tag's tests through the gate, and a JDK and a Kotlin compiler fetched by `curl` with no checksum
  (the JDK is "latest 25", not even a fixed version). The four publishing secrets reach only the
  publish step's env. That step must run Gradle plugin code, since the plugin signs. The models are
  loader-dart `cb7bc26` and loader-go `2ff71ef` / `12e3fb4`. A `checks` job (`contents: read`,
  tokenless clone) would run the build, tests and gate. A `publish` job would check the tag is still
  the tested commit and pin downloads by version + sha256. A `release` job would hold `contents:
  write` for the branch + GitHub Release only. Nothing changed without the owner.
- **Confirm 0.2.0 and 0.3.0 appear** in
  `https://repo1.maven.org/maven2/io/github/jrmarcum/universal-wasm-loader-jvm/maven-metadata.xml`
  (`<latest>0.3.0</latest>`). Both were still syncing at 18:29 on 2026-10-26; not re-read since.
  If either is absent, read its state on `https://central.sonatype.com/publishing/deployments`
  (login required).

## Backlog

- 🟡 **README third-party note** (optional, 2026-10-26 licence check): name the Gradle wrapper's
  Apache-2.0 licence and record where `math.wasm` came from (added in `09bf5c0`, provenance not
  recorded; almost certainly the owner's wasmtk). The repo complies without it.
- 🟡 **A stranded upload (C34 (1), by analogy; reasoned, not measured).**
  `publishAllPublicationsToMavenCentralRepository` uploads, then Central validates and releases. A
  failure after the upload leaves the version on Central with the job red. The release branch and
  the GitHub Release are then skipped, and Central refuses the same version on a re-run, so only a
  manual bookkeeping step recovers. Central, like npm, never reuses a version number. C34 (2)–(4)
  are JSR / npm / Deno only: n/a.

Closed 2026-11-08 by SPEC 4.0.0 (formerly here): the `.wit` fallback that masked server errors
(the owner decided: only absent is raw); the body that stalls after the headers (now within the
limit, tested); the WIT call path's linear export lookup (now a map).

## Workspace ledger

Rows in `../../../cmem/workspace-correspondence.md` naming this repo. **Answered 2026-11-08:** C29
(SPEC 4.0.0, the commits named in its cell), H29 (its follow-up, C29), H19 (the finding above;
nothing purged), C30 and C34 (record-only, read and recorded above). **Answered 2026-10-26** (each
cell names the commit; C18 carries the notes and releases): H6, H7 / C11, H15, H16, H17 / H25 §1,
H20, H22 / H25 §3(d), H24 / H27 / C8, H25 §3(b), H28; drift L2, L3, L6, X3; divergence S4.
