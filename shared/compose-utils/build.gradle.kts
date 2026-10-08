

plugins {
    id(MyPlugins.kotlinMultiplatform)
    id(MyPlugins.composeBase)
}
kotlin {
    jvm("desktop")
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(project(":shared:utils"))
            api(project(":shared:resources:contracts"))
        }
    }
}
