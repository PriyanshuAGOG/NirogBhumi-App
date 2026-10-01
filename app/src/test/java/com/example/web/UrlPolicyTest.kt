package com.nirogbhumi.app.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    private fun ok(url: String?) = UrlPolicy.isAllowedHttps(url)

    @Test fun ownSiteIsAllowed() {
        assertTrue(ok("https://nirogbhumi.com"))
        assertTrue(ok("https://nirogbhumi.com/shop/?add-to-cart=12"))
        assertTrue(ok("https://www.nirogbhumi.com/articles/walk-after-dinner"))
        assertTrue(ok("HTTPS://NirogBhumi.com/x"))
        assertTrue(ok("https://nirogbhumi.com:443/x"))
        assertTrue(ok("https://nirogbhumi.com./x"))
    }

    @Test fun lookalikeHostsAreRefused() {
        assertFalse(ok("https://nirogbhumi.com.evil.example/"))
        assertFalse(ok("https://evilnirogbhumi.com/"))
        assertFalse(ok("https://nirogbhumi.co/"))
        assertFalse(ok("https://notnirogbhumi.com/"))
        assertFalse(ok("https://nirogbhumi.com@evil.example/"))
        assertFalse(ok("https://user:pass@nirogbhumi.com/"))
        assertFalse(ok("https://evil.example/?u=https://nirogbhumi.com"))
        assertFalse(ok("https://evil.example/#nirogbhumi.com"))
        assertFalse(ok("https://shop.nirogbhumi.com/"))   // subdomains only when listed
    }

    @Test fun otherSchemesAndPortsAreRefused() {
        assertFalse(ok("http://nirogbhumi.com/"))
        assertFalse(ok("javascript:alert(1)"))
        assertFalse(ok("intent://nirogbhumi.com#Intent;scheme=https;end"))
        assertFalse(ok("file:///sdcard/x.html"))
        assertFalse(ok("content://com.android.providers.media/x"))
        assertFalse(ok("data:text/html;base64,PHNjcmlwdD4="))
        assertFalse(ok("market://details?id=x"))
        assertFalse(ok("upi://pay?pa=x@y"))
        assertFalse(ok("https://nirogbhumi.com:8443/"))
        assertFalse(ok("https://nirogbhumi.com:80/"))
        assertFalse(ok("//nirogbhumi.com/x"))
    }

    @Test fun malformedInputIsRefused() {
        assertFalse(ok(null)); assertFalse(ok("")); assertFalse(ok("   "))
        assertFalse(ok("https://nirogbhumi.com/ x"))
        assertFalse(ok("https://nirogbhumi.com/\nx"))
        assertFalse(ok("https:\\\\nirogbhumi.com"))
        assertFalse(ok("https://" + "a".repeat(3000)))
        assertFalse(ok("https://"))
    }

    @Test fun explicitWildcardMeansRealSubdomainsOnly() {
        val hosts = setOf("*.example.org")
        assertTrue(UrlPolicy.isAllowedHttps("https://a.example.org/", hosts))
        assertTrue(UrlPolicy.isAllowedHttps("https://a.b.example.org/", hosts))
        assertFalse(UrlPolicy.isAllowedHttps("https://example.org/", hosts))
        assertFalse(UrlPolicy.isAllowedHttps("https://evilexample.org/", hosts))
        assertFalse(UrlPolicy.isAllowedHttps("https://example.org.evil.example/", hosts))
    }

    @Test fun externalOpenNeedsPlainHttps() {
        assertTrue(UrlPolicy.isSafeToOpenExternally("https://example.com/page"))
        assertFalse(UrlPolicy.isSafeToOpenExternally("http://example.com/page"))
        assertFalse(UrlPolicy.isSafeToOpenExternally("intent://x#Intent;end"))
        assertFalse(UrlPolicy.isSafeToOpenExternally("javascript:alert(1)"))
        assertFalse(UrlPolicy.isSafeToOpenExternally("https://a@example.com/"))
        assertFalse(UrlPolicy.isSafeToOpenExternally(null))
    }

    @Test fun navigationDecisions() {
        assertTrue(UrlPolicy.navigation("https://nirogbhumi.com/shop") == UrlPolicy.Navigation.LOAD_IN_APP)
        assertTrue(UrlPolicy.navigation("https://payments.example.com/pay") == UrlPolicy.Navigation.OPEN_IN_BROWSER)
        assertTrue(UrlPolicy.navigation("intent://x#Intent;end") == UrlPolicy.Navigation.BLOCK)
        assertTrue(UrlPolicy.navigation("javascript:alert(1)") == UrlPolicy.Navigation.BLOCK)
        assertTrue(UrlPolicy.navigation("http://nirogbhumi.com/") == UrlPolicy.Navigation.BLOCK)
        assertTrue(UrlPolicy.navigation("https://nirogbhumi.com@evil.example/") == UrlPolicy.Navigation.BLOCK)
        assertTrue(UrlPolicy.navigation(null) == UrlPolicy.Navigation.BLOCK)
    }
}
