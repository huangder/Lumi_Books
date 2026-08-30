package com.huangder.lumibooks.data.backup

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.huangder.lumibooks.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupArchiveResult(val sizeBytes: Long, val itemCount: Int)

@Singleton
class BackupArchiveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotManager: PortableSnapshotManager,
    private val legacyBackupImporter: LegacyBackupImporter
) {
    suspend fun create(outputUri: Uri): BackupArchiveResult = withContext(Dispatchers.IO) {
        val bundle = snapshotManager.capture()
        val entries = mutableListOf<ArchiveEntryMetadata>()
        val rawOutput = context.contentResolver.openOutputStream(outputUri, "wt")
            ?: error("Unable to open backup destination")
        val counting = CountingOutputStream(rawOutput)
        ZipOutputStream(counting.buffered()).use { zip ->
            val stateBytes = bundle.snapshot.toJson().toByteArray(Charsets.UTF_8)
            entries += zip.writeBytes(STATE_PATH, stateBytes)
            for (source in bundle.assetSources.values.distinctBy { it.asset.archivePath }.sortedBy { it.asset.archivePath }) {
                val metadata = source.asset
                zip.putNextEntry(ZipEntry(metadata.archivePath))
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                source.openStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        size += count
                    }
                }
                zip.closeEntry()
                val sha = digest.hexDigest()
                check(sha == metadata.sha256 && size == metadata.sizeBytes) {
                    "Source changed while creating backup: ${metadata.ownerId}"
                }
                entries += ArchiveEntryMetadata(metadata.archivePath, size, sha)
            }
            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("version", FORMAT_VERSION)
                put("createdAt", bundle.snapshot.createdAt)
                put("appVersion", BuildConfig.VERSION_NAME)
                put("entries", JSONArray().apply { entries.forEach { put(it.toJson()) } })
            }.toString().toByteArray(Charsets.UTF_8)
            zip.writeBytes(MANIFEST_PATH, manifest)
        }
        BackupArchiveResult(counting.count, entries.size)
    }

    suspend fun restore(inputUri: Uri): BackupArchiveResult = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "backup_restore_${UUID.randomUUID()}")
        val archive = File(workDir, "backup.zip")
        val extracted = File(workDir, "extracted")
        workDir.mkdirs()
        extracted.mkdirs()
        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                archive.outputStream().buffered().use { input.copyTo(it) }
            } ?: error("Unable to open backup")
            check(archive.length() > 0L) { "Backup is empty" }
            val freeBytes = StatFs(workDir.absolutePath).availableBytes
            check(freeBytes > archive.length() + MIN_FREE_SPACE_BYTES) { "Not enough free space to restore backup" }
            extractSafely(archive, extracted, freeBytes - MIN_FREE_SPACE_BYTES)
            val manifestFile = File(extracted, MANIFEST_PATH)
            if (!manifestFile.isFile) {
                val legacy = legacyBackupImporter.convert(extracted)
                snapshotManager.apply(legacy.snapshot, legacy.assetFiles, replace = true)
                return@withContext BackupArchiveResult(archive.length(), legacy.assetFiles.size + 1)
            }
            val manifest = JSONObject(manifestFile.readText())
            require(manifest.optString("format") == FORMAT) { "Unsupported backup format" }
            require(manifest.optInt("version") in 1..FORMAT_VERSION) { "Unsupported backup version" }
            val listed = manifest.optJSONArray("entries") ?: JSONArray()
            for (index in 0 until listed.length()) {
                val expected = ArchiveEntryMetadata.fromJson(listed.getJSONObject(index))
                val file = safeTarget(extracted, expected.path)
                require(file.isFile) { "Backup entry is missing: ${expected.path}" }
                require(file.length() == expected.sizeBytes) { "Backup entry size mismatch: ${expected.path}" }
                require(file.sha256() == expected.sha256) { "Backup entry checksum mismatch: ${expected.path}" }
            }
            val snapshot = PortableSnapshot.fromJson(File(extracted, STATE_PATH).readText())
            val assetFiles = snapshot.assets.associate { asset ->
                asset.id to safeTarget(extracted, asset.archivePath)
            }
            snapshotManager.apply(snapshot, assetFiles, replace = true)
            BackupArchiveResult(archive.length(), listed.length())
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun extractSafely(archive: File, targetDir: File, maxUncompressedBytes: Long) {
        var total = 0L
        var count = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(++count <= MAX_ENTRY_COUNT) { "Backup contains too many entries" }
                val target = safeTarget(targetDir, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= maxUncompressedBytes) { "Backup is larger than available storage" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun safeTarget(root: File, relativePath: String): File {
        require(relativePath.isNotBlank() && !File(relativePath).isAbsolute) { "Invalid backup path" }
        val rootPath = root.canonicalFile.toPath()
        val target = File(root, relativePath).canonicalFile
        require(target.toPath().startsWith(rootPath)) { "Backup path escapes the restore directory" }
        return target
    }

    private fun ZipOutputStream.writeBytes(path: String, bytes: ByteArray): ArchiveEntryMetadata {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
        return ArchiveEntryMetadata(path, bytes.size.toLong(), bytes.sha256())
    }

    private data class ArchiveEntryMetadata(val path: String, val sizeBytes: Long, val sha256: String) {
        fun toJson() = JSONObject().apply {
            put("path", path); put("sizeBytes", sizeBytes); put("sha256", sha256)
        }

        companion object {
            fun fromJson(json: JSONObject) = ArchiveEntryMetadata(
                json.getString("path"), json.getLong("sizeBytes"), json.getString("sha256")
            )
        }
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var count: Long = 0L
            private set

        override fun write(value: Int) {
            out.write(value)
            count++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            count += length
        }
    }

    companion object {
        private const val FORMAT = "lumi-portable-backup"
        private const val FORMAT_VERSION = 1
        private const val MANIFEST_PATH = "backup-manifest.json"
        private const val STATE_PATH = "state.json"
        private const val MAX_ENTRY_COUNT = 100_000
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024L * 1024L
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

private fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.hexDigest()
}

private fun MessageDigest.hexDigest(): String = digest().toHex()
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
