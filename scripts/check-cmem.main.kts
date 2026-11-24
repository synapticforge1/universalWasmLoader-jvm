// check-cmem.main.kts — size gate for a project's cmem folder (the memory guide, letter H38).
// Run from the repository root:  kotlin scripts/check-cmem.main.kts [cmem-dir] [--summary]
// Exit code 0 when every limit holds, 1 otherwise. No dependencies.
//
// A line-for-line port of the workspace's `tools/check-cmem.ts` (this repo's committed scripts are Kotlin,
// workspace Tooling rule 1). Proven equivalent by an output-diff on the same trees, stdout and exit code
// compared (cmem/archive/check-cmem-port-2026-11-24.md). Change the TS original first, then port and re-prove.

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

// UTF-8 on both streams (a redirected JVM stream otherwise uses the Windows code page).
System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))

val INDEX_BYTES = 3 * 1024 // INDEX.md: a router, read every session
val STATE_LINES = 60 // STATE.md: where the project stands
val FILE_BYTES = 25 * 1024 // every other live note
val LINE_CHARS = 200 // any line outside a code fence
val SKIP_DIRS = setOf("archive", "local") // never read wholesale, so not limited
val STATE_HEADINGS = listOf("## Now", "## Review", "## Next", "## Waiting on owner")
val FENCE = "`".repeat(3) // the marker that opens and closes a code block

val summaryOnly = "--summary" in args
val root = args.firstOrNull { !it.startsWith("--") } ?: "cmem"
val problems = mutableListOf<Pair<String, String>>()
fun add(rule: String, text: String) { problems += rule to text }

// node's readdirSync().sort() orders names by UTF-16 code unit, as String.compareTo does.
fun walk(dir: File): List<File> {
    val out = mutableListOf<File>()
    for (name in (dir.list() ?: emptyArray()).sorted()) {
        val path = File(dir, name)
        if (path.isDirectory) {
            if (name !in SKIP_DIRS) out += walk(path)
        } else if (name.endsWith(".md")) out += path
    }
    return out
}

val rootDir = File(root)
if (!rootDir.exists()) {
    System.err.println("check-cmem: no folder at $root")
    exitProcess(1)
}
if (!File(rootDir, "STATE.md").exists()) add("state-missing", "$root/STATE.md does not exist")
if (!File(rootDir, "INDEX.md").exists()) add("index-missing", "$root/INDEX.md does not exist")

for (path in walk(rootDir)) {
    val rel = path.relativeTo(rootDir).path.replace("\\", "/")
    val text = path.readText(Charsets.UTF_8)
    val bytes = text.toByteArray(Charsets.UTF_8).size
    val lines = text.split(Regex("\r?\n"))

    if (rel == "INDEX.md") {
        if (bytes > INDEX_BYTES) add("index-size", "$rel: $bytes bytes, limit $INDEX_BYTES")
    } else if (rel == "STATE.md") {
        if (lines.size > STATE_LINES) add("state-size", "$rel: ${lines.size} lines, limit $STATE_LINES")
        for (h in STATE_HEADINGS) {
            if (h !in lines) add("state-heading", "$rel: heading \"$h\" is missing")
        }
    } else if (bytes > FILE_BYTES) {
        add("file-size", "$rel: $bytes bytes, limit $FILE_BYTES")
    }

    var inFence = false
    lines.forEachIndexed { i, line ->
        if (line.trimStart().startsWith(FENCE)) {
            inFence = !inFence
            return@forEachIndexed
        }
        if (!inFence && line.length > LINE_CHARS) {
            add("line-length", "$rel:${i + 1}: ${line.length} characters, limit $LINE_CHARS")
        }
    }
}

val counts = linkedMapOf<String, Int>()
for ((rule, _) in problems) counts[rule] = (counts[rule] ?: 0) + 1
if (!summaryOnly) for ((rule, text) in problems) println("$rule  $text")
val summary = counts.entries.joinToString(", ") { "${it.key} ${it.value}" }
val total = problems.size
println(if (total == 0) "check-cmem: all limits hold" else "check-cmem: $total problems ($summary)")
exitProcess(if (total == 0) 0 else 1)
