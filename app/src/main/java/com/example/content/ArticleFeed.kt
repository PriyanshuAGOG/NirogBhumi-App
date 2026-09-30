package com.nirogbhumi.app.content

/**
 * Pure logic behind the Learn list (no Android types, JVM-tested): turning WordPress HTML into plain
 * text, cleaning a search box entry, and merging pages without showing an article twice.
 */
object HtmlText {
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "...", "ndash" to "-", "mdash" to "-", "rsquo" to "'", "lsquo" to "'", "ldquo" to "\"", "rdquo" to "\"",
        "laquo" to "\"", "raquo" to "\"", "copy" to "(c)", "reg" to "(R)", "trade" to "(TM)", "bull" to "-", "middot" to "-",
    )
    private val entity = Regex("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});")
    private val tags = Regex("<[^>]*>")
    private val blockBreaks = Regex("(?i)</(p|div|li|h[1-6])>|<br\\s*/?>")
    private val space = Regex("[\\s\\u00A0]+")

    /** Plain, single-spaced text: tags removed, entities decoded, scripts and styles dropped with their content. */
    fun plain(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var text = raw.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        text = text.replace(blockBreaks, " ").replace(tags, " ")
        // Decoding runs once, after tag removal, so "&lt;script&gt;" stays harmless text and is never re-interpreted as markup.
        text = entity.replace(text) { m -> decode(m.groupValues[1]) ?: m.value }
        return text.replace(space, " ").trim()
    }

    private fun decode(body: String): String? = when {
        body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
        body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
        else -> named[body] ?: named[body.lowercase()]
    }

    private fun codePoint(cp: Int): String? = when {
        cp == 0x2019 || cp == 0x2018 -> "'"
        cp == 0x201C || cp == 0x201D -> "\""
        cp == 0x2026 -> "..."
        cp == 0x2013 || cp == 0x2014 -> "-"
        cp in 0x20..0x10FFFF && cp !in 0xD800..0xDFFF -> String(Character.toChars(cp))
        else -> null
    }
}

object ArticleSearch {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 60

    /** What is sent to the site: trimmed, single-spaced, no control characters, at most 60 characters. */
    fun clean(input: String?): String = (input ?: "")
        .filter { !it.isISOControl() }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_LENGTH)
        .trim()

    /** A search starts from 2 characters; shorter means "show everything". */
    fun effective(input: String?): String? = clean(input).takeIf { it.length >= MIN_LENGTH }
}

data class FeedState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val total: Int = 0,
) {
    val hasMore: Boolean get() = page in 1 until totalPages
    val nextPage: Int get() = page + 1
}

object ArticleFeed {
    /** Appends a page, keeping order and dropping anything already shown (a post published mid-scroll shifts later pages). */
    fun <T> append(state: FeedState<T>, pageItems: List<T>, page: Int, totalPages: Int, total: Int, id: (T) -> Any): FeedState<T> {
        val seen = state.items.map(id).toHashSet()
        val fresh = pageItems.filter { seen.add(id(it)) }
        return FeedState(state.items + fresh, page, totalPages.coerceAtLeast(1), total)
    }

    /** First page of a new search or a refresh replaces what was shown. */
    fun <T> first(pageItems: List<T>, totalPages: Int, total: Int, id: (T) -> Any): FeedState<T> =
        append(FeedState(), pageItems, 1, totalPages, total, id)
}
