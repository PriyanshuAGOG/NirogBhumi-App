package com.nirogbhumi.app.web

import java.net.URI

/**
 * Decides which web addresses the app may load inside its own WebView or hand to another app.
 *
 * The address is PARSED and its host compared exactly - never matched with `startsWith`/`contains`,
 * which `https://nirogbhumi.com.evil.example` or `https://nirogbhumi.com@evil.example` would pass.
 * Only https on the default port, no embedded credentials, and a host that is exactly one of the
 * allowed hosts (or, for entries written as `*.example.com`, a real subdomain of it).
 */
object UrlPolicy {
    /** Hosts that belong to Nirog Bhumi. Subdomains are listed deliberately, not by wildcard. */
    val OWN_HOSTS: Set<String> = setOf("nirogbhumi.com", "www.nirogbhumi.com")

    fun parse(raw: String?): URI? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > 2048 || text.any { it.isISOControl() || it == ' ' || it == '\\' }) return null
        return runCatching { URI(text) }.getOrNull()
    }

    fun isAllowedHttps(raw: String?, allowedHosts: Set<String> = OWN_HOSTS): Boolean {
        val uri = parse(raw) ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return allowedHosts.any { allowed ->
            if (allowed.startsWith("*.")) host.endsWith(allowed.substring(1)) && host.length > allowed.length - 1 else host == allowed.lowercase()
        }
    }

    /** Anything that may be opened in the user's browser: a plain https link to a real host. */
    fun isSafeToOpenExternally(raw: String?): Boolean {
        val uri = parse(raw) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && !uri.host.isNullOrBlank()
    }

    enum class Navigation { LOAD_IN_APP, OPEN_IN_BROWSER, BLOCK }

    /**
     * What to do when a page inside the in-app WebView tries to go somewhere: stay in the app only for
     * our own hosts; send ordinary https links to the user's browser; refuse every other scheme
     * (intent:, javascript:, file:, content:, market:, upi: ...), which a web page must never trigger.
     */
    fun navigation(raw: String?, allowedHosts: Set<String> = OWN_HOSTS): Navigation = when {
        isAllowedHttps(raw, allowedHosts) -> Navigation.LOAD_IN_APP
        isSafeToOpenExternally(raw) -> Navigation.OPEN_IN_BROWSER
        else -> Navigation.BLOCK
    }
}
