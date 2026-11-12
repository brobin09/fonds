package com.brianrobinson.fonds.ai

import com.brianrobinson.fonds.data.DocStatus
import com.brianrobinson.fonds.data.DocumentContext
import com.brianrobinson.fonds.data.DocumentEntity
import com.brianrobinson.fonds.data.Engine
import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.ImageStore
import com.brianrobinson.fonds.data.PageEntity
import com.brianrobinson.fonds.data.Proposals
import com.brianrobinson.fonds.data.Repository
import com.brianrobinson.fonds.data.Settings
import com.brianrobinson.fonds.data.SettingsState
import com.brianrobinson.fonds.data.field
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The processing queue. Each scanned item moves through:
 *   queued → reading (on-device OCR) → read → describing (AI draft) → drafted → (you review) → done
 * Runs one item at a time in the app process. Gemini Nano only runs while Fonds is on screen.
 */
class Pipeline(
    private val repo: Repository,
    private val settings: Settings,
    private val ocr: OcrEngine,
    val nano: NanoEngine,
    val claude: ClaudeEngine,
) {
    private val dao = repo.dao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val kick = Channel<Unit>(Channel.CONFLATED)
    private val forced = ConcurrentHashMap<Long, String>()

    private val foreground = MutableStateFlow(false)

    private val _activity = MutableStateFlow<String?>(null)
    /** Human-readable "what's happening now", shown in the app's status strip. */
    val activity: StateFlow<String?> = _activity.asStateFlow()

    private val _waitingForForeground = MutableStateFlow(false)
    val waitingForForeground: StateFlow<Boolean> = _waitingForForeground.asStateFlow()

    fun start() {
        scope.launch {
            dao.resetReading()
            dao.resetDescribing()
            runCatching { nano.refresh() }
            loop()
        }
    }

    fun kick() {
        kick.trySend(Unit)
    }

    fun setForeground(value: Boolean) {
        foreground.value = value
        if (value) {
            scope.launch { runCatching { nano.refresh() } }
            kick()
        }
    }

    /** Re-describe one item with a specific engine (e.g. "Ask Claude" on a hard page). */
    fun describeWith(docId: Long, engine: String) {
        forced[docId] = engine
        scope.launch {
            repo.requeueDescription(docId)
            kick()
        }
    }

    private suspend fun loop() {
        while (true) {
            val doc = dao.nextPending()
            if (doc == null) {
                _activity.value = null
                kick.receive()
                continue
            }
            try {
                when (doc.status) {
                    DocStatus.QUEUED -> readText(doc)
                    DocStatus.READ -> describe(doc)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                dao.setStatus(doc.id, DocStatus.ERROR, t.message ?: t.javaClass.simpleName, now())
            }
        }
    }

    private fun now() = System.currentTimeMillis()

    private fun where(ctx: DocumentContext?): String =
        ctx?.let { "Box ${it.boxNumber} · Folder ${it.folderNumber}" }.orEmpty()

    private suspend fun readText(doc: DocumentEntity) {
        val ctx = dao.documentContextOnce(doc.id)
        dao.setStatus(doc.id, DocStatus.READING, "Reading text", now())
        val pages = dao.pages(doc.id)
        pages.forEachIndexed { i, p ->
            if (p.text.isBlank()) {
                _activity.value = "Reading text · ${where(ctx)} · page ${i + 1} of ${pages.size}"
                val text = ocr.read(p.imagePath)
                dao.updatePage(p.copy(text = text, textSource = "mlkit"))
            }
        }
        dao.setStatus(doc.id, DocStatus.READ, "", now())
    }

    private fun chooseEngine(s: SettingsState, doc: DocumentEntity): String = when (s.engine) {
        Engine.OFF -> Engine.OFF
        Engine.CLAUDE -> Engine.CLAUDE
        Engine.NANO -> Engine.NANO
        else -> when {
            doc.handwritten && s.claudeForHandwritten && s.hasClaude -> Engine.CLAUDE
            nano.isAvailable -> Engine.NANO
            s.claudeFallback && s.hasClaude -> Engine.CLAUDE
            else -> PATTERN
        }
    }

    private suspend fun describe(doc: DocumentEntity) {
        val s = settings.current
        val ctx = dao.documentContextOnce(doc.id) ?: return
        val pages = dao.pages(doc.id)
        val text = pages.joinToString("\n\n[page break]\n\n") { it.text }.trim()
        val wasForced = forced.containsKey(doc.id)
        val choice = forced.remove(doc.id) ?: chooseEngine(s, doc)

        if (choice == Engine.OFF) {
            dao.setStatus(doc.id, DocStatus.DONE, "", now())
            return
        }

        val label = when (choice) {
            Engine.CLAUDE -> "Asking Claude"
            Engine.NANO -> "Describing on device"
            else -> "Finding dates and names"
        }
        dao.setStatus(doc.id, DocStatus.DESCRIBING, label, now())
        _activity.value = "$label · ${where(ctx)}"

        var engineName: String
        val draft: ItemDraft = when (choice) {
            Engine.CLAUDE -> {
                engineName = "Claude (${modelLabel(s.claudeModel)})"
                describeWithClaude(s, ctx, doc, pages, text)
            }
            Engine.NANO -> {
                engineName = "Gemini Nano (on device)"
                try {
                    describeWithNano(ctx, pages, text)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    if (wasForced || s.engine == Engine.NANO) throw t
                    engineName = "Pattern match (on-device AI failed)"
                    Heuristics.describe(text)
                }
            }
            else -> {
                engineName = "Pattern match (no AI)"
                Heuristics.describe(text)
            }
        }

        draft.pageTexts?.let { texts -> applyTranscription(pages, texts) }

        val current = dao.getDocument(doc.id) ?: return
        val existing = Proposals.parse(current.proposals)
        val fresh = draft.toFieldMap().filter { (k, v) -> v.isNotBlank() && v != current.field(k) }
        val merged = existing + fresh
        val status = if (merged.keys.any { it in Fields.DOCUMENT_ORDER }) DocStatus.DRAFTED else DocStatus.DONE
        dao.updateDocument(
            current.copy(
                proposals = Proposals.encode(merged),
                status = status,
                statusDetail = "",
                engine = engineName,
                updatedAt = now(),
            ),
        )
    }

    private suspend fun applyTranscription(pages: List<PageEntity>, texts: List<String>) {
        pages.forEachIndexed { i, p ->
            val t = texts.getOrNull(i)?.trim().orEmpty()
            if (t.isNotEmpty() && p.textSource != "edited") dao.updatePage(p.copy(text = t, textSource = "claude"))
        }
    }

    private suspend fun awaitForeground() {
        if (!foreground.value) {
            _waitingForForeground.value = true
            _activity.value = "Paused: on-device AI runs while Fonds is open"
            foreground.first { it }
            _waitingForForeground.value = false
        }
    }

    private suspend fun describeWithNano(ctx: DocumentContext, pages: List<PageEntity>, text: String): ItemDraft {
        if (!nano.isAvailable) nano.refresh()
        if (!nano.isAvailable) throw IllegalStateException("Gemini Nano isn't ready on this phone yet. Check Settings.")
        awaitForeground()
        val clipped = text.take(NanoEngine.MAX_TEXT_CHARS)
        val raw = if (clipped.length < 120 && pages.isNotEmpty()) {
            val bmp = ImageStore.decodeScaled(pages.first().imagePath, 896)
            nano.generate(Prompts.itemFromImagesOnly(ctx.collectionTitle, ctx.boxNumber, ctx.folderNumber, ctx.folderLabel), bmp)
        } else {
            nano.generate(Prompts.item(ctx.collectionTitle, ctx.boxNumber, ctx.folderNumber, ctx.folderLabel, clipped))
        }
        return DraftParser.parseItem(raw)
            ?: throw IllegalStateException("The on-device model didn't return a usable description.")
    }

    private suspend fun describeWithClaude(
        s: SettingsState,
        ctx: DocumentContext,
        doc: DocumentEntity,
        pages: List<PageEntity>,
        text: String,
    ): ItemDraft {
        if (!s.hasClaude) throw ClaudeEngine.ClaudeException("Add a Claude API key in Settings to use Claude.")
        val transcribe = doc.handwritten || text.length < 80
        val images = pages.take(ClaudeEngine.MAX_IMAGES).map { it.imagePath }
        val raw = if (transcribe) {
            claude.generate(
                s.claudeKey,
                s.claudeModel,
                Prompts.itemWithTranscription(ctx.collectionTitle, ctx.boxNumber, ctx.folderNumber, ctx.folderLabel, images.size, text.take(4000)),
                images,
                maxTokens = (1500 + 1500 * images.size).coerceAtMost(16_000),
            )
        } else {
            claude.generate(
                s.claudeKey,
                s.claudeModel,
                Prompts.item(ctx.collectionTitle, ctx.boxNumber, ctx.folderNumber, ctx.folderLabel, text.take(60_000)),
                images.take(2),
                maxTokens = 1500,
            )
        }
        return DraftParser.parseItem(raw)
            ?: throw IllegalStateException("Claude's reply couldn't be read as a description.")
    }

    companion object {
        const val PATTERN = "pattern"

        fun modelLabel(id: String): String =
            ClaudeEngine.MODELS.firstOrNull { it.id == id }?.label ?: id
    }
}
