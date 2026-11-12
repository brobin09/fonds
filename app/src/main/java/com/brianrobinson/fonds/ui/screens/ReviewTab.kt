package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.DocStatus
import com.brianrobinson.fonds.data.DocumentRow
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.ListField
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.displayDate
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.data.displayType
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.ConfirmDialog
import com.brianrobinson.fonds.ui.components.EmptyState
import com.brianrobinson.fonds.ui.components.LocationBadge
import com.brianrobinson.fonds.ui.components.PipelineStrip
import com.brianrobinson.fonds.ui.components.StatusPill
import com.brianrobinson.fonds.ui.components.Thumb
import com.brianrobinson.fonds.ui.theme.Fonds
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewTab(collectionId: Long, nav: Nav) {
    val c = LocalContainer.current
    val queueFlow = remember { c.repo.dao.reviewQueue(collectionId) }
    val queue by queueFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val progressFlow = remember { c.repo.dao.inProgress(collectionId) }
    val progress by progressFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var confirmAll by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PipelineStrip() }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("To review (${queue.size})") }
                SegmentedButton(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("In progress (${progress.size})") }
            }
        }

        if (mode == 0) {
            if (queue.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.DoneAll,
                        title = "All caught up",
                        body = if (progress.isNotEmpty()) {
                            "${progress.size} item${if (progress.size == 1) " is" else "s are"} still being read or described. Drafts will appear here."
                        } else {
                            "Every item has been reviewed. Scan more, or export the finding aid."
                        },
                    )
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TabIntro(
                            "Check each draft against the page. Approving accepts every suggestion on the item; open it to change anything first.",
                            Modifier.weight(1f),
                        )
                    }
                }
                if (queue.size > 1) {
                    item {
                        OutlinedButton(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Approve all ${queue.size}")
                        }
                    }
                }
                items(queue, key = { it.document.id }) { row ->
                    ReviewCard(
                        row,
                        modifier = Modifier.animateItem(),
                        onOpen = { nav.go(Routes.document(row.document.id)) },
                        onApprove = { c.appScope.launch { c.repo.approveDocument(row.document.id) } },
                    )
                }
            }
        } else {
            if (progress.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.HourglassTop,
                        title = "Nothing in progress",
                        body = "New scans are read on the phone first, then described. They show up here while that happens.",
                    )
                }
            } else {
                items(progress, key = { it.document.id }) { row ->
                    ProgressRow(row, Modifier.animateItem(), onOpen = { nav.go(Routes.document(row.document.id)) }) {
                        c.appScope.launch {
                            c.repo.retry(row.document.id)
                            c.pipeline.kick()
                        }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = "Approve ${queue.size} drafts?",
            text = "Every suggestion on these items will be accepted as written. You can still open and edit any item afterward.",
            confirmLabel = "Approve all",
            onConfirm = {
                val ids = queue.map { it.document.id }
                c.appScope.launch { c.repo.approveDocuments(ids) }
            },
            onDismiss = { confirmAll = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(row: DocumentRow, modifier: Modifier = Modifier, onOpen: () -> Unit, onApprove: () -> Unit) {
    val d = row.document
    val props = Proposals.parse(d.proposals)
    val summary = d.summary.ifBlank { props[Fields.SUMMARY].orEmpty() }
    val people = ListField.split(d.people) + ListField.split(props[Fields.PEOPLE].orEmpty())
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Thumb(row.thumbPath, width = 72.dp, height = 96.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocationBadge(row.boxNumber, row.folderNumber, d.sequence)
                        Spacer(Modifier.width(6.dp))
                        Text("${row.pageCount} pp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        d.displayTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = if (d.title.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOf(d.displayDate(), d.displayType()).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (people.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    people.distinct().take(5).forEach { p ->
                        Surface(color = Fonds.colors.draft, shape = MaterialTheme.shapes.small) {
                            Text(p, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = Fonds.colors.onDraft)
                Spacer(Modifier.width(4.dp))
                Text(
                    d.engine.ifBlank { "Needs your description" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onOpen) { Text("Open") }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Fonds.colors.approved, contentColor = MaterialTheme.colorScheme.onTertiary),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(row: DocumentRow, modifier: Modifier = Modifier, onOpen: () -> Unit, onRetry: () -> Unit) {
    val d = row.document
    Surface(
        onClick = onOpen,
        color = if (d.status == DocStatus.ERROR) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(row.thumbPath, width = 40.dp, height = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocationBadge(row.boxNumber, row.folderNumber, d.sequence)
                    Spacer(Modifier.weight(1f))
                    StatusPill(d.status, d.reviewed)
                }
                if (d.status == DocStatus.ERROR) {
                    Spacer(Modifier.height(4.dp))
                    Text(d.statusDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3)
                }
            }
            if (d.status == DocStatus.ERROR) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry")
                }
            }
        }
    }
}
