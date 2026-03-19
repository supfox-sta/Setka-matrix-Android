/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.customization

sealed interface CustomizationEvents {
    data class SetThemeMode(val themeMode: String) : CustomizationEvents
    data class SetAccentColorHex(val colorHex: String?) : CustomizationEvents
    data class SetUiScale(val scale: Float) : CustomizationEvents
    data class SetMessageScale(val scale: Float) : CustomizationEvents
    data class SetBubbleRadius(val radiusDp: Int) : CustomizationEvents
    data class SetBubbleWidthPercent(val widthPercent: Int) : CustomizationEvents
    data class SetTimelineOverlayOpacityPercent(val opacityPercent: Int) : CustomizationEvents
    data class SetComposerBackgroundOpacityPercent(val opacityPercent: Int) : CustomizationEvents
    data class SetWallpaperBlurDp(val blurDp: Int) : CustomizationEvents
    data class SetShowEncryptionStatus(val show: Boolean) : CustomizationEvents
    data class SetTopBarBackgroundColorHex(val colorHex: String?) : CustomizationEvents
    data class SetTopBarTextColorHex(val colorHex: String?) : CustomizationEvents
    data class SetComposerBackgroundColorHex(val colorHex: String?) : CustomizationEvents
    data class SetServiceBubbleColorHex(val colorHex: String?) : CustomizationEvents
    data class SetServiceTextColorHex(val colorHex: String?) : CustomizationEvents
    data class SetIncomingBubbleColorHex(val colorHex: String?) : CustomizationEvents
    data class SetOutgoingBubbleColorHex(val colorHex: String?) : CustomizationEvents
    data class SetIncomingBubbleGradientToColorHex(val colorHex: String?) : CustomizationEvents
    data class SetOutgoingBubbleGradientToColorHex(val colorHex: String?) : CustomizationEvents
    data class SetHomeBackgroundColorHex(val colorHex: String?) : CustomizationEvents
    data class SetHomeBackgroundImageUri(val uri: String?) : CustomizationEvents
    data class SetDefaultRoomWallpaperStyle(val style: String?) : CustomizationEvents
    data class SetEnableChatAnimations(val enabled: Boolean) : CustomizationEvents
    data class SetEnableBlurEffects(val enabled: Boolean) : CustomizationEvents
    data class SetInitialTimelineItemCount(val count: Int) : CustomizationEvents
    data class ImportTheme(val theme: SetkaThemeFile) : CustomizationEvents
    data object ResetToDefault : CustomizationEvents
}
