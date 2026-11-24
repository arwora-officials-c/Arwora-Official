package com.arwora

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
 * inside a responsive ConstraintLayout (240dp x 60dp) rendered instantaneously on cold start,
 * holding briefly (400ms) followed by a clean 300ms alpha fade-out (700ms total) and
 * immediate fade transition into MainActivity.
 */
class SplashActivity : ComponentActivity() {

    companion object {
        internal const val ANIM_DURATION_MS = 0L
        internal const val HOLD_DURATION_MS = 400L
        internal const val FADE_OUT_DURATION_MS = 300L
        internal const val TOTAL_DURATION_MS = HOLD_DURATION_MS + FADE_OUT_DURATION_MS
        internal const val INITIAL_SCALE = 1.0f
        internal const val INITIAL_TRANSLATION_Y_DP = 0f
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fadeOutAnimator: ObjectAnimator? = null
    private var wordmarkView: ImageView? = null
    private var hasNavigated = false

    private val startExitTransitionRunnable = Runnable {
        startFadeOutAndNavigate()
    }

    private val safetyNavigateRunnable = Runnable {
        navigateToMain()
    }

    private fun startFadeOutAndNavigate() {
        if (hasNavigated || isFinishing || isDestroyed) return
        val target = wordmarkView
        if (target == null) {
            navigateToMain()
            return
        }

        fadeOutAnimator?.cancel()
        fadeOutAnimator = ObjectAnimator.ofFloat(target, View.ALPHA, target.alpha, 0f).apply {
            duration = FADE_OUT_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    navigateToMain()
                }

                override fun onAnimationCancel(animation: Animator) {
                    navigateToMain()
                }
            })
            start()
        }
        // Guarantee forward navigation even if system animator scale is disabled or interrupted
        handler.postDelayed(safetyNavigateRunnable, FADE_OUT_DURATION_MS + 50L)
    }

    private fun navigateToMain() {
        if (hasNavigated || isFinishing || isDestroyed) return
        hasNavigated = true
        handler.removeCallbacks(startExitTransitionRunnable)
        handler.removeCallbacks(safetyNavigateRunnable)
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
        (application as? NameGenApplication)?.warmupAsync()

        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val bgColor = if (isSystemDark) Color.BLACK else androidx.core.content.ContextCompat.getColor(this, R.color.colorSurface)
        val fgColor = Color.WHITE

        val root = findViewById<ConstraintLayout>(R.id.splash_root)
        val wordmark = findViewById<ImageView>(R.id.iv_arwora_wordmark)
        wordmarkView = wordmark

        root.setBackgroundColor(bgColor)
        window.decorView.setBackgroundColor(bgColor)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        wordmark.setColorFilter(fgColor)
        wordmark.alpha = 1f
        wordmark.scaleX = INITIAL_SCALE
        wordmark.scaleY = INITIAL_SCALE
        wordmark.translationY = 0f

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        handler.postDelayed(startExitTransitionRunnable, HOLD_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(startExitTransitionRunnable)
        handler.removeCallbacks(safetyNavigateRunnable)
        fadeOutAnimator?.removeAllListeners()
        fadeOutAnimator?.cancel()
        fadeOutAnimator = null
        wordmarkView = null
        super.onDestroy()
    }
}
