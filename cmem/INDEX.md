# cmem — Portable Project Memory for universalWasmLoader-jvm

This folder is the **authoritative, portable project memory** for `universalWasmLoader-jvm`. It is
committed and travels with the repo; `cmem/local/` (gitignored) holds machine facts only. The
workspace floor (`../../../cmem/`) applies underneath; this repo's files govern inside it.

## Policy

- **`cmem/` is the single home for ALL project memory.** `CLAUDE.md` at the root is a pointer only.
- **One line per file below; no status narrative here.** State lives once, in [open-work.md](open-work.md).
- **Working memory, not an archive; git is the archive.** Completed work is summarized with its
  commit and a `git show` pointer to the text it replaces.
- **A number is a claim with a date.** Re-derive before quoting or acting on it.
- **`README.md` is NOT memory.** It is the public doc; internal decision logs stay here.
- **No secrets or personal information in any committed file** — [working-rules.md](working-rules.md).
- **Never run a formatter on `cmem/`.**

## Triggers (binding on every agent)

1. **"update the project memory"** (or "update memory", "record this") — revise the matching topic
   file and its line below (absolute dates; update, don't duplicate), then AUDIT: grep the old values
   across `cmem/`, `README.md` and `CLAUDE.md`, classify each hit live or dated, measure when two
   disagree. Sync the README only where the change is user-relevant.
2. **"look for code issues"** (or "code audit") — audit tested AND untested paths for workarounds,
   dead code (grep-verified), bugs (ABI marshalling, pointer/length math, endianness, missing
   `cabi_post` calls) and silent fall-throughs (a default returned instead of an error — the worst
   class). Report `file:line` + severity, fix the safe ones, keep `./gradlew check` green.
3. **Correspondence before new work** (🔒 owner, 2026-10-26) — read the workspace ledger first
   ([working-rules.md](working-rules.md) § Before starting).
4. **Regression gate** — a change to `src/` runs the full gate (`./gradlew check`), reading the counts.

## Files

| File | What it holds |
| --- | --- |
| [open-work.md](open-work.md) | The status line and the ONE list of open items: owner calls, backlog, ledger answers pending |
| [overview.md](overview.md) | What the port is and why: runtime, coordinates (🔒 groupId stays), design principles, API, ABI, SPEC §10, tests and fixtures, build / release / publishing path, credentials |
| [working-rules.md](working-rules.md) | How an increment is done here: ledger first, git, the gate, `.main.kts` scripts, the privacy rule and its path test |
| [compat-gate.md](compat-gate.md) | The SemVer compatibility gate: API snapshot, floor, previous-tag tests, Breaking row; the `bump.sh` port proof; the inversion; traps |
| [best-practices.md](best-practices.md) | This repo's own rules (§ L) and the workspace's 28 ★ rules, copied verbatim under a dated stamp |
