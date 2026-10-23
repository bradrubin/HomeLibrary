package com.example.bookcatalog.ui

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.bookcatalog.domain.IsbnUtils

/**
 * Appends the X check digit that the numeric keypad can't type, shown only once [value] is the
 * first nine digits of an ISBN-10 that ends in X.
 */
@Composable
internal fun IsbnCheckXButton(value: String, onValueChange: (String) -> Unit) {
    if (IsbnUtils.needsIsbn10CheckX(value)) {
        TextButton(onClick = { onValueChange(value.trimEnd() + "X") }) {
            Text("X")
        }
    }
}
