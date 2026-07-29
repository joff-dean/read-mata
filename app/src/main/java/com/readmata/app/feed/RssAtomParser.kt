package com.readmata.app.feed

import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Parses RSS 2.0 and Atom without performing network access. */
object RssAtomParser {
    private val doctype = Regex("""<!DOCTYPE\b""", RegexOption.IGNORE_CASE)

    fun parse(xml: String): List<FeedItem> {
        require(xml.isNotBlank()) { "Feed XML must not be blank" }
        require(xml.length <= MAX_XML_CHARACTERS) { "Feed XML is too large" }
        require(!doctype.containsMatchIn(xml)) { "DOCTYPE declarations are not allowed" }

        val document = try {
            secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .apply {
                    // This resolver is a final no-network fallback in addition to the JAXP flags.
                    setEntityResolver { _, _ -> InputSource(StringReader("")) }
                }
                .parse(InputSource(StringReader(xml)))
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid or unsafe feed XML", error)
        }

        val root = document.documentElement ?: return emptyList()
        return when (root.elementName().lowercase(Locale.ROOT)) {
            "rss" -> parseRss(root)
            "feed" -> parseAtom(root)
            else -> emptyList()
        }
    }

    private fun parseRss(root: Element): List<FeedItem> {
        val channel = root.childElements("channel").firstOrNull() ?: return emptyList()
        return channel.childElements("item").take(MAX_ITEMS).mapIndexed { index, item ->
            val title = HtmlText.clean(item.firstText("title")).limitedTo(MAX_TITLE_CHARACTERS)
            val link = item.feedLink().limitedTo(MAX_METADATA_CHARACTERS)
            val summary = HtmlText.clean(
                item.firstNonBlankText("encoded", "content", "description", "summary"),
            ).limitedTo(MAX_SUMMARY_CHARACTERS)
            val id = item.firstNonBlankText("guid")?.trim()
                ?: link.ifBlank { title }.ifBlank { "rss-$index" }

            FeedItem(
                id = id.limitedTo(MAX_METADATA_CHARACTERS),
                title = title,
                link = link,
                summary = summary,
                publishedAt = item.firstNonBlankText("pubDate", "published", "updated")
                    ?.trim()
                    ?.limitedTo(MAX_DATE_CHARACTERS),
            )
        }
    }

    private fun parseAtom(root: Element): List<FeedItem> =
        root.childElements("entry").take(MAX_ITEMS).mapIndexed { index, entry ->
            val title = HtmlText.clean(entry.firstText("title")).limitedTo(MAX_TITLE_CHARACTERS)
            val link = entry.feedLink().limitedTo(MAX_METADATA_CHARACTERS)
            val summary = HtmlText.clean(
                entry.firstNonBlankText("content", "summary", "description"),
            ).limitedTo(MAX_SUMMARY_CHARACTERS)
            val id = entry.firstNonBlankText("id")?.trim()
                ?: link.ifBlank { title }.ifBlank { "atom-$index" }

            FeedItem(
                id = id.limitedTo(MAX_METADATA_CHARACTERS),
                title = title,
                link = link,
                summary = summary,
                publishedAt = entry.firstNonBlankText("published", "updated", "pubDate")
                    ?.trim()
                    ?.limitedTo(MAX_DATE_CHARACTERS),
            )
        }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false

            // Android's JAXP implementation varies by API level. Every supported flag is
            // enabled, while the explicit DOCTYPE rejection above remains the fail-safe.
            runCatching { isXIncludeAware = false }
            setFeatureWhenSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureWhenSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureWhenSupported("http://xml.org/sax/features/external-general-entities", false)
            setFeatureWhenSupported("http://xml.org/sax/features/external-parameter-entities", false)
            setFeatureWhenSupported(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false,
            )

            // Android's XMLConstants stub does not expose these two JAXP constants on
            // every supported API level, so use their standard property URIs directly.
            runCatching {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            }
            runCatching {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
        }

    private fun DocumentBuilderFactory.setFeatureWhenSupported(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun Element.feedLink(): String {
        val links = childElements("link")
        val preferred = links.firstOrNull {
            val rel = it.attributeByName("rel")
            rel.isNullOrBlank() || rel.equals("alternate", ignoreCase = true)
        } ?: links.firstOrNull()

        return preferred?.attributeByName("href")?.trim()?.takeIf { it.isNotEmpty() }
            ?: preferred?.textContent?.trim().orEmpty()
    }

    private fun Element.firstText(name: String): String? =
        childElements(name).firstOrNull()?.textContent

    private fun Element.firstNonBlankText(vararg names: String): String? = names
        .asSequence()
        .flatMap { childElements(it).asSequence() }
        .map { it.textContent }
        .firstOrNull { it.isNotBlank() }

    private fun Element.childElements(name: String): List<Element> = buildList {
        var child: Node? = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.elementName().equals(name, ignoreCase = true)) add(element)
            }
            child = child.nextSibling
        }
    }

    private fun Element.attributeByName(name: String): String? {
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val attributeName = attribute.localName ?: attribute.nodeName.substringAfter(':')
            if (attributeName.equals(name, ignoreCase = true)) return attribute.nodeValue
        }
        return null
    }

    private fun Element.elementName(): String = localName ?: tagName.substringAfter(':')

    private fun String.limitedTo(maxCharacters: Int): String {
        if (length <= maxCharacters) return this

        val safeEnd = if (
            Character.isHighSurrogate(this[maxCharacters - 1]) &&
            Character.isLowSurrogate(this[maxCharacters])
        ) {
            maxCharacters - 1
        } else {
            maxCharacters
        }
        return substring(0, safeEnd).trimEnd()
    }

    private const val MAX_ITEMS = 20
    private const val MAX_TITLE_CHARACTERS = 500
    private const val MAX_SUMMARY_CHARACTERS = 20_000
    private const val MAX_METADATA_CHARACTERS = 2_048
    private const val MAX_DATE_CHARACTERS = 256
    private const val MAX_XML_CHARACTERS = 2_100_000
}
