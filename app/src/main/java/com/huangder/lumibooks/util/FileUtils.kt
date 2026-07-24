package com.huangder.lumibooks.util

import android.content.Context
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
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val booksDir = getBooksDirectory(context)
            val file = File(booksDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            file
        } catch (e: Exception) {
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

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    it.getString(nameIndex)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * 将用户选择的封面图片复制到 covers 目录
     * 使用 custom_{bookId}_{timestamp}.jpg 命名，保留原始封面不被覆盖
     * @return 复制后的文件路径，失败返回 null
     */
    fun copyCoverImage(context: Context, uri: Uri, bookId: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val coversDir = getCoversDirectory(context)
            // 先删除该书之前的自定义封面
            coversDir.listFiles()?.filter { it.name.startsWith("custom_${bookId}_") }?.forEach { it.delete() }
            // 用时间戳命名，避免与原始封面冲突
            val file = File(coversDir, "custom_${bookId}_${System.currentTimeMillis()}.jpg")
            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 删除书本的自定义封面文件
     */
    fun deleteCustomCover(context: Context, bookId: String) {
        val coversDir = getCoversDirectory(context)
        coversDir.listFiles()?.filter { it.name.startsWith("custom_${bookId}_") }?.forEach { it.delete() }
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
