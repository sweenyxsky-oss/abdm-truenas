plugins {
    id(MyPlugins.kotlinMultiplatform)
}
kotlin {
    jvm("desktop")
    sourceSets.commonMain.dependencies {
        implementation(libs.okio.okio)
    }
}
