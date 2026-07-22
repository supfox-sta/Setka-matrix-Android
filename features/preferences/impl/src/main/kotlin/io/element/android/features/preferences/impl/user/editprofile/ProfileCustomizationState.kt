package io.element.android.features.preferences.impl.user.editprofile

import io.element.android.libraries.architecture.AsyncAction
import kotlinx.collections.immutable.ImmutableList

data class ProfileCustomizationState(
    val profileColorHex: String,
    val badgeEmojiMxcUrl: String?,
    val statusEmojiMxcUrl: String?,
    val profileBackgroundUrl: String?,
    val isSetkaPlusActive: Boolean,
    val badgeEmojiPacks: ImmutableList<SetkaEmojiPack>,
    val saveButtonEnabled: Boolean,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (ProfileCustomizationEvent) -> Unit,
)
