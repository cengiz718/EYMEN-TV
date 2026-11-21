package com.cengiz718.eymentv.model

enum class NavTab(val label: String) {
    TV("TV"),
    RADIO("Radyo"),
    MOVIES("Film"),
    SERIES("Dizi"),
    SETTINGS("Ayarlar")
}

enum class MediaType {
    TV, RADIO, MOVIE, SERIES_EPISODE
}

data class MediaItem(
    val id: String,
    val title: String,
    val category: String,
    val logoOrPoster: String = "",
    val url: String,
    val backupUrl: String = "",
    val type: MediaType,
    val sourceTag: String = "Gömülü",
    val subtitle: String = ""
)

data class EpisodeItem(
    val episodeNumber: Int,
    val title: String,
    val url: String
)

data class SeasonItem(
    val seasonNumber: Int,
    val episodes: List<EpisodeItem>
)

data class SeriesItem(
    val id: String,
    val title: String,
    val category: String,
    val poster: String,
    val seasons: List<SeasonItem>,
    val sourceTag: String = "Gömülü"
)

data class BackupSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val isWioSpor: Boolean = false
)
