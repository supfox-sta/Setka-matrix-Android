/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.pip.PictureInPictureEvents
import io.element.android.features.call.impl.pip.PictureInPictureState
import io.element.android.features.call.impl.pip.aPictureInPictureState
import io.element.android.features.call.impl.utils.InvalidAudioDeviceReason
import io.element.android.features.call.impl.utils.WebViewAudioManager
import io.element.android.features.call.impl.utils.WebViewPipController
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

typealias RequestPermissionCallback = (Array<String>) -> Unit

interface CallScreenNavigator {
    fun close()
}

@Composable
internal fun CallScreenView(
    state: CallScreenState,
    pipState: PictureInPictureState,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    requestPermissions: (Array<String>, RequestPermissionCallback) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun handleBack() {
        if (pipState.supportPip) {
            pipState.eventSink.invoke(PictureInPictureEvents.EnterPictureInPicture)
        } else {
            state.eventSink(CallScreenEvents.Hangup)
        }
    }

    Scaffold(
        modifier = modifier,
    ) { padding ->
        BackHandler {
            handleBack()
        }
        if (state.webViewError != null) {
            ErrorDialog(
                content = buildString {
                    append(stringResource(CommonStrings.error_unknown))
                    state.webViewError.takeIf { it.isNotEmpty() }?.let { append("\n\n").append(it) }
                },
                onSubmit = { state.eventSink(CallScreenEvents.Hangup) },
            )
        } else {
            var webViewAudioManager by remember { mutableStateOf<WebViewAudioManager?>(null) }
            val coroutineScope = rememberCoroutineScope()

            var invalidAudioDeviceReason by remember { mutableStateOf<InvalidAudioDeviceReason?>(null) }
            invalidAudioDeviceReason?.let {
                InvalidAudioDeviceDialog(invalidAudioDeviceReason = it) {
                    invalidAudioDeviceReason = null
                }
            }

            var webView by remember { mutableStateOf<WebView?>(null) }
            var messageChannelsInitialized by remember { mutableStateOf(false) }
            var isSpeakerMode by remember { mutableStateOf(false) }
            var rawHasAnyVideo by remember { mutableStateOf(false) }
            var stableHasAnyVideo by remember { mutableStateOf(false) }
            var connectedAtMillis by remember { mutableStateOf(0L) }
            val nowMillis by produceState(initialValue = 0L, key1 = state.isCallActive, key2 = connectedAtMillis) {
                if (state.callConnectionState != CallConnectionState.Connected || connectedAtMillis <= 0L) {
                    value = 0L
                    return@produceState
                }
                while (true) {
                    value = System.currentTimeMillis()
                    delay(1_000)
                }
            }

            LaunchedEffect(state.callConnectionState) {
                if (state.callConnectionState == CallConnectionState.Connected && connectedAtMillis == 0L) {
                    connectedAtMillis = System.currentTimeMillis()
                } else if (state.callConnectionState != CallConnectionState.Connected) {
                    connectedAtMillis = 0L
                    rawHasAnyVideo = false
                    stableHasAnyVideo = false
                }
            }

            LaunchedEffect(rawHasAnyVideo) {
                if (rawHasAnyVideo) {
                    stableHasAnyVideo = true
                } else {
                    // Avoid fast audio/video UI bouncing on transient track updates.
                    delay(700)
                    if (!rawHasAnyVideo) {
                        stableHasAnyVideo = false
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize(),
            ) {
                CallWebView(
                    modifier = Modifier.fillMaxSize(),
                    url = state.urlState,
                    userAgent = state.userAgent,
                    onPermissionsRequest = { request ->
                        val androidPermissions = mapWebkitPermissions(request.resources)
                        val callback: RequestPermissionCallback = { request.grant(it) }
                        requestPermissions(androidPermissions.toTypedArray(), callback)
                    },
                    onConsoleMessage = onConsoleMessage,
                    onCreateWebView = { createdWebView ->
                        webView = createdWebView
                        createdWebView.addBackHandler(onBackPressed = ::handleBack)
                        var interceptorRef: WebViewWidgetMessageInterceptor? = null
                        val interceptor = WebViewWidgetMessageInterceptor(
                            webView = createdWebView,
                            onUrlLoaded = { url ->
                                if (!messageChannelsInitialized) {
                                    interceptorRef?.let {
                                        state.eventSink(CallScreenEvents.SetupMessageChannels(it))
                                    }
                                    messageChannelsInitialized = true
                                }
                                createdWebView.evaluateJavascript("controls.onBackButtonPressed = () => { backHandler.onBackPressed() }", null)
                                createdWebView.applySetkaCallCustomization(state)
                                if (webViewAudioManager?.isInCallMode?.get() == false) {
                                    Timber.d("URL $url is loaded, starting in-call audio mode")
                                    webViewAudioManager?.onCallStarted()
                                } else {
                                    Timber.d("Can't start in-call audio mode since the app is already in it.")
                                }
                            },
                            onError = { state.eventSink(CallScreenEvents.OnWebViewError(it)) },
                        )
                        webViewAudioManager = WebViewAudioManager(
                            webView = createdWebView,
                            coroutineScope = coroutineScope,
                            preferEarpieceOnStart = state.isAudioOnly && state.callPreferEarpieceByDefault,
                            proximitySensorEnabled = state.callProximitySensorEnabled,
                            onInvalidAudioDeviceAdded = { invalidAudioDeviceReason = it },
                            onSpeakerModeChanged = { enabled ->
                                coroutineScope.launch {
                                    isSpeakerMode = enabled
                                }
                            },
                            onRemoteVideoStateChanged = { enabled ->
                                coroutineScope.launch {
                                    rawHasAnyVideo = enabled
                                }
                            },
                        )
                        interceptorRef = interceptor
                        val pipController = WebViewPipController(createdWebView)
                        pipState.eventSink(PictureInPictureEvents.SetPipController(pipController))
                    },
                    onDestroyWebView = { destroyedWebView ->
                        if (webView === destroyedWebView) {
                            webView = null
                        }
                        messageChannelsInitialized = false
                        isSpeakerMode = false
                        rawHasAnyVideo = false
                        stableHasAnyVideo = false
                        webViewAudioManager?.onCallStopped()
                    }
                )

                val showNativeOverlay = state.isCallActive && state.callConnectionState != CallConnectionState.Ended
                val audioMode = state.isAudioOnly && !state.isVideoEnabled && !stableHasAnyVideo
                if (showNativeOverlay) {
                    val onToggleMicrophone: () -> Unit = {
                        state.eventSink(CallScreenEvents.SetMicrophoneEnabled(!state.isMicrophoneEnabled))
                    }
                    val onToggleVideo: () -> Unit = {
                        state.eventSink(CallScreenEvents.SetVideoEnabled(!state.isVideoEnabled))
                    }
                    val onJoinCall: () -> Unit = {
                        state.eventSink(CallScreenEvents.JoinCall)
                    }
                    val onToggleAudioRoute: () -> Unit = {
                        isSpeakerMode = webViewAudioManager?.toggleSpeakerMode() == true
                    }
                    val onHangup: () -> Unit = {
                        state.eventSink(CallScreenEvents.Hangup)
                    }
                    val callStatusText = state.callStatus(nowMillis = nowMillis, connectedAtMillis = connectedAtMillis)

                    if (audioMode) {
                        AudioOnlyOverlay(
                            state = state,
                            isMicEnabled = state.isMicrophoneEnabled,
                            isVideoEnabled = state.isVideoEnabled,
                            isSpeakerMode = isSpeakerMode,
                            onToggleMicrophone = onToggleMicrophone,
                            onToggleVideo = onToggleVideo,
                            onToggleAudioRoute = onToggleAudioRoute,
                            showJoinCallAction = state.showJoinCallAction,
                            onJoinCall = onJoinCall,
                            onHangup = onHangup,
                            callStatusText = callStatusText,
                            backgroundImageUrl = state.callAudioBackgroundImageUrl,
                        )
                    } else {
                        VideoCallOverlay(
                            state = state,
                            isMicEnabled = state.isMicrophoneEnabled,
                            isVideoEnabled = state.isVideoEnabled,
                            isSpeakerMode = isSpeakerMode,
                            onToggleMicrophone = onToggleMicrophone,
                            onToggleVideo = onToggleVideo,
                            onToggleAudioRoute = onToggleAudioRoute,
                            showJoinCallAction = state.showJoinCallAction,
                            onJoinCall = onJoinCall,
                            onHangup = onHangup,
                            callStatusText = callStatusText,
                        )
                    }
                }
            }
            when (state.urlState) {
                AsyncData.Uninitialized,
                is AsyncData.Loading ->
                    ProgressDialog(text = stringResource(id = CommonStrings.common_please_wait))
                is AsyncData.Failure -> {
                    Timber.e(state.urlState.error, "WebView failed to load URL: ${state.urlState.error.message}")
                    ErrorDialog(
                        content = state.urlState.error.message.orEmpty(),
                        onSubmit = { state.eventSink(CallScreenEvents.Hangup) },
                    )
                }
                is AsyncData.Success -> Unit
            }
        }
    }
}

@Composable
private fun InvalidAudioDeviceDialog(
    invalidAudioDeviceReason: InvalidAudioDeviceReason,
    onDismiss: () -> Unit,
) {
    ErrorDialog(
        content = when (invalidAudioDeviceReason) {
            InvalidAudioDeviceReason.BT_AUDIO_DEVICE_DISABLED -> {
                stringResource(R.string.call_invalid_audio_device_bluetooth_devices_disabled)
            }
        },
        onSubmit = onDismiss,
    )
}

@Composable
private fun CallWebView(
    url: AsyncData<String>,
    userAgent: String,
    onPermissionsRequest: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    onCreateWebView: (WebView) -> Unit,
    onDestroyWebView: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("WebView - can't be previewed")
        }
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    onCreateWebView(this)
                    setup(
                        userAgent = userAgent,
                        onPermissionsRequested = onPermissionsRequest,
                        onConsoleMessage = onConsoleMessage,
                    )
                }
            },
            update = { webView ->
                if (url is AsyncData.Success && webView.url != url.data) {
                    webView.loadUrl(url.data)
                }
            },
            onRelease = { webView ->
                onDestroyWebView(webView)
                webView.destroy()
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.setup(
    userAgent: String,
    onPermissionsRequested: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
) {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    with(settings) {
        javaScriptEnabled = true
        allowContentAccess = true
        allowFileAccess = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadsImagesAutomatically = true
        userAgentString = userAgent
        cacheMode = WebSettings.LOAD_DEFAULT
        setSupportMultipleWindows(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }

    overScrollMode = View.OVER_SCROLL_NEVER
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
    }

    webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            onPermissionsRequested(request)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            onConsoleMessage(consoleMessage)
            return true
        }
    }
}

private fun WebView.addBackHandler(onBackPressed: () -> Unit) {
    addJavascriptInterface(
        object {
            @Suppress("unused")
            @JavascriptInterface
            fun onBackPressed() = onBackPressed()
        },
        "backHandler"
    )
}

private fun WebView.applySetkaCallCustomization(state: CallScreenState) {
    fun String.jsQuoted(): String {
        return replace("\\", "\\\\").replace("'", "\\'")
    }

    val accent = state.callAccentColorHex?.takeIf { it.isNotBlank() }?.jsQuoted().orEmpty()
    val topBarBg = state.callTopBarBackgroundColorHex?.takeIf { it.isNotBlank() }?.jsQuoted().orEmpty()
    val topBarText = state.callTopBarTextColorHex?.takeIf { it.isNotBlank() }?.jsQuoted().orEmpty()
    val audioBgImage = state.callAudioBackgroundImageUrl?.takeIf { it.isNotBlank() }?.jsQuoted().orEmpty()
    val headerBlur = if (state.callBlurEnabled) "10px" else "0px"
    val footerBlur = if (state.callBlurEnabled) "12px" else "0px"
    val transitionMs = if (state.callAnimationsEnabled) "150ms" else "0ms"

    evaluateJavascript(
        """
        (function () {
            const root = document.documentElement;
            if (!root) return;
            root.style.setProperty('--setka-call-header-blur', '${headerBlur}');
            root.style.setProperty('--setka-call-footer-blur', '${footerBlur}');
            root.style.setProperty('--setka-call-transition-duration', '${transitionMs}');
            if ('${accent}') root.style.setProperty('--setka-call-accent', '${accent}');
            if ('${topBarBg}') root.style.setProperty('--setka-call-topbar-bg', '${topBarBg}');
            if ('${topBarText}') root.style.setProperty('--setka-call-topbar-text', '${topBarText}');
            if ('${audioBgImage}') {
                root.style.setProperty('--setka-call-audio-bg-image', "url('${audioBgImage}')");
            } else {
                root.style.removeProperty('--setka-call-audio-bg-image');
            }
            let styleTag = document.getElementById('setka-native-call-style');
            if (!styleTag) {
                styleTag = document.createElement('style');
                styleTag.id = 'setka-native-call-style';
                document.head.appendChild(styleTag);
            }
            styleTag.textContent = `
                [data-testid="lobby_joinCall"] {
                    display: none !important;
                }
            `;
            if (!window.__setkaNativeCallLobbyJoinObserverInstalled) {
                window.__setkaNativeCallLobbyJoinObserverInstalled = true;
                const hideLobbyJoin = () => {
                    document.querySelectorAll('[data-testid="lobby_joinCall"]').forEach((node) => {
                        node.style.display = 'none';
                    });
                };
                hideLobbyJoin();
                const observerTarget = document.body || document.documentElement;
                if (observerTarget) {
                    new MutationObserver(hideLobbyJoin).observe(observerTarget, {
                        childList: true,
                        subtree: true,
                    });
                }
            }

        })();
        """.trimIndent(),
        null,
    )
}

private fun WebView.setVideoEnabledFromNative(enabled: Boolean) {
    evaluateJavascript("window.controls?.setVideoEnabled?.($enabled);", null)
}

private fun WebView.showNativeAudioRoutePicker() {
    evaluateJavascript(
        """
        (function() {
            if (window.controls?.showNativeAudioDevicePicker) {
                window.controls.showNativeAudioDevicePicker();
                return;
            }
            if (window.controls?.showNativeOutputDevicePicker) {
                window.controls.showNativeOutputDevicePicker();
            }
        })();
        """.trimIndent(),
        null,
    )
}

private fun CallScreenState.callStatus(nowMillis: Long, connectedAtMillis: Long): String {
    return when (callConnectionState) {
        CallConnectionState.Calling -> "Calling..."
        CallConnectionState.Connecting -> "Connecting..."
        CallConnectionState.Connected -> {
            if (connectedAtMillis > 0L && nowMillis > 0L) {
                formatElapsedCallTime((nowMillis - connectedAtMillis).coerceAtLeast(0L))
            } else {
                "Connecting..."
            }
        }
        CallConnectionState.Ended -> "Ended"
    }
}

private fun formatElapsedCallTime(elapsedMs: Long): String {
    val elapsedSeconds = elapsedMs / 1000L
    val hours = elapsedSeconds / 3600L
    val minutes = (elapsedSeconds % 3600L) / 60L
    val seconds = elapsedSeconds % 60L
    return if (hours > 0L) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun String?.parseColorOrNull(): Color? {
    val hex = this?.trim().orEmpty()
    if (hex.isEmpty()) return null
    val normalized = if (hex.startsWith("#")) hex else "#$hex"
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}

@Composable
private fun AudioOnlyOverlay(
    state: CallScreenState,
    isMicEnabled: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerMode: Boolean,
    onToggleMicrophone: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleAudioRoute: () -> Unit,
    showJoinCallAction: Boolean,
    onJoinCall: () -> Unit,
    onHangup: () -> Unit,
    callStatusText: String,
    backgroundImageUrl: String?,
) {
    val peerDisplayName = state.callPeerName.orEmpty().ifBlank { state.callPeerId.orEmpty() }.ifBlank { "Unknown" }
    val peerStableId = state.callPeerId.orEmpty().ifBlank { "unknown-peer" }
    val accentColor = state.callAccentColorHex.parseColorOrNull() ?: ElementTheme.colors.iconAccentTertiary
    val gradient = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.22f),
            ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.88f),
            ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.98f),
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault)
            .background(gradient)
    ) {
        if (!backgroundImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = backgroundImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.34f,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Avatar(
                avatarData = AvatarData(
                    id = peerStableId,
                    name = peerDisplayName,
                    url = state.callPeerAvatarUrl,
                    size = AvatarSize.IncomingCall,
                ),
                avatarType = AvatarType.User,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = peerDisplayName,
                style = ElementTheme.typography.fontHeadingMdBold,
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = callStatusText,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (showJoinCallAction) {
                Spacer(modifier = Modifier.height(18.dp))
                JoinCallButton(
                    onClick = onJoinCall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .clip(CircleShape)
                .background(ElementTheme.colors.bgSubtlePrimary.copy(alpha = 0.78f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallActionButton(
                icon = if (isMicEnabled) CompoundIcons.MicOnSolid() else CompoundIcons.MicOffSolid(),
                onClick = onToggleMicrophone,
                containerColor = if (isMicEnabled) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgCriticalPrimary,
                contentDescription = if (isMicEnabled) "Mute microphone" else "Unmute microphone",
            )
            CallActionButton(
                icon = if (isVideoEnabled) CompoundIcons.VideoCallSolid() else CompoundIcons.VideoCallOffSolid(),
                onClick = onToggleVideo,
                containerColor = if (isVideoEnabled) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgSubtleSecondary,
                contentDescription = if (isVideoEnabled) "Disable camera" else "Enable camera",
            )
            CallActionButton(
                icon = if (isSpeakerMode) CompoundIcons.VolumeOnSolid() else CompoundIcons.Earpiece(),
                onClick = onToggleAudioRoute,
                containerColor = ElementTheme.colors.bgSubtleSecondary,
                contentDescription = if (isSpeakerMode) "Loud speaker" else "Earpiece",
            )
            CallActionButton(
                icon = CompoundIcons.EndCall(),
                onClick = onHangup,
                containerColor = ElementTheme.colors.bgCriticalPrimary,
                contentDescription = "Hang up",
            )
        }
    }
}

@Composable
private fun VideoCallOverlay(
    state: CallScreenState,
    isMicEnabled: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerMode: Boolean,
    onToggleMicrophone: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleAudioRoute: () -> Unit,
    showJoinCallAction: Boolean,
    onJoinCall: () -> Unit,
    onHangup: () -> Unit,
    callStatusText: String,
) {
    val peerDisplayName = state.callPeerName.orEmpty().ifBlank { state.callPeerId.orEmpty() }.ifBlank { "Unknown" }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.56f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f),
                            Color.Black.copy(alpha = 0.62f),
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ElementTheme.colors.bgSubtlePrimary.copy(alpha = 0.58f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = peerDisplayName,
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Text(
                    text = callStatusText,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (showJoinCallAction) {
                    JoinCallButton(
                        onClick = onJoinCall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ElementTheme.colors.bgSubtlePrimary.copy(alpha = 0.78f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallActionButton(
                        icon = if (isMicEnabled) CompoundIcons.MicOnSolid() else CompoundIcons.MicOffSolid(),
                        onClick = onToggleMicrophone,
                        containerColor = if (isMicEnabled) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgCriticalPrimary,
                        contentDescription = if (isMicEnabled) "Mute microphone" else "Unmute microphone",
                    )
                    CallActionButton(
                        icon = if (isVideoEnabled) CompoundIcons.VideoCallSolid() else CompoundIcons.VideoCallOffSolid(),
                        onClick = onToggleVideo,
                        containerColor = if (isVideoEnabled) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgCriticalPrimary,
                        contentDescription = if (isVideoEnabled) "Disable camera" else "Enable camera",
                    )
                    CallActionButton(
                        icon = if (isSpeakerMode) CompoundIcons.VolumeOnSolid() else CompoundIcons.Earpiece(),
                        onClick = onToggleAudioRoute,
                        containerColor = ElementTheme.colors.bgSubtleSecondary,
                        contentDescription = if (isSpeakerMode) "Loud speaker" else "Earpiece",
                    )
                    CallActionButton(
                        icon = CompoundIcons.EndCall(),
                        onClick = onHangup,
                        containerColor = ElementTheme.colors.bgCriticalPrimary,
                        contentDescription = "Hang up",
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        text = stringResource(CommonStrings.action_join),
        onClick = onClick,
        modifier = modifier,
        size = ButtonSize.Large,
    )
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentDescription: String,
) {
    val contentColor = if (ElementTheme.isLightTheme) {
        ElementTheme.colors.iconPrimary
    } else {
        ElementTheme.colors.iconOnSolidPrimary
    }
    FilledIconButton(
        modifier = Modifier.size(50.dp),
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun CallScreenViewPreview(
    @PreviewParameter(CallScreenStateProvider::class) state: CallScreenState,
) = ElementPreview {
    CallScreenView(
        state = state,
        pipState = aPictureInPictureState(),
        requestPermissions = { _, _ -> },
        onConsoleMessage = {},
    )
}

@PreviewsDayNight
@Composable
internal fun InvalidAudioDeviceDialogPreview() = ElementPreview {
    InvalidAudioDeviceDialog(invalidAudioDeviceReason = InvalidAudioDeviceReason.BT_AUDIO_DEVICE_DISABLED) {}
}
