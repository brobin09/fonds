package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.ai.NanoState
import android.content.Intent
import com.brianrobinson.fonds.data.CollectionKind
import com.brianrobinson.fonds.data.DocStatus
import com.brianrobinson.fonds.data.DocumentContext
import com.brianrobinson.fonds.data.DocumentEntity
import com.brianrobinson.fonds.data.Engine
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.ListField
import com.brianrobinson.fonds.data.PageEntity
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.field
import com.brianrobinson.fonds.export.Citations
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.Routes
import com.brianrobinson.fonds.ui.components.ChipListField
import com.brianrobinson.fonds.ui.components.ConfirmDialog
import com.brianrobinson.fonds.ui.components.ProposalField
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.components.StatusPill
import com.brianrobinson.fonds.ui.components.TypeField
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentScreen(nav: Nav, documentId: Long) {
    val c = LocalContainer.current
    val dao = c.repo.dao
    val docFlow = remember { dao.documentFlow(documentId) }
    val doc by docFlow.collectAsStateWithLifecycle(initialValue = null)
    val ctxFlow = remember { dao.documentContext(documentId) }
    val ctx by ctxFlow.collectAsStateWithLifecycle(initialValue = null)
    val pagesFlow = remember { dao.pagesFlow(documentId) }
    val pages by pagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val siblingsFlow = remember(ctx?.folderId) { ctx?.folderId?.let { dao.documentRows(it) } ?: flowOf(emptyList()) }
    val siblings by siblingsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val nanoState by c.nano.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberPagerState { pages.size.coerceAtLeast(1) }
    var fullscreen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteItem by remember { mutableStateOf(false) }
    var confirmDeletePage by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingPage by remember { mutableStateOf<PageEntity?>(null) }
    val savePageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/jpeg"),
    ) { uri ->
        val p = pendingPage
        if (uri != null && p != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    File(p.imagePath).inputStream().use { input ->
                        context.contentResolver.openOutputStream(uri)!!.use { out -> input.copyTo(out) }
                    }
                }.isSuccess
            }
            snackbar.showSnackbar(if (ok) "Saved page ${p.sequence}" else "Couldn't save the image")
        }
    }
    val savePagesZip = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            val list = pages
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { os ->
                        java.util.zip.ZipOutputStream(os).use { z ->
                            list.forEach { p ->
                                val f = File(p.imagePath)
                                if (f.exists()) {
                                    z.putNextEntry(java.util.zip.ZipEntry("page-%02d.jpg".format(p.sequence)))
                                    f.inputStream().use { it.copyTo(z) }
                                    z.closeEntry()
                                }
                            }
                        }
                    }
                }.isSuccess
            }
            snackbar.showSnackbar(if (ok) "Saved ${list.size} page images" else "Couldn't save the images")
        }
    }
    val clipboard = LocalClipboardManager.current

    val d = doc
    val isSingle = ctx?.collectionKind == CollectionKind.INBOX
    val index = siblings.indexOfFirst { it.document.id == documentId }
    val prev = siblings.getOrNull(index - 1)
    val next = siblings.getOrNull(index + 1)

    fun launch(block: suspend () -> Unit) {
        c.appScope.launch { block() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(if (d != null) "Item ${d.sequence}" else "Item")
                        ctx?.let { cx ->
                            Text(
                                if (isSingle) {
                                    "Single item" + (d?.sourceCollection?.takeIf { it.isNotBlank() }?.let { " · from $it" } ?: "")
                                } else {
                                    "Box ${cx.boxNumber} · Folder ${cx.folderNumber} · ${cx.collectionTitle}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        enabled = d != null,
                        onClick = {
                            val dd = d ?: return@IconButton
                            val text = shareText(dd, ctx, pages)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, dd.title.ifBlank { "Archival item" })
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Share item"))
                        },
                    ) { Icon(Icons.Outlined.Share, "Share item") }
                    IconButton(onClick = { prev?.let { nav.replace(Routes.document(it.document.id)) } }, enabled = prev != null) {
                        Icon(Icons.Outlined.ChevronLeft, "Previous item")
                    }
                    IconButton(onClick = { next?.let { nav.replace(Routes.document(it.document.id)) } }, enabled = next != null) {
                        Icon(Icons.Outlined.ChevronRight, "Next item")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (nanoState == NanoState.AVAILABLE) {
                                DropdownMenuItem(
                                    text = { Text("Redraft on device") },
                                    leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) },
                                    onClick = { menu = false; c.pipeline.describeWith(documentId, Engine.NANO) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (settings.hasClaude) "Ask Claude (transcribe + describe)" else "Ask Claude (add a key in Settings)") },
                                leadingIcon = { Icon(Icons.Outlined.Cloud, null) },
                                enabled = settings.hasClaude,
                                onClick = {
                                    menu = false
                                    launch { c.repo.setHandwritten(documentId, true) }
                                    c.pipeline.describeWith(documentId, Engine.CLAUDE)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Re-read text on device") },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = { menu = false; launch { c.repo.rereadText(documentId); c.pipeline.kick() } },
                            )
                            DropdownMenuItem(
                                text = { Text("Save this page as image") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    menu = false
                                    pages.getOrNull(pager.currentPage)?.let { pendingPage = it; savePageLauncher.launch("page-%02d.jpg".format(it.sequence)) }
                                },
                            )
                            if (pages.size > 1) {
                                DropdownMenuItem(
                                    text = { Text("Save all ${pages.size} pages (ZIP)") },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                    onClick = { menu = false; savePagesZip.launch("item-pages.zip") },
                                )
                            }
                            HorizontalDivider()
                            if (pages.size > 1 && pager.currentPage > 0) {
                                DropdownMenuItem(
                                    text = { Text("Split before page ${pager.currentPage + 1}") },
                                    leadingIcon = { Icon(Icons.Outlined.CallSplit, null) },
                                    onClick = {
                                        menu = false
                                        val at = pager.currentPage
                                        launch {
                                            c.repo.splitAt(documentId, at)
                                            c.pipeline.kick()
                                        }
                                    },
                                )
                            }
                            if (next != null) {
                                DropdownMenuItem(
                                    text = { Text("Merge with next item") },
                                    leadingIcon = { Icon(Icons.Outlined.MergeType, null) },
                                    onClick = { menu = false; launch { if (c.repo.mergeWithNext(documentId)) c.pipeline.kick() } },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (isSingle) "Add to a collection" else "Move to another folder or collection") },
                                leadingIcon = { Icon(if (isSingle) Icons.Outlined.Inventory2 else Icons.Outlined.DriveFileMove, null) },
                                onClick = { menu = false; moving = true },
                            )
                            HorizontalDivider()
                            if (pages.size > 1) {
                                DropdownMenuItem(
                                    text = { Text("Delete page ${pager.currentPage + 1}") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menu = false; confirmDeletePage = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete item") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDeleteItem = true },
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
        if (d == null) return@Scaffold
        val props = Proposals.parse(d.proposals)
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp),
        ) {
            item {
                PageViewer(pages, pager, Modifier.fillMaxWidth().height(360.dp)) { fullscreen = true }
            }
            if (pages.size > 1) {
                item {
                    PageStrip(pages, pager.currentPage) { i -> scope.launch { pager.animateScrollToPage(i) } }
                }
            }
            item {
                StatusBanner(
                    d,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    onApprove = { launch { c.repo.approveDocument(documentId) } },
                    onRetry = { launch { c.repo.retry(documentId); c.pipeline.kick() } },
                    onReopen = { launch { c.repo.setReviewed(documentId, false) } },
                    onMarkReviewed = { launch { c.repo.approveDocument(documentId) } },
                )
            }
            stickyHeader {
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Describe") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Text") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Cite") })
                }
            }
            when (tab) {
                0 -> item {
                    DescribePane(
                        d, props,
                        isSingle = isSingle,
                        onAddToCollection = { moving = true },
                        nanoAvailable = nanoState == NanoState.AVAILABLE,
                        hasClaude = settings.hasClaude,
                        onField = { key, v -> launch { c.repo.saveDocumentField(documentId, key, v) } },
                        onDate = { v -> launch { c.repo.saveDocumentDate(documentId, v) } },
                        onAccept = { key -> launch { c.repo.acceptProposal(documentId, key) } },
                        onDismiss = { key -> launch { c.repo.dismissProposal(documentId, key) } },
                        onAcceptItem = { key, item -> launch { c.repo.acceptListItem(documentId, key, item) } },
                        onHandwritten = { v -> launch { c.repo.setHandwritten(documentId, v) } },
                        onNote = { v -> launch { c.repo.setNote(documentId, v) } },
                        onRedraft = { engine -> c.pipeline.describeWith(documentId, engine) },
                    )
                }
                1 -> {
                    if (pages.all { it.text.isBlank() } && d.status in DocStatus.BUSY) {
                        item {
                            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Reading the pages…")
                            }
                        }
                    }
                    items(pages, key = { it.id }) { p ->
                        PageTextCard(
                            p,
                            onSave = { v -> launch { c.repo.updatePageText(p.id, v) } },
                            onCopy = {
                                clipboard.setText(AnnotatedString(p.text))
                                scope.launch { snackbar.showSnackbar("Page ${p.sequence} text copied") }
                            },
                            onShowPage = { scope.launch { pager.animateScrollToPage(p.sequence - 1) } },
                        )
                    }
                }
                else -> item {
                    CitePane(d, ctx, isSingle) { text ->
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbar.showSnackbar("Citation copied") }
                    }
                }
            }
        }
    }

    if (fullscreen && pages.isNotEmpty()) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                val bigPager = rememberPagerState(initialPage = pager.currentPage) { pages.size }
                HorizontalPager(bigPager, Modifier.fillMaxSize()) { i -> ZoomableImage(pages[i].imagePath, Modifier.fillMaxSize()) }
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                ) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
                Text(
                    "${bigPager.currentPage + 1} / ${pages.size}",
                    color = Color.White,
                    style = LabelType.small,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
    if (confirmDeleteItem && d != null) {
        ConfirmDialog(
            title = "Delete item ${d.sequence}?",
            text = "Its ${pages.size} page image${if (pages.size == 1) "" else "s"}, transcription and description will be removed.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { scope.launch { c.repo.deleteDocument(documentId); nav.back() } },
            onDismiss = { confirmDeleteItem = false },
        )
    }
    if (confirmDeletePage) {
        val page = pages.getOrNull(pager.currentPage)
        if (page != null) {
            ConfirmDialog(
                title = "Delete page ${page.sequence}?",
                text = "This removes the image and its text from the item.",
                confirmLabel = "Delete page",
                destructive = true,
                onConfirm = { launch { c.repo.deletePage(page.id) } },
                onDismiss = { confirmDeletePage = false },
            )
        }
    }
    if (moving && d != null) {
        AddToCollectionSheet(
            docIds = listOf(documentId),
            currentCollectionId = ctx?.collectionId,
            isSingle = isSingle,
            suggestedLocation = if (isSingle) d.sourceLocation else "",
            onDismiss = { moving = false },
            onDone = { result ->
                moving = false
                scope.launch {
                    val r = snackbar.showSnackbar(
                        result.message,
                        actionLabel = if (result.folderId != null) "Open folder" else null,
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed && result.folderId != null) {
                        nav.go(Routes.folder(result.folderId))
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- viewer

@Composable
private fun PageViewer(pages: List<PageEntity>, pager: PagerState, modifier: Modifier, onExpand: () -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (pages.isEmpty()) {
            Text("No pages", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                ZoomableImage(pages[i].imagePath, Modifier.fillMaxSize().padding(10.dp))
            }
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            ) {
                Text("${pager.currentPage + 1} / ${pages.size}", style = LabelType.small, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            IconButton(onClick = onExpand, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Surface(color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Outlined.Fullscreen, "View full screen", Modifier.padding(4.dp))
                }
            }
        }
    }
}

/** Thumbnails of every page; tap one to jump to it. */
@Composable
private fun PageStrip(pages: List<PageEntity>, current: Int, onPick: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pages.size) { i ->
            val selected = i == current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 46.dp, height = 60.dp)
                        .background(Fonds.colors.labelPaper, MaterialTheme.shapes.small)
                        .border(
                            if (selected) 2.5.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.small,
                        )
                        .clickable { onPick(i) },
                ) {
                    AsyncImage(
                        model = File(pages[i].imagePath),
                        contentDescription = "Page ${i + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                    )
                }
                Text(
                    "${i + 1}",
                    style = LabelType.small,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Pinch to zoom, drag when zoomed, double-tap to toggle. Single-finger swipes go to the pager. */
@Composable
private fun ZoomableImage(path: String, modifier: Modifier = Modifier) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clipToBounds()
            .pointerInput(path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(path) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            },
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}

// ---------------------------------------------------------------- status

@Composable
private fun StatusBanner(
    d: DocumentEntity,
    modifier: Modifier = Modifier,
    onApprove: () -> Unit,
    onRetry: () -> Unit,
    onReopen: () -> Unit,
    onMarkReviewed: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val busy = d.status in DocStatus.BUSY
    val bg = when {
        busy -> scheme.primaryContainer
        d.status == DocStatus.ERROR -> scheme.errorContainer
        d.reviewed -> scheme.tertiaryContainer
        d.status == DocStatus.DRAFTED -> Fonds.colors.draft
        else -> scheme.surfaceContainerHigh
    }
    Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                d.status == DocStatus.ERROR -> Icon(Icons.Outlined.ErrorOutline, null, tint = scheme.onErrorContainer)
                d.reviewed -> Icon(Icons.Outlined.DoneAll, null, tint = scheme.onTertiaryContainer)
                d.status == DocStatus.DRAFTED -> Icon(Icons.Outlined.AutoAwesome, null, tint = Fonds.colors.onDraft)
                else -> Icon(Icons.Outlined.Notes, null)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val (title, body) = when {
                    busy -> (d.statusDetail.ifBlank { "Working" } + "…") to "You can keep scanning; this finishes in the background."
                    d.status == DocStatus.ERROR -> "Something went wrong" to d.statusDetail
                    d.reviewed -> "Reviewed" to (if (d.engine.isNotBlank()) "Drafted by ${d.engine}" else "Described by hand")
                    d.status == DocStatus.DRAFTED -> "Draft ready" to "By ${d.engine}. Check each suggestion against the page."
                    else -> "Text ready" to "Describe the item below, then mark it reviewed."
                }
                Text(title, style = MaterialTheme.typography.labelLarge)
                if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            when {
                busy -> Unit
                d.status == DocStatus.ERROR -> TextButton(onClick = onRetry) { Text("Retry") }
                d.reviewed -> TextButton(onClick = onReopen) { Text("Reopen") }
                d.status == DocStatus.DRAFTED -> Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Fonds.colors.approved, contentColor = scheme.onTertiary),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve all")
                }
                else -> TextButton(onClick = onMarkReviewed) { Text("Mark reviewed") }
            }
        }
    }
}

// ---------------------------------------------------------------- describe

@Composable
private fun DescribePane(
    d: DocumentEntity,
    props: Map<String, String>,
    isSingle: Boolean,
    onAddToCollection: () -> Unit,
    nanoAvailable: Boolean,
    hasClaude: Boolean,
    onField: (String, String) -> Unit,
    onDate: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onAcceptItem: (String, String) -> Unit,
    onHandwritten: (Boolean) -> Unit,
    onNote: (String) -> Unit,
    onRedraft: (String) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isSingle) {
            Surface(
                color = Fonds.colors.labelPaper,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, Fonds.colors.manilaEdge),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Where it's from", color = MaterialTheme.colorScheme.secondary)
                    ProposalField(
                        label = "Repository", value = d.sourceRepository, proposal = null,
                        onSave = { onField(Fields.SOURCE_REPOSITORY, it) }, onAccept = {}, onDismiss = {},
                        leading = Icons.Outlined.Business, capitalization = KeyboardCapitalization.Words,
                    )
                    ProposalField(
                        label = "Collection", value = d.sourceCollection, proposal = null,
                        onSave = { onField(Fields.SOURCE_COLLECTION, it) }, onAccept = {}, onDismiss = {},
                        leading = Icons.Outlined.Inventory2, capitalization = KeyboardCapitalization.Words,
                    )
                    ProposalField(
                        label = "Box / folder or call number", value = d.sourceLocation, proposal = null,
                        onSave = { onField(Fields.SOURCE_LOCATION, it) }, onAccept = {}, onDismiss = {},
                        leading = Icons.Outlined.Place, capitalization = KeyboardCapitalization.Words,
                        placeholder = "e.g. Box 12, Folder 3",
                    )
                    Text(
                        "Used in the citation. Filing it into a collection fills box and folder from here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onAddToCollection, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to a collection")
                    }
                }
            }
        }
        ProposalField(
            label = "Title", value = d.title, proposal = props[Fields.TITLE],
            onSave = { onField(Fields.TITLE, it) }, onAccept = { onAccept(Fields.TITLE) }, onDismiss = { onDismiss(Fields.TITLE) },
            leading = Icons.Outlined.Title, singleLine = false, minLines = 1,
            placeholder = "e.g. Letter from Mary Smith to the Board of Trustees",
        )
        ProposalField(
            label = "Date", value = d.date, proposal = props[Fields.DATE],
            onSave = onDate, onAccept = { onAccept(Fields.DATE) }, onDismiss = { onDismiss(Fields.DATE) },
            leading = Icons.Outlined.Event,
            placeholder = "1952 January 5 · circa 1950 · undated",
            supporting = if (d.dateSort.isNotBlank()) "Sorts as ${d.dateSort}" else "Use DACS form: year, month, day",
        )
        TypeField(
            value = d.docType, proposal = props[Fields.TYPE],
            onSave = { onField(Fields.TYPE, it) }, onAccept = { onAccept(Fields.TYPE) }, onDismiss = { onDismiss(Fields.TYPE) },
        )
        ProposalField(
            label = "Creator", value = d.creator, proposal = props[Fields.CREATOR],
            onSave = { onField(Fields.CREATOR, it) }, onAccept = { onAccept(Fields.CREATOR) }, onDismiss = { onDismiss(Fields.CREATOR) },
            leading = Icons.Outlined.Person, capitalization = KeyboardCapitalization.Words,
        )
        ProposalField(
            label = "Recipient", value = d.recipient, proposal = props[Fields.RECIPIENT],
            onSave = { onField(Fields.RECIPIENT, it) }, onAccept = { onAccept(Fields.RECIPIENT) }, onDismiss = { onDismiss(Fields.RECIPIENT) },
            leading = Icons.Outlined.PersonOutline, capitalization = KeyboardCapitalization.Words,
        )
        ProposalField(
            label = "Summary", value = d.summary, proposal = props[Fields.SUMMARY],
            onSave = { onField(Fields.SUMMARY, it) }, onAccept = { onAccept(Fields.SUMMARY) }, onDismiss = { onDismiss(Fields.SUMMARY) },
            leading = Icons.Outlined.ShortText, singleLine = false, minLines = 3,
        )
        listOf(
            Triple(Fields.PEOPLE, "People", Icons.Outlined.Person),
            Triple(Fields.PLACES, "Places", Icons.Outlined.Place),
            Triple(Fields.ORGS, "Organizations", Icons.Outlined.Business),
            Triple(Fields.SUBJECTS, "Subjects", Icons.Outlined.Sell),
        ).forEach { (key, label, icon) ->
            val values = ListField.split(d.field(key))
            ChipListField(
                label = label,
                icon = icon,
                values = values,
                suggestions = ListField.split(props[key].orEmpty()),
                onAdd = { v -> onField(key, ListField.join(values + v)) },
                onRemove = { v -> onField(key, ListField.join(values.filterNot { it == v })) },
                onAcceptSuggestion = { v -> onAcceptItem(key, v) },
                onAcceptAll = { onAccept(key) },
                onDismiss = { onDismiss(key) },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Draw, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Handwritten", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (hasClaude) "Routes this item to Claude for transcription" else "Marks it for a better reader later",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = d.handwritten, onCheckedChange = onHandwritten)
        }
        ProposalField(
            label = "Processor's note (not exported)", value = d.note, proposal = null,
            onSave = onNote, onAccept = {}, onDismiss = {},
            leading = Icons.Outlined.TextSnippet, singleLine = false, minLines = 2,
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(12.dp)) {
                SectionLabel("Get a fresh draft")
                Text(
                    "New suggestions appear next to your fields. Nothing you've written is replaced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRedraft(Engine.NANO) }, enabled = nanoAvailable, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("On device")
                    }
                    OutlinedButton(onClick = { onRedraft(Engine.CLAUDE) }, enabled = hasClaude, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Cloud, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Claude")
                    }
                }
                if (!nanoAvailable || !hasClaude) {
                    Text(
                        listOfNotNull(
                            if (!nanoAvailable) "On-device AI isn't ready on this phone (see Settings)." else null,
                            if (!hasClaude) "Add a Claude key in Settings to use Claude." else null,
                        ).joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- text

@Composable
private fun PageTextCard(p: PageEntity, onSave: (String) -> Unit, onCopy: () -> Unit, onShowPage: () -> Unit) {
    val source = when (p.textSource) {
        "mlkit" -> "On-device OCR"
        "claude" -> "Claude transcription"
        "edited" -> "Edited by you"
        else -> "Not read yet"
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PAGE ${p.sequence}",
                style = LabelType.small,
                modifier = Modifier.clickable(onClick = onShowPage).padding(end = 8.dp),
            )
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraSmall) {
                Text(source, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCopy, enabled = p.text.isNotBlank()) { Icon(Icons.Outlined.ContentCopy, "Copy page text") }
        }
        ProposalField(
            label = "Text",
            value = p.text,
            proposal = null,
            onSave = onSave,
            onAccept = {},
            onDismiss = {},
            singleLine = false,
            minLines = 6,
        )
    }
}

// ---------------------------------------------------------------- cite

@Composable
private fun CitePane(d: DocumentEntity, ctx: DocumentContext?, isSingle: Boolean, onCopy: (String) -> Unit) {
    if (ctx == null) return
    val props = Proposals.parse(d.proposals)
    fun v(key: String) = d.field(key).ifBlank { props[key].orEmpty() }
    val title = v(Fields.TITLE).ifBlank { "Item ${d.sequence}" }
    val full = if (isSingle) {
        Citations.singleNote(v(Fields.CREATOR), v(Fields.RECIPIENT), title, v(Fields.DATE), d.sourceLocation, d.sourceCollection, d.sourceRepository)
    } else {
        Citations.note(v(Fields.CREATOR), v(Fields.RECIPIENT), title, v(Fields.DATE), ctx.boxNumber, ctx.folderNumber, ctx.collectionTitle, ctx.repository)
    }
    val short = Citations.shortNote(v(Fields.CREATOR), v(Fields.RECIPIENT), title, v(Fields.DATE))
    val bib = if (isSingle) Citations.bibliography(d.sourceCollection, d.sourceRepository) else Citations.bibliography(ctx.collectionTitle, ctx.repository)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!d.reviewed && props.isNotEmpty()) {
            Text(
                "Uses unreviewed drafts where fields are empty. Review the item for a final citation.",
                style = MaterialTheme.typography.bodySmall,
                color = Fonds.colors.onDraft,
            )
        }
        CiteCard("Chicago note", full, onCopy)
        CiteCard("Short note", short, onCopy)
        if (bib != ".") CiteCard("Bibliography", bib, onCopy)
        if (isSingle && d.sourceRepository.isBlank() && d.sourceCollection.isBlank() && d.sourceLocation.isBlank()) {
            Text(
                "Add where it's from on the Describe tab to complete the citation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isSingle) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(12.dp)) {
                SectionLabel("Location")
                LocationLine("Collection", ctx.collectionTitle)
                LocationLine("Repository", ctx.repository)
                LocationLine("Box", "${ctx.boxNumber}")
                LocationLine("Folder", "${ctx.folderNumber}" + (ctx.folderTitle.ifBlank { ctx.folderLabel }.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                LocationLine("Item", "${d.sequence}")
            }
        }
    }
}

@Composable
private fun CiteCard(label: String, text: String, onCopy: (String) -> Unit) {
    Surface(color = Fonds.colors.labelPaper, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.FormatQuote, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(label)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { onCopy(text) }) { Icon(Icons.Outlined.ContentCopy, "Copy $label") }
        }
    }
}

@Composable
private fun LocationLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun shareText(d: DocumentEntity, ctx: DocumentContext?, pages: List<PageEntity>): String {
    val props = Proposals.parse(d.proposals)
    fun v(key: String) = d.field(key).ifBlank { props[key].orEmpty() }
    val single = ctx?.collectionKind == CollectionKind.INBOX
    val title = v(Fields.TITLE).ifBlank { "Untitled item" }
    val citation = if (single || ctx == null) {
        Citations.singleNote(v(Fields.CREATOR), v(Fields.RECIPIENT), title, v(Fields.DATE), d.sourceLocation, d.sourceCollection, d.sourceRepository)
    } else {
        Citations.note(v(Fields.CREATOR), v(Fields.RECIPIENT), title, v(Fields.DATE), ctx.boxNumber, ctx.folderNumber, ctx.collectionTitle, ctx.repository)
    }
    return buildString {
        appendLine(title)
        listOf(v(Fields.DATE), v(Fields.TYPE)).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }?.let { appendLine(it) }
        if (v(Fields.CREATOR).isNotBlank() || v(Fields.RECIPIENT).isNotBlank()) {
            appendLine(listOf(v(Fields.CREATOR), v(Fields.RECIPIENT)).filter { it.isNotBlank() }.joinToString(" to "))
        }
        if (v(Fields.SUMMARY).isNotBlank()) { appendLine(); appendLine(v(Fields.SUMMARY)) }
        listOf(Fields.PEOPLE to "People", Fields.PLACES to "Places", Fields.ORGS to "Organizations", Fields.SUBJECTS to "Subjects").forEach { (k, label) ->
            if (v(k).isNotBlank()) appendLine("$label: ${v(k)}")
        }
        appendLine(); appendLine(citation)
        if (!d.reviewed && props.isNotEmpty()) appendLine("(Description not yet reviewed.)")
        val text = pages.filter { it.text.isNotBlank() }
        if (text.isNotEmpty()) {
            appendLine(); appendLine("Transcription")
            text.forEach { p -> appendLine(); appendLine("[page ${p.sequence}]"); appendLine(p.text) }
        }
    }.trim()
}
