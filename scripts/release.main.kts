// release.main.kts — the local half of a release (workspace H33, slot U4). One command:
//
//   kotlin scripts/release.main.kts [--dry-run]        (or: ./gradlew release [-PdryRun])
//
// Guards, in order, each fatal:
//   1. the working tree is clean (untracked files count);
//   2. the branch is `main`, and `origin/main` (fetched now) is an ancestor of HEAD — the push is a
//      fast-forward that carries the bump commit;
//   3. the tag v<version> exists neither locally nor on origin;
//   4. Maven Central does not serve this version yet (the POM answers 404);
//   5. a cold gate: `gradlew clean check`, the compatibility gate in CI's mode (floor + the previous
//      tag's tests), and both scripts' self-tests.
// Then it pushes `main` — never a tag, never `-f` — and reads it back with `git ls-remote`.
//
// It makes NO tag. CI does the rest, in the owner's order (2026-11-10): checks on the pushed commit,
// then CI creates the tag, then the GitHub Release page from the tag, then the Maven Central
// publish (.github/workflows/publish.yml; cmem/release.md § How a release runs).
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
    val p = ProcessBuilder(*cmd).directory(root).redirectErrorStream(true).start()
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

// 5. a cold gate
step("gradlew clean check (tests + API snapshot)", gradle("clean", "check"))
step("compat selftest", kotlinScript("scripts/compat.main.kts", "selftest"))
step("compat release (floor + previous tag's tests, CI's mode)", kotlinScript("scripts/compat.main.kts", "release"))
step("github-release selftest", kotlinScript("scripts/github-release.main.kts", "selftest"))

if (dryRun) {
    println("dry run: every guard and the gate passed; pushed nothing. Without --dry-run: git push origin main ($head).")
    exitProcess(0)
}

// Push main ONLY (no tag, no -f), then read it back.
step("git push origin main", git("push", "origin", "main"))
val back = git("ls-remote", "origin", "refs/heads/main").out.trim().split(Regex("\\s+")).firstOrNull()
if (back != head) fail("read-back: origin main is $back, not $head")
ok("origin/main = $head (ls-remote)")
println("→ CI now runs checks on $head, then tags $tag, makes the Release page, and publishes $version to Maven Central.")
println("  Watch: https://github.com/synapticforge1/universalWasmLoader-jvm/actions — then record it in cmem/release.md § Release History.")
