package com.localreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.localreview.state.Key
import com.localreview.state.ReviewStateService
import com.localreview.vcs.KeyDeriver
import java.awt.Component
import java.awt.Container
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

/**
 * Decorates the Local Changes tree cell renderer: viewed rows get a "— viewed" suffix in grey
 * italic, and a strikethrough attribute on the suffix so the cue is unambiguous. We intentionally
 * append after the default renderer has filled the component so we never overwrite its text.
 */
class ViewedCellRenderer(
    private val project: Project,
    private val delegate: TreeCellRenderer,
) : TreeCellRenderer {

    private val loggedCall = java.util.concurrent.atomic.AtomicBoolean(false)
    private val loggedLabel = java.util.concurrent.atomic.AtomicBoolean(false)
    private val loggedDecorate = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        if (loggedCall.compareAndSet(false, true)) {
            LOG.info("LocalReview: ViewedCellRenderer.getTreeCellRendererComponent first call")
        }
        val component = delegate.getTreeCellRendererComponent(
            tree, value, selected, expanded, leaf, row, hasFocus,
        )
        if (project.isDisposed) return component

        val label = findColoredComponent(component)
        if (label == null) {
            if (loggedLabel.compareAndSet(false, true)) {
                LOG.info("LocalReview: no SimpleColoredComponent inside ${component.javaClass.name}")
            }
            return component
        }

        val key = keyFor(value) ?: return component
        val viewed = ReviewStateService.getInstance(project).isViewed(key)
        if (viewed) {
            if (loggedDecorate.compareAndSet(false, true)) {
                LOG.info("LocalReview: decorating viewed row for key=$key (label=${label.javaClass.name})")
            }
            val attrs = SimpleTextAttributes(
                SimpleTextAttributes.STYLE_BOLD,
                com.intellij.ui.JBColor(0x208A3C, 0x73BF78),
            )
            label.append("   ✓", attrs)
        }
        return component
    }

    private fun findColoredComponent(root: Component): SimpleColoredComponent? {
        if (root is SimpleColoredComponent) return root
        if (root is Container) {
            for (child in root.components) {
                val found = findColoredComponent(child)
                if (found != null) return found
            }
        }
        return null
    }

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ViewedCellRenderer::class.java)
    }

    private fun keyFor(value: Any?): Key? {
        val node = value as? ChangesBrowserNode<*> ?: return null
        return when (val obj = node.userObject) {
            is Change -> {
                val file = obj.afterRevision?.file ?: obj.beforeRevision?.file
                file?.let { KeyDeriver.keyFor(project, it) }
            }
            is FilePath -> KeyDeriver.keyFor(project, obj)
            is VirtualFile -> KeyDeriver.keyFor(project, obj)
            else -> null
        }
    }
}
