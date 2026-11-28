package com.arwora.feature.home.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.json.JSONObject
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dynamic Weather Live Wallpaper rendering dark atmospheric sky gradients and
 * animated rain streaks, clouds, sunlit glow, or twinkling stars from cached weather state.
 */
class WeatherLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WeatherWallpaperEngine()

    private inner class WeatherWallpaperEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var phase = 0f
        private var conditionType = WeatherConditionType.CLEAR
        private var isDay = true
        private var lastSnapshotCheckMs = 0L

        private val dropPositions = FloatArray(45) { Random.nextFloat() }
        private val dropX = FloatArray(45) { Random.nextFloat() }
        private val dropSpeed = FloatArray(45) { 0.015f + Random.nextFloat() * 0.025f }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                handler.postDelayed(this, 33L)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                refreshConditionFromCache()
                handler.removeCallbacks(drawRunnable)
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        private fun refreshConditionFromCache() {
            val now = System.currentTimeMillis()
            if (now - lastSnapshotCheckMs < 15_000L) return
            lastSnapshotCheckMs = now
            runCatching {
                val prefs = getSharedPreferences(PrefsWeatherCacheStore.PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString("last_weather_snapshot", null) ?: return@runCatching
                val snap = WeatherProParser.snapshotFromJson(JSONObject(raw))
                conditionType = snap.conditionType
                isDay = snap.isDay
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderAtmosphere(canvas)
                }
            } finally {
                if (canvas != null) {
                    runCatching { holder.unlockCanvasAndPost(canvas) }
                }
            }
        }

        private fun renderAtmosphere(canvas: Canvas) {
            val w = canvas.width.toFloat().coerceAtLeast(1f)
            val h = canvas.height.toFloat().coerceAtLeast(1f)
            phase += 0.03f

            val (topColor, bottomColor) = when (conditionType) {
                WeatherConditionType.RAIN,
                WeatherConditionType.DRIZZLE -> Color.parseColor("#0B132B") to Color.parseColor("#1C2541")
                WeatherConditionType.THUNDERSTORM -> Color.parseColor("#090A1A") to Color.parseColor("#241B4B")
                WeatherConditionType.CLOUDY,
                WeatherConditionType.FOG -> Color.parseColor("#141B29") to Color.parseColor("#263248")
                WeatherConditionType.SNOW -> Color.parseColor("#132033") to Color.parseColor("#2D4263")
                else -> if (isDay) {
                    Color.parseColor("#0F172A") to Color.parseColor("#1E3A5F")
                } else {
                    Color.parseColor("#050816") to Color.parseColor("#111B33")
                }
            }

            bgPaint.shader = LinearGradient(0f, 0f, 0f, h, topColor, bottomColor, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            when (conditionType) {
                WeatherConditionType.RAIN,
                WeatherConditionType.DRIZZLE,
                WeatherConditionType.THUNDERSTORM -> {
                    particlePaint.color = Color.argb(120, 125, 211, 252)
                    particlePaint.strokeWidth = 3f
                    for (i in dropPositions.indices) {
                        dropPositions[i] = (dropPositions[i] + dropSpeed[i]) % 1f
                        val x = dropX[i] * w
                        val y = dropPositions[i] * h
                        canvas.drawLine(x, y, x - 6f, y + 36f, particlePaint)
                    }
                }
                WeatherConditionType.SNOW -> {
                    particlePaint.color = Color.argb(175, 226, 232, 240)
                    for (i in dropPositions.indices) {
                        dropPositions[i] = (dropPositions[i] + dropSpeed[i] * 0.45f) % 1f
                        val x = (dropX[i] * w) + sin(phase + i).toFloat() * 14f
                        val y = dropPositions[i] * h
                        canvas.drawCircle(x, y, 4.5f, particlePaint)
                    }
                }
                WeatherConditionType.CLOUDY,
                WeatherConditionType.PARTLY_CLOUDY,
                WeatherConditionType.FOG -> {
                    glowPaint.color = Color.argb(38, 148, 163, 184)
                    for (i in 0..4) {
                        val cx = ((w * 0.25f * i + phase * 18f) % (w + 260f)) - 130f
                        val cy = h * (0.18f + i * 0.08f)
                        canvas.drawCircle(cx, cy, 130f + i * 20f, glowPaint)
                    }
                }
                WeatherConditionType.CLEAR -> {
                    if (isDay) {
                        glowPaint.color = Color.argb(55, 245, 158, 11)
                        val pulse = 150f + sin(phase).toFloat() * 18f
                        canvas.drawCircle(w * 0.78f, h * 0.22f, pulse, glowPaint)
                    } else {
                        particlePaint.color = Color.argb(180, 241, 245, 249)
                        for (i in 0..24) {
                            val sx = dropX[i] * w
                            val sy = (dropPositions[i] * 0.55f) * h
                            val r = 2f + sin(phase * 1.4f + i).toFloat() * 1.5f
                            canvas.drawCircle(sx, sy, r.coerceAtLeast(1f), particlePaint)
                        }
                    }
                }
            }
        }
    }
}
