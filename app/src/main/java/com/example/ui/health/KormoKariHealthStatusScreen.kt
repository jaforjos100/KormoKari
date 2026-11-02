package com.example.ui.health

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.network.ServerHealthState
import com.example.ui.theme.KormoKariNavy
import com.example.ui.theme.KormoKariPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium branded Android screen for KormoKari server readiness and health status.
 *
 * Implements authoritative visual displays for:
 * - CHECKING: Checking website availability
 * - READY: Website is ready
 * - SERVER_NOT_READY: Backend reports maintenance / not ready (with bilingual Bangla & English messages)
 * - NO_INTERNET: Device has no internet connection
 * - CONNECTION_ERROR: Server connection failed
 * - TIMEOUT: Health check timed out
 * - TLS_ERROR: HTTPS certificate verification failed
 */
@Composable
fun KormoKariHealthStatusScreen(
    healthState: ServerHealthState,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onBrowseOffline: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "healthScreenOrbit")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "healthPulse"
    )

    val innerAngleDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerOrbit"
    )

    val outerAngleDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerOrbit"
    )

    val lastCheckedFormatted = remember(healthState.timestamp) {
        val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        sdf.format(Date(healthState.timestamp))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFBF9FB),
                        Color(0xFFFFF6FA)
                    )
                )
            )
            .testTag("health_status_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Orbital rings and KormoKari Brand Logo
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .testTag("health_orbital_container"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasCenter = center
                    val innerRadius = with(density) { 62.dp.toPx() }
                    val outerRadius = with(density) { 92.dp.toPx() }
                    val glowRadius = with(density) { 110.dp.toPx() }

                    // Soft pink radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE91069).copy(alpha = 0.12f),
                                Color(0xFFE91069).copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = canvasCenter,
                            radius = glowRadius
                        ),
                        center = canvasCenter,
                        radius = glowRadius
                    )

                    // Dual orbital rings
                    drawCircle(
                        color = Color(0xFFE91069).copy(alpha = 0.16f),
                        radius = innerRadius,
                        center = canvasCenter,
                        style = Stroke(width = 1.3.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.14f),
                        radius = outerRadius,
                        center = canvasCenter,
                        style = Stroke(width = 1.3.dp.toPx())
                    )

                    // Orbiting Particle on inner ring
                    val rad1 = Math.toRadians(innerAngleDegrees.toDouble() + 45.0)
                    val dot1 = Offset(
                        x = (canvasCenter.x + innerRadius * cos(rad1)).toFloat(),
                        y = (canvasCenter.y + innerRadius * sin(rad1)).toFloat()
                    )
                    drawCircle(color = Color(0xFFE91069), radius = with(density) { 4.dp.toPx() }, center = dot1)

                    // Orbiting Particle on outer ring
                    val rad2 = Math.toRadians(outerAngleDegrees.toDouble() + 130.0)
                    val dot2 = Offset(
                        x = (canvasCenter.x + outerRadius * cos(rad2)).toFloat(),
                        y = (canvasCenter.y + outerRadius * sin(rad2)).toFloat()
                    )
                    drawCircle(color = Color(0xFF3B82F6), radius = with(density) { 3.8.dp.toPx() }, center = dot2)
                }

                Image(
                    painter = painterResource(id = R.drawable.kormokari_logo),
                    contentDescription = stringResource(R.string.logo_content_description),
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .testTag("health_brand_logo"),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Brand Title: "Kormo" (Pink) + "Kari" (Navy)
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color(0xFFE91069), fontWeight = FontWeight.ExtraBold)) {
                        append("Kormo")
                    }
                    withStyle(style = SpanStyle(color = Color(0xFF111827), fontWeight = FontWeight.ExtraBold)) {
                        append("Kari")
                    }
                },
                fontSize = 28.sp,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("health_brand_title")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Main Health Card based on state
            when (healthState) {
                is ServerHealthState.Checking -> {
                    HealthCheckingCard(isRetrying = isRetrying)
                }

                is ServerHealthState.ServerNotReady -> {
                    ServerNotReadyCard(
                        healthState = healthState,
                        lastChecked = lastCheckedFormatted,
                        isRetrying = isRetrying,
                        onRetry = onRetry,
                        onBrowseOffline = onBrowseOffline
                    )
                }

                is ServerHealthState.NoInternet -> {
                    NoInternetCard(
                        healthState = healthState,
                        lastChecked = lastCheckedFormatted,
                        isRetrying = isRetrying,
                        onRetry = onRetry,
                        onOpenSettings = onOpenSettings,
                        onBrowseOffline = onBrowseOffline
                    )
                }

                is ServerHealthState.ConnectionError -> {
                    GenericErrorCard(
                        badgeText = "CONNECTION_ERROR",
                        badgeContainer = Color(0xFFFEF2F2),
                        badgeTextContent = Color(0xFFDC2626),
                        icon = Icons.Default.CloudOff,
                        headlineBn = stringResource(R.string.health_state_connection_error_bn),
                        headlineEn = stringResource(R.string.health_state_connection_error),
                        msgBn = stringResource(R.string.health_state_connection_error_desc_bn),
                        msgEn = stringResource(R.string.health_state_connection_error_desc_en),
                        lastChecked = lastCheckedFormatted,
                        isRetrying = isRetrying,
                        onRetry = onRetry,
                        onBrowseOffline = onBrowseOffline
                    )
                }

                is ServerHealthState.Timeout -> {
                    GenericErrorCard(
                        badgeText = "TIMEOUT",
                        badgeContainer = Color(0xFFFFFBEB),
                        badgeTextContent = Color(0xFFD97706),
                        icon = Icons.Default.TimerOff,
                        headlineBn = stringResource(R.string.health_state_timeout_bn),
                        headlineEn = stringResource(R.string.health_state_timeout),
                        msgBn = stringResource(R.string.health_state_timeout_desc_bn),
                        msgEn = stringResource(R.string.health_state_timeout_desc_en),
                        lastChecked = lastCheckedFormatted,
                        isRetrying = isRetrying,
                        onRetry = onRetry,
                        onBrowseOffline = onBrowseOffline
                    )
                }

                is ServerHealthState.TlsError -> {
                    GenericErrorCard(
                        badgeText = "TLS_ERROR",
                        badgeContainer = Color(0xFFFEF2F2),
                        badgeTextContent = Color(0xFFDC2626),
                        icon = Icons.Default.Lock,
                        headlineBn = stringResource(R.string.health_state_tls_error_bn),
                        headlineEn = stringResource(R.string.health_state_tls_error),
                        msgBn = stringResource(R.string.health_state_tls_error_desc_bn),
                        msgEn = stringResource(R.string.health_state_tls_error_desc_en),
                        lastChecked = lastCheckedFormatted,
                        isRetrying = isRetrying,
                        onRetry = onRetry,
                        onBrowseOffline = onBrowseOffline
                    )
                }

                is ServerHealthState.Ready -> {
                    HealthReadyCard(onContinue = onRetry)
                }
            }
        }
    }
}

/**
 * Authoritative SERVER_NOT_READY Card showing Bangla & English maintenance notices,
 * last check timestamp, and Retry action.
 */
@Composable
private fun ServerNotReadyCard(
    healthState: ServerHealthState.ServerNotReady,
    lastChecked: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onBrowseOffline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server_not_ready_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFFECDD3))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Prominent SERVER_NOT_READY badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier.testTag("server_not_ready_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SERVER_NOT_READY",
                        color = Color(0xFFE11D48),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bangla Headline & Notice
            Text(
                text = stringResource(R.string.trouble_connecting_heading_bn),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.trouble_connecting_desc_bn),
                fontSize = 13.5.sp,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.testTag("server_not_ready_msg_bn")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // English Notice Divider and text
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.trouble_connecting_heading),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.trouble_connecting_desc),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.testTag("server_not_ready_msg_en")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Last checked time
            Text(
                text = stringResource(R.string.health_last_checked, lastChecked),
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("health_last_checked_text")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Retry Button
            Button(
                onClick = onRetry,
                enabled = !isRetrying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KormoKariPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = KormoKariPrimary.copy(alpha = 0.65f),
                    disabledContentColor = Color.White.copy(alpha = 0.85f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp,
                    hoveredElevation = 6.dp
                ),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("health_retry_button")
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.status_checking),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                } else {
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


        }
    }
}

/**
 * Checking card showing loading state.
 */
@Composable
private fun HealthCheckingCard(isRetrying: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("health_checking_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFF3E8EE))
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = KormoKariPrimary,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.health_state_checking_bn),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.health_state_checking),
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * No Internet Card explicitly differentiating device network issues from server errors.
 */
@Composable
private fun NoInternetCard(
    healthState: ServerHealthState.NoInternet,
    lastChecked: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseOffline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("no_internet_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF1F5F9),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.health_state_no_internet_bn),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.health_state_no_internet_desc_bn),
                fontSize = 13.5.sp,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.health_state_no_internet_desc_en),
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.health_last_checked, lastChecked),
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Background Auto-Checking Indicator
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("health_auto_check_indicator")
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

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("health_settings_button")
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_open_network_settings), color = Color(0xFF334155))
            }


        }
    }
}

/**
 * Generic Error Card for ConnectionError, Timeout, and TlsError.
 */
@Composable
private fun GenericErrorCard(
    badgeText: String,
    badgeContainer: Color,
    badgeTextContent: Color,
    icon: ImageVector,
    headlineBn: String,
    headlineEn: String,
    msgBn: String,
    msgEn: String,
    lastChecked: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onBrowseOffline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("health_error_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeContainer,
                modifier = Modifier.testTag("health_error_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = badgeTextContent, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = badgeText, color = badgeTextContent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(text = headlineBn, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = msgBn, fontSize = 13.5.sp, color = Color(0xFF475569), textAlign = TextAlign.Center, lineHeight = 19.sp)

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = msgEn, fontSize = 12.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = stringResource(R.string.health_last_checked, lastChecked), fontSize = 12.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRetry,
                enabled = !isRetrying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KormoKariPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = KormoKariPrimary.copy(alpha = 0.65f),
                    disabledContentColor = Color.White.copy(alpha = 0.85f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp,
                    hoveredElevation = 6.dp
                ),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("health_retry_button")
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.status_checking),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                } else {
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


        }
    }
}

/**
 * Temporary Ready Card indicating successful verification before launching.
 */
@Composable
private fun HealthReadyCard(onContinue: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("health_ready_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFDCFCE7),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.health_state_ready_bn),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.health_state_ready),
                fontSize = 13.sp,
                color = Color(0xFF16A34A),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
