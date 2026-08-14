package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import pl.archiprogram.localreview.ui.SafeRefresh
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for branch changes per repository. CLM's dirty-scope refresh runs after this fires,
 * so refreshing the tree here would paint the old change set under the new branch's
 * viewed-state keys; [pl.archiprogram.localreview.vcs.ChangeSetListener.changeListUpdateDone]
 * triggers the actual refresh once CLM reconciles.
 */
class GitBranchListener
    @JvmOverloads
    constructor(
        private val project: Project,
        private val refresh: (Project) -> Unit = SafeRefresh::scheduleChangesViewRefresh,
    ) : GitRepositoryChangeListener {
        private val lastBranch = ConcurrentHashMap<String, String>()

        override fun repositoryChanged(repository: GitRepository) {
            val rootPath = repository.root.path
            val current = repository.currentBranch?.name ?: "<detached>"
            val previous = lastBranch.put(rootPath, current)
            if (previous != null && previous != current) {
                // Branch actually changed. Kick a refresh — CLM will reconcile, and our
                // ChangeSetListener will drop or re-surface entries keyed against the new branch.
                refresh(project)
            }
        }
    }
