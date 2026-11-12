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
