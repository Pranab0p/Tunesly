package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.delay

/**
 * Universal floating Dynamic Island capsule anchored at the top-center of the screen.
 * Displays compact playback info + live waveform, and expands on tap with quick controls.
 */
@Composable
fun TuneslyDynamicIsland(
    song: Song?,
    isPlaying: Boolean,
    visible: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Auto-collapse expanded island after 6 seconds of inactivity.
    LaunchedEffect(expanded, song, isPlaying) {
        if (expanded) {
            delay(6000)
            expanded = false
        }
    }

    // Collapse if visibility is lost (e.g. full player opened).
    LaunchedEffect(visible) {
        if (!visible) expanded = false
    }

    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible && song != null,
            enter = fadeIn(tween(220)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.86f),
        ) {
            if (song != null) {
                val accent = MaterialTheme.colorScheme.primary
                val islandShape = RoundedCornerShape(28.dp)

                Surface(
                    shape = islandShape,
                    color = Color(0xFF0B0B10).copy(alpha = 0.94f),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    accent.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.16f),
                                    accent.copy(alpha = 0.55f),
                                ),
                            ),
                            shape = islandShape,
                        )
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                        .clip(islandShape)
                        .clickable {
                            if (!expanded) {
                                expanded = true
                            } else {
                                expanded = false
                                onOpenPlayer()
                            }
                        },
                ) {
                    if (!expanded) {
                        // Compact Pill
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1D1D26)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val art = song.artworkAt(ROW_ART_PX)
                                if (art != null) {
                                    AsyncImage(
                                        model = art,
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }

                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 138.dp),
                            )

                            IslandWaveform(
                                isPlaying = isPlaying,
                                color = accent,
                            )
                        }
                    } else {
                        // Expanded Island Card
                        Column(
                            modifier = Modifier
                                .width(308.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1D1D26))
                                        .clickable {
                                            expanded = false
                                            onOpenPlayer()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val art = song.artworkAt(ROW_ART_PX)
                                    if (art != null) {
                                        AsyncImage(
                                            model = art,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(46.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            expanded = false
                                            onOpenPlayer()
                                        },
                                ) {
                                    Text(
                                        text = song.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (song.artist.isNotBlank()) {
                                        Text(
                                            text = song.artist,
                                            color = Color.White.copy(alpha = 0.68f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        expanded = false
                                        onOpenPlayer()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInFull,
                                        contentDescription = "Open player",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                IconButton(
                                    onClick = { expanded = false },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Collapse",
                                        tint = Color.White.copy(alpha = 0.72f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = onPrevious,
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                        .clickable(onClick = onPlayPause),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                IconButton(
                                    onClick = onNext,
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandWaveform(
    isPlaying: Boolean,
    color: Color,
) {
    if (!isPlaying) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(14.dp),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.65f)),
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "island_wave")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(490, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b3",
    )
    val bar4 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(390, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b4",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(14.dp),
    ) {
        listOf(bar1, bar2, bar3, bar4).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((14f * fraction).dp.coerceAtLeast(3.dp))
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
