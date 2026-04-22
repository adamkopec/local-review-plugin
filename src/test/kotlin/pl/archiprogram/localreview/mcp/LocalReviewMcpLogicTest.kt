package pl.archiprogram.localreview.mcp

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import pl.archiprogram.localreview.action.hashAfter
import pl.archiprogram.localreview.action.key
import pl.archiprogram.localreview.settings.LocalReviewSettings
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewStateService

/**
 * Unit tests for the plain-Kotlin logic functions shared by [LocalReviewToolset]'s suspend
 * entry points. These avoid coroutine + ReadAction machinery by testing the functions directly.
 *
 * Change.key(Project) and Change.hashAfter() are internal extension functions declared in
 * `pl.archiprogram.localreview.action.ToggleViewedAction.kt`. We mock their file-level generated
 * class so Change mocks report known Keys/hashes.
 */
class LocalReviewMcpLogicTest {

    private val project: Project = mockk(relaxed = true)
    private val service: ReviewStateService = mockk(relaxed = true)
    private val settings: LocalReviewSettings = mockk(relaxed = true)
    private val clm: ChangeListManager = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns application

        every { project.isDisposed } returns false

        mockkObject(LocalReviewSettings.Companion)
        every { LocalReviewSettings.getInstance() } returns settings
        mockkObject(ReviewStateService.Companion)
        every { ReviewStateService.getInstance(project) } returns service

        mockkStatic(ChangeListManager::class)
        every { ChangeListManager.getInstance(project) } returns clm

        mockkStatic("pl.archiprogram.localreview.action.ToggleViewedActionKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ---------------------------------------------------------------------
    // Gate
    // ---------------------------------------------------------------------

    @Test fun mcp_tools_enabled_reads_the_setting() {
        every { settings.current() } returns LocalReviewSettings.State(enableMcpTools = false)
        assertEquals(false, mcpToolsEnabled())

        every { settings.current() } returns LocalReviewSettings.State(enableMcpTools = true)
        assertEquals(true, mcpToolsEnabled())
    }

    // ---------------------------------------------------------------------
    // listChanges
    // ---------------------------------------------------------------------

    @Nested
    inner class ListChanges {

        @Test fun returns_entries_with_viewed_flags_and_status_labels() {
            val k1 = Key("/r", "main", "/r/a.kt")
            val k2 = Key("/r", "main", "/r/b.kt")
            val c1 = change(k1, FileStatus.MODIFIED)
            val c2 = change(k2, FileStatus.ADDED)
            every { clm.allChanges } returns listOf(c1, c2)
            every { service.isViewed(k1) } returns true
            every { service.isViewed(k2) } returns false

            val out = listChanges(project)

            assertEquals(
                listOf(
                    ChangeEntry(path = "/r/a.kt", viewed = true, status = "MODIFIED"),
                    ChangeEntry(path = "/r/b.kt", viewed = false, status = "NEW"),
                ),
                out,
            )
        }

        @Test fun returns_empty_for_disposed_project() {
            every { project.isDisposed } returns true
            assertEquals(emptyList<ChangeEntry>(), listChanges(project))
            verify(exactly = 0) { clm.allChanges }
        }

        @Test fun skips_changes_without_derivable_key() {
            val k1 = Key("/r", "main", "/r/a.kt")
            val c1 = change(k1, FileStatus.MODIFIED)
            val orphan = mockk<Change>(relaxed = true) {
                every { fileStatus } returns FileStatus.MODIFIED
            }
            every { orphan.key(project) } returns null
            every { clm.allChanges } returns listOf(c1, orphan)
            every { service.isViewed(k1) } returns false

            val out = listChanges(project)
            assertEquals(1, out.size)
        }
    }

    // ---------------------------------------------------------------------
    // markAllViewed
    // ---------------------------------------------------------------------

    @Nested
    inner class MarkAllViewed {

        @Test fun marks_only_unviewed_files_and_returns_count() {
            val k1 = Key("/r", "main", "/r/a.kt")
            val k2 = Key("/r", "main", "/r/b.kt")
            val k3 = Key("/r", "main", "/r/c.kt")
            val c1 = change(k1, FileStatus.MODIFIED, hash = "h1")
            val c2 = change(k2, FileStatus.MODIFIED, hash = "h2")
            val c3 = change(k3, FileStatus.ADDED, hash = "h3")
            every { clm.allChanges } returns listOf(c1, c2, c3)
            every { service.isViewed(k1) } returns false
            every { service.isViewed(k2) } returns true
            every { service.isViewed(k3) } returns false

            val marked = markAllViewed(project)

            assertEquals(2, marked)
            verify(exactly = 1) { service.mark(k1, "h1", any()) }
            verify(exactly = 0) { service.mark(k2, any(), any()) }
            verify(exactly = 1) { service.mark(k3, "h3", any()) }
        }

        @Test fun skips_merge_conflict_files() {
            val k1 = Key("/r", "main", "/r/a.kt")
            val c1 = change(k1, FileStatus.MERGED_WITH_CONFLICTS, hash = "h1")
            every { clm.allChanges } returns listOf(c1)
            every { service.isViewed(k1) } returns false

            assertEquals(0, markAllViewed(project))
            verify(exactly = 0) { service.mark(k1, any(), any()) }
        }

        @Test fun skips_changes_whose_hash_cant_be_computed() {
            val k1 = Key("/r", "main", "/r/a.kt")
            val c1 = change(k1, FileStatus.MODIFIED, hash = null)
            every { clm.allChanges } returns listOf(c1)
            every { service.isViewed(k1) } returns false

            assertEquals(0, markAllViewed(project))
            verify(exactly = 0) { service.mark(any(), any(), any()) }
        }

        @Test fun marks_deleted_files_with_the_deletion_sentinel() {
            // Regression: deletions have no afterRevision, so hashAfter returns DELETED_HASH
            // (a sentinel) rather than null — without this, bulk-mark silently skipped deletes.
            val k1 = Key("/r", "main", "/r/gone.kt")
            val c1 = change(k1, FileStatus.DELETED, hash = pl.archiprogram.localreview.action.DELETED_HASH)
            every { clm.allChanges } returns listOf(c1)
            every { service.isViewed(k1) } returns false

            assertEquals(1, markAllViewed(project))
            verify(exactly = 1) {
                service.mark(k1, pl.archiprogram.localreview.action.DELETED_HASH, any())
            }
        }

        @Test fun returns_zero_for_disposed_project() {
            every { project.isDisposed } returns true
            assertEquals(0, markAllViewed(project))
            verify(exactly = 0) { clm.allChanges }
        }
    }

    // ---------------------------------------------------------------------
    // unmarkAll
    // ---------------------------------------------------------------------

    @Nested
    inner class UnmarkAll {

        @Test fun delegates_to_clearAll_and_returns_count() {
            every { service.clearAll() } returns 7
            assertEquals(7, unmarkAll(project))
            verify(exactly = 1) { service.clearAll() }
        }

        @Test fun returns_zero_for_disposed_project() {
            every { project.isDisposed } returns true
            assertEquals(0, unmarkAll(project))
            verify(exactly = 0) { service.clearAll() }
        }
    }

    // ---------------------------------------------------------------------
    // markFiles
    // ---------------------------------------------------------------------

    @Nested
    inner class MarkFiles {

        @BeforeEach
        fun setUpResolver() {
            mockkObject(PathResolver)
        }

        @Test fun marks_file_in_current_changeset() {
            val key = Key("/r", "main", "/r/a.kt")
            val vf = mockk<VirtualFile>(relaxed = true)
            val c = change(key, FileStatus.MODIFIED, hash = "deadbeef")
            every { clm.allChanges } returns listOf(c)
            every { PathResolver.resolve(project, "/r/a.kt") } returns
                PathResolver.Outcome.Resolved(vf, key)
            every { service.isViewed(key) } returns false

            val out = markFiles(project, listOf("/r/a.kt"))

            assertEquals(listOf(PathResult("/r/a.kt", "marked")), out)
            verify(exactly = 1) { service.mark(key, "deadbeef", any()) }
        }

        @Test fun reports_already_viewed_without_remarking() {
            val key = Key("/r", "main", "/r/a.kt")
            val vf = mockk<VirtualFile>(relaxed = true)
            val c = change(key, FileStatus.MODIFIED, hash = "deadbeef")
            every { clm.allChanges } returns listOf(c)
            every { PathResolver.resolve(project, "/r/a.kt") } returns
                PathResolver.Outcome.Resolved(vf, key)
            every { service.isViewed(key) } returns true

            assertEquals(
                listOf(PathResult("/r/a.kt", "already_viewed")),
                markFiles(project, listOf("/r/a.kt")),
            )
            verify(exactly = 0) { service.mark(any(), any(), any()) }
        }

        @Test fun reports_not_a_current_change_when_resolved_but_not_in_changeset() {
            val key = Key("/r", "main", "/r/a.kt")
            val vf = mockk<VirtualFile>(relaxed = true)
            every { clm.allChanges } returns emptyList()
            every { PathResolver.resolve(project, "/r/a.kt") } returns
                PathResolver.Outcome.Resolved(vf, key)

            assertEquals(
                listOf(PathResult("/r/a.kt", "not_a_current_change")),
                markFiles(project, listOf("/r/a.kt")),
            )
            verify(exactly = 0) { service.mark(any(), any(), any()) }
        }

        @Test fun propagates_path_resolver_failure_outcomes() {
            every { clm.allChanges } returns emptyList()
            every { PathResolver.resolve(project, "") } returns PathResolver.Outcome.BlankPath
            every { PathResolver.resolve(project, "/gone.kt") } returns PathResolver.Outcome.NotFound
            every { PathResolver.resolve(project, "/dir") } returns PathResolver.Outcome.IsDirectory
            every { PathResolver.resolve(project, "/ext.kt") } returns PathResolver.Outcome.OutsideVcs

            val out = markFiles(project, listOf("", "/gone.kt", "/dir", "/ext.kt"))

            assertEquals(
                listOf(
                    PathResult("", "blank_path"),
                    PathResult("/gone.kt", "not_found"),
                    PathResult("/dir", "is_directory"),
                    PathResult("/ext.kt", "outside_vcs"),
                ),
                out,
            )
            verify(exactly = 0) { service.mark(any(), any(), any()) }
        }

        @Test fun disposed_project_reports_project_disposed_per_path() {
            every { project.isDisposed } returns true
            val out = markFiles(project, listOf("/r/a.kt", "/r/b.kt"))
            assertEquals(
                listOf(
                    PathResult("/r/a.kt", "project_disposed"),
                    PathResult("/r/b.kt", "project_disposed"),
                ),
                out,
            )
            verify(exactly = 0) { clm.allChanges }
        }
    }

    // ---------------------------------------------------------------------
    // unmarkFiles
    // ---------------------------------------------------------------------

    @Nested
    inner class UnmarkFiles {

        @BeforeEach
        fun setUpResolver() {
            mockkObject(PathResolver)
        }

        @Test fun unmarks_viewed_file() {
            val key = Key("/r", "main", "/r/a.kt")
            val vf = mockk<VirtualFile>(relaxed = true)
            every { PathResolver.resolve(project, "/r/a.kt") } returns
                PathResolver.Outcome.Resolved(vf, key)
            every { service.unmark(key) } returns true

            assertEquals(
                listOf(PathResult("/r/a.kt", "unmarked")),
                unmarkFiles(project, listOf("/r/a.kt")),
            )
            verify(exactly = 1) { service.unmark(key) }
        }

        @Test fun reports_not_viewed_when_key_had_no_mark() {
            val key = Key("/r", "main", "/r/a.kt")
            val vf = mockk<VirtualFile>(relaxed = true)
            every { PathResolver.resolve(project, "/r/a.kt") } returns
                PathResolver.Outcome.Resolved(vf, key)
            every { service.unmark(key) } returns false

            assertEquals(
                listOf(PathResult("/r/a.kt", "not_viewed")),
                unmarkFiles(project, listOf("/r/a.kt")),
            )
        }

        @Test fun allows_unmarking_files_no_longer_in_current_changeset() {
            val key = Key("/r", "main", "/r/gone.kt")
            val vf = mockk<VirtualFile>(relaxed = true)
            every { PathResolver.resolve(project, "/r/gone.kt") } returns
                PathResolver.Outcome.Resolved(vf, key)
            every { service.unmark(key) } returns true

            unmarkFiles(project, listOf("/r/gone.kt"))

            verify(exactly = 0) { clm.allChanges }
            verify(exactly = 1) { service.unmark(key) }
        }

        @Test fun propagates_path_resolver_failure_outcomes() {
            every { PathResolver.resolve(project, "/gone.kt") } returns PathResolver.Outcome.NotFound
            every { PathResolver.resolve(project, "/dir") } returns PathResolver.Outcome.IsDirectory

            assertEquals(
                listOf(
                    PathResult("/gone.kt", "not_found"),
                    PathResult("/dir", "is_directory"),
                ),
                unmarkFiles(project, listOf("/gone.kt", "/dir")),
            )
            verify(exactly = 0) { service.unmark(any()) }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun change(key: Key, status: FileStatus, hash: String? = null): Change {
        val fp = mockk<FilePath>(relaxed = true) { every { path } returns key.path }
        val rev = mockk<ContentRevision>(relaxed = true) { every { file } returns fp }
        val c = mockk<Change>(relaxed = true) {
            every { afterRevision } returns rev
            every { beforeRevision } returns null
            every { fileStatus } returns status
        }
        every { c.key(project) } returns key
        every { c.hashAfter() } returns hash
        return c
    }
}
