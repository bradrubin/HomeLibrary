package com.example.bookcatalog.ui

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bookcatalog.data.db.Book
import com.example.bookcatalog.data.metadata.MetadataLookupResult
import com.example.bookcatalog.domain.IsbnUtils
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BarcodeScanScreen(
    viewModel: BarcodeScanViewModel,
    onBookFound: (MetadataLookupResult) -> Unit,
    onOpenExisting: (Long) -> Unit,
    onEnterIsbnManually: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var isLookingUp by remember { mutableStateOf(false) }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicateBook by remember { mutableStateOf<Book?>(null) }

    fun lookUpBook(isbn13: String) {
        scope.launch {
            isLookingUp = true
            scanError = null
            try {
                onBookFound(viewModel.lookup(isbn13))
            } catch (exception: SQLiteException) {
                Log.e(TAG, "Could not add scanned book to the library", exception)
                scanError = "Could not access the library. Please try again."
            } finally {
                isLookingUp = false
            }
        }
    }

    fun onScannedValue(rawValue: String?) {
        val isbn13 = rawValue?.let(IsbnUtils::fromBarcode)
        if (isbn13 == null) {
            scanError =
                "That barcode isn't a valid book ISBN. Enter the ISBN manually or scan a 13-digit book barcode."
            return
        }

        scope.launch {
            isLookingUp = true
            scanError = null
            try {
                val existingBook = viewModel.findExistingBook(isbn13)
                if (existingBook != null) {
                    duplicateBook = existingBook
                } else {
                    onBookFound(viewModel.lookup(isbn13))
                }
            } catch (exception: SQLiteException) {
                Log.e(TAG, "Could not process scanned ISBN $isbn13", exception)
                scanError = "Could not access your library. Please try again."
            } finally {
                isLookingUp = false
            }
        }
    }

    fun startScan() {
        scanError = null
        isScanning = true
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                isScanning = false
                onScannedValue(barcode.rawValue)
            }
            .addOnCanceledListener {
                isScanning = false
                onCancel()
            }
            .addOnFailureListener { exception ->
                isScanning = false
                Log.e(TAG, "Google Code Scanner could not start", exception)
                scanError = if (CommonStatusCodes.API_NOT_CONNECTED == statusCode(exception)) {
                    "Google Play services can't start the scanner. Update Google Play services or enter the ISBN manually."
                } else {
                    "Couldn't start the scanner. Try again or enter the ISBN manually."
                }
            }
    }

    LaunchedEffect(Unit) {
        startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan book barcode") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Point your camera at the 13-digit barcode on the back of a book.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (isLookingUp) {
                CircularProgressIndicator()
                Text("Checking your library and looking up book details…")
            } else {
                Button(
                    onClick = ::startScan,
                    enabled = !isScanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isScanning) "Opening scanner…" else "Scan barcode")
                }
            }

            scanError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedButton(
                onClick = onEnterIsbnManually,
                enabled = !isLookingUp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enter ISBN manually")
            }
        }
    }

    duplicateBook?.let { existingBook ->
        AlertDialog(
            onDismissRequest = { duplicateBook = null },
            title = { Text("Already in your library") },
            text = {
                Text(
                    "“${existingBook.title}” is already in your library. " +
                        "Open it or add another copy?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        duplicateBook = null
                        onOpenExisting(existingBook.id)
                    },
                ) {
                    Text("Open book")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { duplicateBook = null }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            duplicateBook = null
                            lookUpBook(existingBook.isbn13.orEmpty())
                        },
                    ) {
                        Text("Add another copy")
                    }
                }
            },
        )
    }
}

private fun statusCode(exception: Exception): Int? =
    (exception as? com.google.android.gms.common.api.ApiException)?.statusCode

private const val TAG = "BarcodeScan"
