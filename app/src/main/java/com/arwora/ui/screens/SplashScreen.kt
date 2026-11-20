package com.arwora.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.arwora.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SPLASH_LOGO_ANIM_DURATION_MS = 600
internal const val SPLASH_POST_ANIM_HOLD_MS = 700L
internal const val SPLASH_TOTAL_DURATION_MS = SPLASH_LOGO_ANIM_DURATION_MS.toLong() + SPLASH_POST_ANIM_HOLD_MS

internal data class SplashPalette(
    val backgroundColor: Color,
    val foregroundColor: Color
)

internal fun resolveSplashPalette(isSystemDark: Boolean): SplashPalette {
    return if (isSystemDark) {
        SplashPalette(
            backgroundColor = Color.Black,
            foregroundColor = Color.White
        )
    } else {
        SplashPalette(
            backgroundColor = Color.White,
            foregroundColor = Color.Black
        )
    }
}

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isSystemDark = remember(configuration.uiMode) {
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    val palette = remember(isSystemDark) { resolveSplashPalette(isSystemDark) }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.85f) }
    var hasFinished by remember { mutableStateOf(false) }

    val view = LocalView.current
    val context = LocalContext.current

    fun finishOnce() {
        if (!hasFinished) {
            hasFinished = true
            onSplashFinished()
        }
    }

    // Match status/navigation bar colors and icon contrast seamlessly with system theme splash.
    DisposableEffect(view, isSystemDark) {
        val window = (context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatusBarColor = window?.statusBarColor
        val prevNavBarColor = window?.navigationBarColor
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars
        val prevLightNav = insetsController?.isAppearanceLightNavigationBars

        val splashBgInt = palette.backgroundColor.toArgb()
        window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(splashBgInt))
            @Suppress("DEPRECATION")
            win.statusBarColor = splashBgInt
            @Suppress("DEPRECATION")
            win.navigationBarColor = splashBgInt
        }
        if (insetsController != null) {
            insetsController.isAppearanceLightStatusBars = !isSystemDark
            insetsController.isAppearanceLightNavigationBars = !isSystemDark
        }

        onDispose {
            if (prevStatusBarColor != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = prevStatusBarColor
            }
            if (prevNavBarColor != null) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = prevNavBarColor
            }
            if (insetsController != null) {
                if (prevLightStatus != null) {
                    insetsController.isAppearanceLightStatusBars = prevLightStatus
                }
                if (prevLightNav != null) {
                    insetsController.isAppearanceLightNavigationBars = prevLightNav
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val alphaJob = launch {
            logoAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = SPLASH_LOGO_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
        val scaleJob = launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = SPLASH_LOGO_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
        alphaJob.join()
        scaleJob.join()
        delay(SPLASH_POST_ANIM_HOLD_MS)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.backgroundColor)
            .systemBarsPadding()
            .testTag("splash_screen_root")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                finishOnce()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_arwora_wordmark),
            contentDescription = "ARWORA",
            colorFilter = ColorFilter.tint(palette.foregroundColor),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .width(248.dp)
                .height(46.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
                .testTag("splash_arwora_wordmark")
        )
    }
}
