package com.alan.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alan.app.data.SiteStatus
import com.alan.app.net.RelayState
import com.alan.app.tunnel.TunnelState

/**
 * Shared hosting-status language: LIVE green (#16A34A), STARTING amber (#D97706),
 * ERROR red (#DC2626), STOPPED neutral. Light/dark variants keep contrast in both modes.
 * Used identically on Home + Detail (+ Relay / PublicAccess for tunnel & relay states).
 */
private object StatusPalette {
    val Live = Color(0xFF16A34A)
    val LiveDark = Color(0xFF4ADE80)
    val LiveContainer = Color(0xFFDCFCE7)
    val OnLiveContainer = Color(0xFF052E16)
    val LiveContainerDark = Color(0xFF14532D)
    val OnLiveContainerDark = Color(0xFFDCFCE7)

    val Starting = Color(0xFFD97706)
    val StartingDark = Color(0xFFFBBF24)
    val StartingContainer = Color(0xFFFEF3C7)
    val OnStartingContainer = Color(0xFF451A03)
    val StartingContainerDark = Color(0xFF451A03)
    val OnStartingContainerDark = Color(0xFFFEF3C7)

    val Fault = Color(0xFFDC2626)
    val FaultDark = Color(0xFFF87171)
}

@Composable
fun liveColor(): Color =
    if (isSystemInDarkTheme()) StatusPalette.LiveDark else StatusPalette.Live

@Composable
fun startingColor(): Color =
    if (isSystemInDarkTheme()) StatusPalette.StartingDark else StatusPalette.Starting

@Composable
fun faultColor(): Color =
    if (isSystemInDarkTheme()) StatusPalette.FaultDark else StatusPalette.Fault

@Composable
fun liveContainer(): Color =
    if (isSystemInDarkTheme()) StatusPalette.LiveContainerDark else StatusPalette.LiveContainer

@Composable
fun onLiveContainer(): Color =
    if (isSystemInDarkTheme()) StatusPalette.OnLiveContainerDark else StatusPalette.OnLiveContainer

@Composable
fun startingContainer(): Color =
    if (isSystemInDarkTheme()) StatusPalette.StartingContainerDark else StatusPalette.StartingContainer

@Composable
fun onStartingContainer(): Color =
    if (isSystemInDarkTheme()) StatusPalette.OnStartingContainerDark else StatusPalette.OnStartingContainer

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(10.dp)) { drawCircle(color) }
}

@Composable
fun StatusBadge(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = container
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun siteStatusDotColor(status: SiteStatus, hosted: Boolean): Color {
    if (hosted) return liveColor()
    return when (status) {
        SiteStatus.RUNNING -> liveColor()
        SiteStatus.ERROR -> faultColor()
        SiteStatus.STOPPED -> MaterialTheme.colorScheme.outline
    }
}

/** Consistent site chips: RUNNING green, ERROR red, STOPPED neutral, plus HOSTED green. */
@Composable
fun SiteStatusBadges(
    status: SiteStatus,
    hosted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            SiteStatus.RUNNING -> StatusBadge("RUNNING", liveContainer(), onLiveContainer())
            SiteStatus.ERROR -> StatusBadge(
                "ERROR",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer
            )
            SiteStatus.STOPPED -> StatusBadge(
                "STOPPED",
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hosted) StatusBadge("HOSTED", liveContainer(), onLiveContainer())
    }
}

/** Tunnel timeline badge: STARTING amber, LIVE green, ERROR red, Idle renders nothing. */
@Composable
fun TunnelBadge(state: TunnelState, modifier: Modifier = Modifier) {
    when (state) {
        is TunnelState.Starting -> StatusBadge("STARTING", startingContainer(), onStartingContainer(), modifier)
        is TunnelState.Live -> StatusBadge("TUNNEL LIVE", liveContainer(), onLiveContainer(), modifier)
        is TunnelState.Error -> StatusBadge(
            "TUNNEL ERROR",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            modifier
        )
        is TunnelState.Idle -> Unit
    }
}

/** VPS relay badge with the same status language. */
@Composable
fun RelayBadge(state: RelayState, modifier: Modifier = Modifier) {
    when (state) {
        is RelayState.Live -> StatusBadge("LIVE", liveContainer(), onLiveContainer(), modifier)
        is RelayState.Connecting -> StatusBadge("CONNECTING", startingContainer(), onStartingContainer(), modifier)
        is RelayState.Error -> StatusBadge(
            "ERROR",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            modifier
        )
        is RelayState.Disconnected -> StatusBadge(
            "STOPPED",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            modifier
        )
    }
}

/** Colored section header: primary icon + primary title text. */
@Composable
fun SectionHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
