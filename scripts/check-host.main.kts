// check-host.main.kts — the host-module gate (workspace letter H40, tier 1).
//
//   kotlin scripts/check-host.main.kts            scan src/main: exit 0 when only Host.kt touches the OS
//   kotlin scripts/check-host.main.kts selftest   prove each pattern catches its API and spares near-misses
//
// Every call this library makes to the operating system lives in
// src/main/kotlin/com/jrmarcum/universalwasmloader/Host.kt: files and paths, the classpath, the network,
// processes, the environment and system properties, the standard streams, and the WASI options that
// inherit them. This gate fails when any other main source file names one of those APIs. Exception TYPES
// (`IOException`, `FileNotFoundException`) and the URI parser (`java.net.URI`) are not OS calls and pass.
// Comments do not count: `// …` from a line start or after whitespace, and `/* … */`. Tests (src/test) may
// use the OS freely.

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

// UTF-8 on both streams (a redirected JVM stream otherwise uses the Windows code page).
System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))

val HOST_FILE = "Host.kt"

/** Name → pattern. Each names one family of OS APIs. */
val FORBIDDEN: List<Pair<String, Regex>> = listOf(
    "java.io.File (files and paths)" to Regex("""\bjava\.io\.File\b|(?<![\w.])File\(|\bimport java\.io\.File$"""),
    "file streams" to Regex("""\b(FileInputStream|FileOutputStream|FileReader|FileWriter|RandomAccessFile)\b"""),
    "java.nio.file (files and paths)" to Regex("""\bjava\.nio\.file\b|\bFiles\.|\bPaths\.|\bPath\.of\("""),
    "the network" to Regex("""\bjava\.net\.(http|Socket|ServerSocket|URL\b|URLConnection|InetAddress|DatagramSocket)|\b(HttpClient|HttpRequest|HttpResponse|Socket|ServerSocket|URLConnection)\b"""),
    "the classpath (resources)" to Regex("""\bgetResource(AsStream)?\(|\bgetSystemResource"""),
    "processes" to Regex("""\b(ProcessBuilder|ProcessHandle)\b|\bRuntime\.getRuntime\("""),
    "environment and properties" to Regex("""\bSystem\.(getenv|getProperty|getProperties|setProperty|clearProperty)\b"""),
    "standard streams and exit" to Regex("""\bSystem\.(out|err|`in`|in|exit|setOut|setErr|setIn)\b|\b(println|print|readLine)\("""),
    "WASI options that inherit the OS" to Regex("""\b(WasiOptions|WasiPreview1)\b|\binheritSystem\("""),
    "JDK-internal servers" to Regex("""\bcom\.sun\.net\b"""),
)

/** The source with its comments blanked (line structure kept, so line numbers stay right). */
fun stripComments(src: String): String {
    val noBlocks = Regex("""/\*[\s\S]*?\*/""").replace(src) { m -> m.value.replace(Regex("[^\n]"), " ") }
    return noBlocks.lines().joinToString("\n") { line -> line.replace(Regex("""(^|\s)//.*$"""), "$1") }
}

/** Every forbidden use in [src]: "line: family: text". */
fun violations(src: String): List<String> =
    stripComments(src).lines().flatMapIndexed { i, line ->
        FORBIDDEN.filter { (_, re) -> re.containsMatchIn(line) }.map { (name, _) -> "${i + 1}: $name: ${line.trim()}" }
    }

fun scan(): Int {
    val dir = File("src/main/kotlin")
    if (!dir.isDirectory) { System.err.println("❌ check-host: no src/main/kotlin here (run from the repository root)"); return 1 }
    val files = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.sortedBy { it.path }.toList()
    if (files.none { it.name == HOST_FILE }) { System.err.println("❌ check-host: $HOST_FILE is missing"); return 1 }
    val problems = files.filter { it.name != HOST_FILE }.flatMap { f ->
        violations(f.readText()).map { "${f.invariantSeparatorsPath}:$it" }
    }
    if (problems.isNotEmpty()) {
        System.err.println("❌ check-host: ${problems.size} OS call(s) outside $HOST_FILE:")
        problems.forEach { System.err.println("  $it") }
        return 1
    }
    println("check-host: only $HOST_FILE touches the OS (${files.size} main source files)")
    return 0
}

fun selftest(): Int {
    var n = 0
    val bad = mutableListOf<String>()
    fun caught(src: String) { n++; if (violations(src).isEmpty()) bad += "not caught: $src" }
    fun spared(src: String) { n++; if (violations(src).isNotEmpty()) bad += "falsely caught: $src -> ${violations(src)}" }

    caught("val f = java.io.File(path)")
    caught("import java.io.File")
    caught("val f = File(\"x\")")
    caught("FileInputStream(path).use { }")
    caught("java.nio.file.Files.readAllBytes(p)")
    caught("Files.createTempDirectory(\"x\")")
    caught("Path.of(\"x\")")
    caught("val c = HttpClient.newBuilder().build()")
    caught("java.net.http.HttpRequest.newBuilder()")
    caught("java.net.Socket(\"h\", 1)")
    caught("Foo::class.java.getResourceAsStream(\"/x\")")
    caught("ProcessBuilder(\"ls\").start()")
    caught("Runtime.getRuntime().exec(\"ls\")")
    caught("System.getenv(\"HOME\")")
    caught("System.getProperty(\"os.name\")")
    caught("System.out.println(x)")
    caught("println(x)")
    caught("WasiOptions.builder().inheritSystem().build()")
    caught("com.sun.net.httpserver.HttpServer.create()")

    spared("throw java.io.FileNotFoundException(\"WASM file not found: \$path\")")
    spared("catch (e: java.io.IOException) { }")
    spared("val uri = java.net.URI.create(url)")
    spared("if (source.startsWith(\"http://\")) {}")
    spared("val x = readFile(y)")
    spared("// HttpClient and System.getenv are named in a comment only")
    spared("/* a File( in a block comment */ val ok = 1")
    spared("val n = profile.size  // ProcessBuilder in a trailing comment")
    spared("Host.readLocal(path)")

    if (bad.isNotEmpty()) { System.err.println("❌ check-host selftest: ${bad.size} of $n FAILED:\n" + bad.joinToString("\n") { "  - $it" }); return 1 }
    println("check-host selftest: $n checks passed")
    return 0
}

exitProcess(if (args.firstOrNull() == "selftest") selftest() else scan())
