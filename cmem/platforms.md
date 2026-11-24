# platforms.md — the support matrix and the platform notes

Last verified: 2026-11-24. The six IDs, the status values, the tiers and the "Tested on" rule are the workspace's
`../../../workspace/cmem/platforms.md`; they are not restated here.

## Tier

**Tier 1 — per engine** (owner, 2026-11-19; letter H40). The library is JVM bytecode on Chicory, a pure-Java
WebAssembly runtime: no native code and no native library, so the JVM hides the OS and the CPU. The suite runs
once per engine (the JVM) on one machine. Every OS call is in `src/main/kotlin/…/Host.kt`, held there by
`scripts/check-host.main.kts`; OS-specific testing concerns `Host.kt` alone, through its on-demand self-check
(`./gradlew hostSelfCheck`), and only when `Host.kt` or the pinned JDK changes.

## Support matrix

| Platform | Status | Target name | Tested on |
| --- | --- | --- | --- |
| `win-x64` | tested | JVM bytecode 24 (one jar for every platform) | dev machine |
| `win-arm64` | pending | JVM bytecode 24 | — (no arm64 machine yet) |
| `mac-x64` | pending | JVM bytecode 24 | — (no Intel Mac yet) |
| `mac-arm64` | pending | JVM bytecode 24 | — (no arm64 Mac yet) |
| `linux-x64` | pending | JVM bytecode 24 | — (not run under the final standard yet) |
| `linux-arm64` | pending | JVM bytecode 24 | — (no arm64 machine yet) |

- **No platform-specific build is published:** Maven Central serves one jar. So no row is `shipped`, and a
  platform added later appends a result line only (Central freezes a version).
- **`win-x64` result (2026-11-24):** Windows 11 Pro (build 26300), JDK 25 toolchain: `./gradlew check` 62 / 62;
  `./gradlew hostSelfCheck` 9 / 9 ok.
- **Through 0.4.3, CI also ran the suite on GitHub's `ubuntu-latest`** (a GitHub-hosted runner, not one of the
  owner's machines). Under the final standard CI no longer tests, so `linux-x64` is `pending` until it runs
  locally (WSL: Ubuntu) or on the Intel Mac.

## The JVM axis (second table)

| JVM | Status | Tested on |
| --- | --- | --- |
| JDK 24 (the minimum: bytecode target 24) | pending | — |
| JDK 25 (the build toolchain, `jvmToolchain(25)`) | tested | dev machine (`win-x64`), 2026-11-24 |

- **The Linux distro table** stays empty until a distro is run. The artifact is pure JVM bytecode, so the glibc
  2.28 / musl baseline does not apply: it links no libc (it needs only a JDK 24+ for that distro).

## Notes per platform

### `win-x64` (and `win-arm64`)

- **The tooling's launchers:** `kotlin` and `gradlew` are `.bat` / `.cmd` files on Windows, so the scripts and
  the Gradle tasks start them through `cmd /c`. Settings: `scripts/compat.main.kts` (`isWindows`),
  `scripts/release.main.kts` (`isWindows`), `build.gradle.kts` (`runKotlinScript`), each tagged
  `[win-x64] [win-arm64]`. Tooling only: the library itself has no platform-specific code.
- **A script run by the `kotlin` launcher cannot start another `kotlin` unless the launcher's `JAVA_OPTS`
  is cleared** (its quoted values break the nested `kotlin.bat`). Setting: `scripts/release.main.kts` `run()`
  (measured 2026-11-10).
