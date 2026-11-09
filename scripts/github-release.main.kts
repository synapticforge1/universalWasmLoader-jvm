// github-release.main.kts — the publish workflow's `release` job, through GitHub's REST API.
//
// Workspace H30 / Tooling rule 9 (owner, 2026-11-08): no GitHub CLI, in CI too. This replaces the
// job's two CLI calls (create the release/<tag> branch; create the GitHub Release) with plain
// HTTPS requests made by the JDK alone: no dependency is resolved, so no package code runs beside
// the job's write token (workspace C30).
//
// Usage:
//   kotlin scripts/github-release.main.kts release <tag> <commit-sha> [--dry-run]
//   kotlin scripts/github-release.main.kts selftest
// Environment: GITHUB_REPOSITORY (owner/repo), GITHUB_TOKEN (needed for writes; a dry run reads
// the public API without it), GITHUB_API_URL (default https://api.github.com; the self-test
// points it at a mock).
//
// Order — the workspace's H32 recipe (owner, 2026-11-09), kept as written:
//  1. The tag must already exist on GitHub and peel (annotated tags followed) to <commit-sha>.
//     POST /releases would otherwise CREATE a missing tag at the default branch — the CLI's
//     --verify-tag used to refuse that (hazard found by loader-rs, 2026-11-09).
//  2. A GitHub Release already there = done, with NO write (so a re-run is safe).
//  3. Otherwise: release/<tag> (kept when it points at <commit-sha>, refused when it points
//     elsewhere, created and read back when missing; the Release targets it), then POST the
//     Release with generated notes and the Maven Central link on top, read it back, and re-check
//     the tag.
// Every network call is held to its own time limit, request to the last byte of the body
// (workspace best-practice 7.21). Every refusal happens before any write.
// Exit 0 = done (or nothing to do), 1 = refused or failed.

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

// UTF-8 on both streams, as compat.main.kts does: a redirected JVM stream otherwise uses the
// Windows code page and prints ✓ / ❌ as "?".
System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))

val TAG_FORM = Regex("""^v\d+\.\d+\.\d+$""")
val SHA_FORM = Regex("""^[0-9a-f]{40}$""")
val REPO_FORM = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")

class Api(
    base: String,
    private val token: String?,
    val repo: String,
    private val limitMs: Long = 30_000,   // per request, to the last byte of the body
    private val backoffMs: Long = 2_000,
) {
    private val base = base.trimEnd('/')
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(minOf(limitMs, 10_000))).build()

    /**
     * One request, held to [limitMs] from the request to the last byte (HttpRequest.timeout alone
     * stops counting when the headers arrive). A GET is retried on a 5xx, an I/O error or the time
     * limit (3 attempts); a write never is.
     */
    fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
        val attempts = if (method == "GET") 3 else 1
        var last: Exception? = null
        for (n in 1..attempts) {
            val b = HttpRequest.newBuilder(URI.create("$base/repos/$repo$path"))
                .timeout(Duration.ofMillis(limitMs))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "universalWasmLoader-jvm-release")
            if (!token.isNullOrEmpty()) b.header("Authorization", "Bearer $token")
            if (body != null) {
                b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body))
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody())
            }
            val pending = client.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
            try {
                val r = pending.get(limitMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (r.statusCode() >= 500 && n < attempts) { Thread.sleep(backoffMs * n); continue }
                return r.statusCode() to r.body()
            } catch (e: java.util.concurrent.TimeoutException) {
                pending.cancel(true)
                last = java.io.IOException("$method $path did not complete within $limitMs ms")
            } catch (e: java.util.concurrent.ExecutionException) {
                last = (e.cause as? Exception) ?: e
            }
            if (n < attempts) Thread.sleep(backoffMs * n)
        }
        throw last ?: IllegalStateException("$method $path failed")
    }
}

// The few fields read from GitHub's JSON, without a JSON library (none is resolved here).
fun field(json: String, key: String): String? =
    Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)

fun objectField(json: String, key: String): String? =
    Regex("\"object\"\\s*:\\s*\\{([^{}]*)\\}").find(json)?.groupValues?.get(1)?.let { field(it, key) }

class Refused(message: String) : Exception(message)

/** The commit the tag peels to on GitHub, or a refusal. */
fun peeledTag(api: Api, tag: String): String {
    val (status, body) = api.call("GET", "/git/ref/tags/$tag")
    if (status == 404) throw Refused("tag $tag is not on GitHub (creating a release would CREATE it at the default branch)")
    if (status != 200) throw Refused("reading tag $tag: HTTP $status")
    var type = objectField(body, "type")
    var sha = objectField(body, "sha")
    var hops = 0
    while (type == "tag" && sha != null && hops++ < 5) {
        val (s2, b2) = api.call("GET", "/git/tags/$sha")
        if (s2 != 200) throw Refused("reading annotated tag object $sha: HTTP $s2")
        type = objectField(b2, "type")
        sha = objectField(b2, "sha")
    }
    if (type != "commit" || sha == null) throw Refused("tag $tag does not resolve to a commit (type $type)")
    return sha
}

/** The publish workflow's release step. Returns the exit code. */
fun release(api: Api, tag: String, sha: String, dryRun: Boolean, hasToken: Boolean): Int {
    try {
        if (!TAG_FORM.matches(tag)) throw Refused("'$tag' is not a vX.Y.Z tag")
        if (!SHA_FORM.matches(sha)) throw Refused("'$sha' is not a 40-hex commit id")
        if (!REPO_FORM.matches(api.repo)) throw Refused("'${api.repo}' is not owner/repo")
        if (!dryRun && !hasToken) throw Refused("GITHUB_TOKEN is not set; only --dry-run runs without it")

        // 1. The tag, first: nothing is written unless it is the tested commit.
        val at = peeledTag(api, tag)
        if (at != sha) throw Refused("tag $tag is at $at on GitHub, not $sha")
        println("✓ tag $tag → $sha on GitHub")

        // 2. A Release already there = done, with no write (H32).
        val (gs, gb) = api.call("GET", "/releases/tags/$tag")
        when {
            gs == 200 -> { println("✓ GitHub Release $tag already exists, nothing written: ${field(gb, "html_url")}"); return 0 }
            gs != 404 -> throw Refused("reading release $tag: HTTP $gs")
        }

        // 3a. release/<tag>, which the Release targets
        val branch = "release/$tag"
        val (bs, bb) = api.call("GET", "/git/ref/heads/$branch")
        when {
            bs == 200 -> {
                val bsha = objectField(bb, "sha")
                if (bsha != sha) throw Refused("branch $branch exists at $bsha, not $sha")
                println("✓ branch $branch already at $sha")
            }
            bs != 404 -> throw Refused("reading branch $branch: HTTP $bs")
            dryRun -> println("would create branch $branch at $sha")
            else -> {
                val (cs, cb) = api.call("POST", "/git/refs", """{"ref":"refs/heads/$branch","sha":"$sha"}""")
                if (cs != 201) throw Refused("creating branch $branch: HTTP $cs ${cb.take(200)}")
                val (rs, rb) = api.call("GET", "/git/ref/heads/$branch")
                if (rs != 200 || objectField(rb, "sha") != sha) throw Refused("branch $branch did not read back at $sha")
                println("✓ created branch $branch at $sha (read back)")
            }
        }

        // 3b. The GitHub Release: generated notes, with the registry link on top (H32). GitHub
        // prepends `body` to the generated notes.
        if (dryRun) { println("would create GitHub Release $tag (target $branch, Maven Central link + generated notes)"); return 0 }
        val version = tag.removePrefix("v")
        val registry = "Maven Central: [io.github.jrmarcum:universal-wasm-loader-jvm:$version]" +
            "(https://central.sonatype.com/artifact/io.github.jrmarcum/universal-wasm-loader-jvm/$version)"
        val body = """{"tag_name":"$tag","target_commitish":"$branch","name":"$tag",""" +
            """"body":"$registry","generate_release_notes":true}"""
        val (ps, pb) = api.call("POST", "/releases", body)
        if (ps != 201) throw Refused("creating GitHub Release $tag: HTTP $ps ${pb.take(200)}")
        val (vs, vb) = api.call("GET", "/releases/tags/$tag")
        if (vs != 200 || field(vb, "tag_name") != tag) throw Refused("GitHub Release $tag did not read back (HTTP $vs)")
        // The tag must not have moved (a release never moves an existing tag; checked anyway).
        val after = peeledTag(api, tag)
        if (after != sha) throw Refused("after the release, tag $tag is at $after, not $sha")
        println("✓ created GitHub Release $tag: ${field(vb, "html_url")} (read back; tag still $sha)")
        return 0
    } catch (e: Refused) {
        System.err.println("❌ github-release: ${e.message}")
        return 1
    } catch (e: Exception) {
        System.err.println("❌ github-release: ${e::class.simpleName}: ${e.message}")
        return 1
    }
}

// ---------------------------------------------------------------------------------------------
// selftest: a mock GitHub API in-process (com.sun.net.httpserver, part of the JDK)
// ---------------------------------------------------------------------------------------------

class Mock : AutoCloseable {
    val tags = mutableMapOf<String, Pair<String, String>>()   // name → (type, sha)
    val tagObjects = mutableMapOf<String, String>()             // annotated tag object → commit
    val branches = mutableMapOf<String, String>()
    val releases = mutableSetOf<String>()
    val log = mutableListOf<String>()
    var auth: String? = null
    var lastBody: String = ""
    var failReleasePost = false
    @Volatile var stallMs = 0L          // > 0: every answer waits this long before its headers
    @Volatile var stallBodyMs = 0L      // > 0: headers and one byte go out, then the body stalls
    private val pool = java.util.concurrent.Executors.newCachedThreadPool()
    private val server = com.sun.net.httpserver.HttpServer.create(
        java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0
    )
    val url: String

    init {
        server.executor = pool
        server.createContext("/") { ex ->
            val path = ex.requestURI.path.removePrefix("/repos/o/r")
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            synchronized(log) { log += "${ex.requestMethod} $path" }
            if (stallMs > 0) try { Thread.sleep(stallMs) } catch (_: InterruptedException) {}
            if (ex.requestMethod != "GET") { auth = ex.requestHeaders.getFirst("Authorization"); lastBody = body }
            fun send(status: Int, json: String) {
                val bytes = json.toByteArray()
                ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                if (bytes.isNotEmpty()) ex.responseBody.use {
                    if (stallBodyMs > 0) {
                        it.write(bytes, 0, 1); it.flush()
                        try { Thread.sleep(stallBodyMs) } catch (_: InterruptedException) {}
                        it.write(bytes, 1, bytes.size - 1)
                    } else it.write(bytes)
                }
                ex.close()
            }
            val obj = { type: String, sha: String -> """{"object":{"sha":"$sha","type":"$type","url":"x"}}""" }
            when {
                ex.requestMethod == "GET" && path.startsWith("/git/ref/tags/") ->
                    tags[path.removePrefix("/git/ref/tags/")]?.let { send(200, obj(it.first, it.second)) } ?: send(404, "{}")
                ex.requestMethod == "GET" && path.startsWith("/git/tags/") ->
                    tagObjects[path.removePrefix("/git/tags/")]?.let { send(200, obj("commit", it)) } ?: send(404, "{}")
                ex.requestMethod == "GET" && path.startsWith("/git/ref/heads/") ->
                    branches[path.removePrefix("/git/ref/heads/")]?.let { send(200, obj("commit", it)) } ?: send(404, "{}")
                ex.requestMethod == "POST" && path == "/git/refs" -> {
                    val ref = field(body, "ref")!!.removePrefix("refs/heads/")
                    branches[ref] = field(body, "sha")!!
                    send(201, "{}")
                }
                ex.requestMethod == "GET" && path.startsWith("/releases/tags/") -> {
                    val t = path.removePrefix("/releases/tags/")
                    if (t in releases) send(200, """{"tag_name":"$t","html_url":"https://example/$t"}""") else send(404, "{}")
                }
                ex.requestMethod == "POST" && path == "/releases" -> {
                    if (failReleasePost) send(422, """{"message":"Validation Failed"}""")
                    else { releases += field(body, "tag_name")!!; send(201, "{}") }
                }
                else -> send(404, "{}")
            }
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}"
    }

    fun writes() = synchronized(log) { log.filter { !it.startsWith("GET ") } }
    override fun close() { server.stop(0); pool.shutdownNow() }
}

fun selftest(): Int {
    val a = "a".repeat(40)
    val b = "b".repeat(40)
    var failed = 0
    var checks = 0
    fun check(name: String, ok: Boolean) {
        checks++
        if (ok) println("  ✓ $name") else { failed++; println("  ✗ $name") }
    }
    fun run(m: Mock, tag: String, sha: String, dry: Boolean = false, token: String? = "t0k", limitMs: Long = 30_000) =
        release(Api(m.url, token, "o/r", limitMs, backoffMs = 10), tag, sha, dry, !token.isNullOrEmpty())

    Mock().use { m ->
        m.tags["v1.0.0"] = "commit" to a
        check("create: exit 0", run(m, "v1.0.0", a) == 0)
        check("create: branch release/v1.0.0 at the commit", m.branches["release/v1.0.0"] == a)
        check("create: release created", "v1.0.0" in m.releases)
        check("create: Bearer token sent on writes", m.auth == "Bearer t0k")
        check("create: generated notes, target the release branch",
            m.lastBody.contains("\"generate_release_notes\":true") && m.lastBody.contains("\"target_commitish\":\"release/v1.0.0\""))
        check("create: the Maven Central link on top of the notes",
            m.lastBody.contains("central.sonatype.com/artifact/io.github.jrmarcum/universal-wasm-loader-jvm/1.0.0"))
        m.log.clear()
        check("re-run: exit 0", run(m, "v1.0.0", a) == 0)
        check("re-run: no write", m.writes().isEmpty())
    }
    Mock().use { m ->
        m.tags["v1.0.1"] = "tag" to "c".repeat(40)
        m.tagObjects["c".repeat(40)] = a
        check("annotated tag is peeled", run(m, "v1.0.1", a) == 0 && "v1.0.1" in m.releases)
    }
    Mock().use { m ->
        m.tags["v1.0.0"] = "commit" to b
        check("tag at another commit: refused", run(m, "v1.0.0", a) == 1)
        check("tag at another commit: no write", m.writes().isEmpty())
    }
    Mock().use { m ->
        check("tag missing: refused", run(m, "v9.9.9", a) == 1)
        check("tag missing: no write (a release would create the tag)", m.writes().isEmpty())
    }
    Mock().use { m ->
        m.tags["v1.0.0"] = "commit" to a
        m.branches["release/v1.0.0"] = b
        check("branch at another commit: refused, no write", run(m, "v1.0.0", a) == 1 && m.writes().isEmpty())
    }
    Mock().use { m ->
        // H32 order: an existing Release is done with NO write, even if release/<tag> is missing.
        m.tags["v1.0.0"] = "commit" to a
        m.releases += "v1.0.0"
        check("existing Release, branch missing: exit 0, no write", run(m, "v1.0.0", a) == 0 && m.writes().isEmpty())
    }
    Mock().use { m ->
        m.tags["v1.0.0"] = "commit" to a
        check("dry run: exit 0, no write", run(m, "v1.0.0", a, dry = true, token = null) == 0 && m.writes().isEmpty())
    }
    Mock().use { m ->
        m.tags["v1.0.0"] = "commit" to a
        check("no token without --dry-run: refused, no request", run(m, "v1.0.0", a, token = null) == 1 && m.log.isEmpty())
    }
    Mock().use { m ->
        // Every call has its own time limit (best-practice 7.21): a stalled API fails, fast.
        m.tags["v1.0.0"] = "commit" to a
        m.stallMs = 5_000
        val t0 = System.nanoTime()
        val code = run(m, "v1.0.0", a, limitMs = 200)
        val ms = (System.nanoTime() - t0) / 1_000_000
        check("stalled API: exit 1 within the limit (3 × 200 ms + backoff; took $ms ms)", code == 1 && ms < 3_000)
        check("stalled API: no write", m.writes().isEmpty())
    }
    Mock().use { m ->
        // The limit runs to the last byte: HttpRequest.timeout alone ends at the headers.
        m.tags["v1.0.0"] = "commit" to a
        m.stallBodyMs = 5_000
        val t0 = System.nanoTime()
        val code = run(m, "v1.0.0", a, limitMs = 200)
        val ms = (System.nanoTime() - t0) / 1_000_000
        check("body stalls after the headers: exit 1 within the limit (took $ms ms)", code == 1 && ms < 3_000)
    }
    Mock().use { m ->
        m.tags["v1.0.0"] = "commit" to a
        m.failReleasePost = true
        check("release POST answers 422: exit 1", run(m, "v1.0.0", a) == 1)
    }
    Mock().use { m ->
        check("malformed tag: refused before any request", run(m, "1.0", a) == 1 && m.log.isEmpty())
    }
    println(if (failed == 0) "github-release selftest: $checks checks passed" else "❌ github-release selftest: $failed of $checks checks failed")
    return if (failed == 0) 0 else 1
}

// ---------------------------------------------------------------------------------------------

val argv = args.toList()
val code = when (argv.firstOrNull()) {
    "selftest" -> selftest()
    "release" -> {
        val rest = argv.drop(1).filter { it != "--dry-run" }
        if (rest.size != 2) {
            System.err.println("usage: kotlin scripts/github-release.main.kts release <tag> <commit-sha> [--dry-run]")
            1
        } else {
            val token = System.getenv("GITHUB_TOKEN")
            val api = Api(System.getenv("GITHUB_API_URL") ?: "https://api.github.com", token, System.getenv("GITHUB_REPOSITORY") ?: "")
            release(api, rest[0], rest[1], "--dry-run" in argv, !token.isNullOrEmpty())
        }
    }
    else -> {
        System.err.println("usage: kotlin scripts/github-release.main.kts (release <tag> <sha> [--dry-run] | selftest)")
        1
    }
}
exitProcess(code)
