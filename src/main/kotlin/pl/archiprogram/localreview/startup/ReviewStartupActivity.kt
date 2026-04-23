package pl.archiprogram.localreview.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.util.concurrency.AppExecutorUtil
import pl.archiprogram.localreview.diagnostics.Logging
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.settings.LocalReviewSettings
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.ui.CounterWidgetFactory
import pl.archiprogram.localreview.vcs.ChangeSetScanner

class ReviewStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.isDisposed) return
        Logging.trace { "Startup for project ${project.name}" }
        // Register the AsyncFileListener once per JVM. AsyncFileListener is NOT a message-bus
        // topic, so it can't be declared in <applicationListeners> — must be added via
        // VirtualFileManager.addAsyncFileListener.
        pl.archiprogram.localreview.vfs.ContentChangeListener.ensureInstalled()
        // Register a global DocumentListener so in-editor edits drop the mark immediately,
        // without waiting for a save to flush to VFS.
        pl.archiprogram.localreview.vfs.DocumentInvalidationListener.ensureInstalled()
        // StatusBarWidgetsManager runs its init *before* VCS mappings finish loading; any factory
        // that gated on hasActiveVcss() used to lose the race. isAvailable() is unconditional
        // now, but force a re-evaluation here so the widget recovers even if it was skipped.
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            project.getService(StatusBarWidgetsManager::class.java)
                ?.updateWidget(CounterWidgetFactory::class.java)
        }
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

        val result =
            ReadAction.nonBlocking<ChangeSetScanner.Result?> {
                if (project.isDisposed) {
                    null
                } else {
                    val clm = ChangeListManager.getInstance(project)
                    ChangeSetScanner.scan(
                        project = project,
                        changes = clm.allChanges,
                        unversionedPaths = clm.unversionedFilesPaths,
                        isViewed = service::isViewed,
                        hasher = hasher,
                    )
                }
            }.executeSynchronously() ?: return

        val settings = LocalReviewSettings.getInstance().current()
        service.reconcile(
            currentChanges = result.currentChanges,
            renames = result.renames,
            rehashedContent = result.rehash,
            settings = settings,
        )
        Logging.trace { "Initial reconcile: ${service.size()} entries remain" }
    }
}
