package com.brianrobinson.fonds.ai

object Prompts {

    val TYPES = listOf(
        "Letter", "Memorandum", "Telegram", "Report", "Minutes", "Speech", "Program",
        "Clipping", "Photograph", "Financial record", "Form", "Notes", "Publication",
        "Legal document", "Other",
    )

    private val ITEM_SCHEMA = """
Return ONLY a JSON object with these keys:
"title": short devised title in archival style, e.g. "Letter from Mary Smith to the Board of Trustees" or "Minutes of the Executive Committee",
"date": the document's own date in DACS form, e.g. "1952 January 5", "1952 January", "1952", "circa 1950", or "undated",
"dateSort": the same date as YYYY-MM-DD, YYYY-MM or YYYY, or "" when undated,
"type": one of ${TYPES.joinToString(", ")},
"creator": author or issuing body,
"recipient": addressee, if any,
"summary": one or two plain factual sentences on what the document is about,
"people": up to 8 personal names mentioned,
"places": up to 5 places mentioned,
"organizations": up to 5 organizations mentioned,
"subjects": 1 to 4 topical subject terms
""".trim()

    private const val RULES =
        "Use only what the document itself shows. Never invent names, dates or facts. " +
            "Leave a field empty (\"\" or []) when the document does not show it."

    /** One line telling the model where the item sits (collection + box/folder, or where a single item came from). */
    fun context(collection: String, box: Int, folder: Int, folderLabel: String): String =
        "Collection: $collection. Location: Box $box, Folder $folder" +
            (if (folderLabel.isNotBlank()) " (\"$folderLabel\")" else "") + "."

    fun singleContext(repository: String, collection: String, location: String): String {
        val from = listOf(location, collection, repository).filter { it.isNotBlank() }.joinToString(", ")
        return if (from.isBlank()) "This is a single item, not yet part of a collection." else "This is a single item from $from."
    }

    fun item(context: String, text: String): String = """
You are an archivist writing item-level description for a finding aid.
$context
$RULES

$ITEM_SCHEMA

Document text (machine-read, may contain recognition errors):
<<<
$text
>>>
""".trim()

    fun itemFromImagesOnly(context: String): String = """
You are an archivist writing item-level description for a finding aid.
$context
The attached image is the document. $RULES

$ITEM_SCHEMA
""".trim()

    fun itemWithTranscription(context: String, pageCount: Int, ocrHint: String): String = """
You are an archivist transcribing and describing one archival item for a finding aid.
$context
The $pageCount attached image(s) are the pages of the item, in order.
First transcribe every page exactly as written: keep original spelling, capitalization, punctuation and line breaks. Mark unreadable words as [illegible]. Do not correct or modernize.
Then describe the item. $RULES

$ITEM_SCHEMA,
"pages": array of strings, one full transcription per page, in order
${if (ocrHint.isNotBlank()) "\nAn on-device OCR pass produced this rough text, which may help with typed portions:\n<<<\n$ocrHint\n>>>" else ""}
""".trim()

    fun folder(collection: String, box: Int, folder: Int, label: String, items: String): String = """
You are an archivist writing file-level (folder) description for a finding aid.
Collection: $collection. Box $box, Folder $folder${if (label.isNotBlank()) ", folder tab reads \"$label\"" else ""}.
Below are the items in this folder, in their original order.
Use only this information. Never invent.

Return ONLY a JSON object:
"title": a concise devised folder title in archival style, without dates, e.g. "Correspondence with alumni" or "Building committee",
"scopeNote": one to three sentences on what the folder contains: kinds of documents, main correspondents, topics

Items:
$items
""".trim()

    fun series(collection: String, folders: String): String = """
You are an archivist proposing an intellectual arrangement (series) for a collection.
Collection: $collection.
Folders are listed in physical order as [id] Box/Folder: title — scope.
Group folders into 2 to 8 series by function or document type (for example Correspondence; Minutes and reports; Financial records; Publications; Photographs; Subject files).
Every folder id must appear in exactly one series. Do not rename folders.

Return ONLY a JSON array of objects:
[{"title": "Correspondence", "scopeNote": "one sentence", "folders": [12, 13, 20]}]

Folders:
$folders
""".trim()

    fun collection(title: String, creator: String, dates: String, extent: String, folders: String): String = """
You are an archivist drafting collection-level description for a finding aid (DACS).
Collection: $title${if (creator.isNotBlank()) ". Creator: $creator" else ""}${if (dates.isNotBlank()) ". Dates: $dates" else ""}${if (extent.isNotBlank()) ". Extent: $extent" else ""}.
Below are the folders, in physical order, with their descriptions.
Use only this information. Never invent biographical facts.

Return ONLY a JSON object:
"abstractText": two or three sentences summarizing the collection for a catalog record,
"scopeContent": one paragraph describing the kinds of materials, main correspondents, subjects, activities and date coverage

Folders:
$folders
""".trim()
}
