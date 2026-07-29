package com.readmata.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    @Test
    fun `splits on sentence boundaries without exceeding maximum`() {
        val chunks = TextChunker.split(
            "첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다.",
            maxLength = 32,
        )

        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 32 })
        assertEquals(
            "첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다.",
            chunks.joinToString(" "),
        )
    }

    @Test
    fun `hard wraps a long unbroken value`() {
        val chunks = TextChunker.split("가".repeat(90), maxLength = 32)

        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= 32 })
    }

    @Test
    fun `returns empty list for blank input`() {
        assertTrue(TextChunker.split("   ", maxLength = 32).isEmpty())
    }
}
