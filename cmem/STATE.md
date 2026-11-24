# STATE: universalWasmLoader-jvm

Updated: 2026-11-24, at the end of the session

## Version
- Released: 0.4.3 on 2026-11-10 (Maven Central `io.github.jrmarcum:universal-wasm-loader-jvm`)
- Working branch: memory-guide-2026-11-24 (not merged, not pushed)

## Now
- The memory guide (H38) applied: STATE, a router INDEX, an untracked CLAUDE.md, an archive, the size check.
- The review standard (H37): slot file, gitignored issues file, privacy gate, review gate; CI only publishes.
- Tier 1 (H40): every OS call in `Host.kt`, held by a gate, with an on-demand self-check.
- Scratch rule 15 (H43) recorded; a test temp-folder leak fixed.

## Review
- Run for: none in progress (the first review under the final standard is due before the next release)
- Position: not started
- Open issues: 0 findings; 1 candidate noted for the first review
- Platforms passed: win-x64

## Next
1. Answer the workspace letters (H37, H38, H40, H41, H42, H43; C49 waits on loader-js).
2. On "push": merge to `main`; the next push runs only the `version` job.
3. Before the next release: "review" (the five classes), then the full suite on each platform with a machine.
4. C49: copy loader-js's re-stamped `fixtures.json` once it lands.

## Waiting on owner
- The GitHub Support request for the purged commits (H19).
- The two test machines (H37); until then every platform but win-x64 is pending.

## Read next
- [open-work.md](open-work.md): the open items, by ID
- [review-process.md](review-process.md): before the first review
