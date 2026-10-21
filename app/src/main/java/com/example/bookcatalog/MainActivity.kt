package com.example.bookcatalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bookcatalog.data.BookRepository
import com.example.bookcatalog.ui.BookEditScreen
import com.example.bookcatalog.ui.BookEditViewModel
import com.example.bookcatalog.ui.LibraryScreen
import com.example.bookcatalog.ui.LibraryViewModel
import com.example.bookcatalog.ui.theme.BookCatalogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as BookCatalogApp).container.bookRepository

        setContent {
            BookCatalogTheme(darkTheme = isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BookCatalogNavigation(repository)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BookCatalogNavigation(repository: BookRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            val viewModel: LibraryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { LibraryViewModel(repository) }
                },
            )
            LibraryScreen(
                viewModel = viewModel,
                onAddBook = { navController.navigate("edit/new") },
                onEditBook = { id -> navController.navigate("edit/$id") },
            )
        }
        composable(
            route = "edit/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            val viewModel: BookEditViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { BookEditViewModel(repository) }
                },
            )
            val bookId = entry.arguments?.getString("bookId")?.toLongOrNull()
            BookEditScreen(
                bookId = bookId,
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
