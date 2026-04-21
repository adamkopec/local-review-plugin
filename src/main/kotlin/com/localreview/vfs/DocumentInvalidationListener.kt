package com.localreview.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.localreview.state.ReviewStateService
import com.localreview.ui.SafeRefresh
import com.localreview.vcs.KeyDeriver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drops the reviewed mark immediately when a document is modified, without waiting for a save.
 *
 * [ContentChangeListener] watches the VFS, but VFS events only fire after a save (autosave, blur,
 * etc.). For in-editor typing we need a finer-grained hook. Any document change on a marked file
 * drops the mark — no hash recheck, no debouncing: the simplest policy, and it matches what a
 * reviewer expects ("I touched it, it's unreviewed again"). This also aligns with the already-
 * locked-in decision that "mark doesn't resurrect on revert."
 */
class DocumentInvalidationListener : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (!file.isValid || file.isInLocalFileSystem.not()) return

        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val key = KeyDeriver.keyFor(project, file) ?: continue
            val service = ReviewStateService.getInstance(project)
            if (!service.isViewed(key)) continue
            LOG.info("LocalReview: document edit dropped mark key=$key")
            service.unmark(key)
            SafeRefresh.refreshFileStatuses(project)
            SafeRefresh.scheduleChangesViewRefresh(project)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(DocumentInvalidationListener::class.java)
        private val installed = AtomicBoolean(false)

        fun ensureInstalled() {
            if (installed.compareAndSet(false, true)) {
                com.intellij.openapi.editor.EditorFactory.getInstance()
                    .eventMulticaster
                    .addDocumentListener(
                        DocumentInvalidationListener(),
                        ApplicationManager.getApplication(),
                    )
                LOG.info("LocalReview: DocumentListener registered")
            }
        }
    }
}
