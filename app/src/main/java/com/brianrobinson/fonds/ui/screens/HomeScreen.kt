package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.CollectionSummary
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.EmptyState
import com.brianrobinson.fonds.ui.components.PipelineStrip
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.components.Thumb
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Nav) {
    val c = LocalContainer.current
    val flow = remember { c.repo.dao.collectionSummaries() }
    val collections by flow.collectAsStateWithLifecycle(initialValue = null)
    var showNew by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Fonds")
                        Text(
                            "Finding aids from the reading room",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { nav.go(Routes.search()) }) { Icon(Icons.Outlined.Search, "Search everything") }
                    IconButton(onClick = { nav.go(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            if (!collections.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showNew = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("New collection") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val list = collections
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding))
            list.isEmpty() -> Welcome(Modifier.padding(padding)) { showNew = true }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PipelineStrip() }
                items(list, key = { it.collection.id }) { s ->
                    CollectionCard(
                        s,
                        onOpen = { nav.go(Routes.collection(s.collection.id)) },
                        onResume = { nav.go(Routes.capture(s.collection.id)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showNew) {
        NewCollectionSheet(
            onDismiss = { showNew = false },
            onCreated = { id, scanNow ->
                showNew = false
                nav.go(Routes.collection(id))
                if (scanNow) nav.go(Routes.capture(id, 1, 1))
            },
        )
    }
}

@Composable
private fun Welcome(modifier: Modifier, onStart: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            EmptyState(
                icon = Icons.Outlined.Inventory2,
                title = "Start your first collection",
                body = "Fonds turns a phone, a box of papers and an afternoon into a working finding aid.",
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Step(1, Icons.Outlined.DocumentScanner, "Scan by box and folder",
                    "Set the box and folder, then scan each document. Fonds files every page where it belongs and keeps original order.")
                Step(2, Icons.Outlined.AutoAwesome, "Let the phone draft",
                    "Text is read on the phone. Gemini Nano drafts titles, dates, names and summaries. Handwriting can go to Claude if you add a key.")
                Step(3, Icons.Outlined.IosShare, "Review, arrange, export",
                    "Approve or fix each draft, group folders into series, then export EAD for ArchivesSpace, a printable finding aid, or spreadsheets.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New collection")
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, icon: ImageVector, title: String, body: String) {
    OutlinedCard(colors = androidx.compose.material3.CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(color = Fonds.colors.manila, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("$n", style = LabelType.big, color = Fonds.colors.onManila) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CollectionCard(s: CollectionSummary, onOpen: () -> Unit, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val col = s.collection
    val band = Fonds.colors.collectionBands[col.colorIndex.mod(Fonds.colors.collectionBands.size)]
    OutlinedCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(band))
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(col.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOf(col.repository, col.identifier).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    if (s.thumbPath != null) {
                        Spacer(Modifier.width(10.dp))
                        Thumb(s.thumbPath, width = 46.dp, height = 60.dp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${s.boxCount} box${if (s.boxCount == 1) "" else "es"} · ${s.folderCount} folders · ${s.documentCount} items · ${s.pageCount} pp",
                    style = LabelType.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.documentCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { s.reviewedCount.toFloat() / s.documentCount.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Fonds.colors.approved,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(4.dp))
                    val parts = buildList {
                        add("${s.reviewedCount} of ${s.documentCount} reviewed")
                        if (s.draftCount > 0) add("${s.draftCount} waiting for you")
                        if (s.busyCount > 0) add("${s.busyCount} processing")
                    }
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onResume) {
                        Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.documentCount == 0) "Start scanning" else "Resume · B${col.cursorBox} F${col.cursorFolder}")
                    }
                    TextButton(onClick = onOpen) { Text("Open") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewCollectionSheet(onDismiss: () -> Unit, onCreated: (Long, Boolean) -> Unit) {
    val c = LocalContainer.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var repository by remember { mutableStateOf(settings.defaultRepository) }
    var identifier by remember { mutableStateOf("") }

    fun create(scanNow: Boolean) {
        if (title.isBlank()) return
        scope.launch {
            if (repository.isNotBlank() && settings.defaultRepository.isBlank()) {
                c.settings.update { it.copy(defaultRepository = repository.trim()) }
            }
            val id = c.repo.createCollection(title, creator, repository, identifier)
            onCreated(id, scanNow)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("New collection")
            Text("What are you processing?", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Collection title") },
                placeholder = { Text("e.g. Alfonso Elder Papers") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = creator, onValueChange = { creator = it },
                label = { Text("Creator (person or organization)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = repository, onValueChange = { repository = it },
                label = { Text("Repository") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = identifier, onValueChange = { identifier = it },
                label = { Text("Collection number (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { create(scanNow = true) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.DocumentScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Create and start scanning")
            }
            OutlinedButton(onClick = { create(scanNow = false) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Just create it")
            }
        }
    }
}
