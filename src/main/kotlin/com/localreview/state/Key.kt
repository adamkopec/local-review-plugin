package com.localreview.state

data class Key(
    val repoRoot: String,
    val branch: String,
    val path: String,
) {
    companion object {
        const val NO_VCS = "<no-vcs>"
        const val NO_BRANCH = "<no-branch>"
        const val DETACHED = "<detached>"
    }
}
