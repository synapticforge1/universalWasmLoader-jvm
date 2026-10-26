package com.jrmarcum.universalwasmloader

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.types.ValueType

internal class MemRef {
    @Volatile
    var current: com.dylibso.chicory.runtime.Memory? = null
}

// Detected at instantiation time; drives string encoding/decoding paths.
internal enum class StringAbi { CANONICAL, WASIC, NONE }

internal object Abi {

    fun witTypeToParamTypes(type: String): List<ValueType> = when (type) {
        "s32", "bool" -> listOf(ValueType.I32)
        "s64"         -> listOf(ValueType.I64)
        "f32"         -> listOf(ValueType.F32)
        "f64"         -> listOf(ValueType.F64)
        "string"      -> listOf(ValueType.I32, ValueType.I32)
        else          -> listOf(ValueType.I32)
    }

    // Profile-aware. Under SPEC 3.0.0, a CANONICAL `string` return is the
    // callee-allocated single i32 pointer to a `[ptr, len]` pair, so the export
    // returns one i32. The WASIC profile keeps its void side-channel return
    // (ptr/len read from exported globals after a void call).
    fun witTypeToReturnTypes(type: String?, stringAbi: StringAbi = StringAbi.NONE): List<ValueType> = when (type) {
        null           -> emptyList()
        "string"       -> if (stringAbi == StringAbi.CANONICAL) listOf(ValueType.I32) else emptyList()
        "s32", "bool"  -> listOf(ValueType.I32)
        "s64"          -> listOf(ValueType.I64)
        "f32"          -> listOf(ValueType.F32)
        "f64"          -> listOf(ValueType.F64)
        else           -> listOf(ValueType.I32)
    }

    // Encode a JVM value to one or more raw longs according to its WIT type.
    // String params are allocated in WASM linear memory via the appropriate ABI.
    fun encodeArg(instance: Instance, type: String, value: Any?, stringAbi: StringAbi): List<Long> = when (type) {
        "s32"  -> listOf(((value as? Number)?.toInt() ?: 0).toLong())
        "s64"  -> listOf((value as? Number)?.toLong() ?: 0L)
        "f32"  -> listOf(((value as? Number)?.toFloat() ?: 0f).toBits().toLong())
        "f64"  -> listOf(((value as? Number)?.toDouble() ?: 0.0).toBits())
        "bool" -> listOf(if (value as? Boolean == true) 1L else 0L)
        "string" -> {
            val bytes = (value?.toString() ?: "").toByteArray(Charsets.UTF_8)
            val ptr = when (stringAbi) {
                StringAbi.CANONICAL ->
                    instance.export("cabi_realloc").apply(0L, 0L, 1L, bytes.size.toLong())[0].toInt()
                StringAbi.WASIC ->
                    instance.export("__malloc").apply(bytes.size.toLong())[0].toInt()
                StringAbi.NONE ->
                    throw IllegalStateException("Module has no string allocator (cabi_realloc or __malloc)")
            }
            instance.memory().write(ptr, bytes)
            listOf(ptr.toLong(), bytes.size.toLong())
        }
        else -> listOf(((value as? Number)?.toInt() ?: 0).toLong())
    }

    // Decode a WASM result (LongArray) to a JVM value according to the WIT return type.
    // String returns are handled separately in callWithAbi — not here.
    fun decodeResult(type: String?, wasmResult: LongArray): Any? = when (type) {
        null   -> null
        "s32"  -> wasmResult[0].toInt()
        "s64"  -> wasmResult[0]
        "f32"  -> Float.fromBits(wasmResult[0].toInt())
        "f64"  -> Double.fromBits(wasmResult[0])
        "bool" -> wasmResult[0] != 0L
        else   -> wasmResult[0].toInt()
    }

    // Raw mode (no WIT): decode a result slot by the export's own wasm result type,
    // as JS raw WebAssembly.Exports do. Before 0.3.0 the slot came back as a raw Long,
    // so an f64 result was its bit pattern.
    fun decodeRawValue(type: ValueType, value: Long): Any = when (type) {
        ValueType.I32 -> value.toInt()
        ValueType.I64 -> value
        ValueType.F32 -> Float.fromBits(value.toInt())
        ValueType.F64 -> Double.fromBits(value)
        else          -> value   // v128 / reference slots: passed through untouched
    }

    // Raw mode (no WIT): convert a JVM argument to the export's own wasm parameter type.
    // Numbers convert like JS ToNumber (an Int passed to an f64 parameter is 2 → 2.0);
    // a Boolean is 1 / 0. Anything else throws — it used to become 0 silently.
    fun encodeRawArg(fnName: String, index: Int, type: ValueType, value: Any?): Long {
        val n: Number = when (value) {
            is Number  -> value
            is Boolean -> if (value) 1 else 0
            else -> throw IllegalArgumentException(
                "argument ${index + 1} of '$fnName' is ${value?.let { it::class.simpleName } ?: "null"}; " +
                    "raw mode (no .wit) passes numbers and booleans only — strings and other types need the module's .wit"
            )
        }
        return when (type) {
            ValueType.I32 -> n.toInt().toLong()
            ValueType.I64 -> n.toLong()
            ValueType.F32 -> n.toFloat().toRawBits().toLong()
            ValueType.F64 -> n.toDouble().toRawBits()
            else -> throw IllegalArgumentException(
                "parameter ${index + 1} of '$fnName' has wasm type $type, which raw mode cannot pass"
            )
        }
    }

    // Build the Chicory host functions for the WIT import section (the "env" namespace).
    // memRef.current must be set to instance.memory() after instantiation for string decoding.
    fun buildEnvFunctions(
        importFuncs: List<WitFunction>,
        userCallbacks: Callbacks,
        memRef: MemRef
    ): List<HostFunction> {
        val hostFunctions = importFuncs.map { fn ->
            val wasmKey    = fn.name.replace('-', '_')  // env-mul → env_mul
            val camelKey   = fn.camelName               // env-mul → envMul (user callback key)
            val params     = fn.params
            val resultType = fn.result

            val paramTypes  = params.flatMap { witTypeToParamTypes(it.type) }
            val returnTypes = witTypeToReturnTypes(resultType)

            HostFunction("env", wasmKey, paramTypes, returnTypes) { _, rawArgs ->
                val jsArgs = mutableListOf<Any?>()
                var i = 0
                for (p in params) {
                    when (p.type) {
                        "string" -> {
                            val ptr = rawArgs[i++].toInt()
                            val len = rawArgs[i++].toInt()
                            val mem = memRef.current
                                ?: error("MemRef not set — memory unavailable in import callback")
                            jsArgs.add(String(mem.readBytes(ptr, len), Charsets.UTF_8))
                        }
                        "bool" -> jsArgs.add(rawArgs[i++] != 0L)
                        "s64"  -> jsArgs.add(rawArgs[i++])
                        "f32"  -> jsArgs.add(Float.fromBits(rawArgs[i++].toInt()))
                        "f64"  -> jsArgs.add(Double.fromBits(rawArgs[i++]))
                        else   -> jsArgs.add(rawArgs[i++].toInt())
                    }
                }

                val ret = userCallbacks.invoke(camelKey, jsArgs)

                when (resultType) {
                    null   -> longArrayOf()
                    "bool" -> longArrayOf(if (ret as? Boolean == true) 1L else 0L)
                    "s64"  -> longArrayOf((ret as? Number)?.toLong() ?: 0L)
                    "f32"  -> longArrayOf(((ret as? Number)?.toFloat() ?: 0f).toBits().toLong())
                    "f64"  -> longArrayOf(((ret as? Number)?.toDouble() ?: 0.0).toBits())
                    else   -> longArrayOf(((ret as? Number)?.toInt() ?: 0).toLong())
                }
            }
        }

        return hostFunctions
    }
}
