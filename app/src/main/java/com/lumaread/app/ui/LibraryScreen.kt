package com.lumaread.app.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumaread.app.R
import com.lumaread.app.data.*
import com.lumaread.app.pdf.PdfPageRenderer
import com.lumaread.app.ui.theme.LumaThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    books: List<BookItem>,
    onImport: () -> Unit,
    onOpen: (BookItem) -> Unit,
    onFavourite: (BookItem) -> Unit,
    themeMode: LumaThemeMode,
    onThemeMode: (LumaThemeMode) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var sort by remember { mutableStateOf(LibrarySort.RECENT) }
    var showSort by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    val visible = remember(books, query, filter, sort) { LibraryRules.visibleBooks(books, query, filter, sort) }
    val continuing = remember(books) { LibraryRules.continueReading(books) }
    val recent = remember(books) { books.sortedByDescending { it.addedAt }.take(6) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp, 22.dp, 12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.lumaread_firefly), "LumaRead firefly", Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("LumaRead", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("A quiet place for your books", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showTheme = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.DarkMode, "Reading theme") }
                    DropdownMenu(showTheme, { showTheme = false }) {
                        LumaThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }, onClick = { onThemeMode(mode); showTheme = false })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search your library") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp)
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(20.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }) }
                Box {
                    FilterChip(selected = false, onClick = { showSort = true }, leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(18.dp)) }, label = { Text(if (sort == LibrarySort.RECENT) "Recent" else "Title") })
                    DropdownMenu(showSort, { showSort = false }) {
                        DropdownMenuItem({ Text("Recently opened") }, onClick = { sort = LibrarySort.RECENT; showSort = false })
                        DropdownMenuItem({ Text("Title A–Z") }, onClick = { sort = LibrarySort.TITLE; showSort = false })
                    }
                }
            }
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add book or audiobook")
            }
        }

        if (query.isBlank() && filter == LibraryFilter.ALL && continuing.isNotEmpty()) {
            item { SectionTitle("Continue Reading") }
            item { BookRail(continuing, onOpen, onFavourite) }
        }
        if (query.isBlank() && filter == LibraryFilter.ALL && recent.isNotEmpty()) {
            item { SectionTitle("Recently Added") }
            item { BookRail(recent, onOpen, onFavourite) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp, 24.dp, 20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (query.isNotBlank()) "Search results" else filter.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${visible.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (visible.isEmpty()) item { EmptyLibrary(books.isNotEmpty(), onImport) }
        else items(visible, key = { it.id }) { book -> LibraryRow(book, { onOpen(book) }, { onFavourite(book) }) }
    }
}

private val LibraryFilter.label: String get() = when (this) {
    LibraryFilter.ALL -> "All"
    LibraryFilter.BOOKS -> "Books"
    LibraryFilter.AUDIOBOOKS -> "Audiobooks"
    LibraryFilter.FAVOURITES -> "Favourites"
    LibraryFilter.MISSING -> "Missing"
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(20.dp, 24.dp, 20.dp, 10.dp))
}

@Composable private fun BookRail(books: List<BookItem>, onOpen: (BookItem) -> Unit, onFavourite: (BookItem) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(books, key = { it.id }) { book -> CompactBookCard(book, { onOpen(book) }, { onFavourite(book) }) }
    }
}

@Composable private fun CompactBookCard(book: BookItem, onOpen: () -> Unit, onFavourite: () -> Unit) {
    Card(Modifier.width(164.dp).clickable(onClick = onOpen), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Cover(book, Modifier.fillMaxWidth().height(142.dp))
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FavouriteButton(book, onFavourite)
            }
            Progress(book)
        }
    }
}

@Composable private fun LibraryRow(book: BookItem, onOpen: () -> Unit, onFavourite: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable(onClick = onOpen), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp)); Progress(book)
            }
            FavouriteButton(book, onFavourite)
        }
    }
}

@Composable private fun Cover(book: BookItem, modifier: Modifier) {
    val context = LocalContext.current
    val cover by produceState<android.graphics.Bitmap?>(null, book.uri) {
        value = withContext(Dispatchers.IO) { if (book.mediaType == MediaType.PDF) runCatching { PdfPageRenderer.renderPage(context, Uri.parse(book.uri), 0, 500) }.getOrNull() else null }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when {
            book.mediaType == MediaType.AUDIO -> Icon(Icons.Default.Headphones, "Audiobook", Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            cover == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else -> Image(cover!!.asImageBitmap(), "Cover of ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable private fun FavouriteButton(book: BookItem, onFavourite: () -> Unit) {
    IconButton(onClick = onFavourite, modifier = Modifier.size(48.dp)) {
        Icon(if (book.favourite) Icons.Default.Star else Icons.Outlined.StarBorder, if (book.favourite) "Remove favourite" else "Add favourite", tint = if (book.favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Progress(book: BookItem) {
    val percent = (book.progress * 100).toInt()
    Text(if (percent == 0) "Not started" else "$percent% complete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(5.dp))
    LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)))
}

@Composable private fun EmptyLibrary(hasBooks: Boolean, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(40.dp, 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (hasBooks) Icons.Default.Search else Icons.Default.AutoStories, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text(if (hasBooks) "Nothing matches" else "Your shelf is ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(if (hasBooks) "Try another search or library filter." else "Add a PDF or audiobook. Your files stay where they are and remain available offline.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        if (!hasBooks) Button(onClick = onImport, modifier = Modifier.padding(top = 20.dp).height(48.dp)) { Text("Choose a file") }
    }
}
