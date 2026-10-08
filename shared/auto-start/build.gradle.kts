
plugins {
    id(MyPlugins.kotlinMultiplatform)
}
kotlin {
    jvm("desktop")
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:utils"))
        }
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            //    // for windows, we use registry
            implementation(libs.jna.platform)
        }
    }
}
