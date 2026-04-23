package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.state.Key

class ChangeSetScannerTest {

    private val project: Project = mockk(relaxed = true)
    private val hasher: ContentHasher = mockk()

    @Before
    fun setUp() {
        mockkObject(KeyDeriver)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun scan_simpleChange_addsKeyToCurrent_noRename_noRehashWhenNotViewed() {
        val file = filePath("/r/a.kt")
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, file) } returns key
        val change = change(afterFile = file)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { false }, hasher)

        assertEquals(setOf(key), result.currentChanges)
        assertTrue(result.renames.isEmpty())
        assertTrue(result.rehash.isEmpty())
    }

    @Test fun scan_renamedFile_beforeAndAfterPathsDiffer_recordsRename() {
        val beforeFp = filePath("/r/old.kt")
        val afterFp = filePath("/r/new.kt")
        val beforeKey = Key("/r", "main", "/r/old.kt")
        val afterKey = Key("/r", "main", "/r/new.kt")
        every { KeyDeriver.keyFor(project, beforeFp) } returns beforeKey
        every { KeyDeriver.keyFor(project, afterFp) } returns afterKey
        val change = change(afterFile = afterFp, beforeFile = beforeFp)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { false }, hasher)

        assertEquals(mapOf(beforeKey to afterKey), result.renames)
        assertTrue(afterKey in result.currentChanges)
    }

    @Test fun scan_changeWithSameBeforeAndAfterKey_noRename() {
        val fp = filePath("/r/a.kt")
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = fp, beforeFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { false }, hasher)

        assertTrue(result.renames.isEmpty())
    }

    @Test fun scan_viewedFile_rehashesDefensively() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/a.kt", vf = vf)
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        every { hasher.hash(vf) } returns "newHashBytes"
        val change = change(afterFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { it == key }, hasher)

        assertEquals("newHashBytes", result.rehash[key])
    }

    @Test fun scan_mergeConflictStatus_skipsRehashEvenIfViewed() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/a.kt", vf = vf)
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = fp, status = FileStatus.MERGED_WITH_CONFLICTS)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { true }, hasher)

        assertFalse(result.rehash.containsKey(key))
    }

    @Test fun scan_unviewedAndNotRenameTarget_noRehash() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/a.kt", vf = vf)
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { false }, hasher)

        assertTrue(result.rehash.isEmpty())
    }

    @Test fun scan_renameTarget_rehashesEvenIfNotPreviouslyViewed() {
        val vf = mockk<VirtualFile>()
        val beforeFp = filePath("/r/old.kt")
        val afterFp = filePath("/r/new.kt", vf = vf)
        val beforeKey = Key("/r", "main", "/r/old.kt")
        val afterKey = Key("/r", "main", "/r/new.kt")
        every { KeyDeriver.keyFor(project, beforeFp) } returns beforeKey
        every { KeyDeriver.keyFor(project, afterFp) } returns afterKey
        every { hasher.hash(vf) } returns "newHashBytes"
        val change = change(afterFile = afterFp, beforeFile = beforeFp)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { false }, hasher)

        // After the rename is recorded, renames.containsValue(afterKey) is true, so we rehash.
        assertEquals("newHashBytes", result.rehash[afterKey])
    }

    @Test fun scan_deletedFile_afterRevisionNull_usesBeforeKeyAsCurrent() {
        val fp = filePath("/r/gone.kt")
        val key = Key("/r", "main", "/r/gone.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = null, beforeFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), isViewed = { true }, hasher)

        assertTrue(key in result.currentChanges)
        assertTrue(result.rehash.isEmpty()) // no afterRevision → no rehash
    }

    // ----- helpers -----

    private fun filePath(p: String, vf: VirtualFile? = null): FilePath = mockk(relaxed = true) {
        every { path } returns p
        every { virtualFile } returns vf
    }

    private fun change(
        afterFile: FilePath?,
        beforeFile: FilePath? = null,
        status: FileStatus = FileStatus.MODIFIED,
    ): Change {
        val afterRev = afterFile?.let { f ->
            mockk<ContentRevision>(relaxed = true) { every { file } returns f }
        }
        val beforeRev = beforeFile?.let { f ->
            mockk<ContentRevision>(relaxed = true) { every { file } returns f }
        }
        return mockk(relaxed = true) {
            every { afterRevision } returns afterRev
            every { beforeRevision } returns beforeRev
            every { fileStatus } returns status
        }
    }
}
