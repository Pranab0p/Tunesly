package com.music.bitchord.ui.screens.social

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
fun UserProfileScreen(
    username: String,
    onBack: () -> Unit,
    onMessageClick: (String) -> Unit,
    onJamClick: (String) -> Unit,
    onPlayTrack: (Song) -> Unit,
    onOpenPlaylist: (id: String, name: String) -> Unit = { _, _ -> },
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var currentUsername by remember(username) { mutableStateOf(username) }
    var profileState by remember(currentUsername) { mutableStateOf<TuneslyPublicProfile?>(null) }
    var loading by remember(currentUsername) { mutableStateOf(true) }
    var error by remember(currentUsername) { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isFollowing by remember { mutableStateOf(false) }
    var followersCount by remember { mutableIntStateOf(0) }
    var followActionLoading by remember { mutableStateOf(false) }

    // Edit Profile Modal State
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Followers / Following Modal State
    var showUserListModal by remember { mutableStateOf(false) }
    var userListType by remember { mutableStateOf("Followers") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(currentUsername) {
        loading = true
        error = null
        scope.launch { TuneslyCloudManager.sendHeartbeat() }
        TuneslyCloudManager.getUserProfile(currentUsername).fold(
            onSuccess = { prof ->
                profileState = prof
                isFollowing = prof.isFollowing
                followersCount = prof.followersCount
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
        // --- 1. Top Navigation Bar (Tunesly Style) ---
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName.ifBlank { profile.username },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }

            IconButton(
                onClick = {
                    val shareUrl = "https://www.tunesly.online/user/@${profile.username}"
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "Check out ${profile.displayName.ifBlank { profile.username }}'s profile on Tunesly: $shareUrl"
                        )
                    }
                    try {
                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Profile"))
                    } catch (e: Exception) {
                        clipboardManager.setText(AnnotatedString(shareUrl))
                        Toast.makeText(context, "Link copied to clipboard: $shareUrl", Toast.LENGTH_SHORT).show()
                    }
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

        val displayPlaylists = remember(profile.playlists, profile.isMe) {
            if (profile.isMe) {
                val local = com.music.bitchord.data.spotify.LocalPlaylistStore.playlists.value
                    .filterNot { it.id == "liked_songs" }
                    .map { lp ->
                        TuneslyPublicPlaylist(
                            id = lp.id,
                            name = lp.title,
                            description = "",
                            trackCount = lp.songs.size,
                            artworkUrl = lp.songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl.orEmpty(),
                        )
                    }
                val existingIds = profile.playlists.map { it.id }.toSet()
                profile.playlists + local.filterNot { existingIds.contains(it.id) }
            } else {
                profile.playlists
            }
        }

        // --- 2. Main Profile Content ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Profile Card Header (Tunesly Dark Glass Card)
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val (isUserOnline, statusText) = remember(profile.isOnline, profile.lastActive) {
                            formatOnlineStatus(profile.isOnline, profile.lastActive)
                        }

                        // User Avatar (DP) with optional Edit badge / Online badge
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, if (isUserOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.primary, CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                TuneslyAvatarView(
                                    avatar = profile.avatar,
                                    displayName = profile.displayName,
                                    username = profile.username,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            if (profile.isMe) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { showEditProfileDialog = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = "Change DP",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(2.5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(if (isUserOnline) Color(0xFF10B981) else Color(0xFF9E9E9E))
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Display Name & Username Handle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.displayName.ifBlank { profile.username },
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Verified Listener",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Text(
                            text = "@${profile.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )

                        // Online / Last Seen Status Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUserOnline) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (isUserOnline) Color(0xFF10B981) else Color(0xFF9E9E9E),
                                            shape = CircleShape,
                                        )
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isUserOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Bio
                        if (profile.bio.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = profile.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Stats Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TuneslyStatPill(
                                count = "%.1fh".format(hoursListened),
                                label = "Listened",
                            )
                            TuneslyStatPill(
                                count = "$followersCount",
                                label = "Followers",
                                onClick = {
                                    userListType = "Followers"
                                    showUserListModal = true
                                },
                            )
                            TuneslyStatPill(
                                count = "${profile.followingCount}",
                                label = "Following",
                                onClick = {
                                    userListType = "Following"
                                    showUserListModal = true
                                },
                            )
                            TuneslyStatPill(
                                count = "${profile.recentTracks.size}",
                                label = "Tracks",
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // Action Buttons
                        if (profile.isMe) {
                            Button(
                                onClick = { showEditProfileDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit Profile & DP", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
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
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Following")
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
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Text("Follow", fontWeight = FontWeight.Bold)
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { onMessageClick(profile.username) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Message")
                                }

                                FilledTonalButton(
                                    onClick = { onJamClick(profile.username) },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Icon(Icons.Rounded.Headphones, contentDescription = "Jam", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Tabs Row (Tracks, Playlists, Taste)
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp,
                        )
                    },
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Recent Songs", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Playlists", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("About & Taste", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Tab 0: Recent Songs List
            if (selectedTab == 0) {
                if (profile.recentTracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No tracks played recently",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(profile.recentTracks) { track ->
                        TuneslyTrackRow(
                            track = track,
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
                }
            }

            // Tab 1: Playlists (Both user's personal/local & cloud public playlists)
            if (selectedTab == 1) {
                if (displayPlaylists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
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
                    items(displayPlaylists) { playlist ->
                        TuneslyPlaylistRow(
                            playlist = playlist,
                            onClick = {
                                onOpenPlaylist(playlist.id, playlist.name)
                            }
                        )
                    }
                }
            }

            // Tab 2: Taste & Top Artists
            if (selectedTab == 2) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Top Artists Listened",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))

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
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Rounded.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
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

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Account & Activity",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Favorite Tracks Saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${profile.favoritesCount}", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(10.dp))
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

    // --- 3. Complete Edit Profile Dialog (DP, Name, Username, Bio) ---
    if (showEditProfileDialog) {
        EditProfileModal(
            currentProfile = profile,
            onDismiss = { showEditProfileDialog = false },
            onProfileUpdated = { updatedUsername, updatedDisplayName, updatedBio, updatedAvatar ->
                profileState = profileState?.copy(
                    username = updatedUsername,
                    displayName = updatedDisplayName,
                    bio = updatedBio,
                    avatar = updatedAvatar,
                )
                if (updatedUsername != currentUsername) {
                    currentUsername = updatedUsername
                }
                showEditProfileDialog = false
                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showUserListModal) {
        UserListModal(
            title = userListType,
            username = profile.username,
            onDismiss = { showUserListModal = false },
            onOpenProfile = { clickedUsername ->
                currentUsername = clickedUsername
            },
        )
    }
}

// Modal for editing DP, Name, Username, and Bio
@Composable
private fun EditProfileModal(
    currentProfile: TuneslyPublicProfile,
    onDismiss: () -> Unit,
    onProfileUpdated: (username: String, displayName: String, bio: String, avatar: String) -> Unit,
) {
    var editDisplayName by remember { mutableStateOf(currentProfile.displayName.ifBlank { currentProfile.username }) }
    var editUsername by remember { mutableStateOf(currentProfile.username) }
    var editBio by remember { mutableStateOf(currentProfile.bio) }
    var editAvatar by remember { mutableStateOf(currentProfile.avatar) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Gallery Photo Picker
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        // Scale to max 480x480 for fast upload and lightweight storage
                        val maxDim = 480
                        val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                        val (targetW, targetH) = if (ratio > 1) {
                            Pair(maxDim, (maxDim / ratio).toInt())
                        } else {
                            Pair((maxDim * ratio).toInt(), maxDim)
                        }
                        val scaled = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                        val bytes = out.toByteArray()
                        val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64Str"

                        withContext(Dispatchers.Main) {
                            editAvatar = dataUri
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not load selected photo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Profile & DP",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Avatar preview and change button
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            TuneslyAvatarView(
                                avatar = editAvatar,
                                displayName = editDisplayName,
                                username = editUsername,
                                modifier = Modifier.fillMaxSize(),
                                textSize = 34.sp,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { galleryPicker.launch("image/*") },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Choose Photo", fontSize = 12.sp)
                            }

                            if (editAvatar.isNotBlank()) {
                                TextButton(
                                    onClick = { editAvatar = "" },
                                ) {
                                    Text("Remove DP", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Display Name Input
                item {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it.take(50) },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Unique Username Handle Input
                item {
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = {
                            // clean username: lowercase alphanumeric and underscores
                            editUsername = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(30)
                        },
                        label = { Text("Username (@handle)") },
                        prefix = { Text("@") },
                        supportingText = { Text("Unique handle across all users") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Bio Input
                item {
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it.take(250) },
                        label = { Text("Bio") },
                        placeholder = { Text("Tell other music lovers about your music taste...") },
                        supportingText = { Text("${editBio.length}/250 characters") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && editUsername.length >= 3,
                onClick = {
                    saving = true
                    errorMessage = null
                    scope.launch {
                        TuneslyCloudManager.updateProfile(
                            displayName = editDisplayName.trim(),
                            newUsername = editUsername.trim(),
                            bio = editBio.trim(),
                            avatar = editAvatar.trim(),
                        ).onSuccess { pair ->
                            saving = false
                            onProfileUpdated(pair.first, pair.second, editBio.trim(), editAvatar.trim())
                        }.onFailure { err ->
                            saving = false
                            errorMessage = err.message ?: "Failed to update profile"
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save Changes")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !saving,
            ) {
                Text("Cancel")
            }
        },
    )
}

// Tunesly Stat Pill
@Composable
private fun TuneslyStatPill(
    count: String,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        } else {
            Modifier
        },
    ) {
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

@Composable
private fun UserListModal(
    title: String,
    username: String,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    var users by remember { mutableStateOf<List<com.music.bitchord.data.tuneslycloud.TuneslyUserSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(title, username) {
        loading = true
        error = null
        val res = if (title == "Followers") {
            TuneslyCloudManager.getFollowers(username)
        } else {
            TuneslyCloudManager.getFollowing(username)
        }
        res.fold(
            onSuccess = {
                users = it
                loading = false
            },
            onFailure = {
                error = it.message ?: "Failed to load $title"
                loading = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (users.isEmpty()) {
                    Text(
                        text = if (title == "Followers") "No followers yet" else "Not following anyone yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(users, key = { it.username }) { user ->
                            var userFollowing by remember { mutableStateOf(false) }
                            var followBusy by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        onDismiss()
                                        onOpenProfile(user.username)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TuneslyAvatarView(
                                        avatar = user.avatar,
                                        displayName = user.username,
                                        username = user.username,
                                        modifier = Modifier.fillMaxSize(),
                                        textSize = 16.sp,
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "@${user.username}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (user.bio.isNotBlank()) {
                                        Text(
                                            text = user.bio,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // Follow / Unfollow toggle button
                                OutlinedButton(
                                    onClick = {
                                        if (followBusy) return@OutlinedButton
                                        followBusy = true
                                        scope.launch {
                                            if (userFollowing) {
                                                TuneslyCloudManager.unfollowUser(user.username).fold(
                                                    onSuccess = {
                                                        userFollowing = false
                                                        followBusy = false
                                                    },
                                                    onFailure = {
                                                        followBusy = false
                                                        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                                                    },
                                                )
                                            } else {
                                                TuneslyCloudManager.followUser(user.username).fold(
                                                    onSuccess = {
                                                        userFollowing = true
                                                        followBusy = false
                                                    },
                                                    onFailure = {
                                                        followBusy = false
                                                        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                                                    },
                                                )
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    if (followBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    } else {
                                        Text(
                                            text = if (userFollowing) "Following" else "Follow",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

// Tunesly Track Row
@Composable
private fun TuneslyTrackRow(
    track: TuneslyCloudTrack,
    onPlay: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onPlay),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                if (track.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// Tunesly Playlist Row
@Composable
private fun TuneslyPlaylistRow(
    playlist: TuneslyPublicPlaylist,
    onClick: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                        modifier = Modifier.size(26.dp),
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
        }
    }
}


@Composable
fun TuneslyAvatarView(
    avatar: String,
    displayName: String,
    username: String,
    modifier: Modifier = Modifier,
    textSize: androidx.compose.ui.unit.TextUnit = 38.sp,
) {
    val bitmap = remember(avatar) {
        if (avatar.startsWith("data:image") && avatar.contains(",")) {
            try {
                val base64 = avatar.substringAfter(",")
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            avatar.isNotBlank() -> {
                AsyncImage(
                    model = avatar,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Text(
                    text = (displayName.ifBlank { username }).take(1).uppercase().ifBlank { "U" },
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

fun formatOnlineStatus(isOnline: Boolean, lastActiveIso: String): Pair<Boolean, String> {
    if (isOnline) {
        return Pair(true, "Online")
    }
    if (lastActiveIso.isBlank()) {
        return Pair(false, "Offline")
    }
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val cleanIso = if (lastActiveIso.contains(".")) lastActiveIso.substringBefore(".") else lastActiveIso.removeSuffix("Z")
        val date = sdf.parse(cleanIso)
        if (date != null) {
            val diffMs = System.currentTimeMillis() - date.time
            val diffMins = diffMs / (60 * 1000)
            val diffHours = diffMs / (60 * 60 * 1000)
            val diffDays = diffMs / (24 * 60 * 60 * 1000)
            when {
                diffMins < 5 -> Pair(true, "Online")
                diffMins < 60 -> Pair(false, "Active ${diffMins}m ago")
                diffHours < 24 -> Pair(false, "Active ${diffHours}h ago")
                diffDays == 1L -> Pair(false, "Active yesterday")
                diffDays < 7 -> Pair(false, "Active ${diffDays}d ago")
                else -> Pair(false, "Offline")
            }
        } else {
            Pair(false, "Offline")
        }
    } catch (_: Exception) {
        Pair(false, "Offline")
    }
}
