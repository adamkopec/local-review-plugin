package pl.archiprogram.localreview.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import pl.archiprogram.localreview.action.hashAfter
import pl.archiprogram.localreview.action.key
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.settings.LocalReviewSettings
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewState
import pl.archiprogram.localreview.state.ReviewStateService

/**
 * Plain-Kotlin logic for the MCP toolset's suspend-fun entry points. Tests exercise these
 * directly so they don't need a coroutine runtime or `ReadAction` context.
 *
 * Tool outputs are returned as plain [String]s so we don't pull in kotlinx-serialization at
 * compile time. List-shaped results are formatted as minimal JSON so AI agents can parse
 * them deterministically.
 */

internal const val DISABLED_MESSAGE =
    "Local Review MCP tools are disabled in plugin settings. Enable them under Settings → Tools → Local Review."

internal fun mcpToolsEnabled(): Boolean =
    LocalReviewSettings.getInstance().current().enableMcpTools

internal data class ChangeEntry(
    val path: String,
    val viewed: Boolean,
    val status: String,
)

internal data class PathResult(
    val path: String,
    val result: String,
)

internal fun listChanges(
    project: Project,
    service: ReviewState = ReviewStateService.getInstance(project),
    clm: ChangeListManager = ChangeListManager.getInstance(project),
): List<ChangeEntry> {
    if (project.isDisposed) return emptyList()
    return clm.allChanges.mapNotNull { change ->
        val key = change.key(project) ?: return@mapNotNull null
        ChangeEntry(
            path = key.path,
            viewed = service.isViewed(key),
            status = statusName(change.fileStatus),
        )
    }
}

internal fun markAllViewed(
    project: Project,
    service: ReviewState = ReviewStateService.getInstance(project),
    clm: ChangeListManager = ChangeListManager.getInstance(project),
): Int {
    if (project.isDisposed) return 0
    var marked = 0
    for (change in clm.allChanges) {
        if (change.fileStatus == FileStatus.MERGED_WITH_CONFLICTS) continue
        val key = change.key(project) ?: continue
        if (service.isViewed(key)) continue
        val hash = change.hashAfter() ?: continue
        service.mark(key, hash, System.currentTimeMillis())
        marked++
    }
    return marked
}

internal fun unmarkAll(
    project: Project,
    service: ReviewState = ReviewStateService.getInstance(project),
): Int {
    if (project.isDisposed) return 0
    return service.clearAll()
}

internal fun markFiles(
    project: Project,
    paths: List<String>,
    service: ReviewState = ReviewStateService.getInstance(project),
    clm: ChangeListManager = ChangeListManager.getInstance(project),
): List<PathResult> {
    if (project.isDisposed) return paths.map { PathResult(it, "project_disposed") }
    val currentByKey: Map<Key, Change> = clm.allChanges
        .mapNotNull { c -> c.key(project)?.let { it to c } }
        .toMap()
    return paths.map { path ->
        val result = when (val outcome = PathResolver.resolve(project, path)) {
            is PathResolver.Outcome.BlankPath -> "blank_path"
            is PathResolver.Outcome.NotFound -> "not_found"
            is PathResolver.Outcome.IsDirectory -> "is_directory"
            is PathResolver.Outcome.OutsideVcs -> "outside_vcs"
            is PathResolver.Outcome.Resolved -> {
                val change = currentByKey[outcome.key]
                when {
                    change == null -> "not_a_current_change"
                    service.isViewed(outcome.key) -> "already_viewed"
                    else -> {
                        val hash = change.hashAfter()
                            ?: ContentHasher.getInstance().hash(outcome.file)
                        if (hash == null) {
                            "hash_unavailable"
                        } else {
                            service.mark(outcome.key, hash, System.currentTimeMillis())
                            "marked"
                        }
                    }
                }
            }
        }
        PathResult(path, result)
    }
}

internal fun unmarkFiles(
    project: Project,
    paths: List<String>,
    service: ReviewState = ReviewStateService.getInstance(project),
): List<PathResult> {
    if (project.isDisposed) return paths.map { PathResult(it, "project_disposed") }
    return paths.map { path ->
        val result = when (val outcome = PathResolver.resolve(project, path)) {
            is PathResolver.Outcome.BlankPath -> "blank_path"
            is PathResolver.Outcome.NotFound -> "not_found"
            is PathResolver.Outcome.IsDirectory -> "is_directory"
            is PathResolver.Outcome.OutsideVcs -> "outside_vcs"
            is PathResolver.Outcome.Resolved ->
                if (service.unmark(outcome.key)) "unmarked" else "not_viewed"
        }
        PathResult(path, result)
    }
}

internal fun statusName(status: FileStatus): String = when (status) {
    FileStatus.MODIFIED -> "MODIFIED"
    FileStatus.ADDED -> "NEW"
    FileStatus.DELETED -> "DELETED"
    FileStatus.MERGED_WITH_CONFLICTS -> "MERGED_WITH_CONFLICTS"
    FileStatus.NOT_CHANGED -> "NOT_CHANGED"
    FileStatus.OBSOLETE -> "OBSOLETE"
    FileStatus.UNKNOWN -> "UNKNOWN"
    else -> status.text ?: "OTHER"
}

internal fun changeEntriesToJson(entries: List<ChangeEntry>): String =
    entries.joinToString(prefix = "[", postfix = "]") { e ->
        """{"path":${jsonString(e.path)},"viewed":${e.viewed},"status":${jsonString(e.status)}}"""
    }

internal fun pathResultsToJson(results: List<PathResult>): String =
    results.joinToString(prefix = "[", postfix = "]") { r ->
        """{"path":${jsonString(r.path)},"result":${jsonString(r.result)}}"""
    }

private fun jsonString(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (c in s) {
        when (c) {
            '\\', '"' -> { sb.append('\\'); sb.append(c) }
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
    }
    sb.append('"')
    return sb.toString()
}
