package com.brianrobinson.fonds.ai

import com.brianrobinson.fonds.data.Fields
import com.brianrobinson.fonds.data.ListField
import org.json.JSONArray
import org.json.JSONObject

/** What an engine proposes for one item. Nothing here is saved until it is reviewed. */
data class ItemDraft(
    val title: String = "",
    val date: String = "",
    val dateSort: String = "",
    val docType: String = "",
    val creator: String = "",
    val recipient: String = "",
    val summary: String = "",
    val people: List<String> = emptyList(),
    val places: List<String> = emptyList(),
    val organizations: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    /** Only set when the engine transcribed the pages itself (Claude, handwriting). */
    val pageTexts: List<String>? = null,
) {
    fun toFieldMap(): Map<String, String> = buildMap {
        put(Fields.TITLE, title)
        put(Fields.DATE, date)
        put(Fields.DATE_SORT, dateSort)
        put(Fields.TYPE, docType)
        put(Fields.CREATOR, creator)
        put(Fields.RECIPIENT, recipient)
        put(Fields.SUMMARY, summary)
        put(Fields.PEOPLE, ListField.join(people))
        put(Fields.PLACES, ListField.join(places))
        put(Fields.ORGS, ListField.join(organizations))
        put(Fields.SUBJECTS, ListField.join(subjects))
    }.filterValues { it.isNotBlank() }
}

object DraftParser {

    /** Pull the first JSON object out of a model reply, tolerating code fences and chatter. */
    fun extractObject(raw: String): JSONObject? {
        val cleaned = raw.replace("```json", "```").replace("```", " ")
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    fun extractArray(raw: String): JSONArray? {
        val cleaned = raw.replace("```json", "```").replace("```", " ")
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return try {
            JSONArray(cleaned.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    fun parseItem(raw: String): ItemDraft? {
        val o = extractObject(raw) ?: return null
        return ItemDraft(
            title = o.str("title"),
            date = o.str("date").let { if (it.equals("unknown", true)) "" else it },
            dateSort = normalizeSort(o.str("dateSort")),
            docType = o.str("type").ifBlank { o.str("docType") },
            creator = o.str("creator"),
            recipient = o.str("recipient"),
            summary = o.str("summary"),
            people = o.list("people"),
            places = o.list("places"),
            organizations = o.list("organizations"),
            subjects = o.list("subjects"),
            pageTexts = if (o.has("pages")) o.list("pages", splitStrings = false) else null,
        )
    }

    private val PROSE = setOf("SUMMARY", "SCOPE")

    private val EMPTY = setOf("none", "n/a", "na", "unknown", "not shown", "not stated", "-", "--", "null", "nil", "[]")

    /**
     * Reads "KEY: value" lines (tolerating bullets, bold markers and different case).
     * A line without a key continues the previous value, so a wrapped summary isn't lost.
     */
    fun parseKeyed(raw: String, keys: Set<String>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var last: String? = null
        val keyAlt = keys.joinToString("|") { Regex.escape(it) }
        val line = Regex("""^[\s*\-•#>]*\**\s*($keyAlt)\s*\**\s*[:：]\s*(.*)$""", RegexOption.IGNORE_CASE)
        raw.replace("```", "").lines().forEach { l ->
            val m = line.find(l)
            if (m != null) {
                val key = keys.first { it.equals(m.groupValues[1], ignoreCase = true) }
                out[key] = clean(m.groupValues[2])
                last = key
            } else if (l.isNotBlank()) {
                // only prose wraps onto a second line; anything else after the answer is chatter
                last?.takeIf { it in PROSE }?.let { k -> out[k] = (out[k].orEmpty() + " " + clean(l)).trim() }
            }
        }
        return out.mapValues { (_, v) -> if (v.lowercase().trim('.', ' ') in EMPTY) "" else v }
    }

    private fun clean(v: String): String =
        v.trim().trim('*').trim().removeSurrounding("[", "]").removeSurrounding("\"").trim()

    private enum class Split { NAMES, PLACES, TERMS }

    /**
     * The prompts ask for semicolons. If a model uses commas anyway: places stay whole
     * ("Durham, North Carolina"), "Smith, John" stays one name, and other lists split.
     */
    private fun splitList(v: String, kind: Split): List<String> {
        if (v.isBlank()) return emptyList()
        val parts = when {
            ';' in v -> v.split(';')
            ',' !in v || kind == Split.PLACES -> listOf(v)
            kind == Split.NAMES -> {
                val c = v.split(',')
                if (c.size == 2 && c[0].trim().none { it == ' ' }) listOf(v) else c
            }
            else -> v.split(',')
        }
        return parts.map { it.trim().trim('.').trim() }.filter { it.isNotEmpty() && it.lowercase() !in EMPTY }
    }

    private val ITEM_KEYS = setOf("TITLE", "DATE", "SORT", "TYPE", "FROM", "TO", "SUMMARY", "PEOPLE", "PLACES", "ORGANIZATIONS", "SUBJECTS")

    /** Item description from labeled lines (Gemini Nano's format). Falls back to JSON. */
    fun parseItemLines(raw: String): ItemDraft? {
        val m = parseKeyed(raw, ITEM_KEYS)
        if (m.values.none { it.isNotBlank() }) return parseItem(raw)
        val date = m["DATE"].orEmpty()
        return ItemDraft(
            title = m["TITLE"].orEmpty(),
            date = date,
            dateSort = normalizeSort(m["SORT"].orEmpty()).ifBlank { com.brianrobinson.fonds.data.Dates.sortFromDacs(date) },
            docType = m["TYPE"].orEmpty().let { t -> Prompts.TYPES.firstOrNull { it.equals(t, ignoreCase = true) } ?: t },
            creator = m["FROM"].orEmpty(),
            recipient = m["TO"].orEmpty(),
            summary = m["SUMMARY"].orEmpty(),
            people = splitList(m["PEOPLE"].orEmpty(), Split.NAMES),
            places = splitList(m["PLACES"].orEmpty(), Split.PLACES),
            organizations = splitList(m["ORGANIZATIONS"].orEmpty(), Split.TERMS),
            subjects = splitList(m["SUBJECTS"].orEmpty(), Split.TERMS),
        )
    }

    /** Plain-text answers: drop a leading label like "Abstract:" and surrounding quotes. */
    fun plain(raw: String): String =
        raw.replace("```", "").trim()
            .replace(Regex("""^\**\s*(abstract|scope and content|scope|note)\s*\**\s*:\s*""", RegexOption.IGNORE_CASE), "")
            .trim().removeSurrounding("\"").trim()

    fun normalizeSort(s: String): String {
        val m = Regex("""^(\d{4})(?:-(\d{1,2}))?(?:-(\d{1,2}))?""").find(s.trim()) ?: return ""
        val y = m.groupValues[1]
        val mo = m.groupValues[2].takeIf { it.isNotEmpty() }?.padStart(2, '0')
        val d = m.groupValues[3].takeIf { it.isNotEmpty() }?.padStart(2, '0')
        return listOfNotNull(y, mo, d).joinToString("-")
    }

    fun JSONObject.str(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val v = opt(key)
        return when (v) {
            is String -> v.trim()
            is JSONArray -> (0 until v.length()).joinToString("; ") { v.optString(it) }
            else -> v?.toString()?.trim().orEmpty()
        }.let { if (it.equals("null", true) || it == "[]") "" else it }
    }

    fun JSONObject.list(key: String, splitStrings: Boolean = true): List<String> {
        if (!has(key) || isNull(key)) return emptyList()
        return when (val v = opt(key)) {
            is JSONArray -> (0 until v.length()).map { v.optString(it).trim() }.filter { it.isNotEmpty() && it != "null" }
            is String -> if (splitStrings) v.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() } else listOf(v)
            else -> emptyList()
        }
    }
}
