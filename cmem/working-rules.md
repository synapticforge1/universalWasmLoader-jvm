# working-rules.md — the standing rules of this repo

Last verified: 2026-11-24. The rules, in the present tense; how a rule changed is in [overview.md](overview.md)
and [archive/](archive/). The workspace floor (`../../../workspace/cmem/working-rules.md` and `tooling-rules.md`)
applies underneath; where this file says nothing, the floor governs.

## Session routine

- **Start:** read `cmem/STATE.md`, then `cmem/INDEX.md`, then the workspace correspondence (below), then only
  the topic files that STATE.md's "Read next" or the task names.
- **End:** rewrite `cmem/STATE.md`; move every item closed this session to `cmem/archive/`; record decisions
  (overview.md) and rules (this file); run `kotlin scripts/check-cmem.main.kts` and fix what it reports;
  empty the session scratchpad of what this session wrote (below).
- **A reviewer** gets its class brief and `cmem/STATE.md` only, and does not browse `cmem/`; any other
  sub-agent gets the task and the exact files the main agent picked from the index.

## Workspace correspondence (🔒 owner, 2026-10-26; letters H41, H42)

- **Before new work, read `../../../workspace/cmem/workspace-correspondence.md`**: one line per OPEN item. Open
  only the items whose "to" names this repo (or "the loaders", "every project") and the ones whose subject
  touches what you will change, in `../../../workspace/cmem/letters/<ID>.md`.
- **Answer by appending ONE bullet** under `## Answers` in the item's file:
  `- **loader-jvm, <date>:** <what was done, the commit, the check>`, every line under 200 characters. Never
  edit the index line or the status; only the creator closes. That bullet is the one write this repo makes
  outside itself; the workspace session commits it.
- **The workspace repository is PUBLIC:** an answer carries nothing this repo could not (no machine path,
  credential or personal fact).
- **Old references** ("handoffs.md H<n>", a ledger row) resolve by searching the workspace's `cmem/archive/`.
- An open item that new work would contradict is handled, or the conflict written into its answer, first.

## Binding triggers

1. **"update the project memory"** (or "update memory", "record this"): revise the matching topic file and its
   index line (absolute dates; update, do not duplicate), then AUDIT — grep the old values across `cmem/` and
   `README.md`, classify each hit live or dated, measure when two disagree. Sync the README only where the change
   is user-relevant.
2. **"look for code issues"** (or "code audit"): audit tested AND untested paths for workarounds, dead code
   (grep-verified), bugs (ABI marshalling, pointer / length math, endianness, missing `cabi_post` calls) and
   silent fall-throughs (a default returned instead of an error, the worst class). Report `file:line` and
   severity, fix the safe ones, keep `./gradlew check` green.
3. **A change to `src/`** runs the full local gate (below), reading the counts.

## Records

- **`cmem/` is the single home of project memory.** The root `CLAUDE.md` is untracked and points at
  `cmem/STATE.md` and `cmem/INDEX.md` only (letter H38). `README.md` is the public doc, not memory.
- **A number is a claim with a date.** Re-derive before quoting or acting on it.
- **A path in a `cmem/` note is relative to that note:** the workspace is `../../../workspace/cmem/` (from the
  repository root it is `../../workspace/cmem/`).
- **Commit ids written before 2026-11-08 are PRE-PURGE** (H19 rewrote every id after the root): look them up in
  [archive/history-map-2026-11-08.md](archive/history-map-2026-11-08.md); `git show` on an old id fails.
- **Limits (the size check):** `INDEX.md` ≤ 3 KB; `STATE.md` ≤ 60 lines with its four required headings; every
  other live note ≤ 25 KB; no line over 200 characters outside a code block. `archive/` and `local/` are
  unlimited. **One recorded exception:** the ★ copy in `best-practices.md` is held VERBATIM until the owner
  decides (letter H38), so its line 158 (201 characters) stays and the check reports it.
- **Open and closed never share a file:** a closed item moves to `cmem/archive/`, its ID kept.
- **Never run a formatter on `cmem/`.**

## Git

- **Branch → gate → `merge --no-ff` to `main`.** Docs and memory land in the same commit as the code.
- **Commit messages go through a file**: `git commit -F <file>`, the file written with `Write`. Never a
  heredoc, never a double-quoted `-m`.
- **Nothing is pushed, tagged or published unless the owner says so.** Nothing is tagged from this machine:
  `./gradlew release` pushes `main` only; CI tags, makes the page and publishes ([release.md](release.md)).
- ⚠️ **The version line in `build.gradle.kts` ARMS a release:** a push of `main` carrying a version with no tag
  yet is released by CI. It moves only through `./gradlew bump`, as the last commit before `./gradlew release`;
  never bump in the change that merges.
- **`git fetch` only in this repo** (Tooling rule 9). A sibling's file is read with
  `git -C <sibling> show origin/main:<file>` on what is already there, or from GitHub's raw file over HTTPS.

## The gate — every gate runs LOCALLY (letter H37)

- **The gates:** `./gradlew check` (the tests AND `checkLegacyAbi`, the `api/` snapshot — wired into `check` in
  `build.gradle.kts`); `kotlin scripts/compat.main.kts selftest` and `release` (the floor and the previous tag's
  tests); `kotlin scripts/github-release.main.kts selftest`; `kotlin scripts/check-host.main.kts` (H40);
  `kotlin scripts/check-privacy.main.kts`; `kotlin scripts/check-cmem.main.kts`. `./gradlew release` runs them
  all (`scripts/release.main.kts`).
- **CI only publishes** (owner, 2026-11-17): it verifies the version, tags, makes the page and publishes, and
  skips a version already published. No test step runs on GitHub.
- **Read the counts, not the exit code**: `build/test-results/test/*.xml` (`tests`, `failures`, `errors`).
- **A public API change re-baselines in its own commit**: `./gradlew updateLegacyAbi`, with the reason.
- **Invert every new test or gate** before trusting it.
- **Fixtures are the stamped set from universalWasmLoader-js**: never edit or regenerate them here.
- **Exports are looked up by camelCase** (`is-positive` → `isPositive`), never kebab or snake.
- **Every OS call is in `Host.kt`** (tier 1, letter H40): files, the classpath, the network, the environment,
  processes and standard streams. `check-host.main.kts` fails when any other source file calls those APIs.

## Tooling (workspace Tooling rule 1, 🔒 owner 2026-10-22)

- **Committed scripts are Kotlin `.main.kts`**, run with `kotlin <file>.main.kts`. No committed bash,
  PowerShell or `.bat`; `gradlew` / `gradlew.bat` are Gradle's wrapper. A port replaces a TS original only after
  an output-diff proves them equivalent (`check-cmem.main.kts`: [archive/](archive/check-cmem-port-2026-11-24.md)).
- 🔒 **No GitHub CLI, in CI too** (Tooling rule 9, letter H30). A workflow uses GitHub's REST API with
  `GITHUB_TOKEN` from a Kotlin script, JDK classes only. Re-grep: `git grep -n -I -w gh -- . ':!cmem' ':!*.md'`
  → 0 hits (`-I`: the wrapper jar matches by chance). A web-UI-only task is an owner item.
- 🔒 **Bun versions** (Tooling rule 10): this repo names and runs no Bun. If a pin is ever added, it is the
  installed version.
- 🔒 **Every new tag gets its Release page from CI, never by hand** (letter H32); only new tags get pages.
- **No heredocs, ever**, not `bun -e "…"` either (Tooling rule 2). File content goes through `Write` / `Edit`;
  a one-time probe is a file in the scratchpad, deleted after use.
- **Line endings are LF**: `.gitattributes` is wildcard-first (`* text=auto eol=lf`).

## Scratch space (🔒 owner, 2026-11-19; Tooling rule 15, letter H43)

- **Throwaway files go only in this session's scratchpad**, never another temp location or another session's
  folder. A scratch path is a machine fact: never in a tracked file.
- **Delete when the item is cleared:** a one-time script after its run; a clone, mutant or build output when
  its finding is verified; a reviewer's folder when its round is triaged. A sub-agent works under the main
  session's scratchpad, and the main session removes its folder.
- **Nothing lives only in scratch**, and no project fact lives in the assistant's per-project memory folder:
  it goes into `cmem/` (private facts into `cmem/local/`). That folder was checked empty on 2026-11-24.
- **Budget: 200 MB per session folder.** Announce anything larger before making it and remove it after.
- **"commit" ends with an empty scratchpad.** Other sessions' folders are never swept from here.

## No secrets, no personal information (🔒 owner, 2026-10-22 / 23 / 26)

- **Never in a committed file, a commit message, a published package or an issue**: tokens, passwords,
  private or signing keys, `.env` contents; email addresses, machine names, private account names, and
  **personal drive paths**.
- **The path test is "is it machine-specific?", not "is it absolute?"** Not public: anything that records the
  owner's machine layout — a user profile (`C:\Users\<name>\…`), the project tree (`D:\Programs\…`), other
  drives, scoop / AppData installs, scratchpad or session ids. Fine: repo- or sibling-relative paths,
  environment variables, placeholders (`<repo-root>`), and a convention path that names nothing about one machine.
- **The one exception, the maintainer contact address**: in the POM `<developers>` entry in `build.gradle.kts`
  and as the git commit author / committer address. No `cmem/` note carries it.
- **Never tracked:** `.claude/`, the root `CLAUDE.md`, `cmem/local/` and `cmem/review-issues.md`.
- **The privacy gate:** `kotlin scripts/check-privacy.main.kts` scans every tracked file, binaries included, for
  machine paths and addresses, and fails when a never-tracked file is tracked. A secret that reached a remote is
  rotated, not just deleted.
- **History:** purged 2026-11-08 (H19). Scan every commit, not the tips:
  `git grep -a … $(git rev-list --all)`.

## Owner commands: "commit", "push", "release" (owner, 2026-11-10)

🔒 Written into every project by the workspace session on the owner's direction (2026-11-10:
"please write that into each project"; workspace `cmem/working-rules.md` Tooling rule 12, letter
H34). When the owner says:

- **"commit"** — first run "update the project memory files" (re-derive this repo's `cmem/` from
  its current state), then commit to the branch being worked on. Nothing is pushed.
- **"push"** — perform "commit", then merge the branch into `main`, then push `main` (and the
  working branch) to GitHub (`origin`) and read it back with `git ls-remote`.
- **"release"** — perform "push", then the release process: the workspace's
  `cmem/release-process.md` §§ 5–8 — CI checks first, then CI pushes the tag, then the GitHub
  Release page from the tag, then the publish to the registry — recorded in `cmem/release.md`
  § "Release History" (newest first, a summary of what changed). The level (patch / minor /
  major) is the owner's: ask if it was not given.

Each command includes the one before it, and none goes further than its own word (a "commit"
never pushes, a "push" never releases). This repo's own gate and merge rules apply at each step.

**The seven review words** (🔒 owner, 2026-11-17; Tooling rule 13, letter H37): **"review"** runs all five
required classes in order — basic, hardening, licensing, security, privacy — after the optional efficiency
review; **"basic review"**, **"hardening review"**, **"licensing review"**, **"security review"**, **"privacy
review"** and **"efficiency review"** each run one class. How: [review-process.md](review-process.md).
