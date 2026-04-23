import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
    id("org.jetbrains.changelog")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    intellijPlatform {
        intellijIdea("2025.2.6.1")
        bundledPlugin("Git4Idea")
        bundledPlugin("com.intellij.mcpServer")
        testFramework(TestFrameworkType.Bundled)
    }
    testImplementation("junit:junit:4.13.2")
    // The IntelliJ test framework ships its own kotlinx-coroutines fork (`*-intellij-N`) via
    // `com.jetbrains.intellij.platform:test-framework`; letting mockk/kotest pull vanilla
    // coroutines shadows `runBlockingWithParallelismCompensation` at runtime.
    testImplementation("io.mockk:mockk:1.13.12") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    }
    testImplementation("io.kotest:kotest-assertions-core:5.9.1") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    testImplementation("io.kotest:kotest-property:5.9.1") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        vendor {
            name = "Adam Kopeć"
            email = "adam.kopec@gmail.com"
            url = providers.gradleProperty("pluginRepositoryUrl").get()
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Non-stable versions (e.g. 0.2.0-beta.1) publish to a matching channel.
        channels = providers.gradleProperty("pluginVersion").map { version ->
            listOf(version.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.6.1")
            create(IntelliJPlatformType.PyCharmCommunity, "2025.2.3")
            create(IntelliJPlatformType.GoLand, "2025.2.3")
        }
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INVALID_PLUGIN,
        )
    }
}

changelog {
    version = providers.gradleProperty("pluginVersion")
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

intellijPlatformTesting {
    runIde.register("runIdeForUiTests") {
        task {
            jvmArgumentProviders += CommandLineArgumentProvider {
                listOf(
                    "-Drobot-server.port=8082",
                    "-Dide.mac.message.dialogs.as.sheets=false",
                    "-Djb.privacy.policy.text=<!--999.999-->",
                    "-Djb.consents.confirmation.enabled=false",
                    "-Didea.trust.all.projects=true",
                    "-Dide.show.tips.on.startup.default.value=false",
                )
            }
        }
        plugins {
            robotServerPlugin()
        }
    }
}

sourceSets {
    create("uiTest") {
        kotlin.srcDir("src/uiTest/kotlin")
        resources.srcDir("src/uiTest/resources")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

configurations {
    named("uiTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("uiTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

dependencies {
    "uiTestImplementation"("com.intellij.remoterobot:remote-robot:0.11.23")
    "uiTestImplementation"("com.intellij.remoterobot:remote-fixtures:0.11.23")
    "uiTestImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.register<Test>("uiTest") {
    description = "Runs Remote Robot UI smoke tests. Requires a running ./gradlew runIdeForUiTests."
    group = "verification"
    testClassesDirs = sourceSets["uiTest"].output.classesDirs
    classpath = sourceSets["uiTest"].runtimeClasspath
    systemProperty("robot-server.port", "8082")
    jvmArgs(
        "-Xmx2g",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    )
    shouldRunAfter("test")
}

tasks {
    test {
        jvmArgs(
            "-Xmx2g",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        )
        systemProperty("idea.test.cyclic.buffer.size", "1048576")
    }
    wrapper {
        gradleVersion = "9.4.1"
    }
    // Bundle the repo-root LICENSE into the plugin zip so the distribution is self-contained
    // for offline auditors.
    prepareSandbox {
        from(layout.projectDirectory.file("LICENSE")) {
            into(rootProject.name)
        }
    }
}

kover {
    currentProject {
        sources {
            excludedSourceSets.add("uiTest")
        }
        instrumentation {
            disabledForTestTasks.add("uiTest")
        }
    }
    reports {
        filters {
            excludes {
                classes(
                    "pl.archiprogram.localreview.Icons*",
                    "pl.archiprogram.localreview.LocalReviewBundle*",
                    "pl.archiprogram.localreview.ui.*",
                    "pl.archiprogram.localreview.settings.LocalReviewConfigurable*",
                    "pl.archiprogram.localreview.startup.*",
                    "pl.archiprogram.localreview.diagnostics.*",
                )
            }
        }
        verify {
            rule {
                bound {
                    minValue = 70
                }
            }
        }
    }
}
