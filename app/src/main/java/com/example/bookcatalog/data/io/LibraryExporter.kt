package com.example.bookcatalog.data.io

import java.io.File
import java.io.OutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryExporter(
    private val store: LibraryStore,
    private val coverDirectory: File,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun exportCsv(output: OutputStream): ExportSummary {
        val books = store.allBooks()
        BookCsv.write(books, output, zone)
        return ExportSummary(books = books.size, covers = 0)
    }

    /** Writes books.csv and a covers folder holding each book's cover file. */
    suspend fun exportZip(
        output: OutputStream,
        onProgress: (TransferProgress) -> Unit = {},
    ): ExportSummary {
        val books = store.allBooks()
        val coverNames = books.mapNotNull { it.coverFile }.distinct()
        var covers = 0
        var missing = 0
        val zip = ZipOutputStream(output)
        zip.putNextEntry(ZipEntry(ZipBundle.CSV_ENTRY))
        BookCsv.write(books, zip, zone)
        zip.closeEntry()
        coverNames.forEachIndexed { index, name ->
            onProgress(TransferProgress("Adding covers", index, coverNames.size))
            val file = File(coverDirectory, name)
            if (ZipBundle.isSafeCoverName(name) && file.isFile) {
                zip.putNextEntry(ZipEntry(ZipBundle.COVER_FOLDER + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                covers++
            } else {
                missing++
            }
        }
        // Finishes the archive without closing the caller's stream.
        zip.finish()
        zip.flush()
        return ExportSummary(books = books.size, covers = covers, missingCovers = missing)
    }
}

internal object ZipBundle {
    const val CSV_ENTRY = "books.csv"
    const val COVER_FOLDER = "covers/"

    /** Plain file names only, so an archive entry can't write outside the covers folder. */
    fun isSafeCoverName(name: String): Boolean =
        name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) && name.length <= 128
}
