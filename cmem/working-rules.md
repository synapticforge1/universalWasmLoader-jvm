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
- **Nothing is pushed, tagged or published unless the owner says so.** `./gradlew release` tags AND
  force-pushes the tag, which publishes: it is an owner action.
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
  `errors`). 27 tests on 2026-10-26.
- **Invert every new test or gate** before trusting it.

## Tooling (workspace Tooling rule 1, 🔒 owner 2026-10-22)

- **Committed scripts are Kotlin `.main.kts`**, run with `kotlin <file>.main.kts` (the cached `.jar`
  it compiles is allowed: "Java and Kotlin can use the jar files"). No committed bash, PowerShell or
  `.bat` scripts; `gradlew` / `gradlew.bat` are Gradle's generated wrapper, not scripts. The one
  committed script is `scripts/compat.main.kts`; it replaced `scripts/bump.sh` after the output-diff
  proof in [compat-gate.md](compat-gate.md).
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
- **History**: no purge needed here — no drive path in any commit of any ref (workspace measurement
  for H25, 2026-10-26; the commit address is allowed).
