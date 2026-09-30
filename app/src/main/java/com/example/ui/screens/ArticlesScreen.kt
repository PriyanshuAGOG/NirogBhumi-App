package com.nirogbhumi.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.content.ArticleFeed
import com.nirogbhumi.app.content.ArticleSearch
import com.nirogbhumi.app.content.FeedState
import com.nirogbhumi.app.content.NirogBhumiArticle
import com.nirogbhumi.app.content.NirogBhumiContentApi
import com.nirogbhumi.app.content.SavedArticles
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.web.UrlPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val ArticleInk = Color(0xFF1B3221)
private val ArticleMuted = Color(0xFF697169)
private val ArticleGreen = Color(0xFF314936)

/** Opens a link in the user's browser, only if it is a plain https address. */
internal fun openWebUrl(context: Context, url: String) {
    if (!UrlPolicy.isSafeToOpenExternally(url)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Learn: the newest articles from nirogbhumi.com (the one education source), with search, "load more"
 * paging and a saved copy of the latest list for when there is no connection. Tapping an article opens
 * the in-app reader (title, picture, the site's own summary, attribution) and from there the full article
 * on the website.
 */
@Composable
fun ArticlesScreen(state: NirogState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(state.searchQuery) }
    var feed by remember { mutableStateOf(FeedState<NirogBhumiArticle>()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var moreFailed by remember { mutableStateOf(false) }
    var savedCopy by remember { mutableStateOf<SavedArticles?>(null) }
    var firstLoad by remember { mutableStateOf(true) }
    // A category chip on the Learn tab hands over its word as the first search, then it is forgotten.
    LaunchedEffect(Unit) { state.searchQuery = "" }

    suspend fun loadFirst(q: String) {
        loading = true; failed = false; moreFailed = false; savedCopy = null
        NirogBhumiContentApi.fetchPage(1, q).fold(
            onSuccess = { page ->
                feed = ArticleFeed.first(page.articles, page.totalPages, page.total) { it.id }
                if (ArticleSearch.effective(q) == null && page.articles.isNotEmpty()) NirogBhumiContentApi.saveOffline(context, page.articles)
            },
            onFailure = {
                feed = FeedState()
                failed = true
                if (ArticleSearch.effective(q) == null) savedCopy = NirogBhumiContentApi.loadOffline(context)
            },
        )
        loading = false
    }

    // Typing waits a moment so the site is asked once, not once per letter.
    LaunchedEffect(query) {
        if (!firstLoad) delay(450)
        firstLoad = false
        loadFirst(query)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader("Learn", onBack = { state.currentScreen = "dashboard" })
        Text("Simple reads from nirogbhumi.com.", fontSize = 13.sp, color = ArticleMuted, modifier = Modifier.padding(horizontal = 20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(ArticleSearch.MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            label = { Text("Search articles") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear", color = ArticleGreen) } },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        val shownSaved = savedCopy
        when {
            loading && feed.items.isEmpty() -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                Spacer(Modifier.width(8.dp))
                Text("Loading articles...", fontSize = 13.sp, color = ArticleMuted)
            }
            failed && shownSaved != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column {
                        Text(
                            "You're offline. Showing the articles saved on ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(shownSaved.savedAtMillis))}.",
                            fontSize = 12.5.sp, color = ArticleMuted,
                        )
                        TextButton(onClick = { scope.launch { loadFirst(query) } }) { Text("Try again", color = ArticleGreen, fontWeight = FontWeight.Bold) }
                    }
                }
                items(shownSaved.articles, key = { it.id }) { article -> ArticleCard(article) { openReader(state, article) } }
            }
            failed -> Column(Modifier.padding(horizontal = 20.dp)) {
                EmptyStateCard(Icons.Filled.CloudOff, "We couldn't reach nirogbhumi.com right now. Check your connection and try again.")
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scope.launch { loadFirst(query) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArticleGreen), shape = RoundedCornerShape(24.dp),
                ) { Text("Try again", color = Color.White) }
                OutlinedButton(onClick = { openWebUrl(context, "https://nirogbhumi.com") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) { Text("Open nirogbhumi.com instead") }
            }
            feed.items.isEmpty() -> Column(Modifier.padding(horizontal = 20.dp)) {
                EmptyStateCard(Icons.Filled.MenuBook, if (ArticleSearch.effective(query) != null) "No articles match \"${ArticleSearch.clean(query)}\". Try a shorter or different word." else "No articles yet. Please check back soon.")
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(feed.items, key = { it.id }) { article -> ArticleCard(article) { openReader(state, article) } }
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (moreFailed) Text("We couldn't load more articles. Check your connection.", fontSize = 12.5.sp, color = ArticleMuted)
                        if (feed.hasMore) {
                            OutlinedButton(
                                enabled = !loadingMore,
                                onClick = {
                                    scope.launch {
                                        loadingMore = true; moreFailed = false
                                        NirogBhumiContentApi.fetchPage(feed.nextPage, query).fold(
                                            onSuccess = { page -> feed = ArticleFeed.append(feed, page.articles, page.page, page.totalPages, page.total) { it.id } },
                                            onFailure = { moreFailed = true },
                                        )
                                        loadingMore = false
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp),
                            ) { if (loadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ArticleGreen) else Text("Load more articles") }
                        } else {
                            Text("That's everything for now.", fontSize = 12.sp, color = ArticleMuted)
                        }
                    }
                }
            }
        }
    }
}

private fun openReader(state: NirogState, article: NirogBhumiArticle) {
    state.selectedDocumentValues = mapOf(
        "id" to article.id, "title" to article.title, "excerpt" to article.excerpt,
        "link" to article.link, "dateLabel" to article.dateLabel, "imageUrl" to article.imageUrl,
    )
    state.currentScreen = "article_reader"
}

/** One article: what the site publishes as its summary, clearly credited, with the full text one tap away on the site. */
@Composable
fun ArticleReaderScreen(state: NirogState) {
    val context = LocalContext.current
    val v = state.selectedDocumentValues
    val title = (v["title"] as? String).orEmpty()
    val link = (v["link"] as? String).orEmpty()
    val canOpen = UrlPolicy.isAllowedHttps(link)
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).verticalScroll(rememberScrollState())) {
        DetailScreenHeader("Article", onBack = { state.currentScreen = "articles" })
        if (title.isBlank()) {
            EmptyStateCard(Icons.Filled.MenuBook, "This article isn't available. Go back to the list and pick another.")
            return@Column
        }
        (v["imageUrl"] as? String)?.takeIf { it.startsWith("https://") }?.let { url ->
            coil.compose.AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontFamily = FontFamily.Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ArticleInk, lineHeight = 30.sp)
            Text(listOf((v["dateLabel"] as? String).orEmpty(), "From nirogbhumi.com").filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = ArticleMuted)
            (v["excerpt"] as? String)?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 15.sp, color = Color(0xFF303B33), lineHeight = 23.sp) }
            if (canOpen) {
                Button(
                    onClick = { openWebUrl(context, link) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArticleGreen), shape = RoundedCornerShape(26.dp),
                ) { Text("Read the full article on nirogbhumi.com", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Text("Education only. It is not medical advice. Ask your doctor before changing treatment.", fontSize = 11.5.sp, color = ArticleMuted)
        }
    }
}

@Composable
private fun ArticleCard(article: NirogBhumiArticle, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, Color(0xFFD8D0C0)),
    ) {
        Column {
            article.imageUrl?.let { url ->
                coil.compose.AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(article.title, fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ArticleInk)
                if (article.excerpt.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(article.excerpt, fontSize = 13.sp, color = Color(0xFF434842), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(article.dateLabel, fontSize = 11.sp, color = Color(0xFF737972))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Read", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArticleGreen)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ArticleGreen, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
