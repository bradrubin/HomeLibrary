package com.example.bookcatalog.ui

import android.graphics.Bitmap
import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.data.covers.CoverBoundaryDetector
import com.example.bookcatalog.data.covers.NormalizedCrop
import com.example.bookcatalog.domain.TitleCase
import com.squareup.moshi.JsonDataException
import android.database.sqlite.SQLiteConstraintException
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID

private data class BookDraft(
    val isbn13: String = "",
    val isbn10: String = "",
    val title: String = "",
    val subtitle: String = "",
    val authors: String = "",
    val publisher: String = "",
    val publishedDate: String = "",
    val pageCount: String = "",
    val language: String = "",
    val subjects: String = "",
    val description: String = "",
    val location: String = "",
    val notes: String = "",
    val rating: String = "",
    val readStatus: String = "unread",
    val coverFile: String = "",
    val coverSourceUrl: String = "",
    val dataSource: String = "",
    val dateAdded: Long = 0L,
) {
    fun toBook(existingBook: Book?): Book = Book(
        id = existingBook?.id ?: 0L,
        isbn13 = isbn13.normalizedIsbn().takeIf(String::isNotBlank),
        isbn10 = isbn10.normalizedIsbn().takeIf(String::isNotBlank),
        title = TitleCase.format(title.trim()),
        subtitle = subtitle.optional()?.let(TitleCase::format),
        authors = authors.optional(),
        publisher = publisher.optional(),
        publishedDate = publishedDate.optional(),
        pageCount = pageCount.toIntOrNull(),
        language = language.optional(),
        subjects = subjects.optional(),
        description = description.optional(),
        coverFile = coverFile.optional() ?: existingBook?.coverFile,
        coverSourceUrl = coverSourceUrl.optional() ?: existingBook?.coverSourceUrl,
        dataSource = existingBook?.dataSource ?: dataSource.optional(),
        location = location.optional(),
        notes = notes.optional(),
        rating = rating.toIntOrNull(),
        readStatus = readStatus.trim().takeIf(String::isNotBlank),
        dateAdded = existingBook?.dateAdded
            ?: dateAdded.takeIf { it > 0L }
            ?: System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
    )

    companion object {
        fun from(book: Book) = BookDraft(
            isbn13 = book.isbn13.orEmpty(),
            isbn10 = book.isbn10.orEmpty(),
            title = book.title,
            subtitle = book.subtitle.orEmpty(),
            authors = book.authors.orEmpty(),
            publisher = book.publisher.orEmpty(),
            publishedDate = book.publishedDate.orEmpty(),
            pageCount = book.pageCount?.toString().orEmpty(),
            language = book.language.orEmpty(),
            subjects = book.subjects.orEmpty(),
            description = book.description.orEmpty(),
            location = book.location.orEmpty(),
            notes = book.notes.orEmpty(),
            rating = book.rating?.toString().orEmpty(),
            readStatus = book.readStatus ?: "unread",
            coverFile = book.coverFile.orEmpty(),
            coverSourceUrl = book.coverSourceUrl.orEmpty(),
            dataSource = book.dataSource.orEmpty(),
            dateAdded = book.dateAdded,
        )
    }
}

private val BookDraftSaver: Saver<BookDraft, Any> = listSaver<BookDraft, String>(
    save = {
        listOf(
            it.isbn13,
            it.isbn10,
            it.title,
            it.subtitle,
            it.authors,
            it.publisher,
            it.publishedDate,
            it.pageCount,
            it.language,
            it.subjects,
            it.description,
            it.location,
            it.notes,
            it.rating,
            it.readStatus,
            it.coverFile,
            it.coverSourceUrl,
            it.dataSource,
            it.dateAdded.toString(),
        )
    },
    restore = { values ->
        BookDraft(
            isbn13 = values.getOrNull(0).orEmpty(),
            isbn10 = values.getOrNull(1).orEmpty(),
            title = values.getOrNull(2).orEmpty(),
            subtitle = values.getOrNull(3).orEmpty(),
            authors = values.getOrNull(4).orEmpty(),
            publisher = values.getOrNull(5).orEmpty(),
            publishedDate = values.getOrNull(6).orEmpty(),
            pageCount = values.getOrNull(7).orEmpty(),
            language = values.getOrNull(8).orEmpty(),
            subjects = values.getOrNull(9).orEmpty(),
            description = values.getOrNull(10).orEmpty(),
            location = values.getOrNull(11).orEmpty(),
            notes = values.getOrNull(12).orEmpty(),
            rating = values.getOrNull(13).orEmpty(),
            readStatus = values.getOrNull(14) ?: "unread",
            coverFile = values.getOrNull(15).orEmpty(),
            coverSourceUrl = values.getOrNull(16).orEmpty(),
            dataSource = values.getOrNull(17).orEmpty(),
            dateAdded = values.getOrNull(18)?.toLongOrNull() ?: 0L,
        )
    },
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookEditScreen(
    bookId: Long?,
    viewModel: BookEditViewModel,
    initialBook: Book? = null,
    lookupMessage: String? = null,
    lookupWarning: String? = null,
    coverDirectory: File? = null,
    coverStore: CoverStore? = null,
    onDone: () -> Unit,
    onSaved: () -> Unit = onDone,
) {
    var draft by rememberSaveable(bookId, stateSaver = BookDraftSaver) {
        mutableStateOf(initialBook?.let(BookDraft::from) ?: BookDraft())
    }
    var originalBook by remember(bookId, initialBook) { mutableStateOf(initialBook) }
    var initialized by rememberSaveable(bookId) { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var formError by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var coverError by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var subjectMappingMessage by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var subjectMappingFailed by rememberSaveable(bookId) { mutableStateOf(false) }
    var isMappingSubjects by rememberSaveable(bookId) { mutableStateOf(false) }
    var captureFile by remember { mutableStateOf<File?>(null) }
    var captureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedCrop by remember { mutableStateOf<NormalizedCrop?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { captured ->
        val source = captureFile
        if (captured && source != null) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        requireNotNull(coverStore).decodeForEditing(source)
                    }
                    val crop = withContext(Dispatchers.Default) {
                        CoverBoundaryDetector.detect(bitmap)
                    }
                    cropBitmap = bitmap
                    detectedCrop = crop
                } catch (exception: IOException) {
                    Log.e(TAG, "Could not load captured cover photo", exception)
                    coverError = "The photo couldn't be opened. Please take another photo."
                    source.delete()
                    captureFile = null
                } catch (exception: IllegalArgumentException) {
                    Log.e(TAG, "Could not load captured cover photo", exception)
                    coverError = "The photo couldn't be opened. Please take another photo."
                    source.delete()
                    captureFile = null
                }
            }
        } else {
            source?.delete()
            captureFile = null
            captureUri = null
        }
    }

    fun takeCoverPhoto() {
        if (coverStore == null) {
            coverError = "Camera cover capture isn't available."
            return
        }
        try {
            val cameraDirectory = File(context.cacheDir, "camera-photos").apply {
                if (!exists() && !mkdirs()) throw IOException("Could not create camera folder")
            }
            val file = File(cameraDirectory, "cover-${UUID.randomUUID()}.jpg")
            if (!file.createNewFile()) throw IOException("Could not create camera photo")
            captureFile = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            captureUri = uri
            coverError = null
            cameraLauncher.launch(uri)
        } catch (exception: IOException) {
            Log.e(TAG, "Could not prepare cover photo capture", exception)
            captureFile?.delete()
            captureFile = null
            captureUri = null
            coverError = "Couldn't prepare the camera. Please try again."
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Could not prepare cover photo capture", exception)
            captureFile?.delete()
            captureFile = null
            captureUri = null
            coverError = "Couldn't prepare the camera. Please try again."
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "No camera app is available for cover capture", exception)
            captureFile?.delete()
            captureFile = null
            captureUri = null
            coverError = "Couldn't prepare the camera. Please try again."
        }
    }

    fun cancelCrop() {
        cropBitmap?.recycle()
        cropBitmap = null
        detectedCrop = null
        captureFile?.delete()
        captureFile = null
        captureUri = null
    }

    fun useCrop(crop: NormalizedCrop) {
        val source = captureFile ?: return
        val store = coverStore ?: return
        scope.launch {
            try {
                val newCover = withContext(Dispatchers.IO) {
                    store.saveUserPhoto(source, crop)
                }
                draft = draft.copy(coverFile = newCover, coverSourceUrl = "user-photo")
                coverError = null
                cropBitmap?.recycle()
                cropBitmap = null
                detectedCrop = null
                source.delete()
                captureFile = null
                captureUri = null
            } catch (exception: IOException) {
                Log.e(TAG, "Could not save cropped cover photo", exception)
                coverError = "The cropped photo couldn't be saved. Please try again."
            } catch (exception: IllegalArgumentException) {
                Log.e(TAG, "Could not crop cover photo", exception)
                coverError = "The crop wasn't valid. Adjust it and try again."
            }
        }
    }

    LaunchedEffect(bookId) {
        if (bookId != null) {
            originalBook = viewModel.getBook(bookId)
            if (!initialized) {
                originalBook?.let { draft = BookDraft.from(it) }
            }
        } else if (!initialized) {
            originalBook = initialBook
        }
        initialized = true
    }

    cropBitmap?.let { bitmap ->
        CropPhotoScreen(
            bitmap = bitmap,
            initialCrop = detectedCrop,
            onUseCrop = ::useCrop,
            onCancel = ::cancelCrop,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (bookId == null) "Add book" else "Edit book") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    originalBook?.takeIf { it.id != 0L }?.let {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete book")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val pages = draft.pageCount.toIntOrNull()
                        val rating = draft.rating.toIntOrNull()
                        formError = when {
                            draft.title.isBlank() -> "Enter a title."
                            draft.pageCount.isNotBlank() && pages == null ->
                                "Page count must be a whole number."
                            draft.rating.isNotBlank() && (rating == null || rating !in 0..5) ->
                                "Rating must be between 0 and 5."
                            else -> null
                        }
                        if (formError == null) {
                            scope.launch {
                                try {
                                    viewModel.save(
                                        draft.toBook(existingBook = originalBook),
                                    )
                                    onSaved()
                                } catch (_: SQLiteConstraintException) {
                                    formError = "A book with that ISBN-13 is already in your library."
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            formError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            lookupMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lookupWarning?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            coverError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            draft.coverFile.takeIf(String::isNotBlank)?.let { coverFile ->
                coverDirectory?.let { directory ->
                    AsyncImage(
                        model = File(directory, coverFile),
                        contentDescription = "Book cover",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Button(
                onClick = ::takeCoverPhoto,
                enabled = captureUri == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (draft.coverFile.isBlank()) "Take cover photo" else "Replace cover photo")
            }
            BookTextField("Title", draft.title, { draft = draft.copy(title = it) }, required = true)
            BookTextField("Subtitle", draft.subtitle, { draft = draft.copy(subtitle = it) })
            BookTextField(
                "Authors",
                draft.authors,
                { draft = draft.copy(authors = it) },
                placeholder = "e.g. Doe, Jane; Smith, John",
            )
            BookTextField("ISBN-13", draft.isbn13, { draft = draft.copy(isbn13 = it) })
            BookTextField("ISBN-10", draft.isbn10, { draft = draft.copy(isbn10 = it) })
            BookTextField("Publisher", draft.publisher, { draft = draft.copy(publisher = it) })
            BookTextField(
                "Published date",
                draft.publishedDate,
                { draft = draft.copy(publishedDate = it) },
            )
            BookTextField(
                "Page count",
                draft.pageCount,
                { draft = draft.copy(pageCount = it) },
                keyboardType = KeyboardType.Number,
            )
            BookTextField("Language", draft.language, { draft = draft.copy(language = it) })
            BookTextField("Subjects (BISAC)", draft.subjects, { draft = draft.copy(subjects = it) })
            Button(
                onClick = {
                    scope.launch {
                        isMappingSubjects = true
                        subjectMappingMessage = null
                        subjectMappingFailed = false
                        try {
                            val subjects = viewModel.mapSubjectsToBisac(draft.toBook(originalBook))
                            if (subjects.isEmpty()) {
                                subjectMappingMessage =
                                    "No confident BISAC classification was returned; subjects were left unchanged."
                                subjectMappingFailed = true
                            } else {
                                draft = draft.copy(subjects = subjects.joinToString("; "))
                                subjectMappingMessage = "Subjects mapped to BISAC."
                            }
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: IOException) {
                            subjectMappingMessage =
                                "Could not map subjects to BISAC. Check the local model connection and try again."
                            subjectMappingFailed = true
                        } catch (exception: HttpException) {
                            subjectMappingMessage =
                                "Could not map subjects to BISAC. Check the local model connection and try again."
                            subjectMappingFailed = true
                        } catch (exception: JsonDataException) {
                            subjectMappingMessage =
                                "The local model returned an invalid BISAC response; subjects were left unchanged."
                            subjectMappingFailed = true
                        } catch (exception: IllegalArgumentException) {
                            subjectMappingMessage =
                                "The local model returned invalid BISAC subjects; subjects were left unchanged."
                            subjectMappingFailed = true
                        } catch (exception: IllegalStateException) {
                            subjectMappingMessage = exception.message
                                ?: "BISAC subject mapping is not configured."
                            subjectMappingFailed = true
                        } finally {
                            isMappingSubjects = false
                        }
                    }
                },
                enabled = !isMappingSubjects,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isMappingSubjects) "Mapping subjects…" else "Map subjects to BISAC")
            }
            subjectMappingMessage?.let {
                Text(
                    it,
                    color = if (subjectMappingFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            BookTextField(
                "Description",
                draft.description,
                { draft = draft.copy(description = it) },
                singleLine = false,
            )
            BookTextField("Location", draft.location, { draft = draft.copy(location = it) })
            BookTextField("Notes", draft.notes, { draft = draft.copy(notes = it) }, singleLine = false)
            BookTextField(
                "Rating (0–5)",
                draft.rating,
                { draft = draft.copy(rating = it) },
                keyboardType = KeyboardType.Number,
            )
            BookTextField(
                "Read status (unread, reading, read)",
                draft.readStatus,
                { draft = draft.copy(readStatus = it) },
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete book?") },
            text = { Text("“${originalBook?.title.orEmpty()}” will be removed from your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        originalBook?.let { book ->
                            scope.launch { viewModel.delete(book) }
                        }
                        showDeleteConfirmation = false
                        onDone()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BookTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    placeholder: String? = null,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label *" else label) },
        placeholder = placeholder?.let { example ->
            {
                Text(
                    example,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        },
        supportingText = supportingText?.let { text -> { Text(text) } },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = if (singleLine) 1 else 3,
    )
}

private fun String.optional(): String? = trim().takeIf(String::isNotBlank)

private fun String.normalizedIsbn(): String =
    filterNot(Char::isWhitespace).replace("-", "").uppercase()

private const val TAG = "BookEditScreen"
