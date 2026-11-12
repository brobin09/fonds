package com.brianrobinson.fonds.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.ai.Describer
import com.brianrobinson.fonds.ai.SeriesSuggestion
import com.brianrobinson.fonds.data.Dates
import com.brianrobinson.fonds.data.FolderRow
import com.brianrobinson.fonds.data.SeriesEntity
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.ConfirmDialog
import com.brianrobinson.fonds.ui.components.EmptyState
import com.brianrobinson.fonds.ui.components.FolderTab
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.components.TextInputDialog
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesTab(collectionId: Long, nav: Nav) {
    val c = LocalContainer.current
    val seriesFlow = remember { c.repo.dao.seriesFlow(collectionId) }
    val series by seriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val foldersFlow = remember { c.repo.dao.folderRows(collectionId) }
    val folders by foldersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var suggesting by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<SeriesSuggestion>?>(null) }
    var suggestionEngine by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SeriesEntity?>(null) }
    var assigning by remember { mutableStateOf<SeriesEntity?>(null) }
    var deleting by remember { mutableStateOf<SeriesEntity?>(null) }

    val unassigned = folders.filter { it.folder.seriesId == null }

    Box {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TabIntro("Series group folders by function or document type. Box and folder order never changes, so original order is always kept.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            suggesting = true
                            scope.launch {
                                val (list, outcome) = runCatching { c.describer.suggestSeries(collectionId) }
                                    .getOrElse { e ->
                                        suggesting = false
                                        snackbar.showSnackbar(e.message ?: "Couldn't suggest series.")
                                        return@launch
                                    }
                                suggesting = false
                                if (list.isEmpty()) {
                                    snackbar.showSnackbar(outcome.note.ifBlank { "No suggestions yet. Describe a few folders first." })
                                } else {
                                    suggestionEngine = outcome.engine
                                    suggestions = list
                                }
                            }
                        },
                        enabled = !suggesting && folders.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (suggesting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (suggesting) "Thinking…" else "Suggest series")
                    }
                    OutlinedButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New series")
                    }
                }
            }

            if (series.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AccountTree,
                        title = "No series yet",
                        body = "Small collections often don't need series; the finding aid will list folders by box. For larger ones, let Fonds suggest an arrangement or make your own.",
                    )
                }
            }

            items(series, key = { it.id }) { s ->
                val members = folders.filter { it.folder.seriesId == s.id }
                SeriesCard(
                    s, members,
                    isFirst = s == series.first(), isLast = s == series.last(),
                    onEdit = { editing = s },
                    onAssign = { assigning = s },
                    onRemoveFolder = { fid -> c.appScope.launch { c.repo.assignFolders(listOf(fid), null) } },
                    onMove = { up -> c.appScope.launch { c.repo.moveSeries(s.id, up) } },
                    onDelete = { deleting = s },
                    onOpenFolder = { fid -> nav.go(Routes.folder(fid)) },
                    modifier = Modifier.animateItem(),
                )
            }

            if (series.isNotEmpty() && unassigned.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    SectionLabel("Not in a series · ${unassigned.size}")
                }
                items(unassigned, key = { "u-${it.folder.id}" }) { row ->
                    FolderCard(row, Modifier.animateItem(), showSeries = false) { nav.go(Routes.folder(row.folder.id)) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (creating) {
        TextInputDialog(
            title = "New series",
            initial = "",
            label = "Series title",
            confirmLabel = "Create",
            secondLabel = "Scope note (optional)",
            onConfirm = {},
            onConfirmTwo = { title, scopeNote -> c.appScope.launch { c.repo.createSeries(collectionId, title, scopeNote) } },
            onDismiss = { creating = false },
        )
    }
    editing?.let { s ->
        TextInputDialog(
            title = "Series ${Describer.roman(s.number)}",
            initial = s.title,
            label = "Series title",
            confirmLabel = "Save",
            secondLabel = "Scope note",
            secondInitial = s.scopeNote,
            onConfirm = {},
            onConfirmTwo = { title, scopeNote -> c.appScope.launch { c.repo.saveSeries(s.copy(title = title, scopeNote = scopeNote)) } },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete “${s.title}”?",
            text = "Its folders stay where they are and become unassigned. Nothing else changes.",
            confirmLabel = "Delete series",
            destructive = true,
            onConfirm = { c.appScope.launch { c.repo.deleteSeries(s.id) } },
            onDismiss = { deleting = null },
        )
    }
    assigning?.let { s ->
        AssignFoldersSheet(
            series = s,
            allSeries = series,
            folders = folders,
            onDismiss = { assigning = null },
            onApply = { ids ->
                c.appScope.launch {
                    val current = folders.filter { it.folder.seriesId == s.id }.map { it.folder.id }.toSet()
                    c.repo.assignFolders(ids.filter { it !in current }, s.id)
                    c.repo.assignFolders(current.filter { it !in ids }, null)
                }
                assigning = null
            },
        )
    }
    suggestions?.let { list ->
        SuggestionSheet(
            list = list,
            engine = suggestionEngine,
            folders = folders,
            hasExisting = series.isNotEmpty(),
            onDismiss = { suggestions = null },
            onApply = { chosen ->
                c.appScope.launch {
                    chosen.forEach { sug ->
                        val id = c.repo.createSeries(collectionId, sug.title, sug.scopeNote)
                        c.repo.assignFolders(sug.folderIds, id)
                    }
                }
                suggestions = null
            },
        )
    }
}

@Composable
private fun SeriesCard(
    s: SeriesEntity,
    members: List<FolderRow>,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onAssign: () -> Unit,
    onRemoveFolder: (Long) -> Unit,
    onMove: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val dates = s.dateRange.ifBlank { Dates.range(members.flatMap { listOf(it.minDate, it.maxDate) }) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.clickable { expanded = !expanded }.padding(start = 14.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SERIES ${Describer.roman(s.number)}", style = LabelType.small, color = MaterialTheme.colorScheme.primary)
                    Text(s.title, style = MaterialTheme.typography.titleLarge)
                    val meta = listOf(dates, "${members.size} folder${if (members.size == 1) "" else "s"}").filter { it.isNotBlank() }.joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.scopeNote.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(s.scopeNote, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) 20 else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Series options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit title and scope") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                        if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, null) }, onClick = { menu = false; onMove(true) })
                        if (!isLast) DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null) }, onClick = { menu = false; onMove(false) })
                        DropdownMenuItem(text = { Text("Delete series") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAssign) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Choose folders")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide folders" else "Show folders")
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 14.dp, end = 4.dp, bottom = 10.dp)) {
                    if (members.isEmpty()) {
                        Text("No folders yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    members.forEach { row ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.clickable { onOpenFolder(row.folder.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("B${row.boxNumber} F${row.folder.number}", style = LabelType.small, modifier = Modifier.width(72.dp))
                            Text(row.folder.displayTitle().ifBlank { "Untitled folder" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onRemoveFolder(row.folder.id) }) { Icon(Icons.Outlined.Close, "Remove from series", Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignFoldersSheet(
    series: SeriesEntity,
    allSeries: List<SeriesEntity>,
    folders: List<FolderRow>,
    onDismiss: () -> Unit,
    onApply: (List<Long>) -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>().apply { addAll(folders.filter { it.folder.seriesId == series.id }.map { it.folder.id }) } }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("Series ${Describer.roman(series.number)}")
            Text(series.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Tick the folders that belong here. A folder can be in only one series.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.heightIn(max = 460.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(folders, key = { it.folder.id }) { row ->
                val id = row.folder.id
                val other = allSeries.firstOrNull { it.id == row.folder.seriesId && it.id != series.id }
                Row(
                    Modifier.fillMaxWidth().clickable { if (id in selected) selected.remove(id) else selected.add(id) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = id in selected, onCheckedChange = { if (it) selected.add(id) else selected.remove(id) })
                    Text("B${row.boxNumber} F${row.folder.number}", style = LabelType.small, modifier = Modifier.width(70.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.folder.displayTitle().ifBlank { "Untitled folder" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (other != null) {
                            Text("Now in Series ${Describer.roman(other.number)}: ${other.title}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onApply(selected.toList()) }) { Text("Save · ${selected.size} folders") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionSheet(
    list: List<SeriesSuggestion>,
    engine: String,
    folders: List<FolderRow>,
    hasExisting: Boolean,
    onDismiss: () -> Unit,
    onApply: (List<SeriesSuggestion>) -> Unit,
) {
    val chosen = remember { mutableStateListOf<Int>().apply { addAll(list.indices) } }
    val byId = folders.associateBy { it.folder.id }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Fonds.colors.onDraft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                SectionLabel("Suggested arrangement · $engine", color = Fonds.colors.onDraft)
            }
            Text("${list.size} series", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (hasExisting) "Chosen series are added alongside your existing ones; folders move into them." else "Untick any you don't want. You can rename and adjust everything afterward.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list.indices.toList()) { i ->
                val s = list[i]
                Surface(
                    color = if (i in chosen) Fonds.colors.draft else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, if (i in chosen) Fonds.colors.manilaEdge else MaterialTheme.colorScheme.outlineVariant),
                    onClick = { if (i in chosen) chosen.remove(i) else chosen.add(i) },
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Checkbox(checked = i in chosen, onCheckedChange = { if (it) chosen.add(i) else chosen.remove(i) })
                        Column(Modifier.weight(1f).padding(top = 10.dp)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            if (s.scopeNote.isNotBlank()) Text(s.scopeNote, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            s.folderIds.take(6).forEach { fid ->
                                val r = byId[fid] ?: return@forEach
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FolderTab(r.folder.number, Modifier.padding(vertical = 2.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "B${r.boxNumber} · ${r.folder.displayTitle().ifBlank { "Untitled folder" }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (s.folderIds.size > 6) {
                                Text("and ${s.folderIds.size - 6} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Not now") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onApply(chosen.sorted().map { list[it] }) }, enabled = chosen.isNotEmpty()) {
                Text("Create ${chosen.size} series")
            }
        }
    }
}
