package com.jrmarcum.universalwasmloader

data class WitParam(val name: String, val type: String)

data class WitFunction(
    val name: String,       // kebab-case — WIT source name
    val camelName: String,  // camelCase — actual WASM binary export/import key
    val params: List<WitParam>,
    val result: String?     // null means no return value
)

data class WitDocument(
    val packageName: String,
    val worldName: String,
    val imports: List<WitFunction>,
    val exports: List<WitFunction>
)

/** A `.wit` that does not parse (SPEC 4.0.0 §2 "Parsing"); the loader adds the `.wit`'s path. */
internal class WitSyntaxException(message: String) : IllegalArgumentException(message)

/**
 * The strict WIT reader of SPEC 4.0.0 §2, ported from universalWasmLoader-js 2.0.0's
 * `wit-parser.js` (same grammar, same messages): an optional `package` line and one `world`
 * holding only `import` / `export` function statements, with `//` comments. Anything else
 * throws [WitSyntaxException] — a `.wit` is never read loosely (3.0.0 read `u32` as `s32`
 * and dropped a parameter written without a comma).
 */
internal object WitParser {

    /** The WIT types this loader translates (SPEC §4); any other type is an error. */
    private val WIT_TYPES = listOf("s32", "s64", "f32", "f64", "bool", "string")

    /** A WIT identifier: kebab-case words of letters and digits. */
    private val IDENT = Regex("""^[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*$""")

    /** One `import` / `export` statement of a world body, without its `;`. */
    private val FUNC_STMT =
        Regex("""^(import|export)\s+([^\s:]+)\s*:\s*func\s*\(([^()]*)\)\s*(?:->\s*(\S+))?$""")

    private val PARAM = Regex("""^([^:\s]+)\s*:\s*(\S+)$""")
    private val PACKAGE = Regex("""^package\s+([^\s;]+)\s*;""")
    private val WORLD = Regex("""^world\s+([^\s{]+)\s*\{([^{}]*)\}$""")
    private val COMMENT = Regex("""//[^\n]*""")
    private val WHITESPACE = Regex("""\s+""")

    fun kebabToCamel(name: String): String {
        val parts = name.split("-")
        return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun fail(message: String): Nothing = throw WitSyntaxException(message)

    private fun parseWitType(raw: String, where: String): String {
        val t = raw.trim()
        if (t !in WIT_TYPES) fail("unsupported WIT type \"$t\" in $where (supported: ${WIT_TYPES.joinToString(", ")})")
        return t
    }

    private fun parseWitParams(raw: String, fnLabel: String): List<WitParam> {
        // A trailing comma is valid WIT: `func(a: s32,)`.
        val parts = raw.split(",").map { it.trim() }.toMutableList()
        if (parts.isNotEmpty() && parts.last() == "") parts.removeAt(parts.size - 1)
        val params = mutableListOf<WitParam>()
        val seen = mutableSetOf<String>()
        for (part in parts) {
            val m = PARAM.find(part)
            if (m == null || !IDENT.matches(m.groupValues[1])) {
                fail("malformed parameter \"$part\" in $fnLabel (expected \"name: type\")")
            }
            val name = m.groupValues[1]
            if (!seen.add(name)) fail("duplicate parameter \"$name\" in $fnLabel")
            params += WitParam(name, parseWitType(m.groupValues[2], "$fnLabel, parameter $name"))
        }
        return params
    }

    fun parse(src: String): WitDocument {
        val code = src.removePrefix("﻿").replace(COMMENT, "")

        var rest = code.trim()
        var packageName = ""
        PACKAGE.find(rest)?.let { pkg ->
            packageName = pkg.groupValues[1]
            rest = rest.substring(pkg.value.length).trim()
        }

        val world = WORLD.find(rest) ?: fail(
            "not a WIT world: " +
                if (rest.isEmpty()) "it is empty" else "expected one \"world <name> { ... }\" block, found: ${rest.take(60)}"
        )
        val worldName = world.groupValues[1]
        if (!IDENT.matches(worldName)) fail("malformed world name \"$worldName\"")

        val imports = mutableListOf<WitFunction>()
        val exports = mutableListOf<WitFunction>()
        val statements = world.groupValues[2].split(";").map { it.trim() }.toMutableList()
        val last = statements.removeAt(statements.size - 1)
        if (last != "") fail("unterminated WIT statement (missing \";\"): $last")
        for (stmt in statements) {
            val m = FUNC_STMT.find(stmt)
                ?: fail("unsupported or malformed WIT statement: ${stmt.replace(WHITESPACE, " ")}")
            val keyword = m.groupValues[1]
            val name = m.groupValues[2]
            val fnLabel = "$keyword $name"
            if (!IDENT.matches(name)) fail("malformed function name in $fnLabel")
            val list = if (keyword == "import") imports else exports
            if (list.any { it.name == name }) fail("duplicate $fnLabel")
            val params = parseWitParams(m.groupValues[3], fnLabel)
            val result = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { parseWitType(it, "$fnLabel, result") }
            list += WitFunction(name, kebabToCamel(name), params, result)
        }

        return WitDocument(packageName, worldName, imports, exports)
    }
}
