package io.element.android.features.preferences.impl.threepid

import io.element.android.libraries.architecture.AsyncData

data class ThreePidSettingsState(
    val privacy: AsyncData<SetkaPrivacy> = AsyncData.Uninitialized,
    val errorMessage: String? = null,
    val eventSink: (ThreePidSettingsEvents) -> Unit,
)
