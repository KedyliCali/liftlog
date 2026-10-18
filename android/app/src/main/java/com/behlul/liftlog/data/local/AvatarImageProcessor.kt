package com.behlul.liftlog.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Turns any picked photo (often a 4000 px, 5 MB camera image) into a small square JPEG before upload,
 * so it fits the server's 512 KB limit and loads instantly on a slow connection.
 */
interface AvatarEncoder {
    suspend fun toAvatarJpeg(uri: Uri): ByteArray
}

@Singleton
class AvatarImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) : AvatarEncoder {

    override suspend fun toAvatarJpeg(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val decoded = decodeDownsampled(uri)
        val square = centerCropSquare(decoded)
        val scaled = square.scale(AVATAR_SIZE_PX, AVATAR_SIZE_PX)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun decodeDownsampled(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF rotation, so portrait photos stay upright
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val shortSide = min(info.size.width, info.size.height)
                val sample = max(1, shortSide / (AVATAR_SIZE_PX * 2))
                decoder.setTargetSampleSize(sample)
                // Software bitmap: hardware bitmaps cannot be cropped or compressed
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val shortSide = min(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = max(1, shortSide / (AVATAR_SIZE_PX * 2)) }
        return context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode image")
    }

    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        val side = min(bitmap.width, bitmap.height)
        return Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    }

    companion object {
        const val AVATAR_SIZE_PX = 256
        private const val JPEG_QUALITY = 85
    }
}
