# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-10-26):** **released 0.2.0** — published on the Central Portal by the owner (reported
2026-10-26), from tag `v0.2.0` = `6812998` (Publish run 40406408422, green end to end; GitHub Release
`v0.2.0`). 0.2.0 carries SPEC §10 (`1e1ecb8`; the `_initialize` behaviour change is its Breaking
Changes row) and the workspace-conformance merge `894db5e`. Gate: 27 / 27 tests. ⚠️ At 17:46 -04:00
the same day `repo1.maven.org` still served only 0.1.2 (0.2.0 files 404) — mirror sync lag.
**Unreleased on `main` since 0.2.0:** the `wasmImport` `.wasm` HTTP-error fix (a bug fix; gate floor
patch against `v0.2.0`, its 27 tests green against the fix; 31 / 31); the publishing plugin 0.37.0
(no deprecation warning left) with automatic publishing → 0.2.1 (owner, 2026-10-26).

## Owner actions (🗓️)

- **Confirm the mirror** shows 0.2.0:
  `https://repo1.maven.org/maven2/io/github/jrmarcum/universal-wasm-loader-jvm/maven-metadata.xml`
  (`<latest>0.2.0</latest>`); the README install lines already say 0.2.0.

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
