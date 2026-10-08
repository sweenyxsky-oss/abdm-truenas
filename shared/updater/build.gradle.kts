plugins {
    id(MyPlugins.kotlinMultiplatform)
    id(Plugins.Kotlin.serialization)
}
kotlin {
    jvm("desktop")
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.serialization.json)
            api(libs.okhttp.okhttp)
            api(libs.kotlin.coroutines.core)
            implementation(project(":shared:utils"))
            implementation(libs.semver)
            implementation("ir.amirab.util:platform:1")
        }
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.jna.platform)
        }
    }
}
