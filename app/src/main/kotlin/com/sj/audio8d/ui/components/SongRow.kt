package com.sj.audio8d.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sj.audio8d.data.Song
import com.sj.audio8d.ui.theme.CardBackground
import com.sj.audio8d.ui.theme.CyanAccent
import com.sj.audio8d.ui.theme.TextSecondary
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds)
    val seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun SongRow(
    song: Song,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onFavoriteClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = CyanAccent)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    song.artist,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(formatDuration(song.durationMs), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        if (onFavoriteClick != null) {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) CyanAccent else TextSecondary
                )
            }
        }
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
            }
        }
    }
}
