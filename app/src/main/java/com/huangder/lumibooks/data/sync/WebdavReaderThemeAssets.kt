package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.backup.PortableAsset
import com.huangder.lumibooks.data.backup.PortablePreference
import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.backup.PortableSnapshotBundle
import com.huangder.lumibooks.data.backup.assetReferenceIds
import com.huangder.lumibooks.data.backup.hasMissingReaderThemePresets
import com.huangder.lumibooks.data.backup.hasSafeFileName
import com.huangder.lumibooks.data.backup.isReaderThemeAssetKind
import com.huangder.lumibooks.data.backup.isReaderThemePreference
import com.huangder.lumibooks.data.backup.mapPortableString
import com.huangder.lumibooks.data.backup.portableAssetId
import com.huangder.lumibooks.data.backup.readerThemeAssetPaths
import com.huangder.lumibooks.data.backup.withStableReaderThemeId
import com.huangder.lumibooks.data.backup.withoutProcessedBackgrounds

/**
 * A state written by an older client can contain either a path on the originating device
 * or an asset reference whose descriptor was lost when escaped JSON wasn't scanned.
 */
internal fun PortableSnapshot.repairReaderThemeAssetReferences(
    local: PortableSnapshotBundle,
    repairedCloud: PortableSnapshot? = null
): PortableSnapshot {
    val oldAssets = assets.associateBy { it.id }
    val cloudAssets = repairedCloud?.assets.orEmpty().associateBy { it.id }
    val knownAssets = (local.snapshot.assets + cloudAssets.values)
        .filter { isReaderThemeAssetKind(it.kind) && it.isValidReaderThemeAsset() }
        .distinctBy { Triple(it.kind, it.sha256, it.fileName) }
        .groupBy { it.kind to it.sha256 }
    val cloudPreferences = repairedCloud?.preferences.orEmpty().associateBy { it.key }
    val repairedAssets = linkedMapOf<String, PortableAsset>()
    val repairedPreferences = preferences.map { original ->
        val preference = original.withoutProcessedBackgrounds()
        val pathsToRepair = preference.readerThemeAssetPaths()
        if (pathsToRepair.isEmpty()) return@map preference
        val cloudPresets = cloudPreferences[preference.key]?.readerThemeAssetPaths().orEmpty()
            .associateBy { it.presetId }
        val paths = pathsToRepair.mapNotNull { (path, kind, presetId) ->
            val referenceId = portableAssetId(path)
            val asset = if (referenceId != null) {
                oldAssets[referenceId]?.takeIf { it.kind == kind && it.isValidReaderThemeAsset() }
                    ?: knownAssets[kind to referenceId.take(64)]?.firstOrNull()
            } else {
                local.localThemeAssetIdsByPath[path]?.let(local.assetSources::get)?.asset?.takeIf { it.kind == kind }
                    ?: cloudPresets[presetId]?.path?.let(::portableAssetId)?.let(cloudAssets::get)
                        ?.takeIf { it.kind == kind && it.isValidReaderThemeAsset() }
            } ?: return@mapNotNull null
            val stable = asset.withStableReaderThemeId()
            repairedAssets[stable.id] = stable
            path to "asset://${stable.id}"
        }.toMap()
        preference.copy(value = mapPortableString(preference.value) { paths[it] ?: it })
    }
    return copy(preferences = repairedPreferences, assets = (repairedAssets.values + assets).distinctBy { it.id })
}

internal fun PortableSnapshot.readerThemeAssetsOrNull(): List<PortableAsset>? {
    val theme = preferences.filter { isReaderThemePreference(it.key) }
    if (theme.hasMissingReaderThemePresets()) return null
    val byId = assets.associateBy { it.id }
    for ((path, kind) in theme.flatMap { it.readerThemeAssetPaths() }) {
        val asset = portableAssetId(path)?.let(byId::get) ?: return null
        if (asset.kind != kind || !asset.isValidReaderThemeAsset()) return null
    }
    return theme.flatMap { it.assetReferenceIds() }.distinct().map { id ->
        byId[id]?.takeIf { isReaderThemeAssetKind(it.kind) && it.isValidReaderThemeAsset() } ?: return null
    }
}

internal fun List<PortablePreference>.withoutReaderTheme() =
    filterNot { isReaderThemePreference(it.key) }

private val readerThemeFileName = Regex("[a-f0-9]{64}\\.[a-z0-9]{1,10}")

private fun PortableAsset.isValidReaderThemeAsset(): Boolean =
    hasSafeFileName() && fileName.matches(readerThemeFileName) &&
        fileName.substringBeforeLast('.') == sha256 && sizeBytes > 0
