package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.DocumentRow
import com.brianrobinson.fonds.data.displayDate
import com.brianrobinson.fonds.data.displayTitle
import com.brianrobinson.fonds.data.titleIsDraft
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.FolderTab
import com.brianrobinson.fonds.ui.components.NumberDialog
import com.brianrobinson.fonds.ui.components.PipelineStrip
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.components.StatusPill
import com.brianrobinson.fonds.ui.components.Thumb
import com.brianrobinson.fonds.ui.components.rememberScanner
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The reading-room screen. Set where you are (box, folder), scan, repeat.
 * Everything else (text, drafts) happens in the background while you keep scanning.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureScreen(nav: Nav, collectionId: Long, boxArg: Int, folderArg: Int) {
    val c = LocalContainer.current
    val dao = c.repo.dao
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val collectionFlow = remember { dao.collectionFlow(collectionId) }
    val collection by collectionFlow.collectAsStateWithLifecycle(initialValue = null)
    val foldersFlow = remember { dao.folderRows(collectionId) }
    val folders by foldersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var box by rememberSaveable { mutableIntStateOf(-1) }
    var folder by rememberSaveable { mutableIntStateOf(-1) }
    var handwritten by rememberSaveable { mutableStateOf(false) }
    var onePerPage by rememberSaveable { mutableStateOf(settings.onePerPageDefault) }
    var restartPerBox by rememberSaveable { mutableStateOf(true) }
    var filing by remember { mutableStateOf(false) }
    var editBox by remember { mutableStateOf(false) }
    var editFolder by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current

    // Start where you left off (or where you were sent).
    LaunchedEffect(collection != null) {
        val col = collection ?: return@LaunchedEffect
        if (box < 0) {
            box = if (boxArg > 0) boxArg else col.cursorBox
            folder = if (folderArg > 0) folderArg else col.cursorFolder
        }
    }
    LaunchedEffect(box, folder) {
        if (box > 0 && folder > 0) c.repo.setCursor(collectionId, box, folder)
    }

    // Keep the screen awake while you work through a box.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Folder tab label: load for the current folder, save shortly after you stop typing.
    var label by remember { mutableStateOf("") }
    var labelLoadedFor by remember { mutableStateOf(Pair(-1, -1)) }
    LaunchedEffect(box, folder) {
        if (box <= 0) return@LaunchedEffect
        label = c.repo.findFolder(collectionId, box, folder)?.label.orEmpty()
        labelLoadedFor = Pair(box, folder)
    }
    LaunchedEffect(label, box, folder) {
        if (labelLoadedFor != Pair(box, folder)) return@LaunchedEffect
        delay(600)
        c.repo.setFolderLabel(collectionId, box, folder, label)
    }

    val current = folders.firstOrNull { it.boxNumber == box && it.folder.number == folder }
    val docsFlow = remember(current?.folder?.id) {
        current?.folder?.id?.let { dao.documentRows(it) } ?: flowOf(emptyList())
    }
    val docs by docsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val foldersInBox = folders.filter { it.boxNumber == box }.map { it.folder.number }.let { nums ->
        (nums + folder).filter { it > 0 }.distinct().sorted()
    }

    fun moveTo(newBox: Int, newFolder: Int) {
        focus.clearFocus()
        box = newBox.coerceAtLeast(1)
        folder = newFolder.coerceAtLeast(1)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val launchScan = rememberScanner(
        onPages = { uris ->
            val b = box
            val f = folder
            scope.launch {
                filing = true
                val ids = runCatching {
                    c.repo.addScan(collectionId, b, f, uris, handwritten, onePerPage)
                }.getOrElse { e ->
                    filing = false
                    snackbar.showSnackbar(e.message ?: "Couldn't save the scan.")
                    return@launch
                }
                filing = false
                c.pipeline.kick()
                val msg = if (ids.size == 1) {
                    "Filed ${uris.size} page${if (uris.size == 1) "" else "s"} as one item in Box $b · Folder $f"
                } else {
                    "Filed ${ids.size} items in Box $b · Folder $f"
                }
                val result = snackbar.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) ids.forEach { c.repo.deleteDocument(it) }
            }
        },
        onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Column {
                        Text("Capture", style = MaterialTheme.typography.titleLarge)
                        Text(
                            collection?.title.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { nav.go(Routes.collection(collectionId)) }) { Text("Collection") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick = { focus.clearFocus(); launchScan() },
                        enabled = !filing && box > 0,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        if (filing) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("Filing pages…", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(if (onePerPage) "Scan pages" else "Scan document", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (onePerPage) {
                            "Each page becomes its own item. Good for clippings and photographs."
                        } else {
                            "Scan every page of one document, then tap Save in the scanner. That's one item."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PipelineStrip() }

            item {
                Slate(
                    box = box.coerceAtLeast(1),
                    folder = folder.coerceAtLeast(1),
                    label = label,
                    onLabel = { label = it },
                    onBoxMinus = { if (box > 1) moveTo(box - 1, 1) },
                    onBoxPlus = { moveTo(box + 1, if (restartPerBox) 1 else folder + 1) },
                    onFolderMinus = { if (folder > 1) moveTo(box, folder - 1) },
                    onFolderPlus = { moveTo(box, folder + 1) },
                    onEditBox = { editBox = true },
                    onEditFolder = { editFolder = true },
                    onNextFolder = { moveTo(box, folder + 1) },
                    onNextBox = { moveTo(box + 1, if (restartPerBox) 1 else folder + 1) },
                    onDone = { focus.clearFocus() },
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
                    FilterChip(
                        selected = restartPerBox,
                        onClick = { restartPerBox = !restartPerBox },
                        label = { Text("Folders restart at 1 each box") },
                        leadingIcon = { Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp)) },
                    )
                }
                if (handwritten) {
                    Text(
                        if (settings.hasClaude && settings.claudeForHandwritten) {
                            "Handwritten items go to Claude for transcription and description."
                        } else {
                            "On-device text recognition struggles with cursive. Add a Claude key in Settings for real transcriptions."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }

            if (foldersInBox.size > 1) {
                item {
                    SectionLabel("Folders in box $box")
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(foldersInBox) { n ->
                            val selected = n == folder
                            FolderTab(
                                n,
                                modifier = Modifier
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { moveTo(box, n) },
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(
                        if (docs.isEmpty()) "In this folder" else "In this folder · ${docs.size} item${if (docs.size == 1) "" else "s"}",
                        modifier = Modifier.weight(1f),
                    )
                    if (current != null) {
                        TextButton(onClick = { nav.go(Routes.folder(current.folder.id)) }) {
                            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open folder")
                        }
                    }
                }
            }

            if (docs.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                MaterialTheme.shapes.medium,
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Nothing scanned in Box $box · Folder $folder yet.\nTap Scan to capture the first document.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(docs.reversed(), key = { it.document.id }) { row ->
                    CaptureItemRow(row, Modifier.animateItem()) { nav.go(Routes.document(row.document.id)) }
                }
            }
        }
    }

    if (editBox) {
        NumberDialog("Go to box", box, onConfirm = { moveTo(it, if (it == box) folder else 1) }, onDismiss = { editBox = false })
    }
    if (editFolder) {
        NumberDialog("Go to folder", folder, onConfirm = { moveTo(box, it) }, onDismiss = { editFolder = false })
    }
}

@Composable
private fun Slate(
    box: Int,
    folder: Int,
    label: String,
    onLabel: (String) -> Unit,
    onBoxMinus: () -> Unit,
    onBoxPlus: () -> Unit,
    onFolderMinus: () -> Unit,
    onFolderPlus: () -> Unit,
    onEditBox: () -> Unit,
    onEditFolder: () -> Unit,
    onNextFolder: () -> Unit,
    onNextBox: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        color = Fonds.colors.labelPaper,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.5.dp, Fonds.colors.manilaEdge),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Counter("Box", box, onBoxMinus, onBoxPlus, onEditBox, Modifier.weight(1f))
                VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 4.dp), color = Fonds.colors.manilaEdge)
                Counter("Folder", folder, onFolderMinus, onFolderPlus, onEditFolder, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = onLabel,
                label = { Text("Folder tab reads… (optional)") },
                placeholder = { Text("Type it exactly as written", fontStyle = FontStyle.Italic) },
                leadingIcon = { Icon(Icons.Outlined.Label, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNextFolder, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Outlined.SkipNext, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Next folder")
                }
                OutlinedButton(onClick = onNextBox, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Next box")
                }
            }
        }
    }
}

@Composable
private fun Counter(
    title: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel(title, color = Fonds.colors.onManila)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Remove, "Previous $title")
            }
            Text(
                "$value",
                style = LabelType.huge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(86.dp)
                    .clickable(onClick = onEdit)
                    .padding(vertical = 2.dp),
            )
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Add, "Next $title")
            }
        }
        Text("tap number to jump", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun CaptureItemRow(row: DocumentRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val d = row.document
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(row.thumbPath, width = 44.dp, height = 58.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ITEM ${d.sequence}", style = LabelType.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    StatusPill(d.status, d.reviewed)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    d.displayTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = if (d.titleIsDraft()) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf("${row.pageCount} pp", d.displayDate()).filter { it.isNotBlank() }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
