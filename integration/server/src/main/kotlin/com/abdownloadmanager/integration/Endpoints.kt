package com.abdownloadmanager.integration

import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiDownloadModel
import com.abdownloadmanager.integration.model.ApiDownloadPart
import com.abdownloadmanager.integration.model.ApiBrowserResponse
import com.abdownloadmanager.integration.model.ApiCategoryModel
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.ApiSettingsModel
import com.abdownloadmanager.integration.model.NewDownloadTask
import com.abdownloadmanager.integration.model.ApiLinkInfo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.server.http.content.staticFiles
import io.ktor.websocket.Frame
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.Socket
import java.util.UUID
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.apikey.apiKey
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.http.content.staticResources
import io.ktor.http.ContentType
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private data class AppPrincipal(val key: String)

internal fun Application.setupRouting(
    json: Json,
    integrationHandler: IntegrationHandler,
    settings: IntegrationSettings,
) {
    val apiKey = settings.apiKey
    val browserToken = UUID.randomUUID().toString()
    install(WebSockets) { maxFrameSize = Long.MAX_VALUE }
    install(Authentication) {
        apiKey {
            headerName = "X-API-Key"
            validate { receivedKey ->
                if (receivedKey == apiKey) {
                    AppPrincipal(receivedKey)
                } else null
            }
            skipWhen {
                apiKey == null
            }
        }
    }
    routing {
        authenticate {
            post("/add") {
                val itemsToAdd = kotlin.runCatching {
                    val message = call.receiveText()
                    AddDownloadsFromIntegration.createFromRequest(
                        json = json,
                        jsonData = message
                    )
                }
                itemsToAdd.onFailure { it.printStackTrace() }
                itemsToAdd.getOrThrow().let { newImportRequest ->
                    integrationHandler.addDownloadByGui(
                        AddDownloadsFromIntegration(
                            newImportRequest.items,
                            newImportRequest.options,
                        )
                    )
                }
                call.respondText("OK")
            }
            get("/settings") {
                call.respondText(json.encodeToString(ApiSettingsModel.serializer(), integrationHandler.getSettings()), ContentType.Application.Json)
            }
            post("/settings") {
                val body = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(call.receiveText())
                val settings = json.decodeFromJsonElement(ApiSettingsModel.serializer(), body["settings"] ?: error("Missing settings"))
                val apiKey = body["apiKey"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
                integrationHandler.updateSettings(settings, apiKey)
                call.respondText("OK")
            }
            get("/browser") {
                val path = call.request.queryParameters["path"]
                call.respondText(json.encodeToString(ApiBrowserResponse.serializer(), integrationHandler.browse(path)), ContentType.Application.Json)
            }
            get("/browser/session") {
                val vncReady = runCatching { Socket("127.0.0.1", 5900).use { true } }.getOrDefault(false)
                val chromiumReady = runCatching {
                    File("/config/system/browser/chromium.pid").readText().trim().toLongOrNull()?.let { pid -> ProcessHandle.of(pid).map { it.isAlive }.orElse(false) } ?: false
                }.getOrDefault(false)
                val ready = vncReady && chromiumReady
                if (!ready) {
                    call.respondText("{\"enabled\":false,\"ready\":false}", ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                } else {
                    call.respondText(
                        "{\"enabled\":true,\"ready\":true,\"token\":\"$browserToken\"}",
                        ContentType.Application.Json,
                    )
                }
            }
            get("/categories") {
                call.respondText(json.encodeToString(ListSerializer(ApiCategoryModel.serializer()), integrationHandler.listCategories()), ContentType.Application.Json)
            }
            post("/categories") {
                val body=json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(call.receiveText())
                val name=body["name"]?.toString()?.trim('"') ?: "New Category"
                val path=body["path"]?.toString()?.trim('"') ?: ""
                val usePath=body["usePath"]?.toString()?.toBooleanStrictOrNull() ?: true
                val fileTypes=body["fileTypes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val urlPatterns=body["urlPatterns"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                call.respondText(integrationHandler.addCategory(name,path,usePath,fileTypes,urlPatterns).toString(), ContentType.Application.Json)
            }
            route("/categories/{id}") {
                post("/rename") {
                    val body=json.decodeFromString<Map<String,String>>(call.receiveText())
                    integrationHandler.renameCategory(call.parameters["id"]!!.toLong(), body["name"] ?: "Category")
                    call.respondText("OK")
                }
                post("/delete") {
                    integrationHandler.deleteCategory(call.parameters["id"]!!.toLong())
                    call.respondText("OK")
                }
            }
            post("/import-links") { val body=json.decodeFromString<Map<String,String>>(call.receiveText()); call.respondText(json.encodeToString(ListSerializer(ApiLinkInfo.serializer()), integrationHandler.inspectDownloadLinks(body["text"].orEmpty())), ContentType.Application.Json) }
            get("/downloads") {
                val downloads = integrationHandler.listDownloads()
                call.respondText(json.encodeToString(ListSerializer(ApiDownloadModel.serializer()), downloads), ContentType.Application.Json)
            }
            get("/queues") {
                val queues = integrationHandler.listQueues()
                val jsonResponse = json.encodeToString(ListSerializer(ApiQueueModel.serializer()), queues)
                call.respondText(jsonResponse, ContentType.Application.Json)
            }
            post("/queues") { val body=json.decodeFromString<Map<String,String>>(call.receiveText()); call.respondText(integrationHandler.addQueue(body["name"] ?: "New Queue").toString(), ContentType.Application.Json) }
            route("/queues/{id}") {
                post("/start") {
                    integrationHandler.startQueue(call.parameters["id"]!!.toLong())
                    call.respondText("OK")
                }
                post("/stop") {
                    integrationHandler.stopQueue(call.parameters["id"]!!.toLong())
                    call.respondText("OK")
                }
                post("/delete") { integrationHandler.deleteQueue(call.parameters["id"]!!.toLong()); call.respondText("OK") }
                post("/rename") { val body=json.decodeFromString<Map<String,String>>(call.receiveText()); integrationHandler.renameQueue(call.parameters["id"]!!.toLong(), body["name"] ?: "Queue"); call.respondText("OK") }
                post("/concurrency") { val body=json.decodeFromString<Map<String,Int>>(call.receiveText()); integrationHandler.setQueueConcurrency(call.parameters["id"]!!.toLong(), body["maxConcurrent"] ?: 1); call.respondText("OK") }
                post("/schedule") {
                    val body = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(call.receiveText())
                    val id = call.parameters["id"]!!.toLong()
                    val enabled = body["enabled"]?.toString()?.toBooleanStrictOrNull() ?: false
                    val activeDays = body["activeDays"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val autoStartEnabled = body["autoStartEnabled"]?.toString()?.toBooleanStrictOrNull() ?: false
                    val startTime = body["startTime"]?.toString()?.trim('"') ?: "02:30"
                    val autoStopEnabled = body["autoStopEnabled"]?.toString()?.toBooleanStrictOrNull() ?: false
                    val endTime = body["endTime"]?.toString()?.trim('"') ?: "07:30"
                    val stopQueueOnEmpty = body["stopQueueOnEmpty"]?.toString()?.toBooleanStrictOrNull() ?: false
                    integrationHandler.setQueueSchedule(id, enabled, activeDays, autoStartEnabled, startTime, autoStopEnabled, endTime, stopQueueOnEmpty)
                    call.respondText("OK")
                }
            }
            route("/downloads/{id}") {
                get("/parts") {
                    val parts = integrationHandler.listDownloadParts(call.parameters["id"]!!.toLong())
                    call.respondText(json.encodeToString(ListSerializer(ApiDownloadPart.serializer()), parts), ContentType.Application.Json)
                }
                post {
                    val body=json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(call.receiveText())
                    val link=body["link"]?.toString()?.trim('"') ?: error("Missing link")
                    val connections=body["preferredConnectionCount"]?.toString()?.toIntOrNull()
                    integrationHandler.updateDownload(call.parameters["id"]!!.toLong(),link,connections)
                    call.respondText("OK")
                }
                post("/pause") {
                    integrationHandler.pauseDownload(call.parameters["id"]!!.toLong())
                    call.respondText("OK")
                }
                post("/resume") {
                    integrationHandler.resumeDownload(call.parameters["id"]!!.toLong())
                    call.respondText("OK")
                }
                post("/retry") {
                    integrationHandler.retryDownload(call.parameters["id"]!!.toLong())
                    call.respondText("OK")
                }
                post("/queue") { val body=json.decodeFromString<Map<String,Long>>(call.receiveText()); integrationHandler.assignDownloadToQueue(call.parameters["id"]!!.toLong(), body["queueId"] ?: 0L); call.respondText("OK") }
                post("/unqueue") { integrationHandler.removeDownloadFromQueue(call.parameters["id"]!!.toLong()); call.respondText("OK") }
                post("/move") { val body=json.decodeFromString<Map<String,Int>>(call.receiveText()); integrationHandler.moveQueueItem(call.parameters["id"]!!.toLong(), body["direction"] ?: 0); call.respondText("OK") }
                post("/remove") {
                    val removeFile = call.request.queryParameters["removeFile"]?.toBoolean() ?: false
                    integrationHandler.removeDownload(call.parameters["id"]!!.toLong(), removeFile)
                    call.respondText("OK")
                }
            }
            post("/start-headless-download") {
                val itemsToAdd = kotlin.runCatching {
                    val message = call.receiveText()
                    json.decodeFromString<NewDownloadTask>(message)
                }
                itemsToAdd.onFailure { it.printStackTrace() }
                integrationHandler.addDownload(itemsToAdd.getOrThrow())
                call.respondText("OK")
            }
            post("/ping") {
                call.respondText("pong")
            }
        }
        staticFiles("/browser/novnc", File("/usr/share/novnc"))
        webSocket("/browser/websockify") {
            if (call.request.queryParameters["token"] != browserToken) {
                close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Invalid browser session"))
                return@webSocket
            }
            val socket = runCatching { Socket("127.0.0.1", 5900) }.getOrElse {
                close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.INTERNAL_ERROR, "Browser display unavailable"))
                return@webSocket
            }
            socket.use { tcp ->
                val input = tcp.getInputStream()
                val output = tcp.getOutputStream()
                val tcpToWeb = launch(Dispatchers.IO) {
                    val buffer = ByteArray(16384)
                    try {
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            send(Frame.Binary(true, buffer.copyOf(count)))
                        }
                    } catch (_: Throwable) {
                    }
                }
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Binary -> output.write(frame.data)
                            is Frame.Text -> output.write(frame.readText().toByteArray(Charsets.ISO_8859_1))
                            else -> Unit
                        }
                        output.flush()
                    }
                } finally {
                    runCatching { tcp.close() }
                    tcpToWeb.cancel()
                    tcpToWeb.join()
                }
            }
        }
        staticResources("/", "web")
    }
}
