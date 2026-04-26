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
internal const val DEFAULT_APP_UPDATE_APK_NAME = "app-gplay-universal-debug.apk"

@Serializable
data class AppUpdateManifest(
    val versionCode: Long? = null,
    val versionName: String? = null,
    val apk: String? = null,
    val title: String? = null,
    val message: String? = null,
    val changelog: String? = null,
    val mandatory: Boolean? = null,
    val important: Boolean? = null,
    val sha256: String? = null,
)

data class AppUpdateInfo(
    val manifest: AppUpdateManifest,
    val apkUrl: String,
)

internal fun AppUpdateInfo.cachedApkFileName(): String {
    val apkName = manifest.resolvedApkName().substringAfterLast('/')
    val extensionStartIndex = apkName.lastIndexOf('.')
    val baseName = if (extensionStartIndex > 0) apkName.substring(0, extensionStartIndex) else apkName
    val extension = if (extensionStartIndex > 0) apkName.substring(extensionStartIndex) else ""
    val versionSuffix = buildList {
        manifest.versionName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.sanitizeCacheSegment()
            ?.let(::add)
        manifest.versionCode
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
    }.joinToString(separator = "-").ifBlank { "latest" }
    return "${baseName.sanitizeCacheSegment()}-$versionSuffix$extension"
}

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

internal fun AppUpdateManifest.resolvedApkName(): String {
    return apk?.trim().orEmpty().ifBlank { DEFAULT_APP_UPDATE_APK_NAME }
}

internal fun AppUpdateManifest.resolvedApkUrl(baseUrl: String = APP_UPDATE_BASE_URL): String {
    val apkReference = resolvedApkName()
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

private fun String.sanitizeCacheSegment(): String {
    return lowercase()
        .replace(CACHE_FILE_SEGMENT_REGEX, "-")
        .trim('-')
        .ifBlank { "update" }
}

private val VERSION_NAME_PART_REGEX = Regex("\\d+")
private val CACHE_FILE_SEGMENT_REGEX = Regex("[^a-z0-9._-]+")
