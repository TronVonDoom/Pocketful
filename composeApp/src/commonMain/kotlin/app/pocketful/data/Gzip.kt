package app.pocketful.data

/**
 * Decompresses a gzip document into text.
 *
 * Exists because the published catalog is served as a `.gz` *file* rather than with a
 * `Content-Encoding: gzip` header, so no HTTP client unwraps it on the way in. That is
 * worth the twelve lines: the catalog is 6MB of JSON and 466KB compressed, and asking
 * every install to pull the uncompressed one over mobile data to save an expect/actual
 * would be a poor trade.
 *
 * Returns null rather than throwing on anything malformed. A corrupt download is a thing
 * to fall back from, not a thing to crash on.
 */
expect fun gunzipToText(bytes: ByteArray): String?
