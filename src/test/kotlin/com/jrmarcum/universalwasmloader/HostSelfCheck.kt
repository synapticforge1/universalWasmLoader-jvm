package com.jrmarcum.universalwasmloader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * The on-demand host self-check (letter H40): `./gradlew hostSelfCheck`. Excluded from `test`, so the
 * suite never runs it on its own. It prints one line per operation and fails when any line is a FAIL.
 */
@Tag("host-self-check")
class HostSelfCheck {
    @Test
    fun `Host operations work on this machine`() {
        val lines = Host.selfCheck()
        lines.forEach { println("host self-check: $it") }
        assertEquals(emptyList<String>(), lines.filter { it.startsWith("FAIL") })
        assertEquals(true, lines.size >= 9, "every check ran (${lines.size})")
    }
}
