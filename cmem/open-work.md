# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-11-08):** **0.4.0 released (SPEC 4.0.0)**: tag `v0.4.0` = `264e6f3`; `main` =
`origin/main` (memory commits only after the tag). Publish run 42512825706 green in all three jobs
(the new C30 split); GitHub Release created. Central: the owner reports 0.4.0 "in process of
publishing". Re-read later the same day: the `repo1` metadata still says `<latest>0.3.0</latest>`
and the 0.4.0 POM is 404. History purged (H19) and force-pushed the same day: every commit id after the
root changed, see [history-map-2026-11-08.md](history-map-2026-11-08.md). Gate: 62 / 62 tests.
Every release, its contents and run: [overview.md](overview.md) § Release history.

## Owner actions (🗓️)

- **Confirm 0.4.0 appears** in
  `https://repo1.maven.org/maven2/io/github/jrmarcum/universal-wasm-loader-jvm/maven-metadata.xml`
  (`<latest>0.4.0</latest>`), then point the README install snippets at 0.4.0 (they say 0.3.0
  until it is live). If it is absent the next day, read its state on
  `https://central.sonatype.com/publishing/deployments` (login required).
- **H19 follow-up: the GitHub Support request** so that the old commits are no longer served by hash
  ([history-map-2026-11-08.md](history-map-2026-11-08.md) § Still open). The purge itself is done.

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
(SPEC 4.0.0, released in 0.4.0), H29 (its follow-up, C29), H19 (purged and pushed), C30 (the split,
released in 0.4.0's run) and C34 (record-only); notes row C36. **Answered 2026-10-26** (each
cell names the commit; C18 carries the notes and releases): H6, H7 / C11, H15, H16, H17 / H25 §1,
H20, H22 / H25 §3(d), H24 / H27 / C8, H25 §3(b), H28; drift L2, L3, L6, X3; divergence S4.
