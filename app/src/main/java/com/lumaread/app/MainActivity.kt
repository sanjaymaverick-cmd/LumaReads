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
import com.lumaread.app.audio.AudiobookService
import com.lumaread.app.audio.M4bChapterReader
import com.lumaread.app.backup.BackupArchive
import com.lumaread.app.data.AppSettingsStore
import com.lumaread.app.data.BookAvailability
import com.lumaread.app.data.BookFormat
import com.lumaread.app.data.BookItem
import com.lumaread.app.data.BookStatus
import com.lumaread.app.data.LibraryRepository
import com.lumaread.app.data.MediaType
import com.lumaread.app.format.FormatDetector
import com.lumaread.app.format.PageTextLoader
import com.lumaread.app.io.BookStorage
import com.lumaread.app.tts.ReadAloudService
import com.lumaread.app.ui.AudiobookScreen
import com.lumaread.app.ui.LibraryScreen
import com.lumaread.app.ui.ReaderScreen
import com.lumaread.app.ui.theme.LumaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsStore = remember { AppSettingsStore(this@MainActivity) }
            var settings by remember { mutableStateOf(settingsStore.load()) }
            LumaTheme(settings.theme) {
                val repository = remember { LibraryRepository(this@MainActivity) }
                var books by remember {
                    mutableStateOf(repository.loadBooks().map { book ->
                        book.copy(status = BookAvailability.resolve(this@MainActivity, book))
                    })
                }
                var selectedBookId by remember { mutableStateOf<String?>(null) }
                var pendingImport by remember { mutableStateOf<Uri?>(null) }
                var pendingRestore by remember { mutableStateOf<Uri?>(null) }
                var statusMessage by remember { mutableStateOf("") }
                val playback by ReadAloudService.state.collectAsStateWithLifecycle()
                val audiobook by AudiobookService.state.collectAsStateWithLifecycle()
                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        pendingImport = uri
                    }
                }
                val backupCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                    if (uri != null) {
                        statusMessage = if (BackupArchive.write(this@MainActivity, uri, books.size)) "Backup saved" else "Backup failed"
                    }
                }
                val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) pendingRestore = uri
                }
                LaunchedEffect(pendingRestore) {
                    val uri = pendingRestore ?: return@LaunchedEffect
                    val ok = withContext(Dispatchers.IO) { BackupArchive.restore(this@MainActivity, uri) }
                    if (ok) {
                        books = repository.loadBooks().map { it.copy(status = BookAvailability.resolve(this@MainActivity, it)) }
                        statusMessage = "Library restored"
                    } else statusMessage = "Restore failed"
                    pendingRestore = null
                }
                LaunchedEffect(pendingImport) {
                    val uri = pendingImport ?: return@LaunchedEffect
                    val imported = withContext(Dispatchers.IO) { importBook(uri) }
                    val existing = books.firstOrNull { it.sourceHash.isNotBlank() && it.sourceHash == imported.sourceHash }
                        ?: books.firstOrNull { it.uri == imported.uri }
                    val book = existing?.copy(
                        title = imported.title, totalPages = imported.totalPages, format = imported.format,
                        mediaType = imported.mediaType, chapters = imported.chapters.ifEmpty { existing.chapters },
                        durationMs = imported.durationMs.coerceAtLeast(existing.durationMs), status = imported.status
                    ) ?: imported
                    books = (books.filterNot { it.id == book.id } + book).sortedByDescending { it.addedAt }
                    repository.saveBooks(books)
                    selectedBookId = book.id
                    pendingImport = null
                }
                LaunchedEffect(playback.bookUri, playback.pageIndex, playback.sentenceIndex, playback.active) {
                    if (playback.active && playback.bookUri.isNotBlank()) {
                        val index = books.indexOfFirst { it.uri == playback.bookUri }
                        if (index >= 0) {
                            val current = books[index]
                            if (current.lastPage != playback.pageIndex || current.lastLine != playback.sentenceIndex) {
                                books = books.toMutableList().also {
                                    it[index] = current.copy(lastPage = playback.pageIndex, lastLine = playback.sentenceIndex)
                                }
                                repository.upsert(books[index])
                            }
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
                            repository.upsert(books[index])
                        }
                    }
                }
                val selected = books.firstOrNull { it.id == selectedBookId }
                when {
                    selected == null -> LibraryScreen(
                        books = books,
                        message = statusMessage,
                        onImport = { documentPicker.launch(arrayOf("*/*")) },
                        onBackup = { backupCreator.launch(BackupArchive.suggestedName()) },
                        onRestore = { restorePicker.launch(arrayOf("application/zip", "*/*")) },
                        onOpen = {
                            books = books.map { item -> if (item.id == it.id) item.copy(lastOpenedAt = System.currentTimeMillis()) else item }
                            repository.upsert(books.first { book -> book.id == it.id })
                            selectedBookId = it.id
                        },
                        onFavourite = { book ->
                            books = books.map { item -> if (item.id == book.id) item.copy(favourite = !item.favourite) else item }
                            repository.upsert(books.first { item -> item.id == book.id })
                        },
                        themeMode = settings.theme,
                        onThemeMode = { selectedTheme ->
                            settings = settings.copy(theme = selectedTheme)
                            settingsStore.save(settings)
                        }
                    )
                    selected.mediaType == MediaType.AUDIO -> AudiobookScreen(book = selected, state = audiobook, onBack = { selectedBookId = null })
                    else -> ReaderScreen(
                        book = selected,
                        playback = playback,
                        onBack = { selectedBookId = null },
                        onBookChanged = { updated ->
                            books = books.map { if (it.id == updated.id) updated else it }
                            repository.upsert(updated)
                        }
                    )
                }
            }
        }
    }

    private fun importBook(uri: Uri): BookItem {
        val displayName = getDisplayName(uri)
        val detected = FormatDetector.detect(displayName, contentResolver.getType(uri), BookStorage.readHeader(this, uri))
        val bookId = BookStorage.newBookId()
        val copied = runCatching { BookStorage.copyFromUri(this, uri, bookId, detected.extension) }.getOrNull()
        val storedUri = copied?.let { BookStorage.fileUri(it) } ?: uri.toString()
        val hash = copied?.let { runCatching { BookStorage.sha256(it) }.getOrDefault("") }.orEmpty()
        val copiedOk = copied != null
        if (detected.format == BookFormat.UNSUPPORTED) {
            return BookItem(
                id = bookId, title = displayName.substringBeforeLast('.').ifBlank { "Unsupported file" },
                uri = storedUri, totalPages = 1, mediaType = detected.mediaType, format = detected.format,
                status = BookStatus.UNSUPPORTED, sourceHash = hash, copied = copiedOk
            )
        }
        if (detected.mediaType == MediaType.AUDIO) {
            val metadata = readAudioMetadata(Uri.parse(storedUri))
            return BookItem(
                id = bookId,
                title = metadata.first.ifBlank { displayName.substringBeforeLast('.').ifBlank { "Untitled audiobook" } },
                uri = storedUri, totalPages = 1, mediaType = MediaType.AUDIO, format = detected.format,
                durationMs = metadata.second,
                chapters = if (detected.format == BookFormat.M4B) M4bChapterReader.read(this, Uri.parse(storedUri)) else emptyList(),
                status = if (copiedOk) BookStatus.AVAILABLE else BookStatus.LINKED, sourceHash = hash, copied = copiedOk
            )
        }
        val pages = runCatching { PageTextLoader.pageCount(this, Uri.parse(storedUri), detected.format) }.getOrDefault(1)
        return BookItem(
            id = bookId, title = displayName.substringBeforeLast('.').ifBlank { "Untitled book" },
            uri = storedUri, totalPages = pages.coerceAtLeast(1), mediaType = detected.mediaType, format = detected.format,
            status = if (copiedOk) BookStatus.AVAILABLE else BookStatus.LINKED, sourceHash = hash, copied = copiedOk
        )
    }

    private fun readAudioMetadata(uri: Uri): Pair<String, Long> = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty() to
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
        }
    }.getOrDefault("" to 0L)

    private fun getDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Book.pdf"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name
    }
}
