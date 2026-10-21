package com.example.bookcatalog.data.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

class CoverStore(
    private val filesDirectory: File,
    private val httpClient: OkHttpClient,
) {
    val coverDirectory: File
        get() = File(filesDirectory, "covers")

    fun download(isbn13: String, sourceUrl: String): String? {
        val response = httpClient.newCall(
            Request.Builder().url(sourceUrl).header("User-Agent", "BookCatalog/1.0").build(),
        ).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("Cover request failed with HTTP ${it.code}")
            val bytes = it.body?.bytes() ?: throw IOException("Cover response was empty")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth < MIN_COVER_WIDTH || bounds.outHeight < MIN_COVER_WIDTH) {
                Log.i(TAG, "Ignoring placeholder-sized cover for $isbn13")
                return null
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw IOException("Cover image could not be decoded")
            val resized = resize(decoded)
            if (resized !== decoded) decoded.recycle()

            val target = File(coverDirectory, "$isbn13.jpg")
            val temporary = File(coverDirectory, "$isbn13.jpg.tmp")
            coverDirectory.mkdirs()
            try {
                FileOutputStream(temporary).use { output ->
                    if (!resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        throw IOException("Cover image could not be encoded")
                    }
                }
                if (!temporary.renameTo(target)) {
                    throw IOException("Cover image could not be saved")
                }
            } finally {
                resized.recycle()
                temporary.delete()
            }
            return target.name
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
        return sample
    }

    private fun resize(bitmap: Bitmap): Bitmap {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / longestEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        private const val TAG = "CoverStore"
        private const val MIN_COVER_WIDTH = 50
        private const val MAX_EDGE = 800
        private const val JPEG_QUALITY = 85
    }
}
