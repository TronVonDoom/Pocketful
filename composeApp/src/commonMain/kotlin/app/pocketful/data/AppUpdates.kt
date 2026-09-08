package app.pocketful.data

import app.pocketful.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where the app goes to find out whether a newer one exists.
 *
 * GitHub Releases rather than a store listing, because that is where this app is
 * published: a tag pushed to the repository builds an APK and attaches it to a release
 * (see `.github/workflows/release.yml`), and this reads the other end of that pipe.
 *
 * Deliberately unauthenticated. The repository is public, so the release endpoint answers
 * without a token, and the alternative -- shipping a token inside the APK -- would put a
 * credential on every device that installs it in exchange for nothing.
 */
class AppUpdates(
    private val client: HttpClient = defaultClient(),
    private val owner: String = OWNER,
    private val repo: String = REPO,
) {

    /**
     * The newest published release, or null if there is none or GitHub could not be asked.
     *
     * Null rather than an exception on failure: "could not check" and "nothing new" lead
     * to the same screen, and a settings row is not the place to explain an HTTP status.
     * The one case worth distinguishing is a release that exists but ships no installable
     * file, which [Release.asset] reports as a release with nothing to download.
     */
    suspend fun latest(): Release? {
        val response = runCatching {
            client.get("$API/repos/$owner/$repo/releases/latest") {
                // GitHub serves a different, older shape without this.
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        }.getOrNull() ?: return null

        if (!response.status.isSuccess()) return null
        val payload = runCatching { response.body<GitHubRelease>() }.getOrNull() ?: return null
        if (payload.draft) return null

        return Release(
            tag = payload.tagName,
            name = payload.name?.takeIf { it.isNotBlank() } ?: payload.tagName,
            notes = payload.body?.trim()?.takeIf { it.isNotEmpty() },
            url = payload.htmlUrl,
            prerelease = payload.prerelease,
            publishedAt = payload.publishedAt?.take(10),
            asset = payload.assets
                // A release can carry mapping files, checksums and source archives
                // alongside the build. Only one of them can be installed.
                .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.let { Asset(it.name, it.browserDownloadUrl, it.size) },
        )
    }

    companion object {
        const val OWNER = "TronVonDoom"
        const val REPO = "Pocketful"
        private const val API = "https://api.github.com"

        /** Where a person goes when the in-app path cannot help them. */
        val releasesUrl: String get() = "https://github.com/$OWNER/$REPO/releases"

        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false })
            }
        }
    }
}

/** A published release, reduced to what a settings row can act on. */
data class Release(
    val tag: String,
    val name: String,
    val notes: String?,
    val url: String,
    val prerelease: Boolean,
    val publishedAt: String?,
    val asset: Asset?,
) {
    /** The version this release claims to be, with the tag's `v` prefix taken off. */
    val version: String get() = tag.removePrefix("v").removePrefix("V")

    /**
     * [notes] with its markdown taken back out.
     *
     * Release notes are written for a web page, and the panel that shows them is one
     * `Text`. Rendering markdown there would mean shipping a parser to display four lines;
     * showing it raw means the user reads `**Settings -> Updates**` with the asterisks in.
     * Stripping the handful of marks GitHub's notes actually use is the proportionate
     * answer, and the "View on GitHub" button is there for anything richer.
     */
    val notesPlain: String?
        get() = notes
            ?.lines()
            ?.map { line ->
                line.trim()
                    .removePrefix(">").trimStart()
                    .trimStart('#').trimStart()
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .replaceFirst(Regex("^[-*+] "), "• ")
            }
            // Blank runs are what is left where a heading or a horizontal rule used to be,
            // and three of them in a five-line panel read as text that failed to load.
            ?.fold(mutableListOf<String>()) { kept, line ->
                if (line.isNotEmpty() || kept.lastOrNull()?.isNotEmpty() == true) kept.add(line)
                kept
            }
            ?.joinToString(separator = "\n")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Whether it is worth offering. A release with no APK is news, not an update. */
    val isNewerThanInstalled: Boolean get() = compareVersions(version, AppVersion.NAME) > 0

    val installable: Boolean get() = asset != null
}

/** The file on a release that can actually be installed. */
data class Asset(val name: String, val url: String, val size: Long) {
    /** Rounded to one decimal, which is as precise as a download estimate deserves. */
    val sizeLabel: String
        get() = when {
            size <= 0 -> "unknown size"
            size < 1_048_576 -> "${size / 1024} KB"
            else -> "${(size * 10 / 1_048_576) / 10.0} MB"
        }
}

/**
 * Orders two dotted version strings, ignoring anything after the numbers.
 *
 * Numeric segment by numeric segment rather than as strings, because "0.10.0" is newer
 * than "0.9.0" and sorts before it alphabetically -- which would mean the app stopped
 * offering updates the first time a minor version reached ten.
 *
 * A pre-release suffix loses a tie: `1.2.0-rc.1` is older than `1.2.0` and newer than
 * `1.1.9`. That is the only thing the suffix is read for; comparing two pre-releases of
 * the same version is a distinction this app has no way to have earned.
 */
internal fun compareVersions(left: String, right: String): Int {
    fun parts(value: String): List<Int> =
        value.substringBefore('-').split('.').map { segment ->
            segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0
        }

    val a = parts(left)
    val b = parts(right)
    for (i in 0 until maxOf(a.size, b.size)) {
        val difference = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
        if (difference != 0) return difference
    }

    val aPre = left.contains('-')
    val bPre = right.contains('-')
    return when {
        aPre == bPre -> 0
        aPre -> -1
        else -> 1
    }
}

// ------------------------------------------------------------------------ wire

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
)
