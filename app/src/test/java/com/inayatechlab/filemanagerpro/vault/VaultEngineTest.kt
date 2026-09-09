package com.inayatechlab.filemanagerpro.vault

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Core .fmpvault format tests: create/unlock/encrypt/restore/tamper (#20). */
class VaultEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fast() = 1000 // test iteration count (PBKDF2 speed)

    private fun seedSource(): File {
        val src = tmp.newFolder("source")
        File(src, "report.txt").writeText("secret report")
        val docs = File(src, "Docs")
        docs.mkdirs()
        File(docs, "photo.jpg").writeBytes(ByteArray(4096) { it.toByte() })
        File(src, ".hidden.cfg").writeText("dotfile too")
        return src
    }

    private fun itemsOf(src: File): List<VaultEngine.AddItem> =
        listOf(
            VaultEngine.AddItem("report.txt", File(src, "report.txt")),
            VaultEngine.AddItem("Docs/photo.jpg", File(src, "Docs/photo.jpg")),
            VaultEngine.AddItem(".hidden.cfg", File(src, ".hidden.cfg"))
        )

    @Test
    fun createUnlockAddRestore_roundtrip() = runBlocking {
        val vaultDir = File(tmp.newFolder("vaults"), "MyVault" + VaultFormat.EXTENSION)
        val src = seedSource()

        val session = VaultEngine.create(vaultDir, "correct horse", fast())
        assertEquals("MyVault", session.name)
        assertTrue(session.entriesSnapshot.isEmpty())
        session.close()

        // Wrong password must fail.
        try {
            VaultEngine.unlock(vaultDir, "wrong")
            fail("wrong password should not unlock")
        } catch (e: VaultCryptoException) {
            assertEquals(VaultCryptoException.Reason.CORRUPT, e.reason)
        }

        // Re-unlock and add files.
        val s2 = VaultEngine.unlock(vaultDir, "correct horse")
        val outcome = VaultEngine.addFiles(s2, itemsOf(src))
        assertEquals(3, outcome.added)
        assertTrue(outcome.errors.isEmpty())
        assertEquals(3, s2.entriesSnapshot.size)
        assertEquals(
            setOf("report.txt", "Docs/photo.jpg", ".hidden.cfg"),
            s2.entriesSnapshot.map { it.relPath }.toSet()
        )

        // Adding the same path again must be rejected (duplicate).
        val dup = VaultEngine.addFiles(s2, listOf(VaultEngine.AddItem("report.txt", File(src, "report.txt"))))
        assertEquals(0, dup.added)
        assertTrue(dup.errors.any { it.contains("already in vault") })

        // Decrypt to cache and compare contents.
        val cache = tmp.newFolder("cache")
        val decrypted = VaultEngine.decryptTo(s2, "Docs/photo.jpg", cache)!!
        assertEquals(4096, decrypted.length())
        val decryptedReport = VaultEngine.decryptTo(s2, "report.txt", cache)!!
        assertEquals("secret report", decryptedReport.readText())
        s2.close()
    }

    @Test
    fun restoreFiles_prefersOriginalFolder_andKeepsBothOnConflict() = runBlocking {
        val vaultDir = File(tmp.newFolder("vaults2"), "V" + VaultFormat.EXTENSION)
        val src = tmp.newFolder("origin")
        File(src, "a.txt").writeText("payload-a")

        val s = VaultEngine.create(vaultDir, "pw1234", fast())
        VaultEngine.addFiles(s, listOf(VaultEngine.AddItem("a.txt", File(src, "a.txt"))))
        s.close()

        // Original folder still exists -> restore lands next to it with a " (1)" suffix.
        val s2 = VaultEngine.unlock(vaultDir, "pw1234")
        val fallback = tmp.newFolder("fallback")
        val outcome = VaultEngine.restoreFiles(s2, null, fallback)
        assertTrue(outcome.errors.isEmpty())
        assertEquals(1, outcome.restored.size)
        assertTrue(File(src, "a (1).txt").exists())
        assertEquals("payload-a", File(src, "a (1).txt").readText())
        s2.close()
    }

    @Test
    fun restoreFiles_fallsBackWhenOriginalFolderIsGone() = runBlocking {
        val vaultDir = File(tmp.newFolder("vaults3"), "V" + VaultFormat.EXTENSION)
        val src = tmp.newFolder("gone")
        File(src, "b.bin").writeBytes(ByteArray(100) { 7 })
        val s = VaultEngine.create(vaultDir, "pw1234", fast())
        VaultEngine.addFiles(s, listOf(VaultEngine.AddItem("b.bin", File(src, "b.bin"))))
        s.close()

        src.deleteRecursively() // original folder removed

        val s2 = VaultEngine.unlock(vaultDir, "pw1234")
        val fallback = tmp.newFolder("fallback2")
        val outcome = VaultEngine.restoreFiles(s2, listOf("b.bin"), fallback)
        assertTrue(outcome.errors.isEmpty())
        val restored = File(fallback, "b.bin")
        assertTrue(restored.exists())
        assertEquals(100, restored.length())
        s2.close()
    }

    @Test
    fun tamperedBlob_failsAuthentication() = runBlocking {
        val vaultDir = File(tmp.newFolder("vaults4"), "V" + VaultFormat.EXTENSION)
        val src = tmp.newFolder("t")
        File(src, "x.txt").writeText("integrity matters")
        val s = VaultEngine.create(vaultDir, "pw1234", fast())
        val outcome = VaultEngine.addFiles(s, listOf(VaultEngine.AddItem("x.txt", File(src, "x.txt"))))
        val blobName = s.findEntry("x.txt")!!.blob
        s.close()

        // Flip bytes in the blob on disk.
        val blob = File(File(vaultDir, VaultFormat.BLOBS_DIR), blobName)
        val bytes = blob.readBytes()
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 0xFF).toByte()
        blob.writeBytes(bytes)

        val s2 = VaultEngine.unlock(vaultDir, "pw1234")
        try {
            VaultEngine.decryptTo(s2, "x.txt", tmp.newFolder("c"))
            fail("tampered blob must fail GCM authentication")
        } catch (e: VaultCryptoException) {
            assertEquals(VaultCryptoException.Reason.CORRUPT, e.reason)
        }
        s2.close()
    }

    @Test
    fun removeFiles_deletesBlobAndEntry() = runBlocking {
        val vaultDir = File(tmp.newFolder("vaults5"), "V" + VaultFormat.EXTENSION)
        val src = tmp.newFolder("r")
        File(src, "one.txt").writeText("1")
        File(src, "two.txt").writeText("2")
        val s = VaultEngine.create(vaultDir, "pw1234", fast())
        VaultEngine.addFiles(
            s,
            listOf(
                VaultEngine.AddItem("one.txt", File(src, "one.txt")),
                VaultEngine.AddItem("two.txt", File(src, "two.txt"))
            )
        )
        val blobOfOne = s.findEntry("one.txt")!!.blob

        val removed = VaultEngine.removeFiles(s, listOf("one.txt"))
        assertEquals(1, removed)
        assertEquals(listOf("two.txt"), s.entriesSnapshot.map { it.relPath })
        assertFalse(File(File(vaultDir, VaultFormat.BLOBS_DIR), blobOfOne).exists())
        assertTrue(File(File(vaultDir, VaultFormat.BLOBS_DIR), s.findEntry("two.txt")!!.blob).exists())
        s.close()
    }

    @Test
    fun shortPasswordAndDuplicateNameAreRejected() = runBlocking {
        val dir = File(tmp.newFolder("v"), "ShortVault" + VaultFormat.EXTENSION)
        try {
            VaultEngine.create(dir, "abc", fast())
            fail("short password must be rejected")
        } catch (e: VaultCryptoException) {
            assertEquals(VaultCryptoException.Reason.WRONG_PASSWORD, e.reason)
        }
        assertFalse(dir.exists())
    }

    @Test
    fun biometricBlob_isStoredAndCanBeCleared() = runBlocking {
        val dir = File(tmp.newFolder("v2"), "BioVault" + VaultFormat.EXTENSION)
        val s = VaultEngine.create(dir, "pw1234", fast(), bioB64 = "opaque-blob")
        assertTrue(s.hasBiometric)
        s.close()

        val meta = VaultEngine.metaOf(dir)
        assertNotNull(meta)
        assertEquals("opaque-blob", meta!!.bioB64)

        VaultEngine.setBiometricBlob(dir, null)
        assertNull(VaultEngine.metaOf(dir)?.bioB64)
    }

    @Test
    fun tooLargeFileIsRefusedWithoutSideEffects() = runBlocking {
        val dir = File(tmp.newFolder("v3"), "BigVault" + VaultFormat.EXTENSION)
        val src = tmp.newFolder("big")
        // A sparse-looking big file — allocation-free on most filesystems.
        val big = File(src, "huge.bin")
        java.io.RandomAccessFile(big, "rw").use { raf ->
            raf.setLength(VaultCrypto.MAX_FILE_BYTES + 1)
        }
        val s = VaultEngine.create(dir, "pw1234", fast())
        val outcome = VaultEngine.addFiles(s, listOf(VaultEngine.AddItem("huge.bin", big)))
        assertEquals(0, outcome.added)
        assertTrue(outcome.errors.isNotEmpty())
        assertTrue(s.entriesSnapshot.isEmpty())
        assertEquals(0, VaultEngine.vaultSize(dir))
        s.close()
    }
}
