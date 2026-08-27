package com.lumaread.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumaread.app.data.BookItem
import com.lumaread.app.data.MediaType
import com.lumaread.app.audio.AudiobookService
import com.lumaread.app.ui.AudiobookScreen
import com.lumaread.app.data.LibraryRepository
import com.lumaread.app.pdf.PdfPageRenderer
import com.lumaread.app.tts.ReadAloudService
import com.lumaread.app.ui.LibraryScreen
import com.lumaread.app.ui.ReaderScreen
import com.lumaread.app.ui.theme.LumaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LumaTheme {
                val repository = remember { LibraryRepository(this@MainActivity) }
                var books by remember { mutableStateOf(repository.loadBooks()) }
                var selectedBookId by remember { mutableStateOf<String?>(null) }
                val playback by ReadAloudService.state.collectAsStateWithLifecycle()
                val audiobook by AudiobookService.state.collectAsStateWithLifecycle()

                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val documentPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        val displayName = getDisplayName(uri)
                        val isAudio = contentResolver.getType(uri)?.startsWith("audio/") == true ||
                            displayName.endsWith(".mp3", true) || displayName.endsWith(".m4b", true) || displayName.endsWith(".m4a", true)
                        val pageCount = if (isAudio) 1 else runCatching { PdfPageRenderer.pageCount(this@MainActivity, uri) }.getOrDefault(1)
                        val audioMetadata = if (isAudio) readAudioMetadata(uri) else null
                        val title = audioMetadata?.first?.ifBlank { null }
                            ?: displayName.substringBeforeLast('.').ifBlank { if (isAudio) "Untitled audiobook" else "Untitled book" }
                        val existing = books.firstOrNull { it.uri == uri.toString() }
                        val book = existing ?: BookItem(
                            id = uri.toString(),
                            title = title,
                            uri = uri.toString(),
                            totalPages = pageCount,
                            mediaType = if (isAudio) MediaType.AUDIO else MediaType.PDF,
                            durationMs = audioMetadata?.second ?: 0L
                        )
                        books = (books.filterNot { it.id == book.id } + book).sortedByDescending { it.addedAt }
                        repository.saveBooks(books)
                        selectedBookId = book.id
                    }
                }

                LaunchedEffect(playback.bookUri, playback.pageIndex, playback.active) {
                    if (playback.active && playback.bookUri.isNotBlank()) {
                        val index = books.indexOfFirst { it.uri == playback.bookUri }
                        if (index >= 0 && books[index].lastPage != playback.pageIndex) {
                            books = books.toMutableList().also { list ->
                                list[index] = list[index].copy(lastPage = playback.pageIndex)
                            }
                            repository.saveBooks(books)
                        }
                    }
                }

                LaunchedEffect(audiobook.uri, audiobook.positionMs / 5_000L, audiobook.durationMs) {
                    if (audiobook.uri.isNotBlank()) {
                        val index = books.indexOfFirst { it.uri == audiobook.uri }
                        if (index >= 0) {
                            books = books.toMutableList().also { list ->
                                list[index] = list[index].copy(
                                    positionMs = audiobook.positionMs,
                                    durationMs = audiobook.durationMs.coerceAtLeast(list[index].durationMs),
                                    lastOpenedAt = System.currentTimeMillis()
                                )
                            }
                            repository.saveBooks(books)
                        }
                    }
                }

                val selected = books.firstOrNull { it.id == selectedBookId }
                if (selected == null) {
                    LibraryScreen(
                        books = books,
                        playback = playback,
                        onImport = { documentPicker.launch(arrayOf("application/pdf", "audio/mpeg", "audio/mp4", "audio/x-m4b", "audio/*")) },
                        onOpen = {
                            books = books.map { item -> if (item.id == it.id) item.copy(lastOpenedAt = System.currentTimeMillis()) else item }
                            repository.saveBooks(books)
                            selectedBookId = it.id
                        },
                        onRemove = { book ->
                            books = books.filterNot { it.id == book.id }
                            repository.saveBooks(books)
                        }
                    )
                } else if (selected.mediaType == MediaType.AUDIO) {
                    AudiobookScreen(book = selected, state = audiobook, onBack = { selectedBookId = null })
                } else {
                    ReaderScreen(
                        book = selected,
                        playback = playback,
                        onBack = { selectedBookId = null },
                        onBookChanged = { updated ->
                            books = books.map { if (it.id == updated.id) updated else it }
                            repository.saveBooks(books)
                        }
                    )
                }
            }
        }
    }

    private fun readAudioMetadata(uri: Uri): Pair<String, Long> = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(this, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            title to duration
        }
    }.getOrDefault("" to 0L)

    private fun getDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Book.pdf"
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index) ?: name
            }
        }
        return name
    }
}
