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
**Unreleased on `main` since 0.2.1:** raw mode (no `.wit`) typed by the export's wasm signature
(float results were bit patterns, bad args silently `0`); a behaviour change with a `_next release_`
Breaking Changes row → **0.3.0** when the owner says. 37 / 37; raw calls measured faster, not slower.

## Owner actions (🗓️)

- **Is anything newer than 0.1.2 public?** The owner reported 0.2.0 published. Measured on
  `repo1.maven.org` the same day at 17:46 and 18:03 -04:00: the 0.2.0 AND 0.2.1 POMs 404, the
  metadata lists only 0.1.2; Central's search index lists only 0.1.2 (17:46). Check both deployments
  on central.sonatype.com → Deployments: PUBLISHING (still syncing), PUBLISHED, or FAILED? 0.2.1 is
  the test of automatic publishing.

## Backlog

- 🗓️ **The `.wit` fallback masks server errors — a SPEC question for all the ports, not a -jvm bug.**
  SPEC 3.0.0 §63 REQUIRES a `.wit` that "cannot be fetched (e.g. 404 or network error)" to fall back
  to raw exports, so a 500 or a timeout silently loads without types (★ 4.3). -jvm conforms (tested).
  -dotnet and -py raised the same (workspace C16, C17); changing it is a SPEC change for every port.
  (The `.wasm` half was a real bug, fixed 2026-10-26 — [overview.md](overview.md) § Public API.)
- 🟡 **`wasmImport` has no timeout** (read 2026-10-26, not run): `HttpClient.newHttpClient()` sets no
  connect timeout and the requests set none, so a server that accepts the connection and never
  answers makes `wasmImport` wait forever — on the `.wasm` and on the `.wit` alike. A timeout (and
  what it should be) is a decision; with one, a `.wit` timeout would fall back under SPEC §63.

## Workspace ledger (answers WRITTEN 2026-10-26; the workspace verifies and closes)

Rows in `../../../cmem/workspace-correspondence.md` naming this repo, answered by the 2026-10-26
conformance work (each row's cell names the commit; C18 carries the notes): H6 (gate, ★ block, memory layout), H7 / C11 (fixtures), H15 (`bump.sh` →
`.main.kts`, proved), H16 (`maintenance.strategy gc`, per clone, done), H17 / H25 §1 (wasmtk links),
H20 (publishing path recorded), H22 / H25 §3(d) (path test), H24 / H27 / C8 (★ block, first copy),
H25 §3(b) (`.claude/` untracked), H28 (address in POM + commits only). Also drift L2 (pointer cut),
L3 (fixtures), L6 (README claims, SPEC copy dropped), X3 (`deno task bump` line gone); divergence S4
(one SPEC text, linked).
