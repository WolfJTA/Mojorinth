package com.example.modrinthforandroid.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Data class representing an active or recently-completed download.
 */
data class DownloadStatus(
    val id: String,           // version ID
    val title: String,        // e.g. "Sodium 0.6.1"
    val filename: String,
    val progress: Int,        // 0–100, or -1 for indeterminate
    val isDone: Boolean = false,
    val isFailed: Boolean = false
)

/**
 * A slim, animated progress banner that slides in from the top whenever
 * there are active or recently-finished downloads.
 *
 * Usage: place this directly inside your Scaffold content, above the rest
 * of the screen content (or in the topBar slot alongside TopAppBar).
 *
 * @param downloads  List of current download statuses.
 * @param onDismiss  Called when the user dismisses a completed download banner.
 */
@Composable
fun DownloadProgressBanner(
    downloads: List<DownloadStatus>,
    modifier: Modifier = Modifier,
    onDismiss: (String) -> Unit = {}
) {
    // Only show when there is something to show
    val visible = downloads.isNotEmpty()

    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit    = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            downloads.forEach { dl ->
                DownloadProgressRow(status = dl, onDismiss = { onDismiss(dl.id) })
            }
        }
    }
}

@Composable
private fun DownloadProgressRow(
    status: DownloadStatus,
    onDismiss: () -> Unit
) {
    val green  = MaterialTheme.colorScheme.primary
    val isDone = status.isDone
    val isFail = status.isFailed

    // Auto-dismiss after 3 s once completed
    LaunchedEffect(isDone) {
        if (isDone) {
            delay(3_000)
            onDismiss()
        }
    }

    // Animated progress value
    val animatedProgress by animateFloatAsState(
        targetValue    = if (isDone) 1f else (status.progress.coerceIn(0, 100) / 100f),
        animationSpec  = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label          = "downloadProgress"
    )

    // Shimmer effect for indeterminate state
    val shimmerTranslate = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmerTranslate.animateFloat(
        initialValue   = -1f,
        targetValue    = 2f,
        animationSpec  = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label          = "shimmerX"
    )

    Surface(
        shape          = RoundedCornerShape(10.dp),
        color          = when {
            isDone -> green.copy(alpha = 0.12f)
            isFail -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            else   -> MaterialTheme.colorScheme.background
        },
        tonalElevation = 1.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status icon / spinner
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isDone -> Icon(
                            Icons.Default.Check,
                            contentDescription = "Done",
                            tint     = green,
                            modifier = Modifier.size(18.dp)
                        )
                        isFail -> Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        else   -> CircularProgressIndicator(
                            modifier   = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color      = green
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = status.title,
                        style    = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text  = when {
                            isDone -> "Downloaded ✓"
                            isFail -> "Download failed"
                            status.progress < 0 -> "Starting…"
                            else -> "${status.progress}%  •  ${status.filename}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isDone -> green
                            isFail -> MaterialTheme.colorScheme.error
                            else   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        }
                    )
                }

                if (isDone || isFail) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Text("×", fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                } else {
                    Text(
                        text  = if (status.progress >= 0) "${status.progress}%" else "…",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = green
                    )
                }
            }

            // Progress bar (only for active / not indeterminate)
            if (!isDone && !isFail) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(green.copy(alpha = 0.15f))
                ) {
                    if (status.progress < 0) {
                        // Indeterminate shimmer bar
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.35f)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            green.copy(alpha = 0.8f),
                                            Color.Transparent
                                        ),
                                        startX = shimmerX * 300f,
                                        endX   = shimmerX * 300f + 200f
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(50))
                                .background(green)
                        )
                    }
                }
            }
        }
    }
}