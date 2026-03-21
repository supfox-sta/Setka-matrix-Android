/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import kotlinx.coroutines.flow.Flow

interface AppPreferencesStore {
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    fun isDeveloperModeEnabledFlow(): Flow<Boolean>

    suspend fun setCustomElementCallBaseUrl(string: String?)
    fun getCustomElementCallBaseUrlFlow(): Flow<String?>

    suspend fun setTheme(theme: String)
    fun getThemeFlow(): Flow<String?>

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    suspend fun setHideInviteAvatars(hide: Boolean?)
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    fun getHideInviteAvatarsFlow(): Flow<Boolean?>
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?)
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?>

    suspend fun setTracingLogLevel(logLevel: LogLevel)
    fun getTracingLogLevelFlow(): Flow<LogLevel>

    suspend fun setTracingLogPacks(targets: Set<TraceLogPack>)
    fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>>

    suspend fun addContactRoom(roomId: String)
    suspend fun removeContactRoom(roomId: String)
    fun getContactRoomsFlow(): Flow<Set<String>>

    suspend fun setRoomWallpaper(roomId: String, wallpaperStyle: String?)
    fun getRoomWallpaperFlow(roomId: String): Flow<String?>

    suspend fun setCustomizationAccentColorHex(colorHex: String?)
    fun getCustomizationAccentColorHexFlow(): Flow<String?>

    suspend fun setCustomizationUiScale(scale: Float)
    fun getCustomizationUiScaleFlow(): Flow<Float>

    suspend fun setCustomizationMessageScale(scale: Float)
    fun getCustomizationMessageScaleFlow(): Flow<Float>

    suspend fun setCustomizationBubbleRadiusDp(radiusDp: Int)
    fun getCustomizationBubbleRadiusDpFlow(): Flow<Int>

    suspend fun setCustomizationBubbleWidthPercent(widthPercent: Int)
    fun getCustomizationBubbleWidthPercentFlow(): Flow<Int>

    suspend fun setCustomizationTimelineOverlayOpacityPercent(opacityPercent: Int)
    fun getCustomizationTimelineOverlayOpacityPercentFlow(): Flow<Int>

    suspend fun setCustomizationComposerBackgroundOpacityPercent(opacityPercent: Int)
    fun getCustomizationComposerBackgroundOpacityPercentFlow(): Flow<Int>

    suspend fun setCustomizationWallpaperBlurDp(blurDp: Int)
    fun getCustomizationWallpaperBlurDpFlow(): Flow<Int>

    suspend fun setCustomizationShowEncryptionStatus(show: Boolean)
    fun getCustomizationShowEncryptionStatusFlow(): Flow<Boolean>

    suspend fun setCustomizationTopBarBackgroundColorHex(colorHex: String?)
    fun getCustomizationTopBarBackgroundColorHexFlow(): Flow<String?>

    suspend fun setCustomizationTopBarTextColorHex(colorHex: String?)
    fun getCustomizationTopBarTextColorHexFlow(): Flow<String?>

    suspend fun setCustomizationComposerBackgroundColorHex(colorHex: String?)
    fun getCustomizationComposerBackgroundColorHexFlow(): Flow<String?>

    suspend fun setCustomizationServiceBubbleColorHex(colorHex: String?)
    fun getCustomizationServiceBubbleColorHexFlow(): Flow<String?>

    suspend fun setCustomizationServiceTextColorHex(colorHex: String?)
    fun getCustomizationServiceTextColorHexFlow(): Flow<String?>

    suspend fun setCustomizationIncomingBubbleColorHex(colorHex: String?)
    fun getCustomizationIncomingBubbleColorHexFlow(): Flow<String?>

    suspend fun setCustomizationOutgoingBubbleColorHex(colorHex: String?)
    fun getCustomizationOutgoingBubbleColorHexFlow(): Flow<String?>

    suspend fun setCustomizationIncomingBubbleGradientToColorHex(colorHex: String?)
    fun getCustomizationIncomingBubbleGradientToColorHexFlow(): Flow<String?>

    suspend fun setCustomizationOutgoingBubbleGradientToColorHex(colorHex: String?)
    fun getCustomizationOutgoingBubbleGradientToColorHexFlow(): Flow<String?>

    suspend fun setCustomizationHomeBackgroundColorHex(colorHex: String?)
    fun getCustomizationHomeBackgroundColorHexFlow(): Flow<String?>

    suspend fun setCustomizationHomeBackgroundImageUri(uri: String?)
    fun getCustomizationHomeBackgroundImageUriFlow(): Flow<String?>

    suspend fun setCustomizationDefaultRoomWallpaperStyle(style: String?)
    fun getCustomizationDefaultRoomWallpaperStyleFlow(): Flow<String?>

    suspend fun setCustomizationEnableChatAnimations(enabled: Boolean)
    fun getCustomizationEnableChatAnimationsFlow(): Flow<Boolean>

    suspend fun setCustomizationEnableBlurEffects(enabled: Boolean)
    fun getCustomizationEnableBlurEffectsFlow(): Flow<Boolean>

    suspend fun setCustomizationCallAudioBackgroundStyle(style: String)
    fun getCustomizationCallAudioBackgroundStyleFlow(): Flow<String>

    suspend fun setCallPreferEarpieceByDefault(enabled: Boolean)
    fun getCallPreferEarpieceByDefaultFlow(): Flow<Boolean>

    suspend fun setCallEnableProximitySensor(enabled: Boolean)
    fun getCallEnableProximitySensorFlow(): Flow<Boolean>

    suspend fun setCustomizationInitialTimelineItemCount(count: Int)
    fun getCustomizationInitialTimelineItemCountFlow(): Flow<Int>

    suspend fun reset()
}
