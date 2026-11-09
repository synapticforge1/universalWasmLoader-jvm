# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-11-09):** **0.4.1 released** (owner: "update the project memory files, commit,
push then release"): tag `v0.4.1` = `f08fb7e`. It is a patch carrying the H30 release job, with no
library change (gate: floor patch; v0.4.0's tests GREEN, 62 run). Publish run 42640047554 was
green in all three jobs. The new REST release job created GitHub Release `v0.4.1` and
`release/v0.4.1` (measured; see overview § Release history). **Live on Maven Central**
(`<latest>0.4.1</latest>`, read 2026-11-09); the README install snippets name 0.4.1. History purged (H19) on 2026-11-08:
every commit id after the root changed, see [history-map-2026-11-08.md](history-map-2026-11-08.md).
Gate: 62 / 62 tests. Every release, its contents and run: [overview.md](overview.md) § Release
history.

## Owner actions (🗓️)

- **H19 follow-up: the GitHub Support request** so that the old commits are no longer served by hash
  ([history-map-2026-11-08.md](history-map-2026-11-08.md) § Still open). The purge itself is done.

## Backlog

- 🟡 **The H32-ordered release script (`8ae7f61`) is UNMEASURED on GitHub until the next tag
  run.** 0.4.1's run measured the H30 version. The change is the order (page check before the
  branch), the total time limit and the Maven link. It is proven by the self-test (22 checks,
  three mutants caught) and by read-only dry runs on the real API. At the next tag, read the page
  back (`author.login` = `github-actions[bot]`, the Maven link on top) and check the tag did not
  move.

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

Rows in `../../../cmem/workspace-correspondence.md` naming this repo. **Answered 2026-11-09
(later):** H32 / C41: release pages are already made by CI here (first new page `v0.4.1` by
`github-actions[bot]`); the order was confirmed against the recipe and fixed to match it
(`8ae7f61`). SPEC 4.0.1 (C35's port allowance) needs no change here.
**Answered 2026-11-09:** H30
(no GitHub CLI in CI: the Kotlin REST script), H31 (record-only: no Bun version here). C29 is
CLOSED for -jvm by loader-js. **Answered 2026-11-08:** C29
(SPEC 4.0.0, released in 0.4.0), H29 (its follow-up, C29), H19 (purged and pushed), C30 (the split,
released in 0.4.0's run) and C34 (record-only); notes row C36. **Answered 2026-10-26** (each
cell names the commit; C18 carries the notes and releases): H6, H7 / C11, H15, H16, H17 / H25 §1,
H20, H22 / H25 §3(d), H24 / H27 / C8, H25 §3(b), H28; drift L2, L3, L6, X3; divergence S4.
