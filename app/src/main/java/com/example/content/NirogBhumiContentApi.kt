package com.nirogbhumi.app.content

import android.content.Context
import com.nirogbhumi.app.web.UrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class NirogBhumiArticle(
    val id: Int,
    val title: String,
    val excerpt: String,
    val link: String,
    val dateLabel: String,
    val imageUrl: String?
)

data class ArticlePage(val articles: List<NirogBhumiArticle>, val page: Int, val totalPages: Int, val total: Int)

/** The last successful first page, kept on the phone so Learn still opens without a connection. */
data class SavedArticles(val articles: List<NirogBhumiArticle>, val savedAtMillis: Long)

/**
 * The one source of education content: nirogbhumi.com's public WordPress posts. (There used to be a second,
 * separate list in the console's Content page; it is retired so a member never sees two versions.)
 *
 * The app shows title, date, picture and the site's own short excerpt with clear attribution, and sends the
 * reader to the site for the full article - it does not copy or re-host article bodies. Every link is checked
 * against our own host before it is shown, so a bad feed entry can never open somewhere else.
 */
object NirogBhumiContentApi {
    private const val BASE_URL = "https://nirogbhumi.com/wp-json/wp/v2/posts"
    const val PAGE_SIZE = 12
    private const val CACHE_FILE = "learn-articles-v1.json"
    private const val MAX_CACHED = 24

    suspend fun fetchPage(page: Int, search: String? = null, perPage: Int = PAGE_SIZE): Result<ArticlePage> = withContext(Dispatchers.IO) {
        runCatching {
            val q = ArticleSearch.effective(search)?.let { "&search=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
            val url = URL("$BASE_URL?_embed&per_page=$perPage&page=${page.coerceAtLeast(1)}&orderby=date&order=desc$q")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false   // a redirect off our host must not be followed silently
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                // WordPress answers 400 "rest_post_invalid_page_number" when you page past the end: that is "no more", not a failure.
                if (code == 400 && page > 1) return@runCatching ArticlePage(emptyList(), page, page - 1, 0)
                if (code !in 200..299) throw IllegalStateException("nirogbhumi.com returned HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                ArticlePage(
                    articles = parseArticles(body),
                    page = page,
                    totalPages = connection.getHeaderField("X-WP-TotalPages")?.toIntOrNull() ?: 1,
                    total = connection.getHeaderField("X-WP-Total")?.toIntOrNull() ?: 0,
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Kept for callers that only want the newest few (the Learn tab's featured card). */
    suspend fun fetchArticles(perPage: Int = PAGE_SIZE): Result<List<NirogBhumiArticle>> =
        fetchPage(1, null, perPage).map { it.articles }

    fun parseArticles(json: String): List<NirogBhumiArticle> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val post = array.optJSONObject(index) ?: return@mapNotNull null
            val id = post.optInt("id", -1)
            if (id == -1) return@mapNotNull null
            val link = post.optString("link").orEmpty()
            if (!UrlPolicy.isAllowedHttps(link)) return@mapNotNull null
            val title = HtmlText.plain(post.optJSONObject("title")?.optString("rendered"))
            val excerpt = HtmlText.plain(post.optJSONObject("excerpt")?.optString("rendered"))
            val imageUrl = post.optJSONObject("_embedded")
                ?.optJSONArray("wp:featuredmedia")
                ?.optJSONObject(0)
                ?.optString("source_url")
                ?.takeIf { it.isNotBlank() && it.startsWith("https://", ignoreCase = true) }
            NirogBhumiArticle(
                id = id,
                title = title.ifBlank { "Nirog Bhumi article" },
                excerpt = excerpt,
                link = link,
                dateLabel = formatDate(post.optString("date").orEmpty()),
                imageUrl = imageUrl
            )
        }
    }

    // ---- offline copy of the first page ----

    suspend fun saveOffline(context: Context, articles: List<NirogBhumiArticle>, nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().put("savedAt", nowMillis).put("articles", JSONArray().also { arr ->
                articles.take(MAX_CACHED).forEach { a ->
                    arr.put(JSONObject().put("id", a.id).put("title", a.title).put("excerpt", a.excerpt).put("link", a.link).put("date", a.dateLabel).put("image", a.imageUrl ?: JSONObject.NULL))
                }
            })
            File(context.filesDir, CACHE_FILE).writeText(json.toString())
        }
    }

    suspend fun loadOffline(context: Context): SavedArticles? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, CACHE_FILE).takeIf { it.exists() } ?: return@runCatching null
            decodeSaved(file.readText())
        }.getOrNull()
    }

    fun decodeSaved(text: String): SavedArticles? {
        val json = JSONObject(text)
        val arr = json.optJSONArray("articles") ?: return null
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val link = o.optString("link")
            if (!UrlPolicy.isAllowedHttps(link)) return@mapNotNull null   // saved data is re-checked on the way back in
            NirogBhumiArticle(
                id = o.optInt("id", -1).takeIf { it != -1 } ?: return@mapNotNull null,
                title = o.optString("title").ifBlank { "Nirog Bhumi article" },
                excerpt = o.optString("excerpt"),
                link = link,
                dateLabel = o.optString("date"),
                imageUrl = o.optString("image").takeIf { it.isNotBlank() && it != "null" && it.startsWith("https://", ignoreCase = true) },
            )
        }
        return if (list.isEmpty()) null else SavedArticles(list, json.optLong("savedAt", 0L))
    }

    private fun formatDate(isoDate: String): String {
        return runCatching {
            val parsed = java.time.LocalDate.parse(isoDate.take(10))
            parsed.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH))
        }.getOrDefault("")
    }
}
