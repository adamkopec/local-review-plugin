package pl.archiprogram.localreview.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ClientProperty
import com.intellij.util.concurrency.AppExecutorUtil
import pl.archiprogram.localreview.state.ReviewStateService
import java.awt.Component
import java.awt.Container
import javax.swing.JTree
import javax.swing.SwingUtilities

/**
 * Installs [ViewedCellRenderer] on every [ChangesListView] that appears in the Commit or Version
 * Control tool windows. Because the tool window's component tree is built lazily, we re-check on
 * every tool-window state change (cheap: early-returns when renderer already installed).
 *
 * Failure is silent: if internal platform structure shifts and we can't find the tree, users lose
 * the inline decoration but nothing crashes. The grouping policy and counter widget still work.
 */
class CommitViewRendererInstaller : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (project.isDisposed) return
        LOG.info("LocalReview: CommitViewRendererInstaller starting for project ${project.name}")
        val conn = project.messageBus.connect()
        conn.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                scheduleInstall(project)
            }
        })
        conn.subscribe(ReviewStateService.TOPIC, object : ReviewStateService.Listener {
            override fun stateChanged() = schedulePaint(project)
        })
        conn.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() = schedulePaint(project)
        })
        scheduleInstall(project)
    }

    companion object {
        private val LOG = Logger.getInstance(CommitViewRendererInstaller::class.java)
        private val MARKER = com.intellij.openapi.util.Key.create<Boolean>("pl.archiprogram.localreview.renderer.installed")
        private val TREES_KEY = com.intellij.openapi.util.Key.create<MutableList<JTree>>("pl.archiprogram.localreview.trees")

        private fun scheduleInstall(project: Project) {
            AppExecutorUtil.getAppExecutorService().submit {
                if (project.isDisposed) return@submit
                SwingUtilities.invokeLater {
                    if (project.isDisposed) return@invokeLater
                    try {
                        install(project)
                    } catch (e: Throwable) {
                        LOG.debug("Installer failed: ${e.message}")
                    }
                }
            }
        }

        private fun schedulePaint(project: Project) {
            SwingUtilities.invokeLater {
                if (project.isDisposed) return@invokeLater
                project.getUserData(TREES_KEY)?.forEach { tree ->
                    try { tree.repaint() } catch (_: Throwable) {}
                }
            }
        }

        private fun install(project: Project) {
            val mgr = ToolWindowManager.getInstance(project)
            val windowIds = listOf("Commit", "Version Control", "Vcs.Log")
            for (id in windowIds) {
                val tw = mgr.getToolWindow(id) ?: continue
                LOG.info("LocalReview: inspecting tool window '$id' (${tw.contentManager.contentCount} contents)")
                for (content in tw.contentManager.contents) {
                    walk(content.component) { tree ->
                        LOG.info("LocalReview: found ${tree.javaClass.simpleName} in '$id'")
                        installOn(project, tree)
                    }
                }
            }
        }

        private fun walk(root: Component, action: (ChangesListView) -> Unit) {
            if (root is ChangesListView) action(root)
            if (root is Container) {
                for (child in root.components) walk(child, action)
            }
        }

        private fun installOn(project: Project, tree: ChangesListView) {
            if (ClientProperty.get(tree, MARKER) == true) {
                LOG.info("LocalReview: renderer already installed on ${tree.javaClass.simpleName}")
                return
            }
            val existing = tree.cellRenderer
            if (existing == null) {
                LOG.warn("LocalReview: ${tree.javaClass.simpleName} has no cell renderer yet")
                return
            }
            if (existing is ViewedCellRenderer) return
            tree.cellRenderer = ViewedCellRenderer(project, existing)
            ClientProperty.put(tree, MARKER, true)

            val trees = project.getUserData(TREES_KEY)
                ?: mutableListOf<JTree>().also { project.putUserData(TREES_KEY, it) }
            if (tree !in trees) trees += tree
            LOG.info("LocalReview: installed ViewedCellRenderer on ${tree.javaClass.simpleName} (wrapping ${existing.javaClass.simpleName})")
        }
    }
}
