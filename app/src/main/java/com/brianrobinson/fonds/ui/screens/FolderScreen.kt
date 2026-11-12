package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.ai.Describer
import com.brianrobinson.fonds.data.Dates
import com.brianrobinson.fonds.data.DocumentRow
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.displayDate
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.data.displayType
import com.brianrobinson.fonds.data.titleIsDraft
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.ConfirmDialog
import com.brianrobinson.fonds.ui.components.FolderTab
import com.brianrobinson.fonds.ui.components.PipelineStrip
import com.brianrobinson.fonds.ui.components.ProposalField
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.components.StatusPill
import com.brianrobinson.fonds.ui.components.Thumb
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(nav: Nav, folderId: Long) {
    val c = LocalContainer.current
    val rowFlow = remember { c.repo.dao.folderRow(folderId) }
    val row by rowFlow.collectAsStateWithLifecycle(initialValue = null)
    val docsFlow = remember { c.repo.dao.documentRows(folderId) }
    val docs by docsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val collectionId = row?.collectionId
    val seriesFlow = remember(collectionId) { collectionId?.let { c.repo.dao.seriesFlow(it) } ?: flowOf(emptyList()) }
    val series by seriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var reorder by remember { mutableStateOf(false) }
    var drafting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmApprove by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<DocumentRow?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val r = row
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(if (r != null) "Box ${r.boxNumber} · Folder ${r.folder.number}" else "Folder")
                        if (r != null) {
                            Text(
                                "${r.docCount} items · ${r.pageCount} pages",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (r != null) {
                        IconButton(onClick = { nav.go(Routes.capture(r.collectionId, r.boxNumber, r.folder.number)) }) {
                            Icon(Icons.Outlined.DocumentScanner, "Scan into this folder")
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete folder") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (r == null) return@Scaffold
        val f = r.folder
        val props = Proposals.parse(f.proposals)
        val computed = Dates.range(r.minDate, r.maxDate)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PipelineStrip() }
            item {
                Surface(
                    color = Fonds.colors.manila.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, Fonds.colors.manilaEdge),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FolderTab(f.number, large = true)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                SectionLabel("Box ${r.boxNumber}", color = Fonds.colors.onManila)
                                Text(
                                    f.displayTitle().ifBlank { "Untitled folder" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontStyle = if (f.titleIsDraft()) FontStyle.Italic else FontStyle.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        ProposalField(
                            label = "Folder tab reads",
                            value = f.label,
                            proposal = null,
                            onSave = { v -> c.appScope.launch { c.repo.saveFolderField(folderId, "label", v) } },
                            onAccept = {}, onDismiss = {},
                            leading = Icons.Outlined.Label,
                            supporting = "Exactly as written on the original folder",
                        )
                        ProposalField(
                            label = "Folder title (for the finding aid)",
                            value = f.title,
                            proposal = props[Fields.TITLE],
                            onSave = { v -> c.appScope.launch { c.repo.saveFolderField(folderId, Fields.TITLE, v) } },
                            onAccept = { c.appScope.launch { c.repo.acceptFolderProposal(folderId, Fields.TITLE) } },
                            onDismiss = { c.appScope.launch { c.repo.dismissFolderProposal(folderId, Fields.TITLE) } },
                            leading = Icons.Outlined.Title,
                            placeholder = f.label.ifBlank { null },
                        )
                        ProposalField(
                            label = "Dates",
                            value = f.dateRange,
                            proposal = props[Fields.DATE_RANGE]?.takeIf { it != computed || f.dateRange.isNotBlank() },
                            onSave = { v -> c.appScope.launch { c.repo.saveFolderField(folderId, Fields.DATE_RANGE, v) } },
                            onAccept = { c.appScope.launch { c.repo.acceptFolderProposal(folderId, Fields.DATE_RANGE) } },
                            onDismiss = { c.appScope.launch { c.repo.dismissFolderProposal(folderId, Fields.DATE_RANGE) } },
                            leading = Icons.Outlined.CalendarMonth,
                            placeholder = computed.ifBlank { null },
                            supporting = if (f.dateRange.isBlank() && computed.isNotBlank()) "Leave blank to use $computed from the reviewed items" else null,
                        )
                        ProposalField(
                            label = "Scope note",
                            value = f.scopeNote,
                            proposal = props[Fields.SCOPE],
                            onSave = { v -> c.appScope.launch { c.repo.saveFolderField(folderId, Fields.SCOPE, v) } },
                            onAccept = { c.appScope.launch { c.repo.acceptFolderProposal(folderId, Fields.SCOPE) } },
                            onDismiss = { c.appScope.launch { c.repo.dismissFolderProposal(folderId, Fields.SCOPE) } },
                            leading = Icons.Outlined.Notes,
                            singleLine = false,
                            minLines = 2,
                        )
                        SeriesPicker(
                            current = series.firstOrNull { it.id == f.seriesId }?.let { "Series ${Describer.roman(it.number)} · ${it.title}" },
                            options = series.map { it.id to "Series ${Describer.roman(it.number)} · ${it.title}" },
                            onPick = { id -> c.appScope.launch { c.repo.setFolderSeries(folderId, id) } },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    drafting = true
                                    scope.launch {
                                        val outcome = runCatching { c.describer.draftFolder(folderId) }
                                        drafting = false
                                        outcome.onSuccess { o -> snackbar.showSnackbar(o.note.ifBlank { "Drafted with ${o.engine}. Review the suggestions above." }) }
                                            .onFailure { e -> snackbar.showSnackbar(e.message ?: "Couldn't draft the folder.") }
                                    }
                                },
                                enabled = !drafting && docs.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (drafting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (drafting) "Drafting…" else "Draft folder description")
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Items · ${docs.size}", Modifier.weight(1f))
                    if (r.draftCount > 0) {
                        TextButton(onClick = { confirmApprove = true }) {
                            Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Approve ${r.draftCount}")
                        }
                    }
                    FilterChip(selected = reorder, onClick = { reorder = !reorder }, label = { Text("Arrange") })
                }
            }

            itemsIndexed(docs, key = { _, d -> d.document.id }) { i, d ->
                FolderItemRow(
                    d,
                    reorder = reorder,
                    canUp = i > 0,
                    canDown = i < docs.lastIndex,
                    modifier = Modifier.animateItem(),
                    onOpen = { nav.go(Routes.document(d.document.id)) },
                    onUp = { c.appScope.launch { c.repo.moveDocument(d.document.id, true) } },
                    onDown = { c.appScope.launch { c.repo.moveDocument(d.document.id, false) } },
                    onMerge = {
                        c.appScope.launch {
                            if (c.repo.mergeWithNext(d.document.id)) c.pipeline.kick()
                        }
                    },
                    onMove = { moving = d },
                    onDelete = { c.appScope.launch { c.repo.deleteDocument(d.document.id) } },
                )
            }
        }
    }

    if (confirmDelete && r != null) {
        ConfirmDialog(
            title = "Delete Box ${r.boxNumber} · Folder ${r.folder.number}?",
            text = "Its ${r.docCount} items and ${r.pageCount} page images will be removed from this phone.",
            confirmLabel = "Delete folder",
            destructive = true,
            onConfirm = { scope.launch { c.repo.deleteFolder(folderId); nav.back() } },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmApprove && r != null) {
        ConfirmDialog(
            title = "Approve ${r.draftCount} drafts in this folder?",
            text = "Every suggestion on those items will be accepted as written. You can still edit any item afterward.",
            confirmLabel = "Approve",
            onConfirm = {
                val ids = docs.filter { !it.document.reviewed && it.document.status in listOf("drafted", "done") }.map { it.document.id }
                c.appScope.launch { c.repo.approveDocuments(ids) }
            },
            onDismiss = { confirmApprove = false },
        )
    }
    moving?.let { d ->
        MoveDialog(
            initialBox = d.boxNumber,
            initialFolder = d.folderNumber,
            onDismiss = { moving = null },
            onMove = { b, fNum ->
                val cid = r?.collectionId ?: return@MoveDialog
                c.appScope.launch {
                    val target = c.repo.ensureFolder(cid, b, fNum)
                    c.repo.moveToFolder(d.document.id, target.id)
                }
            },
        )
    }
}

@Composable
private fun SeriesPicker(current: String?, options: List<Pair<Long, String>>, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), enabled = options.isNotEmpty()) {
            Icon(Icons.Outlined.AccountTree, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    options.isEmpty() -> "No series yet (create them in the Series tab)"
                    current != null -> current
                    else -> "Not in a series"
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Not in a series") }, onClick = { open = false; onPick(null) })
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(id) })
            }
        }
    }
}

@Composable
private fun FolderItemRow(
    row: DocumentRow,
    reorder: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onMerge: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val d = row.document
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(row.thumbPath)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ITEM ${d.sequence}", style = LabelType.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(d.status, d.reviewed)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    d.displayTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = if (d.titleIsDraft()) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(d.displayDate(), d.displayType(), "${row.pageCount} pp").filter { it.isNotBlank() }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (reorder) {
                Column {
                    IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Item options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (canDown) {
                        DropdownMenuItem(
                            text = { Text("Merge with next item") },
                            leadingIcon = { Icon(Icons.Outlined.MergeType, null) },
                            onClick = { menu = false; onMerge() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Move to another folder") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                        onClick = { menu = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete item") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; confirm = true },
                    )
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Delete item ${d.sequence}?",
            text = "Its ${row.pageCount} page image${if (row.pageCount == 1) "" else "s"} and description will be removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirm = false },
        )
    }
}

@Composable
internal fun MoveDialog(initialBox: Int, initialFolder: Int, onDismiss: () -> Unit, onMove: (Int, Int) -> Unit) {
    var box by remember { mutableStateOf(initialBox.toString()) }
    var folder by remember { mutableStateOf(initialFolder.toString()) }
    val b = box.toIntOrNull()
    val f = folder.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                Text(
                    "The folder is created if it doesn't exist yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = box, onValueChange = { v -> box = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Box") }, singleLine = true, textStyle = LabelType.big,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = folder, onValueChange = { v -> folder = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Folder") }, singleLine = true, textStyle = LabelType.big,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = (b ?: 0) > 0 && (f ?: 0) > 0, onClick = { onMove(b!!, f!!); onDismiss() }) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
