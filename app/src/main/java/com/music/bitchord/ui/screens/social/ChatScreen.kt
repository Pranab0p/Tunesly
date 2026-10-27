package com.music.bitchord.ui.screens.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.tuneslycloud.TuneslyChatMessage
import com.music.bitchord.data.tuneslycloud.TuneslyCloudManager
import com.music.bitchord.data.tuneslycloud.TuneslyCloudTrack
import com.music.bitchord.data.tuneslycloud.TuneslyPublicPlaylist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    recipientUsername: String,
    currentPlayingSong: Song?,
    onOpenProfile: (String) -> Unit,
    onPlayTrack: (Song) -> Unit,
    onOpenPlaylist: ((String) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val messages = remember { mutableStateListOf<TuneslyChatMessage>() }
    var textInput by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var attachedSong by remember { mutableStateOf<Song?>(null) }
    var attachedPlaylist by remember { mutableStateOf<TuneslyPublicPlaylist?>(null) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<TuneslyPublicPlaylist>>(emptyList()) }
    var loadingPlaylists by remember { mutableStateOf(false) }
    var recipientAvatar by remember { mutableStateOf("") }
    var recipientDisplayName by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Fetch recipient profile for avatar & display name
    LaunchedEffect(recipientUsername) {
        TuneslyCloudManager.getUserProfile(recipientUsername).onSuccess { prof ->
            recipientAvatar = prof.avatar
            recipientDisplayName = prof.displayName
        }
    }

    // Poll chat thread every 3.5 seconds
    LaunchedEffect(recipientUsername) {
        while (true) {
            TuneslyCloudManager.getChatThread(recipientUsername).onSuccess { list ->
                val hasPendingOptimistic = messages.any { it.id.startsWith("temp_") }
                if (!hasPendingOptimistic && (list.size != messages.size || list.lastOrNull()?.id != messages.lastOrNull()?.id)) {
                    messages.clear()
                    messages.addAll(list)
                    if (list.isNotEmpty()) {
                        listState.animateScrollToItem(list.lastIndex)
                    }
                }
            }
            delay(3500)
        }
    }

    val onSend = {
        val trimmed = textInput.trim()
        val toAttachSong = attachedSong
        val toAttachPlaylist = attachedPlaylist
        if (trimmed.isNotBlank() || toAttachSong != null || toAttachPlaylist != null) {
            val cloudTrack = toAttachSong?.let {
                TuneslyCloudTrack(
                    trackId = it.videoId,
                    title = it.title,
                    artist = it.artist,
                    artworkUrl = it.thumbnailUrl.orEmpty(),
                )
            }

            val tempId = "temp_${System.currentTimeMillis()}"
            val optimisticMsg = TuneslyChatMessage(
                id = tempId,
                isMine = true,
                text = trimmed,
                trackAttachment = cloudTrack,
                playlistAttachment = toAttachPlaylist,
                isEncrypted = true,
                read = false,
                createdAt = "Just now",
            )

            // Instant optimistic send: show bubble immediately, clear input, scroll down
            messages.add(optimisticMsg)
            textInput = ""
            attachedSong = null
            attachedPlaylist = null
            sending = true

            scope.launch {
                listState.animateScrollToItem(messages.lastIndex)
                TuneslyCloudManager.sendMessage(
                    recipientUsername = recipientUsername,
                    text = trimmed,
                    attachedTrack = cloudTrack,
                    attachedPlaylist = toAttachPlaylist,
                ).onSuccess { sentMsg ->
                    val idx = messages.indexOfFirst { it.id == tempId }
                    if (idx != -1) {
                        messages[idx] = sentMsg
                    } else {
                        messages.add(sentMsg)
                    }
                    sending = false
                }.onFailure {
                    // Revert on error
                    messages.removeAll { it.id == tempId }
                    textInput = trimmed
                    attachedSong = toAttachSong
                    attachedPlaylist = toAttachPlaylist
                    sending = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        // Chat Recipient Sub-Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenProfile(recipientUsername) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    TuneslyAvatarView(
                        avatar = recipientAvatar,
                        displayName = recipientDisplayName,
                        username = recipientUsername,
                        modifier = Modifier.fillMaxSize(),
                        textSize = 17.sp,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = recipientDisplayName.ifBlank { "@$recipientUsername" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (recipientUsername.equals("pranab", ignoreCase = true)) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Official Developer",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = if (recipientDisplayName.isNotBlank()) "@$recipientUsername • Tap to view profile" else "Tap to view profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }

        // End-to-End Encryption Security Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(vertical = 6.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "End-to-end encrypted • Private to this chat",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }

        // Messages Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    onPlayTrack = onPlayTrack,
                    onOpenPlaylist = onOpenPlaylist,
                )
            }
        }

        // Attached Song Chip Preview
        attachedSong?.let { song ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Song: ${song.title} • ${song.artist}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { attachedSong = null },
                    )
                }
            }
        }

        // Attached Playlist Chip Preview
        attachedPlaylist?.let { pl ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Playlist: ${pl.name} (${pl.trackCount} tracks)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { attachedPlaylist = null },
                    )
                }
            }
        }

        // Input Field & Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attach Music or Playlist Button
                IconButton(
                    onClick = {
                        showAttachmentDialog = true
                        if (userPlaylists.isEmpty() && !loadingPlaylists) {
                            val myUser = AppSettings.tuneslyUsername.value
                            if (myUser.isNotBlank()) {
                                loadingPlaylists = true
                                scope.launch {
                                    TuneslyCloudManager.getUserProfile(myUser).onSuccess { prof ->
                                        userPlaylists = prof.playlists
                                    }
                                    loadingPlaylists = false
                                }
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (attachedSong != null || attachedPlaylist != null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = if (attachedPlaylist != null) Icons.Rounded.PlaylistPlay else Icons.Rounded.Add,
                        contentDescription = "Attach song or playlist",
                        tint = if (attachedSong != null || attachedPlaylist != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = "Message (encrypted)...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = onSend,
                    enabled = !sending && (textInput.isNotBlank() || attachedSong != null || attachedPlaylist != null),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.size(42.dp),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (textInput.isNotBlank() || attachedSong != null || attachedPlaylist != null) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    // Attachment Chooser Dialog (Song or Playlist)
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = {
                Text(
                    text = "Share Music",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option 1: Current Playing Song
                    if (currentPlayingSong != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    attachedSong = currentPlayingSong
                                    attachedPlaylist = null
                                    showAttachmentDialog = false
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Current Playing Song",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        text = "${currentPlayingSong.title} • ${currentPlayingSong.artist}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Option 2: Share Playlist
                    Text(
                        text = "Share a Playlist",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )

                    if (loadingPlaylists) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (userPlaylists.isEmpty()) {
                        Text(
                            text = "No public playlists found on profile yet. Create a playlist to share!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            userPlaylists.take(5).forEach { pl ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            attachedPlaylist = pl
                                            attachedSong = null
                                            showAttachmentDialog = false
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${pl.trackCount} tracks",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(
    message: TuneslyChatMessage,
    onPlayTrack: (Song) -> Unit,
    onOpenPlaylist: ((String) -> Unit)? = null,
) {
    val isMine = message.isMine
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Attached Track Card
                message.trackAttachment?.let { track ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMine) MaterialTheme.colorScheme.surface.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlayTrack(
                                    Song(
                                        videoId = track.trackId,
                                        title = track.title,
                                        artist = track.artist,
                                        thumbnailUrl = track.artworkUrl,
                                        durationText = null,
                                        fromAutoplay = false,
                                    ),
                                )
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = track.artworkUrl.ifBlank { "https://i.ytimg.com/vi/${track.trackId}/hqdefault.jpg" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = contentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (message.text.isNotBlank() || message.playlistAttachment != null) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Attached Playlist Card
                message.playlistAttachment?.let { pl ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMine) MaterialTheme.colorScheme.surface.copy(alpha = 0.25f) else MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenPlaylist?.invoke(pl.id)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${pl.trackCount} tracks • Shared Playlist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Open",
                                tint = contentColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Text Message
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }

                // Time & Status Row (Lock for E2EE + Read receipts)
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (message.isEncrypted) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Encrypted",
                            tint = contentColor.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(11.dp)
                                .padding(end = 4.dp),
                        )
                    }

                    if (isMine) {
                        Icon(
                            imageVector = if (message.read) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                            contentDescription = if (message.read) "Read" else "Sent",
                            tint = contentColor.copy(alpha = 0.75f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
