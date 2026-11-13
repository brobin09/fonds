package com.brianrobinson.fonds.ui.screens

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.ai.Describer
import com.brianrobinson.fonds.data.CollectionEntity
import com.brianrobinson.fonds.data.CollectionKeys
import com.brianrobinson.fonds.data.Dates
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.FolderRow
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.SeriesEntity
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.export.Citations
import com.brianrobinson.fonds.export.ExportFormat
import com.brianrobinson.fonds.export.ExportOptions
import com.brianrobinson.fonds.export.Exporter
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.launch

@Composable
fun FindingAidTab(collectionId: Long, nav: Nav) {
    val c = LocalContainer.current
    val colFlow = remember { c.repo.dao.collectionFlow(collectionId) }
    val collection by colFlow.collectAsStateWithLifecycle(initialValue = null)
    val foldersFlow = remember { c.repo.dao.folderRows(collectionId) }
    val folders by foldersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val seriesFlow = remember { c.repo.dao.seriesFlow(collectionId) }
    val series by seriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showDrafts by rememberSaveable { mutableStateOf(true) }
    var exportOpen by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val computedDates by produceState("", folders) { value = c.describer.computedDates(collectionId) }
    val computedExtent by produceState("", folders) { value = c.describer.computedExtent(collectionId) }

    val col = collection ?: return
    val props = Proposals.parse(col.proposals)
    fun field(approved: String, key: String) = approved to (if (showDrafts) props[key] else null)

    Box {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { exportOpen = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        enabled = !printing,
                        onClick = {
                            printing = true
                            scope.launch {
                                val s = c.settings.current
                                val data = c.exporter.load(collectionId, ExportOptions(s.includeItemsInExport, s.includeDraftsInExport), withPages = false)
                                if (data != null) Exporter.print(context, c.exporter.html(data), "Guide to the ${col.title}")
                                printing = false
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Print, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Print / PDF")
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = showDrafts,
                        onClick = { showDrafts = !showDrafts },
                        label = { Text("Show unreviewed drafts") },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { nav.go(Routes.details(collectionId)) }) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit details")
                    }
                }
            }
            item {
                Paper(col, computedDates, computedExtent, { a, k -> field(a, k) }, showDrafts) { nav.go(Routes.details(collectionId)) }
            }
            item {
                SectionLabel("Container list", Modifier.padding(top = 8.dp))
            }
            if (folders.isEmpty()) {
                item {
                    Text("Folders appear here as you scan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            containerGroups(series, folders).forEach { (s, rows) ->
                item(key = "h-${s?.id ?: -1}") {
                    if (s != null) {
                        Column(Modifier.padding(top = 10.dp)) {
                            Text("SERIES ${Describer.roman(s.number)}", style = LabelType.small, color = MaterialTheme.colorScheme.primary)
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            if (s.scopeNote.isNotBlank()) Text(s.scopeNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (series.isNotEmpty()) {
                        Text("Not in a series", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                items(rows, key = { "r-${it.folder.id}" }) { row ->
                    ContainerRow(row, showDrafts) { nav.go(Routes.folder(row.folder.id)) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (exportOpen) {
        ExportSheet(
            collection = col,
            onDismiss = { exportOpen = false },
            onSaved = { msg ->
                exportOpen = false
                scope.launch { snackbar.showSnackbar(msg) }
            },
        )
    }
}

private fun containerGroups(series: List<SeriesEntity>, folders: List<FolderRow>): List<Pair<SeriesEntity?, List<FolderRow>>> {
    if (series.isEmpty()) return listOf(null to folders)
    val out = mutableListOf<Pair<SeriesEntity?, List<FolderRow>>>()
    series.forEach { s -> out += (s to folders.filter { it.folder.seriesId == s.id }) }
    val loose = folders.filter { f -> series.none { it.id == f.folder.seriesId } }
    if (loose.isNotEmpty()) out += (null to loose)
    return out
}

@Composable
private fun Paper(
    col: CollectionEntity,
    computedDates: String,
    computedExtent: String,
    field: (String, String) -> Pair<String, String?>,
    showDrafts: Boolean,
    onEdit: () -> Unit,
) {
    Surface(
        color = Fonds.colors.labelPaper,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            if (col.repository.isNotBlank()) SectionLabel(col.repository, color = MaterialTheme.colorScheme.secondary)
            Text("Guide to the ${col.title}", style = MaterialTheme.typography.headlineSmall)
            val dates = col.dateRange.ifBlank { computedDates }
            if (dates.isNotBlank()) Text(dates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            SectionLabel("Collection overview")
            Spacer(Modifier.height(4.dp))
            Overview("Creator", col.creator)
            Overview("Dates", dates, computed = col.dateRange.isBlank() && dates.isNotBlank())
            Overview("Collection no.", col.identifier)
            Overview("Extent", col.extent.ifBlank { computedExtent }, computed = col.extent.isBlank() && computedExtent.isNotBlank())
            Overview("Repository", col.repository)

            val (abs, absDraft) = field(col.abstractText, Fields.ABSTRACT)
            Block("Abstract", abs, absDraft, onEdit)
            val (scope, scopeDraft) = field(col.scopeContent, Fields.SCOPE_CONTENT)
            Block("Scope and content", scope, scopeDraft, onEdit)
            Block("Biographical / historical note", col.bioHist, null, onEdit)
            val (arr, arrDraft) = field(col.arrangement, Fields.ARRANGEMENT)
            Block("Arrangement", arr, arrDraft, onEdit)
            Block("Conditions governing access", col.accessRestrict, null, onEdit)
            Block("Conditions governing use", col.useRestrict, null, onEdit)
            Block(
                "Preferred citation",
                col.preferredCitation,
                if (showDrafts && col.preferredCitation.isBlank()) Citations.preferred(col.title, col.repository) else null,
                onEdit,
            )
            Block("Processing information", col.processingInfo, null, onEdit)
        }
    }
}

@Composable
private fun Overview(label: String, value: String, computed: Boolean = false) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(112.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
            if (computed) Text("counted from your scans", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun Block(title: String, text: String, draft: String?, onEdit: () -> Unit) {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    when {
        text.isNotBlank() -> Text(text, style = MaterialTheme.typography.bodyMedium)
        !draft.isNullOrBlank() -> Surface(color = Fonds.colors.draft, shape = MaterialTheme.shapes.small, modifier = Modifier.clickable(onClick = onEdit)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = Fonds.colors.onDraft)
                    Spacer(Modifier.width(4.dp))
                    Text("DRAFT · TAP TO REVIEW", style = LabelType.caps, color = Fonds.colors.onDraft)
                }
                Text(draft, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
        }
        else -> Text(
            "Not written yet. Tap to add.",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.clickable(onClick = onEdit),
        )
    }
}

@Composable
private fun ContainerRow(row: FolderRow, showDrafts: Boolean, onClick: () -> Unit) {
    val f = row.folder
    val draftTitle = Proposals.parse(f.proposals)[Fields.TITLE].orEmpty()
    val title = f.title.ifBlank { f.label }.ifBlank { if (showDrafts) draftTitle else "" }
    val isDraft = f.title.isBlank() && f.label.isBlank() && title.isNotBlank()
    val dates = f.dateRange.ifBlank { Dates.range(row.minDate, row.maxDate) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("B${row.boxNumber}", style = LabelType.small, modifier = Modifier.width(36.dp))
        Text("F${f.number}", style = LabelType.small, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.ifBlank { "Untitled folder" },
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = if (isDraft || title.isBlank()) FontStyle.Italic else FontStyle.Normal,
                color = if (title.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${row.docCount} item${if (row.docCount == 1) "" else "s"}" + if (row.reviewedCount < row.docCount) " · ${row.docCount - row.reviewedCount} unreviewed" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(dates, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ---------------------------------------------------------------- export

@Composable
private fun rememberSaver(mime: String, onUri: (Uri) -> Unit): ManagedActivityResultLauncher<String, Uri?> =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri -> if (uri != null) onUri(uri) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    collection: CollectionEntity,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    formats: List<ExportFormat> = ExportFormat.entries,
) {
    val c = LocalContainer.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busy by remember { mutableStateOf<ExportFormat?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun save(format: ExportFormat, uri: Uri) {
        busy = format
        failure = null
        scope.launch {
            val ok = runCatching {
                val s = c.settings.current
                val withPages = format == ExportFormat.ITEM_CSV || format == ExportFormat.JSON || format == ExportFormat.ZIP || format == ExportFormat.IMAGES
                val data = c.exporter.load(collection.id, ExportOptions(s.includeItemsInExport, s.includeDraftsInExport), withPages)
                    ?: error("Collection not found")
                val out = context.contentResolver.openOutputStream(uri) ?: error("Couldn't open the file for writing")
                out.use { c.exporter.write(format, data, it) }
            }
            busy = null
            ok.onSuccess { onSaved("Saved ${format.label}") }
                .onFailure { e -> failure = e.message ?: "Export failed" }
        }
    }

    val launchers = mapOf(
        ExportFormat.EAD to rememberSaver(ExportFormat.EAD.mime) { save(ExportFormat.EAD, it) },
        ExportFormat.HTML to rememberSaver(ExportFormat.HTML.mime) { save(ExportFormat.HTML, it) },
        ExportFormat.CONTAINER_CSV to rememberSaver(ExportFormat.CONTAINER_CSV.mime) { save(ExportFormat.CONTAINER_CSV, it) },
        ExportFormat.ITEM_CSV to rememberSaver(ExportFormat.ITEM_CSV.mime) { save(ExportFormat.ITEM_CSV, it) },
        ExportFormat.JSON to rememberSaver(ExportFormat.JSON.mime) { save(ExportFormat.JSON, it) },
        ExportFormat.ZIP to rememberSaver(ExportFormat.ZIP.mime) { save(ExportFormat.ZIP, it) },
        ExportFormat.IMAGES to rememberSaver(ExportFormat.IMAGES.mime) { save(ExportFormat.IMAGES, it) },
    )

    ModalBottomSheet(onDismissRequest = { if (busy == null) onDismiss() }, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionLabel("Export")
            Text(collection.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Choose where to save. Pick Google Drive in the save dialog to have it on your Mac.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (ExportFormat.EAD in formats || ExportFormat.HTML in formats) {
                ToggleRow("Include item-level descriptions", settings.includeItemsInExport) { v -> c.settings.update { it.copy(includeItemsInExport = v) } }
            }
            ToggleRow("Include unreviewed drafts (marked)", settings.includeDraftsInExport) { v -> c.settings.update { it.copy(includeDraftsInExport = v) } }
            Spacer(Modifier.height(8.dp))
            if (busy != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Preparing ${busy?.label}…", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp)) }
            if (ExportFormat.ZIP in formats) {
                FormatRow(Icons.Outlined.FolderZip, "Send to FondsOS on Mac (ZIP)", "Everything: page images, transcriptions, finding aid and data. Save to Drive or Files, then drop it on FondsOS. Also your backup.", busy == null) {
                    launchers.getValue(ExportFormat.ZIP).launch(c.exporter.suggestedName(collection, ExportFormat.ZIP))
                }
            }
            if (ExportFormat.IMAGES in formats) {
                FormatRow(Icons.Outlined.FolderZip, "All page images (ZIP)", "Just the scans, as JPEG files filed by box and folder. Bulk download.", busy == null) {
                    launchers.getValue(ExportFormat.IMAGES).launch(c.exporter.suggestedName(collection, ExportFormat.IMAGES))
                }
            }
            if (ExportFormat.EAD in formats) {
                FormatRow(Icons.Outlined.Code, "EAD 2002 XML", "Standard encoded finding aid. Imports into ArchivesSpace and most catalogs.", busy == null) {
                    launchers.getValue(ExportFormat.EAD).launch(c.exporter.suggestedName(collection, ExportFormat.EAD))
                }
            }
            if (ExportFormat.HTML in formats) {
                FormatRow(Icons.Outlined.Article, "Finding aid (HTML)", "A styled page to open in any browser, share or post.", busy == null) {
                    launchers.getValue(ExportFormat.HTML).launch(c.exporter.suggestedName(collection, ExportFormat.HTML))
                }
            }
            if (ExportFormat.CONTAINER_CSV in formats) {
                FormatRow(Icons.Outlined.TableChart, "Container list (CSV)", "Box, folder, title, dates and series. Opens in Sheets or Excel.", busy == null) {
                    launchers.getValue(ExportFormat.CONTAINER_CSV).launch(c.exporter.suggestedName(collection, ExportFormat.CONTAINER_CSV))
                }
            }
            if (ExportFormat.ITEM_CSV in formats) {
                FormatRow(Icons.Outlined.ViewList, "Item list (CSV)", "Every item with all fields, a Chicago citation and the full text.", busy == null) {
                    launchers.getValue(ExportFormat.ITEM_CSV).launch(c.exporter.suggestedName(collection, ExportFormat.ITEM_CSV))
                }
            }
            if (ExportFormat.JSON in formats) {
                FormatRow(Icons.Outlined.DataObject, "Data (JSON)", "Everything Fonds knows, for other tools or your own scripts.", busy == null) {
                    launchers.getValue(ExportFormat.JSON).launch(c.exporter.suggestedName(collection, ExportFormat.JSON))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FormatRow(icon: ImageVector, title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Icon(icon, null, Modifier.padding(8.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
