/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.update

import kotlinx.serialization.Serializable
import kotlin.math.max

internal const val APP_UPDATE_BASE_URL = "https://web.setka-matrix.ru/themes/element/img/app-android/"
internal const val APP_UPDATE_MANIFEST_FILE = "update.json"
internal const val DEFAULT_APP_UPDATE_DOWNLOAD_NAME = "app-gplay-universal-debug.apk"

@Serializable
data class AppUpdateManifest(
    val versionCode: Long? = null,
    val versionName: String? = null,
    val apk: String? = null,
    val downloadUrl: String? = null,
    val title: String? = null,
    val message: String? = null,
    val changelog: String? = null,
    val mandatory: Boolean? = null,
    val important: Boolean? = null,
)

data class AppUpdateInfo(
    val manifest: AppUpdateManifest,
    val downloadUrl: String,
)

internal fun AppUpdateManifest.shouldOfferUpdate(
    installedVersionCode: Long,
    installedVersionName: String,
): Boolean {
    val versionNameComparison = compareVersionNames(
        remoteVersionName = versionName?.trim().orEmpty(),
        installedVersionName = installedVersionName.trim(),
    )
    if (versionNameComparison != 0) {
        return versionNameComparison > 0
    }
    val remoteVersionCode = versionCode
    if (remoteVersionCode != null) {
        return normalizeComparableVersionCode(remoteVersionCode) > normalizeComparableVersionCode(installedVersionCode)
    }
    return false
}

internal fun AppUpdateManifest.isMandatoryUpdate(): Boolean {
    return mandatory == true || important == true
}

internal fun AppUpdateManifest.resolvedDownloadUrl(baseUrl: String = APP_UPDATE_BASE_URL): String {
    val explicitDownloadUrl = downloadUrl?.trim().orEmpty()
    if (explicitDownloadUrl.startsWith("https://") || explicitDownloadUrl.startsWith("http://")) {
        return explicitDownloadUrl
    }
    val apkReference = apk?.trim().orEmpty().ifBlank { DEFAULT_APP_UPDATE_DOWNLOAD_NAME }
    return if (apkReference.startsWith("https://") || apkReference.startsWith("http://")) {
        apkReference
    } else {
        normalizedUpdateBaseUrl(baseUrl) + apkReference.trimStart('/')
    }
}

internal fun normalizeComparableVersionCode(versionCode: Long): Long {
    return if (versionCode >= 100_000_000L) versionCode / 10 else versionCode
}

internal fun compareVersionNames(
    remoteVersionName: String,
    installedVersionName: String,
): Int {
    if (remoteVersionName.isBlank() || installedVersionName.isBlank()) return 0
    if (remoteVersionName == installedVersionName) return 0
    val remoteParts = VERSION_NAME_PART_REGEX.findAll(remoteVersionName).map { it.value.toInt() }.toList()
    val installedParts = VERSION_NAME_PART_REGEX.findAll(installedVersionName).map { it.value.toInt() }.toList()
    if (remoteParts.isEmpty() || installedParts.isEmpty()) {
        return remoteVersionName.compareTo(installedVersionName)
    }
    val maxSize = max(remoteParts.size, installedParts.size)
    repeat(maxSize) { index ->
        val remotePart = remoteParts.getOrElse(index) { 0 }
        val installedPart = installedParts.getOrElse(index) { 0 }
        if (remotePart != installedPart) {
            return remotePart.compareTo(installedPart)
        }
    }
    return remoteVersionName.compareTo(installedVersionName)
}

private fun normalizedUpdateBaseUrl(baseUrl: String): String {
    return if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
}

private val VERSION_NAME_PART_REGEX = Regex("\\d+")
