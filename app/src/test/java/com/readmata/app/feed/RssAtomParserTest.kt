package com.readmata.app.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RssAtomParserTest {
    @Test
    fun `limits parsed item count and oversized readable fields`() {
        val entries = (1..25).joinToString(separator = "") { index ->
            val title = if (index == 1) "T".repeat(600) else "Title $index"
            val summary = if (index == 1) "S".repeat(21_000) else "Summary $index"
            "<item><title>$title</title><description>$summary</description></item>"
        }
        val xml = "<rss><channel>$entries</channel></rss>"

        val items = RssAtomParser.parse(xml)

        assertEquals(20, items.size)
        assertEquals(500, items.first().title.length)
        assertEquals(20_000, items.first().summary.length)
    }

    @Test
    fun `parses RSS 2 and prefers namespaced full content`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Example feed</title>
                <item>
                  <guid isPermaLink="false">post-42</guid>
                  <title><![CDATA[ A <b>useful</b> story ]]></title>
                  <link>https://example.test/posts/42?a=1&amp;b=2</link>
                  <description><![CDATA[Short description]]></description>
                  <content:encoded><![CDATA[<p>Full&nbsp;story</p><p>Second line.</p>]]></content:encoded>
                  <pubDate>Wed, 15 Jul 2026 10:30:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val result = RssAtomParser.parse(xml)

        assertEquals(1, result.size)
        assertEquals("post-42", result.single().id)
        assertEquals("A useful story", result.single().title)
        assertEquals("https://example.test/posts/42?a=1&b=2", result.single().link)
        assertEquals("Full story Second line.", result.single().summary)
        assertEquals("Wed, 15 Jul 2026 10:30:00 GMT", result.single().publishedAt)
    }

    @Test
    fun `parses Atom default namespace link href and updated date`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example</title>
              <entry>
                <id>tag:example.test,2026:7</id>
                <title type="html">Atom &amp; entry</title>
                <link rel="self" href="https://api.example.test/entry/7" />
                <link rel="alternate" href="https://example.test/entry/7" />
                <summary type="html">Fallback summary</summary>
                <content type="html">&lt;p&gt;Main &lt;strong&gt;content&lt;/strong&gt;.&lt;/p&gt;</content>
                <updated>2026-07-15T10:30:00+09:00</updated>
              </entry>
            </feed>
        """.trimIndent()

        val item = RssAtomParser.parse(xml).single()

        assertEquals("tag:example.test,2026:7", item.id)
        assertEquals("Atom & entry", item.title)
        assertEquals("https://example.test/entry/7", item.link)
        assertEquals("Main content.", item.summary)
        assertEquals("2026-07-15T10:30:00+09:00", item.publishedAt)
    }

    @Test
    fun `rejects doctype and external entity input`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss version="2.0">
              <channel><item><title>&xxe;</title></item></channel>
            </rss>
        """.trimIndent()

        try {
            RssAtomParser.parse(xml)
            fail("DOCTYPE feed must be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("DOCTYPE"))
        }
    }
}
