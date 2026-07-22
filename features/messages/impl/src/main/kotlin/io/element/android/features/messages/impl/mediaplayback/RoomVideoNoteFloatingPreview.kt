/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.features.messages.impl.mediaplayback



import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.scaleIn

import androidx.compose.animation.scaleOut

import androidx.compose.foundation.background

import androidx.compose.foundation.gestures.detectDragGestures

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.unit.IntOffset

import androidx.compose.ui.unit.dp

import androidx.compose.ui.viewinterop.AndroidView

import coil3.compose.AsyncImage

import io.element.android.compound.theme.ElementTheme

import io.element.android.features.messages.impl.videonotes.VideoNotePlayerViewFactory

import io.element.android.features.messages.impl.videonotes.VideoNoteRingProgressCanvas

import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground

import io.element.android.libraries.matrix.api.core.EventId

import io.element.android.libraries.matrix.api.core.RoomId

import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_HEIGHT

import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_WIDTH

import io.element.android.libraries.matrix.ui.media.MediaRequestData

import kotlin.math.roundToInt



@Composable

fun RoomVideoNoteFloatingPreview(

    coordinator: RoomMediaPlaybackCoordinator,

    onOpenPlayingMessage: (RoomId, EventId) -> Unit,

    modifier: Modifier = Modifier,

) {

    val playback = coordinator.videoNotePlayback

    val activeEventId by playback.activeEventId.collectAsState()

    val activeRoomId by playback.activeRoomId.collectAsState()

    val isSourceVisible by playback.isSourceVisible.collectAsState()

    val progress by playback.progress.collectAsState()

    val isPlaying by playback.isPlaying.collectAsState()

    val thumbnailSource by playback.thumbnailSource.collectAsState()

    val blurHash by playback.blurHash.collectAsState()

    val player = playback.getPlayer()



    val showFloating = activeEventId != null &&

        !isSourceVisible &&

        (isPlaying || progress > 0f)



    val ringPadding = 8.dp

    val ringStrokeWidth = 5.dp

    val circleSize = 96.dp

    val ringSize = circleSize + ringPadding * 2

    val edgePadding = 12.dp

    val density = LocalDensity.current

    val ringPaddingPx = with(density) { ringPadding.toPx() }

    val ringStrokePx = with(density) { ringStrokeWidth.toPx() }

    val widgetSizePx = with(density) { ringSize.toPx() }

    val edgePaddingPx = with(density) { edgePadding.toPx() }

    val dismissThresholdPx = with(density) { 56.dp.toPx() }



    var offsetX by rememberSaveable { mutableFloatStateOf(-1f) }

    var offsetY by rememberSaveable { mutableFloatStateOf(-1f) }



    LaunchedEffect(activeEventId) {

        offsetX = -1f

        offsetY = -1f

    }



    AnimatedVisibility(

        visible = showFloating && player != null,

        enter = fadeIn() + scaleIn(initialScale = 0.85f),

        exit = fadeOut() + scaleOut(targetScale = 0.85f),

        modifier = modifier,

    ) {

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

            val maxOffsetX = (constraints.maxWidth - widgetSizePx - edgePaddingPx * 2).coerceAtLeast(0f)

            val maxOffsetY = (constraints.maxHeight - widgetSizePx - edgePaddingPx * 2).coerceAtLeast(0f)



            if (offsetX < 0f || offsetY < 0f) {

                offsetX = maxOffsetX

                offsetY = maxOffsetY

            }



            fun snapToNearestCorner(x: Float, y: Float) {

                val snapLeft = x < maxOffsetX / 2f

                val snapTop = y < maxOffsetY / 2f

                offsetX = if (snapLeft) 0f else maxOffsetX

                offsetY = if (snapTop) 0f else maxOffsetY

            }



            Box(

                modifier = Modifier

                    .offset {

                        IntOffset(

                            (edgePaddingPx + offsetX).roundToInt(),

                            (edgePaddingPx + offsetY).roundToInt(),

                        )

                    }

                    .pointerInput(activeEventId) {

                        detectDragGestures(

                            onDragEnd = {

                                val left = offsetX

                                val top = offsetY

                                val right = offsetX + widgetSizePx

                                val bottom = offsetY + widgetSizePx

                                val outOfBounds = left < -dismissThresholdPx ||

                                    top < -dismissThresholdPx ||

                                    right > maxOffsetX + widgetSizePx + dismissThresholdPx ||

                                    bottom > maxOffsetY + widgetSizePx + dismissThresholdPx

                                if (outOfBounds) {

                                    val eventId = activeEventId

                                    if (eventId != null) {

                                        coordinator.stopVideoNote(eventId)

                                    }

                                } else {

                                    snapToNearestCorner(offsetX, offsetY)

                                }

                            },

                            onDragCancel = {

                                snapToNearestCorner(offsetX, offsetY)

                            },

                            onDrag = { change, dragAmount ->

                                change.consume()

                                offsetX = (offsetX + dragAmount.x).coerceIn(-widgetSizePx, maxOffsetX + widgetSizePx)

                                offsetY = (offsetY + dragAmount.y).coerceIn(-widgetSizePx, maxOffsetY + widgetSizePx)

                            },

                        )

                    }

                    .pointerInput(activeEventId, activeRoomId) {

                        detectTapGestures {

                            val eventId = activeEventId

                            val roomId = activeRoomId

                            if (eventId != null && roomId != null) {

                                onOpenPlayingMessage(roomId, eventId)

                            }

                        }

                    }

                    .padding(4.dp),

                contentAlignment = Alignment.Center,

            ) {

                VideoNoteRingProgressCanvas(

                    progress = progress,

                    ringPaddingPx = ringPaddingPx,

                    ringStrokePx = ringStrokePx,

                    backgroundColor = ElementTheme.colors.iconQuaternary,

                    progressColor = ElementTheme.colors.iconAccentPrimary,

                    showScrubberDot = isPlaying || progress > 0f,

                    modifier = Modifier.size(ringSize),

                )

                Box(

                    modifier = Modifier

                        .size(circleSize)

                        .clip(CircleShape)

                        .blurHashBackground(blurHash, alpha = 0.9f)

                        .background(Color.Black),

                    contentAlignment = Alignment.Center,

                ) {

                    if (isPlaying) {

                        AndroidView(

                            modifier = Modifier

                                .fillMaxSize()

                                .clip(CircleShape),

                            factory = { context ->

                                VideoNotePlayerViewFactory.create(context).apply {

                                    this.player = player

                                }

                            },

                            update = { view ->

                                VideoNotePlayerViewFactory.bindPlayer(view, player)

                            },

                        )

                    } else {

                        thumbnailSource?.let { source ->

                            AsyncImage(

                                modifier = Modifier.fillMaxSize(),

                                model = MediaRequestData(

                                    source = source,

                                    kind = MediaRequestData.Kind.Thumbnail(

                                        width = MAX_THUMBNAIL_WIDTH,

                                        height = MAX_THUMBNAIL_HEIGHT,

                                    ),

                                ),

                                contentDescription = null,

                            )

                        }

                    }

                }

            }

        }

    }

}

