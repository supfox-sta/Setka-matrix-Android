/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import io.element.android.libraries.matrix.api.core.SessionId

sealed interface HomeEvent {
    data class SelectHomeNavigationBarItem(val item: HomeNavigationBarItem) : HomeEvent
    data class SwitchToAccount(val sessionId: SessionId) : HomeEvent
    data object RefreshContacts : HomeEvent
    data object ToggleQuickTheme : HomeEvent
    data object RefreshSetkaPlus : HomeEvent
    data class BuySetkaPlusPlan(val planId: String) : HomeEvent
    data class SetkaPlusVoucherCodeChanged(val code: String) : HomeEvent
    data object RedeemSetkaPlusVoucher : HomeEvent
    data object ClearSetkaPlusVoucherNotice : HomeEvent
    data object ClearSetkaPlusError : HomeEvent
    data object DismissSetkaPlusPaymentResult : HomeEvent
    data object OpenStatusEmojiPicker : HomeEvent
    data object CloseStatusEmojiPicker : HomeEvent
    data class SelectStatusEmoji(val mxcUrl: String?) : HomeEvent
    data object ToggleSetkaLite : HomeEvent
    data object OpenTransferQueue : HomeEvent
    data object DismissTransferQueue : HomeEvent
    data object ClearTransferQueueCompleted : HomeEvent
    data object RefreshSetkaLiteSync : HomeEvent
    data object DismissSetkaLiteSuggestion : HomeEvent
}
