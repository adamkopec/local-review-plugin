package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewStateService

class ReviewBreakdownTest {

    private val project: Project = mockk(relaxed = true)
    private val service: ReviewStateService = mockk(relaxed = true)
    private val clm: ChangeListManager = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        every { project.isDisposed } returns false

        mockkObject(ReviewStateService.Companion)
        every { ReviewStateService.getInstance(project) } returns service

        mockkStatic(ChangeListManager::class)
        every { ChangeListManager.getInstance(project) } returns clm

        mockkObject(KeyDeriver)

        // Default: nothing viewed. Individual tests override.
        every { service.isViewed(any()) } returns false
        every { clm.allChanges } returns emptyList()
        every { clm.unversionedFilesPaths } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test fun compute_disposedProject_returnsEmptyWithoutTouchingCLM() {
        every { project.isDisposed } returns true

        val out = ReviewBreakdown.compute(project)

        assertSame(ReviewBreakdown.EMPTY, out)
        assertEquals(0, out.totalCount)
        verify(exactly = 0) { ChangeListManager.getInstance(project) }
    }

    @Test fun compute_noChanges_noUnversioned_allZero() {
        val out = ReviewBreakdown.compute(project)

        assertEquals(0, out.viewedCount)
        assertEquals(0, out.unviewedCount)
        assertEquals(0, out.totalCount)
    }

    @Test fun compute_trackedOnly_splitsViewedAndUnviewed() {
        val kA = Key("/r", "main", "/r/a.kt")
        val kB = Key("/r", "main", "/r/b.kt")
        val fpA = filePath("/r/a.kt")
        val fpB = filePath("/r/b.kt")
        val chA = change(afterFile = fpA)
        val chB = change(afterFile = fpB)
        every { KeyDeriver.keyFor(project, fpA) } returns kA
        every { KeyDeriver.keyFor(project, fpB) } returns kB
        every { clm.allChanges } returns listOf(chA, chB)
        every { service.isViewed(kA) } returns true
        every { service.isViewed(kB) } returns false

        val out = ReviewBreakdown.compute(project)

        assertEquals(listOf(chA), out.viewedChanges)
        assertEquals(listOf(chB), out.unviewedChanges)
        assertTrue(out.viewedUnversioned.isEmpty())
        assertTrue(out.unviewedUnversioned.isEmpty())
        assertEquals(1, out.viewedCount)
        assertEquals(1, out.unviewedCount)
        assertEquals(2, out.totalCount)
    }

    /** Regression: widget + tool window counted different sets, because only the modifier
     *  included unversioned files. Must be counted here too. */
    @Test fun compute_unversionedOnly_viewed_isReflectedInTotals() {
        val k = Key("/r", "main", "/r/new.kt")
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/new.kt", vf = vf)
        every { KeyDeriver.keyFor(project, fp) } returns k
        every { clm.unversionedFilesPaths } returns listOf(fp)
        every { service.isViewed(k) } returns true

        val out = ReviewBreakdown.compute(project)

        assertTrue(out.viewedChanges.isEmpty())
        assertTrue(out.unviewedChanges.isEmpty())
        assertEquals(listOf(vf), out.viewedUnversioned)
        assertTrue(out.unviewedUnversioned.isEmpty())
        assertEquals(1, out.viewedCount)
        assertEquals(0, out.unviewedCount)
        assertEquals(1, out.totalCount)
    }

    @Test fun compute_unversionedOnly_unviewed_countsInUnviewed() {
        val k = Key("/r", "main", "/r/new.kt")
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/new.kt", vf = vf)
        every { KeyDeriver.keyFor(project, fp) } returns k
        every { clm.unversionedFilesPaths } returns listOf(fp)
        every { service.isViewed(k) } returns false

        val out = ReviewBreakdown.compute(project)

        assertEquals(listOf(vf), out.unviewedUnversioned)
        assertEquals(1, out.unviewedCount)
        assertEquals(1, out.totalCount)
    }

    @Test fun compute_mixedTrackedAndUnversioned_combinesIntoTotals() {
        val kT1 = Key("/r", "main", "/r/t1.kt")
        val kT2 = Key("/r", "main", "/r/t2.kt")
        val kU1 = Key("/r", "main", "/r/u1.kt")
        val kU2 = Key("/r", "main", "/r/u2.kt")
        val fpT1 = filePath("/r/t1.kt")
        val fpT2 = filePath("/r/t2.kt")
        val vfU1 = mockk<VirtualFile>()
        val vfU2 = mockk<VirtualFile>()
        val fpU1 = filePath("/r/u1.kt", vf = vfU1)
        val fpU2 = filePath("/r/u2.kt", vf = vfU2)
        val chT1 = change(afterFile = fpT1)
        val chT2 = change(afterFile = fpT2)
        every { KeyDeriver.keyFor(project, fpT1) } returns kT1
        every { KeyDeriver.keyFor(project, fpT2) } returns kT2
        every { KeyDeriver.keyFor(project, fpU1) } returns kU1
        every { KeyDeriver.keyFor(project, fpU2) } returns kU2
        every { clm.allChanges } returns listOf(chT1, chT2)
        every { clm.unversionedFilesPaths } returns listOf(fpU1, fpU2)
        every { service.isViewed(kT1) } returns true
        every { service.isViewed(kT2) } returns false
        every { service.isViewed(kU1) } returns true
        every { service.isViewed(kU2) } returns false

        val out = ReviewBreakdown.compute(project)

        assertEquals(2, out.viewedCount)
        assertEquals(2, out.unviewedCount)
        assertEquals(4, out.totalCount)
        assertEquals(listOf(chT1), out.viewedChanges)
        assertEquals(listOf(chT2), out.unviewedChanges)
        assertEquals(listOf(vfU1), out.viewedUnversioned)
        assertEquals(listOf(vfU2), out.unviewedUnversioned)
    }

    /** Deletions: no afterRevision, so the loop falls back to beforeRevision.file. */
    @Test fun compute_deletedChange_usesBeforeRevision() {
        val k = Key("/r", "main", "/r/gone.kt")
        val fp = filePath("/r/gone.kt")
        val ch = change(afterFile = null, beforeFile = fp)
        every { KeyDeriver.keyFor(project, fp) } returns k
        every { clm.allChanges } returns listOf(ch)
        every { service.isViewed(k) } returns true

        val out = ReviewBreakdown.compute(project)

        assertEquals(listOf(ch), out.viewedChanges)
        assertEquals(1, out.viewedCount)
    }

    @Test fun compute_changeWithNoRevisions_isSkipped() {
        val ch = mockk<Change>(relaxed = true) {
            every { afterRevision } returns null
            every { beforeRevision } returns null
        }
        every { clm.allChanges } returns listOf(ch)

        val out = ReviewBreakdown.compute(project)

        assertEquals(0, out.totalCount)
    }

    @Test fun compute_changeWhoseKeyDoesNotDerive_isSkipped() {
        val fp = filePath("/outside/foo.kt")
        val ch = change(afterFile = fp)
        every { KeyDeriver.keyFor(project, fp) } returns null
        every { clm.allChanges } returns listOf(ch)

        val out = ReviewBreakdown.compute(project)

        assertEquals(0, out.totalCount)
    }

    @Test fun compute_unversionedFilePathWithNullVirtualFile_isSkipped() {
        val fp = filePath("/r/vanished.kt", vf = null)
        every { clm.unversionedFilesPaths } returns listOf(fp)

        val out = ReviewBreakdown.compute(project)

        assertEquals(0, out.totalCount)
        // Don't even ask the deriver — the VF check comes first.
        verify(exactly = 0) { KeyDeriver.keyFor(project, fp) }
    }

    @Test fun compute_unversionedFilePathWhoseKeyDoesNotDerive_isSkipped() {
        val vf = mockk<VirtualFile>()
        val fp = filePath("/outside/foo.kt", vf = vf)
        every { KeyDeriver.keyFor(project, fp) } returns null
        every { clm.unversionedFilesPaths } returns listOf(fp)

        val out = ReviewBreakdown.compute(project)

        assertEquals(0, out.totalCount)
    }

    // ----- helpers -----

    private fun filePath(p: String, vf: VirtualFile? = null): FilePath = mockk(relaxed = true) {
        every { path } returns p
        every { virtualFile } returns vf
    }

    private fun change(
        afterFile: FilePath?,
        beforeFile: FilePath? = null,
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
        }
    }
}
