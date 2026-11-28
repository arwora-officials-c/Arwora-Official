package com.arwora.feature.home.weather

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composites real-time temperature, weather condition badge, locality, and timestamp
 * onto captured Weather Camera photos and saves them cleanly via zero-permission MediaStore.
 */
object WeatherCameraHelper {

    fun stampWeatherOverlayOnBitmap(
        source: Bitmap,
        snapshot: WeatherSnapshot,
        timestampText: String = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date())
    ): Bitmap {
        val mutable = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val canvas = Canvas(mutable)
        val w = mutable.width.toFloat().coerceAtLeast(1f)
        val h = mutable.height.toFloat().coerceAtLeast(1f)
        val scale = (w / 1080f).coerceIn(0.4f, 2.2f)

        val bannerHeight = (220f * scale).coerceAtMost(h * 0.4f)
        val bannerTop = h - bannerHeight

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                bannerTop - (60f * scale),
                0f,
                h,
                intArrayOf(Color.TRANSPARENT, Color.argb(190, 10, 15, 30), Color.argb(235, 8, 12, 24)),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, (bannerTop - 60f * scale).coerceAtLeast(0f), w, h, gradientPaint)

        val pad = 32f * scale
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val condPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 30f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 24f * scale
        }

        val baselineBottom = h - pad
        canvas.drawText(
            "${snapshot.localityName} • $timestampText",
            pad,
            baselineBottom,
            metaPaint
        )
        canvas.drawText(
            "${snapshot.conditionLabel}  •  Humidity ${snapshot.humidityPercent}%  •  Wind ${snapshot.windSpeedKmh} km/h",
            pad,
            baselineBottom - (38f * scale),
            condPaint
        )
        canvas.drawText(
            "${snapshot.temperatureCelsius}°C",
            pad,
            baselineBottom - (86f * scale),
            tempPaint
        )

        // Subtle Arwora stamp pill on bottom-right
        val pillText = "ARWORA WEATHER CAM"
        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pillWidth = pillTextPaint.measureText(pillText) + (28f * scale)
        val pillHeight = 38f * scale
        val pillRect = RectF(
            w - pad - pillWidth,
            bannerTop + (28f * scale),
            w - pad,
            bannerTop + (28f * scale) + pillHeight
        )
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 30, 41, 59)
        }
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillBg)
        canvas.drawText(
            pillText,
            pillRect.left + (14f * scale),
            pillRect.centerY() + (6f * scale),
            pillTextPaint
        )

        return mutable
    }

    fun saveStampedBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val fileName = "Arwora_Weather_${System.currentTimeMillis()}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ArworaWeather")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            }.getOrNull()
        } else {
            runCatching {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ArworaWeather").apply {
                    mkdirs()
                }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }
    }
}
