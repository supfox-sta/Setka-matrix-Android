package io.element.android.features.preferences.impl.threepid

sealed interface ThreePidSettingsEvents {
    data object Refresh : ThreePidSettingsEvents
    data class SetDiscoverableMsisdn(val enabled: Boolean) : ThreePidSettingsEvents
    data class SetPhoneVisibility(val visibility: SetkaVisibility) : ThreePidSettingsEvents
    data class SetLastSeenVisibility(val visibility: SetkaVisibility) : ThreePidSettingsEvents
    data class SetCallVisibility(val visibility: SetkaVisibility) : ThreePidSettingsEvents
}
