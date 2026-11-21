package com.cengiz718.eymentv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cengiz718.eymentv.data.PlaylistRepository
import com.cengiz718.eymentv.model.*
import com.cengiz718.eymentv.ui.FullscreenPlayerScreen
import com.cengiz718.eymentv.ui.TvActionButton
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EymentvApp()
            }
        }
    }
}

fun tabIcon(tab: NavTab): ImageVector = when (tab) {
    NavTab.TV -> Icons.Default.Tv
    NavTab.RADIO -> Icons.Default.Radio
    NavTab.MOVIES -> Icons.Default.Movie
    NavTab.SERIES -> Icons.Default.VideoLibrary
    NavTab.SETTINGS -> Icons.Default.Settings
}

@Composable
fun EymentvApp() {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()

    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp
    val isMobilePortrait = screenWidth < 600
    val isTabletOrLandscape = screenWidth in 600..899

    var selectedTab by remember { mutableStateOf(NavTab.TV) }
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Listeler hazırlanıyor...") }

    var tvChannels by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var radioChannels by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var seriesList by remember { mutableStateOf<List<SeriesItem>>(emptyList()) }

    var selectedCategory by remember { mutableStateOf("Tümü") }
    var searchQuery by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(repo.getFavorites()) }
    var backupSources by remember { mutableStateOf(repo.getSavedBackupSources()) }

    var activePlaylist by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var activeIndex by remember { mutableStateOf(-1) }
    var selectedSeriesForDetail by remember { mutableStateOf<SeriesItem?>(null) }

    fun refreshContent() {
        scope.launch {
            isLoading = true
            val result = repo.loadAllContent()
            tvChannels = result.tvChannels
            radioChannels = result.radioChannels
            movies = result.movies
            seriesList = result.series
            statusText = result.statusMessage
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshContent()
    }

    if (activeIndex in activePlaylist.indices) {
        val currentItem = activePlaylist[activeIndex]
        FullscreenPlayerScreen(
            item = currentItem,
            isFavorite = favorites.contains(currentItem.id),
            savedPositionMs = repo.getSavedPosition(currentItem.id),
            onToggleFavorite = { id -> favorites = repo.toggleFavorite(id) },
            onNextChannel = { activeIndex = (activeIndex + 1) % activePlaylist.size },
            onPrevChannel = { activeIndex = if (activeIndex - 1 < 0) activePlaylist.lastIndex else activeIndex - 1 },
            onSavePosition = { id, pos -> repo.saveLastWatched(id, pos) },
            onClose = { activeIndex = -1 }
        )
        return
    }

    selectedSeriesForDetail?.let { series ->
        SeriesDetailOverlay(
            series = series,
            isCompact = isMobilePortrait,
            onPlayEpisode = { episode ->
                val epMedia = MediaItem(
                    id = "${series.id}_ep_${episode.episodeNumber}",
                    title = "${series.title} - ${episode.title}",
                    category = series.category,
                    logoOrPoster = series.poster,
                    url = episode.url,
                    type = MediaType.SERIES_EPISODE,
                    sourceTag = series.sourceTag
                )
                activePlaylist = listOf(epMedia)
                activeIndex = 0
                selectedSeriesForDetail = null
            },
            onDismiss = { selectedSeriesForDetail = null }
        )
        return
    }

    Scaffold(
        containerColor = Color(0xFF0B1120),
        bottomBar = {
            if (isMobilePortrait) {
                NavigationBar(containerColor = Color(0xFF0F172A), contentColor = Color.White) {
                    NavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                selectedCategory = "Tümü"
                                searchQuery = ""
                            },
                            icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF38BDF8),
                                selectedTextColor = Color(0xFF38BDF8),
                                indicatorColor = Color(0xFF1E3A8A),
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal = if (isMobilePortrait) 14.dp else 28.dp,
                    vertical = if (isMobilePortrait) 12.dp else 18.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(if (isMobilePortrait) 26.dp else 32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EYMENTV",
                        color = Color.White,
                        fontSize = if (isMobilePortrait) 19.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isMobilePortrait) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavTab.entries.forEach { tab ->
                            TopMenuButton(
                                text = tab.label,
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    selectedCategory = "Tümü"
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isMobilePortrait) 10.dp else 14.dp))

            val gridColumns = when {
                isMobilePortrait -> 2
                isTabletOrLandscape -> 3
                else -> 4
            }
            val posterColumns = when {
                isMobilePortrait -> 2
                isTabletOrLandscape -> 4
                else -> 5
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Yayın listeleri ve WioSpor kaynakları yükleniyor...", color = Color(0xFFCBD5E1))
                    }
                }
            } else {
                when (selectedTab) {
                    NavTab.TV -> MediaCatalogSection(
                        items = tvChannels,
                        favorites = favorites,
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        onCategoryChange = { selectedCategory = it },
                        onSearchChange = { searchQuery = it },
                        isPosterGrid = false,
                        columnsCount = gridColumns,
                        isCompact = isMobilePortrait,
                        onItemClick = { list, idx ->
                            activePlaylist = list
                            activeIndex = idx
                        }
                    )
                    NavTab.RADIO -> MediaCatalogSection(
                        items = radioChannels,
                        favorites = favorites,
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        onCategoryChange = { selectedCategory = it },
                        onSearchChange = { searchQuery = it },
                        isPosterGrid = false,
                        columnsCount = gridColumns,
                        isCompact = isMobilePortrait,
                        onItemClick = { list, idx ->
                            activePlaylist = list
                            activeIndex = idx
                        }
                    )
                    NavTab.MOVIES -> MediaCatalogSection(
                        items = movies,
                        favorites = favorites,
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        onCategoryChange = { selectedCategory = it },
                        onSearchChange = { searchQuery = it },
                        isPosterGrid = true,
                        columnsCount = posterColumns,
                        isCompact = isMobilePortrait,
                        onItemClick = { list, idx ->
                            activePlaylist = list
                            activeIndex = idx
                        }
                    )
                    NavTab.SERIES -> SeriesCatalogSection(
                        seriesList = seriesList,
                        columnsCount = gridColumns,
                        onSelectSeries = { selectedSeriesForDetail = it }
                    )
                    NavTab.SETTINGS -> SettingsSection(
                        statusText = statusText,
                        backupSources = backupSources,
                        isCompact = isMobilePortrait,
                        onAddSource = { name, url, isWio ->
                            val updated = backupSources + BackupSource(
                                id = "custom_${System.currentTimeMillis()}",
                                name = name,
                                url = url,
                                enabled = true,
                                isWioSpor = isWio
                            )
                            backupSources = updated
                            repo.saveBackupSources(updated)
                            refreshContent()
                        },
                        onToggleSource = { id ->
                            val updated = backupSources.map {
                                if (it.id == id) it.copy(enabled = !it.enabled) else it
                            }
                            backupSources = updated
                            repo.saveBackupSources(updated)
                            refreshContent()
                        },
                        onDeleteSource = { id ->
                            val updated = backupSources.filterNot { it.id == id }
                            backupSources = updated
                            repo.saveBackupSources(updated)
                            refreshContent()
                        },
                        onRefreshAll = { refreshContent() }
                    )
                }
            }
        }
    }
}

@Composable
fun TopMenuButton(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .scale(if (focused) 1.05f else 1.0f)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color(0xFF38BDF8) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        color = when {
            focused -> Color(0xFF2563EB)
            selected -> Color(0xFF1E3A8A)
            else -> Color(0xFF1E293B)
        }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
fun MediaCatalogSection(
    items: List<MediaItem>,
    favorites: Set<String>,
    selectedCategory: String,
    searchQuery: String,
    onCategoryChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    isPosterGrid: Boolean,
    columnsCount: Int,
    isCompact: Boolean,
    onItemClick: (List<MediaItem>, Int) -> Unit
) {
    val categories = remember(items) {
        listOf("Tümü", "Favoriler") + items.map { it.category }.distinct()
    }

    val filtered = remember(items, selectedCategory, searchQuery, favorites) {
        items.filter { item ->
            val matchesCat = when (selectedCategory) {
                "Tümü" -> true
                "Favoriler" -> favorites.contains(item.id)
                else -> item.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isCompact) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Kanal veya içerik ara...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    TopMenuButton(text = cat, selected = selectedCategory == cat, onClick = { onCategoryChange(cat) })
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        TopMenuButton(text = cat, selected = selectedCategory == cat, onClick = { onCategoryChange(cat) })
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Ara...", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.width(220.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered.size) { index ->
                val item = filtered[index]
                MediaCard(
                    item = item,
                    isFavorite = favorites.contains(item.id),
                    isPoster = isPosterGrid,
                    onClick = { onItemClick(filtered, index) }
                )
            }
        }
    }
}

@Composable
fun MediaCard(
    item: MediaItem,
    isFavorite: Boolean,
    isPoster: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .scale(if (focused) 1.03f else 1.0f)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF1E3A8A) else Color(0xFF162032))
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Color(0xFF38BDF8) else Color(0xFF263449),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isPoster) 170.dp else 82.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            if (item.logoOrPoster.isNotBlank()) {
                AsyncImage(
                    model = item.logoOrPoster,
                    contentDescription = item.title,
                    contentScale = if (isPoster) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (isPoster) 0.dp else 8.dp)
                )
            } else {
                Icon(
                    imageVector = when (item.type) {
                        MediaType.RADIO -> Icons.Default.Radio
                        MediaType.MOVIE -> Icons.Default.Movie
                        else -> Icons.Default.Tv
                    },
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(36.dp)
                )
            }
            if (isFavorite) {
                Text(
                    text = "★",
                    color = Color(0xFFFACC15),
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = item.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.category} • ${item.sourceTag}",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SeriesCatalogSection(
    seriesList: List<SeriesItem>,
    columnsCount: Int,
    onSelectSeries: (SeriesItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnsCount),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(seriesList) { series ->
            var focused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .scale(if (focused) 1.03f else 1.0f)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) Color(0xFF1E3A8A) else Color(0xFF162032))
                    .border(
                        width = if (focused) 2.5.dp else 1.dp,
                        color = if (focused) Color(0xFF38BDF8) else Color(0xFF263449),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectSeries(series) }
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (series.poster.isNotBlank()) {
                        AsyncImage(
                            model = series.poster,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(series.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text("${series.seasons.size} Sezon • ${series.category}", color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun SeriesDetailOverlay(
    series: SeriesItem,
    isCompact: Boolean,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSeasonIdx by remember { mutableStateOf(0) }
    val currentSeason = series.seasons.getOrNull(selectedSeasonIdx)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(if (isCompact) 16.dp else 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = series.title,
                color = Color.White,
                fontSize = if (isCompact) 20.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TvActionButton("Geri Dön", onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(series.seasons.size) { index ->
                val season = series.seasons[index]
                TopMenuButton(text = "${season.seasonNumber}. Sezon", selected = selectedSeasonIdx == index, onClick = { selectedSeasonIdx = index })
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(currentSeason?.episodes ?: emptyList()) { ep ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF162032))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ep.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    TvActionButton("İzle", onClick = { onPlayEpisode(ep) })
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    statusText: String,
    backupSources: List<BackupSource>,
    isCompact: Boolean,
    onAddSource: (String, String, Boolean) -> Unit,
    onToggleSource: (String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onRefreshAll: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF162032))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Yayın Durumu & Gömülü Liste", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(statusText, color = Color(0xFF38BDF8), fontSize = 13.sp)
                TvActionButton("Tüm Listeleri Yenile", onClick = onRefreshAll)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF162032))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Yeni Yedek M3U / JSON / GitHub Kaynağı Ekle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (isCompact) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Kaynak Adı") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        placeholder = { Text("https://... (.m3u veya .json)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    TvActionButton("Kaynağı Ekle", onClick = {
                        if (newName.isNotBlank() && newUrl.startsWith("http")) {
                            onAddSource(newName.trim(), newUrl.trim(), newUrl.contains("WioSpor", ignoreCase = true))
                            newName = ""
                            newUrl = ""
                        }
                    })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Kaynak Adı") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            placeholder = { Text("https://... (.m3u veya .json)") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                        TvActionButton("Ekle", onClick = {
                            if (newName.isNotBlank() && newUrl.startsWith("http")) {
                                onAddSource(newName.trim(), newUrl.trim(), newUrl.contains("WioSpor", ignoreCase = true))
                                newName = ""
                                newUrl = ""
                            }
                        })
                    }
                }
            }
        }

        items(backupSources) { src ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF162032))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(src.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(src.url, color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvActionButton(label = if (src.enabled) "Aktif ✓" else "Pasif", onClick = { onToggleSource(src.id) })
                    TvActionButton(label = "Sil", onClick = { onDeleteSource(src.id) })
                }
            }
        }
    }
}
