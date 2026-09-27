package com.huangder.lumibooks.dictionary

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object DictionaryPackageInstaller {
    private val files = setOf("dictionary.sqlite", "metadata.json", "licenses.txt", "sources.json")

    suspend fun extract(archive: File, stage: File, descriptor: DictionaryDescriptor) {
        require(archive.length() == descriptor.sizeBytes) { "下载文件大小不一致，请重试" }
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == descriptor.sha256) { "词库校验失败，请重新下载" }
        require(stage.parentFile!!.usableSpace > descriptor.installedBytes + 32L * 1024 * 1024) { "可用存储空间不足" }
        require(stage.mkdirs() || stage.isDirectory)
        var total = 0L
        val seen = mutableSetOf<String>()
        ZipFile(archive).use { zip ->
            for (entry in zip.entries().asSequence()) {
                currentCoroutineContext().ensureActive()
                require(!entry.isDirectory && entry.name in files && seen.add(entry.name)) { "词库压缩包结构不正确" }
                val limit = if (entry.name == "dictionary.sqlite") descriptor.installedBytes else 2L * 1024 * 1024
                var written = 0L
                zip.getInputStream(entry).use { input ->
                    File(stage, entry.name).outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count; written += count
                            require(total <= descriptor.installedBytes && written <= limit) { "解压内容超出声明大小" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
        }
        require(seen == files && total == descriptor.installedBytes) { "词库包缺少文件或大小不匹配" }
        val metadata = JSONObject(File(stage, "metadata.json").readText())
        require(metadata.getInt("formatVersion") == 1 && metadata.getString("id") == descriptor.id &&
            metadata.getString("version") == descriptor.version && metadata.getInt("filterVersion") == descriptor.filterVersion &&
            metadata.getInt("disclaimerVersion") == descriptor.disclaimerVersion) { "词库版本不匹配" }
        require(File(stage, "licenses.txt").length() > 0) { "缺少词库许可" }
        SQLiteDatabase.openDatabase(File(stage, "dictionary.sqlite").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            require(db.version == 1) { "不支持的词库格式" }
            db.rawQuery("PRAGMA quick_check", null).use { require(it.moveToFirst() && it.getString(0) == "ok") }
            db.rawQuery("SELECT name,type FROM sqlite_master WHERE name IN ('entries','aliases','blocked_keys','metadata')", null).use {
                var count = 0
                while (it.moveToNext()) { require(it.getString(1) == "table"); count++ }
                require(count == 4)
            }
            for ((key, value) in mapOf("id" to descriptor.id, "version" to descriptor.version, "filterVersion" to descriptor.filterVersion.toString())) {
                db.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use {
                    require(it.moveToFirst() && it.getString(0) == value) { "数据库版本不匹配" }
                }
            }
            db.rawQuery("SELECT payload FROM entries LIMIT 1", null).use { require(it.moveToFirst()); DictionaryEntry.fromJson(JSONObject(it.getString(0))) }
            db.rawQuery("SELECT key,entry_id FROM aliases LIMIT 1", null).close()
            db.rawQuery("SELECT hash FROM blocked_keys LIMIT 1", null).close()
        }
    }
}
