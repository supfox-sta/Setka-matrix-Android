/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.setka

private val setkaEmojiTokenSanitizer = Regex("[^A-Za-z0-9_]+")
private val setkaEmojiTokenCollapse = Regex("_+")

enum class SetkaPackKind {
    STICKER,
    EMOJI;

    val raw: String = name.lowercase()

    companion object {
        fun fromRaw(value: String?): SetkaPackKind {
            return if (value.equals("emoji", ignoreCase = true)) EMOJI else STICKER
        }
    }
}

data class SetkaPlusSubscription(
    val tier: String,
    val planName: String?,
    val status: String,
    val isActive: Boolean,
    val startedAt: Long,
    val expiresAt: Long,
    val updatedAt: Long,
    val amountRub: Double?,
    val durationDays: Int?,
)

data class SetkaPlusPlan(
    val id: String,
    val name: String,
    val priceRub: Double,
    val durationDays: Int,
    val features: List<String>,
    val active: Boolean,
    val isDefault: Boolean,
    val sortOrder: Int,
)

data class SetkaSticker(
    val id: String,
    val name: String,
    val mxcUrl: String,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val size: Long?,
)

data class SetkaStickerPack(
    val id: String,
    val name: String,
    val kind: SetkaPackKind,
    val stickers: List<SetkaSticker>,
    val createdAt: Long?,
    val updatedAt: Long?,
)

data class SetkaPlusPaymentRequest(
    val paymentId: String,
    val provider: String,
    val status: String,
    val amount: Double,
    val currency: String,
    val label: String,
    val checkoutUrl: String?,
)

data class SetkaBootstrap(
    val subscription: SetkaPlusSubscription?,
    val plans: List<SetkaPlusPlan>,
    val stickerPacks: List<SetkaStickerPack>,
)

data class SetkaPackEditorState(
    val kind: SetkaPackKind,
    val packId: String?,
    val initialName: String,
)

data class SetkaDeleteConfirmation(
    val packId: String,
    val packName: String,
)

data class SetkaSharedPackPreviewState(
    val token: String,
    val pack: SetkaStickerPack? = null,
    val installedPackId: String? = null,
    val isLoading: Boolean = false,
)

fun SetkaSticker.inlineEmojiToken(): String {
    val normalizedName = name
        .trim()
        .replace(' ', '_')
        .replace(setkaEmojiTokenSanitizer, "_")
        .replace(setkaEmojiTokenCollapse, "_")
        .trim('_')
        .ifEmpty { "emoji" }
    return ":$normalizedName:"
}
