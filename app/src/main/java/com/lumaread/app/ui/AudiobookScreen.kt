package com.lumaread.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lumaread.app.audio.AudiobookService
import com.lumaread.app.audio.AudiobookState
import com.lumaread.app.data.BookItem

@Composable
fun AudiobookScreen(book: BookItem, state: AudiobookState, onBack: () -> Unit) {
    val context = LocalContext.current
    val isCurrent = state.uri == book.uri
    val position = if (isCurrent) state.positionMs else book.positionMs
    val duration = if (isCurrent && state.durationMs > 0) state.durationMs else book.durationMs
    fun send(action: String, block: Intent.() -> Unit = {}) {
        ContextCompat.startForegroundService(context, Intent(context, AudiobookService::class.java).setAction(action).apply(block))
    }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to library") }
            Text("Now playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(120.dp))
        Spacer(Modifier.height(24.dp))
        Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Slider(
            value = position.toFloat(),
            onValueChange = { if (isCurrent) send(AudiobookService.ACTION_SEEK) { putExtra(AudiobookService.EXTRA_POSITION_MS, it.toLong()) } },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatAudioTime(position)); Text(formatAudioTime(duration))
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (isCurrent) send(AudiobookService.ACTION_TOGGLE)
            else send(AudiobookService.ACTION_PLAY) {
                putExtra(AudiobookService.EXTRA_URI, book.uri); putExtra(AudiobookService.EXTRA_TITLE, book.title)
                putExtra(AudiobookService.EXTRA_POSITION_MS, book.positionMs); putExtra(AudiobookService.EXTRA_SPEED, 1f)
            }
        }) {
            Icon(if (isCurrent && state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            Text(if (isCurrent && state.playing) " Pause" else " Play")
        }
        Spacer(Modifier.height(28.dp))
        Text("Playback speed", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AudiobookService.SPEEDS.forEach { speed ->
                FilterChip(
                    selected = isCurrent && state.speed == speed,
                    enabled = isCurrent,
                    onClick = { send(AudiobookService.ACTION_SPEED) { putExtra(AudiobookService.EXTRA_SPEED, speed) } },
                    label = { Text("${speed}×") }
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun formatAudioTime(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}
