package com.example.bookcatalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bookcatalog.data.metadata.MetadataLookupResult
import com.example.bookcatalog.domain.IsbnUtils
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun IsbnLookupScreen(
    viewModel: IsbnLookupViewModel,
    onLookupComplete: (MetadataLookupResult) -> Unit,
    onCancel: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var isLookingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val isbn13 = remember(input) { IsbnUtils.toIsbn13(input) }

    fun startLookup() {
        val validIsbn13 = isbn13 ?: return
        keyboardController?.hide()
        scope.launch {
            isLookingUp = true
            try {
                onLookupComplete(viewModel.lookup(validIsbn13))
            } finally {
                isLookingUp = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter ISBN") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Enter a book’s ISBN-10 or ISBN-13 to look up its details.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ISBN") },
                placeholder = {
                    Text(
                        "e.g. 9780385533225",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                },
                singleLine = true,
                isError = input.isNotBlank() && isbn13 == null,
                supportingText = {
                    when {
                        input.isBlank() -> Text("Hyphens and spaces are accepted.")
                        isbn13 != null && isbn13 != IsbnUtils.normalize(input) ->
                            Text("Will look up ISBN-13: $isbn13")
                        isbn13 != null -> Text("Valid ISBN")
                        else -> Text("Enter a valid ISBN-10 or ISBN-13 check digit.")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { startLookup() }),
            )
            Button(
                onClick = { startLookup() },
                enabled = isbn13 != null && !isLookingUp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLookingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text("Looking up…")
                } else {
                    Text("Look up book")
                }
            }
            Text(
                "You can also add the book manually if its ISBN is unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
