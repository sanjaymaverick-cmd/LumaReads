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
import com.lumaread.app.audio.M4bChapterReader
import com.lumaread.app.ui.AudiobookScreen
import com.lumaread.app.data.LibraryRepository
import com.lumaread.app.pdf.PdfPageRenderer
import com.lumaread.app.tts.ReadAloudService
import com.lumaread.app.ui.LibraryScreen
import com.lumaread.app.ui.ReaderScreen
import com.lumaread.app.ui.theme.LumaTheme
import com.lumaread.app.ui.theme.LumaThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appearance = remember { getSharedPreferences("lumaread_appearance", MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(runCatching { LumaThemeMode.valueOf(appearance.getString("theme", null) ?: "PAPER") }.getOrDefault(LumaThemeMode.PAPER))
            }
            LumaTheme(themeMode) {
                val repository = remember { LibraryRepository(this@MainActivity) }
                var books by remember { mutableStateOf(repository.loadBooks()) }
                var selectedBookId by remember { mutableStateOf<String?>(null) }
                var pendingImport by remember { mutableStateOf<Uri?>(null) }
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
                        pendingImport = uri
                    }
                }

                LaunchedEffect(pendingImport) {
                    val uri = pendingImport ?: return@LaunchedEffect
                    val imported = withContext(Dispatchers.IO) {
                        val displayName = getDisplayName(uri)
                        val isAudio = contentResolver.getType(uri)?.startsWith("audio/") == true ||
                            listOf(".mp3", ".m4b", ".m4a").any { displayName.endsWith(it, true) }
                        val pageCount = if (isAudio) 1 else runCatching { PdfPageRenderer.pageCount(this@MainActivity, uri) }.getOrDefault(1)
                        val audioMetadata = if (isAudio) readAudioMetadata(uri) else null
                        val title = audioMetadata?.first?.ifBlank { null }
                            ?: displayName.substringBeforeLast('.').ifBlank { if (isAudio) "Untitled audiobook" else "Untitled book" }
                        BookItem(
                            id = uri.toString(), title = title, uri = uri.toString(), totalPages = pageCount,
                            mediaType = if (isAudio) MediaType.AUDIO else MediaType.PDF,
                            durationMs = audioMetadata?.second ?: 0L,
                            chapters = if (isAudio && displayName.endsWith(".m4b", true)) M4bChapterReader.read(this@MainActivity, uri) else emptyList()
                        )
                    }
                    val existing = books.firstOrNull { it.uri == uri.toString() }
                    val book = existing ?: imported
                    books = (books.filterNot { it.id == book.id } + book).sortedByDescending { it.addedAt }
                    repository.saveBooks(books)
                    selectedBookId = book.id
                    pendingImport = null
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

                LaunchedEffect(audiobook.uri, audiobook.positionMs / 5_000L, audiobook.durationMs, audiobook.speed) {
                    if (audiobook.uri.isNotBlank()) {
                        val index = books.indexOfFirst { it.uri == audiobook.uri }
                        if (index >= 0) {
                            books = books.toMutableList().also { list ->
                                list[index] = list[index].copy(
                                    positionMs = audiobook.positionMs,
                                    durationMs = audiobook.durationMs.coerceAtLeast(list[index].durationMs),
                                    lastOpenedAt = System.currentTimeMillis(),
                                    playbackSpeed = audiobook.speed
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
                        onImport = { documentPicker.launch(arrayOf("application/pdf", "audio/mpeg", "audio/mp4", "audio/x-m4b", "audio/*")) },
                        onOpen = {
                            books = books.map { item -> if (item.id == it.id) item.copy(lastOpenedAt = System.currentTimeMillis()) else item }
                            repository.saveBooks(books)
                            selectedBookId = it.id
                        },
                        onFavourite = { book ->
                            books = books.map { item -> if (item.id == book.id) item.copy(favourite = !item.favourite) else item }
                            repository.saveBooks(books)
                        },
                        themeMode = themeMode,
                        onThemeMode = { selectedTheme ->
                            themeMode = selectedTheme
                            appearance.edit().putString("theme", selectedTheme.name).apply()
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
