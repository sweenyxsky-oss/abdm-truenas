plugins {
    id(MyPlugins.kotlinMultiplatform)
    id(Plugins.Kotlin.serialization)
    id(MyPlugins.composeBase)
}
kotlin {
    jvm("desktop")
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":downloader:core"))
                implementation(project(":shared:utils"))
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.compose.runtime)
            }
        }
    }
}
