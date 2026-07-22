/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.appnav.setka



sealed interface SetkaLoginVerificationEvents {

    data object ClearError : SetkaLoginVerificationEvents

}


