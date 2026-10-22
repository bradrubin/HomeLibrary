package com.example.bookcatalog.data.covers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Parcelable
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.parcelize.Parcelize
import kotlin.math.max

@Parcelize
data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) : Parcelable {
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
        val bytes = response.use {
            if (!it.isSuccessful) throw IOException("Cover request failed with HTTP ${it.code}")
            it.body?.bytes() ?: throw IOException("Cover response was empty")
        }
        val decoded = decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), MAX_EDGE)
        if (decoded.sourceWidth < MIN_COVER_WIDTH || decoded.sourceHeight < MIN_COVER_WIDTH) {
            Log.i(TAG, "Ignoring placeholder-sized cover for $isbn13")
            decoded.bitmap.recycle()
            return null
        }
        return save(resize(decoded.bitmap), "$isbn13.jpg")
    }

    /**
     * Decodes a camera photo upright, since ImageDecoder applies its EXIF orientation, then
     * straightens it with [transform], which is in pixels of the decoded photo.
     */
    fun decodeForEditing(file: File, transform: PhotoTransform? = null): Bitmap {
        val photo = decode(ImageDecoder.createSource(file), EDITING_MAX_EDGE).bitmap
        return transform(photo, transform).also { if (it !== photo) photo.recycle() }
    }

    /** Draws the photo through [transform] into a new bitmap; the original is left to the caller. */
    fun transform(bitmap: Bitmap, transform: PhotoTransform?): Bitmap {
        if (transform == null || transform.isIdentity) return bitmap
        val result = Bitmap.createBitmap(transform.width, transform.height, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply { setValues(transform.values) }
        Canvas(result).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return result
    }

    /**
     * Rotates clockwise, growing the bitmap so none of the photo is cut off. The original is
     * left for the caller to recycle, since it may still be on screen.
     */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { setRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun saveUserPhoto(file: File, crop: NormalizedCrop, transform: PhotoTransform? = null): String {
        val source = decodeForEditing(file, transform)
        val left = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (crop.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (crop.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        if (cropped !== source) source.recycle()
        return save(resize(cropped), "user-${UUID.randomUUID()}.jpg")
    }

    /** Decodes in software memory, so the pixels can be read and the bitmap transformed. */
    private fun decode(source: ImageDecoder.Source, maxEdge: Int): DecodedImage {
        var width = 0
        var height = 0
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            width = info.size.width
            height = info.size.height
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sampleSize(width, height, maxEdge))
        }
        return DecodedImage(bitmap, width, height)
    }

    /** Writes a JPEG through a temporary file, so a failed write never leaves a partial cover. */
    private fun save(bitmap: Bitmap, name: String): String {
        val target = File(coverDirectory, name)
        val temporary = File(coverDirectory, "$name.tmp")
        coverDirectory.mkdirs()
        try {
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IOException("Cover image could not be encoded")
                }
            }
            if (!temporary.renameTo(target)) throw IOException("Cover image could not be saved")
        } finally {
            bitmap.recycle()
            temporary.delete()
        }
        return target.name
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** Scales down to [MAX_EDGE], recycling the original when a new bitmap is made. */
    private fun resize(bitmap: Bitmap): Bitmap {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / longestEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        ).also { if (it !== bitmap) bitmap.recycle() }
    }

    private class DecodedImage(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)

    companion object {
        private const val TAG = "CoverStore"
        private const val MIN_COVER_WIDTH = 50
        private const val MAX_EDGE = 800
        private const val EDITING_MAX_EDGE = 1600
        private const val JPEG_QUALITY = 85
    }
}
