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
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Dedicated splash activity displaying the centered ARWORA geometric vector wordmark
 * inside a responsive ConstraintLayout with a 500ms fade + scale (0.90f -> 1.0f) entrance
 * and 1000ms hold before cross-fading smoothly into MainActivity.
 */
class SplashActivity : ComponentActivity() {

    companion object {
        internal const val ANIM_DURATION_MS = 500L
        internal const val HOLD_DURATION_MS = 1000L
        internal const val TOTAL_DURATION_MS = ANIM_DURATION_MS + HOLD_DURATION_MS
        internal const val INITIAL_SCALE = 0.90f
        internal const val INITIAL_TRANSLATION_Y_DP = 0f
    }

    private val handler = Handler(Looper.getMainLooper())
    private var animatorSet: AnimatorSet? = null
    private var hasNavigated = false

    private val navigateRunnable = Runnable {
        navigateToMain()
    }

    private fun navigateToMain() {
        if (hasNavigated || isFinishing || isDestroyed) return
        hasNavigated = true
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra("skip_splash", true)
            }
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val bgColor = if (isSystemDark) Color.BLACK else Color.WHITE
        val fgColor = if (isSystemDark) Color.WHITE else Color.BLACK

        val root = findViewById<ConstraintLayout>(R.id.splash_root)
        val wordmark = findViewById<ImageView>(R.id.iv_arwora_wordmark)

        root.setBackgroundColor(bgColor)
        window.decorView.setBackgroundColor(bgColor)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        wordmark.setColorFilter(fgColor)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isSystemDark
        insetsController.isAppearanceLightNavigationBars = !isSystemDark

        wordmark.alpha = 0f
        wordmark.scaleX = INITIAL_SCALE
        wordmark.scaleY = INITIAL_SCALE
        wordmark.translationY = 0f

        val fadeIn = ObjectAnimator.ofFloat(wordmark, View.ALPHA, 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(wordmark, View.SCALE_X, INITIAL_SCALE, 1f)
        val scaleY = ObjectAnimator.ofFloat(wordmark, View.SCALE_Y, INITIAL_SCALE, 1f)

        animatorSet = AnimatorSet().apply {
            playTogether(fadeIn, scaleX, scaleY)
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
