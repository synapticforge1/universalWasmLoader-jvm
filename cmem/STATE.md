# STATE: universalWasmLoader-jvm

Updated: 2026-11-24, at the end of the session

## Version
- Released: 0.4.3 on 2026-11-10 (Maven Central `io.github.jrmarcum:universal-wasm-loader-jvm`)
- Working branch: main (memory-guide-2026-11-24 merged and pushed on 2026-11-24, owner: "push")

## Now
- The memory guide (H38) applied: STATE, a router INDEX, an untracked CLAUDE.md, an archive, the size check.
- The review standard (H37): slot file, gitignored issues file, privacy gate, review gate; CI only publishes.
- Tier 1 (H40): every OS call in `Host.kt`, held by a gate, with an on-demand self-check.
- Answers written to the workspace letters H37, H38, H40, H41, H42, H43 and C49 (C49 waits on loader-js).

## Review
- Run for: none in progress (the first review under the final standard is due before the next release)
- Position: not started
- Open issues: 0 findings; 1 candidate noted for the first review
- Platforms passed: win-x64

## Next
1. Read the first run of the CI-only workflow on GitHub: the `version` job only (0.4.3 is tagged).
2. Before the next release: "review" (the five classes), then the full suite on each platform with a machine.
3. C49: copy loader-js's re-stamped `fixtures.json` once it lands (open-work O5).

## Waiting on owner
- The GitHub Support request for the purged commits (H19); the two test machines (H37).

## Read next
- [open-work.md](open-work.md): the open items, by ID
- [review-process.md](review-process.md): before the first review
