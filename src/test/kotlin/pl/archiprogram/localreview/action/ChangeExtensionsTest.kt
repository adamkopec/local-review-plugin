package pl.archiprogram.localreview.action

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.archiprogram.localreview.hash.ContentHasher

/**
 * Unit tests for the file-level extensions [hashAfter] and (indirectly) [key] shared by the
 * action classes and the MCP toolset.
 *
 * Most of the coverage for `key` lives in [TargetCollectorTest]; here we focus on the
 * deletion path of `hashAfter`, which previously returned null and caused every caller to
 * silently skip deleted files.
 */
class ChangeExtensionsTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun hashAfter_deletion_returns_sentinel() {
        // A deletion has beforeRevision != null, afterRevision == null.
        val beforeFile = mockk<FilePath>(relaxed = true)
        val beforeRev = mockk<ContentRevision>(relaxed = true) {
            every { file } returns beforeFile
        }
        val change = mockk<Change>(relaxed = true) {
            every { afterRevision } returns null
            every { beforeRevision } returns beforeRev
        }

        assertEquals(DELETED_HASH, change.hashAfter())
    }

    @Test fun hashAfter_neither_revision_returns_null() {
        // Degenerate: no before, no after — not a real Change, but defensive.
        val change = mockk<Change>(relaxed = true) {
            every { afterRevision } returns null
            every { beforeRevision } returns null
        }

        assertNull(change.hashAfter())
    }

    @Test fun hashAfter_modification_hashes_the_after_virtualfile() {
        val vf = mockk<VirtualFile>(relaxed = true)
        val afterFile = mockk<FilePath>(relaxed = true) { every { virtualFile } returns vf }
        val afterRev = mockk<ContentRevision>(relaxed = true) { every { file } returns afterFile }
        val change = mockk<Change>(relaxed = true) {
            every { afterRevision } returns afterRev
            every { beforeRevision } returns null
        }
        val hasher = mockk<ContentHasher>(relaxed = true) {
            every { hash(vf) } returns "deadbeef"
        }
        mockkObject(ContentHasher.Companion)
        every { ContentHasher.getInstance() } returns hasher

        assertEquals("deadbeef", change.hashAfter())
    }

    @Test fun hashAfter_modification_without_virtualfile_returns_null() {
        // e.g. a pending delete+recreate where the VFS hasn't caught up yet.
        val afterFile = mockk<FilePath>(relaxed = true) { every { virtualFile } returns null }
        val afterRev = mockk<ContentRevision>(relaxed = true) { every { file } returns afterFile }
        val change = mockk<Change>(relaxed = true) {
            every { afterRevision } returns afterRev
            every { beforeRevision } returns null
        }

        assertNull(change.hashAfter())
    }
}