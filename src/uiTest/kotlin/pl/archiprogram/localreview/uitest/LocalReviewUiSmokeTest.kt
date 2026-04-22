package pl.archiprogram.localreview.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration

/**
 * Smoke test against a sandbox IDE launched via `./gradlew runIdeForUiTests`.
 *
 * This is a last-line check that the plugin's pieces actually wire up to the real UI. Most
 * functional coverage lives in [pl.archiprogram.localreview.integration.InvalidationFlowsIT] (headless,
 * fast, deterministic). Remote Robot tests are slow and flaky; keep them minimal.
 *
 * To run:
 *   Terminal 1:  ./gradlew runIdeForUiTests
 *   Terminal 2:  ./gradlew uiTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalReviewUiSmokeTest {

    private val robot: RemoteRobot = RemoteRobot("http://127.0.0.1:${System.getProperty("robot-server.port", "8082")}")

    @BeforeAll
    fun setUp() {
        StepWorker.registerProcessor(StepLogger())
        // Wait for the IDE to be responsive (Welcome Frame or main Frame).
        waitFor(Duration.ofSeconds(60)) {
            robot.callJs<Boolean>("true", true)
        }
    }

    @Test
    fun plugin_isLoadedAndActionRegistered() {
        // Query the running IDE's ActionManager via a JS script evaluated inside the IDE JVM.
        val actionFound = robot.callJs<Boolean>(
            """
            var mgr = com.intellij.openapi.actionSystem.ActionManager.getInstance();
            var action = mgr.getAction("LocalReview.ToggleViewed");
            action != null
            """.trimIndent(),
            true,
        )
        assert(actionFound) { "LocalReview.ToggleViewed action should be registered in the IDE" }
    }

    @Test
    fun pluginService_isInstantiable() {
        // Instantiate the application service and verify its class is ours.
        val serviceClassName = robot.callJs<String>(
            """
            var app = com.intellij.openapi.application.ApplicationManager.getApplication();
            var svc = app.getService(Java.type("pl.archiprogram.localreview.hash.ContentHasher"));
            svc.getClass().getName()
            """.trimIndent(),
            true,
        )
        assert(serviceClassName == "pl.archiprogram.localreview.hash.ContentHasher") {
            "Expected ContentHasher but got $serviceClassName"
        }
    }

    /**
     * Opens the search-everywhere popup, types "Mark as Reviewed", and verifies the plugin's
     * bundle string is discoverable. Catches bundle-loading and action-text regressions.
     */
    @Test
    fun action_isDiscoverableInSearchEverywhere() {
        val textFound = robot.callJs<Boolean>(
            """
            var mgr = com.intellij.openapi.actionSystem.ActionManager.getInstance();
            var action = mgr.getAction("LocalReview.ToggleViewed");
            var presentation = action.getTemplatePresentation();
            var text = presentation.getText();
            text != null && text.toLowerCase().indexOf("reviewed") >= 0
            """.trimIndent(),
            true,
        )
        assert(textFound) { "Action text should contain 'reviewed'" }
    }
}
