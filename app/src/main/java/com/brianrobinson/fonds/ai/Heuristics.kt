package com.brianrobinson.fonds.ai

/**
 * No-AI fallback so the app is still useful with every engine off or unavailable:
 * finds a date, guesses the document type and, for letters, the writer and addressee.
 */
object Heuristics {

    private val MONTHS = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val MONTH_ALT = MONTHS.joinToString("|") { m ->
        val short = m.take(3)
        if (m.length > 3) "$m|$short\\.?" else m
    } + "|sept\\.?"

    private val MDY = Regex("""\b($MONTH_ALT)\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(1[6-9]\d\d|20[0-2]\d)\b""", RegexOption.IGNORE_CASE)
    private val DMY = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTH_ALT),?\s+(1[6-9]\d\d|20[0-2]\d)\b""", RegexOption.IGNORE_CASE)
    private val MY = Regex("""\b($MONTH_ALT),?\s+(1[6-9]\d\d|20[0-2]\d)\b""", RegexOption.IGNORE_CASE)
    private val NUMERIC = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})\b""")
    private val YEAR = Regex("""\b(1[6-9]\d\d|20[0-2]\d)\b""")

    data class FoundDate(val dacs: String, val sort: String)

    private fun monthIndex(token: String): Int {
        val t = token.lowercase().trimEnd('.')
        if (t == "sept") return 9
        return MONTHS.indexOfFirst { it.startsWith(t.take(3)) } + 1
    }

    private fun build(year: Int, month: Int?, day: Int?): FoundDate {
        val monthName = month?.let { MONTHS[it - 1].replaceFirstChar { c -> c.uppercase() } }
        val dacs = listOfNotNull("$year", monthName, day?.toString()).joinToString(" ")
        val sort = buildString {
            append(year)
            if (month != null) append("-%02d".format(month))
            if (month != null && day != null) append("-%02d".format(day))
        }
        return FoundDate(dacs, sort)
    }

    fun findDate(text: String): FoundDate? {
        val head = text.take(1500)
        MDY.find(head)?.let { m ->
            return build(m.groupValues[3].toInt(), monthIndex(m.groupValues[1]), m.groupValues[2].toInt().takeIf { it in 1..31 })
        }
        DMY.find(head)?.let { m ->
            return build(m.groupValues[3].toInt(), monthIndex(m.groupValues[2]), m.groupValues[1].toInt().takeIf { it in 1..31 })
        }
        NUMERIC.find(head)?.let { m ->
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 1900
            if (mo in 1..12 && d in 1..31) return build(y, mo, d)
        }
        MY.find(head)?.let { m -> return build(m.groupValues[2].toInt(), monthIndex(m.groupValues[1]), null) }
        YEAR.find(head)?.let { m -> return build(m.groupValues[1].toInt(), null, null) }
        return null
    }

    fun guessType(text: String): String {
        val t = text.take(3000)
        val upper = t.uppercase()
        return when {
            "WESTERN UNION" in upper || "TELEGRAM" in upper -> "Telegram"
            Regex("""\bMEMO(RANDUM)?\b""").containsMatchIn(upper) -> "Memorandum"
            Regex("""\bMINUTES\b""").containsMatchIn(upper) -> "Minutes"
            Regex("""(?m)^\s*(Dear|My dear)\b""").containsMatchIn(t) ||
                Regex("""(?i)\b(sincerely|yours truly|cordially|respectfully yours|faithfully)\b""").containsMatchIn(t) -> "Letter"
            Regex("""\bPROGRAM(ME)?\b""").containsMatchIn(upper) -> "Program"
            Regex("""\bREPORT\b""").containsMatchIn(upper) -> "Report"
            Regex("""\b(INVOICE|RECEIPT|LEDGER|STATEMENT OF ACCOUNT)\b""").containsMatchIn(upper) -> "Financial record"
            else -> ""
        }
    }

    private fun recipient(text: String): String {
        val m = Regex("""(?m)^\s*(?:My\s+)?[Dd]ear\s+([^,:\n]{2,60})[,:]""").find(text) ?: return ""
        return m.groupValues[1].trim()
    }

    private fun signer(text: String): String {
        val lines = text.lines().map { it.trim() }
        val i = lines.indexOfLast {
            Regex("""(?i)^(sincerely|very truly yours|yours truly|cordially|respectfully|faithfully|yours)\b""").containsMatchIn(it)
        }
        if (i < 0) return ""
        return lines.drop(i + 1).firstOrNull { line ->
            line.isNotEmpty() && line.split(" ").size in 1..6 && line.any { it.isUpperCase() } && !line.contains("@")
        }.orEmpty()
    }

    private fun firstLine(text: String): String =
        text.lines().map { it.trim() }.firstOrNull { line -> line.count { it.isLetter() } >= 6 }
            ?.take(90).orEmpty()

    fun describe(text: String): ItemDraft {
        val date = findDate(text)
        val type = guessType(text)
        val to = if (type == "Letter") recipient(text) else ""
        val from = if (type == "Letter") signer(text) else ""
        val title = when {
            type == "Letter" && from.isNotBlank() && to.isNotBlank() -> "Letter from $from to $to"
            type == "Letter" && from.isNotBlank() -> "Letter from $from"
            type == "Letter" && to.isNotBlank() -> "Letter to $to"
            type.isNotBlank() -> type
            else -> firstLine(text)
        }
        return ItemDraft(
            title = title,
            date = date?.dacs ?: if (text.isNotBlank()) "undated" else "",
            dateSort = date?.sort.orEmpty(),
            docType = type,
            creator = from,
            recipient = to,
        )
    }
}
