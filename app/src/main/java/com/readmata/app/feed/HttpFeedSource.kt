package com.readmata.app.feed

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpFeedSource(
    private val parser: (String) -> List<FeedItem> = RssAtomParser::parse,
) : SourceAdapter {
    override suspend fun load(sourceUrl: String): List<FeedItem> = withContext(Dispatchers.IO) {
        val uri = validateUri(sourceUrl)
        val connection = uri.toURL().openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            connection.setRequestProperty("User-Agent", "ReadMata/0.1 (+Android RSS reader)")

            val status = connection.responseCode
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "안전하지 않은 주소로 이동되어 요청을 중단했습니다."
            }
            check(status in 200..299) { "피드 서버가 HTTP $status 응답을 보냈습니다." }
            check(connection.contentLengthLong < 0 || connection.contentLengthLong <= MAX_FEED_BYTES) {
                "피드가 허용 크기인 2MB를 넘었습니다."
            }

            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MAX_FEED_BYTES) { "피드가 허용 크기인 2MB를 넘었습니다." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }

            parser(decode(bytes, connection.contentType))
        } finally {
            connection.disconnect()
        }
    }

    private fun validateUri(value: String): URI {
        val uri = runCatching { URI(value.trim()) }
            .getOrElse { throw IllegalArgumentException("올바른 RSS 주소를 입력하세요.", it) }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "HTTPS RSS 주소만 사용할 수 있습니다."
        }
        return uri
    }

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val charsetName = contentType
            ?.split(';')
            ?.asSequence()
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim(' ', '"', '\'')

        val charset = charsetName
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: detectXmlCharset(bytes)
            ?: StandardCharsets.UTF_8
        return bytes.toString(charset)
    }

    private fun detectXmlCharset(bytes: ByteArray): Charset? {
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, 256))
            .toString(StandardCharsets.US_ASCII)
        val encoding = XML_ENCODING.find(prefix)?.groupValues?.get(1) ?: return null
        return runCatching { Charset.forName(encoding) }.getOrNull()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val MAX_FEED_BYTES = 2 * 1024 * 1024
        val XML_ENCODING = Regex("encoding\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]", RegexOption.IGNORE_CASE)
    }
}
