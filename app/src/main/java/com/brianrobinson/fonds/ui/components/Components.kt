package com.brianrobinson.fonds.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.ai.Prompts
import com.brianrobinson.fonds.data.DocStatus
import com.brianrobinson.fonds.ui.theme.Fonds
import com.brianrobinson.fonds.ui.theme.LabelType
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

// ---------------------------------------------------------------- helpers

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Launches Google's document scanner and hands back the page images. */
@Composable
fun rememberScanner(onPages: (List<Uri>) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val pagesCallback by rememberUpdatedState(onPages)
    val errorCallback by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scan?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) pagesCallback(uris)
        }
    }
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    return remember(launcher, scanner) {
        {
            val activity = context.findActivity()
            if (activity == null) {
                errorCallback("Couldn't open the scanner.")
            } else {
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                    .addOnFailureListener { e -> errorCallback(e.message ?: "The scanner isn't available. Update Google Play services.") }
            }
        }
    }
}

// ---------------------------------------------------------------- small pieces

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = LabelType.caps, color = color, modifier = modifier)
}

@Composable
fun Thumb(path: String?, modifier: Modifier = Modifier, width: Dp = 52.dp, height: Dp = 68.dp) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(width, height)
            .clip(shape)
            .background(Fonds.colors.labelPaper)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** A manila folder tab with the folder number, like the real thing. */
@Composable
fun FolderTab(number: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    Surface(
        color = Fonds.colors.manila,
        contentColor = Fonds.colors.onManila,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
        border = BorderStroke(1.dp, Fonds.colors.manilaEdge),
        modifier = modifier.widthIn(min = if (large) 64.dp else 46.dp),
    ) {
        Text(
            "$number",
            style = if (large) LabelType.huge else LabelType.big,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (large) 6.dp else 6.dp),
        )
    }
}

/** "B3 · F12 · #4" in label-maker type. */
@Composable
fun LocationBadge(box: Int, folder: Int, item: Int? = null, modifier: Modifier = Modifier) {
    Surface(
        color = Fonds.colors.labelPaper,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Text(
            buildString {
                append("B").append(box).append(" · F").append(folder)
                if (item != null) append(" · #").append(item)
            },
            style = LabelType.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun StatusPill(status: String, reviewed: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val busy = status in DocStatus.BUSY
    data class Look(val label: String, val bg: Color, val fg: Color, val icon: ImageVector?)
    val look = when {
        busy -> Look(
            when (status) {
                DocStatus.QUEUED -> "Queued"
                DocStatus.READING -> "Reading text"
                DocStatus.READ -> "Waiting to describe"
                else -> "Describing"
            },
            scheme.primaryContainer, scheme.onPrimaryContainer, null,
        )
        status == DocStatus.ERROR -> Look("Needs attention", scheme.errorContainer, scheme.onErrorContainer, Icons.Outlined.ErrorOutline)
        reviewed -> Look("Reviewed", scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Outlined.CheckCircle)
        status == DocStatus.DRAFTED -> Look("Draft ready", Fonds.colors.manila, Fonds.colors.onManila, Icons.Outlined.AutoAwesome)
        else -> Look("To review", scheme.surfaceContainerHighest, scheme.onSurfaceVariant, Icons.Outlined.Schedule)
    }
    Surface(color = look.bg, contentColor = look.fg, shape = CircleShape, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = look.fg)
                Spacer(Modifier.width(5.dp))
            } else if (look.icon != null) {
                Icon(look.icon, null, Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(look.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = Fonds.colors.manila, shape = CircleShape, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Fonds.colors.onManila, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Shows what the processing queue is doing right now. Hidden when idle. */
@Composable
fun PipelineStrip(modifier: Modifier = Modifier) {
    val c = LocalContainer.current
    val activity by c.pipeline.activity.collectAsStateWithLifecycle()
    val waiting by c.pipeline.waitingForForeground.collectAsStateWithLifecycle()
    val busyFlow = remember { c.repo.dao.busyCount() }
    val busy by busyFlow.collectAsStateWithLifecycle(initialValue = 0)
    AnimatedVisibility(
        visible = activity != null || busy > 0,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                if (waiting) {
                    Icon(Icons.Outlined.Pause, null, Modifier.size(16.dp))
                } else {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        activity ?: "Getting ready…",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (busy > 1) {
                        Text("$busy items in the queue", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- AI suggestion card

@Composable
fun DraftCard(
    text: String,
    replacing: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Fonds.colors.draft,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Fonds.colors.manilaEdge),
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Fonds.colors.onDraft, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (replacing) "SUGGESTED CHANGE" else "SUGGESTED",
                    style = LabelType.caps,
                    color = Fonds.colors.onDraft,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Dismiss suggestion", tint = Fonds.colors.onDraft)
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Use & edit")
                    }
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use")
                }
            }
        }
    }
}

/**
 * A text field that saves when you leave it, with any pending AI suggestion shown underneath.
 * The suggestion never replaces your text unless you tap Use.
 */
@Composable
fun ProposalField(
    label: String,
    value: String,
    proposal: String?,
    onSave: (String) -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    supporting: String? = null,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val saveLatest by rememberUpdatedState(onSave)
    val textNow by rememberUpdatedState(text)
    val valueNow by rememberUpdatedState(value)
    DisposableEffect(Unit) {
        onDispose { if (textNow != valueNow) saveLatest(textNow) }
    }
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            placeholder = placeholder?.let { p -> { Text(p) } },
            supportingText = supporting?.let { s -> { Text(s) } },
            leadingIcon = leading?.let { icon -> { Icon(icon, null) } },
            trailingIcon = trailing,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (focused && !state.isFocused && text != value) onSave(text)
                    focused = state.isFocused
                },
        )
        val pending = proposal?.takeIf { it.isNotBlank() && it.trim() != value.trim() }
        var lastShown by remember { mutableStateOf("") }
        if (pending != null) lastShown = pending
        AnimatedVisibility(visible = pending != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            DraftCard(
                text = lastShown,
                replacing = value.isNotBlank(),
                onAccept = onAccept,
                onDismiss = onDismiss,
                onEdit = {
                    onAccept()
                    runCatching { focusRequester.requestFocus() }
                },
            )
        }
    }
}

@Composable
fun TypeField(value: String, proposal: String?, onSave: (String) -> Unit, onAccept: () -> Unit, onDismiss: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    ProposalField(
        label = "Type",
        value = value,
        proposal = proposal,
        onSave = onSave,
        onAccept = onAccept,
        onDismiss = onDismiss,
        leading = Icons.Outlined.Category,
        capitalization = KeyboardCapitalization.Words,
        trailing = {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.ArrowDropDown, "Choose a type") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    Prompts.TYPES.forEach { t ->
                        DropdownMenuItem(text = { Text(t) }, onClick = {
                            open = false
                            onSave(t)
                        })
                    }
                }
            }
        },
    )
}

/** Names, places, organizations and subjects as chips, with suggested additions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipListField(
    label: String,
    icon: ImageVector,
    values: List<String>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAcceptSuggestion: (String) -> Unit,
    onAcceptAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entry by remember { mutableStateOf("") }
    val pending = suggestions.filter { s -> values.none { it.equals(s, ignoreCase = true) } }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            SectionLabel(label)
        }
        if (values.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                values.forEach { v ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(v) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                "Remove $v",
                                Modifier.size(16.dp).clickable { onRemove(v) },
                            )
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it },
            placeholder = { Text("Add ${label.lowercase()}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (entry.isNotBlank()) onAdd(entry.trim())
                entry = ""
            }),
            trailingIcon = {
                IconButton(onClick = {
                    if (entry.isNotBlank()) onAdd(entry.trim())
                    entry = ""
                }) { Icon(Icons.Outlined.Add, "Add") }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        AnimatedVisibility(visible = pending.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                color = Fonds.colors.draft,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, Fonds.colors.manilaEdge),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = Fonds.colors.onDraft, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SUGGESTED · TAP TO ADD", style = LabelType.caps, color = Fonds.colors.onDraft, modifier = Modifier.weight(1f))
                        TextButton(onClick = onAcceptAll) { Text("Add all") }
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss suggestions", tint = Fonds.colors.onDraft) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pending.forEach { s ->
                            SuggestionChip(
                                onClick = { onAcceptSuggestion(s) },
                                label = { Text(s, fontStyle = FontStyle.Italic) },
                                icon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NumberDialog(title: String, initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val n = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                singleLine = true,
                textStyle = LabelType.big,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (n != null && n > 0) { onConfirm(n); onDismiss() } }),
            )
        },
        confirmButton = {
            Button(enabled = n != null && n > 0, onClick = {
                onConfirm(n ?: initial)
                onDismiss()
            }) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    multiline: Boolean = false,
    secondLabel: String? = null,
    secondInitial: String = "",
    onConfirmTwo: ((String, String) -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    var second by remember { mutableStateOf(secondInitial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = !multiline,
                    minLines = if (multiline) 3 else 1,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (secondLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        label = { Text(secondLabel) },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = {
                if (onConfirmTwo != null) onConfirmTwo(text.trim(), second.trim()) else onConfirm(text.trim())
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
