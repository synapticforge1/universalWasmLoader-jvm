# history-map-2026-11-08.md — the H19 history purge: what was done, and every old → new id

🔒 Owner, 2026-11-08: "perform the H19 purge" (workspace letter H19; finding in row C36).
**Commit ids written in `cmem/` before 2026-11-08 are PRE-PURGE ids. Look them up here.**

## What was purged, and why

From the first commit `09bf5c0` until `f3fba2d` (2026-06-17, "Stop tracking generated .gradle/ and
build/"), the repo tracked Gradle's `.gradle/` cache (and in `v0.1.1`, also `build/`). One of those
files, `.gradle/9.5.1/executionHistory/executionHistory.bin`, holds the owner's project path and user
profile. The workspace's H25 scan (2026-10-26) read ref tips only and missed it. A blob-level
`git grep -a` over every commit (`git rev-list --all`) found it on 2026-11-08.

## How

- Backup first: a bundle of all 26 refs in the gitignored `cmem/local/backups/`.
- `git filter-branch --index-filter 'git rm -r -q --cached --ignore-unmatch .gradle build'
  --tag-name-filter cat -- --branches --tags ^a978fbc`. Index only, no checkout. The tags are
  lightweight. **The root `a978fbc` ("Initial commit", GitHub-signed, clean tree) is excluded, so it
  keeps its id and signature.** A first run without the exclusion stripped that signature; it was
  rolled back (all 25 refs restored and compared to the saved list) and re-run.
- Verified by a one-time Bun script, deleted after use. It paired old and new commits by their full
  header and message, without the tree and parent lines. Results: 58 of 58 paired, metadata
  identical, parents mapped. 43 trees are identical. The other 15 differ ONLY by deleted `.gradle/` /
  `build/` paths. Every ref moved to its mapped commit. No new commit tracks `.gradle/` or `build/`.
  The path scan over all new commits hits only `cmem/working-rules.md`'s rule text. Control: the
  same scan on the old `v0.1.0` / `v0.1.1` still finds `executionHistory.bin`.
- **Tagged trees:** `v0.1.0` and `v0.1.1` CHANGED (their cache files are gone: the owner-approved
  exception, as rsxtk's). `v0.1.2`, `v0.2.0`, `v0.2.1`, `v0.3.0` and `main` are byte-identical to
  before. None of the tags carried an immutable GitHub Release (API, 2026-11-08: only v0.2.0 / v0.2.1
  / v0.3.0 have releases, all `immutable: false`). 0 forks, 0 PRs.
- Maven Central artifacts are untouched: no published jar held `.gradle/`. Their POMs and the
  GitHub Releases name tags, not commit ids.

## Refs

| ref | old | new |
| --- | --- | --- |
| `v0.1.0` | `09bf5c0` | `322e1d9` |
| `v0.1.1` | `399e7ff` | `da2777a` |
| `v0.1.2` | `f3fba2d` | `19a3757` |
| `v0.2.0` (and `release/v0.2.0`) | `6812998` | `ef3846b` |
| `v0.2.1` (and `release/v0.2.1`) | `ec58eb2` | `5ce187e` |
| `v0.3.0` (and `release/v0.3.0`) | `33eb176` | `cf53474` |
| `main` before the purge | `497aa9c` | `5e6c48d` |
| `origin/main` before the purge | `a194253` | `bc8e722` |

## Every rewritten commit (56; the root `a978fbc` and its unchanged child keep their ids)

| old | new | date | subject |
| --- | --- | --- | --- |
| `09bf5c0ce5` | `322e1d9de0` | 2026-05-20 | create project module |
| `31a51293c9` | `0eb56da28e` | 2026-05-20 | project creation |
| `3560e303b5` | `a01601dba2` | 2026-05-20 | update version |
| `399e7ff0c9` | `da2777acf9` | 2026-05-20 | ci: commit gradle-wrapper.jar so CI can run gradlew |
| `3a6dd0b194` | `40abf94d68` | 2026-05-20 | ci: switch JDK distribution from temurin to zulu for JDK 25 availability |
| `540ddbb1c2` | `b1efaa10c8` | 2026-05-20 | build: add release task to tag and push current version |
| `58f9cb576f` | `b05f2c646e` | 2026-05-20 | update |
| `8597aa7379` | `a9eddfe441` | 2026-05-20 | ci: fix gradlew permissions and Node.js 24 for GitHub Actions |
| `951ee95c19` | `1f587518a4` | 2026-05-20 | update |
| `c053c01c9c` | `e21ad0b54e` | 2026-05-20 | build: replace project.exec with ProcessBuilder in release task (Gradle 9 compat) |
| `da32d2347c` | `9a0381e280` | 2026-05-20 | build: fix release task — use project.exec for git commands |
| `ec3ce70044` | `14d69ae6ab` | 2026-05-20 | update version |
| `64198de96b` | `31598aa6a7` | 2026-06-15 | Add portable cmem/ project memory + SPEC 3.0.0 conformance note |
| `a434c6d6e3` | `86e755bb6c` | 2026-06-15 | Align string returns to SPEC 3.0.0 (callee-allocated + cabi_post) |
| `dc4a1f97ae` | `7d8014fb8d` | 2026-06-15 | Add run:-only Maven Central publish workflow + version bump |
| `f3fba2da6f` | `19a37572fe` | 2026-06-17 | Stop tracking generated .gradle/ and build/ artifacts |
| `1c2777a6d4` | `1978853af8` | 2026-06-24 | Record first Maven Central release (0.1.2) + publish gotchas in cmem |
| `9f2c6d55ce` | `e8c12ffea4` | 2026-06-24 | Add workflow_dispatch trigger to publish workflow |
| `1a00c3025e` | `1e744837dd` | 2026-07-05 | docs(cmem): record SPEC §10 in overview.md (authoritative memory) |
| `1e1ecb8445` | `47c7813e71` | 2026-07-05 | feat(spec10): loader capabilities — call _initialize + minimal WASI shim |
| `0af04cbd86` | `3dbc95b5a1` | 2026-10-20 | update |
| `5f7fa8be6e` | `2d6777d989` | 2026-10-20 | update |
| `085360cbb9` | `c505d6b279` | 2026-10-26 | build: publishing plugin 0.37.0 (no Gradle deprecation left); publish automatically after the tag |
| `0fca5902a1` | `94c297b1aa` | 2026-10-26 | release: bump version to 0.2.0 (minor, through the compatibility gate) |
| `1077afe450` | `a2d48ddff6` | 2026-10-26 | fix: raw mode (no .wit) follows each export's wasm signature |
| `25657f8d3b` | `e6ccaf2edb` | 2026-10-26 | fix: wasmImport times out instead of hanging (10 s connect, 30 s response) |
| `25b64ab584` | `1617af8d3a` | 2026-10-26 | docs(cmem): record the 0.2.0 release run (awaiting the Publish click) |
| `2dbaae8d94` | `0b70663d9b` | 2026-10-26 | Merge record-0.2.0-published: 0.2.0 released |
| `2e8f634575` | `4a8541b8a5` | 2026-10-26 | release: bump version to 0.3.0 (minor, through the compatibility gate) |
| `33eb17658d` | `cf53474202` | 2026-10-26 | Merge release-0.3.0: version 0.3.0 |
| `462119d2eb` | `01a07aef59` | 2026-10-26 | fix: wasmImport reports an HTTP error on the .wasm instead of masking it |
| `4cd10f695c` | `ad152277f1` | 2026-10-26 | feat(release): SemVer compatibility gate; bump.sh ported to .main.kts (H6, H15) |
| `5d571be2cc` | `0cb0c5e574` | 2026-10-26 | Merge record-0.2.0-run: the 0.2.0 release run in memory |
| `5f4289118b` | `de308a446c` | 2026-10-26 | Merge fix-wasmimport-http-error: report an HTTP error on the .wasm |
| `6812998cdb` | `ef3846b794` | 2026-10-26 | Merge docs-dispatch-fix: workflow_dispatch is available from v0.2.0 on |
| `7da165839b` | `348cb7e352` | 2026-10-26 | chore: repo hygiene for the workspace floor (H25, H17, H16) |
| `85a64a0f07` | `58f82b64f0` | 2026-10-26 | docs(cmem): record the 0.2.1 release run; correct an unmeasured check time |
| `8660eacad2` | `fe91d4b3fd` | 2026-10-26 | Merge wasmimport-timeouts: 10 s connect, 30 s response |
| `894db5e9d5` | `01ef84f5b8` | 2026-10-26 | Merge workspace-conformance-2026-10-26: conformance to the workspace rules |
| `8ad7ab5d62` | `8869121791` | 2026-10-26 | Merge publish-plugin-0.37: no deprecation warnings; automatic publishing |
| `911c91117d` | `fc0cf243bb` | 2026-10-26 | Merge release-0.2.0: version 0.2.0 (bumped through the gate, not released) |
| `92c54aa033` | `61f8d583c2` | 2026-10-26 | docs: workflow_dispatch works only for v0.2.0 and later tags |
| `a194253388` | `bc8e722c12` | 2026-10-26 | docs(cmem): update the project memory after 0.3.0 |
| `a8f9168b70` | `a63ffa7a19` | 2026-10-26 | test: the stamped reference fixtures from universalWasmLoader-js (H7, C11) |
| `aee08cd6c5` | `ea119503ba` | 2026-10-26 | docs: 0.2.0 is published; install snippets point at it |
| `bd13ab84af` | `105630c518` | 2026-10-26 | release: bump version to 0.2.1 (patch, through the compatibility gate) |
| `c201052599` | `d2db3ec28e` | 2026-10-26 | Merge fix-raw-mode-types: raw mode typed by the export's wasm signature |
| `cf58e2d0ec` | `bf0cd53cb4` | 2026-10-26 | docs(cmem): record the 0.3.0 release run; 0.2.1 live, 0.2.0 missing |
| `dc8b111f01` | `a391699938` | 2026-10-26 | docs(cmem): memory conformance to the workspace policy (H6, H22, H24, H27, H28, C8, H20) |
| `ec58eb2b75` | `5ce187e60e` | 2026-10-26 | Merge release-0.2.1: version 0.2.1 |
| `ff36a5a140` | `a4242bb001` | 2026-10-26 | docs(cmem): 0.2.0 was waiting for the Publish click; the owner published it |
| `3444ca08b5` | `c1c6160e45` | 2026-11-08 | api: re-baseline for SPEC 4.0.0 (LoadOptions, wasmImport with options) |
| `497aa9cffa` | `5e6c48d38a` | 2026-11-08 | Merge c30-publish-split: three-job publish workflow (C30) |
| `5744af9727` | `ec7398c418` | 2026-11-08 | ci(publish): split into checks / publish / release jobs; pin every download (C30) |
| `cee76ba5d0` | `8b0db4f826` | 2026-11-08 | feat: SPEC 4.0.0 behaviour - only an absent .wit is raw, strict WIT, checked calls and callbacks, a time limit per fetch |
| `f9b1ce62f6` | `a8bade1dfe` | 2026-11-08 | Merge spec-4.0.0: SPEC 4.0.0 (workspace C29) |

## Still open (the owner's)

- **GitHub may still serve the old commits by hash** until its own GC. A full purge is a GitHub
  Support request naming the repo (0 forks, 0 PRs; check: the old `09bf5c0` commit URL returns 404).
