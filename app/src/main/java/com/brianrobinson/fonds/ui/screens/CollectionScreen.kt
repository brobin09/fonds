package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.CollectionSummary
import com.brianrobinson.fonds.data.Dates
import com.brianrobinson.fonds.data.FolderRow
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.data.titleIsDraft
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.ConfirmDialog
import com.brianrobinson.fonds.ui.components.EmptyState
import com.brianrobinson.fonds.ui.components.FolderTab
import com.brianrobinson.fonds.ui.components.PipelineStrip
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.components.StatTile
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(nav: Nav, collectionId: Long, initialTab: Int) {
    val c = LocalContainer.current
    val summaryFlow = remember { c.repo.dao.collectionSummary(collectionId) }
    val summary by summaryFlow.collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = summary

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Column {
                            Text(s?.collection?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(
                                s?.collection?.repository?.takeIf { it.isNotBlank() },
                                s?.collection?.identifier?.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { nav.go(Routes.search(collectionId)) }) { Icon(Icons.Outlined.Search, "Search this collection") }
                        IconButton(onClick = { nav.go(Routes.details(collectionId)) }) { Icon(Icons.Outlined.Info, "Collection details") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete collection") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Boxes") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Series") })
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        text = {
                            val n = s?.draftCount ?: 0
                            if (n > 0) {
                                BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("$n") } }) {
                                    Text("Review")
                                }
                            } else {
                                Text("Review")
                            }
                        },
                    )
                    Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Finding aid") })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { nav.go(Routes.capture(collectionId)) },
                    icon = { Icon(Icons.Outlined.DocumentScanner, null) },
                    text = { Text("Scan") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> BoxesTab(collectionId, s, nav)
                1 -> SeriesTab(collectionId, nav)
                2 -> ReviewTab(collectionId, nav)
                else -> FindingAidTab(collectionId, nav)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this collection?",
            text = "Every scan, transcription and description in “${s?.collection?.title.orEmpty()}” will be removed from this phone. Export a complete package first if you want a copy.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                scope.launch {
                    c.repo.deleteCollection(collectionId)
                    nav.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxesTab(collectionId: Long, s: CollectionSummary?, nav: Nav) {
    val c = LocalContainer.current
    val foldersFlow = remember { c.repo.dao.folderRows(collectionId) }
    val folders by foldersFlow.collectAsStateWithLifecycle(initialValue = null)
    val list = folders ?: return
    if (list.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = "No boxes yet",
            body = "Start with Box 1, Folder 1. Each scan is filed in the folder you set on the capture screen.",
            actionLabel = "Start scanning",
            onAction = { nav.go(Routes.capture(collectionId, 1, 1)) },
        )
        return
    }
    val byBox = list.groupBy { it.boxNumber }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PipelineStrip() }
        if (s != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${s.documentCount}", "items", Modifier.weight(1f))
                    StatTile("${s.pageCount}", "pages", Modifier.weight(1f))
                    val pct = if (s.documentCount == 0) 0 else (s.reviewedCount * 100 / s.documentCount)
                    StatTile("$pct%", "reviewed", Modifier.weight(1f), accent = Fonds.colors.approved)
                    StatTile("${s.draftCount}", "to review", Modifier.weight(1f), accent = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        byBox.forEach { (boxNumber, rows) ->
            stickyHeader(key = "box-$boxNumber") {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Fonds.colors.labelPaper,
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, Fonds.colors.manilaEdge),
                        ) {
                            Text("BOX $boxNumber", style = LabelType.big, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        val items = rows.sumOf { it.docCount }
                        Text(
                            "${rows.size} folder${if (rows.size == 1) "" else "s"} · $items item${if (items == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(rows, key = { it.folder.id }) { row ->
                FolderCard(row, Modifier.animateItem()) { nav.go(Routes.folder(row.folder.id)) }
            }
        }
    }
}

@Composable
fun FolderCard(row: FolderRow, modifier: Modifier = Modifier, showSeries: Boolean = true, onClick: () -> Unit) {
    val f = row.folder
    val title = f.displayTitle()
    val dates = f.dateRange.ifBlank { Dates.range(row.minDate, row.maxDate) }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderTab(f.number)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "Untitled folder" },
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = if (f.titleIsDraft() || title.isBlank()) FontStyle.Italic else FontStyle.Normal,
                    color = if (title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(
                    dates,
                    "${row.docCount} item${if (row.docCount == 1) "" else "s"}",
                    "${row.pageCount} pp",
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showSeries && row.seriesTitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text(
                            "Series ${com.brianrobinson.fonds.ai.Describer.roman(row.seriesNumber ?: 0)} · ${row.seriesTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FolderStatus(row)
        }
    }
}

@Composable
private fun FolderStatus(row: FolderRow) {
    when {
        row.busyCount > 0 -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        row.errorCount > 0 -> Icon(Icons.Outlined.ErrorOutline, "Needs attention", tint = MaterialTheme.colorScheme.error)
        row.draftCount > 0 -> Surface(color = Fonds.colors.manila, contentColor = Fonds.colors.onManila, shape = CircleShape) {
            Text("${row.draftCount}", style = LabelType.small, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        row.docCount > 0 && row.reviewedCount == row.docCount ->
            Icon(Icons.Outlined.CheckCircle, "All reviewed", tint = Fonds.colors.approved)
        else -> Unit
    }
}

@Composable
internal fun TabIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
