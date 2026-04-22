package pl.archiprogram.localreview.state

import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

class State {
    var version: Int = CURRENT_VERSION

    @XCollection(style = XCollection.Style.v2)
    var entries: MutableList<EntryDto> = mutableListOf()

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Tag("entry")
class EntryDto {
    var repoRoot: String = ""
    var branch: String = Key.NO_BRANCH
    var path: String = ""
    var hashHex: String = ""
    var markedAt: Long = 0L

    constructor()

    constructor(key: Key, entry: ReviewEntry) {
        this.repoRoot = key.repoRoot
        this.branch = key.branch
        this.path = key.path
        this.hashHex = entry.hashHex
        this.markedAt = entry.markedAt
    }

    fun toKey(): Key = Key(repoRoot, branch, path)
    fun toEntry(): ReviewEntry = ReviewEntry(hashHex, markedAt)
}
