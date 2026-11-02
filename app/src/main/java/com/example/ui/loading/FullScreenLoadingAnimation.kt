package com.example.ui.loading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.config.KormoKariConfig
import com.example.ui.theme.KormoKariNavy
import com.example.ui.theme.KormoKariPrimary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen loading animation component for KormoKari.
 *
 * Features:
 * - Full-screen soft off-white/pink gradient background.
 * - Breathing radial glow aura in KormoKari brand pink.
 * - Dual orbital rings with 3 clockwise animated orbiting particle dots.
 * - Centered glowing KormoKari logo with subtle breathing pulse animation.
 * - Animated rotating arc ring with sweep gradient.
 * - "Kormo" (Pink) and "Kari" (Navy) brand wordmark with tagline.
 * - Smooth indeterminate or determinate linear progress indicator with rounded pill container.
 * - Dynamically cycling status text messages ("Connecting to KormoKari…", "Loading opportunities…").
 */
@Composable
fun FullScreenLoadingAnimation(
    modifier: Modifier = Modifier,
    statusText: String? = null,
    progress: Float? = null,
    showTagline: Boolean = true
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "fullScreenLoadingAnimation")

    // Breathing scale for center aura and logo
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Breathing alpha for soft glow
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Clockwise angle for inner orbit (5.5s per revolution)
    val innerAngleDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerOrbitLoading"
    )

    // Clockwise angle for outer orbit (7.8s per revolution)
    val outerAngleDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerOrbitLoading"
    )

    // Fast spinner rotation for active loading ring
    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerRingRotation"
    )

    // Dynamic cycling status text
    var currentMessageIndex by remember { mutableIntStateOf(0) }
    val defaultStatusMessages = remember {
        listOf(
            "Connecting to KormoKari…",
            "Loading verified work opportunities…",
            "Preparing your session…",
            "Fetching latest job updates…"
        )
    }

    LaunchedEffect(statusText) {
        if (statusText == null) {
            while (true) {
                delay(2200)
                currentMessageIndex = (currentMessageIndex + 1) % defaultStatusMessages.size
            }
        }
    }

    val displayStatus = statusText ?: defaultStatusMessages[currentMessageIndex]

    val innerRingRadiusDp = 85.dp
    val outerRingRadiusDp = 125.dp
    val glowRadiusDp = 175.dp

    val innerRingRadiusPx = with(density) { innerRingRadiusDp.toPx() }
    val outerRingRadiusPx = with(density) { outerRingRadiusDp.toPx() }
    val glowRadiusPx = with(density) { glowRadiusDp.toPx() }

    val dotPinkRadiusPx = with(density) { 5.dp.toPx() }
    val dotGreyRadiusPx = with(density) { 4.dp.toPx() }
    val dotBlueRadiusPx = with(density) { 4.5.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFBF9FB),
                        Color(0xFFFFF5FA)
                    )
                )
            )
            .testTag("full_screen_loading_animation"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Orbital rings, glowing aura and animated logo
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .testTag("loading_orbital_container"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasCenter = center

                    // 1. Soft pink radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE91069).copy(alpha = glowAlpha),
                                Color(0xFFE91069).copy(alpha = glowAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = canvasCenter,
                            radius = glowRadiusPx * pulseScale
                        ),
                        center = canvasCenter,
                        radius = glowRadiusPx * pulseScale
                    )

                    // 2. Inner subtle orbital ring
                    drawCircle(
                        color = Color(0xFFE91069).copy(alpha = 0.16f),
                        radius = innerRingRadiusPx,
                        center = canvasCenter,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // 3. Outer subtle orbital ring
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.14f),
                        radius = outerRingRadiusPx,
                        center = canvasCenter,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // 4. Rotating sweep arc indicator around the outer ring
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFE91069).copy(alpha = 0.15f),
                                Color(0xFFE91069).copy(alpha = 0.85f)
                            )
                        ),
                        startAngle = spinnerRotation,
                        sweepAngle = 100f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(canvasCenter.x - outerRingRadiusPx, canvasCenter.y - outerRingRadiusPx),
                        size = androidx.compose.ui.geometry.Size(outerRingRadiusPx * 2, outerRingRadiusPx * 2)
                    )

                    // 5. Orbiting Particle 1: Brand Pink on Inner Ring
                    val rad1 = Math.toRadians(innerAngleDegrees.toDouble() + 45.0)
                    val dot1Offset = Offset(
                        x = (canvasCenter.x + innerRingRadiusPx * cos(rad1)).toFloat(),
                        y = (canvasCenter.y + innerRingRadiusPx * sin(rad1)).toFloat()
                    )
                    drawCircle(
                        color = Color(0xFFE91069).copy(alpha = 0.25f),
                        radius = dotPinkRadiusPx * 1.8f,
                        center = dot1Offset
                    )
                    drawCircle(
                        color = Color(0xFFE91069),
                        radius = dotPinkRadiusPx,
                        center = dot1Offset
                    )

                    // 6. Orbiting Particle 2: Muted Grey-Lilac on Outer Ring
                    val rad2 = Math.toRadians(outerAngleDegrees.toDouble() + 130.0)
                    val dot2Offset = Offset(
                        x = (canvasCenter.x + outerRingRadiusPx * cos(rad2)).toFloat(),
                        y = (canvasCenter.y + outerRingRadiusPx * sin(rad2)).toFloat()
                    )
                    drawCircle(
                        color = Color(0xFF94A3B8).copy(alpha = 0.22f),
                        radius = dotGreyRadiusPx * 1.7f,
                        center = dot2Offset
                    )
                    drawCircle(
                        color = Color(0xFF94A3B8),
                        radius = dotGreyRadiusPx,
                        center = dot2Offset
                    )

                    // 7. Orbiting Particle 3: Slate Blue on Outer Ring
                    val rad3 = Math.toRadians(outerAngleDegrees.toDouble() + 295.0)
                    val dot3Offset = Offset(
                        x = (canvasCenter.x + outerRingRadiusPx * cos(rad3)).toFloat(),
                        y = (canvasCenter.y + outerRingRadiusPx * sin(rad3)).toFloat()
                    )
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.24f),
                        radius = dotBlueRadiusPx * 1.7f,
                        center = dot3Offset
                    )
                    drawCircle(
                        color = Color(0xFF3B82F6),
                        radius = dotBlueRadiusPx,
                        center = dot3Offset
                    )
                }

                // Centered Logo with gentle breathing pulse
                Image(
                    painter = painterResource(id = R.drawable.kormokari_logo),
                    contentDescription = stringResource(R.string.logo_content_description),
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .testTag("loading_logo"),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Brand Title: "Kormo" (Pink) + "Kari" (Navy)
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFFE91069),
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) {
                        append("Kormo")
                    }
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) {
                        append("Kari")
                    }
                },
                fontSize = 32.sp,
                letterSpacing = (-1.0).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("loading_brand_title")
            )

            if (showTagline) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = KormoKariConfig.TAGLINE,
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("loading_brand_tagline")
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Rounded Progress Bar Container
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3E8EE)),
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .testTag("loading_progress_bar"),
                            color = KormoKariPrimary,
                            trackColor = Color(0xFFFCE7F3)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .testTag("loading_progress_bar"),
                            color = KormoKariPrimary,
                            trackColor = Color(0xFFFCE7F3)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(14.dp)
                                .testTag("loading_mini_spinner"),
                            color = KormoKariPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayStatus,
                            color = Color(0xFF475569),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("loading_status_text")
                        )
                    }
                }
            }
        }
    }
}
