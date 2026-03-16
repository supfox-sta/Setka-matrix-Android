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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_HEIGHT
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_WIDTH
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.time.isTalkbackActive
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun TimelineItemVideoView(
    content: TimelineItemVideoContent,
    hideMediaContent: Boolean,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onShowContentClick: () -> Unit,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    videoNoteInlinePlaybackState: VideoNoteInlinePlaybackState = VideoNoteInlinePlaybackState.empty(),
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
            var isPlaying by remember { mutableStateOf(false) }
            val circleSize by animateDpAsState(if (isExpanded) expandedCircleSize else baseCircleSize, label = "VideoNoteCircleSize")
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
            val isPreparing = videoNoteInlinePlaybackState.isPreparing

            var progress by remember { mutableFloatStateOf(0f) }
            val ringBgColor = ElementTheme.colors.iconQuaternary
            val ringProgressColor = ElementTheme.colors.iconAccentPrimary
            val context = LocalContext.current
            val player: ExoPlayer? = remember(inlineUri) {
                inlineUri?.let { uri ->
                    ExoPlayer.Builder(context.applicationContext).build().apply {
                        repeatMode = ExoPlayer.REPEAT_MODE_OFF
                        volume = 1f
                        setMediaItem(MediaItem.fromUri(uri))
                        prepare()
                        playWhenReady = false
                    }
                }
            }
            DisposableEffect(player) {
                onDispose { player?.release() }
            }

            var pendingAutoPlay by remember { mutableStateOf(false) }
            LaunchedEffect(inlineUri, pendingAutoPlay, player) {
                val p = player
                if (!pendingAutoPlay || inlineUri == null || p == null) return@LaunchedEffect
                pendingAutoPlay = false
                isExpanded = true
                isPlaying = true
                p.volume = 1f
                p.playWhenReady = true
                p.play()
            }

            LaunchedEffect(isPlaying) {
                while (isPlaying) {
                    val p = player ?: break
                    val durationMs = p.duration.coerceAtLeast(1)
                    val posMs = p.currentPosition.coerceAtLeast(0)
                    progress = (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    delay(50.milliseconds)
                }
            }

            fun stopAndReset() {
                isPlaying = false
                isExpanded = false
                player?.let {
                    it.playWhenReady = false
                    it.pause()
                    it.seekTo(0)
                }
                progress = 0f
            }

            val stopAndResetLatest by rememberUpdatedState(::stopAndReset)
            DisposableEffect(player) {
                val p = player ?: return@DisposableEffect onDispose { }
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            stopAndResetLatest()
                        }
                    }
                }
                p.addListener(listener)
                onDispose { p.removeListener(listener) }
            }

            var scrubJustUntilMs by remember { mutableLongStateOf(0L) }
            fun scrubTo(fraction: Float) {
                val p = player ?: return
                val durationMs = p.duration
                if (durationMs <= 0) return
                p.seekTo((fraction.coerceIn(0f, 1f) * durationMs).toLong())
                val newProgress = (p.currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                progress = newProgress
            }

            Box(
                modifier = containerModifier,
                contentAlignment = Alignment.Center,
            ) {
                ProtectedView(
                    hideContent = hideMediaContent,
                    onShowClick = onShowContentClick,
                ) {
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .pointerInput(canInlinePlay, ringPaddingPx, ringStrokePx, player) {
                                if (!canInlinePlay) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val dx = down.position.x - center.x
                                    val dy = down.position.y - center.y
                                    val dist = hypot(dx, dy)
                                    val outer = min(size.width, size.height) / 2f
                                    val inner = max(0f, outer - (ringPaddingPx + ringStrokePx * 3f))
                                    val onRing = dist in inner..outer
                                    if (!onRing) {
                                        waitForUpOrCancellation()
                                        return@awaitEachGesture
                                    }
                                    scrubJustUntilMs = SystemClock.uptimeMillis() + 250L
                                    fun fractionFrom(pos: Offset): Float {
                                        val px = pos.x - center.x
                                        val py = pos.y - center.y
                                        val angle = atan2(py, px) + (PI / 2.0)
                                        var fraction = (angle / (2.0 * PI)).toFloat()
                                        if (fraction < 0f) fraction += 1f
                                        return fraction.coerceIn(0f, 1f)
                                    }
                                    scrubTo(fractionFrom(down.position))
                                    down.consume()
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                                        if (!change.pressed) break
                                        scrubTo(fractionFrom(change.position))
                                        change.consume()
                                    }
                                }
                            }
                            .then(
                                if (!isTalkbackActive) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (SystemClock.uptimeMillis() < scrubJustUntilMs) return@combinedClickable
                                            if (!canInlinePlay) {
                                                if (canRequestPrepare) {
                                                    if (!isPreparing) {
                                                        pendingAutoPlay = true
                                                        isExpanded = true
                                                        videoNoteInlinePlaybackState.eventSink(VideoNoteInlinePlaybackEvent.Prepare)
                                                    }
                                                    return@combinedClickable
                                                }
                                                onContentClick?.invoke()
                                                return@combinedClickable
                                            }
                                            if (isPlaying) {
                                                stopAndReset()
                                            } else {
                                                val p = player ?: return@combinedClickable
                                                isExpanded = true
                                                isPlaying = true
                                                p.volume = 1f
                                                p.playWhenReady = true
                                                p.play()
                                            }
                                        },
                                        onLongClick = onLongClick,
                                    ).onKeyboardContextMenuAction(onLongClick)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Ring
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .semantics { hideFromAccessibility() },
                        ) {
                            val stroke = Stroke(width = ringStrokePx, cap = StrokeCap.Round)
                            val inset = ringPaddingPx + ringStrokePx / 2f
                            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                            // background ring
                            drawArc(
                                color = ringBgColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = stroke,
                            )
                            // progress ring
                            drawArc(
                                color = ringProgressColor,
                                startAngle = -90f,
                                sweepAngle = 360f * if (canInlinePlay) progress else 0f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = stroke,
                            )
                        }

                        // Video circle
                        Box(
                            modifier = Modifier
                                .size(circleSize)
                                .clip(CircleShape)
                                .blurHashBackground(content.blurHash, alpha = 0.9f),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPlaying && canInlinePlay && player != null) {
                                AndroidView(
                                    modifier = Modifier.matchParentSize(),
                                    factory = { context ->
                                        PlayerView(context).apply {
                                            useController = false
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            this.player = player
                                        }
                                    },
                                    update = { view ->
                                        view.player = player
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
