package com.example.bookcatalog.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bookcatalog.data.db.AppDatabase
import com.example.bookcatalog.data.db.Book
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BookRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val covers = File(context.cacheDir, "repository-covers").apply {
        deleteRecursively()
        mkdirs()
    }
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repository = BookRepository(database, covers)

    @After
    fun cleanUp() {
        database.close()
        covers.deleteRecursively()
    }

    @Test
    fun deletingBookKeepsCoverSharedWithAnotherCopy() = runBlocking {
        val cover = cover("shared.jpg")
        val first = repository.save(Book(title = "Dune", coverFile = cover.name))
        val second = repository.save(Book(title = "Dune", coverFile = cover.name))

        repository.delete(requireNotNull(repository.getBook(first)))
        assertTrue(cover.exists())

        repository.delete(requireNotNull(repository.getBook(second)))
        assertFalse(cover.exists())
    }

    @Test
    fun replacingCoverDeletesTheOldOne() = runBlocking {
        val old = cover("old.jpg")
        val new = cover("new.jpg")
        val id = repository.save(Book(title = "Emma", coverFile = old.name))

        repository.save(requireNotNull(repository.getBook(id)).copy(coverFile = new.name))

        assertFalse(old.exists())
        assertTrue(new.exists())
    }

    @Test
    fun duplicatingCopiesTheBookAndItsCover() = runBlocking {
        val cover = cover("original.jpg")
        val id = repository.save(Book(title = "Dune", format = "Hardcover", rating = 5, coverFile = cover.name))
        val original = requireNotNull(repository.getBook(id))

        val copy = requireNotNull(repository.getBook(repository.duplicate(original)))

        assertNotEquals(original.id, copy.id)
        assertEquals(original.copy(id = copy.id, coverFile = copy.coverFile, dateAdded = copy.dateAdded, dateModified = copy.dateModified), copy)
        val copiedCover = File(covers, requireNotNull(copy.coverFile))
        assertNotEquals(cover.name, copiedCover.name)
        assertTrue(copiedCover.readBytes().contentEquals(cover.readBytes()))

        repository.delete(original)
        assertFalse(cover.exists())
        assertTrue(copiedCover.exists())
    }

    @Test
    fun sweepDeletesOnlyOldUnusedCovers() = runBlocking {
        val used = cover("used.jpg").apply { setLastModified(0) }
        val abandoned = cover("abandoned.jpg").apply { setLastModified(0) }
        val recent = cover("recent.jpg")
        repository.save(Book(title = "Odyssey", coverFile = used.name))

        repository.deleteUnusedCovers(olderThanMillis = 60_000)

        assertTrue(used.exists())
        assertFalse(abandoned.exists())
        assertTrue(recent.exists())
    }

    private fun cover(name: String) = File(covers, name).apply { writeBytes(byteArrayOf(1)) }
}
