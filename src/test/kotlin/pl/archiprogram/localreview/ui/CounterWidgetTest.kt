package pl.archiprogram.localreview.ui

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pl.archiprogram.localreview.LocalReviewBundle
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.vcs.KeyDeriver

/**
 * Exercises [CounterWidget.getText] through the real [ReviewBreakdown] pipeline, stubbing
 * only the platform boundaries. Guards the regression where the status bar and the
 * tool-window "Reviewed N of M" group disagreed because only the tool window counted
 * unversioned files.
 */
class CounterWidgetTest {

    private val project: Project = mockk(relaxed = true)
    private val service: ReviewStateService = mockk(relaxed = true)
    private val clm: ChangeListManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { project.isDisposed } returns false

        mockkObject(ReviewStateService.Companion)
        every { ReviewStateService.getInstance(project) } returns service

        mockkStatic(ChangeListManager::class)
        every { ChangeListManager.getInstance(project) } returns clm

        mockkObject(KeyDeriver)

        mockkObject(LocalReviewBundle)
        every { LocalReviewBundle.message("widget.counter.empty") } returns "No changes"

        every { service.isViewed(any()) } returns false
        every { clm.allChanges } returns emptyList()
        every { clm.unversionedFilesPaths } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun getText_emptyProject_showsEmptyMessage() {
        assertEquals("No changes", CounterWidget(project).getText())
    }

    @Test fun getText_trackedOnly_usesTrackedChanges() {
        val k = Key("/r", "main", "/r/a.kt")
        val fp = filePath("/r/a.kt")
        val ch = change(afterFile = fp)
        every { KeyDeriver.keyFor(project, fp) } returns k
        every { clm.allChanges } returns listOf(ch)
        every { service.isViewed(k) } returns false

        assertEquals("Reviewed 0/1", CounterWidget(project).getText())
    }

    /** The regression: one unversioned file, marked viewed. Previously the widget said
     *  "No changes" because it only looked at `clm.allChanges`. */
    @Test fun getText_unversionedFileMarkedViewed_isCounted() {
        val k = Key("/r", "main", "/r/new.kt")
        val vf = mockk<VirtualFile>()
        val fp = filePath("/r/new.kt", vf = vf)
        every { KeyDeriver.keyFor(project, fp) } returns k
        every { clm.unversionedFilesPaths } returns listOf(fp)
        every { service.isViewed(k) } returns true

        assertEquals("Reviewed 1/1", CounterWidget(project).getText())
    }

    /** Mirrors the user-reported screenshot: one tracked unviewed + one unversioned viewed
     *  should say "Reviewed 1/2", matching the tool window's "1 of 2". Before the fix the
     *  widget reported "Reviewed 0/1" because unversioned files were invisible to it. */
    @Test fun getText_oneTrackedUnviewedPlusOneUnversionedViewed_reports1of2() {
        val kTracked = Key("/r", "main", "/r/tracked.kt")
        val kUnver = Key("/r", "main", "/r/new.kt")
        val fpTracked = filePath("/r/tracked.kt")
        val vfUnver = mockk<VirtualFile>()
        val fpUnver = filePath("/r/new.kt", vf = vfUnver)
        val chTracked = change(afterFile = fpTracked)
        every { KeyDeriver.keyFor(project, fpTracked) } returns kTracked
        every { KeyDeriver.keyFor(project, fpUnver) } returns kUnver
        every { clm.allChanges } returns listOf(chTracked)
        every { clm.unversionedFilesPaths } returns listOf(fpUnver)
        every { service.isViewed(kTracked) } returns false
        every { service.isViewed(kUnver) } returns true

        assertEquals("Reviewed 1/2", CounterWidget(project).getText())
    }

    @Test fun getText_disposedProject_reportsEmpty() {
        every { project.isDisposed } returns true

        assertEquals("No changes", CounterWidget(project).getText())
    }

    // ----- helpers -----

    private fun filePath(p: String, vf: VirtualFile? = null): FilePath = mockk(relaxed = true) {
        every { path } returns p
        every { virtualFile } returns vf
    }

    private fun change(afterFile: FilePath?, beforeFile: FilePath? = null): Change {
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
