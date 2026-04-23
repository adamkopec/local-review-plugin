package pl.archiprogram.localreview.mcp

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pl.archiprogram.localreview.action.hashAfter
import pl.archiprogram.localreview.action.key
import pl.archiprogram.localreview.settings.LocalReviewSettings
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewState

/**
 * Unit tests for the plain-Kotlin logic functions shared by [LocalReviewToolset]'s suspend
 * entry points. These avoid coroutine + ReadAction machinery by testing the functions directly.
 *
 * Uses [FakeReviewState] (not `mockk<ReviewStateService>`) because mockk's inline instrumentation
 * collides with the coroutines-debug javaagent the IntelliJ test framework attaches on 2025.2+.
 *
 * Change.key(Project) and Change.hashAfter() are internal extension functions declared in
 * `pl.archiprogram.localreview.action.ToggleViewedAction.kt`; we mock their file-level generated
 * class so Change mocks report known Keys/hashes.
 */
class LocalReviewMcpLogicTest {

    private val project: Project = mockk(relaxed = true)
    private val state = FakeReviewState()
    private val settings: LocalReviewSettings = mockk(relaxed = true)
    private val clm: ChangeListManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { project.isDisposed } returns false

        mockkObject(LocalReviewSettings.Companion)
        every { LocalReviewSettings.getInstance() } returns settings

        mockkStatic("pl.archiprogram.localreview.action.ToggleViewedActionKt")
        mockkObject(PathResolver)
    }

    @After
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

    @Test fun listChanges_returns_entries_with_viewed_flags_and_status_labels() {
        val k1 = Key("/r", "main", "/r/a.kt")
        val k2 = Key("/r", "main", "/r/b.kt")
        val c1 = change(k1, FileStatus.MODIFIED)
        val c2 = change(k2, FileStatus.ADDED)
        every { clm.allChanges } returns listOf(c1, c2)
        state.viewed += k1

        val out = listChanges(project, state, clm)

        assertEquals(
            listOf(
                ChangeEntry(path = "/r/a.kt", viewed = true, status = "MODIFIED"),
                ChangeEntry(path = "/r/b.kt", viewed = false, status = "NEW"),
            ),
            out,
        )
    }

    @Test fun listChanges_returns_empty_for_disposed_project() {
        every { project.isDisposed } returns true
        assertEquals(emptyList<ChangeEntry>(), listChanges(project, state, clm))
    }

    @Test fun listChanges_skips_changes_without_derivable_key() {
        val k1 = Key("/r", "main", "/r/a.kt")
        val c1 = change(k1, FileStatus.MODIFIED)
        val orphan = mockk<Change>(relaxed = true) {
            every { fileStatus } returns FileStatus.MODIFIED
        }
        every { orphan.key(project) } returns null
        every { clm.allChanges } returns listOf(c1, orphan)

        val out = listChanges(project, state, clm)
        assertEquals(1, out.size)
    }

    // ---------------------------------------------------------------------
    // markAllViewed
    // ---------------------------------------------------------------------

    @Test fun markAllViewed_marks_only_unviewed_files_and_returns_count() {
        val k1 = Key("/r", "main", "/r/a.kt")
        val k2 = Key("/r", "main", "/r/b.kt")
        val k3 = Key("/r", "main", "/r/c.kt")
        val c1 = change(k1, FileStatus.MODIFIED, hash = "h1")
        val c2 = change(k2, FileStatus.MODIFIED, hash = "h2")
        val c3 = change(k3, FileStatus.ADDED, hash = "h3")
        every { clm.allChanges } returns listOf(c1, c2, c3)
        state.viewed += k2

        val marked = markAllViewed(project, state, clm)

        assertEquals(2, marked)
        assertEquals(
            listOf(k1 to "h1", k3 to "h3"),
            state.marks.map { it.first to it.second },
        )
    }

    @Test fun markAllViewed_skips_merge_conflict_files() {
        val k1 = Key("/r", "main", "/r/a.kt")
        val c1 = change(k1, FileStatus.MERGED_WITH_CONFLICTS, hash = "h1")
        every { clm.allChanges } returns listOf(c1)

        assertEquals(0, markAllViewed(project, state, clm))
        assertEquals(emptyList<Pair<Key, String>>(), state.marks.map { it.first to it.second })
    }

    @Test fun markAllViewed_skips_changes_whose_hash_cant_be_computed() {
        val k1 = Key("/r", "main", "/r/a.kt")
        val c1 = change(k1, FileStatus.MODIFIED, hash = null)
        every { clm.allChanges } returns listOf(c1)

        assertEquals(0, markAllViewed(project, state, clm))
        assertEquals(emptyList<Pair<Key, String>>(), state.marks.map { it.first to it.second })
    }

    @Test fun markAllViewed_marks_deleted_files_with_the_deletion_sentinel() {
        // Regression: deletions have no afterRevision, so hashAfter returns DELETED_HASH
        // (a sentinel) rather than null — without this, bulk-mark silently skipped deletes.
        val k1 = Key("/r", "main", "/r/gone.kt")
        val c1 = change(k1, FileStatus.DELETED, hash = pl.archiprogram.localreview.action.DELETED_HASH)
        every { clm.allChanges } returns listOf(c1)

        assertEquals(1, markAllViewed(project, state, clm))
        assertEquals(
            listOf(k1 to pl.archiprogram.localreview.action.DELETED_HASH),
            state.marks.map { it.first to it.second },
        )
    }

    @Test fun markAllViewed_returns_zero_for_disposed_project() {
        every { project.isDisposed } returns true
        assertEquals(0, markAllViewed(project, state, clm))
    }

    // ---------------------------------------------------------------------
    // unmarkAll
    // ---------------------------------------------------------------------

    @Test fun unmarkAll_delegates_to_clearAll_and_returns_count() {
        state.clearAllReturn = 7
        assertEquals(7, unmarkAll(project, state))
        assertEquals(1, state.clearAllCount)
    }

    @Test fun unmarkAll_returns_zero_for_disposed_project() {
        every { project.isDisposed } returns true
        assertEquals(0, unmarkAll(project, state))
        assertEquals(0, state.clearAllCount)
    }

    // ---------------------------------------------------------------------
    // markFiles
    // ---------------------------------------------------------------------

    @Test fun markFiles_marks_file_in_current_changeset() {
        val key = Key("/r", "main", "/r/a.kt")
        val vf = mockk<VirtualFile>(relaxed = true)
        val c = change(key, FileStatus.MODIFIED, hash = "deadbeef")
        every { clm.allChanges } returns listOf(c)
        every { PathResolver.resolve(project, "/r/a.kt") } returns
            PathResolver.Outcome.Resolved(vf, key)

        val out = markFiles(project, listOf("/r/a.kt"), state, clm)

        assertEquals(listOf(PathResult("/r/a.kt", "marked")), out)
        assertEquals(listOf(key to "deadbeef"), state.marks.map { it.first to it.second })
    }

    @Test fun markFiles_reports_already_viewed_without_remarking() {
        val key = Key("/r", "main", "/r/a.kt")
        val vf = mockk<VirtualFile>(relaxed = true)
        val c = change(key, FileStatus.MODIFIED, hash = "deadbeef")
        every { clm.allChanges } returns listOf(c)
        every { PathResolver.resolve(project, "/r/a.kt") } returns
            PathResolver.Outcome.Resolved(vf, key)
        state.viewed += key

        assertEquals(
            listOf(PathResult("/r/a.kt", "already_viewed")),
            markFiles(project, listOf("/r/a.kt"), state, clm),
        )
        assertEquals(emptyList<Pair<Key, String>>(), state.marks.map { it.first to it.second })
    }

    @Test fun markFiles_reports_not_a_current_change_when_resolved_but_not_in_changeset() {
        val key = Key("/r", "main", "/r/a.kt")
        val vf = mockk<VirtualFile>(relaxed = true)
        every { clm.allChanges } returns emptyList()
        every { PathResolver.resolve(project, "/r/a.kt") } returns
            PathResolver.Outcome.Resolved(vf, key)

        assertEquals(
            listOf(PathResult("/r/a.kt", "not_a_current_change")),
            markFiles(project, listOf("/r/a.kt"), state, clm),
        )
        assertEquals(emptyList<Pair<Key, String>>(), state.marks.map { it.first to it.second })
    }

    @Test fun markFiles_propagates_path_resolver_failure_outcomes() {
        every { clm.allChanges } returns emptyList()
        every { PathResolver.resolve(project, "") } returns PathResolver.Outcome.BlankPath
        every { PathResolver.resolve(project, "/gone.kt") } returns PathResolver.Outcome.NotFound
        every { PathResolver.resolve(project, "/dir") } returns PathResolver.Outcome.IsDirectory
        every { PathResolver.resolve(project, "/ext.kt") } returns PathResolver.Outcome.OutsideVcs

        val out = markFiles(project, listOf("", "/gone.kt", "/dir", "/ext.kt"), state, clm)

        assertEquals(
            listOf(
                PathResult("", "blank_path"),
                PathResult("/gone.kt", "not_found"),
                PathResult("/dir", "is_directory"),
                PathResult("/ext.kt", "outside_vcs"),
            ),
            out,
        )
        assertEquals(emptyList<Pair<Key, String>>(), state.marks.map { it.first to it.second })
    }

    @Test fun markFiles_disposed_project_reports_project_disposed_per_path() {
        every { project.isDisposed } returns true
        val out = markFiles(project, listOf("/r/a.kt", "/r/b.kt"), state, clm)
        assertEquals(
            listOf(
                PathResult("/r/a.kt", "project_disposed"),
                PathResult("/r/b.kt", "project_disposed"),
            ),
            out,
        )
    }

    // ---------------------------------------------------------------------
    // unmarkFiles
    // ---------------------------------------------------------------------

    @Test fun unmarkFiles_unmarks_viewed_file() {
        val key = Key("/r", "main", "/r/a.kt")
        val vf = mockk<VirtualFile>(relaxed = true)
        every { PathResolver.resolve(project, "/r/a.kt") } returns
            PathResolver.Outcome.Resolved(vf, key)
        state.viewed += key

        assertEquals(
            listOf(PathResult("/r/a.kt", "unmarked")),
            unmarkFiles(project, listOf("/r/a.kt"), state),
        )
        assertEquals(listOf(key), state.unmarks)
    }

    @Test fun unmarkFiles_reports_not_viewed_when_key_had_no_mark() {
        val key = Key("/r", "main", "/r/a.kt")
        val vf = mockk<VirtualFile>(relaxed = true)
        every { PathResolver.resolve(project, "/r/a.kt") } returns
            PathResolver.Outcome.Resolved(vf, key)
        // key NOT in state.viewed → unmark returns false

        assertEquals(
            listOf(PathResult("/r/a.kt", "not_viewed")),
            unmarkFiles(project, listOf("/r/a.kt"), state),
        )
    }

    @Test fun unmarkFiles_allows_unmarking_files_no_longer_in_current_changeset() {
        val key = Key("/r", "main", "/r/gone.kt")
        val vf = mockk<VirtualFile>(relaxed = true)
        every { PathResolver.resolve(project, "/r/gone.kt") } returns
            PathResolver.Outcome.Resolved(vf, key)
        state.viewed += key

        unmarkFiles(project, listOf("/r/gone.kt"), state)

        assertEquals(listOf(key), state.unmarks)
    }

    @Test fun unmarkFiles_propagates_path_resolver_failure_outcomes() {
        every { PathResolver.resolve(project, "/gone.kt") } returns PathResolver.Outcome.NotFound
        every { PathResolver.resolve(project, "/dir") } returns PathResolver.Outcome.IsDirectory

        assertEquals(
            listOf(
                PathResult("/gone.kt", "not_found"),
                PathResult("/dir", "is_directory"),
            ),
            unmarkFiles(project, listOf("/gone.kt", "/dir"), state),
        )
        assertEquals(emptyList<Key>(), state.unmarks)
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

    /**
     * In-memory [ReviewState] that records every write so tests can assert directly on the
     * captured sequence without touching mockk matchers (whose inline instrumentation trips the
     * IDE's coroutines-debug javaagent on Kotlin final classes).
     */
    private class FakeReviewState : ReviewState {
        val viewed = mutableSetOf<Key>()
        val marks = mutableListOf<Triple<Key, String, Long>>()
        val unmarks = mutableListOf<Key>()
        var clearAllReturn = 0
        var clearAllCount = 0

        override fun isViewed(key: Key): Boolean = key in viewed

        override fun mark(key: Key, hashHex: String, now: Long) {
            marks.add(Triple(key, hashHex, now))
            viewed += key
        }

        override fun unmark(key: Key): Boolean {
            unmarks.add(key)
            return viewed.remove(key)
        }

        override fun clearAll(): Int {
            clearAllCount++
            viewed.clear()
            return clearAllReturn
        }
    }
}
