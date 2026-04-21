package com.localreview.startup

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.localreview.diagnostics.Logging
import com.localreview.hash.ContentHasher
import com.localreview.settings.LocalReviewSettings
import com.localreview.state.ReviewStateService
import com.localreview.vcs.KeyDeriver

class ReviewStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (project.isDisposed) return
        Logging.trace { "Startup for project ${project.name}" }
        // Register the AsyncFileListener once per JVM. AsyncFileListener is NOT a message-bus
        // topic, so it can't be declared in <applicationListeners> — must be added via
        // VirtualFileManager.addAsyncFileListener.
        com.localreview.vfs.ContentChangeListener.ensureInstalled()
        // Register a global DocumentListener so in-editor edits drop the mark immediately,
        // without waiting for a save to flush to VFS.
        com.localreview.vfs.DocumentInvalidationListener.ensureInstalled()
        // Defer reconcile to a pool thread; ChangeSetListener will run its own reconcile on the
        // first CLM update, so we only need to capture anything stored from a prior session.
        AppExecutorUtil.getAppExecutorService().submit {
            reconcileInitial(project)
        }
    }

    private fun reconcileInitial(project: Project) {
        if (project.isDisposed) return
        val service = ReviewStateService.getInstance(project)
        val hasher = ContentHasher.getInstance()

        val current = mutableSetOf<com.localreview.state.Key>()
        val rehash = mutableMapOf<com.localreview.state.Key, String>()

        ReadAction.run<RuntimeException> {
            if (project.isDisposed) return@run
            val clm = ChangeListManager.getInstance(project)
            for (change in clm.allChanges) {
                val file = change.afterRevision?.file ?: change.beforeRevision?.file ?: continue
                val key = KeyDeriver.keyFor(project, file) ?: continue
                current.add(key)
                if (service.isViewed(key)) {
                    val vf = file.virtualFile ?: continue
                    val hex = hasher.hash(vf) ?: continue
                    rehash[key] = hex
                }
            }
        }

        val settings = LocalReviewSettings.getInstance().current()
        service.reconcile(
            currentChanges = current,
            renames = emptyMap(),
            rehashedContent = rehash,
            settings = settings,
        )
        Logging.trace { "Initial reconcile: ${service.size()} entries remain" }
    }
}
