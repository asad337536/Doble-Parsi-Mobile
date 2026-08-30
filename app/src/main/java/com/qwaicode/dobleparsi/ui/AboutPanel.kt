/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Source and releases: github.com/QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qwaicode.dobleparsi.R
import com.qwaicode.dobleparsi.core.Credits

/**
 * Developer + copyright card.
 *
 * The real GitHub mark, an always-on animation on it (a gradient ring orbiting the
 * logo, a breathing logo, and a full spin whenever it is tapped) and one tap straight
 * to github.com/QW-AI-Code.
 */
@Composable
fun AboutPanel() {
    val context = LocalContext.current
    val signature = remember { Credits.signature() }

    val open: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Panel(title = str(R.string.section_about), icon = Icons.Filled.Copyright) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GitHubMark { open(Credits.GITHUB_URL) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = Credits.AUTHOR,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DP.Text
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str(R.string.about_role),
                    style = MaterialTheme.typography.labelSmall,
                    color = DP.TextMuted
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Credits.GITHUB_SHORT,
                    style = MaterialTheme.typography.bodySmall,
                    color = DP.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { open(Credits.GITHUB_URL) }
                        .padding(vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinkPill(
                label = str(R.string.about_open),
                icon = Icons.Filled.OpenInNew,
                modifier = Modifier.weight(1f)
            ) { open(Credits.GITHUB_URL) }
            LinkPill(
                label = str(R.string.about_star),
                icon = Icons.Filled.Star,
                modifier = Modifier.weight(1f)
            ) { open(Credits.GITHUB_URL) }
        }

        Spacer(Modifier.height(14.dp))
        RowDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            text = "${Credits.COPYRIGHT} · ${str(R.string.about_rights)}",
            style = MaterialTheme.typography.labelSmall,
            color = DP.TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = str(R.string.about_license),
            style = MaterialTheme.typography.labelSmall,
            color = DP.TextMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = signature,
            style = MaterialTheme.typography.labelSmall,
            color = DP.TextMuted
        )
    }
}

/** GitHub mark with an orbiting gradient ring, a breathing logo and a tap spin. */
@Composable
private fun GitHubMark(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "github")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "orbit"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "breathe"
    )
    var taps by remember { mutableStateOf(0) }
    val kick by animateFloatAsState(
        targetValue = taps * 360f,
        animationSpec = tween(720),
        label = "kick"
    )

    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .clickable {
                taps += 1
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer { rotationZ = orbit }
                .background(
                    Brush.sweepGradient(
                        listOf(DP.BrandStart, DP.BrandEnd, DP.Accent, DP.BrandStart)
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(DP.CardHigh, CircleShape)
        )
        Icon(
            painter = painterResource(R.drawable.ic_github),
            contentDescription = Credits.GITHUB_SHORT,
            tint = DP.Text,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                    rotationZ = kick
                }
        )
    }
}

@Composable
private fun LinkPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(DP.Brand.copy(alpha = 0.14f))
            .border(1.dp, DP.Brand.copy(alpha = 0.30f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = DP.Brand, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = DP.Text,
            fontWeight = FontWeight.Bold
        )
    }
}
