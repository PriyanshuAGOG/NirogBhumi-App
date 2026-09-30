package com.nirogbhumi.app.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NirogBhumiContentApiTest {
    private fun post(id: Int, link: String, title: String = "T$id", image: String? = "https://nirogbhumi.com/i$id.jpg") = """
        {"id":$id,"date":"2026-07-0${id}T09:00:00","link":"$link","title":{"rendered":"$title"},"excerpt":{"rendered":"<p>Short &amp; sweet&hellip;</p>"},
         "_embedded":{"wp:featuredmedia":[{"source_url":${if (image == null) "\"\"" else "\"$image\""}}]}}
    """.trimIndent()

    @Test fun parsesPostsIntoPlainText() {
        val list = NirogBhumiContentApi.parseArticles("[" + post(1, "https://nirogbhumi.com/a", "Walk &#8211; after dinner") + "]")
        assertEquals(1, list.size)
        assertEquals("Walk - after dinner", list[0].title)
        assertEquals("Short & sweet...", list[0].excerpt)
        assertEquals("Jul 1, 2026", list[0].dateLabel)
        assertEquals("https://nirogbhumi.com/i1.jpg", list[0].imageUrl)
    }

    @Test fun linksOffOurOwnSiteAreDropped() {
        val json = "[" + listOf(
            post(1, "https://nirogbhumi.com/ok"),
            post(2, "https://evil.example/phish"),
            post(3, "https://nirogbhumi.com.evil.example/x"),
            post(4, "javascript:alert(1)"),
            post(5, "http://nirogbhumi.com/plain-http"),
        ).joinToString(",") + "]"
        assertEquals(listOf(1), NirogBhumiContentApi.parseArticles(json).map { it.id })
    }

    @Test fun insecureOrEmptyImagesAreIgnored() {
        val list = NirogBhumiContentApi.parseArticles("[" + post(1, "https://nirogbhumi.com/a", image = "http://nirogbhumi.com/i.jpg") + "," + post(2, "https://nirogbhumi.com/b", image = null) + "]")
        assertNull(list[0].imageUrl); assertNull(list[1].imageUrl)
    }

    @Test fun savedCopyRoundTripsAndIsRecheckedOnTheWayIn() {
        val articles = listOf(NirogBhumiArticle(7, "Title", "Excerpt", "https://nirogbhumi.com/x", "Jul 7, 2026", null))
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        kotlinx.coroutines.runBlocking { NirogBhumiContentApi.saveOffline(ctx, articles, nowMillis = 1234L) }
        val back = kotlinx.coroutines.runBlocking { NirogBhumiContentApi.loadOffline(ctx) }!!
        assertEquals(articles, back.articles); assertEquals(1234L, back.savedAtMillis)
        // a tampered file with a foreign link loses that entry
        val tampered = """{"savedAt":1,"articles":[{"id":1,"title":"a","link":"https://evil.example/"},{"id":2,"title":"b","link":"https://nirogbhumi.com/b"}]}"""
        assertEquals(listOf(2), NirogBhumiContentApi.decodeSaved(tampered)!!.articles.map { it.id })
        assertNull(NirogBhumiContentApi.decodeSaved("""{"savedAt":1,"articles":[{"id":1,"link":"https://evil.example/"}]}"""))
    }

    @Test fun emptyFeedParsesToEmptyList() { assertTrue(NirogBhumiContentApi.parseArticles("[]").isEmpty()) }
}
