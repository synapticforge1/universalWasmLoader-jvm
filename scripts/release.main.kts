// release.main.kts — the local half of a release (workspace H33, slot U4; H37). One command:
//
//   kotlin scripts/release.main.kts [--dry-run]        (or: ./gradlew release [-PdryRun])
//   kotlin scripts/release.main.kts selftest           the review gate's logic, no git, no network
//
// Guards, in order, each fatal:
//   0. THE REVIEW SET (review standard, letter H37): the gitignored `cmem/review-issues.md` holds a line
//      `- Review complete: <commit>` (the last one counts); that commit is an ancestor of HEAD; and since
//      it only `cmem/`, `README.md` and the version line of `build.gradle.kts` changed (memory, release
//      notes, the bump). A review that has not run, or code changed after it, refuses the release;
//   1. the working tree is clean (untracked files count);
//   2. the branch is `main`, and `origin/main` (fetched now) is an ancestor of HEAD — the push is a
//      fast-forward that carries the bump commit;
//   3. the tag v<version> exists neither locally nor on origin;
//   4. Maven Central does not serve this version yet (the POM answers 404);
//   5. every gate, cold and LOCALLY (CI no longer tests: letter H37): `gradlew clean check`, the
//      compatibility gate (floor + the previous tag's tests), the host-module gate, the privacy gate,
//      the memory size check, and the scripts' self-tests.
// Then it pushes `main` — never a tag, never `-f` — and reads it back with `git ls-remote`.
//
// It makes NO tag. CI does the rest: it verifies the version is new, then creates the tag, then the
// GitHub Release page from the tag, then the Maven Central publish (.github/workflows/publish.yml;
// cmem/release.md § How a release runs).
// --dry-run runs every guard and the gate, and pushes nothing.

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// UTF-8 on both streams (a redirected JVM stream otherwise uses the Windows code page).
System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))

val root = File(".").canonicalFile
// [win-x64] [win-arm64] `kotlin` and `gradlew` are .bat / .cmd launchers there, started through cmd
// (cmem/platforms.md § win-x64).
val isWindows = System.getProperty("os.name").lowercase().contains("win")
val dryRun = "--dry-run" in args
val GROUP_PATH = "io/github/jrmarcum/universal-wasm-loader-jvm"

fun fail(msg: String): Nothing {
    System.err.println("❌ release: $msg")
    exitProcess(1)
}

fun ok(msg: String) = println("✓ $msg")

data class Run(val exit: Int, val out: String)

fun run(vararg cmd: String, timeoutMin: Long = 60): Run {
    val pb = ProcessBuilder(*cmd).directory(root).redirectErrorStream(true)
    // This script runs under the `kotlin` launcher, which exports JAVA_OPTS with quoted values;
    // a nested kotlin.bat then fails to parse `if "%JAVA_OPTS%"==""` (measured 2026-11-10:
    // `"=ALL-UNNAMED" … was unexpected at this time`). Children start without the launcher's own
    // variables.
    pb.environment().keys.removeIf { it == "JAVA_OPTS" || it == "KOTLIN_OPTS" || it.startsWith("_KOTLIN") }
    val p = pb.start()
    val out = p.inputStream.bufferedReader().readText()
    if (!p.waitFor(timeoutMin, TimeUnit.MINUTES)) { p.destroyForcibly(); return Run(-1, "$out\n(timed out)") }
    return Run(p.exitValue(), out)
}

fun git(vararg a: String): Run = run("git", *a)

fun kotlinScript(script: String, vararg a: String): Run =
    if (isWindows) run("cmd", "/c", "kotlin", File(root, script).path, *a) else run("kotlin", File(root, script).path, *a)

fun gradle(vararg a: String): Run =
    if (isWindows) run("cmd", "/c", File(root, "gradlew.bat").absolutePath, "--no-daemon", "--console=plain", *a)
    else run(File(root, "gradlew").absolutePath, "--no-daemon", "--console=plain", *a)

fun step(name: String, r: Run) {
    if (r.exit != 0) fail("$name failed (exit ${r.exit}):\n" + r.out.lines().takeLast(25).joinToString("\n"))
    ok(name)
}

// The review gate's pure parts (guard 0), tested by `selftest`.
val REVIEW_LINE = Regex("""^\s*-\s*Review complete:\s*([0-9a-f]{7,40})\b""")

/** The commit of the LAST `- Review complete: <commit>` line, or null. */
fun reviewedCommit(issues: String?): String? =
    issues?.lines()?.mapNotNull { REVIEW_LINE.find(it)?.groupValues?.get(1) }?.lastOrNull()

/** Files changed since the review that are not memory, release notes or the bump: each is a refusal. */
fun disallowedChanges(changed: List<String>, gradleDiff: String): List<String> =
    changed.filter { p ->
        when {
            p.startsWith("cmem/") || p == "README.md" -> false
            p == "build.gradle.kts" -> gradleDiff.lines()
                .filter { (it.startsWith("+") || it.startsWith("-")) && !it.startsWith("+++") && !it.startsWith("---") }
                .any { !Regex("""^[+-]version\s*=\s*"\d+\.\d+\.\d+"\s*$""").matches(it) }
            else -> true
        }
    }

if (args.firstOrNull() == "selftest") {
    var n = 0
    val bad = mutableListOf<String>()
    fun check(name: String, ok: Boolean) { n++; if (!ok) bad += name }
    check("no file → no review", reviewedCommit(null) == null)
    check("no line → no review", reviewedCommit("# issues\n- C-1 open\n") == null)
    check("the line is read", reviewedCommit("- Review complete: 5554109\n") == "5554109")
    check("the LAST line counts", reviewedCommit("- Review complete: aaaaaaa\n- Review complete: bbbbbbb\n") == "bbbbbbb")
    check("a non-hex id is not a review", reviewedCommit("- Review complete: main\n") == null)
    check("memory and README may change", disallowedChanges(listOf("cmem/STATE.md", "README.md"), "").isEmpty())
    val bump = "--- a/build.gradle.kts\n+++ b/build.gradle.kts\n@@ -10 +10 @@\n-version = \"0.4.3\"\n+version = \"0.4.4\"\n"
    check("the version line may change", disallowedChanges(listOf("build.gradle.kts"), bump).isEmpty())
    val more = bump + "@@ -20 +20 @@\n-val chicoryVersion = \"1.0.0\"\n+val chicoryVersion = \"1.1.0\"\n"
    check("any other build change is refused", disallowedChanges(listOf("build.gradle.kts"), more) == listOf("build.gradle.kts"))
    check("code is refused", disallowedChanges(listOf("src/main/kotlin/x.kt", "cmem/a.md"), "") == listOf("src/main/kotlin/x.kt"))
    check("a workflow is refused", disallowedChanges(listOf(".github/workflows/publish.yml"), "").isNotEmpty())
    if (bad.isNotEmpty()) fail("release selftest: ${bad.size} of $n checks FAILED:\n" + bad.joinToString("\n") { "  - $it" })
    println("release selftest: $n checks passed")
    exitProcess(0)
}

// 0. the review set has run on this code (letter H37)
val issuesFile = File(root, "cmem/review-issues.md")
val reviewed = reviewedCommit(issuesFile.takeIf { it.isFile }?.readText())
    ?: fail("no `- Review complete: <commit>` line in cmem/review-issues.md — run \"review\" first (cmem/review-process.md)")
if (git("cat-file", "-e", "$reviewed^{commit}").exit != 0) fail("the reviewed commit $reviewed is not in this repository")
if (git("merge-base", "--is-ancestor", reviewed, "HEAD").exit != 0) fail("the reviewed commit $reviewed is not an ancestor of HEAD")
val changedSince = git("diff", "--name-only", reviewed, "HEAD").out.lines().filter { it.isNotBlank() }
val refused = disallowedChanges(changedSince, git("diff", reviewed, "HEAD", "--", "build.gradle.kts").out)
if (refused.isNotEmpty()) fail("changed since the review at $reviewed (re-review first):\n" + refused.joinToString("\n") { "  $it" })
ok("review complete at $reviewed; since then only memory, README and the version line changed")

// 1. clean tree
val status = git("status", "--porcelain", "--untracked-files=all")
if (status.exit != 0) fail("`git status` failed")
status.out.lines().filter { it.isNotBlank() }.let { dirty ->
    if (dirty.isNotEmpty()) fail("the working tree is not clean (untracked included):\n" + dirty.take(10).joinToString("\n") { "  $it" })
}
ok("working tree clean (untracked included)")

// 2. on main, a fast-forward of origin/main
val branch = git("rev-parse", "--abbrev-ref", "HEAD").out.trim()
if (branch != "main") fail("on '$branch', not main (merge first: workspace H33 U2)")
step("git fetch origin", git("fetch", "-q", "origin"))
val head = git("rev-parse", "HEAD").out.trim()
val originMain = git("rev-parse", "origin/main").out.trim()
if (git("merge-base", "--is-ancestor", originMain, head).exit != 0) fail("origin/main ($originMain) is not an ancestor of HEAD ($head): pull or rebase first")
if (originMain == head) fail("HEAD is origin/main: nothing to push (bump first: ./gradlew bump -Pkind=…)")
ok("main = origin/main + ${git("rev-list", "--count", "$originMain..$head").out.trim()} commit(s), a fast-forward")

// 3. the tag is new
val version = Regex("""(?m)^version\s*=\s*"(\d+\.\d+\.\d+)"""").find(File(root, "build.gradle.kts").readText())?.groupValues?.get(1)
    ?: fail("no `version = \"X.Y.Z\"` line in build.gradle.kts")
val tag = "v$version"
if (git("tag", "--list", tag).out.isNotBlank()) fail("tag $tag already exists locally")
val remoteTag = git("ls-remote", "--tags", "origin", "refs/tags/$tag")
if (remoteTag.exit != 0) fail("`git ls-remote` failed: ${remoteTag.out}")
if (remoteTag.out.isNotBlank()) fail("tag $tag already exists on origin — a published tag never moves; bump to a new version")
ok("tag $tag exists neither locally nor on origin")

// 4. Maven Central does not have it
val pom = "https://repo1.maven.org/maven2/$GROUP_PATH/$version/universal-wasm-loader-jvm-$version.pom"
val status4 = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    .sendAsync(HttpRequest.newBuilder(URI.create(pom)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding())
    .get(30, TimeUnit.SECONDS).statusCode()
when (status4) {
    404 -> ok("Maven Central does not serve $version (POM 404)")
    200 -> fail("Maven Central already serves $version — a published version is never re-released")
    else -> fail("Maven Central answered HTTP $status4 for $pom; cannot tell")
}

// 5. every gate, cold and locally (CI no longer tests)
step("gradlew clean check (tests + API snapshot)", gradle("clean", "check"))
step("compat selftest", kotlinScript("scripts/compat.main.kts", "selftest"))
step("compat release (floor + the previous tag's tests)", kotlinScript("scripts/compat.main.kts", "release"))
step("check-host (only Host.kt touches the OS)", kotlinScript("scripts/check-host.main.kts"))
step("check-privacy (no machine path, no stray address, nothing local tracked)", kotlinScript("scripts/check-privacy.main.kts"))
// The size check's one recorded exception (cmem/working-rules.md § Records): the ★ copy in best-practices.md
// is held verbatim until the owner decides (letter H38), so a line-length finding THERE is accepted; any
// other problem refuses. The check itself stays byte-equal to the workspace's TS original.
kotlinScript("scripts/check-cmem.main.kts").let { r ->
    val problemLines = r.out.lines().filter { it.isNotBlank() && !it.startsWith("check-cmem:") }
    val held = problemLines.filter { it.startsWith("line-length  best-practices.md:") }
    if (r.exit != 0 && (problemLines.isEmpty() || held.size != problemLines.size)) {
        fail("check-cmem failed (exit ${r.exit}):\n" + r.out.lines().takeLast(25).joinToString("\n"))
    }
    ok("check-cmem (the memory size check)" + if (held.isEmpty()) "" else "; ${held.size} held ★ line(s) accepted (H38)")
}
step("self-tests (github-release, check-host, check-privacy, release)", Run(
    listOf(
        kotlinScript("scripts/github-release.main.kts", "selftest"),
        kotlinScript("scripts/check-host.main.kts", "selftest"),
        kotlinScript("scripts/check-privacy.main.kts", "selftest"),
        kotlinScript("scripts/release.main.kts", "selftest"),
    ).maxOf { it.exit }, "",
))

if (dryRun) {
    println("dry run: every guard and the gate passed; pushed nothing. Without --dry-run: git push origin main ($head).")
    exitProcess(0)
}

// Push main ONLY (no tag, no -f), then read it back.
step("git push origin main", git("push", "origin", "main"))
val back = git("ls-remote", "origin", "refs/heads/main").out.trim().split(Regex("\\s+")).firstOrNull()
if (back != head) fail("read-back: origin main is $back, not $head")
ok("origin/main = $head (ls-remote)")
println("→ CI now verifies that $version is new, then tags $tag on $head, makes the Release page, and publishes it to Maven Central.")
println("  Watch: https://github.com/synapticforge1/universalWasmLoader-jvm/actions — then record it in cmem/release.md § Release History.")
