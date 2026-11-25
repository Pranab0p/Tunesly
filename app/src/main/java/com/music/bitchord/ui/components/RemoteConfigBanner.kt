package com.music.bitchord.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.RemoteConfig

/**
 * Floating server-driven announcement / maintenance banner controlled via
 * `https://tunesly.online/api/app/config` without requiring an APK update.
 */
@Composable
fun RemoteConfigBanner(
    config: RemoteConfig,
    dismissedAt: String,
    hasDynamicIslandVisible: Boolean,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stamp = config.updatedAt.ifBlank { config.announcementMessage }
    val showAnnouncement = config.announcementEnabled &&
        config.announcementMessage.isNotBlank() &&
        dismissedAt != stamp
    val showMaintenance = config.maintenanceMode && config.maintenanceMessage.isNotBlank()
    val visible = showMaintenance || showAnnouncement

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
            .statusBarsPadding()
            .padding(
                top = if (hasDynamicIslandVisible) 48.dp else 10.dp,
                start = 16.dp,
                end = 16.dp,
            ),
    ) {
        val isMaintenance = showMaintenance
        val borderColor = if (isMaintenance) Color(0xFFEF4444).copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        val bgColor = if (isMaintenance) Color(0xFF1F0D10).copy(alpha = 0.95f)
        else Color(0xFF111119).copy(alpha = 0.95f)

        val title = if (isMaintenance) "Service Notice"
        else config.announcementTitle.ifBlank { "Tunesly Announcement" }
        val message = if (isMaintenance) config.maintenanceMessage else config.announcementMessage
        val actionUrl = if (!isMaintenance) config.announcementActionUrl else ""

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = bgColor,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .then(
                    if (actionUrl.isNotBlank()) {
                        Modifier.clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)))
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isMaintenance) Icons.Rounded.Warning else Icons.Rounded.Campaign,
                    contentDescription = null,
                    tint = if (isMaintenance) Color(0xFFF87171) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = message,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isMaintenance) {
                    IconButton(
                        onClick = { onDismiss(stamp) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
