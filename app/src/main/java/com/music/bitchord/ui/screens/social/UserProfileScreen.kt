package com.music.bitchord.ui.screens.social

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.tuneslycloud.TuneslyCloudManager
import com.music.bitchord.data.tuneslycloud.TuneslyCloudTrack
import com.music.bitchord.data.tuneslycloud.TuneslyPublicPlaylist
import com.music.bitchord.data.tuneslycloud.TuneslyPublicProfile
import kotlinx.coroutines.launch

// Instagram Iconic Gradient Colors
private val InstaStoryGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF833AB4),
        Color(0xFFC13584),
        Color(0xFFE1306C),
        Color(0xFFFD1D1D),
        Color(0xFFF77737),
        Color(0xFFFCAF45),
    )
)

@Composable
fun UserProfileScreen(
    username: String,
    onBack: () -> Unit,
    onMessageClick: (String) -> Unit,
    onJamClick: (String) -> Unit,
    onPlayTrack: (Song) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var profileState by remember(username) { mutableStateOf<TuneslyPublicProfile?>(null) }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isFollowing by remember { mutableStateOf(false) }
    var followersCount by remember { mutableIntStateOf(0) }
    var followActionLoading by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var editBioText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(username) {
        loading = true
        error = null
        TuneslyCloudManager.getUserProfile(username).fold(
            onSuccess = { prof ->
                profileState = prof
                isFollowing = prof.isFollowing
                followersCount = prof.followersCount
                editBioText = prof.bio
                loading = false
            },
            onFailure = {
                error = it.message ?: "Failed to load profile"
                loading = false
            },
        )
    }

    if (loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (error != null || profileState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = error ?: "User not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val profile = profileState!!
    val hoursListened = (profile.totalPlayTimeSeconds / 3600f).coerceAtLeast(0f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
    ) {
        // --- 1. Instagram-Style Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = profile.username,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Verified Music User",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString("@${profile.username}"))
                    Toast.makeText(context, "Copied @${profile.username} to clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = "Share Profile",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

        // --- 2. Instagram Profile Content Body ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Header: Avatar + Counts
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Instagram Story Ring Avatar
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(InstaStoryGradient)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (profile.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = profile.avatar,
                                    contentDescription = profile.username,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = profile.username.take(1).uppercase(),
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Stats: Tracks, Followers, Following
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            InstagramStatColumn(
                                count = "${profile.recentTracks.size.coerceAtLeast(1)}",
                                label = "Tracks",
                            )
                            InstagramStatColumn(
                                count = "$followersCount",
                                label = "Followers",
                            )
                            InstagramStatColumn(
                                count = "${profile.followingCount}",
                                label = "Following",
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Bio Details
                    Text(
                        text = profile.username,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Text(
                            text = "🎵 Tunesly Music Explorer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    if (profile.bio.isNotBlank()) {
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${"%.1f".format(hoursListened)} hours listened on Tunesly",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Action Buttons (Instagram Style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (profile.isMe) {
                            // Current user: Edit Profile
                            OutlinedButton(
                                onClick = { showBioDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Bio", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        } else {
                            // Follow / Following Button
                            if (isFollowing) {
                                OutlinedButton(
                                    onClick = {
                                        if (followActionLoading) return@OutlinedButton
                                        followActionLoading = true
                                        scope.launch {
                                            TuneslyCloudManager.unfollowUser(profile.username).onSuccess {
                                                isFollowing = false
                                                followersCount = (followersCount - 1).coerceAtLeast(0)
                                            }
                                            followActionLoading = false
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Following", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (followActionLoading) return@Button
                                        followActionLoading = true
                                        scope.launch {
                                            TuneslyCloudManager.followUser(profile.username).onSuccess {
                                                isFollowing = true
                                                followersCount += 1
                                            }
                                            followActionLoading = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Follow", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Message Button
                            FilledTonalButton(
                                onClick = { onMessageClick(profile.username) },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Message", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            // Jam Room Shortcut
                            FilledTonalButton(
                                onClick = { onJamClick(profile.username) },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Rounded.Headphones, contentDescription = "Jam", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Jam", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Instagram Highlights Story Bubbles
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item {
                            InstagramHighlightBubble(
                                title = "🔥 Hits",
                                icon = Icons.Rounded.Stars,
                                onClick = { selectedTab = 0 },
                            )
                        }
                        item {
                            InstagramHighlightBubble(
                                title = "💿 Playlists",
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                onClick = { selectedTab = 1 },
                            )
                        }
                        item {
                            InstagramHighlightBubble(
                                title = "✨ Taste",
                                icon = Icons.Rounded.AutoAwesome,
                                onClick = { selectedTab = 2 },
                            )
                        }
                        item {
                            InstagramHighlightBubble(
                                title = "🎧 Jam",
                                icon = Icons.Rounded.Headphones,
                                onClick = { onJamClick(profile.username) },
                            )
                        }
                    }
                }
            }

            // --- 3. Instagram Tabs Row (Grid, Playlists, Taste) ---
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 2.dp,
                        )
                    },
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                    },
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                Icons.Rounded.Apps,
                                contentDescription = "Tracks Grid",
                                tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = "Playlists",
                                tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = "About & Taste",
                                tint = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            // --- 4. Tab Contents ---
            when (selectedTab) {
                // Tab 0: Instagram 3-Column Square Music Grid
                0 -> {
                    if (profile.recentTracks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No tracks played yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        // Chunk tracks into rows of 3 like Instagram Grid
                        val chunkedTracks = profile.recentTracks.chunked(3)
                        items(chunkedTracks) { rowTracks ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                for (track in rowTracks) {
                                    InstagramGridPostItem(
                                        track = track,
                                        modifier = Modifier.weight(1f),
                                        onPlay = {
                                            onPlayTrack(
                                                Song(
                                                    videoId = track.trackId,
                                                    title = track.title,
                                                    artist = track.artist,
                                                    thumbnailUrl = track.artworkUrl,
                                                    durationText = null,
                                                    fromAutoplay = false,
                                                )
                                            )
                                        },
                                    )
                                }
                                // Fill remaining slots if row has less than 3 items
                                for (i in rowTracks.size until 3) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Tab 1: Playlists
                1 -> {
                    if (profile.playlists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No public playlists shared yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(profile.playlists) { playlist ->
                            InstagramPlaylistCard(playlist = playlist)
                        }
                    }
                }

                // Tab 2: Taste, Top Artists & Stats
                2 -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Top Artists",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))

                            if (profile.topArtists.isEmpty()) {
                                Text(
                                    text = "Listening history building up...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    profile.topArtists.take(4).forEach { artist ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(vertical = 4.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = artist,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            Text(
                                text = "Member Information",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Total Listen Time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("%.1f hours".format(hoursListened), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Favorite Tracks Saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${profile.favoritesCount}", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Joined Tunesly", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(profile.memberSince.take(10), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Bio Dialog
    if (showBioDialog) {
        AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text("Edit Your Bio") },
            text = {
                OutlinedTextField(
                    value = editBioText,
                    onValueChange = { editBioText = it.take(200) },
                    placeholder = { Text("Write something about your music taste...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            TuneslyCloudManager.updateProfile(bio = editBioText).onSuccess {
                                profileState = profileState?.copy(bio = editBioText)
                            }
                            showBioDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBioDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// Instagram Stat Column (Counts + Label)
@Composable
private fun InstagramStatColumn(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Instagram Highlight Story Bubble
@Composable
private fun InstagramHighlightBubble(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// Instagram 3-Column Square Grid Post Item
@Composable
private fun InstagramGridPostItem(
    track: TuneslyCloudTrack,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onPlay() }
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (track.artworkUrl.isNotBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // Instagram gradient vignette overlay at the bottom with title
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f),
                        )
                    )
                ),
            contentAlignment = Alignment.BottomStart,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// Instagram Playlist Card
@Composable
private fun InstagramPlaylistCard(playlist: TuneslyPublicPlaylist) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (playlist.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.artworkUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
