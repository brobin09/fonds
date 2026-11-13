package com.brianrobinson.fonds.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.CollectionEntity
import com.brianrobinson.fonds.data.DocStatus
import com.brianrobinson.fonds.data.DocumentRow
import com.brianrobinson.fonds.data.displayDate
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.data.displayType
import com.brianrobinson.fonds.data.titleIsDraft
import com.brianrobinson.fonds.export.ExportFormat
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.ConfirmDialog
import com.brianrobinson.fonds.ui.components.EmptyState
import com.brianrobinson.fonds.ui.components.PipelineStrip
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.components.StatusPill
import com.brianrobinson.fonds.ui.components.Thumb
import com.brianrobinson.fonds.ui.components.ItemTile
import com.brianrobinson.fonds.ui.components.ViewToggle
import com.brianrobinson.fonds.ui.components.galleryColumns
import com.brianrobinson.fonds.ui.components.tileGrid
import com.brianrobinson.fonds.ui.components.rememberScanner
import com.brianrobinson.fonds.ui.theme.Fonds
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Single items: scan one document without starting a collection. Each is read and described
 * like any other item, and can be filed into a collection whenever you're ready.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(nav: Nav, autoScan: Boolean) {
    val c = LocalContainer.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val inbox by produceState<CollectionEntity?>(null) { value = c.repo.inbox() }
    val rowsFlow = remember(inbox?.id) { inbox?.id?.let { c.repo.dao.newestDocumentRows(it) } ?: flowOf(emptyList()) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var handwritten by rememberSaveable { mutableStateOf(false) }
    var onePerPage by rememberSaveable { mutableStateOf(false) }
    var filing by remember { mutableStateOf(false) }
    var autoScanned by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var adding by remember { mutableStateOf<List<Long>?>(null) }
    var deleting by remember { mutableStateOf<DocumentRow?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var sourceOpen by rememberSaveable { mutableStateOf(false) }

    val launchScan = rememberScanner(
        onPages = { uris ->
            scope.launch {
                filing = true
                val s = c.settings.current
                val ids = runCatching {
                    c.repo.addSingleItems(uris, handwritten, onePerPage, s.lastSourceRepository, s.lastSourceCollection, s.lastSourceLocation)
                }.getOrElse { e ->
                    filing = false
                    snackbar.showSnackbar(e.message ?: "Couldn't save the scan.")
                    return@launch
                }
                filing = false
                c.pipeline.kick()
                val msg = if (ids.size == 1) "Saved. Reading and describing it now." else "Saved ${ids.size} items. Reading them now."
                val r = snackbar.showSnackbar(msg, actionLabel = if (ids.size == 1) "Open" else null, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed && ids.size == 1) nav.go(Routes.document(ids.first()))
            }
        },
        onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )

    LaunchedEffect(Unit) {
        if (autoScan && !autoScanned) {
            autoScanned = true
            launchScan()
        }
    }

    fun exitSelection() {
        selecting = false
        selected.clear()
    }

    val columns = galleryColumns()
    val toReview = rows.count { !it.document.reviewed && it.document.status in listOf(DocStatus.DRAFTED, DocStatus.DONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (selecting) exitSelection() else nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Column {
                        Text(if (selecting) "${selected.size} selected" else "Single items")
                        if (!selecting && rows.isNotEmpty()) {
                            Text(
                                "${rows.size} item${if (rows.size == 1) "" else "s"}" + if (toReview > 0) " · $toReview to review" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (rows.isNotEmpty()) {
                        if (selecting) {
                            TextButton(onClick = {
                                if (selected.size == rows.size) selected.clear() else { selected.clear(); selected.addAll(rows.map { it.document.id }) }
                            }) { Text(if (selected.size == rows.size) "None" else "All") }
                        } else {
                            ViewToggle(settings.galleryView) { c.settings.update { it.copy(galleryView = !it.galleryView) } }
                            TextButton(onClick = { selecting = true }) { Text("Select") }
                            IconButton(onClick = { exporting = true }) { Icon(Icons.Outlined.FileDownload, "Export single items") }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (selecting) {
                        Button(
                            onClick = { adding = selected.toList() },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Icon(Icons.Outlined.Inventory2, null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (selected.isEmpty()) "Select items to file" else "Add ${selected.size} to a collection",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    } else {
                        Button(
                            onClick = { launchScan() },
                            enabled = !filing,
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            if (filing) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(12.dp))
                                Text("Saving…", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Scan an item", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PipelineStrip() }
            if (!selecting) {
                item {
                    SourceCard(
                        open = sourceOpen,
                        onToggle = { sourceOpen = !sourceOpen },
                        repository = settings.lastSourceRepository,
                        collection = settings.lastSourceCollection,
                        location = settings.lastSourceLocation,
                        onRepository = { v -> c.settings.update { it.copy(lastSourceRepository = v) } },
                        onCollection = { v -> c.settings.update { it.copy(lastSourceCollection = v) } },
                        onLocation = { v -> c.settings.update { it.copy(lastSourceLocation = v) } },
                        onClear = { c.settings.update { it.copy(lastSourceRepository = "", lastSourceCollection = "", lastSourceLocation = "") } },
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = handwritten,
                            onClick = { handwritten = !handwritten },
                            label = { Text("Handwritten") },
                            leadingIcon = { Icon(Icons.Outlined.Draw, null, Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = onePerPage,
                            onClick = { onePerPage = !onePerPage },
                            label = { Text("One item per page") },
                            leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        title = "Scan one item",
                        body = "A letter, a clipping, a program, a photograph. Fonds reads it, drafts a title, date, names and summary, and gives you a citation. File it into a collection whenever you like, or never.",
                    )
                }
            } else {
                item {
                    SectionLabel(if (selecting) "Tap items to select them" else "Newest first · long-press to select")
                }
                if (settings.galleryView) {
                    tileGrid(rows, columns, "in") { row, m ->
                        val id = row.document.id
                        ItemTile(
                            row, m,
                            caption = listOf(row.document.displayDate(), row.document.sourceCollection).filter { it.isNotBlank() }.joinToString(" · "),
                            selecting = selecting,
                            selected = id in selected,
                            onClick = {
                                if (selecting) {
                                    if (id in selected) selected.remove(id) else selected.add(id)
                                } else {
                                    nav.go(Routes.document(id))
                                }
                            },
                            onLongClick = {
                                selecting = true
                                if (id !in selected) selected.add(id)
                            },
                        )
                    }
                } else items(rows, key = { it.document.id }) { row ->
                    InboxRow(
                        row = row,
                        selecting = selecting,
                        isSelected = row.document.id in selected,
                        modifier = Modifier.animateItem(),
                        onOpen = {
                            if (selecting) {
                                if (row.document.id in selected) selected.remove(row.document.id) else selected.add(row.document.id)
                            } else {
                                nav.go(Routes.document(row.document.id))
                            }
                        },
                        onLongPress = {
                            selecting = true
                            if (row.document.id !in selected) selected.add(row.document.id)
                        },
                        onAdd = { adding = listOf(row.document.id) },
                        onApprove = { c.appScope.launch { c.repo.approveDocument(row.document.id) } },
                        onDelete = { deleting = row },
                    )
                }
            }
        }
    }

    adding?.let { ids ->
        val firstSource = rows.firstOrNull { it.document.id in ids }?.document?.sourceLocation.orEmpty()
        AddToCollectionSheet(
            docIds = ids,
            currentCollectionId = inbox?.id,
            isSingle = true,
            suggestedLocation = firstSource,
            onDismiss = { adding = null },
            onDone = { result ->
                adding = null
                exitSelection()
                scope.launch {
                    val r = snackbar.showSnackbar(result.message, actionLabel = if (result.folderId != null) "Open folder" else null, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed && result.folderId != null) nav.go(Routes.folder(result.folderId))
                }
            },
        )
    }
    deleting?.let { row ->
        ConfirmDialog(
            title = "Delete this item?",
            text = "“${row.document.displayTitle()}” and its ${row.pageCount} page image${if (row.pageCount == 1) "" else "s"} will be removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { c.appScope.launch { c.repo.deleteDocument(row.document.id) } },
            onDismiss = { deleting = null },
        )
    }
    if (exporting) {
        inbox?.let { col ->
            ExportSheet(
                collection = col,
                formats = listOf(ExportFormat.IMAGES, ExportFormat.ITEM_CSV, ExportFormat.JSON, ExportFormat.ZIP),
                onDismiss = { exporting = false },
                onSaved = { msg ->
                    exporting = false
                    scope.launch { snackbar.showSnackbar(msg) }
                },
            )
        }
    }
}

@Composable
private fun SourceCard(
    open: Boolean,
    onToggle: () -> Unit,
    repository: String,
    collection: String,
    location: String,
    onRepository: (String) -> Unit,
    onCollection: (String) -> Unit,
    onLocation: (String) -> Unit,
    onClear: () -> Unit,
) {
    val summary = listOf(collection, location, repository).filter { it.isNotBlank() }.joinToString(" · ")
    Surface(
        color = Fonds.colors.labelPaper,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Fonds.colors.manilaEdge),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Where are these from?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary.ifBlank { "Optional. Applied to each new scan for its citation." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Hide" else "Edit")
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceInput("Repository", repository, "e.g. NCCU Archives", onRepository)
                    SourceInput("Collection", collection, "e.g. James E. Shepard Papers", onCollection)
                    SourceInput("Box / folder or call number", location, "e.g. Box 12, Folder 3", onLocation)
                    Text(
                        "These stick until you change them, so you can scan several items from the same place. When you file an item, its box and folder are filled in from here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.isNotBlank()) {
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceInput(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(hint, fontStyle = FontStyle.Italic) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxRow(
    row: DocumentRow,
    selecting: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onAdd: () -> Unit,
    onApprove: () -> Unit,
    onDelete: () -> Unit,
) {
    val d = row.document
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(checked = isSelected, onCheckedChange = { onOpen() })
            }
            Thumb(row.thumbPath)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StatusPill(d.status, d.reviewed)
                Spacer(Modifier.height(3.dp))
                Text(
                    d.displayTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = if (d.titleIsDraft()) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(d.displayDate(), d.displayType(), "${row.pageCount} pp").filter { it.isNotBlank() }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val from = listOf(d.sourceCollection, d.sourceLocation).filter { it.isNotBlank() }.joinToString(" · ")
                if (from.isNotBlank()) {
                    Text("From $from", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!selecting) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Item options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add to a collection") },
                            leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                            onClick = { menu = false; onAdd() },
                        )
                        if (!d.reviewed && d.status in listOf(DocStatus.DRAFTED, DocStatus.DONE)) {
                            DropdownMenuItem(
                                text = { Text("Approve draft") },
                                leadingIcon = { Icon(Icons.Outlined.Check, null) },
                                onClick = { menu = false; onApprove() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}
