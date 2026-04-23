package pl.archiprogram.localreview.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.remoterobot.utils.waitFor
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration

/**
 * Smoke test against a sandbox IDE launched via `./gradlew runIdeForUiTests`.
 *
 * To run:
 *   Terminal 1:  ./gradlew runIdeForUiTests
 *   Terminal 2:  ./gradlew uiTest
 */
class LocalReviewUiSmokeTest {
    companion object {
        private val robot: RemoteRobot = RemoteRobot("http://127.0.0.1:${System.getProperty("robot-server.port", "8082")}")

        @BeforeClass
        @JvmStatic
        fun setUp() {
            StepWorker.registerProcessor(StepLogger())
            waitFor(Duration.ofSeconds(60)) {
                robot.callJs<Boolean>("true", true)
            }
        }
    }

    @Test
    fun plugin_isLoadedAndActionRegistered() {
        val actionFound =
            robot.callJs<Boolean>(
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
        val serviceClassName =
            robot.callJs<String>(
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

    @Test
    fun action_isDiscoverableInSearchEverywhere() {
        val textFound =
            robot.callJs<Boolean>(
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
