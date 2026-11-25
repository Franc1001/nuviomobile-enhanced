package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock

/**
 * Picks which of an Xtream account's servers (the main one plus up to four backups) is used, and
 * moves to the next one when a server stops answering or a stream fails to play.
 *
 * The server that answered last stays the active one, so later requests and channels go straight to
 * it instead of waiting on a dead server first.
 */
internal object LiveTvXtreamFailover {
    private val log = Logger.withTag("LiveTvXtreamFailover")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val probeHeaders = mapOf("Accept" to "application/json, text/plain, */*", "User-Agent" to "Nuvio/1.0")

    private const val PROBE_TIMEOUT_MS = 8_000L
    private const val RESOLVE_CACHE_MS = 2 * 60_000L

    private var activeServer: String? = null
    private var lastResolvedAtMs: Long = 0L

    // Servers already tried for the stream that is playing now.
    private var currentStreamPath: String? = null
    private val triedServers = mutableSetOf<String>()

    fun reset() {
        activeServer = null
        lastResolvedAtMs = 0L
        currentStreamPath = null
        triedServers.clear()
    }

    /** The server to use now: the one that answered last, or the main server. */
    fun activeServerUrl(settings: LiveTvXtreamSettings): String {
        val servers = settings.allServerUrls
        return activeServer?.takeIf { it in servers } ?: servers.firstOrNull().orEmpty()
    }

    /**
     * Settings pointing at a server that answers, trying the active one first and then the others in
     * order. When none answers, the active server is kept so the caller reports its error.
     */
    suspend fun resolve(settings: LiveTvXtreamSettings): LiveTvXtreamSettings {
        val servers = settings.allServerUrls
        val current = activeServerUrl(settings)
        val now = Clock.System.now().toEpochMilliseconds()
        if (servers.size <= 1 || (now - lastResolvedAtMs < RESOLVE_CACHE_MS && activeServer in servers)) {
            return settings.onServer(current)
        }
        val ordered = listOf(current) + servers.filter { it != current }
        for (server in ordered) {
            if (probe(settings.onServer(server))) {
                if (server != current) log.i { "Xtream server switched to ${redact(server)}" }
                activeServer = server
                lastResolvedAtMs = now
                return settings.onServer(server)
            }
            log.w { "Xtream server not answering: ${redact(server)}" }
        }
        return settings.onServer(current)
    }

    /** A new channel is starting: it gets a fresh set of retries. */
    fun startPlayback(@Suppress("UNUSED_PARAMETER") channelId: String) {
        currentStreamPath = null
        triedServers.clear()
    }

    /** [url] moved onto the active server, when it is a stream of one of the account's servers. */
    fun rewriteToActiveServer(url: String, settings: LiveTvXtreamSettings): String {
        val (server, path) = splitServer(url, settings) ?: return url
        val active = activeServerUrl(settings)
        return if (server == active) url else active + path
    }

    /**
     * After [failedUrl] failed to play: the same stream on the next server not yet tried for it, which
     * also becomes the active server. Null when every server has been tried.
     */
    fun nextUrlAfterFailure(failedUrl: String, settings: LiveTvXtreamSettings): String? {
        val servers = settings.allServerUrls
        if (servers.size <= 1) return null
        val (failedServer, path) = splitServer(failedUrl, settings) ?: return null
        if (path != currentStreamPath) {
            currentStreamPath = path
            triedServers.clear()
        }
        triedServers += failedServer
        val start = servers.indexOf(failedServer)
        val next = (1 until servers.size)
            .map { offset -> servers[(start + offset) % servers.size] }
            .firstOrNull { it !in triedServers }
            ?: return null
        log.i { "Xtream stream failed on ${redact(failedServer)}, trying ${redact(next)}" }
        activeServer = next
        lastResolvedAtMs = Clock.System.now().toEpochMilliseconds()
        return next + path
    }

    private fun splitServer(url: String, settings: LiveTvXtreamSettings): Pair<String, String>? {
        // Longest first, so a server whose address is a prefix of another's does not match its streams.
        val server = settings.allServerUrls
            .sortedByDescending { it.length }
            .firstOrNull { url.startsWith("$it/") }
            ?: return null
        return server to url.removePrefix(server)
    }

    private suspend fun probe(settings: LiveTvXtreamSettings): Boolean = try {
        val url = "${settings.serverUrl}/player_api.php?username=${settings.username.encodeURLParameter()}" +
            "&password=${settings.password.encodeURLParameter()}"
        val body = withTimeoutOrNull(PROBE_TIMEOUT_MS) { httpGetTextWithHeaders(url, probeHeaders) }
        val userInfo = body?.let { (json.parseToJsonElement(it) as? JsonObject)?.get("user_info") as? JsonObject }
        val auth = (userInfo?.get("auth") as? JsonPrimitive)?.contentOrNull
        userInfo != null && auth != "0"
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        false
    }

    private fun LiveTvXtreamSettings.onServer(server: String) = copy(
        serverUrl = server,
        username = username.trim(),
        password = password.trim(),
    )

    private fun redact(server: String) = server.substringAfter("://").substringBefore('/')
}

internal fun LiveTvChannel.isXtreamChannel(): Boolean = id.startsWith("xtream:")
