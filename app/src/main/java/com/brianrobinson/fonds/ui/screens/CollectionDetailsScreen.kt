package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.data.CollectionKeys
import com.brianrobinson.fonds.data.Engine
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.anyField
import com.brianrobinson.fonds.export.Citations
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.components.ProposalField
import com.brianrobinson.fonds.ui.components.SectionLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailsScreen(nav: Nav, collectionId: Long) {
    val c = LocalContainer.current
    val flow = remember { c.repo.dao.collectionFlow(collectionId) }
    val col by flow.collectAsStateWithLifecycle(initialValue = null)
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var drafting by remember { mutableStateOf(false) }

    val computedDates by produceState("", col?.updatedAt) { value = c.describer.computedDates(collectionId) }
    val computedExtent by produceState("", col?.updatedAt) { value = c.describer.computedExtent(collectionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Collection details") },
                actions = {
                    TextButton(
                        enabled = !drafting,
                        onClick = {
                            drafting = true
                            scope.launch {
                                val r = runCatching { c.describer.draftCollection(collectionId) }
                                drafting = false
                                r.onSuccess { o -> snackbar.showSnackbar(o.note.ifBlank { "Drafted with ${o.engine}. Review the suggestions." }) }
                                    .onFailure { e -> snackbar.showSnackbar(e.message ?: "Couldn't draft.") }
                            }
                        },
                    ) {
                        if (drafting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Draft")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val cl = col ?: return@Scaffold
        val props = Proposals.parse(cl.proposals)

        @Composable
        fun F(
            label: String,
            key: String,
            multiline: Boolean = false,
            proposal: Boolean = false,
            supporting: String? = null,
            placeholder: String? = null,
            words: Boolean = false,
        ) {
            ProposalField(
                label = label,
                value = cl.anyField(key),
                proposal = if (proposal) props[key] else null,
                onSave = { v -> c.appScope.launch { c.repo.saveCollectionField(collectionId, key, v) } },
                onAccept = { c.appScope.launch { c.repo.acceptCollectionProposal(collectionId, key) } },
                onDismiss = { c.appScope.launch { c.repo.dismissCollectionProposal(collectionId, key) } },
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                supporting = supporting,
                placeholder = placeholder,
                capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "These fields become the top of the finding aid. Tap Draft to have Fonds suggest an abstract, scope and content, dates and extent from what you've described so far.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionLabel("Identity") }
            item { F("Title", CollectionKeys.TITLE, words = true) }
            item { F("Creator", CollectionKeys.CREATOR, words = true, supporting = "Person, family or organization that made or gathered the records") }
            item { F("Collection number", CollectionKeys.IDENTIFIER) }
            item { F("Repository", CollectionKeys.REPOSITORY, words = true) }

            item { SectionLabel("Description", Modifier.padding(top = 8.dp)) }
            item {
                Column {
                    F("Dates", Fields.DATE_RANGE, proposal = true, placeholder = computedDates.ifBlank { null })
                    if (computedDates.isNotBlank() && cl.dateRange != computedDates) {
                        ComputedChip("From your scans: $computedDates") {
                            c.appScope.launch { c.repo.saveCollectionField(collectionId, Fields.DATE_RANGE, computedDates) }
                        }
                    }
                }
            }
            item {
                Column {
                    F("Extent", Fields.EXTENT, proposal = true, placeholder = computedExtent.ifBlank { null })
                    if (computedExtent.isNotBlank() && cl.extent != computedExtent) {
                        ComputedChip("Counted: $computedExtent") {
                            c.appScope.launch { c.repo.saveCollectionField(collectionId, Fields.EXTENT, computedExtent) }
                        }
                    }
                }
            }
            item { F("Abstract", Fields.ABSTRACT, multiline = true, proposal = true) }
            item { F("Scope and content", Fields.SCOPE_CONTENT, multiline = true, proposal = true) }
            item {
                F(
                    "Biographical / historical note", CollectionKeys.BIOHIST, multiline = true,
                    supporting = "Written by you. Fonds never drafts biographical facts.",
                )
            }
            item { F("Arrangement", Fields.ARRANGEMENT, multiline = true, proposal = true) }

            item { SectionLabel("Access and use", Modifier.padding(top = 8.dp)) }
            item { F("Conditions governing access", CollectionKeys.ACCESS, multiline = true, placeholder = "e.g. Open for research.") }
            item { F("Conditions governing use", CollectionKeys.USE, multiline = true) }
            item {
                Column {
                    F("Preferred citation", CollectionKeys.CITATION, multiline = true)
                    if (cl.preferredCitation.isBlank()) {
                        ComputedChip("Use the standard form") {
                            c.appScope.launch {
                                c.repo.saveCollectionField(collectionId, CollectionKeys.CITATION, Citations.preferred(cl.title, cl.repository))
                            }
                        }
                    }
                }
            }
            item {
                Column {
                    F("Processing information", CollectionKeys.PROCESSING, multiline = true)
                    if (cl.processingInfo.isBlank()) {
                        ComputedChip("Write it for me") {
                            val month = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                            val who = settings.processorName.ifBlank { "the processor" }
                            val engines = when (settings.engine) {
                                Engine.OFF -> "by hand"
                                Engine.CLAUDE -> "with Anthropic's Claude"
                                Engine.NANO -> "with Google's on-device Gemini Nano"
                                else -> if (settings.hasClaude) "with Google's on-device Gemini Nano and, for handwritten items, Anthropic's Claude" else "with Google's on-device Gemini Nano"
                            }
                            val text = "Processed and described by $who in $month using Fonds. Pages were captured with a phone document scanner and their text was recognized on the device. Item descriptions were drafted $engines and reviewed by the processor before export."
                            c.appScope.launch { c.repo.saveCollectionField(collectionId, CollectionKeys.PROCESSING, text) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComputedChip(label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        AssistChip(
            onClick = onClick,
            label = { Text(label, maxLines = 2) },
            leadingIcon = { Icon(Icons.Outlined.Calculate, null, Modifier.size(16.dp)) },
        )
    }
}
