package com.localreview.vcs

import com.intellij.openapi.project.Project
import com.localreview.ui.SafeRefresh
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for branch changes per repository. We do NOT refresh the tree directly here — CLM's
 * dirty-scope refresh hasn't run yet, so the tree would paint the old change set with the new
 * branch's viewed-state keys. Instead we remember the last-known branch and let
 * [com.localreview.vcs.ChangeSetListener.changeListUpdateDone] trigger the refresh.
 */
class GitBranchListener(private val project: Project) : GitRepositoryChangeListener {

    private val lastBranch = ConcurrentHashMap<String, String>()

    override fun repositoryChanged(repository: GitRepository) {
        val rootPath = repository.root.path
        val current = repository.currentBranch?.name ?: "<detached>"
        val previous = lastBranch.put(rootPath, current)
        if (previous != null && previous != current) {
            // Branch actually changed. Kick a refresh — CLM will reconcile, and our
            // ChangeSetListener will drop or re-surface entries keyed against the new branch.
            SafeRefresh.scheduleChangesViewRefresh(project)
        }
    }
}
