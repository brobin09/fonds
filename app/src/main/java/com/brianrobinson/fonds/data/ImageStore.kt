package com.brianrobinson.fonds.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Page images live in app-private storage: files/scans/<collectionId>/<uuid>.jpg */
class ImageStore(private val context: Context) {

    private fun dirFor(collectionId: Long): File =
        File(context.filesDir, "scans/$collectionId").apply { mkdirs() }

    fun importUri(collectionId: Long, uri: Uri): String {
        val out = File(dirFor(collectionId), "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open scanned image" }
            out.outputStream().use { input.copyTo(it) }
        }
        return out.absolutePath
    }

    fun delete(paths: List<String>) {
        paths.forEach { runCatching { File(it).delete() } }
    }

    fun deleteCollectionDir(collectionId: Long) {
        runCatching { File(context.filesDir, "scans/$collectionId").deleteRecursively() }
    }

    fun totalBytes(): Long =
        File(context.filesDir, "scans").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        /** Decode a downsampled bitmap whose long edge is at most [maxEdge]. */
        fun decodeScaled(path: String, maxEdge: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0) return null
            var sample = 1
            while (longEdge / (sample * 2) >= maxEdge) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
            val edge = maxOf(bmp.width, bmp.height)
            if (edge <= maxEdge) return bmp
            val scale = maxEdge.toFloat() / edge
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled != bmp) bmp.recycle()
            return scaled
        }

        fun jpegBytes(path: String, maxEdge: Int, quality: Int = 85): ByteArray? {
            val bmp = decodeScaled(path, maxEdge) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bmp.recycle()
            return out.toByteArray()
        }
    }
}
