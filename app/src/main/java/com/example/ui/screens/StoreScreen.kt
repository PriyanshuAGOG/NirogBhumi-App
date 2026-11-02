package com.nirogbhumi.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nirogbhumi.app.content.StoreCatalog
import com.nirogbhumi.app.content.StoreProduct
import com.nirogbhumi.app.content.WebsiteLinks
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.NirogCard
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType
import com.nirogbhumi.app.web.UrlPolicy
import kotlinx.coroutines.launch

/** Opens one of OUR pages in the phone's browser, where ordering and payment happen. Anything else is refused. */
fun openOnWebsite(context: android.content.Context, url: String): Boolean {
    if (!UrlPolicy.isAllowedHttps(url)) return false
    return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/** The Nirog Bhumi shop, listed from nirogbhumi.com. Ordering happens on the website. */
@Composable
fun StoreScreen(state: NirogState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<StoreProduct>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    fun loadNext() {
        if (loading && products.isNotEmpty()) return
        loading = true
        scope.launch {
            StoreCatalog.fetchPage(page + 1).onSuccess { result ->
                products = (products + result.products).distinctBy { it.id }
                page = result.page
                totalPages = result.totalPages
                failed = false
            }.onFailure { failed = products.isEmpty() }
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadNext() }

    Column(Modifier.fillMaxSize().background(NirogColor.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = NirogSpace.sm, vertical = NirogSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.activeTab = "Learn"; state.currentScreen = "dashboard" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NirogColor.forest)
            }
            Column(Modifier.weight(1f)) {
                Text("Nirog Bhumi Store", style = NirogType.sectionHeading, color = NirogColor.forest)
                Text("Choose here, order on our website", style = NirogType.caption, color = NirogColor.inkMuted)
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = NirogSpace.lg),
            verticalArrangement = Arrangement.spacedBy(NirogSpace.md),
        ) {
            if (products.isEmpty() && loading) {
                item { Box(Modifier.fillMaxWidth().padding(NirogSpace.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NirogColor.forest) } }
            }
            if (products.isEmpty() && !loading) {
                item {
                    NirogCard {
                        Text(if (failed) "We couldn't load the store right now." else "No products are listed yet.", style = NirogType.cardTitle, color = NirogColor.forest)
                        Spacer(Modifier.height(NirogSpace.xs))
                        Text("You can still browse and order on our website.", style = NirogType.caption, color = NirogColor.inkMuted)
                        Spacer(Modifier.height(NirogSpace.md))
                        Button(
                            onClick = { openOnWebsite(context, StoreCatalog.SHOP_URL) },
                            colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forest),
                        ) { Text("Open the store on nirogbhumi.com") }
                        if (failed) {
                            OutlinedButton(onClick = { loadNext() }, modifier = Modifier.padding(top = NirogSpace.sm)) { Text("Try again") }
                        }
                    }
                }
            }
            items(products, key = { it.id }) { product -> ProductCard(product) { openOnWebsite(context, product.link) } }
            if (products.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = NirogSpace.md), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (page < totalPages) {
                            OutlinedButton(onClick = { loadNext() }, enabled = !loading) { Text(if (loading) "Loading…" else "Show more products") }
                        }
                        Text(
                            "Payment and delivery are handled securely on nirogbhumi.com.",
                            style = NirogType.caption, color = NirogColor.inkMuted, modifier = Modifier.padding(top = NirogSpace.sm),
                        )
                        Spacer(Modifier.height(NirogSpace.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: StoreProduct, onOpen: () -> Unit) {
    NirogCard {
        Row(horizontalArrangement = Arrangement.spacedBy(NirogSpace.md)) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).background(NirogColor.surfaceAlt), contentAlignment = Alignment.Center) {
                if (product.imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = product.imageUrl, contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NirogSpace.xs)) {
                Text(product.name, style = NirogType.cardTitle, color = NirogColor.forest, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(product.priceLabel, style = NirogType.cardTitle, color = NirogColor.forest)
                    product.regularPriceLabel?.let {
                        Text(it, style = NirogType.caption, color = NirogColor.inkMuted, textDecoration = TextDecoration.LineThrough)
                    }
                }
                if (product.summary.isNotBlank()) Text(product.summary, style = NirogType.caption, color = NirogColor.inkMuted, maxLines = 3)
                if (!product.inStock) Text("Currently out of stock", style = NirogType.caption, color = NirogColor.terracotta)
            }
        }
        Spacer(Modifier.height(NirogSpace.md))
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forest),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("View and order on website")
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** On the consultation request screen: book a time yourself on the website's own booking page. */
@Composable
fun BookOnWebsiteCard() {
    val context = LocalContext.current
    NirogCard {
        Text("Book a time yourself", style = NirogType.cardTitle, color = NirogColor.forest)
        Spacer(Modifier.height(NirogSpace.xs))
        Text("Pick a slot with our team on nirogbhumi.com. Or send a request below and we'll confirm a time with you.", style = NirogType.caption, color = NirogColor.inkMuted)
        Spacer(Modifier.height(NirogSpace.md))
        Button(
            onClick = { openOnWebsite(context, WebsiteLinks.CONSULTATION_URL) },
            colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forest),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("Book on nirogbhumi.com")
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** Under the article list: everything else lives on the website's Education page. */
@Composable
fun MoreOnWebsiteLink() {
    val context = LocalContext.current
    OutlinedButton(onClick = { openOnWebsite(context, WebsiteLinks.EDUCATION_URL) }, modifier = Modifier.fillMaxWidth()) {
        Text("Browse all education on nirogbhumi.com")
        Spacer(Modifier.size(8.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}
