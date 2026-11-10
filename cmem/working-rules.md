# working-rules.md — how an increment is done in this repo

Repo-specific rules. The workspace floor (`../../../cmem/working-rules.md`) applies underneath;
where this file says nothing, the floor governs. Recorded 2026-10-26 (workspace letters H6, H15,
H22, H28).

## Before starting

- **Read the workspace ledger first** (🔒 owner, 2026-10-26): `../../../cmem/workspace-correspondence.md`,
  every row whose "to" names this repo (or `universalWasmLoader-*`, "the loaders", "every repo") and
  every row whose subject touches what you will change; then the letter it points to in
  `../../../cmem/handoffs.md`. An open item that the new work would contradict is handled, or the
  conflict written into its row, first. Answer in this repo's `cmem/`, then write the commit into the
  row's "full text" cell. Never flip a row's status; its creator closes it.

## Git

- **Branch → gate → `merge --no-ff` to `main`** (the workspace floor for a repo with no rule of its
  own; adopted 2026-10-26). Docs and memory land in the same commit as the code they describe.
- **Commit messages go through a file**: `git commit -F <file>`, the file written with `Write`. Never a
  heredoc, never a double-quoted `-m`.
- **Nothing is pushed, tagged or published unless the owner says so.** Since 2026-11-10 (workspace
  H33) nothing is tagged from this machine: `./gradlew release` pushes `main` only, and CI checks,
  tags, makes the page and publishes ([release.md](release.md)). ⚠️ **The version line in
  `build.gradle.kts` ARMS a release**: any push of `main` that carries a version with no tag yet
  is released by CI. So the version line moves only through `bump`, as the last commit before
  `./gradlew release`.
- **The version line in `build.gradle.kts` stays at the released version** until a gated bump
  (`./gradlew bump`); never bump in the change that merges.

## The gate

- **Local gate = CI gate**: `./gradlew check` (the tests AND `checkLegacyAbi`, the `api/` snapshot
  check — wired into `check` in `build.gradle.kts`, because KGP 2.2 does not hook it by itself) plus
  `kotlin scripts/compat.main.kts selftest`. Read the steps from `.github/workflows/*.yml`.
- **Before a release**: `kotlin scripts/compat.main.kts report` (floor + the previous tag's tests);
  the bump itself runs the same gate and refuses below the floor. Design: [compat-gate.md](compat-gate.md).
- **A public API change re-baselines in its own commit**: `./gradlew updateLegacyAbi`, with the reason.
- **Read the counts, not the exit code**: `build/test-results/test/*.xml` (`tests`, `failures`,
  `errors`). 62 tests on 2026-11-08.
- **Invert every new test or gate** before trusting it.

## Tooling (workspace Tooling rule 1, 🔒 owner 2026-10-22)

- **Committed scripts are Kotlin `.main.kts`**, run with `kotlin <file>.main.kts` (the cached `.jar`
  it compiles is allowed: "Java and Kotlin can use the jar files"). No committed bash, PowerShell or
  `.bat` scripts; `gradlew` / `gradlew.bat` are Gradle's generated wrapper, not scripts. Committed
  scripts: `scripts/compat.main.kts` (it replaced `scripts/bump.sh` after the output-diff proof in
  [compat-gate.md](compat-gate.md)) and `scripts/github-release.main.kts` (2026-11-09, H30).
- 🔒 **No GitHub CLI, in CI too** (owner, 2026-11-08; workspace Tooling rule 9, letter H30). Check the
  repo instead: `git ls-remote`, the repo's files, Maven Central's `repo1` metadata, GitHub's
  public REST API. A web-UI-only task (a Support request, a Release page by hand) is an owner item.
  In a workflow, GitHub's REST API with `GITHUB_TOKEN`, from this repo's own mechanism (Kotlin,
  JDK classes only, as -dart used Dart and -dotnet `.csx`), not a Bun step. Re-grep:
  `git grep -n -I -w gh -- . ':!cmem' ':!*.md'` → 0 hits (`-I`: the wrapper jar is binary and
  matches by chance).
- 🔒 **Bun-version notations are the latest installed Bun** (owner, 2026-11-09; workspace Tooling
  rule 10, letter H31). This repo names no Bun version and runs no Bun, in CI or in a committed
  script (H31 measured "No Bun version written: … loader-jvm"; H30's change was made in Kotlin, so
  it added none). One-time probes run with Bun leave nothing behind. Nothing to move; if a Bun pin
  is ever added, it is the installed version.
- 🔒 **Every new tag gets its GitHub Release page from CI, never by hand** (owner, 2026-11-09;
  workspace letter H32, row C41). Here that is `publish.yml`'s `release` job
  ([overview.md](overview.md) § Build and release). Only NEW tags get pages: v0.1.0–v0.1.2 have
  none and keep none (owner: "only new tags will get release pages"). Measured 2026-11-09: 8 tags,
  5 pages, all by `github-actions[bot]`.
- **`git fetch` only in this repo** (workspace Tooling rule 9). To read a sibling's file (e.g.
  -js's `SPEC.md`), use `git -C <sibling> show origin/main:<file>` on what is already fetched, or
  GitHub's raw file over HTTPS; never fetch in the sibling. (Broken once on 2026-11-09; it moved
  only loader-js's `origin/*` tracking refs.)
- **No `bun -e "…"` either** (workspace Tooling rule 2: an inline script is a heredoc). A one-time
  probe is a file in the scratchpad, deleted after use.
- A one-time probe deleted after use may be a shell script, still written with `Write`.
- **No heredocs, ever**, for any content. File content goes through `Write` / `Edit`.
- **Line endings are LF**: `.gitattributes` is wildcard-first (`* text=auto eol=lf`).
- **Never run a formatter on `cmem/`.**

## No secrets, no personal information (🔒 owner, 2026-10-22 / 23 / 26; full rule: workspace § Records)

- **Never in a committed file, a commit message, a published package or an issue**: tokens,
  passwords, private or signing keys, `.env` contents; email addresses, machine names, private
  account names, and **personal drive paths**.
- **The path test is "is it machine-specific?", not "is it absolute?"** (owner, 2026-10-23). Not
  public: anything that records the owner's machine layout — a user profile (`C:\Users\<name>\…`),
  the project tree (`D:\Programs\…`), other drives, scoop / AppData installs, scratchpad or session
  ids. Fine: repo- or sibling-relative paths, environment variables, placeholders (`<repo-root>`),
  and a CONVENTION path that names nothing about one machine. A scan hit is a candidate, judged by
  that test.
- **The one exception, the maintainer contact address** (owner, 2026-10-23 and 2026-10-26): it may
  appear in the registry manifest field Maven Central reads (the POM `<developers>` entry in
  `build.gradle.kts`), as the git commit author/committer address, and in ONE committed `cmem/` note
  that names the maintainer contact. **Here it is in the POM field and on commits only**; no `cmem/`
  note carries it (the GPG key uid line in `overview.md` was reworded on 2026-10-26 for this).
- **Per-machine tool settings are never tracked**: `.claude/` is gitignored (untracked 2026-10-26,
  letter H25 §3(b)). Private machine facts go in the gitignored `cmem/local/`.
- **Scan before committing**, text AND binaries (`-a`; committed `.wasm` can carry DWARF paths):
  `git grep -a -n -i -E '[A-Za-z]:[\\/]+(Programs|Users|scoop|tools)[\\/]|/[a-z]/(Programs|Users)/'`.
  A secret that reached a remote is rotated, not just deleted.
- **History: purged 2026-11-08 (H19; 🔒 owner: "perform the H19 purge").** A tracked Gradle cache
  binary (`.gradle/…/executionHistory.bin`) held the owner's project path and user profile in the
  first 15 commits, including the trees of tags `v0.1.0` / `v0.1.1`. The workspace's H25 scan read
  ref tips only and missed it. `.gradle/` and `build/` were removed from every commit, and every id
  after the root changed. Record and old → new map: [history-map-2026-11-08.md](history-map-2026-11-08.md).
  **Scan every commit, not the tips:** `git grep -a … $(git rev-list --all)`. The commit address is
  allowed.

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
