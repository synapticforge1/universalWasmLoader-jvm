# release-flow-proofs.md — how each change to the release flow was proven before it landed

Moved from release.md on 2026-11-24 (memory guide: a rule file states the rule; its history is archived).

## 2026-11-10 — checks, then the CI tag, then the page, then the publish (letter H33)

- **The local half, rehearsed end to end.** A scratch clone used a scratch bare repository as `origin`.
  `bump patch` passed the gate (floor patch; v0.4.2's 62 tests green) and committed `bump to v0.4.3`, the
  version line only, leaving a clean tree. `release --dry-run` passed every guard and the cold gate. The real
  run pushed `main` and read it back, and no tag appeared on the bare origin. A second run was refused.
- **Measured refusals:** `bump` with no level; `bump` on a dirty tree.
- **The CI half, read-only** (dry runs on the real API, no token): `tag v0.4.2` at its commit → nothing
  written; at 0.4.1's commit → refused; `tag v0.4.3` → would create; `release v0.4.3` before its tag →
  refused. The self-test had 29 checks; the mutant "an existing tag elsewhere may be overwritten" was caught.
- **One defect found by the rehearsal and fixed:** a script started by the `kotlin` launcher on Windows could
  not start another `kotlin` (the launcher's quoted `JAVA_OPTS` broke the nested `kotlin.bat`).
- **Measured on GitHub:** the non-release path, run 42909738486 on `8ec529d` (`checks` green, the rest
  skipped, no tag made); the release path, 0.4.3, run 42911973493, all four jobs green.

## 2026-11-24 — CI only publishes; the review set and every gate run locally (letter H37)

Recorded in [the 2026-11-24 entry of design notes](../overview.md) and the commit that made the change;
the rehearsal is listed there.
