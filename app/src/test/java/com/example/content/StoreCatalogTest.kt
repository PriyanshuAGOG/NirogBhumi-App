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
class StoreCatalogTest {
    private fun product(id: Int, link: String, price: String = "149900", regular: String = "149900", sale: Boolean = false, stock: Boolean = true) = """
        {"id":$id,"name":"Herbal <b>Tea</b> &amp; Kit $id","permalink":"$link","on_sale":$sale,"is_in_stock":$stock,
         "short_description":"<p>Calming &amp; simple.</p>","images":[{"src":"https://nirogbhumi.com/img$id.jpg"}],
         "prices":{"price":"$price","regular_price":"$regular","currency_minor_unit":2,"currency_prefix":"₹","currency_suffix":"","currency_symbol":"₹"}}
    """.trimIndent()

    @Test fun parsesProductsIntoPlainText() {
        val list = StoreCatalog.parseProducts("[" + product(1, "https://nirogbhumi.com/product/tea/") + "]")
        assertEquals(1, list.size)
        assertEquals("Herbal Tea & Kit 1", list[0].name)
        assertEquals("₹1,499", list[0].priceLabel)
        assertNull(list[0].regularPriceLabel)
        assertEquals("Calming & simple.", list[0].summary)
        assertEquals("https://nirogbhumi.com/img1.jpg", list[0].imageUrl)
        assertTrue(list[0].inStock)
    }

    @Test fun saleShowsTheOldPriceCrossedOut() {
        val list = StoreCatalog.parseProducts("[" + product(2, "https://nirogbhumi.com/p/", price = "99900", regular = "149900", sale = true) + "]")
        assertEquals("₹999", list[0].priceLabel)
        assertEquals("₹1,499", list[0].regularPriceLabel)
    }

    @Test fun pricesKeepPaiseOnlyWhenThereAreAny() {
        val list = StoreCatalog.parseProducts("[" + product(3, "https://nirogbhumi.com/p/", price = "49950", regular = "49950") + "]")
        assertEquals("₹499.50", list[0].priceLabel)
    }

    @Test fun linksOffOurOwnSiteAreDropped() {
        val json = "[" + listOf(
            product(1, "https://nirogbhumi.com/ok/"),
            product(2, "https://evil.example/steal"),
            product(3, "https://nirogbhumi.com.evil.example/x"),
            product(4, "http://nirogbhumi.com/plain-http"),
        ).joinToString(",") + "]"
        assertEquals(listOf(1L), StoreCatalog.parseProducts(json).map { it.id })
    }

    @Test fun outOfStockIsKept() {
        assertEquals(false, StoreCatalog.parseProducts("[" + product(5, "https://nirogbhumi.com/p/", stock = false) + "]")[0].inStock)
    }

    @Test fun garbageGivesAnEmptyList() {
        assertTrue(StoreCatalog.parseProducts("not json").isEmpty())
        assertTrue(StoreCatalog.parseProducts("{}").isEmpty())
    }

    @Test fun findsTheBookingPageAmongPages() {
        val pages = """[
          {"link":"https://nirogbhumi.com/privacy-policy/","title":{"rendered":"Privacy Policy"}},
          {"link":"https://nirogbhumi.com/about/","title":{"rendered":"About us"}},
          {"link":"https://nirogbhumi.com/book-consultation/","title":{"rendered":"Book a Consultation"}},
          {"link":"https://evil.example/consultation/","title":{"rendered":"Consultation"}}]"""
        assertEquals("https://nirogbhumi.com/book-consultation/", WebsiteLinks.pickBookingPage(pages))
    }

    @Test fun noBookingPageGivesNull() {
        assertNull(WebsiteLinks.pickBookingPage("""[{"link":"https://nirogbhumi.com/about/","title":{"rendered":"About"}}]"""))
        assertNull(WebsiteLinks.pickBookingPage("nope"))
    }
}
