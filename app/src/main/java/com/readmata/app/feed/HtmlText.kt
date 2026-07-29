package com.readmata.app.feed

/** Small, dependency-free HTML-to-text normalizer for feed fields. */
object HtmlText {
    private val comments = Regex("""(?s)<!--.*?-->""")
    private val nonReadableBlocks =
        Regex("""(?is)<(?:script|style)\b[^>]*>.*?</(?:script|style)\s*>""")
    private val blockBreaks =
        Regex("""(?is)<\s*(?:br|/?(?:p|div|li|h[1-6]|tr|section|article))\b[^>]*>""")
    private val tags = Regex("""(?is)</?[A-Za-z][^>]*>|<\?.*?\?>|<![^>]*>""")
    private val whitespace = Regex("""\s+""")
    private val entities = Regex("""&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|[A-Za-z][A-Za-z0-9]+);""")

    private val namedEntities = mapOf(
        "amp" to "&",
        "apos" to "'",
        "gt" to ">",
        "lt" to "<",
        "nbsp" to " ",
        "quot" to "\"",
        "ndash" to "–",
        "mdash" to "—",
        "hellip" to "…",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "middot" to "·",
    )

    fun clean(html: String?): String {
        if (html.isNullOrBlank()) return ""

        // A second pass also handles values such as &amp;lt;p&amp;gt; from CDATA.
        val decoded = decodeEntities(decodeEntities(html))
        return decoded
            .replace(comments, " ")
            .replace(nonReadableBlocks, " ")
            .replace(blockBreaks, " ")
            // Inline tags do not create visible whitespace in HTML. Block tags were
            // converted to breaks above, so removing the remaining markup preserves
            // punctuation such as <strong>reader</strong>! correctly.
            .replace(tags, "")
            .replace('\u00a0', ' ')
            .replace(whitespace, " ")
            .trim()
    }

    private fun decodeEntities(value: String): String = entities.replace(value) { match ->
        val token = match.groupValues[1]
        when {
            token.startsWith("#x", ignoreCase = true) ->
                decodeCodePoint(token.drop(2), radix = 16) ?: match.value
            token.startsWith('#') ->
                decodeCodePoint(token.drop(1), radix = 10) ?: match.value
            else -> namedEntities[token.lowercase()] ?: match.value
        }
    }

    private fun decodeCodePoint(value: String, radix: Int): String? {
        val codePoint = value.toIntOrNull(radix) ?: return null
        if (!Character.isValidCodePoint(codePoint) || codePoint in 0xD800..0xDFFF) return null
        return String(Character.toChars(codePoint))
    }
}
