package com.example.bookcatalog.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.domain.SortOrder
import java.io.File
import java.time.LocalDate

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LibraryScreen(
    viewModel: LibraryViewModel,
    coverDirectory: File,
    onAddBook: () -> Unit,
    onScanBook: () -> Unit,
    onLookupBook: () -> Unit,
    onEditBook: (Long) -> Unit,
    onImportExport: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val controls = state.controls
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportSelected(it, zip = true) }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportSelected(it, zip = false) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    BackHandler(enabled = controls.selecting, onBack = viewModel::stopSelecting)

    fun onBookClick(book: Book) {
        if (controls.selecting) viewModel.toggleSelected(book.id) else onEditBook(book.id)
    }

    fun onBookLongClick(book: Book) {
        if (controls.selecting) viewModel.toggleSelected(book.id) else viewModel.startSelecting(book.id)
    }

    Scaffold(
        topBar = {
            if (controls.selecting) {
                SelectionTopBar(
                    selectedCount = controls.selectedIds.size,
                    onClose = viewModel::stopSelecting,
                    onSelectAll = viewModel::selectAllShown,
                    onDeselectAll = viewModel::deselectAll,
                    onExportZip = { exportZip.launch("book-catalog-${LocalDate.now()}.zip") },
                    onExportCsv = { exportCsv.launch("book-catalog-${LocalDate.now()}.csv") },
                    onDelete = { confirmDelete = true },
                )
            } else {
                LibraryTopBar(
                    sort = controls.sort,
                    grid = controls.grid,
                    canSelect = !state.libraryIsEmpty,
                    onSort = viewModel::setSort,
                    onToggleGrid = viewModel::toggleGrid,
                    onSelect = { viewModel.startSelecting() },
                    onImportExport = onImportExport,
                )
            }
        },
        floatingActionButton = {
            if (!controls.selecting) AddBookButton(onScanBook, onAddBook, onLookupBook)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (!state.libraryIsEmpty) {
                SearchField(controls.query, viewModel::setQuery)
            }
            when {
                state.libraryIsEmpty -> EmptyMessage("Your library is empty", "Add a book to start your collection.")
                state.books.isEmpty() -> EmptyMessage("No matching books", "Try a different title, author, ISBN or publisher.")
                controls.grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookGridItem(
                            book = book,
                            coverDirectory = coverDirectory,
                            selected = book.id in controls.selectedIds,
                            modifier = Modifier.combinedClickable(
                                onClick = { onBookClick(book) },
                                onLongClick = { onBookLongClick(book) },
                            ),
                        )
                    }
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.books, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            coverDirectory = coverDirectory,
                            selecting = controls.selecting,
                            selected = book.id in controls.selectedIds,
                            modifier = Modifier.combinedClickable(
                                onClick = { onBookClick(book) },
                                onLongClick = { onBookLongClick(book) },
                            ),
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val count = controls.selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (count == 1) "Delete 1 book?" else "Delete $count books?") },
            text = { Text("They will be removed from your library, along with their covers.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelected()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LibraryTopBar(
    sort: SortOrder,
    grid: Boolean,
    canSelect: Boolean,
    onSort: (SortOrder) -> Unit,
    onToggleGrid: () -> Unit,
    onSelect: () -> Unit,
    onImportExport: () -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("Book Catalog") },
        actions = {
            Box {
                IconButton(onClick = { sortExpanded = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort by ${sort.label.lowercase()}")
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    SortOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                            onClick = {
                                sortExpanded = false
                                onSort(option)
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onToggleGrid) {
                if (grid) {
                    Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Show as list")
                } else {
                    Icon(Icons.Default.GridView, contentDescription = "Show as grid")
                }
            }
            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                    if (canSelect) {
                        DropdownMenuItem(
                            text = { Text("Select books") },
                            onClick = {
                                overflowExpanded = false
                                onSelect()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Import / Export") },
                        onClick = {
                            overflowExpanded = false
                            onImportExport()
                        },
                    )
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExportZip: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit,
) {
    var exportExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Stop selecting")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all shown")
            }
            IconButton(onClick = onDeselectAll, enabled = selectedCount > 0) {
                Icon(Icons.Default.Deselect, contentDescription = "Deselect all")
            }
            Box {
                IconButton(onClick = { exportExpanded = true }, enabled = selectedCount > 0) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "Export selected")
                }
                DropdownMenu(expanded = exportExpanded, onDismissRequest = { exportExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Export ZIP backup") },
                        onClick = {
                            exportExpanded = false
                            onExportZip()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export CSV only") },
                        onClick = {
                            exportExpanded = false
                            onExportCsv()
                        },
                    )
                }
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
            }
        },
    )
}

@Composable
private fun AddBookButton(onScanBook: () -> Unit, onAddBook: () -> Unit, onLookupBook: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = "Add book")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                "Scan barcode" to onScanBook,
                "Add manually" to onAddBook,
                "Enter ISBN" to onLookupBook,
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search your library") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun EmptyMessage(title: String, detail: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                detail,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BookListItem(
    book: Book,
    coverDirectory: File,
    selecting: Boolean,
    selected: Boolean,
    modifier: Modifier,
) {
    ListItem(
        modifier = modifier,
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
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
                Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
        trailingContent = if (selecting) {
            { Checkbox(checked = selected, onCheckedChange = null) }
        } else {
            null
        },
    )
}

@Composable
private fun BookGridItem(
    book: Book,
    coverDirectory: File,
    selected: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
    ) {
        if (book.coverFile != null) {
            AsyncImage(
                model = File(coverDirectory, book.coverFile),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
            )
        }
    }
}
