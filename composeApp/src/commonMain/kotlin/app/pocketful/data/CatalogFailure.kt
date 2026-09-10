package app.pocketful.data

import io.ktor.client.plugins.HttpRequestTimeoutException

/**
 * Why a catalog request failed, as one sentence about what to do next.
 *
 * Six outcomes, because they want six different reactions and merging them throws away the
 * only thing the reader has to go on. The catalog being down is worth waiting out; being
 * rate-limited is worth slowing down; being offline is worth checking the wifi; and an
 * answer this app could not read is none of those -- it means the app needs updating, and
 * "try again in a moment" is advice that will never once work for it.
 *
 * This lives here rather than beside one screen because both of the places that ask the
 * catalog anything need it, and only one of them used to have it. [CardLookup] classified
 * its failures properly while [CatalogBrowser] caught everything in a bare `onFailure` and
 * told the user to check a connection that was fine -- so the browse tab said "check your
 * connection" through an outage that had nothing to do with the user's network. One
 * function means the next screen to call the catalog cannot regress that by omission.
 *
 * The first two cases are decided on a type rather than by reading the exception's
 * message. Substring-matching English out of whatever the platform's socket layer happened
 * to say is a guess even when it is right, and it puts every failure the guess misses into
 * the same reassuring sentence. Only [looksOffline] still guesses, because the exceptions
 * for it are platform types that common code cannot name.
 *
 * @param subject what was being asked for, for the two messages that name it --
 *   "that search", "the set index". Reads as the object of "refused ___".
 */
fun catalogFailureMessage(cause: Throwable, subject: String): String = when {
    cause is CatalogUnavailable && cause.status == TOO_MANY_REQUESTS ->
        "The card catalog is asking for fewer requests at once. Try again in a moment."

    cause is CatalogUnavailable && cause.status >= SERVER_ERROR ->
        "The card catalog is down right now -- nothing to do with your collection, " +
            "which is safe on this device. Try again in a few minutes."

    cause is CatalogUnavailable ->
        "The card catalog refused $subject (${cause.status})."

    cause is HttpRequestTimeoutException ->
        "The card catalog took too long to answer. Try again in a moment."

    looksOffline(cause) ->
        "Could not reach the card catalog. Check your connection and try again."

    else ->
        "The card catalog sent back something this app could not read. This one is " +
            "worth reporting -- $subject may need an app update to work again."
}

/**
 * A guess, and the only one left.
 *
 * There is no common-code type for "the socket could not be opened": it is a `java.net`
 * exception on Android and something else everywhere else. So this reads the message,
 * which is what the whole of [catalogFailureMessage] used to do.
 */
private fun looksOffline(cause: Throwable): Boolean {
    val text = (cause.message ?: "") + " " + (cause.cause?.message ?: "")
    return listOf("resolve", "connect", "unreachable", "network", "host")
        .any { text.contains(it, ignoreCase = true) }
}

private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR = 500
