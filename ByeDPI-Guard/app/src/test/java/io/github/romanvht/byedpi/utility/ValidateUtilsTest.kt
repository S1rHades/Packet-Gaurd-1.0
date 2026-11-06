package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** checkIp/checkNotLocalIp call into android.net and are left to on-device testing. */
class ValidateUtilsTest {

    @Test
    fun port_acceptsTheValidRange() {
        listOf("1", "80", "1080", "65535").forEach { assertTrue(it, checkPort(it)) }
    }

    @Test
    fun port_rejectsOutOfRangeAndNonNumbers() {
        listOf("0", "65536", "-1", "", " 80", "80a", "99999999999").forEach { assertFalse(it, checkPort(it)) }
    }

    @Test
    fun domain_acceptsOrdinaryNames() {
        listOf("google.com", "www.iana.org", "a.b").forEach { assertTrue(it, checkDomain(it)) }
    }

    @Test
    fun domain_rejectsMalformedNames() {
        listOf("", "localhost", ".example.com", "example.com.", "a".repeat(250) + ".com")
            .forEach { assertFalse(it, checkDomain(it)) }
    }
}
