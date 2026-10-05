package com.abdownloadmanager.integration

import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiDownloadModel
import com.abdownloadmanager.integration.model.ApiBrowserResponse
import com.abdownloadmanager.integration.model.ApiCategoryModel
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.ApiSettingsModel
import com.abdownloadmanager.integration.model.NewDownloadTask
import io.ktor.server.application.Application
import io.ktor.server.application.install
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

private data class AppPrincipal(val key: String)

internal fun Application.setupRouting(
    json: Json,
    integrationHandler: IntegrationHandler,
    settings: IntegrationSettings,
) {
    val apiKey = settings.apiKey
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
                call.respondText(json.encodeToString(ApiSettingsModel.serializer(), integrationHandler.getSettings(, ContentType.Application.Json))
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
                call.respondText(json.encodeToString(ApiBrowserResponse.serializer(), integrationHandler.browse(path, ContentType.Application.Json))
            }
            get("/categories") {
                call.respondText(json.encodeToString(ListSerializer(ApiCategoryModel.serializer(, ContentType.Application.Json), integrationHandler.listCategories()))
            }
            post("/categories") {
                val body=json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(call.receiveText())
                val name=body["name"]?.toString()?.trim('"') ?: "New Category"
                val path=body["path"]?.toString()?.trim('"') ?: ""
                val usePath=body["usePath"]?.toString()?.toBooleanStrictOrNull() ?: true
                val fileTypes=body["fileTypes"]?.let { json.decodeFromJsonElement<List<String>>(it) } ?: emptyList()
                val urlPatterns=body["urlPatterns"]?.let { json.decodeFromJsonElement<List<String>>(it) } ?: emptyList()
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
            get("/downloads") {
                val downloads = integrationHandler.listDownloads()
                call.respondText(json.encodeToString(ListSerializer(ApiDownloadModel.serializer()), downloads))
            }
            get("/queues") {
                val queues = integrationHandler.listQueues()
                val jsonResponse = json.encodeToString(ListSerializer(ApiQueueModel.serializer()), queues)
                call.respondText(jsonResponse)
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
                    val activeDays = body["activeDays"]?.let { json.decodeFromJsonElement<List<String>>(it) } ?: emptyList()
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
        staticResources("/", "web")
    }
}
