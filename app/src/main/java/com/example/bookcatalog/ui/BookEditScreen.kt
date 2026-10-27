package com.example.bookcatalog.ui

import android.graphics.Bitmap
import android.os.Parcelable
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.covers.CoverStore
import com.example.bookcatalog.data.covers.CoverBoundaryDetector
import com.example.bookcatalog.data.covers.NormalizedCrop
import com.example.bookcatalog.data.covers.PhotoTransform
import com.example.bookcatalog.data.io.AmazonImporter
import com.example.bookcatalog.data.metadata.ModelException
import com.example.bookcatalog.domain.IsbnUtils
import kotlinx.coroutines.Job
import com.example.bookcatalog.domain.AuthorNames
import com.example.bookcatalog.domain.BookFormat
import com.example.bookcatalog.domain.Language
import com.example.bookcatalog.domain.PublicationYear
import com.example.bookcatalog.domain.ReadStatus
import com.example.bookcatalog.domain.TitleCase
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.IOException
import java.util.UUID

@Parcelize
private data class BookDraft(
    val isbn13: String = "",
    val isbn10: String = "",
    val asin: String = "",
    val title: String = "",
    val subtitle: String = "",
    val authors: String = "",
    val publisher: String = "",
    val publishedDate: String = "",
    val pageCount: String = "",
    val audioHours: String = "",
    val audioMinutes: String = "",
    val format: String = "",
    val language: String = Language.DEFAULT,
    val subjects: String = "",
    val description: String = "",
    val location: String = "",
    val notes: String = "",
    val rating: String = "",
    val readStatus: String = ReadStatus.UNREAD,
    val coverFile: String = "",
    val coverSourceUrl: String = "",
    val dataSource: String = "",
    val dateAdded: Long = 0L,
) : Parcelable {
    fun toBook(existingBook: Book?): Book = Book(
        id = existingBook?.id ?: 0L,
        isbn13 = isbn13.normalizedIsbn().takeIf(String::isNotBlank),
        isbn10 = isbn10.normalizedIsbn().takeIf(String::isNotBlank),
        asin = asin.normalizedIsbn().takeIf(String::isNotBlank),
        title = TitleCase.format(title.trim(), keepUppercaseWords = true),
        subtitle = subtitle.optional()?.let { TitleCase.format(it, keepUppercaseWords = true) },
        authors = AuthorNames.normalize(authors),
        publisher = publisher.optional(),
        publishedDate = publishedDate.optional(),
        pageCount = pageCount.toIntOrNull(),
        audioMinutes = audioLength(),
        format = format.optional(),
        language = language.optional(),
        subjects = subjects.optional(),
        description = description.optional(),
        coverFile = coverFile.optional() ?: existingBook?.coverFile,
        coverSourceUrl = coverSourceUrl.optional() ?: existingBook?.coverSourceUrl,
        dataSource = dataSource.optional() ?: existingBook?.dataSource,
        location = location.optional(),
        notes = notes.optional(),
        rating = rating.toIntOrNull(),
        readStatus = readStatus.trim().takeIf(String::isNotBlank),
        dateAdded = existingBook?.dateAdded
            ?: dateAdded.takeIf { it > 0L }
            ?: System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
    )

    val hasAudioLength: Boolean get() = audioHours.isNotBlank() || audioMinutes.isNotBlank()

    /** The listening length in minutes, or null when it is blank or isn't whole numbers. */
    fun audioLength(): Int? {
        if (!hasAudioLength) return null
        val hours = if (audioHours.isBlank()) 0 else audioHours.trim().toIntOrNull() ?: return null
        val minutes = if (audioMinutes.isBlank()) 0 else audioMinutes.trim().toIntOrNull() ?: return null
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    companion object {
        fun from(book: Book) = BookDraft(
            isbn13 = book.isbn13.orEmpty(),
            isbn10 = book.isbn10.orEmpty(),
            asin = book.asin.orEmpty(),
            title = book.title,
            subtitle = book.subtitle.orEmpty(),
            authors = book.authors.orEmpty(),
            publisher = book.publisher.orEmpty(),
            publishedDate = book.publishedDate.orEmpty(),
            pageCount = book.pageCount?.toString().orEmpty(),
            audioHours = book.audioMinutes?.takeIf { it >= 60 }?.let { (it / 60).toString() }.orEmpty(),
            audioMinutes = book.audioMinutes?.let { (it % 60).toString() }.orEmpty(),
            format = book.format.orEmpty(),
            language = book.language.orEmpty(),
            subjects = book.subjects.orEmpty(),
            description = book.description.orEmpty(),
            location = book.location.orEmpty(),
            notes = book.notes.orEmpty(),
            rating = book.rating?.toString().orEmpty(),
            readStatus = book.readStatus ?: ReadStatus.UNREAD,
            coverFile = book.coverFile.orEmpty(),
            coverSourceUrl = book.coverSourceUrl.orEmpty(),
            dataSource = book.dataSource.orEmpty(),
            dateAdded = book.dateAdded,
        )
    }
}

/** The outcome of a HomeChat button, shown under it. */
@Parcelize
private data class ModelStatus(val text: String, val isError: Boolean) : Parcelable

private enum class ModelTask { SUBJECTS, DESCRIPTION }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookEditScreen(
    bookId: Long?,
    viewModel: BookEditViewModel,
    initialBook: Book? = null,
    lookupMessage: String? = null,
    lookupWarning: String? = null,
    coverDirectory: File,
    coverStore: CoverStore,
    onDone: () -> Unit,
    onSaved: () -> Unit = onDone,
    onDuplicated: (Long) -> Unit = {},
) {
    var draft by rememberSaveable(bookId) {
        mutableStateOf(initialBook?.let(BookDraft::from) ?: BookDraft())
    }
    var originalBook by remember(bookId, initialBook) { mutableStateOf(initialBook) }
    var initialized by rememberSaveable(bookId) { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var formError by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var coverError by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var subjectStatus by rememberSaveable(bookId) { mutableStateOf<ModelStatus?>(null) }
    var descriptionStatus by rememberSaveable(bookId) { mutableStateOf<ModelStatus?>(null) }
    // Not saved: the request is cancelled if the screen is recreated, so it must not stay busy.
    var runningTask by remember { mutableStateOf<ModelTask?>(null) }
    var isbnStatus by rememberSaveable(bookId) { mutableStateOf<ModelStatus?>(null) }
    // The ISBN the details were last filled in from, so only a new ISBN starts a lookup.
    var lookedUpIsbn by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var isbnLookup by remember { mutableStateOf<Job?>(null) }
    // What a running ISBN lookup is doing; the form is covered and can't be saved until it ends.
    var isbnLookupStep by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Saved, so a photo survives the screen being recreated while the camera or cropper is open.
    var captureFile by rememberSaveable(stateSaver = FileSaver) { mutableStateOf<File?>(null) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var cropping by rememberSaveable { mutableStateOf(false) }
    var detectedCrop by rememberSaveable { mutableStateOf<NormalizedCrop?>(null) }
    var cropTransform by rememberSaveable { mutableStateOf<PhotoTransform?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppingChosenImage by rememberSaveable { mutableStateOf(false) }
    // Kept from the first composition, since the lookup that produced them isn't saved.
    val lookupNotice by rememberSaveable(bookId) { mutableStateOf(lookupMessage) }
    val lookupProblem by rememberSaveable(bookId) { mutableStateOf(lookupWarning) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun takeCoverPhoto() {
        try {
            val cameraDirectory = File(context.cacheDir, "camera-photos").apply {
                if (!exists() && !mkdirs()) throw IOException("Could not create camera folder")
            }
            captureFile = File(cameraDirectory, "cover-${UUID.randomUUID()}.jpg")
            coverError = null
            showCamera = true
        } catch (exception: IOException) {
            Log.e(TAG, "Could not prepare cover photo capture", exception)
            coverError = "Couldn't prepare the camera. Please try again."
        }
    }

    fun closeCamera(error: String? = null) {
        showCamera = false
        captureFile?.delete()
        captureFile = null
        coverError = error
    }

    /** Straightens a camera photo to its detected cover edges; a chosen image is framed whole. */
    fun onPhotoCaptured(chosenImage: Boolean = false) {
        showCamera = false
        val source = captureFile ?: return
        scope.launch {
            try {
                val photo = withContext(Dispatchers.IO) { coverStore.decodeForEditing(source) }
                val detected = if (chosenImage) {
                    null
                } else {
                    withContext(Dispatchers.Default) { CoverBoundaryDetector.detect(photo) }
                }
                val transform = detected?.transform ?: PhotoTransform.identity(photo.width, photo.height)
                cropBitmap = withContext(Dispatchers.Default) {
                    coverStore.transform(photo, transform).also { if (it !== photo) photo.recycle() }
                }
                detectedCrop = if (chosenImage) NormalizedCrop(0f, 0f, 1f, 1f) else detected?.crop
                croppingChosenImage = chosenImage
                cropTransform = transform
                cropping = true
            } catch (exception: IOException) {
                Log.e(TAG, "Could not load captured cover photo", exception)
                closeCamera("The image couldn't be opened. Please try another.")
            }
        }
    }

    fun cancelCrop() {
        cropBitmap?.recycle()
        cropBitmap = null
        cropping = false
        detectedCrop = null
        cropTransform = null
        captureFile?.delete()
        captureFile = null
    }

    // Recreating the screen drops the bitmap, so decode the photo again from its saved file.
    LaunchedEffect(cropping) {
        val source = captureFile
        if (cropping && cropBitmap == null && source != null) {
            try {
                cropBitmap = withContext(Dispatchers.IO) { coverStore.decodeForEditing(source, cropTransform) }
            } catch (exception: IOException) {
                Log.e(TAG, "Could not reload cover photo", exception)
                coverError = "The photo couldn't be opened. Please take another photo."
                cancelCrop()
            }
        }
    }

    fun rotateCrop(crop: NormalizedCrop) {
        val bitmap = cropBitmap ?: return
        scope.launch {
            val rotated = withContext(Dispatchers.Default) { coverStore.rotate(bitmap, 90f) }
            // The old bitmap may still be drawing, so it is left for the garbage collector.
            cropBitmap = rotated
            detectedCrop = crop
            cropTransform = cropTransform?.thenRotateClockwise()
        }
    }

    fun useCrop(crop: NormalizedCrop) {
        val source = captureFile ?: return
        scope.launch {
            try {
                val newCover = withContext(Dispatchers.IO) {
                    coverStore.saveUserPhoto(source, crop, cropTransform)
                }
                draft = draft.copy(coverFile = newCover, coverSourceUrl = "user-photo")
                coverError = null
                cropBitmap?.recycle()
                cropBitmap = null
                cropping = false
                detectedCrop = null
                cropTransform = null
                source.delete()
                captureFile = null
            } catch (exception: IOException) {
                Log.e(TAG, "Could not save cropped cover photo", exception)
                coverError = "The cropped photo couldn't be saved. Please try again."
            } catch (exception: IllegalArgumentException) {
                Log.e(TAG, "Could not crop cover photo", exception)
                coverError = "The crop wasn't valid. Adjust it and try again."
            }
        }
    }

    /** Copies a chosen image to the camera folder, then crops it just like a cover photo. */
    fun useChosenImage(uri: android.net.Uri) {
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val cameraDirectory = File(context.cacheDir, "camera-photos").apply {
                        if (!exists() && !mkdirs()) throw IOException("Could not create camera folder")
                    }
                    val target = File(cameraDirectory, "cover-${UUID.randomUUID()}.jpg")
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("The image couldn't be opened")
                    input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
                    target
                }
                captureFile = file
                coverError = null
                onPhotoCaptured(chosenImage = true)
            } catch (exception: IOException) {
                Log.e(TAG, "Could not copy chosen cover image", exception)
                coverError = "The image couldn't be opened. Please choose another."
            } catch (exception: SecurityException) {
                Log.e(TAG, "Not allowed to read chosen cover image", exception)
                coverError = "The app wasn't allowed to open that image."
            }
        }
    }

    /**
     * Fills in a book that came from an Amazon book list from a new ISBN, as adding it by ISBN
     * would, keeping its ASIN, format, read status and the user's own location, notes, rating
     * and date added. The edition's details are shown as soon as they arrive; the BISAC subjects
     * and description follow when HomeChat finishes, which can take a minute.
     */
    fun lookUpIsbn(isbn13: String) {
        lookedUpIsbn = isbn13
        isbnLookup?.cancel()
        keyboard?.hide()
        isbnLookup = scope.launch {
            try {
                isbnStatus = null
                isbnLookupStep = "Looking up ISBN $isbn13…"
                val details = viewModel.fetchDetails(isbn13)
                val found = details.book
                if (found == null) {
                    isbnStatus = ModelStatus(
                        if (details.allSourcesFailed) {
                            "Couldn’t connect to the book lookup services. Check your internet connection and try again."
                        } else {
                            "No book details were found for ISBN $isbn13."
                        },
                        isError = true,
                    )
                    return@launch
                }
                val current = draft
                draft = BookDraft.from(found).copy(
                    asin = current.asin,
                    format = current.format,
                    readStatus = current.readStatus,
                    location = current.location,
                    notes = current.notes,
                    rating = current.rating,
                    dateAdded = current.dateAdded,
                    coverFile = found.coverFile ?: current.coverFile,
                    coverSourceUrl = found.coverSourceUrl ?: current.coverSourceUrl,
                    dataSource = listOfNotNull(AmazonImporter.SOURCE, found.dataSource).joinToString("+"),
                )
                val problems = mutableListOf<String>()
                if (found.coverFile == null) {
                    problems += if (details.coverDownloadFailed) {
                        "Its cover couldn't be downloaded."
                    } else {
                        "No cover was found for this ISBN."
                    }
                }
                fun report() {
                    isbnStatus = ModelStatus(
                        (listOf("Details filled in from ISBN $isbn13.") + problems).joinToString(" "),
                        isError = problems.isNotEmpty(),
                    )
                }
                report()

                // Only the subjects and description change from here, so other edits made meanwhile are kept.
                if (viewModel.canMapSubjects && !details.hasPublisherBisac) {
                    isbnLookupStep = "Mapping subjects to BISAC…"
                    try {
                        val headings = viewModel.mapSubjectsToBisac(draft.toBook(originalBook))
                        if (headings.isNotEmpty()) draft = draft.copy(subjects = headings.joinToString("; "))
                    } catch (exception: ModelException) {
                        problems += "Could not map subjects to BISAC."
                    }
                }
                if (viewModel.canWriteDescriptions) {
                    isbnLookupStep = "Writing a description…"
                    try {
                        viewModel.writeDescription(draft.toBook(originalBook))?.let { draft = draft.copy(description = it) }
                    } catch (exception: ModelException) {
                        problems += "Could not write a description."
                    }
                }
                report()
            } finally {
                // A newer lookup may have replaced this one, and owns the progress card now.
                if (isbnLookup === coroutineContext[Job]) {
                    isbnLookupStep = null
                    if (isbnStatus == null) {
                        // Stopped before any details arrived, so typing the same ISBN tries again.
                        lookedUpIsbn = null
                        isbnStatus = ModelStatus("Lookup stopped before any details were found.", isError = true)
                    }
                }
            }
        }
    }

    /**
     * Starts a lookup when an Amazon book's ISBN is changed to a different valid one. Each field
     * waits for a full ISBN of its own length, so the first ten digits of an ISBN-13 being typed
     * can't start a lookup of an unrelated ISBN-10.
     */
    fun onIsbnEdited(value: String, length: Int) {
        if (draft.asin.isBlank() || IsbnUtils.normalize(value).length != length) return
        val isbn13 = IsbnUtils.toIsbn13(value) ?: return
        if (isbn13 != (lookedUpIsbn ?: originalBook?.isbn13)) lookUpIsbn(isbn13)
    }

    val chooseImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::useChosenImage)
    }

    fun runModelTask(task: ModelTask, setStatus: (ModelStatus?) -> Unit, work: suspend () -> ModelStatus) {
        scope.launch {
            runningTask = task
            setStatus(null)
            try {
                setStatus(work())
            } catch (exception: ModelException) {
                setStatus(ModelStatus("${exception.message} Nothing was changed.", isError = true))
            } finally {
                runningTask = null
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

    BackHandler(enabled = isbnLookupStep != null) { isbnLookup?.cancel() }

    captureFile?.takeIf { showCamera }?.let { file ->
        CameraCaptureScreen(
            outputFile = file,
            onCaptured = ::onPhotoCaptured,
            onCancel = { closeCamera() },
            onError = ::closeCamera,
        )
        return
    }

    cropBitmap?.let { bitmap ->
        CropPhotoScreen(
            bitmap = bitmap,
            initialCrop = detectedCrop,
            chosenImage = croppingChosenImage,
            onRotate = ::rotateCrop,
            onUseCrop = ::useCrop,
            onCancel = ::cancelCrop,
        )
        return
    }

    // A new book can always be saved; an existing one acts as a view until something changes.
    val hasChanges = bookId == null || originalBook?.let { draft != BookDraft.from(it) } == true

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
                    originalBook?.takeIf { it.id != 0L }?.let { book ->
                        // The saved book is copied, so unsaved edits must be saved or cancelled first.
                        IconButton(
                            onClick = { scope.launch { onDuplicated(viewModel.duplicate(book)) } },
                            enabled = !hasChanges && isbnLookupStep == null,
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate book")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete book")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (hasChanges && isbnLookupStep == null) Row(
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
                        formError = when {
                            draft.title.isBlank() -> "Enter a title."
                            draft.publishedDate.isNotBlank() && !PublicationYear.isValid(draft.publishedDate.trim()) ->
                                "Publication year must be a four-digit year."
                            draft.pageCount.isNotBlank() && pages == null ->
                                "Page count must be a whole number."
                            draft.hasAudioLength && draft.audioLength() == null ->
                                "Audio length must be whole hours and minutes."
                            else -> null
                        }
                        if (formError == null) {
                            scope.launch {
                                viewModel.save(draft.toBook(existingBook = originalBook))
                                onSaved()
                            }
                        } else {
                            // The error is shown above the fields, which may be scrolled out of view.
                            scope.launch { scrollState.animateScrollTo(0) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                formError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                lookupNotice?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                lookupProblem?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                coverError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                draft.coverFile.takeIf(String::isNotBlank)?.let { coverFile ->
                    AsyncImage(
                        model = File(coverDirectory, coverFile),
                        contentDescription = "Book cover",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = ::takeCoverPhoto,
                        enabled = captureFile == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (draft.coverFile.isBlank()) "Take cover photo" else "Replace cover photo")
                    }
                    OutlinedButton(
                        onClick = { chooseImage.launch(arrayOf("image/*")) },
                        enabled = captureFile == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Choose image")
                    }
                }
                BookTextField("Title", draft.title, { draft = draft.copy(title = it) }, required = true, capitalization = KeyboardCapitalization.Words)
                BookTextField("Subtitle", draft.subtitle, { draft = draft.copy(subtitle = it) }, capitalization = KeyboardCapitalization.Words)
                BookTextField(
                    "Authors",
                    draft.authors,
                    { draft = draft.copy(authors = it) },
                    placeholder = "e.g. Jane Doe; John Smith",
                    capitalization = KeyboardCapitalization.Words,
                )
                BookTextField(
                    "ISBN-13",
                    draft.isbn13,
                    {
                        draft = draft.copy(isbn13 = it)
                        onIsbnEdited(it, length = 13)
                    },
                    keyboardType = KeyboardType.Number,
                )
                BookTextField(
                    "ISBN-10",
                    draft.isbn10,
                    {
                        draft = draft.copy(isbn10 = it)
                        onIsbnEdited(it, length = 10)
                    },
                    keyboardType = KeyboardType.Number,
                    trailingIcon = {
                        IsbnCheckXButton(draft.isbn10) {
                            draft = draft.copy(isbn10 = it)
                            onIsbnEdited(it, length = 10)
                        }
                    },
                )
                isbnStatus?.let { ModelStatusText(it) }
                BookTextField(
                    "ASIN",
                    draft.asin,
                    { draft = draft.copy(asin = it) },
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                )
                BookTextField("Publisher", draft.publisher, { draft = draft.copy(publisher = it) }, capitalization = KeyboardCapitalization.Words)
                BookTextField(
                    "Publication year",
                    draft.publishedDate,
                    { draft = draft.copy(publishedDate = it) },
                    keyboardType = KeyboardType.Number,
                )
                BookTextField(
                    "Page count",
                    draft.pageCount,
                    { draft = draft.copy(pageCount = it) },
                    keyboardType = KeyboardType.Number,
                )
                ChoiceField(
                    label = "Format",
                    value = draft.format,
                    choices = listOf("" to "Not set") + BookFormat.choices.map { it to it },
                ) { draft = draft.copy(format = it) }
                // Kept visible for a book that has a length, so changing its format doesn't hide it.
                if (draft.format == "Audiobook" || draft.hasAudioLength) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BookTextField(
                            "Audio hours",
                            draft.audioHours,
                            { draft = draft.copy(audioHours = it) },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                        BookTextField(
                            "Minutes",
                            draft.audioMinutes,
                            { draft = draft.copy(audioMinutes = it) },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                BookTextField("Language", draft.language, { draft = draft.copy(language = it) }, capitalization = KeyboardCapitalization.Words)
                BookTextField("Subjects (BISAC)", draft.subjects, { draft = draft.copy(subjects = it) }, capitalization = KeyboardCapitalization.Words)
                Button(
                    onClick = {
                        runModelTask(ModelTask.SUBJECTS, { subjectStatus = it }) {
                            val subjects = viewModel.mapSubjectsToBisac(draft.toBook(originalBook))
                            if (subjects.isEmpty()) {
                                ModelStatus("No confident BISAC classification was found. Nothing was changed.", isError = true)
                            } else {
                                draft = draft.copy(subjects = subjects.joinToString("; "))
                                ModelStatus("Subjects mapped to BISAC.", isError = false)
                            }
                        }
                    },
                    enabled = runningTask == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (runningTask == ModelTask.SUBJECTS) "Mapping subjects…" else "Map subjects to BISAC")
                }
                subjectStatus?.let { ModelStatusText(it) }
                BookTextField(
                    "Description",
                    draft.description,
                    { draft = draft.copy(description = it) },
                    singleLine = false,
                    capitalization = KeyboardCapitalization.Sentences,
                )
                Button(
                    onClick = {
                        runModelTask(ModelTask.DESCRIPTION, { descriptionStatus = it }) {
                            val hadDescription = draft.description.isNotBlank()
                            val description = viewModel.writeDescription(draft.toBook(originalBook))
                            if (description == null) {
                                ModelStatus("The book couldn't be identified. Nothing was changed.", isError = true)
                            } else {
                                draft = draft.copy(description = description)
                                ModelStatus(if (hadDescription) "Description summarized." else "Description written.", isError = false)
                            }
                        }
                    },
                    enabled = runningTask == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            runningTask == ModelTask.DESCRIPTION -> "Writing description…"
                            draft.description.isBlank() -> "Write description"
                            else -> "Summarize description"
                        },
                    )
                }
                descriptionStatus?.let { ModelStatusText(it) }
                BookTextField("Location", draft.location, { draft = draft.copy(location = it) }, capitalization = KeyboardCapitalization.Sentences)
                BookTextField(
                    "Notes",
                    draft.notes,
                    { draft = draft.copy(notes = it) },
                    singleLine = false,
                    capitalization = KeyboardCapitalization.Sentences,
                )
                StarRatingField(
                    rating = draft.rating.toIntOrNull() ?: 0,
                    onRatingChange = { draft = draft.copy(rating = if (it == 0) "" else it.toString()) },
                )
                ChoiceField(
                    label = "Read status",
                    value = draft.readStatus,
                    choices = ReadStatus.choices.map { it to it.replaceFirstChar(Char::uppercase) },
                ) { draft = draft.copy(readStatus = it) }
            }
            isbnLookupStep?.let { step ->
                IsbnLookupOverlay(step, onStop = { isbnLookup?.cancel() })
            }
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
                        showDeleteConfirmation = false
                        // Leaving first would cancel this scope before the delete finished.
                        scope.launch {
                            originalBook?.let { viewModel.delete(it) }
                            onDone()
                        }
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
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
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
        trailingIcon = trailingIcon,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            },
        singleLine = singleLine,
        // Next moves focus down the form; multi-line fields keep Enter for new lines.
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        minLines = if (singleLine) 1 else 3,
    )
}

/** A read-only field that opens a menu of [choices], given as stored value to label pairs. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = choices.firstOrNull { it.first == value }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (choice, choiceLabel) ->
                DropdownMenuItem(
                    text = { Text(choiceLabel) },
                    onClick = {
                        onValueChange(choice)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Five tappable stars; tapping the highest lit star clears the rating. */
@Composable
private fun StarRatingField(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Rating",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 8.dp),
        )
        (1..5).forEach { star ->
            IconButton(onClick = { onRatingChange(if (star == rating) 0 else star) }) {
                Icon(
                    imageVector = if (star <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (star == 1) "1 star" else "$star stars",
                    tint = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val FileSaver = Saver<File?, String>(save = { it?.path }, restore = ::File)

/** Dims the form and blocks touches while an ISBN lookup fills it in. */
@Composable
private fun IsbnLookupOverlay(step: String, onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(step, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "This can take a minute. Stopping keeps what has been filled in so far.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onStop) { Text("Stop lookup") }
            }
        }
    }
}

@Composable
private fun ModelStatusText(status: ModelStatus) {
    Text(
        status.text,
        color = if (status.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun String.optional(): String? = trim().takeIf(String::isNotBlank)

private fun String.normalizedIsbn(): String =
    filterNot(Char::isWhitespace).replace("-", "").uppercase()

private const val TAG = "BookEditScreen"
