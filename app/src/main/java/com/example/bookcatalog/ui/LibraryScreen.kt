package com.example.bookcatalog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bookcatalog.data.db.Book
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LibraryScreen(
    viewModel: LibraryViewModel,
    coverDirectory: File,
    onAddBook: () -> Unit,
    onScanBook: () -> Unit,
    onLookupBook: () -> Unit,
    onEditBook: (Long) -> Unit,
) {
    val books by viewModel.books.collectAsState()
    val scope = rememberCoroutineScope()
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Book Catalog") })
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenuExpanded = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add book")
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Scan barcode") },
                        onClick = {
                            addMenuExpanded = false
                            onScanBook()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add manually") },
                        onClick = {
                            addMenuExpanded = false
                            onAddBook()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Enter ISBN") },
                        onClick = {
                            addMenuExpanded = false
                            onLookupBook()
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Your library is empty",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "Add a book to start your collection.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                items(books, key = { it.id }) { book ->
                    BookListItem(
                        book = book,
                        coverDirectory = coverDirectory,
                        onClick = { onEditBook(book.id) },
                        onEdit = { onEditBook(book.id) },
                        onDelete = { bookToDelete = book },
                    )
                }
            }
        }
    }

    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Delete book?") },
            text = { Text("“${book.title}” will be removed from your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { viewModel.delete(book) }
                        bookToDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BookListItem(
    book: Book,
    coverDirectory: File,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (book.coverFile != null) {
                AsyncImage(
                    model = File(coverDirectory, book.coverFile),
                    contentDescription = "Cover of ${book.title}",
                    modifier = Modifier
                        .width(48.dp)
                        .height(64.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        headlineContent = {
            Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val details = listOfNotNull(
                book.authors?.takeIf(String::isNotBlank),
                book.publishedDate?.takeIf(String::isNotBlank),
            )
            Text(details.joinToString(" · ").ifBlank { "No additional details" })
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Book actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}
