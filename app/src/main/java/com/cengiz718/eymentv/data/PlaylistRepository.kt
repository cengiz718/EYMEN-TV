package com.cengiz718.eymentv.data

import android.content.Context
import com.cengiz718.eymentv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PlaylistRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("eymentv_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BELESPLUS_M3U =
            "https://raw.githubusercontent.com/sunarproje/support/refs/heads/main/belesplus.m3u"
        const val DEFAULT_WIOSPOR_REPO =
            "https://raw.githubusercontent.com/Wiojelt/WioSpor/main/repo.json"
        const val DEFAULT_WIOSPOR_PLUGINS =
            "https://raw.githubusercontent.com/Wiojelt/WioSpor/main/builds/plugins.json"
    }

    fun getSavedBackupSources(): List<BackupSource> {
        val raw = prefs.getString("backup_sources", null)
        if (raw.isNullOrBlank()) {
            val defaults = listOf(
                BackupSource("src_belesplus", "Beleş Plus Yedek M3U", DEFAULT_BELESPLUS_M3U, true, false),
                BackupSource("src_wiospor", "WioSpor GitHub Kaynağı", DEFAULT_WIOSPOR_REPO, true, true)
            )
            saveBackupSources(defaults)
            return defaults
        }
        val list = mutableListOf<BackupSource>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                BackupSource(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    enabled = o.optBoolean("enabled", true),
                    isWioSpor = o.optBoolean("isWioSpor", false)
                )
            )
        }
        return list
    }

    fun saveBackupSources(sources: List<BackupSource>) {
        val arr = JSONArray()
        sources.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("url", s.url)
            o.put("enabled", s.enabled)
            o.put("isWioSpor", s.isWioSpor)
            arr.put(o)
        }
        prefs.edit().putString("backup_sources", arr.toString()).apply()
    }

    fun getFavorites(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun toggleFavorite(id: String): Set<String> {
        val current = getFavorites().toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        prefs.edit().putStringSet("favorites", current).apply()
        return current
    }

    fun saveLastWatched(mediaId: String, positionMs: Long) {
        prefs.edit()
            .putString("last_media_id", mediaId)
            .putLong("pos_$mediaId", positionMs)
            .apply()
    }

    fun getSavedPosition(mediaId: String): Long = prefs.getLong("pos_$mediaId", 0L)

    data class LoadedCatalog(
        val tvChannels: List<MediaItem>,
        val radioChannels: List<MediaItem>,
        val movies: List<MediaItem>,
        val series: List<SeriesItem>,
        val statusMessage: String
    )

    suspend fun loadAllContent(): LoadedCatalog = withContext(Dispatchers.IO) {
        val tvList = mutableListOf<MediaItem>()
        val radioList = mutableListOf<MediaItem>()
        val movieList = mutableListOf<MediaItem>()
        val seriesList = mutableListOf<SeriesItem>()

        try {
            val jsonStr = context.assets.open("default_playlist.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)

            val tvArr = root.optJSONArray("tv") ?: JSONArray()
            for (i in 0 until tvArr.length()) {
                val o = tvArr.getJSONObject(i)
                tvList.add(
                    MediaItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        category = o.optString("category", "Genel"),
                        logoOrPoster = o.optString("logo", ""),
                        url = o.getString("url"),
                        backupUrl = o.optString("backupUrl", ""),
                        type = MediaType.TV,
                        sourceTag = "Gömülü"
                    )
                )
            }

            val radArr = root.optJSONArray("radio") ?: JSONArray()
            for (i in 0 until radArr.length()) {
                val o = radArr.getJSONObject(i)
                radioList.add(
                    MediaItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        category = o.optString("category", "Radyo"),
                        logoOrPoster = o.optString("logo", ""),
                        url = o.getString("url"),
                        type = MediaType.RADIO,
                        sourceTag = "Gömülü"
                    )
                )
            }

            val movArr = root.optJSONArray("movies") ?: JSONArray()
            for (i in 0 until movArr.length()) {
                val o = movArr.getJSONObject(i)
                movieList.add(
                    MediaItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        category = o.optString("category", "Film"),
                        logoOrPoster = o.optString("poster", ""),
                        url = o.getString("url"),
                        type = MediaType.MOVIE,
                        sourceTag = "Gömülü",
                        subtitle = o.optString("year", "")
                    )
                )
            }

            val serArr = root.optJSONArray("series") ?: JSONArray()
            for (i in 0 until serArr.length()) {
                val o = serArr.getJSONObject(i)
                val seasons = mutableListOf<SeasonItem>()
                val sArr = o.optJSONArray("seasons") ?: JSONArray()
                for (s in 0 until sArr.length()) {
                    val sObj = sArr.getJSONObject(s)
                    val epList = mutableListOf<EpisodeItem>()
                    val eArr = sObj.optJSONArray("episodes") ?: JSONArray()
                    for (e in 0 until eArr.length()) {
                        val eObj = eArr.getJSONObject(e)
                        epList.add(
                            EpisodeItem(
                                episodeNumber = eObj.getInt("episodeNumber"),
                                title = eObj.getString("title"),
                                url = eObj.getString("url")
                            )
                        )
                    }
                    seasons.add(SeasonItem(sObj.getInt("seasonNumber"), epList))
                }
                seriesList.add(
                    SeriesItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        category = o.optString("category", "Dizi"),
                        poster = o.optString("poster", ""),
                        seasons = seasons,
                        sourceTag = "Gömülü"
                    )
                )
            }
        } catch (_: Exception) { }

        val activeSources = getSavedBackupSources().filter { it.enabled }
        var extraLoadedCount = 0

        for (source in activeSources) {
            try {
                if (source.isWioSpor || source.url.contains("WioSpor", ignoreCase = true)) {
                    val wioItems = fetchWioSporEntries(source.url, source.name)
                    tvList.addAll(wioItems)
                    extraLoadedCount += wioItems.size
                } else {
                    val text = fetchUrlText(source.url)
                    if (text.trimStart().startsWith("{")) {
                        val parsed = parseCustomJson(text, source.name)
                        tvList.addAll(parsed.first)
                        radioList.addAll(parsed.second)
                        movieList.addAll(parsed.third)
                        extraLoadedCount += parsed.first.size + parsed.second.size + parsed.third.size
                    } else {
                        val parsedM3u = parseM3uContent(text, source.name)
                        parsedM3u.forEach { item ->
                            when (item.type) {
                                MediaType.RADIO -> radioList.add(item)
                                MediaType.MOVIE -> movieList.add(item)
                                else -> tvList.add(item)
                            }
                        }
                        extraLoadedCount += parsedM3u.size
                    }
                }
            } catch (_: Exception) { }
        }

        LoadedCatalog(
            tvChannels = tvList,
            radioChannels = radioList,
            movies = movieList,
            series = seriesList,
            statusMessage = "Gömülü + $extraLoadedCount yedek içerik yüklendi"
        )
    }

    private fun fetchUrlText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 9000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Eymentv/1.4")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseM3uContent(raw: String, sourceTag: String): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val lines = raw.lines()
        var currentTitle = ""
        var currentGroup = "Yedek TV"
        var currentLogo = ""
        var index = 0
        val logoRegex = Regex("""tvg-logo="([^"]*)"""")
        val groupRegex = Regex("""group-title="([^"]*)"""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                currentLogo = logoRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
                currentGroup = groupRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: "Yedek TV"
                currentTitle = trimmed.substringAfterLast(",").trim().ifEmpty { "Kanal ${index + 1}" }
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val lowerGroup = currentGroup.lowercase()
                val lowerTitle = currentTitle.lowercase()
                val detectedType = when {
                    lowerGroup.contains("radyo") || lowerGroup.contains("radio") || lowerTitle.contains("radyo") || lowerTitle.contains(" fm") -> MediaType.RADIO
                    lowerGroup.contains("film") || lowerGroup.contains("movie") || lowerGroup.contains("vod") || trimmed.endsWith(".mp4") || trimmed.endsWith(".mkv") -> MediaType.MOVIE
                    else -> MediaType.TV
                }
                result.add(
                    MediaItem(
                        id = "${sourceTag.hashCode()}_$index",
                        title = currentTitle.ifEmpty { "Yayın ${index + 1}" },
                        category = currentGroup,
                        logoOrPoster = currentLogo,
                        url = trimmed,
                        type = detectedType,
                        sourceTag = sourceTag
                    )
                )
                index++
                currentTitle = ""
                currentLogo = ""
            }
        }
        return result
    }

    private fun parseCustomJson(raw: String, sourceTag: String): Triple<List<MediaItem>, List<MediaItem>, List<MediaItem>> {
        val tv = mutableListOf<MediaItem>()
        val rad = mutableListOf<MediaItem>()
        val mov = mutableListOf<MediaItem>()
        val root = JSONObject(raw)
        root.optJSONArray("tv")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tv.add(MediaItem("${sourceTag}_tv_$i", o.getString("title"), o.optString("category", "TV"), o.optString("logo", ""), o.getString("url"), o.optString("backupUrl", ""), MediaType.TV, sourceTag))
            }
        }
        root.optJSONArray("radio")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                rad.add(MediaItem("${sourceTag}_rad_$i", o.getString("title"), o.optString("category", "Radyo"), o.optString("logo", ""), o.getString("url"), "", MediaType.RADIO, sourceTag))
            }
        }
        root.optJSONArray("movies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                mov.add(MediaItem("${sourceTag}_mov_$i", o.getString("title"), o.optString("category", "Film"), o.optString("poster", ""), o.getString("url"), "", MediaType.MOVIE, sourceTag))
            }
        }
        return Triple(tv, rad, mov)
    }

    private fun fetchWioSporEntries(repoUrl: String, sourceTag: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            val repoText = fetchUrlText(repoUrl)
            val repoObj = JSONObject(repoText)
            val pluginLists = repoObj.optJSONArray("pluginLists")
            val pluginsJsonUrl = if (pluginLists != null && pluginLists.length() > 0) {
                pluginLists.getString(0)
            } else {
                DEFAULT_WIOSPOR_PLUGINS
            }
            val pluginsRaw = fetchUrlText(pluginsJsonUrl)
            val arr = JSONArray(pluginsRaw)
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val name = p.optString("name", "WioSpor Kanal ${i + 1}")
                val icon = p.optString("iconUrl", "")
                val desc = p.optString("description", "WioSpor Canlı Spor")
                val streamUrl = p.optString("streamUrl", p.optString("url", ""))
                if (streamUrl.startsWith("http") && !streamUrl.endsWith(".cs3")) {
                    items.add(
                        MediaItem(
                            id = "wiospor_$i",
                            title = name,
                            category = "WioSpor",
                            logoOrPoster = icon,
                            url = streamUrl,
                            type = MediaType.TV,
                            sourceTag = sourceTag,
                            subtitle = desc
                        )
                    )
                }
            }
        } catch (_: Exception) { }

        if (items.isEmpty()) {
            val wioGroups = listOf(
                Triple("WioSpor - Mor Spor 1", "https://trkvz.daioncdn.net/aspor/aspor.m3u8?ce=3&app=45f847c4-04e8-419a-a561-2ebf87084765", "WioSpor (Mor Grup)"),
                Triple("WioSpor - Turuncu Spor 1", "https://trkvz-live.ercdn.net/anewshd/anewshd.m3u8", "WioSpor (Turuncu Grup)")
            )
            wioGroups.forEachIndexed { idx, (title, url, cat) ->
                items.add(
                    MediaItem(
                        id = "wiospor_group_$idx",
                        title = title,
                        category = cat,
                        url = url,
                        type = MediaType.TV,
                        sourceTag = "WioSpor",
                        subtitle = "Wiojelt/WioSpor Senkronize"
                    )
                )
            }
        }
        return items
    }
}
