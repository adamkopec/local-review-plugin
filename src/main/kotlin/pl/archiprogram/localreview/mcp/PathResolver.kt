package pl.archiprogram.localreview.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.vcs.KeyDeriver
import java.io.File

/**
 * Resolve a user-supplied path string into a [Key] for the given project.
 *
 * Accepts:
 *  - absolute filesystem paths
 *  - project-relative paths (resolved against [Project.getBasePath])
 *
 * Rejects:
 *  - blank strings
 *  - paths that don't exist on disk
 *  - directories
 *  - files outside any VCS root (no derivable [Key])
 *
 * The returned [Key] identifies a file unambiguously but does **not** imply that the file is
 * part of the current changeset — callers that scope to current changes (e.g. `mark_files`)
 * must intersect with `ChangeListManager.getInstance(project).allChanges` themselves.
 */
object PathResolver {
    sealed class Outcome {
        data class Resolved(val file: VirtualFile, val key: Key) : Outcome()

        object NotFound : Outcome()

        object IsDirectory : Outcome()

        object BlankPath : Outcome()

        object OutsideVcs : Outcome()
    }

    fun resolve(
        project: Project,
        path: String,
    ): Outcome {
        if (path.isBlank()) return Outcome.BlankPath

        val file = findFile(project, path) ?: return Outcome.NotFound
        if (!file.isValid) return Outcome.NotFound
        if (file.isDirectory) return Outcome.IsDirectory

        val key = KeyDeriver.keyFor(project, file) ?: return Outcome.OutsideVcs
        return Outcome.Resolved(file, key)
    }

    private fun findFile(
        project: Project,
        path: String,
    ): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        val asFile = File(path)
        val absolute =
            if (asFile.isAbsolute) {
                asFile
            } else {
                val base = project.basePath ?: return null
                File(base, path)
            }
        // Canonicalize to handle mixed case on case-insensitive filesystems (macOS, Windows)
        // and symlinks, so the derived Key matches what ChangeListManager reports.
        val canonical =
            try {
                absolute.canonicalFile
            } catch (_: Throwable) {
                absolute.absoluteFile
            }
        return lfs.findFileByIoFile(canonical)
    }
}
