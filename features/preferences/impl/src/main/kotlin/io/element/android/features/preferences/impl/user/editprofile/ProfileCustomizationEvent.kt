package io.element.android.features.preferences.impl.user.editprofile

sealed interface ProfileCustomizationEvent {
    data object PickProfileBackground : ProfileCustomizationEvent
    data object RemoveProfileBackground : ProfileCustomizationEvent
    data class UpdateProfileColorHex(val color: String) : ProfileCustomizationEvent
    data class SetBadgeEmoji(val mxcUrl: String?) : ProfileCustomizationEvent
    data class SetStatusEmoji(val mxcUrl: String?) : ProfileCustomizationEvent
    data object Exit : ProfileCustomizationEvent
    data object DiscardChanges : ProfileCustomizationEvent
    data object Save : ProfileCustomizationEvent
    data object CloseDialog : ProfileCustomizationEvent
}
