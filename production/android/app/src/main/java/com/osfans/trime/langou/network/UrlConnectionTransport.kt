package com.osfans.trime.langou.network

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UrlConnectionTransport(
    baseUrl: String,
) : LangouTransport {
    private val baseUrl = baseUrl.trimEnd('/')

    init {
        require(this.baseUrl.startsWith("https://")) {
            "Langou API must use HTTPS"
        }
    }

    override suspend fun requestJson(
        method: String,
        path: String,
        body: String?,
        bearerToken: String?,
    ): String =
        withConnection(method, path, body, bearerToken, "application/json") { connection ->
            connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        }

    override suspend fun postSse(
        path: String,
        body: String,
        bearerToken: String,
        onEvent: (SseEvent) -> Unit,
    ) {
        withConnection("POST", path, body, bearerToken, "text/event-stream") { connection ->
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var eventName = "message"
                val data = StringBuilder()

                fun dispatch() {
                    if (data.isNotEmpty()) {
                        onEvent(SseEvent(eventName, data.toString()))
                    }
                    eventName = "message"
                    data.clear()
                }

                reader.forEachLine { line ->
                    when {
                        line.isEmpty() -> dispatch()
                        line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.substringAfter(':').trimStart())
                        }
                    }
                }
                dispatch()
            }
        }
    }

    private suspend fun <T> withConnection(
        method: String,
        path: String,
        body: String?,
        bearerToken: String?,
        accept: String,
        block: (HttpURLConnection) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val connection =
                (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = body != null
                    setRequestProperty("Accept", accept)
                    if (body != null) {
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    if (bearerToken != null) {
                        setRequestProperty("Authorization", "Bearer $bearerToken")
                    }
                }
            try {
                if (body != null) {
                    connection.outputStream.use { stream ->
                        stream.write(body.toByteArray(Charsets.UTF_8))
                    }
                }
                if (connection.responseCode !in 200..299) {
                    val errorBody =
                        connection.errorStream
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use(BufferedReader::readText)
                            .orEmpty()
                    throw LangouApiException(
                        code = "http_${connection.responseCode}",
                        message = errorBody.take(MAX_ERROR_LENGTH).ifBlank { "网络请求失败" },
                    )
                }
                if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    @Suppress("UNCHECKED_CAST")
                    "" as T
                } else {
                    block(connection)
                }
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_ERROR_LENGTH = 512
    }
}
