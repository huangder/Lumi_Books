package com.huangder.lumibooks.dictionary

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DictionaryPackageInstallerTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture(extra: String? = null): Pair<File, DictionaryDescriptor> {
        val dbFile = temporary.newFile("db-${System.nanoTime()}")
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.version = 1
            db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY,payload TEXT)")
            db.execSQL("CREATE TABLE aliases(key TEXT,entry_id TEXT)")
            db.execSQL("CREATE TABLE blocked_keys(hash TEXT)")
            db.execSQL("CREATE TABLE metadata(key TEXT,value TEXT)")
            db.execSQL("INSERT INTO metadata VALUES('id','test')")
            db.execSQL("INSERT INTO metadata VALUES('version','1')")
            db.execSQL("INSERT INTO metadata VALUES('filterVersion','1')")
            db.execSQL("INSERT INTO entries VALUES('1',?)", arrayOf("""{"id":"1","headword":"hello","senses":[{"id":"1","definition":"greeting"}]}"""))
        }
        val parts = linkedMapOf("dictionary.sqlite" to dbFile.readBytes(), "metadata.json" to
            JSONObject().put("formatVersion",1).put("id","test").put("version","1").put("filterVersion",1).put("disclaimerVersion",1).toString().toByteArray(),
            "licenses.txt" to "MIT\nCopyright fixture".toByteArray(), "sources.json" to "{}".toByteArray())
        if (extra != null) parts[extra] = "outside".toByteArray()
        val zip = temporary.newFile("archive-${System.nanoTime()}.zip")
        ZipOutputStream(zip.outputStream()).use { out -> parts.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() } }
        return zip to DictionaryDescriptor("test","Test","","1","fixture","https://example.org","MIT","fixture","format conversion","1",
            "https://github.com/huangder/Lumi_Books/releases/download/v1/test.zip",zip.length(),parts.values.sumOf { it.size.toLong() },sha256(zip.readBytes()),1,1)
    }
    @Test fun installsVerifiedDatabaseAndOfflineLicense() = runBlocking {
        val (zip, descriptor) = fixture()
        val stage = File(temporary.root,"stage")
        DictionaryPackageInstaller.extract(zip,stage,descriptor)
        assertTrue(File(stage,"dictionary.sqlite").isFile)
        assertTrue(File(stage,"licenses.txt").readText().contains("Copyright"))
    }
    @Test fun rejectsCorruptionAndTraversalWithoutWritingOutsideStage() = runBlocking {
        val (zip,d) = fixture()
        try { DictionaryPackageInstaller.extract(zip,File(temporary.root,"stage-a"),d.copy(sha256="0".repeat(64))); fail("accepted bad hash") }
        catch (_: IllegalArgumentException) { }
        val (evil,e) = fixture("../escaped")
        try { DictionaryPackageInstaller.extract(evil,File(temporary.root,"stage-b"),e); fail("accepted traversal") }
        catch (_: IllegalArgumentException) { }
        assertFalse(File(temporary.root,"escaped").exists())
    }
    @Test fun rejectsMismatchedVersionAndUncompressedSize() = runBlocking {
        val (zip,d) = fixture()
        try { DictionaryPackageInstaller.extract(zip,File(temporary.root,"stage-c"),d.copy(version="2")); fail("accepted wrong version") }
        catch (_: IllegalArgumentException) { }
        try { DictionaryPackageInstaller.extract(zip,File(temporary.root,"stage-d"),d.copy(installedBytes=10)); fail("accepted excess data") }
        catch (_: IllegalArgumentException) { }
    }
}
