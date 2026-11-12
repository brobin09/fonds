package com.brianrobinson.fonds.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brianrobinson.fonds.LocalContainer
import com.brianrobinson.fonds.ai.ClaudeEngine
import com.brianrobinson.fonds.ai.NanoState
import com.brianrobinson.fonds.data.Engine
import com.brianrobinson.fonds.ui.Nav
import com.brianrobinson.fonds.ui.components.SectionLabel
import com.brianrobinson.fonds.ui.theme.Fonds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: Nav) {
    val c = LocalContainer.current
    val s by c.settings.state.collectAsStateWithLifecycle()
    val nano by c.nano.state.collectAsStateWithLifecycle()
    val nanoBytes by c.nano.downloadedBytes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val storage by produceState(0L) { value = withContext(Dispatchers.IO) { c.images.totalBytes() } }

    fun update(t: (com.brianrobinson.fonds.data.SettingsState) -> com.brianrobinson.fonds.data.SettingsState) = c.settings.update(t)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel("Who drafts descriptions") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineOption(Engine.AUTO, s.engine, "Automatic", "Recommended. Gemini Nano on this phone drafts descriptions. Handwritten items go to Claude if you add a key below.") { update { it.copy(engine = Engine.AUTO) } }
                    EngineOption(Engine.NANO, s.engine, "On this phone only", "Nothing ever leaves the Pixel. Uses Gemini Nano; handwriting will be rough.") { update { it.copy(engine = Engine.NANO) } }
                    EngineOption(Engine.CLAUDE, s.engine, "Claude for everything", "Sends page images and text to Anthropic for every item. Most accurate; billed per use to your API key.") { update { it.copy(engine = Engine.CLAUDE) } }
                    EngineOption(Engine.OFF, s.engine, "No AI", "Text is still read on the phone. Dates and letter writers are picked out by pattern; you write the rest.") { update { it.copy(engine = Engine.OFF) } }
                }
            }

            item { SectionLabel("On-device AI", Modifier.padding(top = 10.dp)) }
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, title, tint) = when (nano) {
                            NanoState.AVAILABLE -> Triple(Icons.Outlined.CheckCircle, "Gemini Nano is ready", Fonds.colors.approved)
                            NanoState.DOWNLOADABLE -> Triple(Icons.Outlined.CloudDownload, "Gemini Nano can be downloaded", MaterialTheme.colorScheme.primary)
                            NanoState.DOWNLOADING -> Triple(Icons.Outlined.CloudDownload, "Downloading Gemini Nano…", MaterialTheme.colorScheme.primary)
                            NanoState.CHECKING -> Triple(Icons.Outlined.PhoneAndroid, "Checking this phone…", MaterialTheme.colorScheme.onSurfaceVariant)
                            NanoState.UNAVAILABLE -> Triple(Icons.Outlined.ErrorOutline, "Not available right now", MaterialTheme.colorScheme.error)
                        }
                        Icon(icon, null, tint = tint)
                        Spacer(Modifier.width(10.dp))
                        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { c.nano.refresh() } }) { Icon(Icons.Outlined.Refresh, "Check again") }
                    }
                    Text(
                        when (nano) {
                            NanoState.AVAILABLE -> "Descriptions are drafted on the Pixel's own chip. Android only runs it while Fonds is open, so drafting pauses in the background."
                            NanoState.DOWNLOADABLE -> "A one-time download through Android's AICore. Use Wi-Fi."
                            NanoState.DOWNLOADING -> "You can keep using the app."
                            NanoState.CHECKING -> ""
                            NanoState.UNAVAILABLE -> "Fonds will use pattern matching (or Claude, if you allow it) until it's available. Updating Android System Intelligence / AICore from the Play Store usually fixes this." +
                                if (c.nano.lastError.isNotBlank()) "\n\n${c.nano.lastError}" else ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (nano == NanoState.DOWNLOADING) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (nanoBytes > 0) Text("${nanoBytes / 1_000_000} MB", style = MaterialTheme.typography.labelSmall)
                    }
                    if (nano == NanoState.DOWNLOADABLE) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { scope.launch { c.nano.download() } }) {
                            Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download model")
                        }
                    }
                }
            }

            item { SectionLabel("Claude (optional)", Modifier.padding(top = 10.dp)) }
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("For handwriting and hard pages", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "When Claude is used, that item's page images and text are sent to Anthropic's API. Check the archive's policy on sharing reproductions before turning it on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = s.claudeKey,
                        onValueChange = { v -> update { it.copy(claudeKey = v.trim()) }; testResult = null },
                        label = { Text("Anthropic API key") },
                        placeholder = { Text("sk-ant-…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Key, null) },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showKey) "Hide key" else "Show key")
                            }
                        },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        supportingText = { Text("Stored only on this phone, in Fonds's private storage") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Model", style = MaterialTheme.typography.labelLarge)
                    ClaudeEngine.MODELS.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = s.claudeModel == m.id, role = Role.RadioButton) { update { it.copy(claudeModel = m.id) } }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.claudeModel == m.id, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(m.label, style = MaterialTheme.typography.bodyLarge)
                                Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ToggleLine("Use Claude for items marked handwritten", s.claudeForHandwritten) { v -> update { it.copy(claudeForHandwritten = v) } }
                    ToggleLine("Use Claude when on-device AI isn't available", s.claudeFallback) { v -> update { it.copy(claudeFallback = v) } }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            enabled = s.hasClaude && !testing,
                            onClick = {
                                testing = true
                                testResult = null
                                scope.launch {
                                    testResult = runCatching { c.claude.test(s.claudeKey, s.claudeModel) }
                                        .fold({ "Connected. Claude says: $it" }, { e -> e.message ?: "Couldn't connect." })
                                    testing = false
                                }
                            },
                        ) {
                            if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Test connection")
                        }
                    }
                    testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                }
            }

            item { SectionLabel("Processing", Modifier.padding(top = 10.dp)) }
            item {
                Card {
                    SettingField("Your name (for processing notes)", s.processorName, words = true) { v -> update { it.copy(processorName = v) } }
                    Spacer(Modifier.height(8.dp))
                    SettingField("Default repository", s.defaultRepository, words = true) { v -> update { it.copy(defaultRepository = v) } }
                }
            }

            item { SectionLabel("Capture", Modifier.padding(top = 10.dp)) }
            item {
                Card {
                    ToggleLine("Keep the screen on while capturing", s.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
                    ToggleLine("Start in one-item-per-page mode", s.onePerPageDefault) { v -> update { it.copy(onePerPageDefault = v) } }
                }
            }

            item { SectionLabel("Export", Modifier.padding(top = 10.dp)) }
            item {
                Card {
                    ToggleLine("Include item-level descriptions", s.includeItemsInExport) { v -> update { it.copy(includeItemsInExport = v) } }
                    ToggleLine("Include unreviewed drafts (marked)", s.includeDraftsInExport) { v -> update { it.copy(includeDraftsInExport = v) } }
                }
            }

            item { SectionLabel("Appearance", Modifier.padding(top = 10.dp)) }
            item {
                Card {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { i, (key, label) ->
                            SegmentedButton(
                                selected = s.theme == key,
                                onClick = { update { it.copy(theme = key) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ToggleLine("Use wallpaper colors (Material You)", s.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
                }
            }

            item { SectionLabel("Storage", Modifier.padding(top = 10.dp)) }
            item {
                Card {
                    Text("Scans use ${"%.1f".format(storage / 1_000_000.0)} MB on this phone.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Everything lives in Fonds's private storage. Export a complete package (ZIP) to Google Drive now and then as a backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "Fonds 0.1 · descriptions are drafts until you approve them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun EngineOption(key: String, current: String, title: String, body: String, onPick: () -> Unit) {
    val selected = key == current
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onPick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingField(label: String, value: String, words: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(),
    )
}
