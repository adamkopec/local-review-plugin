package pl.archiprogram.localreview.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State as StateAnn
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import pl.archiprogram.localreview.settings.LocalReviewSettings
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Project-scoped store of "viewed" marks, persisted to `.idea/cache/local-review-state.xml`.
 *
 * Storage location is [StoragePathMacros.CACHE_FILE] (NOT `WORKSPACE_FILE`) because review marks
 * are strictly per-user; committing them would leak who reviewed what.
 */
@Service(Service.Level.PROJECT)
@StateAnn(
    name = "LocalReviewState",
    storages = [Storage(StoragePathMacros.CACHE_FILE)],
)
class ReviewStateService(private val project: Project) : PersistentStateComponent<State> {

    interface Listener {
        fun stateChanged()
    }

    private val lock = ReentrantReadWriteLock()
    private val entries: MutableMap<Key, ReviewEntry> = HashMap()

    // ----- PSC -----

    override fun getState(): State {
        val snapshot = lock.read { HashMap(entries) }
        return State().apply {
            version = State.CURRENT_VERSION
            entries = snapshot.entries
                .map { (k, v) -> EntryDto(k, v) }
                .toMutableList()
        }
    }

    override fun loadState(state: State) {
        val migrated = migrate(state)
        lock.write {
            entries.clear()
            for (dto in migrated.entries) {
                try {
                    entries[dto.toKey()] = dto.toEntry()
                } catch (e: Exception) {
                    LOG.warn("Dropping malformed entry for ${dto.path}: ${e.message}")
                }
            }
        }
        fireChanged()
    }

    private fun migrate(state: State): State {
        // v1 is the initial schema. Future versions should branch here.
        return state
    }

    // ----- Core ops -----

    fun isViewed(key: Key): Boolean = lock.read { entries.containsKey(key) }

    fun getEntry(key: Key): ReviewEntry? = lock.read { entries[key] }

    fun size(): Int = lock.read { entries.size }

    /** All currently viewed keys whose [Key.repoRoot] and [Key.branch] match the supplied scope. */
    fun viewedKeysFor(repoRoot: String, branch: String): Set<Key> = lock.read {
        entries.keys.filterTo(HashSet()) { it.repoRoot == repoRoot && it.branch == branch }
    }

    fun mark(key: Key, hashHex: String, now: Long = System.currentTimeMillis()) {
        val changed = lock.write {
            val existing = entries[key]
            if (existing != null && existing.hashHex == hashHex) false
            else {
                entries[key] = ReviewEntry(hashHex, now)
                true
            }
        }
        if (changed) {
            LOG.info("LocalReview: marked key=$key")
            fireChanged()
        }
    }

    fun unmark(key: Key): Boolean {
        val removed = lock.write { entries.remove(key) != null }
        if (removed) fireChanged()
        return removed
    }

    fun toggle(key: Key, hashHexIfMarking: String?, now: Long = System.currentTimeMillis()): Boolean {
        val marked = lock.write {
            if (entries.containsKey(key)) {
                entries.remove(key)
                false
            } else {
                if (hashHexIfMarking == null) return@write null
                entries[key] = ReviewEntry(hashHexIfMarking, now)
                true
            }
        }
        if (marked != null) fireChanged()
        return marked ?: false
    }

    /**
     * Atomic bulk update used by [ChangeSetListener] and [ContentChangeListener]:
     *  - drops entries whose [Key] is not in [currentChanges]
     *  - re-keys entries per [renames] (oldKey → newKey)
     *  - for each entry in [rehashedContent], drops it if the new hash differs from stored
     *  - applies TTL and per-branch cap using [now] and [settings]
     */
    fun reconcile(
        currentChanges: Set<Key>,
        renames: Map<Key, Key>,
        rehashedContent: Map<Key, String>,
        settings: LocalReviewSettings.State,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        var changed = false
        lock.write {
            // 1. Renames: move entries to new keys (still must survive the currentChanges filter)
            for ((old, new) in renames) {
                val entry = entries.remove(old)
                if (entry != null) {
                    entries[new] = entry
                    changed = true
                }
            }

            // 2. Drop entries not backed by a current Change
            val iter = entries.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                if (e.key !in currentChanges) {
                    iter.remove()
                    changed = true
                }
            }

            // 3. Rehash-based invalidation
            for ((key, newHash) in rehashedContent) {
                val entry = entries[key] ?: continue
                if (entry.hashHex != newHash) {
                    entries.remove(key)
                    changed = true
                }
            }

            // 4. TTL eviction
            if (settings.ttlDays > 0) {
                val cutoff = now - settings.ttlDays * DAY_MS
                val iterTtl = entries.entries.iterator()
                while (iterTtl.hasNext()) {
                    if (iterTtl.next().value.markedAt < cutoff) {
                        iterTtl.remove()
                        changed = true
                    }
                }
            }

            // 5. Per-branch cap (LRU by markedAt)
            if (settings.perBranchCap > 0) {
                val byBranch = entries.entries.groupBy { it.key.repoRoot to it.key.branch }
                for ((_, inBranch) in byBranch) {
                    if (inBranch.size > settings.perBranchCap) {
                        inBranch
                            .sortedBy { it.value.markedAt }
                            .take(inBranch.size - settings.perBranchCap)
                            .forEach {
                                entries.remove(it.key)
                                changed = true
                            }
                    }
                }
            }
        }
        if (changed) fireChanged()
        return changed
    }

    /** Remove every viewed mark scoped to the given (repoRoot, branch). */
    fun clearBranch(repoRoot: String, branch: String): Int {
        var removed = 0
        lock.write {
            val iter = entries.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                if (e.key.repoRoot == repoRoot && e.key.branch == branch) {
                    iter.remove()
                    removed++
                }
            }
        }
        if (removed > 0) fireChanged()
        return removed
    }

    /** Remove every viewed mark for this project. */
    fun clearAll(): Int {
        val removed = lock.write {
            val n = entries.size
            entries.clear()
            n
        }
        if (removed > 0) fireChanged()
        return removed
    }

    // ----- Observer -----

    private fun fireChanged() {
        project.messageBus.syncPublisher(TOPIC).stateChanged()
        // Ask the Local Changes tree to rebuild — our ChangesViewModifier needs to re-emit
        // the "Reviewed (N)" synthetic group with the new contents.
        try {
            project.messageBus
                .syncPublisher(com.intellij.openapi.vcs.changes.ChangesViewModifier.TOPIC)
                .updated()
        } catch (_: Throwable) {
            // TOPIC may not be available on some IDE flavors / future versions.
        }
    }

    companion object {
        @JvmField
        val TOPIC: Topic<Listener> = Topic.create("LocalReview.StateChanged", Listener::class.java)

        private const val DAY_MS: Long = 24L * 60 * 60 * 1000
        private val LOG = Logger.getInstance(ReviewStateService::class.java)

        fun getInstance(project: Project): ReviewStateService = project.service()
    }
}
