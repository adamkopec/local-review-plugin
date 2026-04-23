package pl.archiprogram.localreview.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import pl.archiprogram.localreview.state.Key

/**
 * Single branch provider impl. Tries Git4Idea directly; if the Git plugin is absent or its repo
 * manager returns nothing (SVN project, unsynced state, etc.), we fall back to a sentinel.
 *
 * Splitting this into interface + service-override caused the branch to always resolve to
 * `NO_BRANCH` in the sandbox (the optional fragment's service override didn't take effect in
 * 2024.1), so keys stored by actions and keys derived by the renderer/modifier ended up in
 * different scopes. Keeping it simple and direct avoids that class of bug entirely.
 */
class DefaultBranchProvider : BranchProvider {
    override fun currentBranch(
        project: Project,
        repoRoot: VirtualFile,
    ): String {
        if (project.isDisposed) return Key.NO_BRANCH
        return try {
            val mgr = git4idea.repo.GitRepositoryManager.getInstance(project)
            // Look up by path string, not VirtualFile identity. On some threads / timing windows,
            // `getRepositoryForRoot(vf)` returns null even when the repo exists, because the
            // VFS handle passed from our caller is a different instance than the one Git cached.
            val repoRootPath = repoRoot.path
            val repo = mgr.repositories.firstOrNull { it.root.path == repoRootPath }
            if (repo == null) {
                LOG.info("LocalReview: no Git repo for path=$repoRootPath; known=${mgr.repositories.map { it.root.path }}")
                Key.NO_BRANCH
            } else {
                repo.currentBranch?.name ?: Key.DETACHED
            }
        } catch (_: NoClassDefFoundError) {
            // Git4Idea plugin disabled.
            Key.NO_BRANCH
        } catch (_: ClassNotFoundException) {
            Key.NO_BRANCH
        } catch (t: Throwable) {
            LOG.info("LocalReview: branch lookup failed: ${t.message}")
            Key.NO_BRANCH
        }
    }

    companion object {
        private val LOG = Logger.getInstance(DefaultBranchProvider::class.java)
    }
}
