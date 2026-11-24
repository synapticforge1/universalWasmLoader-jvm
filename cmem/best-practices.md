# best-practices.md — METHOD, not findings

One bold rule, the incident that paid for it, and the origin tag. Post-mortems stay in their home
file. Rules this repo pays for itself are appended under § L with `[universalWasmLoader-jvm]`, with
the next free number; numbers are never reused.

## § L This repo's own rules

- **L1 A script that prints nothing and exits 0 has not been shown to run.** Kotlin block comments
  NEST: `` `api/*.api` `` inside a KDoc opened a second comment that swallowed the rest of
  `compat.main.kts`, so `selftest` exited 0 with no output, three times, under two launchers
  (2026-10-26, [compat-gate.md](compat-gate.md) § Traps). Expect the success line, not the exit code;
  never write `/*` inside a Kotlin comment. [universalWasmLoader-jvm]
- **L2 A gate that could not run must not report a verdict.** The first freshness check printed
  "api snapshot is STALE" when `cmd` could not even find `gradlew.bat` (2026-10-26, § Traps). Tell
  "the check ran and failed" from "the check did not run". [universalWasmLoader-jvm; cf. -c L3
  "a negative check must tell absent from different"]
- **L3 Measure what a build plugin wires before trusting it.** KGP 2.2's ABI validation does not
  hook `check` (`check --dry-run` listed no `checkLegacyAbi`), and the standalone
  binary-compatibility-validator 0.18.1 cannot read JVM 24 class files ("Unsupported class file
  major version 68") (2026-10-26, § Rule 1). [universalWasmLoader-jvm]
- **L4 A comparison must be shown a difference before its "same" means anything.** The first
  `bump.sh` ↔ port proof compared `❌` lines that neither side's decoding could see, so `errSame`
  read True on every case; and `bump.sh`'s own error path was silent under `pipefail` (2026-10-26,
  § The port). [universalWasmLoader-jvm; cf. workspace 2.1]
- **L5 A fallback path is a product path: test it with every type it can be handed.** Raw mode (no
  `.wit`, SPEC §63) returned f64 results as their bit patterns and turned a `String` argument into
  `0` from 0.1.0 to 0.2.1; its one test (`init_check`) returned an i32, and memory claimed it decoded
  by `ValueType` (2026-10-26, [overview.md](overview.md) § Raw mode). [universalWasmLoader-jvm;
  cf. workspace 4.3]
- **L6 A history scan reads every commit, not the ref tips.** "No drive path in any commit of any
  ref" (workspace H25, 2026-10-26) was true of the tips only. A Gradle cache binary tracked in the
  first 15 commits, including the tagged trees of `v0.1.0` / `v0.1.1`, held the owner's paths. It
  was found with `git grep -a … $(git rev-list --all)` (2026-11-08,
  [archive/history-map-2026-11-08.md](archive/history-map-2026-11-08.md)). [universalWasmLoader-jvm]
- **L7 A history rewrite verifies itself pair by pair, then against a control.** Pair old and new
  commits by header + message (without tree / parent lines), and require identical metadata, mapped
  parents, and tree differences that are exactly the planned deletions. Then run the scan on the
  OLD refs and see it still hit. The pairing caught `filter-branch` stripping the GitHub signature
  from the clean root (one unpaired commit); the re-run used `^<root>`. After `gc --prune=now`,
  rebuild the commit-graph, which still listed pruned commits (`fsck` error). (2026-11-08)
  [universalWasmLoader-jvm; cf. zilc, zailc]
- **L8 A rule check is inverted by mutants, one per rule, in one run.** Eight mutants, each undoing
  one SPEC 4.0.0 refusal, turned exactly the 14 matching tests red and none other. That proves each
  test guards its own rule, which a green run alone cannot show. Read the counts from the XML: a
  compile error left the OLD XML in place and showed "0 failures" (2026-11-08, [overview.md](overview.md)
  § SPEC 4.0.0). [universalWasmLoader-jvm; cf. L2]
- **L9 Know which pushes start workflows before pushing.** GitHub creates no tag events when one
  push carries more than three tags, so moving all six tags in one `--atomic` push re-ran no
  `publish.yml`. The Actions API showed only the `main` CI run (2026-11-08). A single tag push
  (the old `./gradlew release`) does start one; a tag pushed with a job's `GITHUB_TOKEN` starts
  none, which is why the 2026-11-10 workflow tags, pages and publishes in the same run.
  [universalWasmLoader-jvm; cf. loader-dart's three re-runs]
- **L10 Count what a run leaves behind, and count it again for the gates that run OLD code.** `LoaderTest`
  left 12 temp folders per run, found only by counting the temp root after a run. After the fix the
  rehearsal still left 36: the compatibility gate runs the previous tag's tests UNTOUCHED, so their leak
  came back with them. The gate now gives those JVMs a temp directory inside its own worktree, which it
  removes (2026-11-24). [universalWasmLoader-jvm; cf. wasmrt I10]
- **L11 Delete a Gradle build tree with Git Bash `rm -rf`, not PowerShell `Remove-Item`.** Kotlin test classes
  named after long backticked test names pass Windows' path limit, and `Remove-Item -Recurse` fails part-way
  (2026-11-24). [universalWasmLoader-jvm]

The workspace's ★ rules, copied under their stamp: [best-practices-shared.md](best-practices-shared.md).
