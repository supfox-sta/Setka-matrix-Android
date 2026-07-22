/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.LocalSetkaCustomization
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.parseSetkaColorOrNull

@Composable
fun RoomMediaPlaybackBarView(
    state: RoomMediaPlaybackBarState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RoomMediaPlaybackBarState.Hidden -> Unit
        is RoomMediaPlaybackBarState.Visible -> {
            RoomMediaPlaybackBarRow(
                state = state,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun RoomMediaPlaybackBarRow(
    state: RoomMediaPlaybackBarState.Visible,
    modifier: Modifier = Modifier,
) {
    val customization = LocalSetkaCustomization.current
    val bubbleColor = parseSetkaColorOrNull(customization.serviceBubbleColorHex)
        ?: ElementTheme.colors.bgSubtleSecondary
    val textColor = parseSetkaColorOrNull(customization.serviceTextColorHex)
        ?: ElementTheme.colors.textPrimary
    val title = state.title.ifBlank {
        when (state.kind) {
            RoomMediaPlaybackKind.Voice -> stringResource(R.string.room_media_playback_voice_title)
            RoomMediaPlaybackKind.VideoNote -> stringResource(R.string.room_media_playback_video_note_title)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(color = bubbleColor.copy(alpha = 0.92f), shape = RoundedCornerShape(14.dp))
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // Consume taps on the bar background so they don't pass through to the timeline.
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = { state.eventSink(RoomMediaPlaybackBarEvent.TogglePlayPause) },
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (state.isPlaying) CompoundIcons.Pause() else CompoundIcons.PlaySolid(),
                    contentDescription = null,
                    tint = textColor,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = textColor,
                maxLines = 1,
            )
            MediaPlaybackProgressBar(
                progress = state.progress,
                enabled = !state.isLoading,
                onSeek = { state.eventSink(RoomMediaPlaybackBarEvent.Seek(it)) },
            )
        }
        IconButton(onClick = { state.eventSink(RoomMediaPlaybackBarEvent.Stop) }) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = null,
                tint = textColor,
            )
        }
    }
}

@Composable
private fun MediaPlaybackProgressBar(
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = ElementTheme.colors.bgSubtlePrimary
    val progressColor = ElementTheme.colors.iconAccentPrimary
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val fraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(fraction)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) break
                        change.consume()
                        val dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek(dragFraction)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(trackColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress.coerceAtLeast(0.02f))
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(progressColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .padding(end = 6.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(progressColor),
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun RoomMediaPlaybackBarViewPreview() = ElementPreview {
    RoomMediaPlaybackBarView(
        state = RoomMediaPlaybackBarState.Visible(
            kind = RoomMediaPlaybackKind.VideoNote,
            title = "Видео-кружок",
            progress = 0.42f,
            isPlaying = true,
            isLoading = false,
            eventSink = {},
        ),
    )
}
