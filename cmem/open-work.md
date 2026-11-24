# open-work.md — the open items, and only those

Last verified: 2026-11-24. Each item has a stable ID; a closed item moves to [archive/](archive/), ID kept.
Where the project stands is in [STATE.md](STATE.md); releases are in [release.md](release.md).

## Waiting on the owner

- **O1 · H19: the GitHub Support request** so that the purged commits are no longer served by hash
  ([archive/history-map-2026-11-08.md](archive/history-map-2026-11-08.md) § Still open).
- **O3 · The two test machines** (an Intel Mac with Parallels 26, an arm64 Mac with Parallels 27): until they
  exist, every platform but `win-x64` is `pending` ([platforms.md](platforms.md)).

## Before the next release

- **O4 · The first review under the final standard** (letter H37, [review-process.md](review-process.md)):
  "review" runs the five classes; `./gradlew release` refuses until `cmem/review-issues.md` holds a
  `- Review complete: <commit>` line for a commit that the release's code has not changed since.
- **O5 · C49: the new fixtures stamp.** wasmtk rewrote its history (2026-11-20); the stamp
  `4a5c3cf668da…` becomes `5ea819768c21…`. loader-js re-stamps first; this repo then copies its
  `tests/fixtures.json` and checks the bytes. Read 2026-11-24: loader-js still has the old stamp.

## Backlog

- **O6 · U10 as a user** is done by hand after a release: a one-time `.main.kts` resolving the version from
  repo1 and loading fixtures (done for 0.4.3). No committed script.
- **O7 · Under Gradle's `-q`, `./gradlew release` prints none of the script's lines** (measured 2026-11-10).
  Run it without `-q`, or call `kotlin scripts/release.main.kts`, to see the guards.
- **O8 · README third-party note** (optional): name the Gradle wrapper's Apache-2.0 licence and record where
  `math.wasm` came from (added in `09bf5c0`; almost certainly the owner's wasmtk). The licensing review (O4)
  will judge it.
- **O9 · A stranded upload (C34 (1), by analogy).** The page comes before the publish, and the publish skips
  a version `repo1` already serves, so a re-run after a later failure goes to the read-back. Still open: an
  upload Central holds but has not synced yet would be sent again (Central refuses the duplicate); the
  60-minute read-back poll keeps that window small.
