package pl.archiprogram.localreview.vfs

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.ui.SafeRefresh
import pl.archiprogram.localreview.vcs.KeyDeriver
import java.util.concurrent.ExecutorService

/**
 * Application-scoped async listener that rehashes files when their content changes. If a rehash
 * yields a different digest than the one stored when the file was marked, the mark is dropped.
 *
 * Heavy work runs on a bounded executor pool; rehashes acquire a read lock via [runReadAction].
 */
class ContentChangeListener : AsyncFileListener {
    override fun prepareChange(events: MutableList<out VFileEvent>): ChangeApplier? {
        val candidates =
            events
                .asSequence()
                .filterIsInstance<VFileContentChangeEvent>()
                .mapNotNull { it.file }
                .toList()
        if (candidates.isEmpty()) return null
        pl.archiprogram.localreview.diagnostics.Logging.trace {
            "LocalReview: VFS content change for ${candidates.size} file(s): " +
                candidates.take(5).joinToString { it.path } + (if (candidates.size > 5) " …" else "")
        }
        return Applier(candidates)
    }

    private class Applier(private val files: List<VirtualFile>) : ChangeApplier {
        override fun afterVfsChange() {
            val executor = executor()
            // Dedupe by identity; the platform can emit multiple events per file per batch.
            val unique = files.distinct()
            for (file in unique) {
                if (!file.isValid || file.isDirectory) continue
                executor.submit { handleFile(file) }
            }
        }

        private fun handleFile(file: VirtualFile) {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                val key =
                    runReadAction {
                        if (project.isDisposed) null else KeyDeriver.keyFor(project, file)
                    } ?: continue

                val service = project.service<ReviewStateService>()
                val existing = service.getEntry(key) ?: continue

                // Defensive: if the file is no longer a Change, ChangeSetListener will clean it
                // up on the next CLM update anyway. We still rehash because the edit might have
                // happened before CLM refreshed.
                val newHash =
                    try {
                        runReadAction {
                            if (project.isDisposed) null else ContentHasher.getInstance().hash(file)
                        }
                    } catch (e: Throwable) {
                        LOG.debug("Rehash failed for ${file.path}: ${e.message}")
                        null
                    } ?: continue

                if (newHash != existing.hashHex) {
                    LOG.info("LocalReview: content changed — unmarking key=$key")
                    service.unmark(key)
                    SafeRefresh.refreshFileStatuses(project)
                    SafeRefresh.scheduleChangesViewRefresh(project)
                } else {
                    pl.archiprogram.localreview.diagnostics.Logging.trace {
                        "LocalReview: content unchanged for key=$key; mark stays"
                    }
                }
            }
        }
    }

    companion object {
        @Volatile private var testExecutor: ExecutorService? = null
        private val installed = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Test-only: redirect rehash work to a deterministic executor. */
        @JvmStatic
        fun setTestExecutor(executor: ExecutorService?) {
            testExecutor = executor
        }

        /** Install the listener once per JVM. Safe to call from any project-level startup. */
        @JvmStatic
        fun ensureInstalled() {
            if (installed.compareAndSet(false, true)) {
                com.intellij.openapi.vfs.VirtualFileManager.getInstance().addAsyncFileListener(
                    ContentChangeListener(),
                    com.intellij.openapi.application.ApplicationManager.getApplication(),
                )
                LOG.info("LocalReview: AsyncFileListener registered")
            }
        }

        private fun executor(): ExecutorService = testExecutor ?: SHARED_EXECUTOR

        private val SHARED_EXECUTOR: ExecutorService by lazy {
            AppExecutorUtil.createBoundedApplicationPoolExecutor("LocalReview.Hasher", 2)
        }

        private val LOG = Logger.getInstance(ContentChangeListener::class.java)
    }
}
