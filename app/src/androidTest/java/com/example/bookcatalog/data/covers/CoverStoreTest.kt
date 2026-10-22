package com.example.bookcatalog.data.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CoverStoreTest {
    @Test
    fun savesCroppedCoverAtSourceCropResolutionWithinCoverLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(context.cacheDir, "cover-crop-test.jpg")
        val store = CoverStore(context.filesDir, OkHttpClient())
        var savedCover: File? = null
        val sourceBitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(sourceFile).use { output ->
                check(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }

            val savedName = store.saveUserPhoto(
                sourceFile,
                NormalizedCrop(left = 0.25f, top = 0.25f, right = 0.75f, bottom = 0.75f),
            )
            savedCover = File(store.coverDirectory, savedName)
            val savedBitmap = BitmapFactory.decodeFile(savedCover.absolutePath)
            try {
                assertEquals(600, savedBitmap.width)
                assertEquals(800, savedBitmap.height)
            } finally {
                savedBitmap.recycle()
            }
        } finally {
            sourceBitmap.recycle()
            sourceFile.delete()
            savedCover?.delete()
        }
    }
}
