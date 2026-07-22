/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.text.SpannedString
import android.os.SystemClock
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.geometry.Size
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.sqrt
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.components.ATimelineItemEventRow
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayout
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContentProvider
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.features.messages.impl.timeline.protection.coerceRatioWhenHidingContent
import io.element.android.features.messages.impl.videonotes.timeline.VideoNoteInlinePlaybackEvent
import io.element.android.features.messages.impl.videonotes.timeline.VideoNoteInlinePlaybackState
import io.element.android.features.messages.impl.mediaplayback.LocalRoomMediaPlaybackCoordinator
import io.element.android.features.messages.impl.mediaplayback.LocalRoomId
import io.element.android.features.messages.impl.videonotes.VideoNotePlayerViewFactory
import io.element.android.features.messages.impl.videonotes.VideoNoteRingProgressCanvas
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_HEIGHT
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_WIDTH
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.time.isTalkbackActive
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun TimelineItemVideoView(
    content: TimelineItemVideoContent,
    eventId: EventId? = null,
    hideMediaContent: Boolean,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onShowContentClick: () -> Unit,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    videoNoteInlinePlaybackState: VideoNoteInlinePlaybackState = VideoNoteInlinePlaybackState.empty(),
    localSendState: LocalEventSendState? = null,
    modifier: Modifier = Modifier,
) {
    val isTalkbackActive = isTalkbackActive()
    val a11yLabel = stringResource(CommonStrings.common_video)
    val description = content.caption?.let { "$a11yLabel: $it" } ?: a11yLabel
    Column(modifier = modifier) {
        if (content.isVideoNote) {
            val containerModifier = if (content.showCaption) {
                Modifier.padding(top = 6.dp)
            } else {
                Modifier
            }
            // Video note rendering (circular format, Telegram-like inline playback)
            val baseCircleSize = 150.dp
            val expandedCircleSize = 220.dp
            val ringPadding = 10.dp
            val ringStrokeWidth = 6.dp
            var isExpanded by remember { mutableStateOf(false) }
            val playbackCoordinator = LocalRoomMediaPlaybackCoordinator.current
            val roomId = LocalRoomId.current
            val roomPlayback = playbackCoordinator?.videoNotePlayback
            val isThisActive = eventId != null && roomPlayback?.isActive(eventId) == true
            val enginePlaying by (roomPlayback?.isPlaying ?: remember { MutableStateFlow(false) }).collectAsState()
            val engineProgress by (roomPlayback?.progress ?: remember { MutableStateFlow(0f) }).collectAsState()
            val engineLoading by (roomPlayback?.isLoading ?: remember { MutableStateFlow(false) }).collectAsState()
            val isPlaying = isThisActive && enginePlaying
            val progress = if (isThisActive) engineProgress else 0f
            val player = if (isThisActive) roomPlayback?.getPlayer() else null
            val circleSize by animateDpAsState(
                targetValue = if (isExpanded || isThisActive) expandedCircleSize else baseCircleSize,
                label = "VideoNoteCircleSize",
            )
            val ringSize = circleSize + ringPadding * 2

            val density = LocalDensity.current
            val ringStrokePx = with(density) { ringStrokeWidth.toPx() }
            val ringPaddingPx = with(density) { ringPadding.toPx() }

            val rawVideoUrl = content.mediaSource.url
            val directInlineUri: Uri? = rawVideoUrl.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("file:", ignoreCase = true) ||
                    it.startsWith("content:", ignoreCase = true)
            }?.let(Uri::parse)
            val isMxc = rawVideoUrl.startsWith("mxc://", ignoreCase = true)
            val mxcInlineUri = if (isMxc) videoNoteInlinePlaybackState.localUri else null
            val inlineUri = directInlineUri ?: mxcInlineUri
            val canInlinePlay = !hideMediaContent && inlineUri != null
            val canRequestPrepare = !hideMediaContent && isMxc && inlineUri == null
            val isPreparing = videoNoteInlinePlaybackState.isPreparing || (isThisActive && engineLoading)

            val ringBgColor = ElementTheme.colors.iconQuaternary
            val ringProgressColor = ElementTheme.colors.iconAccentPrimary

            var pendingAutoPlay by remember { mutableStateOf(false) }

            fun beginVideoNoteSession() {
                playbackCoordinator?.pauseVoicePlayback()
                playbackCoordinator?.pinVideoBar()
            }

            LaunchedEffect(inlineUri, pendingAutoPlay, eventId, roomPlayback) {
                val id = eventId
                val uri = inlineUri
                val playback = roomPlayback
                if (!pendingAutoPlay || uri == null || id == null || playback == null || roomId == null) return@LaunchedEffect
                pendingAutoPlay = false
                beginVideoNoteSession()
                playback.attachSource(
                    eventId = id,
                    roomId = roomId,
                    uri = uri,
                    thumbnailSource = content.thumbnailSource ?: content.mediaSource,
                    blurHash = content.blurHash,
                )
                playback.play()
                isExpanded = true
            }

            LaunchedEffect(isThisActive) {
                if (!isThisActive) {
                    isExpanded = false
                }
            }

            val windowInfo = LocalWindowInfo.current
            val viewConfiguration = LocalViewConfiguration.current
            var isRingScrubbing by remember { mutableStateOf(false) }
            val blockScrollWhileScrubbing = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        return if (isRingScrubbing) available else Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        return if (isRingScrubbing) available else Offset.Zero
                    }
                }
            }

            fun pausePlayback() {
                roomPlayback?.pause()
            }

            fun resumePlayback() {
                val id = eventId
                val uri = inlineUri
                val playback = roomPlayback
                if (id == null || uri == null || playback == null || roomId == null) {
                    return
                }
                if (!playback.isActive(id)) {
                    beginVideoNoteSession()
                    playback.attachSource(
                        eventId = id,
                        roomId = roomId,
                        uri = uri,
                        thumbnailSource = content.thumbnailSource ?: content.mediaSource,
                        blurHash = content.blurHash,
                    )
                }
                playback.play()
                isExpanded = true
            }

            fun stopPlayback() {
                val id = eventId
                if (id != null) {
                    playbackCoordinator?.stopVideoNote(id)
                } else {
                    roomPlayback?.stop()
                }
                isExpanded = false
            }

            DisposableEffect(eventId, roomPlayback) {
                onDispose {
                    val id = eventId
                    if (id != null && roomPlayback?.isActive(id) == true) {
                        roomPlayback.setSourceVisible(false)
                    }
                }
            }

            var scrubJustUntilMs by remember { mutableLongStateOf(0L) }
            fun scrubTo(fraction: Float) {
                roomPlayback?.seekToProgress(fraction)
            }

            fun handleCircleTap() {
                if (!canInlinePlay) {
                    if (canRequestPrepare) {
                        if (!isPreparing) {
                            pendingAutoPlay = true
                            isExpanded = true
                            videoNoteInlinePlaybackState.eventSink(VideoNoteInlinePlaybackEvent.Prepare)
                        }
                    }
                    return
                }
                if (isPlaying) {
                    pausePlayback()
                } else {
                    resumePlayback()
                }
            }

            fun isOnRing(position: Offset, width: Float, height: Float): Boolean {
                val center = Offset(width / 2f, height / 2f)
                val dist = hypot(position.x - center.x, position.y - center.y)
                val outer = min(width, height) / 2f
                val inner = max(0f, outer - (ringPaddingPx + ringStrokePx * 3f))
                return dist in inner..outer
            }

            Box(
                modifier = containerModifier.onGloballyPositioned { coordinates ->
                    val id = eventId
                    val playback = roomPlayback
                    if (id == null || playback?.isActive(id) != true) return@onGloballyPositioned
                    val bounds = coordinates.boundsInWindow()
                    val viewportHeight = windowInfo.containerSize.height.toFloat()
                    val marginPx = with(density) { 4.dp.toPx() }
                    val isVisible = bounds.bottom > marginPx && bounds.top < viewportHeight - marginPx
                    playback.setSourceVisible(isVisible)
                },
                contentAlignment = Alignment.Center,
            ) {
                ProtectedView(
                    hideContent = hideMediaContent,
                    onShowClick = onShowContentClick,
                ) {
                    Box(
                        modifier = Modifier.size(ringSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Video circle
                        Box(
                            modifier = Modifier
                                .size(circleSize)
                                .clip(CircleShape)
                                .blurHashBackground(content.blurHash, alpha = 0.9f),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isThisActive && canInlinePlay && player != null) {
                                AndroidView(
                                    modifier = Modifier
                                        .matchParentSize()
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
                                var isLoaded by remember { mutableStateOf(false) }
                                AsyncImage(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .then(if (isLoaded) Modifier.background(Color.White) else Modifier),
                                    model = MediaRequestData(
                                        source = content.thumbnailSource ?: content.mediaSource,
                                        kind = MediaRequestData.Kind.Thumbnail(
                                            width = content.thumbnailWidth?.toLong() ?: MAX_THUMBNAIL_WIDTH,
                                            height = content.thumbnailHeight?.toLong() ?: MAX_THUMBNAIL_HEIGHT,
                                        )
                                    ),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.Center,
                                    contentDescription = description,
                                    onState = { isLoaded = it is AsyncImagePainter.State.Success },
                                )
                                if (!isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.35f))
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isPreparing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .semantics { hideFromAccessibility() },
                                            )
                                        } else {
                                            Image(
                                                imageVector = CompoundIcons.PlaySolid(),
                                                contentDescription = stringResource(id = CommonStrings.a11y_play),
                                                colorFilter = ColorFilter.tint(Color.White),
                                                modifier = Modifier.semantics { hideFromAccessibility() }
                                            )
                                        }
                                    }
                                }
                            }
                            MediaUploadProgressOverlay(
                                localSendState = localSendState,
                                showProgressLabel = false,
                            )
                        }

                        // Transparent gesture layer + ring on top (receives all touches)
                        if (!isTalkbackActive) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .nestedScroll(blockScrollWhileScrubbing)
                                    .pointerInput(
                                        isThisActive,
                                        canInlinePlay,
                                        enginePlaying,
                                        ringPaddingPx,
                                        ringStrokePx,
                                        viewConfiguration.touchSlop,
                                    ) {
                                        val width = size.width.toFloat()
                                        val height = size.height.toFloat()
                                        val dismissThresholdPx = with(density) { 72.dp.toPx() }
                                        val tapSlopPx = viewConfiguration.touchSlop

                                        fun fractionFrom(pos: Offset): Float {
                                            val center = Offset(width / 2f, height / 2f)
                                            val px = pos.x - center.x
                                            val py = pos.y - center.y
                                            val angle = atan2(py, px) + (PI / 2.0)
                                            var fraction = (angle / (2.0 * PI)).toFloat()
                                            if (fraction < 0f) fraction += 1f
                                            return fraction.coerceIn(0f, 1f)
                                        }

                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val downPosition = down.position

                                            if (canInlinePlay && isOnRing(downPosition, width, height)) {
                                                isRingScrubbing = true
                                                try {
                                                    scrubJustUntilMs = SystemClock.uptimeMillis() + 250L
                                                    scrubTo(fractionFrom(downPosition))
                                                    down.consume()
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                                        val change = event.changes.firstOrNull { it.id == down.id }
                                                            ?: event.changes.firstOrNull()
                                                            ?: break
                                                        if (!change.pressed) break
                                                        scrubTo(fractionFrom(change.position))
                                                        change.consume()
                                                    }
                                                } finally {
                                                    isRingScrubbing = false
                                                }
                                                return@awaitEachGesture
                                            }

                                            var accumulatedDismissDragX = 0f
                                            var movedPastSlop = false
                                            down.consume()
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                    ?: event.changes.firstOrNull()
                                                    ?: break
                                                if (!change.pressed) {
                                                    if (!movedPastSlop &&
                                                        SystemClock.uptimeMillis() >= scrubJustUntilMs &&
                                                        !isOnRing(downPosition, width, height)
                                                    ) {
                                                        handleCircleTap()
                                                    } else if (isThisActive && accumulatedDismissDragX > dismissThresholdPx) {
                                                        stopPlayback()
                                                    }
                                                    break
                                                }
                                                val drag = change.position - change.previousPosition
                                                if (isThisActive && drag.x > 0f) {
                                                    accumulatedDismissDragX += drag.x
                                                }
                                                val totalMove = change.position - downPosition
                                                if (sqrt(totalMove.x * totalMove.x + totalMove.y * totalMove.y) > tapSlopPx) {
                                                    movedPastSlop = true
                                                }
                                                change.consume()
                                            }
                                        }
                                    },
                            ) {
                                VideoNoteRingProgressCanvas(
                                    progress = if (canInlinePlay) progress else 0f,
                                    ringPaddingPx = ringPaddingPx,
                                    ringStrokePx = ringStrokePx,
                                    backgroundColor = ringBgColor,
                                    progressColor = ringProgressColor,
                                    showScrubberDot = canInlinePlay && (isPlaying || progress > 0f),
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        } else {
                            VideoNoteRingProgressCanvas(
                                progress = if (canInlinePlay) progress else 0f,
                                ringPaddingPx = ringPaddingPx,
                                ringStrokePx = ringStrokePx,
                                backgroundColor = ringBgColor,
                                progressColor = ringProgressColor,
                                showScrubberDot = canInlinePlay && (isPlaying || progress > 0f),
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                }
            }
        } else {
            // Regular video rendering
            val containerModifier = if (content.showCaption) {
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
            } else {
                Modifier
            }
            TimelineItemAspectRatioBox(
                modifier = containerModifier.blurHashBackground(content.blurHash, alpha = 0.9f),
                aspectRatio = coerceRatioWhenHidingContent(content.aspectRatio, hideMediaContent),
                contentAlignment = Alignment.Center,
            ) {
                ProtectedView(
                    hideContent = hideMediaContent,
                    onShowClick = onShowContentClick,
                ) {
                    var isLoaded by remember { mutableStateOf(false) }
                    AsyncImage(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isLoaded) Modifier.background(Color.White) else Modifier)
                            .then(
                                if (!isTalkbackActive && onContentClick != null) {
                                    Modifier
                                        .combinedClickable(
                                            onClick = onContentClick,
                                            onLongClick = onLongClick,
                                        )
                                        .onKeyboardContextMenuAction(onLongClick)
                                } else {
                                    Modifier
                                }
                            ),
                        model = MediaRequestData(
                            source = content.thumbnailSource,
                            kind = MediaRequestData.Kind.Thumbnail(
                                width = content.thumbnailWidth?.toLong() ?: MAX_THUMBNAIL_WIDTH,
                                height = content.thumbnailHeight?.toLong() ?: MAX_THUMBNAIL_HEIGHT,
                            )
                        ),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        contentDescription = description,
                        onState = { isLoaded = it is AsyncImagePainter.State.Success },
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            imageVector = CompoundIcons.PlaySolid(),
                            contentDescription = stringResource(id = CommonStrings.a11y_play),
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                    }
                    MediaUploadProgressOverlay(
                        localSendState = localSendState,
                        showProgressLabel = false,
                    )
                }
            }
        }

        if (content.showCaption) {
            Spacer(modifier = Modifier.height(8.dp))
            val caption = if (LocalInspectionMode.current) {
                SpannedString(content.caption)
            } else {
                content.formattedCaption ?: SpannedString(content.caption)
            }
            CompositionLocalProvider(
                LocalContentColor provides ElementTheme.colors.textPrimary,
                LocalTextStyle provides ElementTheme.typography.fontBodyLgRegular,
            ) {
                val aspectRatio = content.aspectRatio ?: DEFAULT_ASPECT_RATIO
                EditorStyledText(
                    modifier = Modifier
                        .padding(horizontal = 4.dp) // This is (12.dp - 8.dp) contentPadding from CommonLayout
                        .widthIn(min = MIN_HEIGHT_IN_DP.dp * aspectRatio, max = MAX_HEIGHT_IN_DP.dp * aspectRatio),
                    text = caption,
                    onLinkClickedListener = onLinkClick,
                    onLinkLongClickedListener = onLinkLongClick,
                    style = ElementRichTextEditorStyle.textStyle(),
                    releaseOnDetach = false,
                    onTextLayout = ContentAvoidingLayout.measureLegacyLastTextLine(onContentLayoutChange = onContentLayoutChange),
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemVideoViewPreview(@PreviewParameter(TimelineItemVideoContentProvider::class) content: TimelineItemVideoContent) = ElementPreview {
    TimelineItemVideoView(
        content = content,
        hideMediaContent = false,
        onShowContentClick = {},
        onContentClick = {},
        onLongClick = {},
        onLinkClick = {},
        onLinkLongClick = {},
        onContentLayoutChange = {},
    )
}

@PreviewsDayNight
@Composable
internal fun TimelineItemVideoViewHideMediaContentPreview() = ElementPreview {
    TimelineItemVideoView(
        content = aTimelineItemVideoContent(),
        hideMediaContent = true,
        onShowContentClick = {},
        onContentClick = {},
        onLongClick = {},
        onLinkClick = {},
        onLinkLongClick = {},
        onContentLayoutChange = {},
    )
}

@PreviewsDayNight
@Composable
internal fun TimelineVideoWithCaptionRowPreview() = ElementPreview {
    Column {
        sequenceOf(false, true).forEach { isMine ->
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = isMine,
                    content = aTimelineItemVideoContent().copy(
                        filename = "video.mp4",
                        caption = "A long caption that may wrap into several lines",
                        aspectRatio = 2.5f,
                    ),
                    groupPosition = TimelineItemGroupPosition.Last,
                ),
            )
        }
        ATimelineItemEventRow(
            event = aTimelineItemEvent(
                isMine = false,
                content = aTimelineItemVideoContent().copy(
                    filename = "video.mp4",
                    caption = "Video with null aspect ratio",
                    aspectRatio = null,
                ),
                groupPosition = TimelineItemGroupPosition.Last,
            ),
        )
    }
}
