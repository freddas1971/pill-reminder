package com.pillreminder.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object Photos {

    fun photoDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    /** Where the camera writes its picture before we shrink and keep it. */
    fun cameraTempFile(context: Context): File =
        File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "capture.jpg") }

    /** Copy a picture (from camera or gallery) into app storage, shrunk and upright. Returns the new path. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1200) }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        bmp = scaleDown(bmp, 1024)

        val out = File(photoDir(context), "${UUID.randomUUID()}.jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        out.absolutePath
    }.getOrNull()

    fun load(path: String?, maxSide: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    /** Side-by-side picture of several pills, for the notification. */
    fun collage(paths: List<String>): Bitmap? {
        val bitmaps = paths.take(4).mapNotNull { load(it, 600) }
        if (bitmaps.isEmpty()) return null
        if (bitmaps.size == 1) return bitmaps[0]
        val side = 480
        val gap = 12
        val out = Bitmap.createBitmap(side * bitmaps.size + gap * (bitmaps.size - 1), side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        bitmaps.forEachIndexed { i, b ->
            val s = minOf(b.width, b.height)
            val src = Rect((b.width - s) / 2, (b.height - s) / 2, (b.width + s) / 2, (b.height + s) / 2)
            val left = i * (side + gap)
            canvas.drawBitmap(b, src, Rect(left, 0, left + side, side), paint)
        }
        return out
    }

    /** Remove pictures that no pill uses any more. */
    fun cleanUp(context: Context, used: Set<String>) {
        photoDir(context).listFiles()?.forEach { f ->
            if (f.absolutePath !in used && System.currentTimeMillis() - f.lastModified() > 60 * 60 * 1000L) f.delete()
        }
    }

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (w / (s * 2) >= target && h / (s * 2) >= target) s *= 2
        return s
    }

    private fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(b.width, b.height)
        if (longest <= maxSide) return b
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }
}
