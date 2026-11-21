package com.cengiz718.eymentv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cengiz718.eymentv.model.MediaItem
import com.cengiz718.eymentv.model.MediaType
import kotlinx.coroutines.delay

@Composable
fun FullscreenPlayerScreen(
    item: MediaItem,
    isFavorite: Boolean,
    savedPositionMs: Long,
    onToggleFavorite: (String) -> Unit,
    onNextChannel: () -> Unit,
    onPrevChannel: () -> Unit,
    onSavePosition: (String, Long) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val config = LocalConfiguration.current
    val isCompact = config.screenWidthDp < 600

    var activeUrl by remember(item.id) { mutableStateOf(item.url) }
    var usingBackup by remember(item.id) { mutableStateOf(false) }
    var playerError by remember(item.id) { mutableStateOf<String?>(null) }
    var showOverlay by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    val overlayFocus = remember { FocusRequester() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(activeUrl) {
        playerError = null
        val mediaItem = ExoMediaItem.fromUri(activeUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (savedPositionMs > 0L && (item.type == MediaType.MOVIE || item.type == MediaType.SERIES_EPISODE)) {
            exoPlayer.seekTo(savedPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!usingBackup && item.backupUrl.isNotBlank()) {
                    usingBackup = true
                    activeUrl = item.backupUrl
                } else {
                    playerError = "Yayın akışı açılamadı (${error.errorCodeName})."
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onSavePosition(item.id, exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            try { overlayFocus.requestFocus() } catch (_: Exception) {}
            delay(6500)
            if (item.type != MediaType.RADIO && playerError == null) {
                showOverlay = false
            }
        }
    }

    BackHandler {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showOverlay = !showOverlay }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (!showOverlay) {
                                showOverlay = true
                                true
                            } else false
                        }
                        Key.ChannelUp -> {
                            onNextChannel()
                            true
                        }
                        Key.ChannelDown -> {
                            onPrevChannel()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (item.type == MediaType.RADIO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF090D16))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 96.dp else 120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB).copy(alpha = 0.25f))
                            .border(2.dp, Color(0xFF38BDF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(if (isCompact) 48.dp else 60.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = if (isCompact) 22.sp else 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${item.category} • CANLI RADYO",
                        color = Color(0xFF38BDF8),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E293B))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = playerError!!, color = Color.White, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TvActionButton("Tekrar Dene", onClick = {
                            playerError = null
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        })
                        TvActionButton("Sonraki", onClick = onNextChannel)
                        TvActionButton("Geri", onClick = onClose)
                    }
                }
            }
        }

        if (showOverlay || item.type == MediaType.RADIO) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
                    .padding(horizontal = if (isCompact) 16.dp else 32.dp, vertical = if (isCompact) 16.dp else 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (usingBackup) Color(0xFFF59E0B) else Color(0xFF10B981),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (usingBackup) "YEDEK AKIŞ" else item.sourceTag.uppercase(),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.category, color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = if (isCompact) 19.sp else 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TvActionButton(
                        label = if (isPlaying) "Duraklat" else "Oynat",
                        modifier = Modifier.focusRequester(overlayFocus),
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    )
                    TvActionButton(label = "Önceki", onClick = onPrevChannel)
                    TvActionButton(label = "Sonraki", onClick = onNextChannel)
                    TvActionButton(
                        label = if (isFavorite) "Favoride ★" else "Favori ☆",
                        onClick = { onToggleFavorite(item.id) }
                    )
                    if (item.type != MediaType.RADIO) {
                        TvActionButton(label = "Tam Ekran Döndür", onClick = {
                            val currentOrient = activity?.requestedOrientation
                            activity?.requestedOrientation =
                                if (currentOrient == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        })
                    }
                    if (item.backupUrl.isNotBlank() && !usingBackup) {
                        TvActionButton(label = "Yedek Akış", onClick = {
                            usingBackup = true
                            activeUrl = item.backupUrl
                        })
                    }
                    TvActionButton(label = "Kapat", onClick = onClose)
                }
            }
        }
    }
}

@Composable
fun TvActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color(0xFF38BDF8) else Color(0xFF334155),
                shape = RoundedCornerShape(10.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Color(0xFF2563EB) else Color(0xFF1E293B),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium)
    }
}
