package com.nuvio.app.features.livetv

data class LiveTvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val streamType: String? = null,
    val stalkerCommand: String? = null,
    val epgId: String? = null,
)

data class LiveTvEpgProgram(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String? = null,
    val category: String? = null,
) {
    fun isAiringAt(timeMillis: Long): Boolean = timeMillis in startMillis until endMillis

    fun progressAt(timeMillis: Long): Float {
        val duration = endMillis - startMillis
        if (duration <= 0L) return 0f
        return ((timeMillis - startMillis).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}

data class LiveTvEpgSource(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
)

data class LiveTvStalkerSettings(
    val portalUrl: String = "",
    val macAddress: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = portalUrl.isNotBlank() && macAddress.isNotBlank()
}

data class LiveTvXtreamSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
    /** Extra server addresses for the same account, tried in order when the main one fails. */
    val backupServerUrls: List<String> = emptyList(),
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** The main server first, then the backups: normalized, without duplicates, at most [LIVE_TV_XTREAM_MAX_SERVERS]. */
    val allServerUrls: List<String>
        get() = (listOf(serverUrl) + backupServerUrls)
            .map(::normalizeXtreamServerUrl)
            .filter(String::isNotBlank)
            .distinct()
            .take(LIVE_TV_XTREAM_MAX_SERVERS)
}

const val LIVE_TV_XTREAM_MAX_SERVERS = 5

fun normalizeXtreamServerUrl(url: String): String =
    url.trim().trimEnd('/').substringBefore("/player_api.php").trimEnd('/')

enum class LiveTvPlaylistType {
    Url,
    LocalFile,
}

data class LiveTvPlaylist(
    val id: String,
    val name: String,
    val type: LiveTvPlaylistType,
    val source: String,
    val isEnabled: Boolean = true,
)

data class LiveTvUiState(
    val playlistUrl: String = "",
    val playlists: List<LiveTvPlaylist> = emptyList(),
    val stalkerSettings: LiveTvStalkerSettings = LiveTvStalkerSettings(),
    val xtreamSettings: LiveTvXtreamSettings = LiveTvXtreamSettings(),
    val channels: List<LiveTvChannel> = emptyList(),
    val favoriteChannelIds: Set<String> = emptySet(),
    val lastWatchedChannelId: String? = null,
    val recentChannelIds: List<String> = emptyList(),
    val channelViewMode: LiveTvChannelViewMode = LiveTvChannelViewMode.List,
    val categorySortMode: LiveTvCategorySortMode = LiveTvCategorySortMode.Alphabetical,
    /** Category names in the order the person arranged them, for [LiveTvCategorySortMode.Custom]. */
    val customCategoryOrder: List<String> = emptyList(),
    /** Whether the versions of one channel (HD, FHD, SD…) show as one entry in the sidebar layout. */
    val isVersionMergeEnabled: Boolean = true,
    /** The version chosen for each merged channel, by its version key. */
    val preferredVersionIds: Map<String, String> = emptyMap(),
    /** Whether the sidebar layout plays a muted preview of the highlighted channel. */
    val isPreviewEnabled: Boolean = true,
    /** Whether the sidebar layout uses translucent glass panels. */
    val isGlassEnabled: Boolean = true,
    val isNavigationEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val epgSources: List<LiveTvEpgSource> = emptyList(),
    val isEpgAutoDetectEnabled: Boolean = true,
    val epgPrograms: Map<String, List<LiveTvEpgProgram>> = emptyMap(),
    val isEpgLoading: Boolean = false,
    val epgErrorMessage: String? = null,
) {
    val hasPlaylist: Boolean
        get() = playlists.isNotEmpty() || playlistUrl.isNotBlank() ||
            stalkerSettings.isConfigured || xtreamSettings.isConfigured

    val showInNavigation: Boolean
        get() = hasPlaylist && isNavigationEnabled

    val isChannelGridEnabled: Boolean
        get() = channelViewMode == LiveTvChannelViewMode.Grid
}

enum class LiveTvChannelViewMode(val storageValue: String) {
    List("list"),
    Grid("grid"),
    Sidebar("sidebar"),
    ;

    companion object {
        fun fromStorage(value: String?): LiveTvChannelViewMode =
            entries.firstOrNull { it.storageValue == value } ?: List
    }
}
