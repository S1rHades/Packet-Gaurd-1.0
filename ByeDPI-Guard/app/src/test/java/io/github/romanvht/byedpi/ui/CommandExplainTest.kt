package io.github.romanvht.byedpi.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandExplainTest {

    @Test
    fun positions() {
        assertEquals(CmdPos(1), parsePos("1"))
        assertEquals(CmdPos(-5, anchor = CmdPos.Anchor.End), parsePos("-5"))
        assertEquals(CmdPos(1, CmdPos.Field.SiteName), parsePos("1+s"))
        assertEquals(CmdPos(-5, CmdPos.Field.SiteName, CmdPos.Anchor.End), parsePos("-5+se"))
        assertEquals(CmdPos(0, CmdPos.Field.SiteName, CmdPos.Anchor.Middle), parsePos("0+sm"))
        assertEquals(CmdPos(3, CmdPos.Field.HostHeader, repeated = true), parsePos("3:2:1+h"))
        assertNull(parsePos("abc"))
        assertNull(parsePos(""))
    }

    @Test
    fun explainsShortLongAndSeparateValues() {
        val parts = explainCommand("ciadpi -d1 -r1+s --fake -1 --ttl=8 -Ku -An -Qr -S")
        assertEquals(
            listOf(
                Technique.Disorder, Technique.TlsRec, Technique.Fake, Technique.FakeTtl,
                Technique.Proto, Technique.Auto, Technique.FakeTlsMod, Technique.Md5Sig
            ),
            parts.map { it.technique }
        )
        assertEquals(CmdPos(1, CmdPos.Field.SiteName), parts[1].pos)
        assertEquals("--fake -1", parts[2].text)
        assertEquals("8", parts[3].value)
        assertEquals("u", parts[4].value)
    }

    @Test
    fun plumbingAndUnknownOptionsAreSkipped() {
        assertTrue(explainCommand("-i 127.0.0.1 -p 1080 -b 16384 -X9 garbage").isEmpty())
        assertTrue(explainCommand("").isEmpty())
    }
}
