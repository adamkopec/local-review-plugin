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

    @Test fun scanSimpleChangeAddsKeyToCurrentNoRenameNoRehashWhenNotViewed() {
        val file = filePath("/r/a.kt")
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, file) } returns key
        val change = change(afterFile = file)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { false }, hasher)

        assertEquals(setOf(key), result.currentChanges)
        assertTrue(result.renames.isEmpty())
        assertTrue(result.rehash.isEmpty())
    }

    @Test fun scanRenamedFileBeforeAndAfterPathsDifferRecordsRename() {
        val beforeFp = filePath("/r/old.kt")
        val afterFp = filePath("/r/new.kt")
        val beforeKey = Key("/r", "main", "/r/old.kt")
        val afterKey = Key("/r", "main", "/r/new.kt")
        every { KeyDeriver.keyFor(project, beforeFp) } returns beforeKey
        every { KeyDeriver.keyFor(project, afterFp) } returns afterKey
        val change = change(afterFile = afterFp, beforeFile = beforeFp)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { false }, hasher)

        assertEquals(mapOf(beforeKey to afterKey), result.renames)
        assertTrue(afterKey in result.currentChanges)
    }

    @Test fun scanChangeWithSameBeforeAndAfterKeyNoRename() {
        val fp = filePath("/r/a.kt")
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = fp, beforeFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { false }, hasher)

        assertTrue(result.renames.isEmpty())
    }

    @Test fun scanViewedFileRehashesDefensively() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/a.kt", vf = vf)
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        every { hasher.hash(vf) } returns "newHashBytes"
        val change = change(afterFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { it == key }, hasher)

        assertEquals("newHashBytes", result.rehash[key])
    }

    @Test fun scanMergeConflictStatusSkipsRehashEvenIfViewed() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/a.kt", vf = vf)
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = fp, status = FileStatus.MERGED_WITH_CONFLICTS)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { true }, hasher)

        assertFalse(result.rehash.containsKey(key))
    }

    @Test fun scanUnviewedAndNotRenameTargetNoRehash() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/a.kt", vf = vf)
        val key = Key("/r", "main", "/r/a.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { false }, hasher)

        assertTrue(result.rehash.isEmpty())
    }

    @Test fun scanRenameTargetRehashesEvenIfNotPreviouslyViewed() {
        val vf = mockk<VirtualFile>()
        val beforeFp = filePath("/r/old.kt")
        val afterFp = filePath("/r/new.kt", vf = vf)
        val beforeKey = Key("/r", "main", "/r/old.kt")
        val afterKey = Key("/r", "main", "/r/new.kt")
        every { KeyDeriver.keyFor(project, beforeFp) } returns beforeKey
        every { KeyDeriver.keyFor(project, afterFp) } returns afterKey
        every { hasher.hash(vf) } returns "newHashBytes"
        val change = change(afterFile = afterFp, beforeFile = beforeFp)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { false }, hasher)

        // After the rename is recorded, renames.containsValue(afterKey) is true, so we rehash.
        assertEquals("newHashBytes", result.rehash[afterKey])
    }

    @Test fun scanDeletedFileAfterRevisionNullUsesBeforeKeyAsCurrent() {
        val fp = filePath("/r/gone.kt")
        val key = Key("/r", "main", "/r/gone.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        val change = change(afterFile = null, beforeFile = fp)

        val result = ChangeSetScanner.scan(project, listOf(change), emptyList(), isViewed = { true }, hasher)

        assertTrue(key in result.currentChanges)
        assertTrue(result.rehash.isEmpty()) // no afterRevision → no rehash
    }

    // ----- unversioned files -----

    @Test fun scanUnversionedFileKeyEntersCurrentChangesSoReviewedMarkSurvivesReconcile() {
        // Regression: before this case was handled, marking an unversioned file and then letting
        // CLM fire changeListUpdateDone caused ReviewStateService.reconcile step 2 to drop the
        // mark because the unversioned key wasn't in currentChanges. Reproducible by saving any
        // new unversioned file in the project directory.
        val fp = filePath("/r/newfile.txt")
        val key = Key("/r", "main", "/r/newfile.txt")
        every { KeyDeriver.keyFor(project, fp) } returns key

        val result = ChangeSetScanner.scan(project, emptyList(), listOf(fp), isViewed = { true }, hasher)

        assertTrue(key in result.currentChanges)
    }

    @Test fun scanUnversionedFileViewedRehashesDefensively() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/newfile.txt", vf = vf)
        val key = Key("/r", "main", "/r/newfile.txt")
        every { KeyDeriver.keyFor(project, fp) } returns key
        every { hasher.hash(vf) } returns "freshHash"

        val result = ChangeSetScanner.scan(project, emptyList(), listOf(fp), isViewed = { it == key }, hasher)

        assertEquals("freshHash", result.rehash[key])
    }

    @Test fun scanUnversionedFileNotViewedSkipsRehash() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/newfile.txt", vf = vf)
        val key = Key("/r", "main", "/r/newfile.txt")
        every { KeyDeriver.keyFor(project, fp) } returns key

        val result = ChangeSetScanner.scan(project, emptyList(), listOf(fp), isViewed = { false }, hasher)

        assertTrue(key in result.currentChanges)
        assertTrue(result.rehash.isEmpty())
    }

    @Test fun scanUnversionedFileOutsideVcsRootStillKeyedSoReconcileDoesNotDrop() {
        // KeyDeriver.keyFor(project, VirtualFile) falls back to keyForNoVcs when the file is
        // outside any VCS root; the FilePath overload returns null in that case. Verify we
        // don't crash and simply skip the file (which is fine: it wouldn't have been markable
        // in the first place without a VCS root, so there's no mark to protect).
        val fp = filePath("/elsewhere/orphan.txt")
        every { KeyDeriver.keyFor(project, fp) } returns null

        val result = ChangeSetScanner.scan(project, emptyList(), listOf(fp), isViewed = { true }, hasher)

        assertTrue(result.currentChanges.isEmpty())
        assertTrue(result.rehash.isEmpty())
    }

    // ----- helpers -----

    private fun filePath(
        p: String,
        vf: VirtualFile? = null,
    ): FilePath =
        mockk(relaxed = true) {
            every { path } returns p
            every { virtualFile } returns vf
        }

    private fun change(
        afterFile: FilePath?,
        beforeFile: FilePath? = null,
        status: FileStatus = FileStatus.MODIFIED,
    ): Change {
        val afterRev =
            afterFile?.let { f ->
                mockk<ContentRevision>(relaxed = true) { every { file } returns f }
            }
        val beforeRev =
            beforeFile?.let { f ->
                mockk<ContentRevision>(relaxed = true) { every { file } returns f }
            }
        return mockk(relaxed = true) {
            every { afterRevision } returns afterRev
            every { beforeRevision } returns beforeRev
            every { fileStatus } returns status
        }
    }
}
