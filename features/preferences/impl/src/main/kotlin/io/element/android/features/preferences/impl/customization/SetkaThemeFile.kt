/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.customization

data class SetkaThemeFile(
    val version: Int = 5,
    val themeMode: String? = null,
    val accentColorHex: String? = null,
    val uiScale: Float = 1.0f,
    val messageScale: Float = 1.0f,
    val bubbleRadiusDp: Int = 12,
    val bubbleWidthPercent: Int = 78,
    val timelineOverlayOpacityPercent: Int = 22,
    val composerBackgroundOpacityPercent: Int = 92,
    val wallpaperBlurDp: Int = 0,
    val showEncryptionStatus: Boolean = false,
    val topBarBackgroundColorHex: String? = null,
    val topBarTextColorHex: String? = null,
    val composerBackgroundColorHex: String? = null,
    val serviceBubbleColorHex: String? = null,
    val serviceTextColorHex: String? = null,
    val incomingBubbleColorHex: String? = null,
    val outgoingBubbleColorHex: String? = null,
    val incomingBubbleGradientToColorHex: String? = null,
    val outgoingBubbleGradientToColorHex: String? = null,
    val homeBackgroundColorHex: String? = null,
    val homeBackgroundImageUri: String? = null,
    val defaultRoomWallpaperStyle: String? = null,
    val enableChatAnimations: Boolean = true,
    val enableBlurEffects: Boolean = true,
    val initialTimelineItemCount: Int = 12,
)
