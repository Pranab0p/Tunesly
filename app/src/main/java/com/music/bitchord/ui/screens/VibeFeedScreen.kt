package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt

/**
 * Full-screen vertical Reels/TikTok-style music discovery feed triggered from the
 * glowing Home vibe pill.
 */
@Composable
fun VibeFeedScreen(
    forYouSongs: List<Song>,
    trendingSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPlaySong: (Song) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLike: (Song) -> Unit,
    onOpenPlaylistPicker: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onMoreActions: (Song) -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = For You, 1 = Trending
    BackHandler(onBack = onClose)
    val activeSongs = remember(selectedTab, forYouSongs, trendingSongs) {
        val list = if (selectedTab == 0) forYouSongs else trendingSongs
        list.ifEmpty { forYouSongs.ifEmpty { trendingSongs } }
    }

    val pagerState = rememberPagerState(pageCount = { activeSongs.size.coerceAtLeast(1) })
    var likedIds by remember { mutableStateOf(setOf<String>()) }

    // Auto-play the track on the settled vertical page
    LaunchedEffect(pagerState.settledPage, selectedTab, activeSongs) {
        val song = activeSongs.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (song.videoId != currentSong?.videoId) {
            onPlaySong(song)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07070B)),
    ) {
        if (activeSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading your Vibe Feed...",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                )
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageSong = activeSongs[page]
                val isCurrentPage = pageSong.videoId == currentSong?.videoId
                val isLiked = pageSong.videoId in likedIds

                Box(modifier = Modifier.fillMaxSize()) {
                    // Full-bleed background artwork
                    AsyncImage(
                        model = pageSong.artworkAt(HEADER_ART_PX),
                        contentDescription = pageSong.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Top & Bottom gradient vignette for crisp legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.65f),
                                    0.25f to Color.Black.copy(alpha = 0.15f),
                                    0.55f to Color.Black.copy(alpha = 0.35f),
                                    0.85f to Color.Black.copy(alpha = 0.88f),
                                    1.0f to Color.Black.copy(alpha = 0.96f),
                                ),
                            ),
                    )

                    // Right-side vertical floating action buttons
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 14.dp, bottom = 195.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        VibeCircleAction(
                            icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            tint = if (isLiked) Color(0xFFFF4D6D) else Color.White,
                            onClick = {
                                likedIds = if (isLiked) likedIds - pageSong.videoId else likedIds + pageSong.videoId
                                onToggleLike(pageSong)
                            },
                        )
                        VibeCircleAction(
                            icon = Icons.Rounded.Lyrics,
                            onClick = onOpenFullPlayer,
                        )
                        VibeCircleAction(
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            onClick = { onOpenPlaylistPicker(pageSong) },
                        )
                        VibeCircleAction(
                            icon = Icons.Rounded.Download,
                            onClick = { onDownload(pageSong) },
                        )
                        VibeCircleAction(
                            icon = Icons.Rounded.Share,
                            onClick = { onShare(pageSong) },
                        )
                        VibeCircleAction(
                            icon = Icons.Rounded.MoreVert,
                            onClick = { onMoreActions(pageSong) },
                        )
                    }

                    // Bottom Track Info, Progress & Playback Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = pageSong.artworkAt(ROW_ART_PX),
                                contentDescription = pageSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1F1F29)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pageSong.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = pageSong.artist,
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                                    .clickable {
                                        if (isCurrentPage) onOpenFullPlayer() else onPlaySong(pageSong)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = if (isCurrentPage && isPlaying) "Playing" else "Play",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        val safeDuration = if (isCurrentPage && durationMs > 0L) durationMs else 240_000L
                        val currentPos = if (isCurrentPage) positionMs.coerceIn(0L, safeDuration) else 0L
                        val progress = (currentPos.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

                        Slider(
                            value = progress,
                            onValueChange = { frac ->
                                if (isCurrentPage) onSeek((frac * safeDuration).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatVibeTime(currentPos),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                            )
                            Text(
                                text = formatVibeTime(safeDuration),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Main Playback Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onToggleShuffle) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            IconButton(onClick = onPrevious) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.24f))
                                    .border(1.2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        if (isCurrentPage) onPlayPause() else onPlaySong(pageSong)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isCurrentPage && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            IconButton(onClick = onNext) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            IconButton(onClick = onToggleRepeat) {
                                Icon(
                                    imageVector = if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (repeatMode != 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Swipe up for next",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        // Top Bar Overlay: Back + Tunesly | [For You | Trending] | Search
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(end = 6.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Tunesly",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // [For You | Trending] pill switcher
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VibeTabChip(
                    label = "For You",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                VibeTabChip(
                    label = "Trending",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
            }

            IconButton(
                onClick = {
                    onClose()
                    onOpenSearch()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun VibeTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VibeCircleAction(
    icon: ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatVibeTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
