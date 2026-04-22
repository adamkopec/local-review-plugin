package pl.archiprogram.localreview.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(
    name = "LocalReviewSettings",
    storages = [Storage("localReview.xml")],
)
class LocalReviewSettings : PersistentStateComponent<LocalReviewSettings.State> {

    data class State(
        var ttlDays: Int = DEFAULT_TTL_DAYS,
        var perBranchCap: Int = DEFAULT_PER_BRANCH_CAP,
        var enableGrouping: Boolean = true,
        var autoMarkOnDiffClose: Boolean = false,
        var enableDebugLogging: Boolean = false,
        var enableMcpTools: Boolean = true,
    )

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun current(): State = state

    companion object {
        const val DEFAULT_TTL_DAYS = 30
        const val DEFAULT_PER_BRANCH_CAP = 500

        fun getInstance(): LocalReviewSettings =
            ApplicationManager.getApplication().service()
    }
}
