/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdateManifestTest {
    @Test
    fun `same release does not trigger update when installed APK version includes ABI digit`() {
        val manifest = AppUpdateManifest(versionCode = 20260303)

        assertThat(
            manifest.shouldOfferUpdate(
                installedVersionCode = 202603030,
                installedVersionName = "26.03.3",
            )
        ).isFalse()
    }

    @Test
    fun `newer release triggers update`() {
        val manifest = AppUpdateManifest(versionCode = 20260304)

        assertThat(
            manifest.shouldOfferUpdate(
                installedVersionCode = 202603030,
                installedVersionName = "26.03.3",
            )
        ).isTrue()
    }

    @Test
    fun `version name fallback triggers update when code is missing`() {
        val manifest = AppUpdateManifest(versionName = "26.03.4")

        assertThat(
            manifest.shouldOfferUpdate(
                installedVersionCode = 202603030,
                installedVersionName = "26.03.3",
            )
        ).isTrue()
    }

    @Test
    fun `newer version name triggers update even when base version codes match`() {
        val manifest = AppUpdateManifest(
            versionCode = 202603031,
            versionName = "26.03.4",
        )

        assertThat(
            manifest.shouldOfferUpdate(
                installedVersionCode = 202603032,
                installedVersionName = "26.03.3",
            )
        ).isTrue()
    }

    @Test
    fun `same version name does not trigger update when only abi digit differs`() {
        val manifest = AppUpdateManifest(
            versionCode = 202603031,
            versionName = "26.03.3",
        )

        assertThat(
            manifest.shouldOfferUpdate(
                installedVersionCode = 202603032,
                installedVersionName = "26.03.3",
            )
        ).isFalse()
    }

    @Test
    fun `mandatory update is true when mandatory flag is true`() {
        val manifest = AppUpdateManifest(mandatory = true)

        assertThat(manifest.isMandatoryUpdate()).isTrue()
    }

    @Test
    fun `mandatory update is true when important flag is true`() {
        val manifest = AppUpdateManifest(important = true)

        assertThat(manifest.isMandatoryUpdate()).isTrue()
    }

    @Test
    fun `relative apk name resolves against default base url`() {
        val manifest = AppUpdateManifest(apk = "app-gplay-universal-debug.apk")

        assertThat(manifest.resolvedApkUrl())
            .isEqualTo("https://web.setka-matrix.ru/themes/element/img/app-android/app-gplay-universal-debug.apk")
    }

    @Test
    fun `absolute apk url is preserved`() {
        val manifest = AppUpdateManifest(apk = "https://cdn.example.org/app.apk")

        assertThat(manifest.resolvedApkUrl()).isEqualTo("https://cdn.example.org/app.apk")
    }

    @Test
    fun `cached apk file name keeps extension and version identity`() {
        val updateInfo = AppUpdateInfo(
            manifest = AppUpdateManifest(
                apk = "app-gplay-universal-debug.apk",
                versionName = "26.03.4",
                versionCode = 202603031,
            ),
            apkUrl = "https://web.setka-matrix.ru/themes/element/img/app-android/app-gplay-universal-debug.apk",
        )

        assertThat(updateInfo.cachedApkFileName())
            .isEqualTo("app-gplay-universal-debug-26.03.4-202603031.apk")
    }

    @Test
    fun `cached apk file names differ between releases that share the same remote file name`() {
        val oldUpdate = AppUpdateInfo(
            manifest = AppUpdateManifest(
                apk = "app-gplay-universal-debug.apk",
                versionName = "26.03.4",
                versionCode = 202603031,
            ),
            apkUrl = "https://web.setka-matrix.ru/themes/element/img/app-android/app-gplay-universal-debug.apk",
        )
        val newUpdate = AppUpdateInfo(
            manifest = AppUpdateManifest(
                apk = "app-gplay-universal-debug.apk",
                versionName = "26.03.5",
                versionCode = 202603041,
            ),
            apkUrl = "https://web.setka-matrix.ru/themes/element/img/app-android/app-gplay-universal-debug.apk",
        )

        assertThat(newUpdate.cachedApkFileName()).isNotEqualTo(oldUpdate.cachedApkFileName())
    }
}
