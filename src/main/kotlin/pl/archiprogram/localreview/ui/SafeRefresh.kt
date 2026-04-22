package pl.archiprogram.localreview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusManager

/**
 * Helper that funnels tree / status refreshes through one place. Wraps `ChangesViewManager`'s
 * `@ApiStatus.Internal` `scheduleRefresh()` with a stable fallback ([VcsDirtyScopeManager]).
 */
object SafeRefresh {

    fun scheduleChangesViewRefresh(project: Project) {
        if (project.isDisposed) return
        // Only attempt the tree-only refresh via ChangesViewManager. We intentionally do NOT
        // fall back to VcsDirtyScopeManager.markEverythingDirty() — that forces a full CLM
        // re-scan which then fires changeListUpdateDone → reconcile → refresh, a feedback loop.
        try {
            val cls = Class.forName("com.intellij.openapi.vcs.changes.ui.ChangesViewManager")
            val getInstance = cls.getMethod("getInstance", Project::class.java)
            val manager = getInstance.invoke(null, project)
            cls.getMethod("scheduleRefresh").invoke(manager)
        } catch (e: Throwable) {
            LOG.debug("ChangesViewManager.scheduleRefresh unavailable: ${e.message}")
        }
    }

    fun refreshFileStatuses(project: Project) {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                FileStatusManager.getInstance(project).fileStatusesChanged()
            }
        }
    }

    private val LOG = Logger.getInstance(SafeRefresh::class.java)
}
