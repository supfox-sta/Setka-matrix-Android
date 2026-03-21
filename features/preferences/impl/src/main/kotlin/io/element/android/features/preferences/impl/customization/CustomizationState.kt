/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.customization

data class CustomizationState(
    val themeMode: String?,
    val accentColorHex: String?,
    val uiScale: Float,
    val messageScale: Float,
    val bubbleRadiusDp: Int,
    val bubbleWidthPercent: Int,
    val timelineOverlayOpacityPercent: Int,
    val composerBackgroundOpacityPercent: Int,
    val wallpaperBlurDp: Int,
    val showEncryptionStatus: Boolean,
    val topBarBackgroundColorHex: String?,
    val topBarTextColorHex: String?,
    val composerBackgroundColorHex: String?,
    val serviceBubbleColorHex: String?,
    val serviceTextColorHex: String?,
    val incomingBubbleColorHex: String?,
    val outgoingBubbleColorHex: String?,
    val incomingBubbleGradientToColorHex: String?,
    val outgoingBubbleGradientToColorHex: String?,
    val homeBackgroundColorHex: String?,
    val homeBackgroundImageUri: String?,
    val defaultRoomWallpaperStyle: String?,
    val enableChatAnimations: Boolean,
    val enableBlurEffects: Boolean,
    val callAudioBackgroundStyle: String,
    val callPreferEarpieceByDefault: Boolean,
    val callProximitySensorEnabled: Boolean,
    val initialTimelineItemCount: Int,
    val eventSink: (CustomizationEvents) -> Unit,
)
