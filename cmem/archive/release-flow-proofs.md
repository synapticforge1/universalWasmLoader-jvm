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

- **Rehearsed end to end** in a scratch clone whose `origin` was a scratch bare repository holding the branch
  as `main` and every tag. A `- Review complete: <HEAD>` line was written to the clone's gitignored
  `cmem/review-issues.md`; `bump patch` passed the gate (floor patch; v0.4.3's 62 tests green) and committed
  `bump to v0.4.4`, the version line only.
- **`release --dry-run`:** every guard and every gate passed — the review gate, clean tree, fast-forward,
  new tag, Central 404, `clean check`, compat self-test and release mode, `check-host`, `check-privacy`,
  `check-cmem` (the held ★ line accepted and named), the four self-tests.
- **Refused, as designed:** no review line (measured on the real repo); a code commit after the reviewed
  commit ("changed since the review … Callbacks.kt").
- **The real run** pushed `main` to the scratch origin and read it back; no tag appeared there.
- **The workflow** (`version` → `tag` → `release` → `publish`) parsed with Bun.YAML: permissions as designed,
  0 `uses:`, no test step, the secrets in one step. The `version` job's shell logic ran in Git Bash: 0.4.3
  found; two version lines and none refused; 0.4.3 not new, 9.9.9 new.
- **Measured on GitHub, the non-release path:** the push of merge `a2e1f10` (2026-11-24) ran run 45508441477 —
  `version` green (the main-only guard, the token-less clone, "Is this a release?"), and `tag` / `release` /
  `publish` skipped; GitHub's tags unchanged (10). The release path is measured at the next release.
