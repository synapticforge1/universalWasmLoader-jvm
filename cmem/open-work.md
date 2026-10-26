# open-work.md — the one list of what is outstanding

Only open items; each points at its record. The status line lives here and nowhere else.

**Status (2026-10-26, 18:29 -04:00):** **0.3.0 released** (tag `v0.3.0` = `33eb176`, Publish run
40411608731 green, published automatically). `main` = `origin/main`; nothing unreleased in `src/`.
Gate: 40 / 40 tests, API snapshot unchanged since 0.1.2. On `repo1.maven.org`: 0.1.2 and 0.2.1 live;
0.2.0 (published by hand ~18:25) and 0.3.0 still syncing (POMs 404 at 18:29). Every release, its
contents and run: [overview.md](overview.md) § Release history.

## Owner actions (🗓️)

- **Confirm 0.2.0 and 0.3.0 appear** in
  `https://repo1.maven.org/maven2/io/github/jrmarcum/universal-wasm-loader-jvm/maven-metadata.xml`
  (`<latest>0.3.0</latest>`). The June release took 15 min – a few hours. If either is still absent
  the next day, read its state on `https://central.sonatype.com/publishing/deployments` (login
  required). No release from 0.2.1 on needs the Publish click.

## Backlog

- 🗓️ **The `.wit` fallback masks server errors — a SPEC question for all the ports, not a -jvm bug.**
  SPEC 3.0.0 §63 REQUIRES a `.wit` that "cannot be fetched (e.g. 404 or network error)" to fall back
  to raw exports, so a 500 or a timeout silently loads without types (★ 4.3). -jvm conforms (tested;
  the owner agrees with the fallback for a `.wit` that times out). -dotnet and -py raised the same
  (workspace C16, C17); changing it is a SPEC change for every port.
- 🟡 **A server that sends headers and then stalls the `.wasm` BODY** is not covered by the 30 s
  response timeout (the JDK request timeout ends when the response starts) — not tested. A body
  timeout would need a bounded read; decide whether it is worth it.
- 🟡 **The WIT call path looks its export up linearly** (`witDoc.exports.find` on every call, 77–124
  ns/call measured): the raw path's per-export cache would apply there too. Not slow today; only if
  a profile ever shows it.
- 🟡 **README third-party note** (optional, 2026-10-26 licence check): name the Gradle wrapper's
  Apache-2.0 licence and record where `math.wasm` came from (added in `09bf5c0`, provenance not
  recorded; almost certainly the owner's wasmtk). The repo complies without it.

## Workspace ledger (answers WRITTEN 2026-10-26; the workspace verifies and closes)

Rows in `../../../cmem/workspace-correspondence.md` naming this repo, answered by the 2026-10-26
conformance work (each row's cell names the commit; C18 carries the notes and the releases): H6
(gate, ★ block, memory layout), H7 / C11 (fixtures), H15 (`bump.sh` → `.main.kts`, proved), H16
(`maintenance.strategy gc`, per clone), H17 / H25 §1 (wasmtk links), H20 (publishing path), H22 /
H25 §3(d) (path test), H24 / H27 / C8 (★ block, first copy), H25 §3(b) (`.claude/` untracked), H28
(address in POM + commits only). Also drift L2, L3, L6, X3; divergence S4.
