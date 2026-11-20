package com.arwora

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

/**
 * View-based splash controller supporting dynamic Dark (#000000/#FFFFFF) and
 * Light (#FFFFFF/#000000) system theme matching with a 600ms entrance animation.
 */
class SplashActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var animatorSet: AnimatorSet? = null

    private val navigateRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    putExtra("skip_splash", true)
                }
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val bgColor = if (isSystemDark) Color.BLACK else Color.WHITE
        val fgColor = if (isSystemDark) Color.WHITE else Color.BLACK

        val root = findViewById<FrameLayout>(R.id.splash_root)
        val wordmark = findViewById<ImageView>(R.id.iv_arwora_wordmark)

        root.setBackgroundColor(bgColor)
        window.decorView.setBackgroundColor(bgColor)
        wordmark.setColorFilter(fgColor)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isSystemDark
        insetsController.isAppearanceLightNavigationBars = !isSystemDark

        val expressiveInterpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
        val fadeIn = ObjectAnimator.ofFloat(wordmark, View.ALPHA, 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(wordmark, View.SCALE_X, 0.85f, 1f)
        val scaleY = ObjectAnimator.ofFloat(wordmark, View.SCALE_Y, 0.85f, 1f)

        animatorSet = AnimatorSet().apply {
            playTogether(fadeIn, scaleX, scaleY)
            duration = 600L
            interpolator = expressiveInterpolator
            start()
        }

        handler.postDelayed(navigateRunnable, 1300L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        animatorSet?.cancel()
        super.onDestroy()
    }
}
