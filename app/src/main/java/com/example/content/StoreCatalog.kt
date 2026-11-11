package com.nirogbhumi.app.content

import com.nirogbhumi.app.web.UrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

data class StoreProduct(
    val id: Long,
    val name: String,
    val priceLabel: String,
    /** The crossed-out price when the product is on sale. */
    val regularPriceLabel: String?,
    val imageUrl: String?,
    /** The product's own page on nirogbhumi.com, where it is ordered. */
    val link: String,
    val summary: String,
    val inStock: Boolean,
)

data class StorePage(val products: List<StoreProduct>, val page: Int, val totalPages: Int, val total: Int)

/**
 * The shop on nirogbhumi.com, read through WooCommerce's public Store API (the same list the website's own shop shows).
 * The app only LISTS products; ordering and payment happen on the website, which the product's link opens.
 * Every link is checked against our own host before it is kept.
 */
object StoreCatalog {
    private const val PRODUCTS_URL = "https://nirogbhumi.com/wp-json/wc/store/v1/products"
    const val PAGE_SIZE = 12

    /** Where "see everything on the website" goes. */
    const val SHOP_URL = "https://nirogbhumi.com/shop/"

    suspend fun fetchPage(page: Int, search: String? = null): Result<StorePage> = withContext(Dispatchers.IO) {
        runCatching {
            val q = search?.trim()?.takeIf { it.length >= 2 }?.let { "&search=" + java.net.URLEncoder.encode(it.take(60), "UTF-8") }.orEmpty()
            val connection = (URL("$PRODUCTS_URL?per_page=$PAGE_SIZE&page=${page.coerceAtLeast(1)}$q").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                if (code == 400 && page > 1) return@runCatching StorePage(emptyList(), page, page - 1, 0)
                if (code !in 200..299) throw IllegalStateException("The shop returned HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                StorePage(
                    products = parseProducts(body),
                    page = page,
                    totalPages = connection.getHeaderField("X-WP-TotalPages")?.toIntOrNull() ?: 1,
                    total = connection.getHeaderField("X-WP-Total")?.toIntOrNull() ?: 0,
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun parseProducts(json: String): List<StoreProduct> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::parseProduct) }
    }

    private fun parseProduct(item: JSONObject): StoreProduct? {
        val link = item.optString("permalink")
        if (!UrlPolicy.isAllowedHttps(link)) return null
        val name = HtmlText.plain(item.optString("name")).ifBlank { return null }
        val prices = item.optJSONObject("prices")
        val price = priceLabel(prices, "price")
        val regular = priceLabel(prices, "regular_price")
        val onSale = item.optBoolean("on_sale") && regular != null && regular != price
        val image = item.optJSONArray("images")?.optJSONObject(0)?.optString("src")?.takeIf { it.startsWith("https://") }
        val summary = HtmlText.plain(item.optString("short_description")).ifBlank { HtmlText.plain(item.optString("description")) }
        return StoreProduct(
            id = item.optLong("id"),
            name = name,
            priceLabel = price ?: "See price on the website",
            regularPriceLabel = if (onSale) regular else null,
            imageUrl = image,
            link = link,
            summary = summary.take(180),
            inStock = item.optBoolean("is_in_stock", true),
        )
    }

    /** "₹1,499" from the Store API's minor-unit string ("149900" with 2 decimals). Null when the product has no price. */
    internal fun priceLabel(prices: JSONObject?, field: String): String? {
        prices ?: return null
        val raw = prices.optString(field).takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
        val minor = prices.optInt("currency_minor_unit", 2).coerceIn(0, 4)
        val amount = BigDecimal(raw).movePointLeft(minor)
        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
            minimumFractionDigits = if (amount.stripTrailingZeros().scale() <= 0) 0 else minor
            maximumFractionDigits = minor
        }
        val prefix = prices.optString("currency_prefix").ifBlank { if (prices.optString("currency_suffix").isBlank()) prices.optString("currency_symbol") else "" }
        return prefix + formatter.format(amount) + prices.optString("currency_suffix")
    }
}

/** Addresses on the website that the app sends people to. All are on our own host (UrlPolicy checks again before opening). */
object WebsiteLinks {
    const val HOME_URL = "https://nirogbhumi.com/"
    const val CONSULTATION_URL = "https://nirogbhumi.com/consultation"
    const val EDUCATION_URL = "https://nirogbhumi.com/education"
}
