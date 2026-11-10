# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-11-10):** **0.4.3 released and live** (owner: "release", level patch), the first
release in the workspace's H33 shape: `bump` committed `5554109`, `./gradlew release` pushed
`main` only, and CI run 42911973493 checked, tagged (`v0.4.3`, annotated, by
`github-actions[bot]`), made the page and published, with a fatal read-back and signature check.
Maven Central `<latest>0.4.3</latest>`; checked as a user (U10). The README install snippets name
0.4.3. ⚠️ The version line now ARMS a release ([release.md](release.md)). Gate: 62 / 62 tests. History purged (H19) on
2026-11-08 (every id after the root changed; [history-map-2026-11-08.md](history-map-2026-11-08.md)).
Every release, its summary and run: [release.md](release.md) § Release History.

## Owner actions (🗓️)

- **H19 follow-up: the GitHub Support request** so that the old commits are no longer served by hash
  ([history-map-2026-11-08.md](history-map-2026-11-08.md) § Still open). The purge itself is done.

## Backlog

- 🟡 **U10 as a user** is done by hand after a release (a one-time `.main.kts` resolving the
  version from repo1, loading fixtures; done for 0.4.3). No committed script; the workspace card
  did not ask for one.
- 🟡 **Under Gradle's `-q`, `./gradlew release` prints none of the script's lines** (measured
  2026-11-10; the push was read back with `ls-remote`). Run it without `-q`, or call
  `kotlin scripts/release.main.kts` directly, to see the guards.
- 🟡 **README third-party note** (optional, 2026-10-26 licence check): name the Gradle wrapper's
  Apache-2.0 licence and record where `math.wasm` came from (added in `09bf5c0`, provenance not
  recorded; almost certainly the owner's wasmtk). The repo complies without it.
- 🟡 **A stranded upload (C34 (1), by analogy) — mostly addressed 2026-11-10, unmeasured.** The
  page is now made BEFORE the publish (owner's order), and the publish job skips the upload when
  `repo1` already serves the version. So "Re-run failed jobs" after a failure that follows the
  upload goes straight to the read-back. Still open: an upload that Central holds but has not
  synced yet, and that a re-run therefore cannot see, would be uploaded again (Central refuses a
  duplicate); the 60-min read-back poll makes that window small. C34 (2)–(4) are JSR / npm / Deno
  only: n/a.

Closed 2026-11-08 by SPEC 4.0.0 (formerly here): the `.wit` fallback that masked server errors
(the owner decided: only absent is raw); the body that stalls after the headers (now within the
limit, tested); the WIT call path's linear export lookup (now a map).

## Workspace ledger

Rows in `../../../cmem/workspace-correspondence.md` naming this repo. **Answered 2026-11-10:**
H33 (the release process, slot by slot), C45 (no `release/<tag>` branch), H34 (the owner's three
words, committed as found). **Answered 2026-11-09 (later):** H32 / C41: release pages are already made by CI here (first new page `v0.4.1` by
`github-actions[bot]`); the order was confirmed against the recipe and fixed to match it
(`8ae7f61`). SPEC 4.0.1 (C35's port allowance) needs no change here.
**Answered 2026-11-09:** H30
(no GitHub CLI in CI: the Kotlin REST script), H31 (record-only: no Bun version here). C29 is
CLOSED for -jvm by loader-js. **Answered 2026-11-08:** C29
(SPEC 4.0.0, released in 0.4.0), H29 (its follow-up, C29), H19 (purged and pushed), C30 (the split,
released in 0.4.0's run) and C34 (record-only); notes row C36. **Answered 2026-10-26** (each
cell names the commit; C18 carries the notes and releases): H6, H7 / C11, H15, H16, H17 / H25 §1,
H20, H22 / H25 §3(d), H24 / H27 / C8, H25 §3(b), H28; drift L2, L3, L6, X3; divergence S4.
