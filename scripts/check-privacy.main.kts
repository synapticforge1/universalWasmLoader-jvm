// check-privacy.main.kts — the privacy and credential gate (workspace review standard § 10, letter H37).
//
//   kotlin scripts/check-privacy.main.kts            scan the tracked files: exit 0 when clean
//   kotlin scripts/check-privacy.main.kts selftest   prove each check catches its case and spares the allowed
//
// Three checks, over `git ls-files` (binaries included, read byte for byte):
//   1. NEVER-TRACKED files are not tracked: `cmem/review-issues.md`, `cmem/local/`, `.claude/`, the root
//      `CLAUDE.md` (the memory guide and the review standard keep them local).
//   2. NO MACHINE PATH: a drive path into a user profile, the project tree or a tool install
//      (`<drive>:\Users\<name>`, `<drive>:\Programs\<…>`), or its Unix forms (`/c/Users/<name>`, `/mnt/<d>/…`,
//      `/home/<name>`). The rule's own placeholder examples (`<name>`, `…`) pass.
//   3. NO EMAIL ADDRESS outside `build.gradle.kts` — the POM `<developers>` field Maven Central reads is the
//      one place the maintainer address may appear (owner, 2026-10-23). This script never names it.
// A secret is not searched for by name here: this repo holds none, and the release secrets live only in
// GitHub's repository settings. A finding is fixed by removing it (and rotating a secret that reached a remote).

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// UTF-8 on both streams (a redirected JVM stream otherwise uses the Windows code page).
System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))

val NEVER_TRACKED: List<Pair<String, (String) -> Boolean>> = listOf(
    "cmem/review-issues.md (gitignored review issues)" to { p: String -> p == "cmem/review-issues.md" },
    "cmem/local/ (machine facts)" to { p: String -> p.startsWith("cmem/local/") },
    ".claude/ (per-machine tool settings)" to { p: String -> p.startsWith(".claude/") },
    "the root CLAUDE.md (an untracked pointer)" to { p: String -> p == "CLAUDE.md" },
)

// A segment that is a placeholder or an elision is the rule's own example, not a machine fact.
val SEGMENT = """([^\\/\s`'"<>()\[\]…|*]+)"""
val MACHINE_PATHS: List<Pair<String, Regex>> = listOf(
    "a drive path into a user profile, the project tree or a tool install" to
        Regex("""\b[A-Za-z]:[\\/]{1,2}(Users|Programs|scoop|tools)[\\/]{1,2}$SEGMENT""", RegexOption.IGNORE_CASE),
    "a Unix-style drive path" to Regex("""(?<![\w.])/(mnt/)?[a-z]/(Users|Programs)/$SEGMENT""", RegexOption.IGNORE_CASE),
    "a Linux home folder" to Regex("""(?<![\w.~])/home/$SEGMENT"""),
)
val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
val EMAIL_ALLOWED_IN = setOf("build.gradle.kts")

/**
 * Valid UTF-8 is read as UTF-8 (so a `…` placeholder stays one character); anything else — a `.wasm`, a jar — as
 * ISO-8859-1, so its bytes map one to one and an ASCII path inside a binary is still seen.
 */
fun decode(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
} catch (_: java.nio.charset.CharacterCodingException) {
    bytes.toString(Charsets.ISO_8859_1)
}

/** The findings for one file's content, as [decode] read it. */
fun findings(path: String, text: String): List<String> {
    val out = mutableListOf<String>()
    for ((what, re) in MACHINE_PATHS) re.findAll(text).forEach { out += "$path: $what: ${it.value.take(60)}" }
    if (path !in EMAIL_ALLOWED_IN) EMAIL.findAll(text).forEach { out += "$path: an email address (allowed only in the POM field)" }
    return out
}

fun trackedProblems(tracked: List<String>): List<String> =
    tracked.flatMap { p -> NEVER_TRACKED.filter { (_, test) -> test(p) }.map { (what, _) -> "$p is tracked: $what" } }

fun gitLsFiles(): List<String> {
    val p = ProcessBuilder("git", "ls-files", "-z").redirectErrorStream(true).start()
    val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
    if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
        System.err.println("❌ check-privacy: `git ls-files` failed — the check did not run"); exitProcess(1)
    }
    return out.split('\u0000').filter { it.isNotEmpty() }
}

fun scan(): Int {
    val tracked = gitLsFiles()
    val problems = trackedProblems(tracked).toMutableList()
    for (p in tracked) {
        val f = File(p)
        if (!f.isFile) continue // a deleted, not yet committed file
        problems += findings(p, decode(f.readBytes()))
    }
    if (problems.isNotEmpty()) {
        System.err.println("❌ check-privacy: ${problems.size} finding(s):")
        problems.forEach { System.err.println("  $it") }
        return 1
    }
    println("check-privacy: ${tracked.size} tracked files clean (no machine path, no stray address, nothing local tracked)")
    return 0
}

fun selftest(): Int {
    var n = 0
    val bad = mutableListOf<String>()
    fun caught(path: String, text: String) { n++; if (findings(path, text).isEmpty()) bad += "not caught: $path: $text" }
    fun spared(path: String, text: String) { n++; if (findings(path, text).isNotEmpty()) bad += "falsely caught: $path: $text" }
    fun trackedCaught(p: String) { n++; if (trackedProblems(listOf(p)).isEmpty()) bad += "not caught as tracked: $p" }
    fun trackedSpared(p: String) { n++; if (trackedProblems(listOf(p)).isNotEmpty()) bad += "falsely caught as tracked: $p" }

    caught("a.md", "see D:\\Programs\\someproject\\file")
    caught("a.md", "C:\\Users\\someone\\scoop\\apps")
    caught("a.md", "c:/users/someone/x")
    caught("a.md", "D:\\\\Programs\\\\proj")
    caught("a.md", "/c/Users/someone/x")
    caught("a.md", "/mnt/d/Programs/proj")
    caught("a.md", "/home/someone/.bun")
    caught("x.wasm", "\u0000\u0001D:\\Programs\\proj\\src\u0000")
    n++; if (findings("y.wasm", decode(byteArrayOf(0xC3.toByte(), 0x28) + "D:\\Programs\\proj".toByteArray())).isEmpty()) bad += "not caught: a path inside invalid UTF-8"
    n++; if (findings("z.md", decode("the tree (`D:\\Programs\\…`)".toByteArray(Charsets.UTF_8))).isNotEmpty()) bad += "falsely caught: a UTF-8 elision read from bytes"
    caught("README.md", "write to first.last@example.org")
    caught("scripts/x.main.kts", "a.b@c.io")

    spared("cmem/working-rules.md", "a user profile (`C:\\Users\\<name>\\…`), the project tree (`D:\\Programs\\…`)")
    spared("a.md", "a placeholder `<repo-root>/cmem` and `../../workspace/cmem/`")
    spared("a.md", "a convention path C:\\zig-cache\\wazmrt")
    spared("a.md", "~/.bun/bin and /tmp and ./home/x")
    spared("build.gradle.kts", "email.set(\"first.last@example.org\")")
    spared("a.md", "a scoped name `@types/bun` and `user@` and `@jrmarcum`")

    trackedCaught("cmem/review-issues.md")
    trackedCaught("cmem/local/environment.md")
    trackedCaught(".claude/settings.json")
    trackedCaught("CLAUDE.md")
    trackedSpared("cmem/review-process.md")
    trackedSpared("docs/CLAUDE.md")

    if (bad.isNotEmpty()) { System.err.println("❌ check-privacy selftest: ${bad.size} of $n FAILED:\n" + bad.joinToString("\n") { "  - $it" }); return 1 }
    println("check-privacy selftest: $n checks passed")
    return 0
}

exitProcess(if (args.firstOrNull() == "selftest") selftest() else scan())
