/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.test

import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.CallAudioBackgroundStyles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryAppPreferencesStore(
    isDeveloperModeEnabled: Boolean = false,
    customElementCallBaseUrl: String? = null,
    hideInviteAvatars: Boolean? = null,
    timelineMediaPreviewValue: MediaPreviewValue? = null,
    theme: String? = null,
    logLevel: LogLevel = LogLevel.INFO,
    traceLockPacks: Set<TraceLogPack> = emptySet(),
) : AppPreferencesStore {
    private val isDeveloperModeEnabled = MutableStateFlow(isDeveloperModeEnabled)
    private val customElementCallBaseUrl = MutableStateFlow(customElementCallBaseUrl)
    private val theme = MutableStateFlow(theme)
    private val logLevel = MutableStateFlow(logLevel)
    private val tracingLogPacks = MutableStateFlow(traceLockPacks)
    private val hideInviteAvatars = MutableStateFlow(hideInviteAvatars)
    private val timelineMediaPreviewValue = MutableStateFlow(timelineMediaPreviewValue)
    private val contactRooms = MutableStateFlow(emptySet<String>())
    private val roomWallpapers = MutableStateFlow(emptyMap<String, String>())
    private val customizationAccentColorHex = MutableStateFlow<String?>(null)
    private val customizationUiScale = MutableStateFlow(1.0f)
    private val customizationMessageScale = MutableStateFlow(1.0f)
    private val customizationBubbleRadiusDp = MutableStateFlow(12)
    private val customizationBubbleWidthPercent = MutableStateFlow(78)
    private val customizationTimelineOverlayOpacityPercent = MutableStateFlow(22)
    private val customizationComposerBackgroundOpacityPercent = MutableStateFlow(92)
    private val customizationWallpaperBlurDp = MutableStateFlow(0)
    private val customizationShowEncryptionStatus = MutableStateFlow(false)
    private val customizationTopBarBackgroundColorHex = MutableStateFlow<String?>(null)
    private val customizationTopBarTextColorHex = MutableStateFlow<String?>(null)
    private val customizationComposerBackgroundColorHex = MutableStateFlow<String?>(null)
    private val customizationServiceBubbleColorHex = MutableStateFlow<String?>(null)
    private val customizationServiceTextColorHex = MutableStateFlow<String?>(null)
    private val customizationIncomingBubbleColorHex = MutableStateFlow<String?>(null)
    private val customizationOutgoingBubbleColorHex = MutableStateFlow<String?>(null)
    private val customizationIncomingBubbleGradientToColorHex = MutableStateFlow<String?>(null)
    private val customizationOutgoingBubbleGradientToColorHex = MutableStateFlow<String?>(null)
    private val customizationHomeBackgroundColorHex = MutableStateFlow<String?>(null)
    private val customizationHomeBackgroundImageUri = MutableStateFlow<String?>(null)
    private val customizationDefaultRoomWallpaperStyle = MutableStateFlow<String?>(null)
    private val customizationEnableChatAnimations = MutableStateFlow(true)
    private val customizationEnableBlurEffects = MutableStateFlow(true)
    private val customizationCallAudioBackgroundStyle = MutableStateFlow(CallAudioBackgroundStyles.GRADIENT)
    private val callPreferEarpieceByDefault = MutableStateFlow(true)
    private val callEnableProximitySensor = MutableStateFlow(true)
    private val customizationInitialTimelineItemCount = MutableStateFlow(5)

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        isDeveloperModeEnabled.value = enabled
    }

    override fun isDeveloperModeEnabledFlow(): Flow<Boolean> {
        return isDeveloperModeEnabled
    }

    override suspend fun setCustomElementCallBaseUrl(string: String?) {
        customElementCallBaseUrl.tryEmit(string)
    }

    override fun getCustomElementCallBaseUrlFlow(): Flow<String?> {
        return customElementCallBaseUrl
    }

    override suspend fun setTheme(theme: String) {
        this.theme.value = theme
    }

    override fun getThemeFlow(): Flow<String?> {
        return theme
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getHideInviteAvatarsFlow(): Flow<Boolean?> {
        return hideInviteAvatars
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?> {
        return timelineMediaPreviewValue
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setHideInviteAvatars(hide: Boolean?) {
        hideInviteAvatars.value = hide
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?) {
        timelineMediaPreviewValue.value = mediaPreviewValue
    }

    override suspend fun setTracingLogLevel(logLevel: LogLevel) {
        this.logLevel.value = logLevel
    }

    override fun getTracingLogLevelFlow(): Flow<LogLevel> {
        return logLevel
    }

    override suspend fun setTracingLogPacks(targets: Set<TraceLogPack>) {
        tracingLogPacks.value = targets
    }

    override fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>> {
        return tracingLogPacks
    }


    override suspend fun addContactRoom(roomId: String) {
        contactRooms.value = contactRooms.value + roomId
    }

    override suspend fun removeContactRoom(roomId: String) {
        contactRooms.value = contactRooms.value - roomId
    }

    override fun getContactRoomsFlow(): Flow<Set<String>> {
        return contactRooms
    }

    override suspend fun setRoomWallpaper(roomId: String, wallpaperStyle: String?) {
        roomWallpapers.value = if (wallpaperStyle.isNullOrBlank()) {
            roomWallpapers.value - roomId
        } else {
            roomWallpapers.value + (roomId to wallpaperStyle)
        }
    }

    override fun getRoomWallpaperFlow(roomId: String): Flow<String?> {
        return roomWallpapers.map { it[roomId] }
    }

    override suspend fun setCustomizationAccentColorHex(colorHex: String?) {
        customizationAccentColorHex.value = colorHex
    }

    override fun getCustomizationAccentColorHexFlow(): Flow<String?> {
        return customizationAccentColorHex
    }

    override suspend fun setCustomizationUiScale(scale: Float) {
        customizationUiScale.value = scale
    }

    override fun getCustomizationUiScaleFlow(): Flow<Float> {
        return customizationUiScale
    }

    override suspend fun setCustomizationMessageScale(scale: Float) {
        customizationMessageScale.value = scale
    }

    override fun getCustomizationMessageScaleFlow(): Flow<Float> {
        return customizationMessageScale
    }

    override suspend fun setCustomizationBubbleRadiusDp(radiusDp: Int) {
        customizationBubbleRadiusDp.value = radiusDp
    }

    override fun getCustomizationBubbleRadiusDpFlow(): Flow<Int> {
        return customizationBubbleRadiusDp
    }

    override suspend fun setCustomizationBubbleWidthPercent(widthPercent: Int) {
        customizationBubbleWidthPercent.value = widthPercent
    }

    override fun getCustomizationBubbleWidthPercentFlow(): Flow<Int> {
        return customizationBubbleWidthPercent
    }

    override suspend fun setCustomizationTimelineOverlayOpacityPercent(opacityPercent: Int) {
        customizationTimelineOverlayOpacityPercent.value = opacityPercent
    }

    override fun getCustomizationTimelineOverlayOpacityPercentFlow(): Flow<Int> {
        return customizationTimelineOverlayOpacityPercent
    }

    override suspend fun setCustomizationComposerBackgroundOpacityPercent(opacityPercent: Int) {
        customizationComposerBackgroundOpacityPercent.value = opacityPercent
    }

    override fun getCustomizationComposerBackgroundOpacityPercentFlow(): Flow<Int> {
        return customizationComposerBackgroundOpacityPercent
    }

    override suspend fun setCustomizationWallpaperBlurDp(blurDp: Int) {
        customizationWallpaperBlurDp.value = blurDp
    }

    override fun getCustomizationWallpaperBlurDpFlow(): Flow<Int> {
        return customizationWallpaperBlurDp
    }

    override suspend fun setCustomizationShowEncryptionStatus(show: Boolean) {
        customizationShowEncryptionStatus.value = show
    }

    override fun getCustomizationShowEncryptionStatusFlow(): Flow<Boolean> {
        return customizationShowEncryptionStatus
    }

    override suspend fun setCustomizationTopBarBackgroundColorHex(colorHex: String?) {
        customizationTopBarBackgroundColorHex.value = colorHex
    }

    override fun getCustomizationTopBarBackgroundColorHexFlow(): Flow<String?> {
        return customizationTopBarBackgroundColorHex
    }

    override suspend fun setCustomizationTopBarTextColorHex(colorHex: String?) {
        customizationTopBarTextColorHex.value = colorHex
    }

    override fun getCustomizationTopBarTextColorHexFlow(): Flow<String?> {
        return customizationTopBarTextColorHex
    }

    override suspend fun setCustomizationComposerBackgroundColorHex(colorHex: String?) {
        customizationComposerBackgroundColorHex.value = colorHex
    }

    override fun getCustomizationComposerBackgroundColorHexFlow(): Flow<String?> {
        return customizationComposerBackgroundColorHex
    }

    override suspend fun setCustomizationServiceBubbleColorHex(colorHex: String?) {
        customizationServiceBubbleColorHex.value = colorHex
    }

    override fun getCustomizationServiceBubbleColorHexFlow(): Flow<String?> {
        return customizationServiceBubbleColorHex
    }

    override suspend fun setCustomizationServiceTextColorHex(colorHex: String?) {
        customizationServiceTextColorHex.value = colorHex
    }

    override fun getCustomizationServiceTextColorHexFlow(): Flow<String?> {
        return customizationServiceTextColorHex
    }

    override suspend fun setCustomizationIncomingBubbleColorHex(colorHex: String?) {
        customizationIncomingBubbleColorHex.value = colorHex
    }

    override fun getCustomizationIncomingBubbleColorHexFlow(): Flow<String?> {
        return customizationIncomingBubbleColorHex
    }

    override suspend fun setCustomizationOutgoingBubbleColorHex(colorHex: String?) {
        customizationOutgoingBubbleColorHex.value = colorHex
    }

    override fun getCustomizationOutgoingBubbleColorHexFlow(): Flow<String?> {
        return customizationOutgoingBubbleColorHex
    }

    override suspend fun setCustomizationIncomingBubbleGradientToColorHex(colorHex: String?) {
        customizationIncomingBubbleGradientToColorHex.value = colorHex
    }

    override fun getCustomizationIncomingBubbleGradientToColorHexFlow(): Flow<String?> {
        return customizationIncomingBubbleGradientToColorHex
    }

    override suspend fun setCustomizationOutgoingBubbleGradientToColorHex(colorHex: String?) {
        customizationOutgoingBubbleGradientToColorHex.value = colorHex
    }

    override fun getCustomizationOutgoingBubbleGradientToColorHexFlow(): Flow<String?> {
        return customizationOutgoingBubbleGradientToColorHex
    }

    override suspend fun setCustomizationHomeBackgroundColorHex(colorHex: String?) {
        customizationHomeBackgroundColorHex.value = colorHex
    }

    override fun getCustomizationHomeBackgroundColorHexFlow(): Flow<String?> {
        return customizationHomeBackgroundColorHex
    }

    override suspend fun setCustomizationHomeBackgroundImageUri(uri: String?) {
        customizationHomeBackgroundImageUri.value = uri
    }

    override fun getCustomizationHomeBackgroundImageUriFlow(): Flow<String?> {
        return customizationHomeBackgroundImageUri
    }

    override suspend fun setCustomizationDefaultRoomWallpaperStyle(style: String?) {
        customizationDefaultRoomWallpaperStyle.value = style
    }

    override fun getCustomizationDefaultRoomWallpaperStyleFlow(): Flow<String?> {
        return customizationDefaultRoomWallpaperStyle
    }

    override suspend fun setCustomizationEnableChatAnimations(enabled: Boolean) {
        customizationEnableChatAnimations.value = enabled
    }

    override fun getCustomizationEnableChatAnimationsFlow(): Flow<Boolean> {
        return customizationEnableChatAnimations
    }

    override suspend fun setCustomizationEnableBlurEffects(enabled: Boolean) {
        customizationEnableBlurEffects.value = enabled
    }

    override fun getCustomizationEnableBlurEffectsFlow(): Flow<Boolean> {
        return customizationEnableBlurEffects
    }

    override suspend fun setCustomizationCallAudioBackgroundStyle(style: String) {
        customizationCallAudioBackgroundStyle.value = style
    }

    override fun getCustomizationCallAudioBackgroundStyleFlow(): Flow<String> {
        return customizationCallAudioBackgroundStyle
    }

    override suspend fun setCallPreferEarpieceByDefault(enabled: Boolean) {
        callPreferEarpieceByDefault.value = enabled
    }

    override fun getCallPreferEarpieceByDefaultFlow(): Flow<Boolean> {
        return callPreferEarpieceByDefault
    }

    override suspend fun setCallEnableProximitySensor(enabled: Boolean) {
        callEnableProximitySensor.value = enabled
    }

    override fun getCallEnableProximitySensorFlow(): Flow<Boolean> {
        return callEnableProximitySensor
    }

    override suspend fun setCustomizationInitialTimelineItemCount(count: Int) {
        customizationInitialTimelineItemCount.value = count
    }

    override fun getCustomizationInitialTimelineItemCountFlow(): Flow<Int> {
        return customizationInitialTimelineItemCount
    }

    override suspend fun reset() {
        // No op
    }
}
