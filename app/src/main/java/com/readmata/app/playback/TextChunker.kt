package com.readmata.app.playback

object TextChunker {
    private val sentenceBoundary = Regex("(?<=[.!?。！？])\\s+|\\n+")
    private val horizontalWhitespace = Regex("[\\t\\u000B\\f\\r ]+")

    fun split(text: String, maxLength: Int): List<String> {
        require(maxLength >= 32) { "maxLength must be at least 32" }

        val normalized = text
            .replace(horizontalWhitespace, " ")
            .trim()

        if (normalized.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                result += current.toString().trim()
                current.clear()
            }
        }

        normalized.split(sentenceBoundary)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { sentence ->
                if (sentence.length > maxLength) {
                    flushCurrent()
                    result += hardWrap(sentence, maxLength)
                } else if (current.isEmpty()) {
                    current.append(sentence)
                } else if (current.length + 1 + sentence.length <= maxLength) {
                    current.append(' ').append(sentence)
                } else {
                    flushCurrent()
                    current.append(sentence)
                }
            }

        flushCurrent()
        return result
    }

    private fun hardWrap(text: String, maxLength: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0

        while (start < text.length) {
            val maximumEnd = minOf(start + maxLength, text.length)
            if (maximumEnd == text.length) {
                chunks += text.substring(start).trim()
                break
            }

            val preferredMinimum = start + maxLength / 2
            val whitespace = text.lastIndexOf(' ', maximumEnd)
            val end = if (whitespace >= preferredMinimum) whitespace else maximumEnd
            chunks += text.substring(start, end).trim()
            start = end
            while (start < text.length && text[start].isWhitespace()) start++
        }

        return chunks.filter(String::isNotEmpty)
    }
}
