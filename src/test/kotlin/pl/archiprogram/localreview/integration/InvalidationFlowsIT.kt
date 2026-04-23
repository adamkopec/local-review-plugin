package pl.archiprogram.localreview.integration

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.vcs.KeyDeriver
import pl.archiprogram.localreview.vfs.ContentChangeListener
import pl.archiprogram.localreview.vfs.DocumentInvalidationListener
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * End-to-end regression guard for the plugin's core promise: an edit to a reviewed file drops
 * its mark, whether the edit flows through the in-memory Document or through a VFS save.
 *
 * These run headlessly under [BasePlatformTestCase] using the real platform (EditorFactory,
 * VirtualFileManager, ChangeListManager) — no mocked UI, no mocked VCS, no mocked listeners.
 * The async hashing pool is swapped for a synchronous executor so assertions are deterministic.
 */
class InvalidationFlowsIT : BasePlatformTestCase() {
    private lateinit var service: ReviewStateService
    private lateinit var hasher: ContentHasher

    override fun setUp() {
        super.setUp()
        service = ReviewStateService.getInstance(project)
        service.clearAll()
        hasher = ContentHasher.getInstance()
        // Install the listeners (no-ops after first install — each has an AtomicBoolean guard).
        DocumentInvalidationListener.ensureInstalled()
        ContentChangeListener.ensureInstalled()
        ContentChangeListener.setTestExecutor(DirectExecutorService())
    }

    override fun tearDown() {
        try {
            ContentChangeListener.setTestExecutor(null)
            service.clearAll()
        } finally {
            super.tearDown()
        }
    }

    fun testDocumentEditDropsViewedMark() {
        val file = myFixture.addFileToProject("src/Foo.kt", "original content").virtualFile
        val key = KeyDeriver.keyFor(project, file)!!
        service.mark(key, hasher.hash(file)!!)
        assertTrue("file should be marked after mark()", service.isViewed(key))

        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file)!!
            doc.insertString(0, "edit ")
        }

        assertFalse(
            "DocumentInvalidationListener must drop the mark on any document edit",
            service.isViewed(key),
        )
    }

    fun testVfsSaveWithDifferentContentDropsViewedMark() {
        val file = myFixture.addFileToProject("src/Bar.kt", "original").virtualFile
        val key = KeyDeriver.keyFor(project, file)!!
        service.mark(key, hasher.hash(file)!!)
        // Ensure the document doesn't short-circuit via DocumentInvalidationListener — we want
        // to prove the VFS path works on its own. Drop the mark via document-less file edit.
        // (We overwrite bytes directly; no document exists yet because the file isn't open.)

        WriteAction.runAndWait<Exception> {
            file.setBinaryContent("mutated".toByteArray())
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertFalse(
            "ContentChangeListener must drop the mark when VFS content hash differs",
            service.isViewed(key),
        )
    }

    fun testVfsSaveWithIdenticalContentKeepsViewedMark() {
        val file = myFixture.addFileToProject("src/Baz.kt", "original").virtualFile
        val key = KeyDeriver.keyFor(project, file)!!
        val originalHash = hasher.hash(file)!!
        service.mark(key, originalHash)

        // Rewrite identical bytes — VFS fires a content event but the hash matches.
        WriteAction.runAndWait<Exception> {
            file.setBinaryContent("original".toByteArray())
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue(
            "Mark must survive a VFS save that produced the same bytes",
            service.isViewed(key),
        )
    }

    fun testUnmarkedFileVfsEditNoFalsePositive() {
        // A file that was never marked should not become spuriously "marked" by any listener.
        val file = myFixture.addFileToProject("src/Qux.kt", "first").virtualFile
        val key = KeyDeriver.keyFor(project, file)!!
        assertFalse(service.isViewed(key))

        WriteAction.runAndWait<Exception> {
            file.setBinaryContent("second".toByteArray())
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertFalse(service.isViewed(key))
        assertEquals(0, service.size())
    }

    fun testDocumentEditAfterUnmarkDoesNotCrash() {
        // Sanity: listener must be tolerant of edits to files it has no entry for.
        val file = myFixture.addFileToProject("src/Qux2.kt", "a").virtualFile
        val key = KeyDeriver.keyFor(project, file)!!
        service.mark(key, hasher.hash(file)!!)
        service.unmark(key)
        assertFalse(service.isViewed(key))

        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file)!!
            doc.insertString(0, "x")
        }

        assertFalse(service.isViewed(key))
    }

    /** Executor that runs every submitted task synchronously on the caller thread. */
    private class DirectExecutorService : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {}

        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()

        override fun isShutdown(): Boolean = false

        override fun isTerminated(): Boolean = false

        override fun awaitTermination(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = true
    }
}
