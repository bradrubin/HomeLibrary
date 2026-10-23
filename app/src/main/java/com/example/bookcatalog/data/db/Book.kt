package com.example.bookcatalog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["isbn13"]), Index(value = ["asin"])],
)
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val isbn13: String? = null,
    val isbn10: String? = null,
    /** Amazon's ID for a Kindle or Audible edition, which has no ISBN of its own. */
    val asin: String? = null,
    val title: String,
    val subtitle: String? = null,
    val authors: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val format: String? = null,
    val language: String? = null,
    val subjects: String? = null,
    val description: String? = null,
    val coverFile: String? = null,
    val coverSourceUrl: String? = null,
    val dataSource: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val rating: Int? = null,
    val readStatus: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
)
