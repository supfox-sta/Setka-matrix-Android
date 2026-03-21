/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import io.element.android.libraries.architecture.AsyncData

data class CallScreenState(
    val urlState: AsyncData<String>,
    val webViewError: String?,
    val userAgent: String,
    val isCallActive: Boolean,
    val isInWidgetMode: Boolean,
    val isAudioOnly: Boolean,
    val isMicrophoneEnabled: Boolean,
    val isVideoEnabled: Boolean,
    val showJoinCallAction: Boolean,
    val callConnectionState: CallConnectionState,
    val callPeerId: String?,
    val callPeerName: String?,
    val callPeerAvatarUrl: String?,
    val callAccentColorHex: String?,
    val callTopBarBackgroundColorHex: String?,
    val callTopBarTextColorHex: String?,
    val callBlurEnabled: Boolean,
    val callAnimationsEnabled: Boolean,
    val callAudioBackgroundImageUrl: String?,
    val callPreferEarpieceByDefault: Boolean,
    val callProximitySensorEnabled: Boolean,
    val eventSink: (CallScreenEvents) -> Unit,
)

enum class CallConnectionState {
    Calling,
    Connecting,
    Connected,
    Ended,
}
