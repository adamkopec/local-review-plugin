import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.3")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract the <Plugin description> block from README.md and convert to HTML.
        // Single source of truth: same text appears on GitHub and on the Marketplace listing.
        description = providers.fileContents(
            layout.projectDirectory.file("README.md"),
        ).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    error("Plugin description markers not found in README.md")
                }
                subList(indexOf(start) + 1, indexOf(end))
                    .joinToString("\n")
                    .let(::markdownToHTML)
            }
        }

        // Pull release notes from CHANGELOG.md. Shows the current version's section if
        // already released, otherwise the [Unreleased] section during development.
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

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
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
            recommended()
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.1")
            ide(IntelliJPlatformType.PyCharmCommunity, "2024.1")
            ide(IntelliJPlatformType.GoLand, "2024.1")
        }
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INVALID_PLUGIN,
            FailureLevel.MISSING_DEPENDENCIES,
        )
    }
}

changelog {
    version = providers.gradleProperty("pluginVersion")
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

intellijPlatformTesting {
    testIde {
        register("testIdePyCharm") {
            type = IntelliJPlatformType.PyCharmCommunity
            version = providers.gradleProperty("platformVersion")
            task {
                useJUnitPlatform()
                include("**/*IT*")
            }
        }
        register("testIdeGoLand") {
            type = IntelliJPlatformType.GoLand
            version = providers.gradleProperty("platformVersion")
            task {
                useJUnitPlatform()
                include("**/*IT*")
            }
        }
    }

    // `runIdeForUiTests` launches the sandbox IDE with the robot-server plugin enabled, so that
    // Remote Robot UI tests can connect to it over http://127.0.0.1:8082. Run this in one
    // terminal, then `./gradlew uiTest` in another.
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

// Separate source set + task for Remote Robot UI tests. They require a running IDE launched via
// `runIdeForUiTests`; keep them out of the regular `test` task so they don't block CI.
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
    useJUnitPlatform()
    systemProperty("robot-server.port", "8082")
    // Don't fail the suite when the IDE isn't up — fail fast with a clear message in the test.
    shouldRunAfter("test")
}

tasks {
    test {
        useJUnitPlatform()
        maxParallelForks = 1
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
        gradleVersion = "8.13"
    }
    // Include the repo-root LICENSE file alongside the bundled jars so the plugin zip is
    // self-contained for auditors / users inspecting it offline. The Marketplace listing
    // and GitHub's license detector pick up the license from the repo root regardless.
    prepareSandbox {
        from(layout.projectDirectory.file("LICENSE")) {
            into(rootProject.name)
        }
    }
}

kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = 80
                }
            }
        }
    }
}
