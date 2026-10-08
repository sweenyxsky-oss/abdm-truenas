import buildlogic.versioning.*
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import ir.amirab.util.platform.Arch
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.isLinux
import ir.amirab.util.platform.isMac
import ir.amirab.util.platform.isWindows

plugins {
    id(MyPlugins.kotlin)
    id(MyPlugins.composeDesktop)
    id(Plugins.Kotlin.serialization)
    id(Plugins.ksp)
    id(Plugins.aboutLibraries)
    id(Plugins.kotlinRpc)
//    id(MyPlugins.proguardDesktop)
}


dependencies {
    implementation(libs.decompose)
    implementation(libs.decompose.jbCompose)

    implementation(libs.koin.core)

    implementation(libs.kotlin.serialization.json)

    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.swing)

    implementation(libs.kotlin.datetime)

    implementation(libs.compose.reorderable)

    implementation(libs.arrow.core)
    implementation(libs.arrow.optics)
    ksp(libs.arrow.opticKsp)

    implementation(libs.androidx.datastore)

    implementation(libs.aboutLibraries.core)
    implementation(libs.markdownRenderer.core)
    implementation(libs.filekit.dialogs.compose) {
        exclude(group = "net.java.dev.jna")
    }
    implementation(libs.proxyVole) {
        exclude(group = "net.java.dev.jna")
    }
    implementation(libs.jna.core)
    implementation(libs.jna.platform)

    implementation(libs.kotlin.reflect) // used by kotlin-rpc
    implementation(libs.kotlinx.rpc.client)
    implementation(libs.kotlinx.rpc.client.ktor)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.kotlinx.rpc.serializationJson)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.server.cio)

    // cli
    implementation(libs.clikit)

    implementation(project(":downloader:core"))
    implementation(project(":downloader:monitor"))

    implementation(project(":integration:server"))
    implementation(project(":desktop:shared"))
    implementation(project(":desktop:app-utils"))

    implementation(libs.composeNativeTray)
    implementation(libs.nucleus.aot)

    implementation(project(":shared:app"))
    implementation(project(":shared:utils"))
    implementation(project(":shared:updater"))
    implementation(project(":desktop:mac_utils"))
    implementation(project(":desktop:slf4j-impl"))
}

aboutLibraries {
    export {
        prettyPrint = true
    }
    library {
        mergePlatformArtifacts = true
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
}

val isAOTEnabled = false

tasks.processResources {
    from(tasks.named("exportLibraryDefinitions"))
    from(rootProject.file("web")) {
        into("web")
    }
}


val desktopPackageName = "com.abdownloadmanager.desktop"
nucleus {
    application {
//            val getProguardConfigurationsTask = tasks.getProguardConfigurations.get()
        buildTypes.release.proguard {
            isEnabled.set(false)
//                obfuscate.set(false)
//                optimize.set(true)
//                configurationFiles.from(
//                    project.fileTree("proguard"),
//                    getProguardConfigurationsTask.outputs.files.asFileTree.filter {
//                        !it.name.contains("r8")
//                    },
//                )
        }

        // Define the main class for the application.
        mainClass = "$desktopPackageName.AppKt"
        additionalLaunchers {
            create("ABDMHeadless") {
                mainClass = "com.abdownloadmanager.desktop.headless.HeadlessApp"
                winConsole = true
                jvmArgs(*defaultJvmArgs())
            }
        }
        nativeDistributions {
            cleanupNativeLibs = true
            aotCache {
                enabled = isAOTEnabled
            }
            modules(
                "java.instrument",
                "jdk.unsupported",
                "jdk.accessibility",
            )
            if (Platform.isLinux()) {
                // filekit library requires this module in linux.
                modules("jdk.security.auth")
            }
            packageVersion = getAppVersionStringForPackaging()
            packageName = getAppName()
            vendor = "abdownloadmanager.com"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            val menuGroupName = getPrettifiedAppName()
            licenseFile.set(rootProject.file("LICENSE"))
            linux {
                appCategory = "Network"
                iconFile = project.file("icons/icon.png")
                menuGroup = menuGroupName
                shortcut = true
            }
        }
    }
}

/**
 * Temporary workaround. needs to be improved by the plugin.
 * I want to add default jvm-args used by the default launcher.
 * however when I add a single item to the jvmArgs, all default values are gone!
 * so I have to manually add these default values to each additional launcher
 */
fun defaultJvmArgs(): Array<String> {
    fun appDir(vararg pathParts: String): String {
        /** For windows we need to pass '\\' to jpackage file, each '\' need to be escaped.
        Otherwise '$APPDIR\resources' is passed to jpackage,
        and '\r' is treated as a special character at run time.
         */
        val separator = if (Platform.isWindows()) "\\\\" else "/"
        return listOf($$"$APPDIR", *pathParts).joinToString(separator) { it }
    }

    return buildList {
        if (isAOTEnabled) {
            add("-XX:AOTCache=${appDir("app.aot")}")
        }
//        add("-Djpackage.app-version=1.0.0")
        add("-Dcompose.application.resources.dir=${appDir("resources")}")
        add("-Dcompose.application.configure.swing.globals=true")
        add("--enable-native-access=ALL-UNNAMED")
        add("-Dnucleus.executable.type=dev")
        add("-Dnucleus.app.id=${getAppName()}")
        if (Platform.isMac()) {
            add("-Dapple.awt.enableTemplateImages=true")
        }
        add("-Dskiko.library.path=${appDir()}")
    }.toTypedArray()
}

fun Task.dependsOnAOT() {
    if (isAOTEnabled) {
        dependsOn("generateReleaseAotCache")
    }
}

val postReleaseDistributable = tasks.register("postReleaseDistributable") {
    dependsOn("createReleaseDistributable")
    description = "Any modification need to be added to the app distributable folder, should be added here"
    dependsOnAOT()
}


// Headless TrueNAS runtime. This uses the same downloader core as the desktop app.
tasks.register<JavaExec>("runHeadless") {
    group = "application"
    description = "Run ABDM without the Compose desktop UI"
    mainClass.set("com.abdownloadmanager.desktop.headless.HeadlessApp")
    classpath = sourceSets["main"].runtimeClasspath
}
