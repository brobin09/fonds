package com.brianrobinson.fonds.data

import org.json.JSONObject

/** Field keys shared by the AI layer, the review UI and the exporters. */
object Fields {
    const val TITLE = "title"
    const val DATE = "date"
    const val DATE_SORT = "dateSort"
    const val TYPE = "docType"
    const val CREATOR = "creator"
    const val RECIPIENT = "recipient"
    const val SUMMARY = "summary"
    const val PEOPLE = "people"
    const val PLACES = "places"
    const val ORGS = "organizations"
    const val SUBJECTS = "subjects"

    // folder / series / collection
    const val SCOPE = "scopeNote"
    const val DATE_RANGE = "dateRange"
    const val ABSTRACT = "abstractText"
    const val SCOPE_CONTENT = "scopeContent"
    const val ARRANGEMENT = "arrangement"
    const val EXTENT = "extent"

    val DOCUMENT_ORDER = listOf(TITLE, DATE, TYPE, CREATOR, RECIPIENT, SUMMARY, PEOPLE, PLACES, ORGS, SUBJECTS)

    fun label(key: String): String = when (key) {
        TITLE -> "Title"
        DATE -> "Date"
        DATE_SORT -> "Sortable date"
        TYPE -> "Type"
        CREATOR -> "Creator"
        RECIPIENT -> "Recipient"
        SUMMARY -> "Summary"
        PEOPLE -> "People"
        PLACES -> "Places"
        ORGS -> "Organizations"
        SUBJECTS -> "Subjects"
        SCOPE -> "Scope note"
        DATE_RANGE -> "Dates"
        ABSTRACT -> "Abstract"
        SCOPE_CONTENT -> "Scope and content"
        ARRANGEMENT -> "Arrangement"
        EXTENT -> "Extent"
        else -> key
    }
}

object Proposals {
    fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.optString(k)) } }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun encode(map: Map<String, String>): String {
        val clean = map.filterValues { it.isNotBlank() }
        if (clean.isEmpty()) return ""
        val o = JSONObject()
        clean.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun without(raw: String, vararg keys: String): String = encode(parse(raw) - keys.toSet())
}

/** Multi-value fields are stored as "a; b; c". */
object ListField {
    fun split(raw: String): List<String> =
        raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }

    fun join(items: List<String>): String =
        items.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("; ")

    val KEYS = setOf(Fields.PEOPLE, Fields.PLACES, Fields.ORGS, Fields.SUBJECTS)
}

fun DocumentEntity.field(key: String): String = when (key) {
    Fields.TITLE -> title
    Fields.DATE -> date
    Fields.DATE_SORT -> dateSort
    Fields.TYPE -> docType
    Fields.CREATOR -> creator
    Fields.RECIPIENT -> recipient
    Fields.SUMMARY -> summary
    Fields.PEOPLE -> people
    Fields.PLACES -> places
    Fields.ORGS -> organizations
    Fields.SUBJECTS -> subjects
    else -> ""
}

fun DocumentEntity.withField(key: String, value: String): DocumentEntity = when (key) {
    Fields.TITLE -> copy(title = value)
    Fields.DATE -> copy(date = value)
    Fields.DATE_SORT -> copy(dateSort = value)
    Fields.TYPE -> copy(docType = value)
    Fields.CREATOR -> copy(creator = value)
    Fields.RECIPIENT -> copy(recipient = value)
    Fields.SUMMARY -> copy(summary = value)
    Fields.PEOPLE -> copy(people = value)
    Fields.PLACES -> copy(places = value)
    Fields.ORGS -> copy(organizations = value)
    Fields.SUBJECTS -> copy(subjects = value)
    else -> this
}

/** The value shown in lists: approved first, then the AI draft, then a placeholder. */
fun DocumentEntity.displayTitle(): String =
    title.ifBlank { Proposals.parse(proposals)[Fields.TITLE].orEmpty() }.ifBlank { "Untitled item" }

fun DocumentEntity.displayDate(): String =
    date.ifBlank { Proposals.parse(proposals)[Fields.DATE].orEmpty() }

fun DocumentEntity.displayType(): String =
    docType.ifBlank { Proposals.parse(proposals)[Fields.TYPE].orEmpty() }

fun DocumentEntity.titleIsDraft(): Boolean = title.isBlank() && Proposals.parse(proposals).containsKey(Fields.TITLE)

fun FolderEntity.displayTitle(): String =
    title.ifBlank { label }.ifBlank { Proposals.parse(proposals)[Fields.TITLE].orEmpty() }

fun FolderEntity.titleIsDraft(): Boolean =
    title.isBlank() && label.isBlank() && Proposals.parse(proposals).containsKey(Fields.TITLE)
