# check-cmem-port-2026-11-24.md — the proof that `scripts/check-cmem.main.kts` equals the TS original

Closed 2026-11-24 (letter H38, step 7). Read only when the port or the original changes.

- **Why a port:** the memory guide says to save the workspace's `tools/check-cmem.ts` as
  `scripts/check-cmem.ts` and run it with Bun. This repo's committed scripts are Kotlin `.main.kts` and it runs
  no Bun (workspace Tooling rule 1; letter H31). Rule 1 allows a port once an output-diff proves the two equal.
- **What was compared:** for each tree, both tools in full mode and with `--summary` — stdout and exit code
  byte for byte (CRLF normalised); for a missing folder, stderr and exit code. The original: the workspace's
  `tools/check-cmem.ts` at its commit `03690b0`, run with Bun 1.4.4. The port: `kotlin` 2.4.21 on JDK 25.
- **Trees:** ten built samples and four real trees. The samples cover a pass; both index files missing; INDEX
  at 3,073 and 3,071 bytes; STATE at 71 lines with no headings, and at 61 lines; a 28 KB note; lines of 200
  and 201 characters, inside and outside fences (one fence indented); CRLF with `é` (2 bytes), `𝔘` (a
  surrogate pair) and `✓` (3 bytes); `archive/` and `local/` skipped; a `.txt` ignored; mixed-case names
  (`Zed`, `alpha`, `Beta/`, `beta/`) for the sort order; nested folders. The real trees were this repo's
  `cmem/` (before the migration), the workspace's `cmem/`, and loader-js's and loader-go's `cmem/`, read in
  place.
- **Result:** 29 of 29 the same (every rule, both modes, the missing folder).
- **Inverted:** a port mutant with the line limit at 201 gave 8 differences on the samples.
- **The harness** was a one-time Bun script in the session scratchpad, deleted after use (Tooling rule 15).
- **When the TS original changes,** port the change, re-run the same comparison, and replace this record.
