/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.setkalite.api"
}

dependencies {
    implementation(libs.coroutines.core)
}
