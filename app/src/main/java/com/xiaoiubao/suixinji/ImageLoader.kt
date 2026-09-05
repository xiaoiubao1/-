package com.xiaoiubao.suixinji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache

/** Call from a background thread. Cached bitmaps must not be recycled by consumers. */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    fun load(context: Context, uri: String, width: Int = 1440, height: Int = 1920): Bitmap? {
        val key = "$uri@$width:$height"
        cache.get(key)?.let { return it }
        return try {
            val resolver = context.contentResolver
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
            }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?.also { cache.put(key, it) }
        } catch (_: Exception) { null }
    }
    internal fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        require(targetWidth > 0 && targetHeight > 0)
        var sample = 1
        while (width / sample > targetWidth || height / sample > targetHeight) sample *= 2
        return sample
    }
    fun invalidateAll() { cache.evictAll() }
}
