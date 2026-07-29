package com.readmata.app.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {
    @Test
    fun `removes markup unsafe blocks and normalizes entities`() {
        val html = """
            <div>Hello&nbsp;<strong>reader</strong>!</div>
            <script>doNotRead()</script>
            <p>Number &#35;1 &#x1F3A7; &amp; more.</p>
        """.trimIndent()

        assertEquals(
            "Hello reader! Number #1 🎧 & more.",
            HtmlText.clean(html),
        )
    }

    @Test
    fun `handles null blank and doubly escaped markup`() {
        assertEquals("", HtmlText.clean(null))
        assertEquals("", HtmlText.clean("  \n "))
        assertEquals("Hello", HtmlText.clean("&amp;lt;p&amp;gt;Hello&amp;lt;/p&amp;gt;"))
        assertEquals("2 < 3 and 5 > 4", HtmlText.clean("2 &lt; 3 and 5 &gt; 4"))
    }
}
