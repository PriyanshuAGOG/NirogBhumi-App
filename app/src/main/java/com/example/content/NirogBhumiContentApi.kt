package com.nirogbhumi.app.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class NirogBhumiArticle(
    val id: Int,
    val title: String,
    val excerpt: String,
    val link: String,
    val dateLabel: String,
    val imageUrl: String?
)

/**
 * Reads real articles from nirogbhumi.com's WordPress REST API instead of showing
 * fabricated in-app content. The API is public and needs no key: wp-json/wp/v2/posts
 * is enabled by default on WordPress sites. Full article bodies are intentionally
 * not fetched or re-rendered here - WordPress post HTML can contain arbitrary
 * embeds/shortcodes that don't translate reliably to Compose, so reading the rest
 * of an article opens the real link on the site instead.
 */
object NirogBhumiContentApi {
    private const val BASE_URL = "https://nirogbhumi.com/wp-json/wp/v2/posts"

    suspend fun fetchArticles(perPage: Int = 12): Result<List<NirogBhumiArticle>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$BASE_URL?_embed&per_page=$perPage&orderby=date&order=desc")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("nirogbhumi.com returned HTTP ${connection.responseCode}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseArticles(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseArticles(json: String): List<NirogBhumiArticle> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val post = array.optJSONObject(index) ?: return@mapNotNull null
            val id = post.optInt("id", -1)
            if (id == -1) return@mapNotNull null
            val title = stripHtml(post.optJSONObject("title")?.optString("rendered").orEmpty())
            val excerpt = stripHtml(post.optJSONObject("excerpt")?.optString("rendered").orEmpty())
            val link = post.optString("link").orEmpty()
            val dateRaw = post.optString("date").orEmpty()
            val imageUrl = post.optJSONObject("_embedded")
                ?.optJSONArray("wp:featuredmedia")
                ?.optJSONObject(0)
                ?.optString("source_url")
                ?.takeIf { it.isNotBlank() }
            NirogBhumiArticle(
                id = id,
                title = title.ifBlank { "Nirog Bhumi article" },
                excerpt = excerpt,
                link = link,
                dateLabel = formatDate(dateRaw),
                imageUrl = imageUrl
            )
        }
    }

    private fun formatDate(isoDate: String): String {
        return runCatching {
            val parsed = java.time.LocalDate.parse(isoDate.take(10))
            parsed.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }.getOrDefault("")
    }

    private fun stripHtml(raw: String): String = raw
        .replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&")
        .replace("&#8217;", "'")
        .replace("&#8216;", "'")
        .replace("&#8220;", "\"")
        .replace("&#8221;", "\"")
        .replace("&#8230;", "...")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
