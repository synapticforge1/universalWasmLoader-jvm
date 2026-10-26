#!/usr/bin/env kotlin
//
// compat.main.kts — the SemVer compatibility gate and the version bump (workspace letter H6;
// replaces scripts/bump.sh, workspace letter H15). Design and proof: cmem/compat-gate.md.
//
//   kotlin scripts/compat.main.kts bump [patch|minor|major]   raise the version, refusing below the floor
//   kotlin scripts/compat.main.kts report                     the floor and the previous-tag tests, nothing written
//   kotlin scripts/compat.main.kts release                    CI, on the tag: the version agrees with the gate
//   kotlin scripts/compat.main.kts selftest                   pure checks of the gate's logic (no git, no build)
//
// `./gradlew bump [-Pkind=...]` runs `bump`. build.gradle.kts is the only place the version lives.
// After a bump:  commit, then `./gradlew release` (tags v<version>, pushes the tag → CI publishes).
//
// Output contract kept from bump.sh: the success line on stdout and the ❌ messages on stderr are
// byte-identical; gate diagnostics go to stderr.

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

// UTF-8 on both streams, as bash wrote them: a redirected JVM stream otherwise uses the
// Windows code page and turns ✅ / ❌ / → into '?'.
System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))

val API_FILE = "api/universal-wasm-loader-jvm.api"
val README_BREAKING_HEADING = "### ⚠️ Breaking Changes"
val NEXT_RELEASE = "_next release_"
val VERSION_LINE = Regex("""^(\s*version\s*=\s*)"(\d+)\.(\d+)\.(\d+)"""", RegexOption.MULTILINE)
val SPEC_IN_README = Regex("""SPEC\.md[^\n]*?\*\*v(\d+\.\d+\.\d+)\*\*""")
val SPEC_IN_SPEC = Regex("""^Version:\s*(\d+\.\d+\.\d+)""", RegexOption.MULTILINE)

enum class Level { PATCH, MINOR, MAJOR }

data class Ver(val major: Int, val minor: Int, val patch: Int) : Comparable<Ver> {
    override fun compareTo(other: Ver) = compareValuesBy(this, other, Ver::major, Ver::minor, Ver::patch)
    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(s: String): Ver? = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").find(s.trim())
            ?.destructured?.let { (a, b, c) -> Ver(a.toInt(), b.toInt(), c.toInt()) }
    }
}

fun nextVersion(v: Ver, kind: Level): Ver = when (kind) {
    Level.MAJOR -> Ver(v.major + 1, 0, 0)
    Level.MINOR -> Ver(v.major, v.minor + 1, 0)
    Level.PATCH -> Ver(v.major, v.minor, v.patch + 1)
}

/** The level of the step from [from] to [to]; null when [to] is not above [from]. */
fun levelOf(from: Ver, to: Ver): Level? = when {
    to <= from -> null
    to.major != from.major -> Level.MAJOR
    to.minor != from.minor -> Level.MINOR
    else -> Level.PATCH
}

/** One key per class header and per member line of a Kotlin ABI dump (the `.api` file under `api/`). */
fun surfaceKeys(api: String): Set<String> {
    val keys = linkedSetOf<String>()
    var owner: String? = null
    for (raw in api.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> {}
            line == "}" -> owner = null
            line.endsWith("{") -> {
                owner = line.removeSuffix("{").trim()
                keys += owner
            }
            else -> keys += "${owner ?: "<top>"} :: $line"
        }
    }
    return keys
}

data class Floor(val level: Level, val reasons: List<String>)

/** Rule 2: removed or changed → major, added → minor, nothing → patch; the SPEC version moves it by its own level. */
fun floorOf(releasedApi: String, currentApi: String, releasedSpec: Ver, currentSpec: Ver): Floor {
    val before = surfaceKeys(releasedApi)
    val after = surfaceKeys(currentApi)
    val removed = before - after
    val added = after - before
    val reasons = mutableListOf<String>()
    var level = Level.PATCH
    if (removed.isNotEmpty()) {
        level = Level.MAJOR
        removed.forEach { reasons += "removed or changed: $it" }
    }
    if (added.isNotEmpty()) {
        if (level < Level.MINOR) level = Level.MINOR
        added.forEach { reasons += "added: $it" }
    }
    if (currentSpec != releasedSpec) {
        val specLevel = levelOf(releasedSpec, currentSpec) ?: Level.MAJOR // a lowered SPEC is accepting less
        reasons += "SPEC.md v$releasedSpec → v$currentSpec ($specLevel)"
        if (specLevel > level) level = specLevel
    }
    return Floor(level, reasons)
}

/** The Breaking Changes section holds a row whose first cell is [label]. */
fun hasBreakingRow(readme: String, label: String): Boolean {
    val start = readme.indexOf(README_BREAKING_HEADING)
    if (start < 0) return false
    val body = readme.substring(start + README_BREAKING_HEADING.length)
    val end = Regex("""^#""", RegexOption.MULTILINE).find(body)?.range?.first ?: body.length
    return body.substring(0, end).lines().any { it.trim().startsWith("| $label |") }
}

/**
 * Rules 2, 4 and 6 for a bump of [kind] from [current]: a list of refusals, empty when allowed.
 * Pre-1.0 a minor may stand in for a major floor; the Breaking Changes row is still required.
 */
fun problemsFor(kind: Level, floor: Level, current: Ver, hasRow: Boolean): List<String> {
    val problems = mutableListOf<String>()
    val preOne = current.major == 0
    val allowed = kind >= floor || (preOne && floor == Level.MAJOR && kind == Level.MINOR)
    if (!allowed) problems += "a ${kind.name.lowercase()} bump is below the floor (${floor.name.lowercase()})"
    if (floor == Level.MAJOR && !hasRow) {
        problems += "the floor is major: README needs a `| $NEXT_RELEASE |` row under \"$README_BREAKING_HEADING\""
    }
    return problems
}

fun specOf(readme: String?, spec: String?): Ver? =
    readme?.let { SPEC_IN_README.find(it)?.groupValues?.get(1) }?.let(Ver::parse)
        ?: spec?.let { SPEC_IN_SPEC.find(it)?.groupValues?.get(1) }?.let(Ver::parse)

// ---------------------------------------------------------------------------------------------
// Repository access
// ---------------------------------------------------------------------------------------------

val root: File = __FILE__.absoluteFile.parentFile.parentFile
val isWindows = System.getProperty("os.name").lowercase().contains("win")

fun fail(msg: String): Nothing {
    System.err.println(msg)
    kotlin.system.exitProcess(1)
}

fun note(msg: String) = System.err.println(msg)

data class Run(val exit: Int, val out: String)

fun run(dir: File, vararg cmd: String, timeoutMin: Long = 30): Run {
    val p = ProcessBuilder(*cmd).directory(dir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    if (!p.waitFor(timeoutMin, TimeUnit.MINUTES)) {
        p.destroyForcibly()
        return Run(-1, out + "\n(timed out)")
    }
    return Run(p.exitValue(), out)
}

fun git(vararg args: String): Run = run(root, "git", *args)

fun gitShow(ref: String, path: String): String? =
    git("show", "$ref:$path").takeIf { it.exit == 0 }?.out

// The wrapper by ABSOLUTE path: cmd does not search the working directory when
// NoDefaultCurrentDirectoryInExePath is set, and then `gradlew.bat` "is not recognized".
fun gradle(dir: File, vararg args: String): Run =
    if (isWindows) run(dir, "cmd", "/c", File(dir, "gradlew.bat").absolutePath, "--no-daemon", "--console=plain", *args)
    else {
        File(dir, "gradlew").setExecutable(true)
        run(dir, File(dir, "gradlew").absolutePath, "--no-daemon", "--console=plain", *args)
    }

val gradleFile = File(root, "build.gradle.kts")

fun readVersion(): Ver {
    if (!gradleFile.isFile) fail("❌ bump: ${gradleFile.path} not found")
    val m = VERSION_LINE.find(gradleFile.readText())
        ?: fail("❌ bump: could not find a 'version = \"X.Y.Z\"' line in build.gradle.kts")
    return Ver(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt())
}

fun writeVersion(to: Ver) {
    val text = gradleFile.readText()
    gradleFile.writeText(VERSION_LINE.replace(text) { "${it.groupValues[1]}\"$to\"" })
}

/** The newest `vX.Y.Z` tag strictly below [below] (or the newest of all when null). */
fun lastTag(below: Ver? = null): String? =
    git("tag", "--list", "v*").out.lines().mapNotNull { t -> Ver.parse(t)?.let { t.trim() to it } }
        .filter { below == null || it.second < below }
        .maxByOrNull { it.second }?.first

/** The released surface: the tag's snapshot, or the committed baseline for a tag older than the gate. */
fun releasedApi(tag: String): String =
    gitShow(tag, API_FILE)
        ?: File(root, "api/$tag.api").takeIf { it.isFile }?.readText()
        ?: fail("❌ compat: no surface for $tag (neither $tag:$API_FILE nor api/$tag.api)")

fun currentSpec(): Ver = specOf(File(root, "README.md").readText(), null)
    ?: fail("❌ compat: README.md does not declare `SPEC.md` **vX.Y.Z**")

fun releasedSpec(tag: String): Ver = specOf(gitShow(tag, "README.md"), gitShow(tag, "SPEC.md"))
    ?: fail("❌ compat: no SPEC version recorded at $tag")

/** Rule 1: the committed snapshot must match the code, or the floor is computed from a stale file. */
fun requireFreshSurface() {
    val r = gradle(root, "checkLegacyAbi")
    if (r.exit != 0) {
        note(r.out.lines().takeLast(25).joinToString("\n"))
        // Only call it stale when the task itself ran and failed; anything else is a build fault.
        val ranTheCheck = r.out.contains("checkLegacyAbi") && r.out.contains("FAILED")
        if (ranTheCheck) fail("❌ compat: $API_FILE is STALE — the public API changed. Re-baseline with `./gradlew updateLegacyAbi` in its own commit, with the reason (Rule 5).")
        fail("❌ compat: could not run `gradlew checkLegacyAbi` (exit ${r.exit}) — see the output above; this is a build fault, not a surface verdict.")
    }
}

/**
 * Rule 3: the previous tag's tests (its src/test, fixtures included) against the CURRENT library.
 * A git worktree of the tag in the temp dir; its src/main and build files replaced by the current ones.
 */
fun previousTests(tag: String): Run {
    val wt = Files.createTempDirectory("uwl-jvm-prev-").toFile()
    try {
        val add = git("worktree", "add", "--detach", "--force", wt.path, tag)
        if (add.exit != 0) return Run(add.exit, add.out)
        File(wt, "src/main").deleteRecursively()
        File(root, "src/main").copyRecursively(File(wt, "src/main"))
        for (f in listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties")) {
            File(root, f).takeIf { it.isFile }?.copyTo(File(wt, f), overwrite = true)
        }
        File(root, "gradle").copyRecursively(File(wt, "gradle"), overwrite = true)
        File(root, if (isWindows) "gradlew.bat" else "gradlew").copyTo(File(wt, if (isWindows) "gradlew.bat" else "gradlew"), overwrite = true)
        val r = gradle(wt, "test")
        val results = File(wt, "build/test-results/test").listFiles { f -> f.name.endsWith(".xml") }.orEmpty()
        var tests = 0
        var failed = 0
        for (x in results) {
            val head = Regex("""<testsuite [^>]*>""").find(x.readText())?.value ?: continue
            fun attr(n: String) = Regex("""\b$n="(\d+)"""").find(head)?.groupValues?.get(1)?.toInt() ?: 0
            tests += attr("tests")
            failed += attr("failures") + attr("errors")
        }
        val green = r.exit == 0 && tests > 0 && failed == 0
        val summary = "previous-tag tests ($tag): ${if (green) "GREEN" else "RED"} — $tests run, $failed failed, exit ${r.exit}"
        return Run(if (green) 0 else 1, if (green) summary else summary + "\n" + r.out.lines().takeLast(15).joinToString("\n"))
    } finally {
        git("worktree", "remove", "--force", wt.path)
        git("worktree", "prune")
        wt.deleteRecursively()
    }
}

fun printFloor(tag: String, floor: Floor) {
    note("compat: floor against $tag is ${floor.level.name.lowercase()}")
    floor.reasons.take(20).forEach { note("  - $it") }
    if (floor.reasons.size > 20) note("  … ${floor.reasons.size - 20} more")
}

fun computeFloor(tag: String): Floor =
    floorOf(releasedApi(tag), File(root, API_FILE).readText(), releasedSpec(tag), currentSpec())

// ---------------------------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------------------------

fun cmdBump(kindArg: String) {
    val kind = when (kindArg) {
        "patch" -> Level.PATCH
        "minor" -> Level.MINOR
        "major" -> Level.MAJOR
        else -> fail("❌ bump: unknown release kind \"$kindArg\" — use patch | minor | major")
    }
    val current = readVersion()
    val tag = lastTag() ?: fail("❌ compat: no v* tag to compare against")
    requireFreshSurface()
    val floor = computeFloor(tag)
    printFloor(tag, floor)
    val readme = File(root, "README.md")
    val problems = problemsFor(kind, floor.level, current, hasBreakingRow(readme.readText(), NEXT_RELEASE)).toMutableList()
    if (floor.level != Level.MAJOR) {
        val prev = previousTests(tag)
        note(prev.out)
        if (prev.exit != 0) problems += "the previous tag's tests are RED against the current library (Rule 3)"
    }
    if (problems.isNotEmpty()) fail(problems.joinToString("\n") { "❌ compat: $it" })
    val to = nextVersion(current, kind)
    writeVersion(to)
    val text = readme.readText()
    if (hasBreakingRow(text, NEXT_RELEASE)) readme.writeText(text.replace("| $NEXT_RELEASE |", "| $to |"))
    println("✅ build.gradle.kts → $to  (${kindArg} bump from $current)")
}

fun cmdReport() {
    val tag = lastTag() ?: fail("❌ compat: no v* tag to compare against")
    requireFreshSurface()
    printFloor(tag, computeFloor(tag))
    val prev = previousTests(tag)
    note(prev.out)
    if (prev.exit != 0) kotlin.system.exitProcess(1)
}

fun cmdRelease() {
    val version = readVersion()
    val ref = System.getenv("GITHUB_REF_NAME")
    if (ref != null && ref.startsWith("v") && ref != "v$version") fail("❌ compat: tag $ref does not match build.gradle.kts version $version")
    val prevTag = lastTag(below = version) ?: run {
        note("compat: no tag below $version — first release, nothing to compare")
        return
    }
    val step = levelOf(Ver.parse(prevTag)!!, version)!!
    requireFreshSurface()
    val floor = computeFloor(prevTag)
    printFloor(prevTag, floor)
    val problems = problemsFor(step, floor.level, Ver.parse(prevTag)!!, hasBreakingRow(File(root, "README.md").readText(), version.toString())).toMutableList()
    if (floor.level != Level.MAJOR) {
        val prev = previousTests(prevTag)
        note(prev.out)
        if (prev.exit != 0) problems += "the previous tag's tests are RED against this release (Rule 3)"
    }
    if (problems.isNotEmpty()) fail(problems.joinToString("\n") { "❌ compat: $prevTag → $version: $it" })
    note("compat: $prevTag → $version (${step.name.lowercase()}) passes the gate")
}

fun cmdSelftest() {
    var n = 0
    val bad = mutableListOf<String>()
    fun check(name: String, ok: Boolean) { n++; if (!ok) bad += name }

    val v = Ver(0, 1, 2)
    check("patch 0.1.2 → 0.1.3", nextVersion(v, Level.PATCH) == Ver(0, 1, 3))
    check("minor resets patch", nextVersion(v, Level.MINOR) == Ver(0, 2, 0))
    check("major resets both", nextVersion(v, Level.MAJOR) == Ver(1, 0, 0))
    check("components unbounded", nextVersion(Ver(1, 9, 9), Level.PATCH) == Ver(1, 9, 10))
    check("parse rejects junk", Ver.parse("1.2") == null && Ver.parse("v1.2.3") == Ver(1, 2, 3))
    check("levelOf minor", levelOf(Ver(0, 1, 2), Ver(0, 2, 0)) == Level.MINOR)
    check("levelOf not above", levelOf(Ver(0, 1, 2), Ver(0, 1, 2)) == null)

    val base = "public final class a/B {\n\tpublic final fun f ()V\n}\n"
    val spec = Ver(3, 0, 0)
    check("same surface → patch", floorOf(base, base, spec, spec).level == Level.PATCH)
    val added = "public final class a/B {\n\tpublic final fun f ()V\n\tpublic final fun g ()V\n}\n"
    check("member added → minor", floorOf(base, added, spec, spec).level == Level.MINOR)
    check("member removed → major", floorOf(added, base, spec, spec).level == Level.MAJOR)
    val retyped = "public final class a/B {\n\tpublic final fun f ()I\n}\n"
    check("member retyped → major", floorOf(base, retyped, spec, spec).level == Level.MAJOR)
    val opened = "public class a/B {\n\tpublic final fun f ()V\n}\n"
    check("class header changed → major", floorOf(base, opened, spec, spec).level == Level.MAJOR)
    check("SPEC minor → minor", floorOf(base, base, spec, Ver(3, 1, 0)).level == Level.MINOR)
    check("SPEC lowered → major", floorOf(base, base, spec, Ver(2, 9, 0)).level == Level.MAJOR)
    check("whitespace-only change → patch", floorOf(base, base.replace("\t", "    ") + "\n\n", spec, spec).level == Level.PATCH)

    val readme = "# x\n\n$README_BREAKING_HEADING\n\n| Version | What changed | How to migrate |\n| --- | --- | --- |\n| $NEXT_RELEASE | a | b |\n\n## Next\n| _other_ | x |\n"
    check("row found", hasBreakingRow(readme, NEXT_RELEASE))
    check("row outside the section ignored", !hasBreakingRow(readme, "_other_"))
    check("no section → no row", !hasBreakingRow("# x\n| $NEXT_RELEASE | a |\n", NEXT_RELEASE))

    check("patch at floor patch allowed", problemsFor(Level.PATCH, Level.PATCH, v, false).isEmpty())
    check("patch below floor minor refused", problemsFor(Level.PATCH, Level.MINOR, v, false).isNotEmpty())
    check("pre-1.0 minor at major floor, no row → refused", problemsFor(Level.MINOR, Level.MAJOR, v, false).isNotEmpty())
    check("pre-1.0 minor at major floor, row → allowed", problemsFor(Level.MINOR, Level.MAJOR, v, true).isEmpty())
    check("pre-1.0 patch at major floor, row → refused", problemsFor(Level.PATCH, Level.MAJOR, v, true).isNotEmpty())
    check("1.x minor at major floor → refused", problemsFor(Level.MINOR, Level.MAJOR, Ver(1, 0, 0), true).isNotEmpty())
    check("1.x major at major floor, row → allowed", problemsFor(Level.MAJOR, Level.MAJOR, Ver(1, 0, 0), true).isEmpty())
    check("1.x major at major floor, no row → refused", problemsFor(Level.MAJOR, Level.MAJOR, Ver(1, 0, 0), false).isNotEmpty())

    check("SPEC from README", specOf("conforms to `SPEC.md` **v3.0.0**.", null) == Ver(3, 0, 0))
    check("SPEC from SPEC.md fallback", specOf("no declaration", "# t\n\nVersion: 3.0.0\n") == Ver(3, 0, 0))
    check("SPEC absent → null", specOf("nothing", null) == null)

    val rewritten = VERSION_LINE.replace("plugins {}\n\nversion = \"0.1.2\"\nval chicoryVersion = \"1.0.0\"\n") { "${it.groupValues[1]}\"0.1.3\"" }
    check("version line rewritten, other version strings untouched", rewritten.contains("version = \"0.1.3\"") && rewritten.contains("chicoryVersion = \"1.0.0\""))

    if (bad.isNotEmpty()) fail("❌ compat selftest: ${bad.size} of $n checks FAILED:\n" + bad.joinToString("\n") { "  - $it" })
    println("compat selftest: $n checks passed")
}

when (args.firstOrNull() ?: "bump") {
    "bump" -> cmdBump(args.getOrElse(1) { "patch" })
    "report" -> cmdReport()
    "release" -> cmdRelease()
    "selftest" -> cmdSelftest()
    else -> fail("usage: kotlin scripts/compat.main.kts bump [patch|minor|major] | report | release | selftest")
}
