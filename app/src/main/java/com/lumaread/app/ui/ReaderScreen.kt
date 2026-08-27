package com.lumaread.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lumaread.app.data.BookItem
import com.lumaread.app.pdf.PdfPageRenderer
import com.lumaread.app.tts.PlaybackState
import com.lumaread.app.tts.ReadAloudService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: BookItem,
    playback: PlaybackState,
    onBack: () -> Unit,
    onBookChanged: (BookItem) -> Unit
) {
    val context = LocalContext.current
    val playbackForBook = playback.active && playback.bookUri == book.uri
    var page by remember(book.id) { mutableIntStateOf(book.lastPage.coerceIn(0, book.totalPages - 1)) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("lumaread_voice", android.content.Context.MODE_PRIVATE) }
    var speed by remember { mutableFloatStateOf(prefs.getFloat("speed", 1.0f)) }
    var voiceMode by remember { mutableStateOf(prefs.getString("voice_mode", ReadAloudService.VOICE_NATURAL) ?: ReadAloudService.VOICE_NATURAL) }

    LaunchedEffect(playback.pageIndex, playback.bookUri, playback.active) {
        if (playbackForBook && playback.pageIndex in 0 until book.totalPages && playback.pageIndex != page) {
            page = playback.pageIndex
            onBookChanged(book.copy(lastPage = page))
        }
    }

    fun changePage(newPage: Int) {
        val safe = newPage.coerceIn(0, book.totalPages - 1)
        if (safe != page) {
            page = safe
            onBookChanged(book.copy(lastPage = safe))
        }
    }

    fun startReading() {
        val intent = Intent(context, ReadAloudService::class.java).apply {
            action = ReadAloudService.ACTION_START
            putExtra(ReadAloudService.EXTRA_URI, book.uri)
            putExtra(ReadAloudService.EXTRA_TITLE, book.title)
            putExtra(ReadAloudService.EXTRA_PAGE, page)
            putExtra(ReadAloudService.EXTRA_TOTAL_PAGES, book.totalPages)
            putExtra(ReadAloudService.EXTRA_SPEED, speed)
            putExtra(ReadAloudService.EXTRA_VOICE_MODE, voiceMode)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun sendAction(action: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ReadAloudService::class.java).setAction(action)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(
                            book.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Page ${page + 1} of ${book.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val nextMarks = if (page in book.bookmarks) book.bookmarks - page else book.bookmarks + page
                        onBookChanged(book.copy(bookmarks = nextMarks, lastPage = page))
                    }) {
                        Icon(
                            if (page in book.bookmarks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark page",
                            tint = if (page in book.bookmarks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showVoiceSettings = true }) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "Voice settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PdfPage(
                uri = book.uri,
                page = page,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            ReaderControls(
                page = page,
                totalPages = book.totalPages,
                playback = if (playbackForBook) playback else PlaybackState(),
                onPageChange = ::changePage,
                onPrevious = {
                    if (playbackForBook) sendAction(ReadAloudService.ACTION_PREVIOUS_PAGE)
                    else changePage(page - 1)
                },
                onNext = {
                    if (playbackForBook) sendAction(ReadAloudService.ACTION_NEXT_PAGE)
                    else changePage(page + 1)
                },
                onReadToggle = {
                    when {
                        !playbackForBook -> startReading()
                        playback.playing -> sendAction(ReadAloudService.ACTION_PAUSE)
                        else -> sendAction(ReadAloudService.ACTION_RESUME)
                    }
                },
                onStop = { sendAction(ReadAloudService.ACTION_STOP) }
            )
        }
    }

    if (showVoiceSettings) {
        VoiceSettingsDialog(
            speed = speed,
            voiceMode = voiceMode,
            onDismiss = { showVoiceSettings = false },
            onSave = { newSpeed, newMode ->
                speed = newSpeed
                voiceMode = newMode
                prefs.edit().putFloat("speed", speed).putString("voice_mode", voiceMode).apply()
                if (playbackForBook) startReading()
                showVoiceSettings = false
            }
        )
    }
}

@Composable
private fun PdfPage(uri: String, page: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scale by remember(page) { mutableFloatStateOf(1f) }
    var offsetX by remember(page) { mutableFloatStateOf(0f) }
    var offsetY by remember(page) { mutableFloatStateOf(0f) }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri, key2 = page) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                PdfPageRenderer.renderPage(context, Uri.parse(uri), page, 1500)
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF page ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        }
    }
}

@Composable
private fun ReaderControls(
    page: Int,
    totalPages: Int,
    playback: PlaybackState,
    onPageChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReadToggle: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (totalPages > 1) {
                Slider(
                    value = page.toFloat(),
                    onValueChange = { onPageChange(it.toInt()) },
                    valueRange = 0f..(totalPages - 1).toFloat(),
                    steps = (totalPages - 2).coerceAtMost(50).coerceAtLeast(0)
                )
            }

            if (playback.active) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (playback.loading) "Preparing voice…" else playback.message,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, enabled = page > 0) {
                    Icon(Icons.Default.NavigateBefore, contentDescription = "Previous page", modifier = Modifier.size(34.dp))
                }

                Button(
                    onClick = onReadToggle,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(
                        if (playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        when {
                            playback.loading -> "Preparing"
                            playback.playing -> "Pause"
                            playback.active -> "Resume"
                            else -> "Read aloud"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (playback.active) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop reading")
                    }
                } else {
                    IconButton(onClick = onNext, enabled = page < totalPages - 1) {
                        Icon(Icons.Default.NavigateNext, contentDescription = "Next page", modifier = Modifier.size(34.dp))
                    }
                }
            }

            if (playback.active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onNext, enabled = page < totalPages - 1) {
                        Text("Skip to next page")
                        Icon(Icons.Default.NavigateNext, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSettingsDialog(
    speed: Float,
    voiceMode: String,
    onDismiss: () -> Unit,
    onSave: (Float, String) -> Unit
) {
    var localSpeed by remember { mutableFloatStateOf(speed) }
    var localMode by remember { mutableStateOf(voiceMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read Aloud voice") },
        text = {
            Column {
                Text(
                    "LumaRead chooses the best matching English or Hindi voice installed on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Text("Voice quality", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = localMode == ReadAloudService.VOICE_NATURAL,
                        onClick = { localMode = ReadAloudService.VOICE_NATURAL },
                        label = { Text("Natural") }
                    )
                    FilterChip(
                        selected = localMode == ReadAloudService.VOICE_OFFLINE,
                        onClick = { localMode = ReadAloudService.VOICE_OFFLINE },
                        label = { Text("Offline") }
                    )
                    FilterChip(
                        selected = localMode == ReadAloudService.VOICE_SYSTEM,
                        onClick = { localMode = ReadAloudService.VOICE_SYSTEM },
                        label = { Text("System") }
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Speed", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${String.format("%.2f", localSpeed)}×", color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = localSpeed,
                    onValueChange = { localSpeed = it },
                    valueRange = 0.65f..1.75f
                )
                Text(
                    if (localMode == ReadAloudService.VOICE_NATURAL)
                        "Natural mode can use a higher-quality network voice when your speech engine provides one."
                    else if (localMode == ReadAloudService.VOICE_OFFLINE)
                        "Offline mode avoids voices that require an internet connection."
                    else
                        "System mode uses your phone's default text-to-speech voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(localSpeed, localMode) }) { Text("Use voice") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
