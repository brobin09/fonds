package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.CollectionEntity
import com.brianrobinson.fonds.data.LocationParse
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.launch

/** Where the items ended up, so the caller can offer to open it. */
data class FiledResult(val message: String, val folderId: Long?)

/**
 * Pick a collection, then a box and folder. Used to add single items to a collection
 * and to move items between collections (or back out to single items).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionSheet(
    docIds: List<Long>,
    currentCollectionId: Long?,
    isSingle: Boolean,
    suggestedLocation: String,
    onDismiss: () -> Unit,
    onDone: (FiledResult) -> Unit,
) {
    val c = LocalContainer.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val collectionsFlow = remember { c.repo.dao.regularCollectionsFlow() }
    val collections by collectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var chosen by remember { mutableStateOf<CollectionEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var box by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var folderInfo by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val n = docIds.size
    val what = if (n == 1) "this item" else "these $n items"

    fun choose(col: CollectionEntity) {
        chosen = col
        box = (LocationParse.box(suggestedLocation) ?: col.cursorBox).toString()
        folder = (LocationParse.folder(suggestedLocation) ?: col.cursorFolder).toString()
    }

    val b = box.toIntOrNull()
    val f = folder.toIntOrNull()
    LaunchedEffect(chosen?.id, b, f) {
        val col = chosen
        folderInfo = if (col == null || b == null || f == null || b <= 0 || f <= 0) {
            ""
        } else {
            val existing = c.repo.findFolder(col.id, b, f)
            if (existing == null) {
                "New folder. It will be created in Box $b."
            } else {
                val count = c.repo.dao.documentsInFolder(existing.id).size
                val t = existing.displayTitle()
                "Existing folder" + (if (t.isNotBlank()) " “$t”" else "") + " with $count item${if (count == 1) "" else "s"}. New items go at the end."
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!working) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(if (isSingle) "Add to a collection" else "Move")
            Text(
                if (chosen == null) "Where should $what go?" else "Box and folder in ${chosen?.title}",
                style = MaterialTheme.typography.headlineSmall,
            )

            val col = chosen
            if (col == null) {
                if (collections.isEmpty() && !creating) {
                    Text(
                        "You don't have any collections yet. Make one here; you can fill in its details later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collections.forEach { cl ->
                        CollectionChoice(cl, isCurrent = cl.id == currentCollectionId) { choose(cl) }
                    }
                    if (!isSingle) {
                        Surface(
                            onClick = {
                                working = true
                                scope.launch {
                                    c.repo.moveToSingleItems(docIds)
                                    working = false
                                    onDone(FiledResult("Moved $what to Single items", null))
                                }
                            },
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Single items", style = MaterialTheme.typography.titleSmall)
                                    Text("Take it out of any collection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (creating) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("New collection title") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { creating = false; newTitle = "" }) { Text("Cancel") }
                        Button(enabled = newTitle.isNotBlank(), onClick = {
                            scope.launch {
                                val id = c.repo.createCollection(newTitle, "", settings.defaultRepository, "")
                                c.repo.dao.getCollection(id)?.let { choose(it) }
                                creating = false
                            }
                        }) { Text("Create") }
                    }
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New collection")
                    }
                }
            } else {
                CollectionChoice(col, isCurrent = col.id == currentCollectionId, onChange = { chosen = null })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = box,
                        onValueChange = { v -> box = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Box") },
                        singleLine = true,
                        textStyle = LabelType.big,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { v -> folder = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Folder") },
                        singleLine = true,
                        textStyle = LabelType.big,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (suggestedLocation.isNotBlank() && (LocationParse.box(suggestedLocation) != null || LocationParse.folder(suggestedLocation) != null)) {
                    Text(
                        "Filled in from where the item came from: $suggestedLocation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (folderInfo.isNotBlank()) {
                    Text(folderInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Folder tab reads (optional, for a new folder)") },
                    leadingIcon = { Icon(Icons.Outlined.Label, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !working && (b ?: 0) > 0 && (f ?: 0) > 0,
                    onClick = {
                        working = true
                        scope.launch {
                            val folderId = c.repo.moveToCollection(docIds, col.id, b!!, f!!, label)
                            working = false
                            onDone(FiledResult("${if (n == 1) "Added" else "Added $n items"} to ${col.title}, Box $b · Folder $f", folderId))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Outlined.Inventory2, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (b != null && f != null) "File in Box $b · Folder $f" else "Choose a box and folder")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionChoice(col: CollectionEntity, isCurrent: Boolean, onChange: (() -> Unit)? = null, onPick: (() -> Unit)? = null) {
    val band = Fonds.colors.collectionBands[col.colorIndex.mod(Fonds.colors.collectionBands.size)]
    Surface(
        onClick = { (onPick ?: onChange)?.invoke() },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(band))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(col.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    col.repository.takeIf { it.isNotBlank() },
                    if (isCurrent) "current collection" else null,
                ).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onChange != null) {
                TextButton(onClick = onChange) { Text("Change") }
            }
        }
    }
}
