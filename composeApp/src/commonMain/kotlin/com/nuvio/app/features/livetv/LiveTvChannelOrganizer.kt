package com.nuvio.app.features.livetv

/** How the categories of the Live TV channel list are ordered. */
enum class LiveTvCategorySortMode(val storageValue: String) {
    /** As the playlist or provider lists them. */
    Playlist("playlist"),
    Alphabetical("az"),
    MostChannels("count"),
    /** The order the person arranged. */
    Custom("custom"),
    ;

    companion object {
        fun fromStorage(value: String?): LiveTvCategorySortMode =
            entries.firstOrNull { it.storageValue == value } ?: Alphabetical
    }
}

/**
 * One channel as the list shows it: the versions of the same channel that a playlist carries in
 * several qualities ("Sport 1 HD", "Sport 1 FHD", "Sport 1 SD") under one entry.
 */
internal data class LiveTvMergedChannel(
    val key: String,
    /** The version that plays: the one the person chose, otherwise the best quality. */
    val primary: LiveTvChannel,
    /** Every version, best quality first. One entry when the channel has no other versions. */
    val versions: List<LiveTvChannel>,
) {
    val hasVersions: Boolean get() = versions.size > 1
}

/** A version's quality, for ordering versions and for its badge. */
internal enum class LiveTvChannelQuality(val label: String?, val rank: Int) {
    Uhd("4K", 4),
    Fhd("FHD", 3),
    Hd("HD", 2),
    Sd("SD", 1),
    Unknown(null, 0),
}

private val uhdPattern = Regex("""(?i)(?<![\p{L}\p{N}])(4k|uhd|2160p|8k)(?![\p{L}\p{N}])""")
private val fhdPattern = Regex("""(?i)(?<![\p{L}\p{N}])(fhd|1080[pi]?|full\s*hd)(?![\p{L}\p{N}])""")
private val hdPattern = Regex("""(?i)(?<![\p{L}\p{N}])(hd|720p|hdr)(?![\p{L}\p{N}])|ᴴᴰ|ʰᵈ""")
private val sdPattern = Regex("""(?i)(?<![\p{L}\p{N}])(sd|576[pi]?|480[pi]?|360p|lq)(?![\p{L}\p{N}])""")

// Words that tell versions apart without being part of the channel's name.
private val versionNoisePattern = Regex(
    """(?i)(?<![\p{L}\p{N}])(4k|uhd|2160p|8k|fhd|1080[pi]?|full\s*hd|hd|720p|hdr|sd|576[pi]?|480[pi]?|360p|lq|hevc|h\.?26[45]|x26[45]|[0-9]{2,3}\s*fps|raw|backup|bkp)(?![\p{L}\p{N}])|ᴴᴰ|ʰᵈ|ᵁᴴᴰ|ᶠᴴᴰ""",
)
private val nonWordPattern = Regex("""[^\p{L}\p{N}]+""")

internal fun liveTvChannelQuality(name: String): LiveTvChannelQuality = when {
    uhdPattern.containsMatchIn(name) -> LiveTvChannelQuality.Uhd
    fhdPattern.containsMatchIn(name) -> LiveTvChannelQuality.Fhd
    hdPattern.containsMatchIn(name) -> LiveTvChannelQuality.Hd
    sdPattern.containsMatchIn(name) -> LiveTvChannelQuality.Sd
    else -> LiveTvChannelQuality.Unknown
}

/** The name without its quality and version words, as the versions of one channel share it. */
internal fun liveTvBaseChannelName(name: String): String =
    versionNoisePattern.replace(name, " ")
        .replace(nonWordPattern, " ")
        .trim()
        .lowercase()
        .ifBlank { name.trim().lowercase() }

/**
 * Groups the versions of each channel. Versions merge only inside one category, so a channel that a
 * playlist lists under two categories stays in both. Order follows each channel's first appearance.
 */
internal fun mergeLiveTvChannelVersions(
    channels: List<LiveTvChannel>,
    preferredVersionIds: Map<String, String>,
    keyOf: (LiveTvChannel) -> String = ::liveTvVersionKey,
): List<LiveTvMergedChannel> {
    val groups = LinkedHashMap<String, MutableList<LiveTvChannel>>()
    channels.forEach { channel ->
        groups.getOrPut(keyOf(channel)) { ArrayList(2) }.add(channel)
    }
    return groups.map { (key, versions) ->
        if (versions.size == 1) {
            LiveTvMergedChannel(key, versions[0], versions)
        } else {
            val ordered = versions.sortedByDescending { liveTvChannelQuality(it.name).rank }
            val preferred = preferredVersionIds[key]?.let { id -> ordered.firstOrNull { it.id == id } }
            LiveTvMergedChannel(key, preferred ?: ordered.first(), ordered)
        }
    }
}

/** Each channel as its own entry, for when merging is off. */
internal fun unmergedLiveTvChannels(channels: List<LiveTvChannel>): List<LiveTvMergedChannel> =
    channels.map { LiveTvMergedChannel(key = "single:${it.id}", primary = it, versions = listOf(it)) }

internal fun liveTvVersionKey(channel: LiveTvChannel): String =
    channel.group?.trim()?.lowercase().orEmpty() + "\u001F" + liveTvBaseChannelName(channel.name)

/** A short label telling a version apart from the others of its channel. */
internal fun liveTvVersionLabel(channel: LiveTvChannel, versions: List<LiveTvChannel>, index: Int): String {
    val quality = liveTvChannelQuality(channel.name)
    val sameQuality = versions.count { liveTvChannelQuality(it.name) == quality }
    val base = quality.label
    return when {
        base != null && sameQuality == 1 -> base
        base != null -> "$base · ${index + 1}"
        else -> "#${index + 1}"
    }
}

/**
 * [categories] in [mode]'s order. [categories] is in playlist order; [counts] is keyed by the
 * lowercase category name. In [LiveTvCategorySortMode.Custom], categories missing from [customOrder]
 * (new ones from a playlist update) follow the arranged ones in playlist order.
 */
internal fun sortLiveTvCategories(
    categories: List<String>,
    counts: Map<String, Int>,
    mode: LiveTvCategorySortMode,
    customOrder: List<String>,
): List<String> = when (mode) {
    LiveTvCategorySortMode.Playlist -> categories
    LiveTvCategorySortMode.Alphabetical -> categories.sortedBy { it.lowercase() }
    LiveTvCategorySortMode.MostChannels -> categories.sortedWith(
        compareByDescending<String> { counts[it.lowercase()] ?: 0 }.thenBy { it.lowercase() },
    )
    LiveTvCategorySortMode.Custom -> {
        val position = customOrder.withIndex().associate { (index, name) -> name.lowercase() to index }
        categories.withIndex()
            .sortedWith(compareBy({ position[it.value.lowercase()] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }
}
