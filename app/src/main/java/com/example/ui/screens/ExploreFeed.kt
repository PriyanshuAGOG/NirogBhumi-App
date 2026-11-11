package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.content.ArticleCategory
import com.nirogbhumi.app.content.ArticleFeed
import com.nirogbhumi.app.content.ArticleSearch
import com.nirogbhumi.app.content.FeedState
import com.nirogbhumi.app.content.NirogBhumiArticle
import com.nirogbhumi.app.content.NirogBhumiContentApi
import com.nirogbhumi.app.content.SavedArticles
import com.nirogbhumi.app.ui.NirogState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The Explore feed, shown right in the Learn tab: every article and blog post from nirogbhumi.com, newest first, with a search box
 * that works, the site's own topics as filters, "load more" paging and a saved copy for when there is no connection. Tapping a post
 * opens the in-app reader and, from there, the full article on the website.
 */
@Composable
fun ExploreFeed(state: NirogState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<ArticleCategory>>(emptyList()) }
    var feed by remember { mutableStateOf(FeedState<NirogBhumiArticle>()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var moreFailed by remember { mutableStateOf(false) }
    var savedCopy by remember { mutableStateOf<SavedArticles?>(null) }
    var lastQuery by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { categories = NirogBhumiContentApi.fetchCategories().getOrDefault(emptyList()) }

    suspend fun loadFirst() {
        loading = true; failed = false; moreFailed = false; savedCopy = null
        val plainList = ArticleSearch.effective(query) == null && categoryId == 0
        NirogBhumiContentApi.fetchPage(1, query, categoryId = categoryId.takeIf { it > 0 }).fold(
            onSuccess = { page ->
                feed = ArticleFeed.first(page.articles, page.totalPages, page.total) { it.id }
                if (plainList && page.articles.isNotEmpty()) NirogBhumiContentApi.saveOffline(context, page.articles)
            },
            onFailure = {
                feed = FeedState()
                failed = true
                if (plainList) savedCopy = NirogBhumiContentApi.loadOffline(context)
            },
        )
        loading = false
    }

    // One load per change. Typing waits a moment so the site is asked once, not once per letter; picking a topic loads at once.
    LaunchedEffect(query, categoryId) {
        if (lastQuery != null && lastQuery != query) delay(450)
        lastQuery = query
        loadFirst()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(ArticleSearch.MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search articles and blogs") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear", color = ArticleGreen) } },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        if (categories.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExploreChip("All", categoryId == 0) { categoryId = 0 }
                categories.forEach { c -> ExploreChip(c.name, categoryId == c.id) { categoryId = if (categoryId == c.id) 0 else c.id } }
            }
        }

        val shownSaved = savedCopy
        when {
            loading && feed.items.isEmpty() -> Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF9CB79F))
                Spacer(Modifier.width(8.dp))
                Text("Loading from nirogbhumi.com…", fontSize = 13.sp, color = ArticleMuted)
            }
            failed && shownSaved != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You're offline. Showing the articles saved on ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(shownSaved.savedAtMillis))}.",
                    fontSize = 12.5.sp, color = ArticleMuted,
                )
                TextButton(onClick = { scope.launch { loadFirst() } }) { Text("Try again", color = ArticleGreen, fontWeight = FontWeight.Bold) }
                shownSaved.articles.forEach { article -> ArticleCard(article) { openReader(state, article) } }
            }
            failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmptyStateCard(Icons.Filled.CloudOff, "We couldn't reach nirogbhumi.com right now. Check your connection and try again.")
                Button(
                    onClick = { scope.launch { loadFirst() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArticleGreen), shape = RoundedCornerShape(24.dp),
                ) { Text("Try again", color = Color.White) }
                OutlinedButton(onClick = { openWebUrl(context, "https://nirogbhumi.com") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) {
                    Text("Open nirogbhumi.com", color = ArticleGreen)
                }
            }
            feed.items.isEmpty() -> EmptyStateCard(
                Icons.Filled.MenuBook,
                if (ArticleSearch.effective(query) != null) "No articles match \"${ArticleSearch.clean(query)}\". Try a shorter or different word." else "Nothing has been published in this topic yet.",
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                feed.items.forEach { article -> ArticleCard(article) { openReader(state, article) } }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (moreFailed) Text("We couldn't load more articles. Check your connection.", fontSize = 12.5.sp, color = ArticleMuted)
                    if (feed.hasMore) {
                        OutlinedButton(
                            enabled = !loadingMore,
                            onClick = {
                                scope.launch {
                                    loadingMore = true; moreFailed = false
                                    NirogBhumiContentApi.fetchPage(feed.nextPage, query, categoryId = categoryId.takeIf { it > 0 }).fold(
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

@Composable
private fun ExploreChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else ArticleGreen,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) ArticleGreen else Color.White, RoundedCornerShape(50))
            .border(0.5.dp, Color(0xFFD8D0C0), RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
