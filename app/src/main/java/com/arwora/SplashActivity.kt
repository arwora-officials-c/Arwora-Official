package com.arwora

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * View-based splash controller supporting dynamic Dark (#000000/#FFFFFF) and
 * Light (#FFFFFF/#000000) system theme matching with a 700ms entrance animation
 * (fade + scale + slide-up) and 500ms hold.
 */
class SplashActivity : ComponentActivity() {

    companion object {
        internal const val ANIM_DURATION_MS = 700L
        internal const val HOLD_DURATION_MS = 500L
        internal const val TOTAL_DURATION_MS = ANIM_DURATION_MS + HOLD_DURATION_MS
        internal const val INITIAL_SCALE = 0.82f
        internal const val INITIAL_TRANSLATION_Y_DP = 24f
    }

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

        val initialOffsetPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            INITIAL_TRANSLATION_Y_DP,
            resources.displayMetrics
        )
        wordmark.alpha = 0f
        wordmark.scaleX = INITIAL_SCALE
        wordmark.scaleY = INITIAL_SCALE
        wordmark.translationY = initialOffsetPx

        val fadeIn = ObjectAnimator.ofFloat(wordmark, View.ALPHA, 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(wordmark, View.SCALE_X, INITIAL_SCALE, 1f)
        val scaleY = ObjectAnimator.ofFloat(wordmark, View.SCALE_Y, INITIAL_SCALE, 1f)
        val slideUp = ObjectAnimator.ofFloat(wordmark, View.TRANSLATION_Y, initialOffsetPx, 0f)

        animatorSet = AnimatorSet().apply {
            playTogether(fadeIn, scaleX, scaleY, slideUp)
            duration = ANIM_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            start()
        }

        handler.postDelayed(navigateRunnable, TOTAL_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        animatorSet?.cancel()
        super.onDestroy()
    }
}
