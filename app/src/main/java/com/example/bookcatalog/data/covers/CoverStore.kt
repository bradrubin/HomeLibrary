package com.example.bookcatalog.data.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f)
        require(right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }
}

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
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
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

    fun decodeForEditing(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Captured photo could not be decoded")
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, EDITING_MAX_EDGE)
            },
        ) ?: throw IOException("Captured photo could not be decoded")
        return orientBitmap(file, bitmap)
    }

    fun saveUserPhoto(file: File, crop: NormalizedCrop): String {
        val source = decodeForEditing(file)
        val left = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (crop.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (crop.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        if (cropped !== source) source.recycle()
        val resized = resize(cropped)
        if (resized !== cropped) cropped.recycle()

        val target = File(coverDirectory, "user-${UUID.randomUUID()}.jpg")
        val temporary = File(coverDirectory, "${target.name}.tmp")
        coverDirectory.mkdirs()
        try {
            writeBitmap(resized, temporary)
            if (!temporary.renameTo(target)) {
                throw IOException("Cover image could not be saved")
            }
        } finally {
            resized.recycle()
            temporary.delete()
        }
        return target.name
    }

    private fun orientBitmap(file: File, bitmap: Bitmap): Bitmap {
        val orientation = ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
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

    private fun writeBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                throw IOException("Cover image could not be encoded")
            }
        }
    }

    companion object {
        private const val TAG = "CoverStore"
        private const val MIN_COVER_WIDTH = 50
        private const val MAX_EDGE = 800
        private const val EDITING_MAX_EDGE = 1600
        private const val JPEG_QUALITY = 85
    }
}
