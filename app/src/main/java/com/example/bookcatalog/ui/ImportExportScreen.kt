package com.example.bookcatalog.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.bookcatalog.data.io.AmazonImportSummary
import com.example.bookcatalog.data.io.DuplicateHandling
import com.example.bookcatalog.data.io.ImportOptions
import com.example.bookcatalog.data.io.ImportSummary
import com.example.bookcatalog.data.io.IsbnImportSummary
import com.example.bookcatalog.data.io.IsbnList
import java.time.LocalDate

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImportExportScreen(
    viewModel: ImportExportViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var duplicates by rememberSaveable { mutableStateOf(DuplicateHandling.SKIP) }
    var fetchMissingCovers by rememberSaveable { mutableStateOf(false) }
    var isbnText by rememberSaveable { mutableStateOf("") }
    val isbnList = remember(isbnText) { IsbnList.parse(isbnText) }
    val exportZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.export(it, zip = true) } }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.export(it, zip = false) } }
    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.import(it, ImportOptions(duplicates, fetchMissingCovers)) } }
    val importAmazon = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importAmazon) }
    val baseName = "book-catalog-${LocalDate.now()}"

    // An Amazon import can take hours, and leaving this screen stops it.
    val view = LocalView.current
    DisposableEffect(state.busy) {
        view.keepScreenOn = state.busy
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import / Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.titleMedium)
            Text(
                "A ZIP backup holds every book and its cover and can be imported again. " +
                    "CSV only is for spreadsheets and has no images.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { exportZip.launch("$baseName.zip") },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export ZIP backup") }
            OutlinedButton(
                onClick = { exportCsv.launch("$baseName.csv") },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export CSV only") }
            state.exportMessage?.let { Text(it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Import", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose a ZIP backup or a CSV file. Books whose ISBN is already in your library:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                DuplicateHandling.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = duplicates == option,
                                onClick = { duplicates = option },
                                enabled = !state.busy,
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = duplicates == option, onClick = null, enabled = !state.busy)
                        Text(
                            when (option) {
                                DuplicateHandling.SKIP -> "Skip them"
                                DuplicateHandling.OVERWRITE -> "Overwrite them with the imported details"
                                DuplicateHandling.KEEP_BOTH -> "Keep both, adding another copy"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = fetchMissingCovers,
                        onValueChange = { fetchMissingCovers = it },
                        enabled = !state.busy,
                        role = Role.Checkbox,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = fetchMissingCovers, onCheckedChange = null, enabled = !state.busy)
                Text(
                    "Fetch missing covers after import (about one per second)",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Button(
                onClick = {
                    importFile.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "text/csv",
                            "text/comma-separated-values",
                            "text/plain",
                            "application/octet-stream",
                        ),
                    )
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Choose file to import") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Import Amazon book list", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose a CSV from the Kindle Book List Exporter. Purchased Kindle and Audible books " +
                    "are added, then each is matched to a print edition for its details, cover, BISAC " +
                    "subjects and description. Books already imported are skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Looking books up takes about half a minute each. Keep this screen open; if the import " +
                    "stops, choose the same file again to pick up where it left off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    importAmazon.launch(
                        arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"),
                    )
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Choose Amazon book list") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Import a list of ISBNs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste ISBNs one per line or separated by commas. Each is looked up and filled in just as " +
                    "if you had entered it, then added. ISBNs already in your library are skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = isbnText,
                onValueChange = { isbnText = it },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ISBNs") },
                minLines = 4,
                maxLines = 10,
                isError = isbnList.invalid.isNotEmpty(),
                supportingText = {
                    val count = isbnList.isbns.size
                    Text(
                        when {
                            isbnText.isBlank() -> "Hyphens and spaces are accepted."
                            isbnList.invalid.isEmpty() -> "$count ISBN${if (count == 1) "" else "s"}"
                            else -> "$count ISBN${if (count == 1) "" else "s"}; not valid, and will be left out: " +
                                isbnList.invalid.joinToString(", ")
                        },
                    )
                },
            )
            OutlinedButton(
                onClick = { viewModel.importIsbns(isbnText) },
                enabled = !state.busy && isbnList.isbns.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Look up and add books") }

            if (state.busy) {
                val progress = state.progress
                if (progress == null || progress.total == 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(progress?.stage ?: "Working…")
                } else {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${progress.stage}: ${progress.done + 1} of ${progress.total}")
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.importSummary?.let { ImportSummaryCard(it) }
            state.amazonSummary?.let { AmazonSummaryCard(it) }
            state.isbnSummary?.let { IsbnSummaryCard(it) }
        }
    }
}

@Composable
private fun ImportSummaryCard(summary: ImportSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Import finished", style = MaterialTheme.typography.titleMedium)
            Text("Imported: ${summary.imported}")
            Text("Updated: ${summary.updated}")
            Text("Skipped as duplicates: ${summary.skipped}")
            Text("Errors: ${summary.errors.size}")
            if (summary.coversCopied > 0) Text("Covers copied from the backup: ${summary.coversCopied}")
            if (summary.coversFetched + summary.coverFetchFailures > 0) {
                Text("Covers fetched: ${summary.coversFetched} (${summary.coverFetchFailures} couldn't be fetched)")
            }
            if (summary.invalidIsbnRows.isNotEmpty()) {
                Text(
                    "Imported without an ISBN because it wasn't valid: " +
                        "row${if (summary.invalidIsbnRows.size == 1) "" else "s"} " +
                        summary.invalidIsbnRows.joinToString(", "),
                )
            }
            summary.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            summary.warnings.forEach {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AmazonSummaryCard(summary: AmazonImportSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            summary.error?.let {
                Text("Import failed", style = MaterialTheme.typography.titleMedium)
                Text(it, color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Text(
                if (summary.remaining > 0) "Import paused" else "Import finished",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Added: ${summary.added}")
            Text("Already in your library: ${summary.alreadyInLibrary}")
            Text("Matched to a print edition: ${summary.matched}")
            Text("No matching edition found: ${summary.unmatched}")
            val leftOut = listOf(
                "library loans" to summary.libraryLoans,
                "borrowed books and samples" to summary.borrowed,
                "books Amazon no longer has details for" to summary.unavailable,
                "print books" to summary.otherFormats,
            ).filter { it.second > 0 }
            if (leftOut.isNotEmpty()) {
                Text(
                    "Left out: " + leftOut.joinToString(", ") { (label, count) -> "$count $label" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (summary.remaining > 0) {
                Text(
                    (if (summary.stoppedEarly) "Stopped after several lookups in a row failed. " else "") +
                        "${summary.remaining} books still need looking up. Check the connection and " +
                        "choose the same file again to finish them.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun IsbnSummaryCard(summary: IsbnImportSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (summary.failed.isNotEmpty()) "Import paused" else "Import finished",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Added: ${summary.added}")
            Text("Already in your library: ${summary.alreadyInLibrary}")
            if (summary.withWarnings > 0) {
                Text(
                    "${summary.withWarnings} added without a cover, BISAC subjects or description; " +
                        "open them to fill those in.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (summary.notFound.isNotEmpty()) {
                Text("No details found, so not added: ${summary.notFound.joinToString(", ")}")
            }
            if (summary.invalid.isNotEmpty()) {
                Text("Not valid ISBNs: ${summary.invalid.joinToString(", ")}")
            }
            if (summary.failed.isNotEmpty()) {
                Text(
                    (if (summary.stoppedEarly) "Stopped after several lookups in a row failed. " else "") +
                        "The lookup services didn't all answer for ${summary.failed.joinToString(", ")}. " +
                        "Check the connection or wait a few minutes, then import the list again to finish them.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
