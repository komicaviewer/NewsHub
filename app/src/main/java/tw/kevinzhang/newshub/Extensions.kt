package tw.kevinzhang.newshub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.Modifier
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun String.toBitmap(): Bitmap {
    val imageBytes = android.util.Base64.decode(this, android.util.Base64.DEFAULT)
    return imageBytes.toBitmap()
}

fun ByteArray.toBitmap() =
    BitmapFactory.decodeByteArray(this, 0, this.size)

fun Long.toMMDDString(): String = OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault()).format(
    DateTimeFormatter.ofPattern("M/d"))

fun Long.toHumanTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    val diffSeconds = diff / 1000
    val diffMinutes = diff / (60 * 1000)
    val diffHours = diff / (60 * 60 * 1000)
    val diffDays = diff / (24 * 60 * 60 * 1000)
    return when {
        diffSeconds < 60 -> "just now"
        diffMinutes < 60 -> "${diffMinutes}m"
        diffHours < 24 -> "${diffHours}h"
        else -> this.toMMDDString()
    }
}

fun String.encode() = URLEncoder.encode(this, "utf-8")

fun Int?.isZeroOrNull() = this == 0 || this == null

fun <T> Modifier.thenIfNotNull(any: T?, callback: Modifier.(T) -> Modifier): Modifier {
    return if (any != null) {
        then(callback(any))
    } else {
        this
    }
}
