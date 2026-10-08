package com.abdownloadmanager.desktop.headless

import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.di.Di
import com.abdownloadmanager.desktop.integration.HEADLESS_PROPERTY
import com.abdownloadmanager.desktop.repository.AppRepository
import com.abdownloadmanager.desktop.utils.EntryType
import com.abdownloadmanager.desktop.utils.EntrypointInitializer
import com.abdownloadmanager.integration.Integration
import com.abdownloadmanager.integration.IntegrationResult
import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.HttpDownloadCredentialsFromIntegration
import com.abdownloadmanager.shared.util.DownloadSystem
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.abdownloadmanager.shared.storage.appsettings.BaseAppSettingsStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeadlessRuntimeTest {
    @Test
    fun coreStartsCapturesDownloadAndServesApiWithoutDesktopWindows() = runBlocking {
        val home = Files.createTempDirectory("abdm-headless-test").toFile()
        System.setProperty("user.home", home.path)
        System.setProperty("java.awt.headless", "true")
        System.setProperty(HEADLESS_PROPERTY, "true")
        val payload = "ABDM headless download regression test".toByteArray()
        val source = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        source.createContext("/download") { exchange ->
            exchange.responseHeaders.add("Content-Disposition", "attachment; filename=server-name.txt")
            if (exchange.requestMethod == "HEAD") {
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.sendResponseHeaders(200, -1)
            } else {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            exchange.close()
        }
        source.start()
        try {
            EntrypointInitializer.boot(entryType = EntryType.CLI)
            Di.boot(entryType = EntryType.CLI)
            val koin = GlobalContext.get()
            assertNull(koin.getOrNull<AppComponent>(), "TrueNAS must not register desktop windows")
            val repository = koin.get<AppRepository>()
            val system = koin.get<DownloadSystem>()
            val integration = koin.get<Integration>()
            repository.apiEnabled.value = false
            repository.saveLocation.value = home.resolve("downloads").path
            repository.boot()
            system.boot()
            koin.get<BaseAppSettingsStorage>().useCategoryByDefault.value = false
            val monitor = launch { system.downloadMonitor.downloadListFlow.collect { } }
            assertTrue(integration.integrationHandler.listCategories().isNotEmpty())
            integration.integrationHandler.addDownloadByGui(AddDownloadsFromIntegration(listOf(
                HttpDownloadCredentialsFromIntegration("http://127.0.0.1:${source.address.port}/download", suggestedName = "wrong-name.bin")
            )))
            withTimeout(15_000) {
                while (integration.integrationHandler.listDownloads().isEmpty()) delay(100)
            }
            val captured = integration.integrationHandler.listDownloads()
            assertEquals(1, captured.size)
            assertEquals("server-name.txt", captured.first().name)
            withTimeout(15_000) {
                while (!home.resolve("downloads/server-name.txt").exists()) delay(100)
                while (!home.resolve("downloads/server-name.txt").readBytes().contentEquals(payload)) delay(100)
            }
            integration.boot()
            val port = ServerSocket(0).use { it.localPort }
            repository.apiPort.value = port
            repository.apiAuthEnabled.value = false
            repository.apiEnabled.value = true
            withTimeout(15_000) {
                while (integration.integrationStatus.value !is IntegrationResult.Success) delay(100)
            }
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/ping")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, response.statusCode())
            assertEquals("pong", response.body())
            repository.apiEnabled.value = false
            monitor.cancel()
            withTimeout(15_000) {
                while (integration.integrationStatus.value !is IntegrationResult.Inactive) delay(100)
            }
        } finally {
            source.stop(0)
            GlobalContext.getOrNull()?.get<CoroutineScope>()?.cancel()
            stopKoin()
            home.deleteRecursively()
            System.clearProperty(HEADLESS_PROPERTY)
        }
    }
}
