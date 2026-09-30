package com.nirogbhumi.app.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleFeedTest {
    @Test fun htmlBecomesPlainText() {
        assertEquals("Walk after dinner", HtmlText.plain("<p>Walk <strong>after</strong> dinner</p>"))
        assertEquals("It's 5 - 10 minutes...", HtmlText.plain("It&#8217;s 5 &ndash; 10 minutes&hellip;"))
        assertEquals("Tea & honey", HtmlText.plain("Tea &amp; honey"))
        assertEquals("a b", HtmlText.plain("a&nbsp;&nbsp;b"))
        assertEquals("one two", HtmlText.plain("one</p><p>two"))
        assertEquals("", HtmlText.plain(null))
        assertEquals("", HtmlText.plain("   "))
    }

    @Test fun scriptsAndStylesAreDroppedWithTheirContent() {
        assertEquals("Hello", HtmlText.plain("Hello<script>alert(1)</script>"))
        assertEquals("Hello", HtmlText.plain("<style>p{color:red}</style>Hello"))
    }

    @Test fun decodedEntitiesAreNotReinterpretedAsMarkup() {
        assertEquals("<script>alert(1)</script>", HtmlText.plain("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }

    @Test fun numericEntitiesDecodeAndBrokenOnesStay() {
        assertEquals("A", HtmlText.plain("&#65;"))
        assertEquals("A", HtmlText.plain("&#x41;"))
        assertEquals("&#99999999;", HtmlText.plain("&#99999999;"))
        assertEquals("&notanentity;", HtmlText.plain("&notanentity;"))
    }

    @Test fun searchIsCleaned() {
        assertEquals("sugar diet", ArticleSearch.clean("  sugar \n  diet "))
        assertEquals(60, ArticleSearch.clean("x".repeat(100)).length)
        assertEquals("", ArticleSearch.clean(null))
        assertNull(ArticleSearch.effective("a"))
        assertNull(ArticleSearch.effective("   "))
        assertEquals("ab", ArticleSearch.effective(" ab "))
    }

    private data class A(val id: Int)

    @Test fun pagesAppendWithoutDuplicates() {
        val first = ArticleFeed.first(listOf(A(1), A(2), A(3)), totalPages = 3, total = 7) { it.id }
        assertEquals(listOf(A(1), A(2), A(3)), first.items)
        assertTrue(first.hasMore); assertEquals(2, first.nextPage)
        val second = ArticleFeed.append(first, listOf(A(3), A(4), A(5)), 2, 3, 7) { it.id }   // A(3) shifted onto page 2
        assertEquals(listOf(1, 2, 3, 4, 5), second.items.map { it.id })
        val third = ArticleFeed.append(second, listOf(A(6), A(7)), 3, 3, 7) { it.id }
        assertFalse(third.hasMore)
        assertEquals(7, third.items.size)
    }

    @Test fun emptyResultsAreNotMoreToLoad() {
        val s = ArticleFeed.first(emptyList<A>(), totalPages = 0, total = 0) { it.id }
        assertTrue(s.items.isEmpty()); assertFalse(s.hasMore)
    }

    @Test fun aNewSearchReplacesWhatWasShown() {
        val old = ArticleFeed.first(listOf(A(1), A(2)), 5, 50) { it.id }
        val fresh = ArticleFeed.first(listOf(A(9)), 1, 1) { it.id }
        assertEquals(listOf(9), fresh.items.map { it.id }); assertEquals(2, old.items.size)
    }
}
