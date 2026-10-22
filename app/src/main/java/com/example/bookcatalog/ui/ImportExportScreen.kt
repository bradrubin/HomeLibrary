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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.bookcatalog.data.io.DuplicateHandling
import com.example.bookcatalog.data.io.ImportOptions
import com.example.bookcatalog.data.io.ImportSummary
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
    val exportZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.export(it, zip = true) } }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.export(it, zip = false) } }
    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.import(it, ImportOptions(duplicates, fetchMissingCovers)) } }
    val baseName = "book-catalog-${LocalDate.now()}"

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
