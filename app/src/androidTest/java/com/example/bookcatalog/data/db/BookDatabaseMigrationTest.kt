package com.example.bookcatalog.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrationKeepsBooksAndAllowsMultipleCopiesWithSameIsbn() {
        val isbn13 = "9780385533225"
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO books (isbn13, title, dateAdded, dateModified)
                VALUES ('$isbn13', 'Existing copy', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )
        database.execSQL(
            """
            INSERT INTO books (isbn13, title, dateAdded, dateModified)
            VALUES ('$isbn13', 'Second copy', 2, 2)
            """.trimIndent(),
        )

        database.query(
            "SELECT COUNT(*) FROM books WHERE isbn13 = ?",
            arrayOf(isbn13),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        database.close()
    }

    @Test
    fun migrationAddsEmptyFormatToExistingBooks() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL("INSERT INTO books (title, dateAdded, dateModified) VALUES ('Existing', 1, 1)")
            close()
        }

        val database = helper.runMigrationsAndValidate(TEST_DATABASE, 3, true, AppDatabase.MIGRATION_2_3)

        database.query("SELECT title, format FROM books").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Existing", cursor.getString(0))
            assertEquals(true, cursor.isNull(1))
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
