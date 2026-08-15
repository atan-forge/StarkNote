package com.stark.note.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSearchTest {
    @Test
    fun findsAllCaseInsensitiveMatchesInFullBody() {
        val matches = NoteSearch.findMatches("Alpha beta alpha\nbottom ALPHA", "alpha")

        assertEquals(
            listOf(
                SearchMatch(0, 5),
                SearchMatch(11, 16),
                SearchMatch(24, 29)
            ),
            matches
        )
    }

    @Test
    fun emptyQueryClearsMatches() {
        assertEquals(emptyList<SearchMatch>(), NoteSearch.findMatches("Alpha", ""))
        assertEquals(emptyList<SearchMatch>(), NoteSearch.findMatches("Alpha", "   "))
    }

    @Test
    fun findsMatchAtEndOfLongBody() {
        val body = "top\n".repeat(500) + "target"

        assertEquals(
            listOf(SearchMatch(body.length - 6, body.length)),
            NoteSearch.findMatches(body, "target")
        )
    }

    @Test
    fun nextAndPreviousSearchIndicesWrap() {
        val matches = NoteSearch.findMatches("one two one", "one")

        var selected = 0
        selected = (selected + 1).mod(matches.size)
        assertEquals(1, selected)
        selected = (selected - 1 + matches.size).mod(matches.size)
        assertEquals(0, selected)
    }
}
