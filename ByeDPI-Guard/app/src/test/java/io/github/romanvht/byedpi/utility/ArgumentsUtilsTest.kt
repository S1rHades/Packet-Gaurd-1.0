package io.github.romanvht.byedpi.utility

import org.junit.Assert.assertEquals
import org.junit.Test

/** shellSplit turns a strategy command line into the argv handed to the native engine. */
class ArgumentsUtilsTest {

    @Test
    fun splitsOnAnyWhitespace() {
        assertEquals(listOf("-o1", "-a1", "-r-5+se"), shellSplit("  -o1\t-a1 \n -r-5+se  "))
    }

    @Test
    fun emptyInputGivesNoArguments() {
        assertEquals(emptyList<String>(), shellSplit(""))
        assertEquals(emptyList<String>(), shellSplit("   "))
    }

    @Test
    fun quotesKeepSpacesAndAreRemoved() {
        assertEquals(listOf("-f-1", "--fake-sni", "my host.com"), shellSplit("-f-1 --fake-sni \"my host.com\""))
        assertEquals(listOf("-H", "a b"), shellSplit("-H 'a b'"))
    }

    @Test
    fun quotedPartJoinsTheWordAroundIt() {
        assertEquals(listOf("--sni=google.com"), shellSplit("--sni=\"google.com\""))
    }

    @Test
    fun emptyQuotesProduceAnEmptyArgument() {
        assertEquals(listOf("-x", "", "-y"), shellSplit("-x \"\" -y"))
        assertEquals(listOf("-x", ""), shellSplit("-x ''"))
    }

    @Test
    fun otherQuoteTypeIsLiteralInsideQuotes() {
        assertEquals(listOf("it's"), shellSplit("\"it's\""))
    }

    @Test
    fun backslashEscapesOnlyTheClosingQuote() {
        assertEquals(listOf("say \"hi\""), shellSplit("\"say \\\"hi\\\"\""))
        assertEquals(listOf("a\\b"), shellSplit("\"a\\b\""))
    }

    @Test
    fun benchmarkSniPlaceholderSurvives() {
        // BenchmarkViewModel substitutes {sni} with a quoted host before the command is split.
        val command = "-f-1 -s1 --fake-sni {sni}".replace("{sni}", "\"google.com\"")
        assertEquals(listOf("-f-1", "-s1", "--fake-sni", "google.com"), shellSplit(command))
    }
}
