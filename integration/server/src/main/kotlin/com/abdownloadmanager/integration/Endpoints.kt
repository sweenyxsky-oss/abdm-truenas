package com.abdownloadmanager.integration

import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiDownloadModel
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.NewDownloadTask
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.apikey.apiKey
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
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
            get("/downloads") {
                val downloads = integrationHandler.listDownloads()
                call.respondText(json.encodeToString(ListSerializer(ApiDownloadModel.serializer()), downloads))
            }
            get("/queues") {
                val queues = integrationHandler.listQueues()
                val jsonResponse = json.encodeToString(ListSerializer(ApiQueueModel.serializer()), queues)
                call.respondText(jsonResponse)
            }
            post("/queues") { val body=json.decodeFromString<Map<String,String>>(call.receiveText()); call.respondText(integrationHandler.addQueue(body["name"] ?: "New Queue").toString()) }
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
    }
}
