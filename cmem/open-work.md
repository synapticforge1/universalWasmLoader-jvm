# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-10-26):** **released 0.2.0** — published on the Central Portal by the owner (reported
2026-10-26), from tag `v0.2.0` = `6812998` (Publish run 40406408422, green end to end; GitHub Release
`v0.2.0`). 0.2.0 carries SPEC §10 (`1e1ecb8`; the `_initialize` behaviour change is its Breaking
Changes row) and the workspace-conformance merge `894db5e`. Gate: 27 / 27 tests. ⚠️ At 17:46 -04:00
the same day `repo1.maven.org` still served only 0.1.2 (0.2.0 files 404) — mirror sync lag.
**0.2.1 tagged and uploaded** (owner: "push the fix as release 0.2.1"; gated bump, floor patch
against `v0.2.0`, its 27 tests green; 31 / 31): tag `v0.2.1` = `ec58eb2`; CI run 40408685419 and
Publish run 40408929155 green end to end. It carries the `wasmImport` `.wasm` HTTP-error fix and the
publishing plugin 0.37.0 with automatic publishing — the first release meant to publish itself.
**0.3.0 tagged and uploaded:** tag `v0.3.0` = `33eb176`; CI run 40410899982 and Publish run
40411608731 green end to end; publishes itself (automatic publishing). **0.2.1 IS PUBLIC** on
`repo1.maven.org` (metadata `latest=0.2.1`, POM 200, read 18:24 -04:00) — automatic publishing
works. **0.2.0 is NOT** (POM 404 at the same read, although 0.2.1 uploaded after it is live).
Bumped through the gate (owner: "yes 0.3.0"): floor patch against `v0.2.1` (API
unchanged), its 31 tests green; a minor because both changes are behaviour changes with Breaking
Changes rows (stamped `0.3.0`): raw mode (no `.wit`) typed by the export's wasm signature (float
results were bit patterns, bad args silently `0`; raw calls measured faster, not slower) and
`wasmImport` timeouts (10 s connect, 30 s response). 40 / 40.

## Owner actions (🗓️)

- 🔴 **0.2.0 is missing from Maven Central** while 0.2.1 (uploaded later) is live (18:24 -04:00):
  the 0.2.0 deployment most likely never reached PUBLISHED. Check its state on the Deployments
  page; if it is VALIDATED, Publish it, if FAILED or dropped, 0.2.0 stays a git tag only (0.2.1 and
  0.3.0 supersede it; a version on Central cannot be re-uploaded anyway). Then confirm 0.3.0 appears.
  Earlier record: the owner sees 0.2.0 published on the Portal
  (`https://central.sonatype.com/publishing/deployments`, login required — not readable from here).
  Measured on `repo1.maven.org` the same day at 17:46, 18:03 and 18:12 -04:00: the 0.2.0 and 0.2.1
  POMs 404, the metadata lists only 0.1.2; Central's search index lists only 0.1.2 (17:46). The June
  release took "15 min – a few hours" to appear; re-check, and if still absent the next day, read the
  two deployments' state (PUBLISHING / PUBLISHED / FAILED) on that page. 0.2.1 is the first test of
  automatic publishing.

## Backlog

- 🗓️ **The `.wit` fallback masks server errors — a SPEC question for all the ports, not a -jvm bug.**
  SPEC 3.0.0 §63 REQUIRES a `.wit` that "cannot be fetched (e.g. 404 or network error)" to fall back
  to raw exports, so a 500 or a timeout silently loads without types (★ 4.3). -jvm conforms (tested).
  -dotnet and -py raised the same (workspace C16, C17); changing it is a SPEC change for every port.
  (The `.wasm` half was a real bug, fixed 2026-10-26 — [overview.md](overview.md) § Public API.)

## Workspace ledger (answers WRITTEN 2026-10-26; the workspace verifies and closes)

Rows in `../../../cmem/workspace-correspondence.md` naming this repo, answered by the 2026-10-26
conformance work (each row's cell names the commit; C18 carries the notes): H6 (gate, ★ block, memory layout), H7 / C11 (fixtures), H15 (`bump.sh` →
`.main.kts`, proved), H16 (`maintenance.strategy gc`, per clone, done), H17 / H25 §1 (wasmtk links),
H20 (publishing path recorded), H22 / H25 §3(d) (path test), H24 / H27 / C8 (★ block, first copy),
H25 §3(b) (`.claude/` untracked), H28 (address in POM + commits only). Also drift L2 (pointer cut),
L3 (fixtures), L6 (README claims, SPEC copy dropped), X3 (`deno task bump` line gone); divergence S4
(one SPEC text, linked).
