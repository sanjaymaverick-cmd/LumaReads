package com.lumaread.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var seekPosition by remember(position) { mutableFloatStateOf(position.toFloat()) }
    var showSleep by remember { mutableStateOf(false) }
    fun send(action: String, block: Intent.() -> Unit = {}) = ContextCompat.startForegroundService(
        context, Intent(context, AudiobookService::class.java).setAction(action).apply(block)
    )
    fun play() = send(AudiobookService.ACTION_PLAY) {
        putExtra(AudiobookService.EXTRA_URI, book.uri); putExtra(AudiobookService.EXTRA_TITLE, book.title)
        putExtra(AudiobookService.EXTRA_POSITION_MS, book.positionMs); putExtra(AudiobookService.EXTRA_SPEED, book.playbackSpeed)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowBack, "Back to library") }
                Text("Audiobook", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showSleep = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Bedtime, "Sleep timer") }
                    DropdownMenu(showSleep, { showSleep = false }) {
                        listOf(0L to "Off", 15L to "15 minutes", 30L to "30 minutes", 45L to "45 minutes", 60L to "1 hour").forEach { (minutes, label) ->
                            DropdownMenuItem({ Text(label) }, onClick = { send(AudiobookService.ACTION_SLEEP) { putExtra(AudiobookService.EXTRA_SLEEP_MS, minutes * 60_000L) }; showSleep = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(38.dp))
            Surface(Modifier.size(196.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Headphones, null, Modifier.size(92.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(28.dp))
            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (isCurrent && state.sleepRemainingMs > 0L) Text("Sleep timer · ${formatAudioTime(state.sleepRemainingMs)}", color = MaterialTheme.colorScheme.primary)
            state.error?.takeIf { isCurrent }?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(24.dp))
            Slider(
                value = seekPosition.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { seekPosition = it },
                onValueChangeFinished = {
                    if (isCurrent) send(AudiobookService.ACTION_SEEK) { putExtra(AudiobookService.EXTRA_POSITION_MS, seekPosition.toLong()) }
                    else play()
                }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatAudioTime(position)); Text("−${formatAudioTime((duration - position).coerceAtLeast(0L))}")
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (isCurrent) send(AudiobookService.ACTION_SKIP_BACK) else play() }, modifier = Modifier.size(56.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Replay, "Back 15 seconds"); Text("15", style = MaterialTheme.typography.labelSmall) }
                }
                FilledIconButton(onClick = { if (isCurrent) send(AudiobookService.ACTION_TOGGLE) else play() }, modifier = Modifier.size(76.dp), shape = CircleShape) {
                    if (isCurrent && state.loading) CircularProgressIndicator(Modifier.size(30.dp)) else Icon(if (isCurrent && state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (isCurrent && state.playing) "Pause" else "Play", Modifier.size(42.dp))
                }
                IconButton(onClick = { if (isCurrent) send(AudiobookService.ACTION_SKIP_FORWARD) else play() }, modifier = Modifier.size(56.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Forward10, "Forward 15 seconds"); Text("15", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Playback speed", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AudiobookService.SPEEDS.forEach { speed ->
                    FilterChip(selected = (if (isCurrent) state.speed else book.playbackSpeed) == speed, onClick = {
                        if (!isCurrent) play()
                        send(AudiobookService.ACTION_SPEED) { putExtra(AudiobookService.EXTRA_SPEED, speed) }
                    }, label = { Text("${speedLabel(speed)}×") })
                }
            }
        }
        if (book.chapters.isNotEmpty()) {
            item { Text("Chapters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 8.dp)) }
            itemsIndexed(book.chapters) { index, chapter ->
                val next = book.chapters.getOrNull(index + 1)?.startMs ?: duration
                val active = position in chapter.startMs until next.coerceAtLeast(chapter.startMs + 1)
                Surface(Modifier.fillMaxWidth().clickable { if (!isCurrent) play(); send(AudiobookService.ACTION_SEEK) { putExtra(AudiobookService.EXTRA_POSITION_MS, chapter.startMs) } }, color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                        Text(chapter.title, Modifier.weight(1f)); Text(formatAudioTime(chapter.startMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else item { Text("Chapter metadata is not available for this file.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 30.dp)) }
    }
}

private fun speedLabel(speed: Float) = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
internal fun formatAudioTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1_000
    return if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
