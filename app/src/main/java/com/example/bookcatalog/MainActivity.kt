package com.example.bookcatalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bookcatalog.data.metadata.MetadataLookupResult
import com.example.bookcatalog.ui.BarcodeScanScreen
import com.example.bookcatalog.ui.BarcodeScanViewModel
import com.example.bookcatalog.ui.BookEditScreen
import com.example.bookcatalog.ui.BookEditViewModel
import com.example.bookcatalog.ui.ImportExportScreen
import com.example.bookcatalog.ui.ImportExportViewModel
import com.example.bookcatalog.ui.IsbnLookupScreen
import com.example.bookcatalog.ui.IsbnLookupViewModel
import com.example.bookcatalog.ui.LibraryScreen
import com.example.bookcatalog.ui.LibraryViewModel
import com.example.bookcatalog.ui.theme.BookCatalogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BookCatalogApp).container

        setContent {
            BookCatalogTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BookCatalogNavigation(container)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BookCatalogNavigation(container: AppContainer) {
    val repository = container.bookRepository
    val metadataRepository = container.metadataRepository
    val coverStore = container.coverStore
    val navController = rememberNavController()
    var pendingLookup by remember { mutableStateOf<MetadataLookupResult?>(null) }
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            val viewModel: LibraryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LibraryViewModel(repository, container.libraryExporter, container.contentResolver)
                    }
                },
            )
            LibraryScreen(
                viewModel = viewModel,
                coverDirectory = coverStore.coverDirectory,
                onAddBook = { navController.navigate("edit/new") },
                onScanBook = { navController.navigate("scan") },
                onLookupBook = { navController.navigate("lookup") },
                onEditBook = { id -> navController.navigate("edit/$id") },
                onImportExport = { navController.navigate("import-export") },
            )
        }
        composable("import-export") {
            ImportExportScreen(
                viewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ImportExportViewModel(
                                container.libraryExporter,
                                container.libraryImporter,
                                container.amazonImporter,
                                container.contentResolver,
                            )
                        }
                    },
                ),
                onBack = { navController.popBackStack() },
            )
        }
        composable("lookup") {
            val viewModel: IsbnLookupViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { IsbnLookupViewModel(metadataRepository) }
                },
            )
            IsbnLookupScreen(
                viewModel = viewModel,
                onLookupComplete = { result ->
                    pendingLookup = result
                    navController.navigate("edit/new")
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable("scan") {
            val viewModel: BarcodeScanViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        BarcodeScanViewModel(repository, metadataRepository)
                    }
                },
            )
            BarcodeScanScreen(
                viewModel = viewModel,
                onBookFound = { result ->
                    pendingLookup = result
                    navController.navigate("edit/new")
                },
                onOpenExisting = { id ->
                    navController.navigate("edit/$id") {
                        popUpTo("scan") { inclusive = true }
                    }
                },
                onEnterIsbnManually = { navController.navigate("lookup") },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = "edit/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            val viewModel: BookEditViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { BookEditViewModel(repository, metadataRepository) }
                },
            )
            val bookId = entry.arguments?.getString("bookId")?.toLongOrNull()
            BookEditScreen(
                bookId = bookId,
                viewModel = viewModel,
                initialBook = pendingLookup?.book.takeIf { bookId == null },
                lookupMessage = pendingLookup?.message.takeIf { bookId == null },
                lookupWarning = pendingLookup?.warning.takeIf { bookId == null },
                coverDirectory = coverStore.coverDirectory,
                coverStore = coverStore,
                onDone = {
                    pendingLookup = null
                    navController.popBackStack()
                },
                onSaved = {
                    pendingLookup = null
                    navController.navigate("library") {
                        popUpTo("library") { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
