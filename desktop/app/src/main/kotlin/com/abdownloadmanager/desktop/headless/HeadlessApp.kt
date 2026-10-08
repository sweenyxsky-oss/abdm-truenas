package com.abdownloadmanager.desktop.headless

import com.abdownloadmanager.desktop.di.Di
import com.abdownloadmanager.desktop.repository.AppRepository
import com.abdownloadmanager.desktop.utils.EntryType
import com.abdownloadmanager.desktop.utils.EntrypointInitializer
import com.abdownloadmanager.integration.Integration
import com.abdownloadmanager.shared.util.ApiKeyUtil
import com.abdownloadmanager.shared.util.DownloadSystem
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Headless ABDM runtime for the TrueNAS web application.
 *
 * This deliberately reuses the existing downloader/queue/integration stack instead
 * of implementing a second download engine. No Compose UI is started.
 */
object HeadlessApp : KoinComponent {
    private val appRepository: AppRepository by inject()
    private val downloadSystem: DownloadSystem by inject()
    private val integration: Integration by inject()

    @JvmStatic
    fun main(args: Array<String>) {
        // Tells the integration layer there is no desktop UI (captured downloads are added directly).
        System.setProperty(com.abdownloadmanager.desktop.integration.HEADLESS_PROPERTY, "true")
        runBlocking {
            EntrypointInitializer.boot(
                debug = args.any { it == "--debug" },
                entryType = EntryType.CLI,
            )

            Di.boot(entryType = EntryType.CLI)

            // Load persisted application settings and start the real downloader.
            appRepository.boot()
            // Fully boot the downloader and its queue/category state before exposing the HTTP API.
            downloadSystem.boot()

            // First-run container defaults are written into ABDM's persistent settings.
            // AppRepository owns the Integration lifecycle, so do not call integration.enable()
            // directly here; otherwise persisted API settings can race with the container defaults.
            val containerDownloadFolder = System.getenv("ABDM_DOWNLOAD_FOLDER")?.takeIf { it.isNotBlank() }
            val containerApiPort = System.getenv("ABDM_API_PORT")?.toIntOrNull()?.takeIf { it in 1..65535 }
            val containerApiKey = System.getenv("ABDM_API_KEY")?.takeIf { it.isNotBlank() }
            val initMarker = java.io.File(System.getProperty("user.home"), ".abdm-truenas-initialized")
            if (!initMarker.exists()) {
                containerDownloadFolder?.let { appRepository.saveLocation.value = it }
                containerApiPort?.let { appRepository.apiPort.value = it }
                appRepository.apiEnabled.value = true

                if (containerApiKey != null) {
                    require(ApiKeyUtil.isValidKey(containerApiKey)) {
                        "ABDM_API_KEY is not a valid ABDM API key"
                    }
                    appRepository.apiAuthKey.value = containerApiKey
                    appRepository.apiAuthEnabled.value = true
                }

                initMarker.parentFile?.mkdirs()
                initMarker.writeText("initialized")
            }

            // Start the HTTP service only after core state and first-run settings are ready.
            integration.boot()

            // The monitor only updates its state flows while they have subscribers.
            // Keep one headless subscription so the REST API always exposes live download state.
            launch {
                downloadSystem.downloadMonitor.downloadListFlow.collect { }
            }

            // Keep the JVM alive while Ktor/download coroutines run.
            kotlinx.coroutines.awaitCancellation()
        }
    }
}
