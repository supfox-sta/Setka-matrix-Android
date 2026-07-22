/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

import extension.setupDependencyInjection

plugins {
    id("io.element.android-library")
}

setupDependencyInjection()

android {
    namespace = "io.element.android.libraries.setkalite.impl"
}

dependencies {
    implementation(projects.appconfig)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.preferences.api)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.services.appnavstate.api)

    implementation(projects.services.toolbox.api)

    implementation(libs.coroutines.core)
    implementation(libs.timber)
    implementation(libs.androidx.datastore.preferences)

    api(projects.libraries.setkalite.api)

    testImplementation(libs.test.junit)
    testImplementation(libs.test.truth)
    testImplementation(libs.coroutines.test)
}
