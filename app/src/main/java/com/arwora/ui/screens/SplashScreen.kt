package com.arwora.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.arwora.ui.components.AppLogo
import com.arwora.ui.theme.BackgroundDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SPLASH_LOGO_ANIM_DURATION_MS = 550
internal const val SPLASH_TEXT_ANIM_DURATION_MS = 700
internal const val SPLASH_POST_ANIM_HOLD_MS = 220L

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit = {}
) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.86f) }
    val textAlpha = remember { Animatable(0f) }
    val textScale = remember { Animatable(0.86f) }
    var hasFinished by remember { mutableStateOf(false) }

    val view = LocalView.current
    val context = LocalContext.current

    fun finishOnce() {
        if (!hasFinished) {
            hasFinished = true
            onSplashFinished()
        }
    }

    // Keep system bar icons light against the pure black #000000 splash surface.
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars
        val prevLightNav = insetsController?.isAppearanceLightNavigationBars
        if (insetsController != null) {
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        onDispose {
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
        val logoAlphaJob = launch {
            logoAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = SPLASH_LOGO_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
        val logoScaleJob = launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = SPLASH_LOGO_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
        val alphaJob = launch {
            textAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = SPLASH_TEXT_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
        val scaleJob = launch {
            textScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = SPLASH_TEXT_ANIM_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
        logoAlphaJob.join()
        logoScaleJob.join()
        alphaJob.join()
        scaleJob.join()
        delay(SPLASH_POST_ANIM_HOLD_MS)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
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
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo(
                size = 180.dp,
                contentDescription = "Arwora Hyper Toolkit Logo",
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
                    .testTag("splash_app_logo")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .scale(textScale.value)
                    .alpha(textAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Arwora",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("splash_brand_title")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6),
                                    Color(0xFF38BDF8)
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "HYPER TOOLKIT",
                    color = Color(0xFFA78BFA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.2.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
