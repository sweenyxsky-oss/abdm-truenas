package com.abdownloadmanager.desktop.headless

import com.abdownloadmanager.desktop.di.Di
import com.abdownloadmanager.desktop.repository.AppRepository
import com.abdownloadmanager.desktop.utils.EntryType
import com.abdownloadmanager.desktop.utils.EntrypointInitializer
import com.abdownloadmanager.integration.Integration
import com.abdownloadmanager.integration.IntegrationSettings
import com.abdownloadmanager.shared.util.DownloadSystem
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
    private val integration: Integration by inject()
    private val downloadSystem: DownloadSystem by inject()

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            EntrypointInitializer.boot(
                debug = args.any { it == "--debug" },
                entryType = EntryType.CLI,
            )

            Di.boot()

            // Load persisted application settings and start the real downloader.
            appRepository.boot()
            integration.boot()
            downloadSystem.boot()

            val port = System.getenv("ABDM_API_PORT")?.toIntOrNull() ?: 15151
            val apiKey = System.getenv("ABDM_API_KEY")?.takeIf { it.isNotBlank() }
            integration.enable(IntegrationSettings(port = port, apiKey = apiKey))

            // Keep the JVM alive while Ktor/download coroutines run.
            kotlinx.coroutines.awaitCancellation()
        }
    }
}
