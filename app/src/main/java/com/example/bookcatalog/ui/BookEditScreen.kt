package com.example.bookcatalog.ui

import android.graphics.Bitmap
import android.os.Parcelable
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.bookcatalog.data.metadata.ModelException
import com.example.bookcatalog.domain.BookFormat
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
    val title: String = "",
    val subtitle: String = "",
    val authors: String = "",
    val publisher: String = "",
    val publishedDate: String = "",
    val pageCount: String = "",
    val format: String = "",
    val language: String = "",
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
        title = TitleCase.format(title.trim()),
        subtitle = subtitle.optional()?.let(TitleCase::format),
        authors = authors.optional(),
        publisher = publisher.optional(),
        publishedDate = publishedDate.optional(),
        pageCount = pageCount.toIntOrNull(),
        format = format.optional(),
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
    // Saved, so a photo survives the screen being recreated while the camera or cropper is open.
    var captureFile by rememberSaveable(stateSaver = FileSaver) { mutableStateOf<File?>(null) }
    var cropping by rememberSaveable { mutableStateOf(false) }
    var detectedCrop by rememberSaveable { mutableStateOf<NormalizedCrop?>(null) }
    var cropRotation by rememberSaveable { mutableStateOf(0f) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Kept from the first composition, since the lookup that produced them isn't saved.
    val lookupNotice by rememberSaveable(bookId) { mutableStateOf(lookupMessage) }
    val lookupProblem by rememberSaveable(bookId) { mutableStateOf(lookupWarning) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { captured ->
        val source = captureFile
        if (captured && source != null) {
            scope.launch {
                try {
                    val photo = withContext(Dispatchers.IO) { coverStore.decodeForEditing(source) }
                    val detected = withContext(Dispatchers.Default) {
                        CoverBoundaryDetector.detect(photo)
                    }
                    val rotation = detected?.rotationDegrees ?: 0f
                    cropBitmap = withContext(Dispatchers.Default) {
                        coverStore.rotate(photo, rotation).also { if (it !== photo) photo.recycle() }
                    }
                    detectedCrop = detected?.crop
                    cropRotation = rotation
                    cropping = true
                } catch (exception: IOException) {
                    Log.e(TAG, "Could not load captured cover photo", exception)
                    coverError = "The photo couldn't be opened. Please take another photo."
                    source.delete()
                    captureFile = null
                }
            }
        } else {
            source?.delete()
            captureFile = null
        }
    }

    fun takeCoverPhoto() {
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
            coverError = null
            cameraLauncher.launch(uri)
        } catch (exception: Exception) {
            if (exception !is IOException && exception !is IllegalArgumentException &&
                exception !is ActivityNotFoundException
            ) {
                throw exception
            }
            Log.e(TAG, "Could not prepare cover photo capture", exception)
            captureFile?.delete()
            captureFile = null
            coverError = "Couldn't prepare the camera. Please try again."
        }
    }

    fun cancelCrop() {
        cropBitmap?.recycle()
        cropBitmap = null
        cropping = false
        detectedCrop = null
        cropRotation = 0f
        captureFile?.delete()
        captureFile = null
    }

    // Recreating the screen drops the bitmap, so decode the photo again from its saved file.
    LaunchedEffect(cropping) {
        val source = captureFile
        if (cropping && cropBitmap == null && source != null) {
            try {
                cropBitmap = withContext(Dispatchers.IO) { coverStore.decodeForEditing(source, cropRotation) }
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
            cropRotation = (cropRotation + 90f) % 360f
        }
    }

    fun useCrop(crop: NormalizedCrop) {
        val source = captureFile ?: return
        scope.launch {
            try {
                val newCover = withContext(Dispatchers.IO) {
                    coverStore.saveUserPhoto(source, crop, cropRotation)
                }
                draft = draft.copy(coverFile = newCover, coverSourceUrl = "user-photo")
                coverError = null
                cropBitmap?.recycle()
                cropBitmap = null
                cropping = false
                detectedCrop = null
                cropRotation = 0f
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

    cropBitmap?.let { bitmap ->
        CropPhotoScreen(
            bitmap = bitmap,
            initialCrop = detectedCrop,
            onRotate = ::rotateCrop,
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
                            draft.publishedDate.isNotBlank() && !PublicationYear.isValid(draft.publishedDate.trim()) ->
                                "Publication year must be a four-digit year."
                            draft.pageCount.isNotBlank() && pages == null ->
                                "Page count must be a whole number."
                            draft.rating.isNotBlank() && (rating == null || rating !in 0..5) ->
                                "Rating must be between 0 and 5."
                            else -> null
                        }
                        if (formError == null) {
                            scope.launch {
                                viewModel.save(draft.toBook(existingBook = originalBook))
                                onSaved()
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
            Button(
                onClick = ::takeCoverPhoto,
                enabled = captureFile == null,
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
            BookTextField("Language", draft.language, { draft = draft.copy(language = it) })
            BookTextField("Subjects (BISAC)", draft.subjects, { draft = draft.copy(subjects = it) })
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
            BookTextField("Location", draft.location, { draft = draft.copy(location = it) })
            BookTextField("Notes", draft.notes, { draft = draft.copy(notes = it) }, singleLine = false)
            BookTextField(
                "Rating (0–5)",
                draft.rating,
                { draft = draft.copy(rating = it) },
                keyboardType = KeyboardType.Number,
            )
            ChoiceField(
                label = "Read status",
                value = draft.readStatus,
                choices = ReadStatus.choices.map { it to it.replaceFirstChar(Char::uppercase) },
            ) { draft = draft.copy(readStatus = it) }
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

private val FileSaver = Saver<File?, String>(save = { it?.path }, restore = ::File)

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
