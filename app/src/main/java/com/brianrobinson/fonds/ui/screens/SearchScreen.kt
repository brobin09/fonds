package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.CollectionKind
import com.brianrobinson.fonds.data.SearchHit
import com.brianrobinson.fonds.data.displayDate
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.EmptyState
import com.brianrobinson.fonds.ui.components.LocationBadge
import com.brianrobinson.fonds.ui.components.Thumb
import com.brianrobinson.fonds.ui.theme.Fonds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: Nav, initialCollectionId: Long) {
    val c = LocalContainer.current
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var scopeId by rememberSaveable { mutableLongStateOf(initialCollectionId) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(query) {
        delay(250)
        debounced = query.trim()
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val collectionsFlow = remember { c.repo.dao.collectionsFlow() }
    val collections by collectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val resultsFlow = remember(debounced, scopeId) {
        if (debounced.length >= 2) c.repo.dao.search("%$debounced%", scopeId) else flowOf(emptyList())
    }
    val results by resultsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scopeTitle = collections.firstOrNull { it.id == initialCollectionId }?.title

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Names, places, words on the page…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (initialCollectionId != 0L && scopeTitle != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = scopeId != 0L, onClick = { scopeId = initialCollectionId }, label = { Text(scopeTitle, maxLines = 1) })
                        FilterChip(selected = scopeId == 0L, onClick = { scopeId = 0L }, label = { Text("All collections") })
                    }
                }
            }
            when {
                debounced.length < 2 -> item {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Search everything",
                        body = "Titles, summaries, names, places, subjects, folder labels and the full text of every page.",
                    )
                }
                results.isEmpty() -> item {
                    EmptyState(icon = Icons.Outlined.Search, title = "No matches", body = "Nothing contains “$debounced”. Try a shorter word or a different spelling.")
                }
                else -> {
                    item {
                        Text(
                            "${results.size}${if (results.size >= 300) "+" else ""} item${if (results.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(results, key = { it.document.id }) { hit ->
                        HitRow(hit, debounced, showCollection = scopeId == 0L) { nav.go(Routes.document(hit.document.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, query: String, showCollection: Boolean, onClick: () -> Unit) {
    val d = hit.document
    val highlight = SpanStyle(background = Fonds.colors.manila, fontWeight = FontWeight.SemiBold)
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Thumb(hit.thumbPath, width = 44.dp, height = 58.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hit.collectionKind == CollectionKind.INBOX) {
                        Surface(color = Fonds.colors.manila, shape = MaterialTheme.shapes.extraSmall) {
                            Text("Single item", style = MaterialTheme.typography.labelSmall, color = Fonds.colors.onManila, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else {
                        LocationBadge(hit.boxNumber, hit.folderNumber, d.sequence)
                    }
                    if (showCollection && hit.collectionKind != CollectionKind.INBOX) {
                        Spacer(Modifier.width(6.dp))
                        Text(hit.collectionTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(highlighted(d.displayTitle(), query, highlight), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                d.displayDate().takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val snippet = snippet(hit.matchText ?: d.summary, query)
                if (snippet.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        highlighted(snippet, query, highlight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun snippet(text: String, query: String): String {
    if (text.isBlank()) return ""
    val flat = text.replace(Regex("\\s+"), " ")
    val i = flat.indexOf(query, ignoreCase = true)
    if (i < 0) return flat.take(160)
    val start = (i - 60).coerceAtLeast(0)
    val end = (i + query.length + 100).coerceAtMost(flat.length)
    return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
}

private fun highlighted(text: String, query: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    var from = 0
    while (true) {
        val i = text.indexOf(query, from, ignoreCase = true)
        if (i < 0) {
            append(text.substring(from))
            break
        }
        append(text.substring(from, i))
        pushStyle(style)
        append(text.substring(i, i + query.length))
        pop()
        from = i + query.length
    }
}
