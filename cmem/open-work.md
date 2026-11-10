# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-11-10):** latest release **0.4.2**, live (tag `v0.4.2` = `598d321`; Maven Central
`<latest>0.4.2</latest>`; the README install snippets name it). **The release process was
converged to the workspace's H33 shape on the working branch `h33-release-process`, NOT merged
into `main` and NOT pushed (H34: that is "push", the owner's word):** `bump` takes a required level and commits itself; `./gradlew release` pushes `main`
only; CI checks, tags, makes the page and publishes ([release.md](release.md)). The local half was
rehearsed end to end against a scratch origin. Gate: 62 / 62 tests. History purged (H19) on
2026-11-08 (every id after the root changed; [history-map-2026-11-08.md](history-map-2026-11-08.md)).
Every release, its summary and run: [release.md](release.md) § Release History.

## Owner actions (🗓️)

- **H19 follow-up: the GitHub Support request** so that the old commits are no longer served by hash
  ([history-map-2026-11-08.md](history-map-2026-11-08.md) § Still open). The purge itself is done.

## Backlog

- 🟡 **The H33 release workflow is UNMEASURED on GitHub.** At the first push of `main`, read the
  run: `checks` green, then `tag` / `release` / `publish` SKIPPED (the version is already tagged).
  At the next release, read: the tag CI made (annotated, peels to the bump commit, by
  `github-actions[bot]`), the page from it, the Central read-back and the signature step green.
  Rehearsed so far: the local half end to end (scratch origin), the REST `tag` / `release`
  commands as read-only dry runs on the real API, 29 self-test checks ([release.md](release.md)).
- 🟡 **U10 as a user** is done by hand after a release (resolve the version from Central in a
  scratch project, load a fixture); no script yet. The workspace card did not ask for one.
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
