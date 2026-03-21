/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.impl.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.CallAudioBackgroundStyles
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val developerModeKey = booleanPreferencesKey("developerMode")
private val customElementCallBaseUrlKey = stringPreferencesKey("elementCallBaseUrl")
private val themeKey = stringPreferencesKey("theme")
private val hideInviteAvatarsKey = booleanPreferencesKey("hideInviteAvatars")
private val timelineMediaPreviewValueKey = stringPreferencesKey("timelineMediaPreviewValue")
private val logLevelKey = stringPreferencesKey("logLevel")
private val traceLogPacksKey = stringPreferencesKey("traceLogPacks")
private val contactRoomsKey = stringPreferencesKey("contactRooms")
private val roomWallpapersKey = stringPreferencesKey("roomWallpapers")
private val customizationAccentColorHexKey = stringPreferencesKey("customizationAccentColorHex")
private val customizationUiScaleKey = floatPreferencesKey("customizationUiScale")
private val customizationMessageScaleKey = floatPreferencesKey("customizationMessageScale")
private val customizationBubbleRadiusDpKey = intPreferencesKey("customizationBubbleRadiusDp")
private val customizationBubbleWidthPercentKey = intPreferencesKey("customizationBubbleWidthPercent")
private val customizationTimelineOverlayOpacityPercentKey = intPreferencesKey("customizationTimelineOverlayOpacityPercent")
private val customizationComposerBackgroundOpacityPercentKey = intPreferencesKey("customizationComposerBackgroundOpacityPercent")
private val customizationWallpaperBlurDpKey = intPreferencesKey("customizationWallpaperBlurDp")
private val customizationShowEncryptionStatusKey = booleanPreferencesKey("customizationShowEncryptionStatus")
private val customizationTopBarBackgroundColorHexKey = stringPreferencesKey("customizationTopBarBackgroundColorHex")
private val customizationTopBarTextColorHexKey = stringPreferencesKey("customizationTopBarTextColorHex")
private val customizationComposerBackgroundColorHexKey = stringPreferencesKey("customizationComposerBackgroundColorHex")
private val customizationServiceBubbleColorHexKey = stringPreferencesKey("customizationServiceBubbleColorHex")
private val customizationServiceTextColorHexKey = stringPreferencesKey("customizationServiceTextColorHex")
private val customizationIncomingBubbleColorHexKey = stringPreferencesKey("customizationIncomingBubbleColorHex")
private val customizationOutgoingBubbleColorHexKey = stringPreferencesKey("customizationOutgoingBubbleColorHex")
private val customizationIncomingBubbleGradientToColorHexKey = stringPreferencesKey("customizationIncomingBubbleGradientToColorHex")
private val customizationOutgoingBubbleGradientToColorHexKey = stringPreferencesKey("customizationOutgoingBubbleGradientToColorHex")
private val customizationHomeBackgroundColorHexKey = stringPreferencesKey("customizationHomeBackgroundColorHex")
private val customizationHomeBackgroundImageUriKey = stringPreferencesKey("customizationHomeBackgroundImageUri")
private val customizationDefaultRoomWallpaperStyleKey = stringPreferencesKey("customizationDefaultRoomWallpaperStyle")
private val customizationEnableChatAnimationsKey = booleanPreferencesKey("customizationEnableChatAnimations")
private val customizationEnableBlurEffectsKey = booleanPreferencesKey("customizationEnableBlurEffects")
private val customizationCallAudioBackgroundStyleKey = stringPreferencesKey("customizationCallAudioBackgroundStyle")
private val callPreferEarpieceByDefaultKey = booleanPreferencesKey("callPreferEarpieceByDefault")
private val callEnableProximitySensorKey = booleanPreferencesKey("callEnableProximitySensor")
private val customizationInitialTimelineItemCountKey = intPreferencesKey("customizationInitialTimelineItemCount")

@ContributesBinding(AppScope::class)
class DefaultAppPreferencesStore(
    private val buildMeta: BuildMeta,
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) : AppPreferencesStore {
    companion object {
        private const val DEFAULT_UI_SCALE = 1.0f
        private const val MIN_UI_SCALE = 0.90f
        private const val MAX_UI_SCALE = 1.15f
        private const val DEFAULT_MESSAGE_SCALE = 1.0f
        private const val MIN_MESSAGE_SCALE = 0.85f
        private const val MAX_MESSAGE_SCALE = 1.40f
        private const val DEFAULT_BUBBLE_RADIUS_DP = 12
        private const val MIN_BUBBLE_RADIUS_DP = 4
        private const val MAX_BUBBLE_RADIUS_DP = 28
        private const val DEFAULT_BUBBLE_WIDTH_PERCENT = 78
        private const val MIN_BUBBLE_WIDTH_PERCENT = 60
        private const val MAX_BUBBLE_WIDTH_PERCENT = 95
        private const val DEFAULT_TIMELINE_OVERLAY_OPACITY_PERCENT = 22
        private const val MIN_TIMELINE_OVERLAY_OPACITY_PERCENT = 0
        private const val MAX_TIMELINE_OVERLAY_OPACITY_PERCENT = 60
        private const val DEFAULT_COMPOSER_BACKGROUND_OPACITY_PERCENT = 92
        private const val MIN_COMPOSER_BACKGROUND_OPACITY_PERCENT = 0
        private const val MAX_COMPOSER_BACKGROUND_OPACITY_PERCENT = 100
        private const val DEFAULT_WALLPAPER_BLUR_DP = 0
        private const val MIN_WALLPAPER_BLUR_DP = 0
        private const val MAX_WALLPAPER_BLUR_DP = 20
        private const val DEFAULT_ENABLE_CHAT_ANIMATIONS = true
        private const val DEFAULT_ENABLE_BLUR_EFFECTS = true
        private const val DEFAULT_CALL_AUDIO_BACKGROUND_STYLE = CallAudioBackgroundStyles.GRADIENT
        private const val DEFAULT_CALL_PREFER_EARPIECE = true
        private const val DEFAULT_CALL_ENABLE_PROXIMITY_SENSOR = true
        private const val DEFAULT_INITIAL_TIMELINE_ITEM_COUNT = 12
        private const val MIN_INITIAL_TIMELINE_ITEM_COUNT = 1
        private const val MAX_INITIAL_TIMELINE_ITEM_COUNT = 50
    }

    private val store = preferenceDataStoreFactory.create("elementx_preferences")

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[developerModeKey] = enabled
        }
    }

    override fun isDeveloperModeEnabledFlow(): Flow<Boolean> {
        return store.data.map { prefs ->
            // disabled by default on release and nightly, enabled by default on debug
            prefs[developerModeKey] ?: (buildMeta.buildType == BuildType.DEBUG)
        }
    }

    override suspend fun setCustomElementCallBaseUrl(string: String?) {
        store.edit { prefs ->
            if (string != null) {
                prefs[customElementCallBaseUrlKey] = string
            } else {
                prefs.remove(customElementCallBaseUrlKey)
            }
        }
    }

    override fun getCustomElementCallBaseUrlFlow(): Flow<String?> {
        return store.data.map { prefs ->
            prefs[customElementCallBaseUrlKey]
        }
    }

    override suspend fun setTheme(theme: String) {
        store.edit { prefs ->
            prefs[themeKey] = theme
        }
    }

    override fun getThemeFlow(): Flow<String?> {
        return store.data.map { prefs ->
            prefs[themeKey]
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getHideInviteAvatarsFlow(): Flow<Boolean?> {
        return store.data.map { prefs ->
            prefs[hideInviteAvatarsKey]
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setHideInviteAvatars(hide: Boolean?) {
        store.edit { prefs ->
            if (hide != null) {
                prefs[hideInviteAvatarsKey] = hide
            } else {
                prefs.remove(hideInviteAvatarsKey)
            }
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?) {
        store.edit { prefs ->
            if (mediaPreviewValue != null) {
                prefs[timelineMediaPreviewValueKey] = mediaPreviewValue.name
            } else {
                prefs.remove(timelineMediaPreviewValueKey)
            }
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?> {
        return store.data.map { prefs ->
            prefs[timelineMediaPreviewValueKey]?.let { MediaPreviewValue.valueOf(it) }
        }
    }

    override suspend fun setTracingLogLevel(logLevel: LogLevel) {
        store.edit { prefs ->
            prefs[logLevelKey] = logLevel.name
        }
    }

    override fun getTracingLogLevelFlow(): Flow<LogLevel> {
        return store.data.map { prefs ->
            prefs[logLevelKey]?.let { LogLevel.valueOf(it) } ?: buildMeta.defaultLogLevel()
        }
    }

    override suspend fun setTracingLogPacks(targets: Set<TraceLogPack>) {
        val value = targets.joinToString(",") { it.key }
        store.edit { prefs ->
            prefs[traceLogPacksKey] = value
        }
    }

    override fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>> {
        return store.data.map { prefs ->
            prefs[traceLogPacksKey]
                ?.split(",")
                ?.mapNotNull { value -> TraceLogPack.entries.find { it.key == value } }
                ?.toSet()
                ?: emptySet()
        }
    }

    override suspend fun addContactRoom(roomId: String) {
        store.edit { prefs ->
            val contactRooms = decodeStringSet(prefs[contactRoomsKey]) + roomId
            prefs[contactRoomsKey] = encodeStringSet(contactRooms)
        }
    }

    override suspend fun removeContactRoom(roomId: String) {
        store.edit { prefs ->
            val contactRooms = decodeStringSet(prefs[contactRoomsKey]) - roomId
            if (contactRooms.isEmpty()) {
                prefs.remove(contactRoomsKey)
            } else {
                prefs[contactRoomsKey] = encodeStringSet(contactRooms)
            }
        }
    }

    override fun getContactRoomsFlow(): Flow<Set<String>> {
        return store.data.map { prefs ->
            decodeStringSet(prefs[contactRoomsKey])
        }
    }

    override suspend fun setRoomWallpaper(roomId: String, wallpaperStyle: String?) {
        store.edit { prefs ->
            val roomWallpapers = decodeStringMap(prefs[roomWallpapersKey]).toMutableMap()
            if (wallpaperStyle.isNullOrBlank()) {
                roomWallpapers.remove(roomId)
            } else {
                roomWallpapers[roomId] = wallpaperStyle
            }
            if (roomWallpapers.isEmpty()) {
                prefs.remove(roomWallpapersKey)
            } else {
                prefs[roomWallpapersKey] = encodeStringMap(roomWallpapers)
            }
        }
    }

    override fun getRoomWallpaperFlow(roomId: String): Flow<String?> {
        return store.data.map { prefs ->
            decodeStringMap(prefs[roomWallpapersKey])[roomId]
        }
    }

    override suspend fun setCustomizationAccentColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationAccentColorHexKey)
            } else {
                prefs[customizationAccentColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationAccentColorHexFlow(): Flow<String?> {
        return store.data.map { prefs ->
            prefs[customizationAccentColorHexKey]
        }
    }

    override suspend fun setCustomizationUiScale(scale: Float) {
        store.edit { prefs ->
            prefs[customizationUiScaleKey] = scale.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        }
    }

    override fun getCustomizationUiScaleFlow(): Flow<Float> {
        return store.data.map { prefs ->
            (prefs[customizationUiScaleKey] ?: DEFAULT_UI_SCALE)
                .coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        }
    }

    override suspend fun setCustomizationMessageScale(scale: Float) {
        store.edit { prefs ->
            prefs[customizationMessageScaleKey] = scale.coerceIn(MIN_MESSAGE_SCALE, MAX_MESSAGE_SCALE)
        }
    }

    override fun getCustomizationMessageScaleFlow(): Flow<Float> {
        return store.data.map { prefs ->
            (prefs[customizationMessageScaleKey] ?: DEFAULT_MESSAGE_SCALE)
                .coerceIn(MIN_MESSAGE_SCALE, MAX_MESSAGE_SCALE)
        }
    }

    override suspend fun setCustomizationBubbleRadiusDp(radiusDp: Int) {
        store.edit { prefs ->
            prefs[customizationBubbleRadiusDpKey] = radiusDp.coerceIn(MIN_BUBBLE_RADIUS_DP, MAX_BUBBLE_RADIUS_DP)
        }
    }

    override fun getCustomizationBubbleRadiusDpFlow(): Flow<Int> {
        return store.data.map { prefs ->
            (prefs[customizationBubbleRadiusDpKey] ?: DEFAULT_BUBBLE_RADIUS_DP)
                .coerceIn(MIN_BUBBLE_RADIUS_DP, MAX_BUBBLE_RADIUS_DP)
        }
    }

    override suspend fun setCustomizationBubbleWidthPercent(widthPercent: Int) {
        store.edit { prefs ->
            prefs[customizationBubbleWidthPercentKey] = widthPercent.coerceIn(MIN_BUBBLE_WIDTH_PERCENT, MAX_BUBBLE_WIDTH_PERCENT)
        }
    }

    override fun getCustomizationBubbleWidthPercentFlow(): Flow<Int> {
        return store.data.map { prefs ->
            (prefs[customizationBubbleWidthPercentKey] ?: DEFAULT_BUBBLE_WIDTH_PERCENT)
                .coerceIn(MIN_BUBBLE_WIDTH_PERCENT, MAX_BUBBLE_WIDTH_PERCENT)
        }
    }

    override suspend fun setCustomizationTimelineOverlayOpacityPercent(opacityPercent: Int) {
        store.edit { prefs ->
            prefs[customizationTimelineOverlayOpacityPercentKey] = opacityPercent.coerceIn(
                MIN_TIMELINE_OVERLAY_OPACITY_PERCENT,
                MAX_TIMELINE_OVERLAY_OPACITY_PERCENT,
            )
        }
    }

    override fun getCustomizationTimelineOverlayOpacityPercentFlow(): Flow<Int> {
        return store.data.map { prefs ->
            (prefs[customizationTimelineOverlayOpacityPercentKey] ?: DEFAULT_TIMELINE_OVERLAY_OPACITY_PERCENT)
                .coerceIn(MIN_TIMELINE_OVERLAY_OPACITY_PERCENT, MAX_TIMELINE_OVERLAY_OPACITY_PERCENT)
        }
    }

    override suspend fun setCustomizationComposerBackgroundOpacityPercent(opacityPercent: Int) {
        store.edit { prefs ->
            prefs[customizationComposerBackgroundOpacityPercentKey] = opacityPercent.coerceIn(
                MIN_COMPOSER_BACKGROUND_OPACITY_PERCENT,
                MAX_COMPOSER_BACKGROUND_OPACITY_PERCENT,
            )
        }
    }

    override fun getCustomizationComposerBackgroundOpacityPercentFlow(): Flow<Int> {
        return store.data.map { prefs ->
            (prefs[customizationComposerBackgroundOpacityPercentKey] ?: DEFAULT_COMPOSER_BACKGROUND_OPACITY_PERCENT)
                .coerceIn(MIN_COMPOSER_BACKGROUND_OPACITY_PERCENT, MAX_COMPOSER_BACKGROUND_OPACITY_PERCENT)
        }
    }

    override suspend fun setCustomizationWallpaperBlurDp(blurDp: Int) {
        store.edit { prefs ->
            prefs[customizationWallpaperBlurDpKey] = blurDp.coerceIn(MIN_WALLPAPER_BLUR_DP, MAX_WALLPAPER_BLUR_DP)
        }
    }

    override fun getCustomizationWallpaperBlurDpFlow(): Flow<Int> {
        return store.data.map { prefs ->
            (prefs[customizationWallpaperBlurDpKey] ?: DEFAULT_WALLPAPER_BLUR_DP)
                .coerceIn(MIN_WALLPAPER_BLUR_DP, MAX_WALLPAPER_BLUR_DP)
        }
    }

    override suspend fun setCustomizationShowEncryptionStatus(show: Boolean) {
        store.edit { prefs ->
            prefs[customizationShowEncryptionStatusKey] = show
        }
    }

    override fun getCustomizationShowEncryptionStatusFlow(): Flow<Boolean> {
        return store.data.map { prefs ->
            prefs[customizationShowEncryptionStatusKey] ?: false
        }
    }

    override suspend fun setCustomizationTopBarBackgroundColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationTopBarBackgroundColorHexKey)
            } else {
                prefs[customizationTopBarBackgroundColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationTopBarBackgroundColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationTopBarBackgroundColorHexKey] }
    }

    override suspend fun setCustomizationTopBarTextColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationTopBarTextColorHexKey)
            } else {
                prefs[customizationTopBarTextColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationTopBarTextColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationTopBarTextColorHexKey] }
    }

    override suspend fun setCustomizationComposerBackgroundColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationComposerBackgroundColorHexKey)
            } else {
                prefs[customizationComposerBackgroundColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationComposerBackgroundColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationComposerBackgroundColorHexKey] }
    }

    override suspend fun setCustomizationServiceBubbleColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationServiceBubbleColorHexKey)
            } else {
                prefs[customizationServiceBubbleColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationServiceBubbleColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationServiceBubbleColorHexKey] }
    }

    override suspend fun setCustomizationServiceTextColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationServiceTextColorHexKey)
            } else {
                prefs[customizationServiceTextColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationServiceTextColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationServiceTextColorHexKey] }
    }

    override suspend fun setCustomizationIncomingBubbleColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationIncomingBubbleColorHexKey)
            } else {
                prefs[customizationIncomingBubbleColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationIncomingBubbleColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationIncomingBubbleColorHexKey] }
    }

    override suspend fun setCustomizationOutgoingBubbleColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationOutgoingBubbleColorHexKey)
            } else {
                prefs[customizationOutgoingBubbleColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationOutgoingBubbleColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationOutgoingBubbleColorHexKey] }
    }

    override suspend fun setCustomizationIncomingBubbleGradientToColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationIncomingBubbleGradientToColorHexKey)
            } else {
                prefs[customizationIncomingBubbleGradientToColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationIncomingBubbleGradientToColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationIncomingBubbleGradientToColorHexKey] }
    }

    override suspend fun setCustomizationOutgoingBubbleGradientToColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationOutgoingBubbleGradientToColorHexKey)
            } else {
                prefs[customizationOutgoingBubbleGradientToColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationOutgoingBubbleGradientToColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationOutgoingBubbleGradientToColorHexKey] }
    }

    override suspend fun setCustomizationHomeBackgroundColorHex(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex.isNullOrBlank()) {
                prefs.remove(customizationHomeBackgroundColorHexKey)
            } else {
                prefs[customizationHomeBackgroundColorHexKey] = colorHex.trim()
            }
        }
    }

    override fun getCustomizationHomeBackgroundColorHexFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationHomeBackgroundColorHexKey] }
    }

    override suspend fun setCustomizationHomeBackgroundImageUri(uri: String?) {
        store.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(customizationHomeBackgroundImageUriKey)
            } else {
                prefs[customizationHomeBackgroundImageUriKey] = uri
            }
        }
    }

    override fun getCustomizationHomeBackgroundImageUriFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationHomeBackgroundImageUriKey] }
    }

    override suspend fun setCustomizationDefaultRoomWallpaperStyle(style: String?) {
        store.edit { prefs ->
            if (style.isNullOrBlank()) {
                prefs.remove(customizationDefaultRoomWallpaperStyleKey)
            } else {
                prefs[customizationDefaultRoomWallpaperStyleKey] = style.trim()
            }
        }
    }

    override fun getCustomizationDefaultRoomWallpaperStyleFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[customizationDefaultRoomWallpaperStyleKey] }
    }

    override suspend fun setCustomizationEnableChatAnimations(enabled: Boolean) {
        store.edit { prefs ->
            prefs[customizationEnableChatAnimationsKey] = enabled
        }
    }

    override fun getCustomizationEnableChatAnimationsFlow(): Flow<Boolean> {
        return store.data.map { prefs ->
            prefs[customizationEnableChatAnimationsKey] ?: DEFAULT_ENABLE_CHAT_ANIMATIONS
        }
    }

    override suspend fun setCustomizationEnableBlurEffects(enabled: Boolean) {
        store.edit { prefs ->
            prefs[customizationEnableBlurEffectsKey] = enabled
        }
    }

    override fun getCustomizationEnableBlurEffectsFlow(): Flow<Boolean> {
        return store.data.map { prefs ->
            prefs[customizationEnableBlurEffectsKey] ?: DEFAULT_ENABLE_BLUR_EFFECTS
        }
    }

    override suspend fun setCustomizationCallAudioBackgroundStyle(style: String) {
        store.edit { prefs ->
            prefs[customizationCallAudioBackgroundStyleKey] = style.trim().ifBlank {
                DEFAULT_CALL_AUDIO_BACKGROUND_STYLE
            }
        }
    }

    override fun getCustomizationCallAudioBackgroundStyleFlow(): Flow<String> {
        return store.data.map { prefs ->
            prefs[customizationCallAudioBackgroundStyleKey] ?: DEFAULT_CALL_AUDIO_BACKGROUND_STYLE
        }
    }

    override suspend fun setCallPreferEarpieceByDefault(enabled: Boolean) {
        store.edit { prefs ->
            prefs[callPreferEarpieceByDefaultKey] = enabled
        }
    }

    override fun getCallPreferEarpieceByDefaultFlow(): Flow<Boolean> {
        return store.data.map { prefs ->
            prefs[callPreferEarpieceByDefaultKey] ?: DEFAULT_CALL_PREFER_EARPIECE
        }
    }

    override suspend fun setCallEnableProximitySensor(enabled: Boolean) {
        store.edit { prefs ->
            prefs[callEnableProximitySensorKey] = enabled
        }
    }

    override fun getCallEnableProximitySensorFlow(): Flow<Boolean> {
        return store.data.map { prefs ->
            prefs[callEnableProximitySensorKey] ?: DEFAULT_CALL_ENABLE_PROXIMITY_SENSOR
        }
    }

    override suspend fun setCustomizationInitialTimelineItemCount(count: Int) {
        store.edit { prefs ->
            prefs[customizationInitialTimelineItemCountKey] = count.coerceIn(
                MIN_INITIAL_TIMELINE_ITEM_COUNT,
                MAX_INITIAL_TIMELINE_ITEM_COUNT,
            )
        }
    }

    override fun getCustomizationInitialTimelineItemCountFlow(): Flow<Int> {
        return store.data.map { prefs ->
            (prefs[customizationInitialTimelineItemCountKey] ?: DEFAULT_INITIAL_TIMELINE_ITEM_COUNT)
                .coerceIn(MIN_INITIAL_TIMELINE_ITEM_COUNT, MAX_INITIAL_TIMELINE_ITEM_COUNT)
        }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }
}

private fun encodeStringSet(values: Set<String>): String {
    return values
        .filter { it.isNotBlank() }
        .sorted()
        .joinToString(",") { encodeToken(it) }
}

private fun decodeStringSet(value: String?): Set<String> {
    if (value.isNullOrBlank()) return emptySet()
    return value
        .split(',')
        .mapNotNull { token -> decodeToken(token) }
        .filter { it.isNotBlank() }
        .toSet()
}

private fun encodeStringMap(values: Map<String, String>): String {
    return values.entries
        .sortedBy { it.key }
        .joinToString("&") { entry ->
            "${encodeToken(entry.key)}=${encodeToken(entry.value)}"
        }
}

private fun decodeStringMap(value: String?): Map<String, String> {
    if (value.isNullOrBlank()) return emptyMap()
    return buildMap {
        value.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val key = decodeToken(pair.substring(0, separator)) ?: return@forEach
            val mapValue = decodeToken(pair.substring(separator + 1)) ?: return@forEach
            if (key.isNotBlank()) {
                put(key, mapValue)
            }
        }
    }
}

private fun encodeToken(token: String): String {
    return URLEncoder.encode(token, StandardCharsets.UTF_8)
}

private fun decodeToken(token: String): String? {
    return runCatching {
        URLDecoder.decode(token, StandardCharsets.UTF_8)
    }.getOrNull()
}

private fun BuildMeta.defaultLogLevel(): LogLevel {
    return when (buildType) {
        BuildType.DEBUG -> LogLevel.TRACE
        BuildType.NIGHTLY -> LogLevel.DEBUG
        BuildType.RELEASE -> LogLevel.INFO
    }
}
