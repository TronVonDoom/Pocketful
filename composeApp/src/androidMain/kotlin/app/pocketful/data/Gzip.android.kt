package app.pocketful.data

import java.util.zip.GZIPInputStream

actual fun gunzipToText(bytes: ByteArray): String? = runCatching {
    GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }
}.getOrNull()
