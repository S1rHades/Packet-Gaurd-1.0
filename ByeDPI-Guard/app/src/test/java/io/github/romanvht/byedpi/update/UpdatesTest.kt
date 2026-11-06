package io.github.romanvht.byedpi.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun versionComparison() {
        assertTrue(isNewer("2.5.0", "2.4.0"))
        assertTrue(isNewer("2.10.0", "2.9.9"))
        assertTrue(isNewer("3", "2.9"))
        assertTrue(isNewer("2.4.1", "2.4"))
        assertFalse(isNewer("2.4.0", "2.4.0"))
        assertFalse(isNewer("2.4", "2.4.0"))
        assertFalse(isNewer("2.3.9", "2.4.0"))
    }

    @Test
    fun debugAndTagSuffixesAreIgnored() {
        assertEquals("2.4.0", versionCore("2.4.0-debug"))
        assertEquals("2.5.0", versionCore("v2.5.0"))
        assertTrue(isNewer("2.5.0", "2.4.0-debug"))
        assertFalse(isNewer("2.4.0", "2.4.0-debug"))
    }

    private val release = """
        {
          "tag_name": "v2.5.0",
          "body": "## What's new\n\n- **In-app updates:** check and install new versions\n  from Settings.\n- Release notes in the app\n  1. nested lines are skipped\n- Uses [GitHub releases](https://github.com) with `signed` APKs\n- A fourth point\n\n## Install\n\n- not a note",
          "assets": [
            {"name": "PacketGuard-v2.5.0-arm64-v8a.apk", "size": 2500000, "browser_download_url": "https://example.com/arm64.apk"},
            {"name": "PacketGuard-v2.5.0-universal.apk", "size": 3100000, "browser_download_url": "https://example.com/universal.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesTheUniversalApkAndNotes() {
        val r = parseRelease(release)!!
        assertEquals("2.5.0", r.version)
        assertEquals("https://example.com/universal.apk", r.apkUrl)
        assertEquals(3_100_000L, r.sizeBytes)
        assertEquals(
            listOf("In-app updates: check and install new versions", "Release notes in the app", "Uses GitHub releases with signed APKs"),
            r.notes
        )
    }

    @Test
    fun releaseWithoutTheUniversalApkOrBrokenJsonIsIgnored() {
        assertNull(parseRelease("""{"assets":[{"name":"PacketGuard-v2.5.0-x86.apk","browser_download_url":"x"}]}"""))
        assertNull(parseRelease("""{"assets":[{"name":"app-universal-debug.apk","browser_download_url":"x"}]}"""))
        assertNull(parseRelease("not json"))
        assertNull(parseRelease("""{"message":"Not Found"}"""))
    }

    @Test
    fun missingOrNullBodyGivesNoNotes() {
        val r = parseRelease(
            """{"body":null,"assets":[{"name":"PacketGuard-v2.5.0-universal.apk","browser_download_url":"u"}]}"""
        )!!
        assertTrue(r.notes.isEmpty())
        assertEquals(0L, r.sizeBytes)
        assertTrue(releaseNotes("No headings here\n- a bullet").isEmpty())
    }
}
