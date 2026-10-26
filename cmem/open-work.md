# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-10-26):** released **0.1.2** (Maven Central, 2026-06-24). Unreleased on `main`: SPEC
§10 (`1e1ecb8`, a behaviour change — README Breaking Changes `_next release_`) and the 2026-10-26
workspace-conformance work (compat gate, stamped fixtures, memory). Gate: 27 / 27 tests, floor
**patch** against `v0.1.2`, previous-tag tests 24 / 24.

## Owner actions (🗓️)

- **Next release and its number.** The gate allows a patch (the API did not change), but the
  `_initialize` behaviour change has a Breaking Changes row — loader-dart shipped the same change as a
  minor (0.2.0). The bump level is the owner's call; `./gradlew bump -Pkind=minor` would give 0.2.0.
- **Central Portal auto-publish**: each release still needs the manual Publish click unless
  `automaticRelease = true` is set ([overview.md](overview.md) § Build and release).

## Backlog

- 🔴 **`wasmImport` masks HTTP errors** (`UniversalWasmLoader.kt` `loadFromUrl`, read 2026-10-26, not
  run): the `.wasm` fetch never checks the status, so a 404/500 error page is handed to Chicory and
  surfaces as a module parse error; the `.wit` fetch turns any non-200 or exception into "no `.wit`",
  so a 500 / timeout silently falls back to raw mode (★ 4.3 silent-wrong). loader-js fixed its `.wasm`
  half in `64551fa`; loader-dotnet logged the `.wit` half (workspace C14, C16). Fix both with a test
  per path; decide the `.wit` rule against SPEC (404 = absent, anything else = error?).
- **CI half of the gate UNMEASURED** until the next push / tag runs on GitHub ([compat-gate.md](compat-gate.md)).

## Workspace ledger (answers to write after the commit lands)

Rows in `../../../cmem/workspace-correspondence.md` naming this repo, answered by the 2026-10-26
conformance work: H6 (gate, ★ block, memory layout), H7 / C11 (fixtures), H15 (`bump.sh` →
`.main.kts`, proved), H16 (`maintenance.strategy gc`, per clone, done), H17 / H25 §1 (wasmtk links),
H20 (publishing path recorded), H22 / H25 §3(d) (path test), H24 / H27 / C8 (★ block, first copy),
H25 §3(b) (`.claude/` untracked), H28 (address in POM + commits only). Also drift L2 (pointer cut),
L3 (fixtures), L6 (README claims, SPEC copy dropped), X3 (`deno task bump` line gone); divergence S4
(one SPEC text, linked).
