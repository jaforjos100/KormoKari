package com.example.ui.startup

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.network.NetworkState
import com.example.ui.theme.KormoKariNavy
import com.example.ui.theme.KormoKariPalePink
import com.example.ui.theme.KormoKariPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phase 3 — KormoKari Native Animated Splash Screen with Pull-to-Refresh Support.
 *
 * Recreates the authentic KormoKari splash design:
 * - Full-screen soft off-white/pink background
 * - Soft pink radial glow in the center
 * - Two subtle circular orbital rings
 * - Centered KormoKari logo icon
 * - Three small orbital dots (pink, muted grey-lilac, slate blue) animated clockwise around the rings
 * - "Kormo" in brand pink and "Kari" in dark navy
 * - Tagline: "Find work. Create opportunity."
 * - Integrated Material 3 Pull-to-Refresh mechanism for reloading TWA content and re-checking connectivity
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KormoKariStartupScreen(
    state: StartupScreenState,
    onLaunchRequested: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onBrowseOffline: () -> Unit = {},
    onBackFromOffline: () -> Unit = {},
    onExitRequested: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onCategorySelected: (String?) -> Unit = {},
    onSelectJob: (com.example.data.local.CachedJob?) -> Unit = {},
    onRequestBatteryExemption: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onServerNotReady: (com.example.network.ServerHealthState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Trigger TWA launch flow immediately when splash opens without artificial sleep
    LaunchedEffect(Unit) {
        onLaunchRequested()
    }

    if (state.uiState is StartupUiState.HealthStatus) {
        com.example.ui.health.KormoKariHealthStatusScreen(
            healthState = state.uiState.healthState,
            isRetrying = state.isRefreshing || state.serverHealthState is com.example.network.ServerHealthState.Checking,
            onRetry = onRetry,
            onOpenSettings = onOpenSettings,
            onBrowseOffline = onBrowseOffline,
            modifier = modifier
        )
        return
    }

    if (state.uiState is StartupUiState.OfflineMode) {
        com.example.ui.offline.KormoKariOfflineScreen(
            state = state,
            onReconnect = onRetry,
            onBackToStartup = onBackFromOffline,
            onSearchQueryChanged = onSearchQueryChanged,
            onCategorySelected = onCategorySelected,
            onSelectJob = onSelectJob,
            onRequestBatteryExemption = onRequestBatteryExemption,
            onOpenBatterySettings = onOpenBatterySettings,
            modifier = modifier
        )
        return
    }

    if (state.uiState is StartupUiState.PwaWebView) {
        com.example.ui.pwa.KormoKariPwaWebView(
            url = state.uiState.url,
            networkState = state.networkState,
            onNavigateOffline = onBrowseOffline,
            onBack = onExitRequested,
            onServerNotReady = onServerNotReady,
            modifier = modifier
        )
        return
    }

    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier
            .fillMaxSize()
            .testTag("pull_to_refresh_box"),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("pull_to_refresh_indicator"),
                color = KormoKariPrimary,
                containerColor = Color.White
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFFBF9FB),
                            Color(0xFFFFF6FA)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            when (val ui = state.uiState) {
                is StartupUiState.AnimatedSplash,
                is StartupUiState.TwaLaunched -> {
                    KormoKariSplashDesign(
                        isReducedMotion = isReducedMotion
                    )
                }

                is StartupUiState.OfflineMode -> {
                    // Handled above
                }

                is StartupUiState.PwaWebView -> {
                    // Handled above
                }

                is StartupUiState.HealthStatus -> {
                    // Handled above
                }

                is StartupUiState.Error -> {
                    if (ui.isNetworkError) {
                        KormoKariNoConnectionView(
                            networkState = state.networkState,
                            onRetry = onRetry,
                            onOpenSettings = onOpenSettings,
                            onOpenExternal = onOpenExternal,
                            onBrowseOffline = onBrowseOffline
                        )
                    } else {
                        ErrorRecoverySurface(
                            error = ui,
                            onRetry = onRetry,
                            onOpenExternal = onOpenExternal
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recreates the exact KormoKari splash screen visual design matching 480 × 854 reference proportions.
 */
@Composable
fun KormoKariSplashDesign(
    isReducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Infinite transition for clockwise orbital dot rotation
    val infiniteTransition = rememberInfiniteTransition(label = "orbitalDots")

    // Clockwise angle for inner orbit (e.g. 6.5s per revolution)
    val innerAngleDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerOrbitClockwise"
    )

    // Clockwise angle for outer orbit (e.g. 8.8s per revolution)
    val outerAngleDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerOrbitClockwise"
    )

    // Fast spinner rotation for active loading ring indicator
    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splashSpinnerRing"
    )

    // Ring dimensions scaled for 480 × 854 reference proportions
    val innerRingRadiusDp = 92.dp
    val outerRingRadiusDp = 136.dp
    val glowRadiusDp = 185.dp
    val logoSizeDp = 122.dp

    val innerRingRadiusPx = with(density) { innerRingRadiusDp.toPx() }
    val outerRingRadiusPx = with(density) { outerRingRadiusDp.toPx() }
    val glowRadiusPx = with(density) { glowRadiusDp.toPx() }

    val dotPinkRadiusPx = with(density) { 5.5.dp.toPx() }
    val dotGreyRadiusPx = with(density) { 4.2.dp.toPx() }
    val dotBlueRadiusPx = with(density) { 4.8.dp.toPx() }

    val haloPinkRadiusPx = with(density) { 9.dp.toPx() }
    val haloGreyRadiusPx = with(density) { 7.5.dp.toPx() }
    val haloBlueRadiusPx = with(density) { 8.5.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Orbit and Center Logo container
        Box(
            modifier = Modifier
                .size(300.dp)
                .testTag("kormokari_orbital_container"),
            contentAlignment = Alignment.Center
        ) {
            // Canvas drawing: Soft pink radial glow, two subtle orbital rings, rotating loading sweep arc, and 3 clockwise rotating dots
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasCenter = center

                // 1. Soft pink radial glow in the center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE91069).copy(alpha = 0.12f),
                            Color(0xFFE91069).copy(alpha = 0.035f),
                            Color.Transparent
                        ),
                        center = canvasCenter,
                        radius = glowRadiusPx
                    ),
                    center = canvasCenter,
                    radius = glowRadiusPx
                )

                // 2. Inner subtle circular orbital ring
                drawCircle(
                    color = Color(0xFFE91069).copy(alpha = 0.17f),
                    radius = innerRingRadiusPx,
                    center = canvasCenter,
                    style = Stroke(width = 1.4.dp.toPx())
                )

                // 3. Outer subtle circular orbital ring
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.14f),
                    radius = outerRingRadiusPx,
                    center = canvasCenter,
                    style = Stroke(width = 1.4.dp.toPx())
                )

                // 4. Rotating sweep arc loading indicator around the outer ring
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
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(canvasCenter.x - outerRingRadiusPx, canvasCenter.y - outerRingRadiusPx),
                    size = androidx.compose.ui.geometry.Size(outerRingRadiusPx * 2, outerRingRadiusPx * 2)
                )

                // Dot positions: Clockwise trigonometric positioning
                // Dot 1: Brand Pink on Inner Ring
                val angle1Deg = if (isReducedMotion) 45.0 else (innerAngleDegrees.toDouble() + 45.0)
                val rad1 = Math.toRadians(angle1Deg)
                val dot1Offset = Offset(
                    x = (canvasCenter.x + innerRingRadiusPx * cos(rad1)).toFloat(),
                    y = (canvasCenter.y + innerRingRadiusPx * sin(rad1)).toFloat()
                )

                // Dot 2: Muted Grey-Lilac on Outer Ring
                val angle2Deg = if (isReducedMotion) 130.0 else (outerAngleDegrees.toDouble() + 130.0)
                val rad2 = Math.toRadians(angle2Deg)
                val dot2Offset = Offset(
                    x = (canvasCenter.x + outerRingRadiusPx * cos(rad2)).toFloat(),
                    y = (canvasCenter.y + outerRingRadiusPx * sin(rad2)).toFloat()
                )

                // Dot 3: Slate Blue on Outer Ring
                val angle3Deg = if (isReducedMotion) 295.0 else (outerAngleDegrees.toDouble() + 295.0)
                val rad3 = Math.toRadians(angle3Deg)
                val dot3Offset = Offset(
                    x = (canvasCenter.x + outerRingRadiusPx * cos(rad3)).toFloat(),
                    y = (canvasCenter.y + outerRingRadiusPx * sin(rad3)).toFloat()
                )

                // Draw Dot 1 (Pink with soft halo)
                drawCircle(
                    color = Color(0xFFE91069).copy(alpha = 0.22f),
                    radius = haloPinkRadiusPx,
                    center = dot1Offset
                )
                drawCircle(
                    color = Color(0xFFE91069),
                    radius = dotPinkRadiusPx,
                    center = dot1Offset
                )

                // Draw Dot 2 (Muted Grey-Lilac with soft halo)
                drawCircle(
                    color = Color(0xFFB4A9C4).copy(alpha = 0.18f),
                    radius = haloGreyRadiusPx,
                    center = dot2Offset
                )
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = dotGreyRadiusPx,
                    center = dot2Offset
                )

                // Draw Dot 3 (Slate Blue with soft halo)
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.20f),
                    radius = haloBlueBlueRadiusPx(haloBlueRadiusPx),
                    center = dot3Offset
                )
                drawCircle(
                    color = Color(0xFF3B82F6),
                    radius = dotBlueRadiusPx,
                    center = dot3Offset
                )
            }

            // Centered KormoKari logo icon inside orbital rings
            Image(
                painter = painterResource(id = R.drawable.kormokari_logo),
                contentDescription = stringResource(R.string.logo_content_description),
                modifier = Modifier
                    .size(logoSizeDp)
                    .testTag("kormokari_splash_logo"),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // "Kormo" in brand pink, "Kari" in dark navy
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
            fontSize = 35.sp,
            letterSpacing = (-1.1).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("brand_title")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tagline: "Find work. Create opportunity."
        Text(
            text = KormoKariConfig.TAGLINE,
            color = Color(0xFF64748B),
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("brand_tagline")
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Full Screen Loading Animation indicator card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3E8EE)),
            modifier = Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .testTag("full_screen_loading_indicator")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .testTag("startup_loading_progress_bar"),
                    color = KormoKariPrimary,
                    trackColor = Color(0xFFFCE7F3)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(14.dp)
                            .testTag("startup_mini_spinner"),
                        color = KormoKariPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.startup_loading),
                        color = Color(0xFF475569),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("startup_loading_status_text")
                    )
                }
            }
        }
    }
}

private fun haloBlueBlueRadiusPx(value: Float): Float = value

/**
 * Non-intrusive error/recovery surface only displayed if connectivity fails,
 * preventing an unrecoverable blank screen.
 */
@Composable
private fun ErrorRecoverySurface(
    error: StartupUiState.Error,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(24.dp)
            .testTag("error_recovery_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(30.dp),
                color = KormoKariPalePink
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (error.isNetworkError) Icons.Default.WifiOff else Icons.Default.Info,
                        contentDescription = null,
                        tint = KormoKariPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = error.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = KormoKariNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("error_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("error_message")
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (error.canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("retry_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KormoKariPrimary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 3.dp,
                        pressedElevation = 6.dp,
                        hoveredElevation = 4.dp
                    ),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_retry),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.action_retry),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenExternal,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_external_browser_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_open_browser))
            }
        }
    }
}

/**
 * Dedicated, custom 'No Connection' view displayed when initial network connectivity checks fail.
 * Recreates KormoKari's brand aesthetic with network diagnostic badge, retry, settings, and browser fallback.
 */
@Composable
fun KormoKariNoConnectionView(
    networkState: NetworkState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExternal: () -> Unit,
    onBrowseOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp)
            .testTag("no_connection_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Elevated Circular Illustration with brand soft pink halo
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(118.dp),
                shape = RoundedCornerShape(59.dp),
                color = Color(0xFFE91069).copy(alpha = 0.08f)
            ) {}

            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(44.dp),
                color = KormoKariPalePink,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = stringResource(R.string.no_connection_title),
                        tint = KormoKariPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. KormoKari Brand Identity
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
            fontSize = 26.sp,
            letterSpacing = (-0.8).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Clear Headline
        Text(
            text = stringResource(R.string.no_connection_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = KormoKariNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("no_connection_title")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Descriptive Subtitle & Instructions
        Text(
            text = stringResource(R.string.no_connection_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier
                .widthIn(max = 380.dp)
                .testTag("no_connection_message")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Connectivity Diagnostic Pill + Offline Mode Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when (networkState) {
                    is NetworkState.Connected -> Color(0xFF10B981).copy(alpha = 0.12f)
                    is NetworkState.Checking -> Color(0xFF3B82F6).copy(alpha = 0.12f)
                    is NetworkState.Disconnected -> Color(0xFFEF4444).copy(alpha = 0.10f)
                },
                modifier = Modifier.testTag("network_status_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (networkState) {
                            is NetworkState.Connected -> Color(0xFF10B981)
                            is NetworkState.Checking -> Color(0xFF3B82F6)
                            is NetworkState.Disconnected -> Color(0xFFEF4444)
                        },
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (networkState) {
                            is NetworkState.Connected -> stringResource(R.string.status_connected)
                            is NetworkState.Checking -> stringResource(R.string.status_checking)
                            is NetworkState.Disconnected -> stringResource(R.string.status_offline)
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (networkState) {
                            is NetworkState.Connected -> Color(0xFF047857)
                            is NetworkState.Checking -> Color(0xFF1D4ED8)
                            is NetworkState.Disconnected -> Color(0xFFB91C1C)
                        }
                    )
                }
            }

            com.example.ui.offline.OfflineModeBadge()
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 6. Background Auto-Check Online Status Indicator
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF1F5F9),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .height(48.dp)
                .testTag("offline_auto_check_indicator")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFF64748B),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ইন্টারনেট সংযোগ স্বয়ংক্রিয়ভাবে যাচাই করা হচ্ছে…",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 7. Action 2: Open Network Settings
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .height(50.dp)
                .testTag("open_network_settings_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = KormoKariNavy
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_open_network_settings),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = KormoKariNavy
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 8. Action 3: Open External Browser Fallback
        OutlinedButton(
            onClick = onOpenExternal,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .height(48.dp)
                .testTag("open_external_browser_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_open_browser),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 9. Pull-to-refresh swipe down affordance hint
        Row(
            modifier = Modifier.testTag("pull_to_refresh_hint"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.pull_to_refresh_hint),
                fontSize = 12.5.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}
