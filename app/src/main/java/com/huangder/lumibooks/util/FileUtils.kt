package com.huangder.lumibooks.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileUtils {
    private const val BOOKS_DIR = "books"
    private const val COVERS_DIR = "covers"

    fun getBooksDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), BOOKS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCoversDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), COVERS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyFileToInternal(context: Context, uri: Uri, fileName: String): File? {
        var destination: File? = null
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val booksDir = getBooksDirectory(context)
            val providerName = File(fileName).name
            val safeFileName = providerName.takeUnless {
                it.isBlank() || it == "." || it == ".."
            } ?: "book"
            val requested = File(booksDir, safeFileName)
            val file = if (!requested.exists()) requested else {
                val base = safeFileName.substringBeforeLast('.', safeFileName)
                val extension = safeFileName.substringAfterLast('.', "")
                generateSequence(2) { it + 1 }
                    .map { index -> File(booksDir, if (extension.isBlank()) "$base ($index)" else "$base ($index).$extension") }
                    .first { !it.exists() }
            }
            destination = file

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            file
        } catch (e: Exception) {
            destination?.let { partialFile -> runCatching { partialFile.delete() } }
            e.printStackTrace()
            null
        }
    }

    fun generateBookId(): String {
        return UUID.randomUUID().toString()
    }

    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }

    fun getFileNameFromLocation(context: Context, location: String): String {
        return if (BookFileAccess.isContentUri(location)) {
            getFileNameFromUri(context, Uri.parse(location)) ?: "book"
        } else {
            File(location).name
        }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    /**
     * 将用户选择的封面图片复制到 covers 目录
     * 使用 custom_{bookId}_{timestamp}.jpg 命名，保留原始封面不被覆盖
     * @return 复制后的文件路径，失败返回 null
     */
    fun copyCoverImage(context: Context, uri: Uri, bookId: String): String? {
        var destination: File? = null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val coversDir = getCoversDirectory(context)
            val destinationFile = File(coversDir, "custom_${bookId}_${System.currentTimeMillis()}.jpg")
            destination = destinationFile
            inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            destinationFile.absolutePath
        } catch (error: Exception) {
            destination?.let { partialFile -> runCatching { partialFile.delete() } }
            error.printStackTrace()
            null
        }
    }

    /**
     * 将 Bitmap（如浏览器裁剪捕获的封面区域）压缩保存到 covers 目录
     * 与 [copyCoverImage] 使用相同的 custom_{bookId}_{timestamp}.jpg 命名规则
     * @return 保存后的文件路径，失败返回 null
     */
    fun saveCoverBitmap(context: Context, bitmap: Bitmap, bookId: String): String? {
        var destination: File? = null
        return try {
            val coversDir = getCoversDirectory(context)
            val destinationFile = File(coversDir, "custom_${bookId}_${System.currentTimeMillis()}.jpg")
            destination = destinationFile
            FileOutputStream(destinationFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            destinationFile.absolutePath
        } catch (error: Exception) {
            destination?.let { partialFile -> runCatching { partialFile.delete() } }
            error.printStackTrace()
            null
        }
    }

    /**
     * 删除书本的自定义封面文件
     */
    fun deleteCustomCover(context: Context, bookId: String) {
        deleteOtherCustomCovers(context, bookId, keepLocation = null)
    }

    fun deleteOtherCustomCovers(context: Context, bookId: String, keepLocation: String?) {
        runCatching {
            val keepFile = keepLocation?.let(::File)?.canonicalFile
            val coversDir = getCoversDirectory(context)
            coversDir.listFiles()
                ?.filter { cover ->
                    cover.name.startsWith("custom_${bookId}_") &&
                        runCatching { cover.canonicalFile != keepFile }.getOrDefault(true)
                }
                ?.forEach { cover -> runCatching { cover.delete() } }
        }
    }

    /**
     * 判断封面路径是否为用户自定义封面
     */
    fun isCustomCover(coverPath: String?): Boolean {
        return coverPath?.contains("/custom_") == true || coverPath?.contains("\\custom_") == true
    }

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Returns true only for files copied/generated inside this app's books directory. */
    fun isAppManagedBookLocation(context: Context, location: String): Boolean {
        if (BookFileAccess.isContentUri(location)) return false
        return isInsideDirectory(File(location), getBooksDirectory(context))
    }

    /**
     * Deletes the physical book only when it belongs to the app-managed books directory.
     * SAF/authorized-directory documents and arbitrary external paths are deliberately kept.
     */
    fun deleteAppManagedBookFile(context: Context, location: String): Boolean {
        if (!isAppManagedBookLocation(context, location)) return true
        return deleteFile(File(location))
    }

    /** Deletes a derived file only when it is inside an app-private directory. */
    fun deleteAppOwnedFile(context: Context, location: String?): Boolean {
        if (location.isNullOrBlank() || BookFileAccess.isContentUri(location)) return true
        val file = File(location)
        val ownedRoots = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.getExternalFilesDir(null)
        )
        if (ownedRoots.none { root -> isInsideDirectory(file, root) }) return true
        return deleteFile(file)
    }

    fun deleteTxtIndexCache(context: Context, sourceLocation: String) {
        val hash = sourceLocation.hashCode().toString(16)
        deleteFile(File(context.cacheDir, "txt_index/$hash.cache"))
    }

    private fun isInsideDirectory(file: File, directory: File): Boolean {
        return runCatching {
            val canonicalFile = file.canonicalFile
            val canonicalDirectory = directory.canonicalFile
            canonicalFile.path.startsWith(canonicalDirectory.path + File.separator)
        }.getOrDefault(false)
    }

    fun getFileSize(file: File): Long {
        return if (file.exists()) file.length() else 0
    }

    fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> "${sizeBytes / (1024 * 1024)} MB"
            else -> "${sizeBytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
