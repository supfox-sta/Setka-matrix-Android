/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.preferences.api.store.CallAudioBackgroundStyles
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
class CustomizationPresenter(
    private val appPreferencesStore: AppPreferencesStore,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) : Presenter<CustomizationState> {
    @Composable
    override fun present(): CustomizationState {
        val themeMode by remember {
            appPreferencesStore.getThemeFlow()
        }.collectAsState(initial = null)
        val accentColorHex by remember {
            appPreferencesStore.getCustomizationAccentColorHexFlow()
        }.collectAsState(initial = null)
        val uiScale by remember {
            appPreferencesStore.getCustomizationUiScaleFlow()
        }.collectAsState(initial = 1.0f)
        val messageScale by remember {
            appPreferencesStore.getCustomizationMessageScaleFlow()
        }.collectAsState(initial = 1.0f)
        val bubbleRadiusDp by remember {
            appPreferencesStore.getCustomizationBubbleRadiusDpFlow()
        }.collectAsState(initial = 12)
        val bubbleWidthPercent by remember {
            appPreferencesStore.getCustomizationBubbleWidthPercentFlow()
        }.collectAsState(initial = 78)
        val timelineOverlayOpacityPercent by remember {
            appPreferencesStore.getCustomizationTimelineOverlayOpacityPercentFlow()
        }.collectAsState(initial = 22)
        val composerBackgroundOpacityPercent by remember {
            appPreferencesStore.getCustomizationComposerBackgroundOpacityPercentFlow()
        }.collectAsState(initial = 92)
        val wallpaperBlurDp by remember {
            appPreferencesStore.getCustomizationWallpaperBlurDpFlow()
        }.collectAsState(initial = 0)
        val showEncryptionStatus by remember {
            appPreferencesStore.getCustomizationShowEncryptionStatusFlow()
        }.collectAsState(initial = false)
        val topBarBackgroundColorHex by remember {
            appPreferencesStore.getCustomizationTopBarBackgroundColorHexFlow()
        }.collectAsState(initial = null)
        val topBarTextColorHex by remember {
            appPreferencesStore.getCustomizationTopBarTextColorHexFlow()
        }.collectAsState(initial = null)
        val composerBackgroundColorHex by remember {
            appPreferencesStore.getCustomizationComposerBackgroundColorHexFlow()
        }.collectAsState(initial = null)
        val serviceBubbleColorHex by remember {
            appPreferencesStore.getCustomizationServiceBubbleColorHexFlow()
        }.collectAsState(initial = null)
        val serviceTextColorHex by remember {
            appPreferencesStore.getCustomizationServiceTextColorHexFlow()
        }.collectAsState(initial = null)
        val incomingBubbleColorHex by remember {
            appPreferencesStore.getCustomizationIncomingBubbleColorHexFlow()
        }.collectAsState(initial = null)
        val outgoingBubbleColorHex by remember {
            appPreferencesStore.getCustomizationOutgoingBubbleColorHexFlow()
        }.collectAsState(initial = null)
        val incomingBubbleGradientToColorHex by remember {
            appPreferencesStore.getCustomizationIncomingBubbleGradientToColorHexFlow()
        }.collectAsState(initial = null)
        val outgoingBubbleGradientToColorHex by remember {
            appPreferencesStore.getCustomizationOutgoingBubbleGradientToColorHexFlow()
        }.collectAsState(initial = null)
        val homeBackgroundColorHex by remember {
            appPreferencesStore.getCustomizationHomeBackgroundColorHexFlow()
        }.collectAsState(initial = null)
        val homeBackgroundImageUri by remember {
            appPreferencesStore.getCustomizationHomeBackgroundImageUriFlow()
        }.collectAsState(initial = null)
        val defaultRoomWallpaperStyle by remember {
            appPreferencesStore.getCustomizationDefaultRoomWallpaperStyleFlow()
        }.collectAsState(initial = null)
        val enableChatAnimations by remember {
            appPreferencesStore.getCustomizationEnableChatAnimationsFlow()
        }.collectAsState(initial = true)
        val enableBlurEffects by remember {
            appPreferencesStore.getCustomizationEnableBlurEffectsFlow()
        }.collectAsState(initial = true)
        val callAudioBackgroundStyle by remember {
            appPreferencesStore.getCustomizationCallAudioBackgroundStyleFlow()
        }.collectAsState(initial = CallAudioBackgroundStyles.GRADIENT)
        val callPreferEarpieceByDefault by remember {
            appPreferencesStore.getCallPreferEarpieceByDefaultFlow()
        }.collectAsState(initial = true)
        val callProximitySensorEnabled by remember {
            appPreferencesStore.getCallEnableProximitySensorFlow()
        }.collectAsState(initial = true)
        val initialTimelineItemCount by remember {
            appPreferencesStore.getCustomizationInitialTimelineItemCountFlow()
        }.collectAsState(initial = 12)

        fun handleEvent(event: CustomizationEvents) {
            sessionCoroutineScope.launch {
                when (event) {
                    is CustomizationEvents.SetThemeMode -> {
                        appPreferencesStore.setTheme(event.themeMode)
                    }
                    is CustomizationEvents.SetAccentColorHex -> {
                        appPreferencesStore.setCustomizationAccentColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetUiScale -> {
                        appPreferencesStore.setCustomizationUiScale(event.scale)
                    }
                    is CustomizationEvents.SetMessageScale -> {
                        appPreferencesStore.setCustomizationMessageScale(event.scale)
                    }
                    is CustomizationEvents.SetBubbleRadius -> {
                        appPreferencesStore.setCustomizationBubbleRadiusDp(event.radiusDp)
                    }
                    is CustomizationEvents.SetBubbleWidthPercent -> {
                        appPreferencesStore.setCustomizationBubbleWidthPercent(event.widthPercent)
                    }
                    is CustomizationEvents.SetTimelineOverlayOpacityPercent -> {
                        appPreferencesStore.setCustomizationTimelineOverlayOpacityPercent(event.opacityPercent)
                    }
                    is CustomizationEvents.SetComposerBackgroundOpacityPercent -> {
                        appPreferencesStore.setCustomizationComposerBackgroundOpacityPercent(event.opacityPercent)
                    }
                    is CustomizationEvents.SetWallpaperBlurDp -> {
                        appPreferencesStore.setCustomizationWallpaperBlurDp(event.blurDp)
                    }
                    is CustomizationEvents.SetShowEncryptionStatus -> {
                        appPreferencesStore.setCustomizationShowEncryptionStatus(event.show)
                    }
                    is CustomizationEvents.SetTopBarBackgroundColorHex -> {
                        appPreferencesStore.setCustomizationTopBarBackgroundColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetTopBarTextColorHex -> {
                        appPreferencesStore.setCustomizationTopBarTextColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetComposerBackgroundColorHex -> {
                        appPreferencesStore.setCustomizationComposerBackgroundColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetServiceBubbleColorHex -> {
                        appPreferencesStore.setCustomizationServiceBubbleColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetServiceTextColorHex -> {
                        appPreferencesStore.setCustomizationServiceTextColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetIncomingBubbleColorHex -> {
                        appPreferencesStore.setCustomizationIncomingBubbleColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetOutgoingBubbleColorHex -> {
                        appPreferencesStore.setCustomizationOutgoingBubbleColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetIncomingBubbleGradientToColorHex -> {
                        appPreferencesStore.setCustomizationIncomingBubbleGradientToColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetOutgoingBubbleGradientToColorHex -> {
                        appPreferencesStore.setCustomizationOutgoingBubbleGradientToColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetHomeBackgroundColorHex -> {
                        appPreferencesStore.setCustomizationHomeBackgroundColorHex(event.colorHex)
                    }
                    is CustomizationEvents.SetHomeBackgroundImageUri -> {
                        appPreferencesStore.setCustomizationHomeBackgroundImageUri(event.uri)
                    }
                    is CustomizationEvents.SetDefaultRoomWallpaperStyle -> {
                        appPreferencesStore.setCustomizationDefaultRoomWallpaperStyle(event.style)
                    }
                    is CustomizationEvents.SetEnableChatAnimations -> {
                        appPreferencesStore.setCustomizationEnableChatAnimations(event.enabled)
                    }
                    is CustomizationEvents.SetEnableBlurEffects -> {
                        appPreferencesStore.setCustomizationEnableBlurEffects(event.enabled)
                    }
                    is CustomizationEvents.SetCallAudioBackgroundStyle -> {
                        appPreferencesStore.setCustomizationCallAudioBackgroundStyle(event.style)
                    }
                    is CustomizationEvents.SetCallPreferEarpieceByDefault -> {
                        appPreferencesStore.setCallPreferEarpieceByDefault(event.enabled)
                    }
                    is CustomizationEvents.SetCallProximitySensorEnabled -> {
                        appPreferencesStore.setCallEnableProximitySensor(event.enabled)
                    }
                    is CustomizationEvents.SetInitialTimelineItemCount -> {
                        appPreferencesStore.setCustomizationInitialTimelineItemCount(event.count)
                    }
                    is CustomizationEvents.ImportTheme -> {
                        appPreferencesStore.setTheme(event.theme.themeMode ?: "System")
                        appPreferencesStore.setCustomizationAccentColorHex(event.theme.accentColorHex)
                        appPreferencesStore.setCustomizationUiScale(event.theme.uiScale)
                        appPreferencesStore.setCustomizationMessageScale(event.theme.messageScale)
                        appPreferencesStore.setCustomizationBubbleRadiusDp(event.theme.bubbleRadiusDp)
                        appPreferencesStore.setCustomizationBubbleWidthPercent(event.theme.bubbleWidthPercent)
                        appPreferencesStore.setCustomizationTimelineOverlayOpacityPercent(event.theme.timelineOverlayOpacityPercent)
                        appPreferencesStore.setCustomizationComposerBackgroundOpacityPercent(event.theme.composerBackgroundOpacityPercent)
                        appPreferencesStore.setCustomizationWallpaperBlurDp(event.theme.wallpaperBlurDp)
                        appPreferencesStore.setCustomizationShowEncryptionStatus(event.theme.showEncryptionStatus)
                        appPreferencesStore.setCustomizationTopBarBackgroundColorHex(event.theme.topBarBackgroundColorHex)
                        appPreferencesStore.setCustomizationTopBarTextColorHex(event.theme.topBarTextColorHex)
                        appPreferencesStore.setCustomizationComposerBackgroundColorHex(event.theme.composerBackgroundColorHex)
                        appPreferencesStore.setCustomizationServiceBubbleColorHex(event.theme.serviceBubbleColorHex)
                        appPreferencesStore.setCustomizationServiceTextColorHex(event.theme.serviceTextColorHex)
                        appPreferencesStore.setCustomizationIncomingBubbleColorHex(event.theme.incomingBubbleColorHex)
                        appPreferencesStore.setCustomizationOutgoingBubbleColorHex(event.theme.outgoingBubbleColorHex)
                        appPreferencesStore.setCustomizationIncomingBubbleGradientToColorHex(event.theme.incomingBubbleGradientToColorHex)
                        appPreferencesStore.setCustomizationOutgoingBubbleGradientToColorHex(event.theme.outgoingBubbleGradientToColorHex)
                        appPreferencesStore.setCustomizationHomeBackgroundColorHex(event.theme.homeBackgroundColorHex)
                        appPreferencesStore.setCustomizationHomeBackgroundImageUri(event.theme.homeBackgroundImageUri)
                        appPreferencesStore.setCustomizationDefaultRoomWallpaperStyle(event.theme.defaultRoomWallpaperStyle)
                        appPreferencesStore.setCustomizationEnableChatAnimations(event.theme.enableChatAnimations)
                        appPreferencesStore.setCustomizationEnableBlurEffects(event.theme.enableBlurEffects)
                        appPreferencesStore.setCustomizationCallAudioBackgroundStyle(event.theme.callAudioBackgroundStyle)
                        appPreferencesStore.setCallPreferEarpieceByDefault(event.theme.callPreferEarpieceByDefault)
                        appPreferencesStore.setCallEnableProximitySensor(event.theme.callProximitySensorEnabled)
                        appPreferencesStore.setCustomizationInitialTimelineItemCount(event.theme.initialTimelineItemCount)
                    }
                    CustomizationEvents.ResetToDefault -> {
                        appPreferencesStore.setTheme("System")
                        appPreferencesStore.setCustomizationAccentColorHex(null)
                        appPreferencesStore.setCustomizationUiScale(1.0f)
                        appPreferencesStore.setCustomizationMessageScale(1.0f)
                        appPreferencesStore.setCustomizationBubbleRadiusDp(12)
                        appPreferencesStore.setCustomizationBubbleWidthPercent(78)
                        appPreferencesStore.setCustomizationTimelineOverlayOpacityPercent(22)
                        appPreferencesStore.setCustomizationComposerBackgroundOpacityPercent(92)
                        appPreferencesStore.setCustomizationWallpaperBlurDp(0)
                        appPreferencesStore.setCustomizationShowEncryptionStatus(false)
                        appPreferencesStore.setCustomizationTopBarBackgroundColorHex(null)
                        appPreferencesStore.setCustomizationTopBarTextColorHex(null)
                        appPreferencesStore.setCustomizationComposerBackgroundColorHex(null)
                        appPreferencesStore.setCustomizationServiceBubbleColorHex(null)
                        appPreferencesStore.setCustomizationServiceTextColorHex(null)
                        appPreferencesStore.setCustomizationIncomingBubbleColorHex(null)
                        appPreferencesStore.setCustomizationOutgoingBubbleColorHex(null)
                        appPreferencesStore.setCustomizationIncomingBubbleGradientToColorHex(null)
                        appPreferencesStore.setCustomizationOutgoingBubbleGradientToColorHex(null)
                        appPreferencesStore.setCustomizationHomeBackgroundColorHex(null)
                        appPreferencesStore.setCustomizationHomeBackgroundImageUri(null)
                        appPreferencesStore.setCustomizationDefaultRoomWallpaperStyle(null)
                        appPreferencesStore.setCustomizationEnableChatAnimations(true)
                        appPreferencesStore.setCustomizationEnableBlurEffects(true)
                        appPreferencesStore.setCustomizationCallAudioBackgroundStyle(CallAudioBackgroundStyles.GRADIENT)
                        appPreferencesStore.setCallPreferEarpieceByDefault(true)
                        appPreferencesStore.setCallEnableProximitySensor(true)
                        appPreferencesStore.setCustomizationInitialTimelineItemCount(12)
                    }
                }
            }
        }

        return CustomizationState(
            themeMode = themeMode,
            accentColorHex = accentColorHex,
            uiScale = uiScale,
            messageScale = messageScale,
            bubbleRadiusDp = bubbleRadiusDp,
            bubbleWidthPercent = bubbleWidthPercent,
            timelineOverlayOpacityPercent = timelineOverlayOpacityPercent,
            composerBackgroundOpacityPercent = composerBackgroundOpacityPercent,
            wallpaperBlurDp = wallpaperBlurDp,
            showEncryptionStatus = showEncryptionStatus,
            topBarBackgroundColorHex = topBarBackgroundColorHex,
            topBarTextColorHex = topBarTextColorHex,
            composerBackgroundColorHex = composerBackgroundColorHex,
            serviceBubbleColorHex = serviceBubbleColorHex,
            serviceTextColorHex = serviceTextColorHex,
            incomingBubbleColorHex = incomingBubbleColorHex,
            outgoingBubbleColorHex = outgoingBubbleColorHex,
            incomingBubbleGradientToColorHex = incomingBubbleGradientToColorHex,
            outgoingBubbleGradientToColorHex = outgoingBubbleGradientToColorHex,
            homeBackgroundColorHex = homeBackgroundColorHex,
            homeBackgroundImageUri = homeBackgroundImageUri,
            defaultRoomWallpaperStyle = defaultRoomWallpaperStyle,
            enableChatAnimations = enableChatAnimations,
            enableBlurEffects = enableBlurEffects,
            callAudioBackgroundStyle = callAudioBackgroundStyle,
            callPreferEarpieceByDefault = callPreferEarpieceByDefault,
            callProximitySensorEnabled = callProximitySensorEnabled,
            initialTimelineItemCount = initialTimelineItemCount,
            eventSink = ::handleEvent,
        )
    }
}
