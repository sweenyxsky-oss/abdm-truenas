package com.abdownloadmanager.truenas.integration

import com.abdownloadmanager.truenas.repository.TrueNasRepository
import com.abdownloadmanager.integration.IntegrationHandler
import com.abdownloadmanager.integration.model.*
import com.abdownloadmanager.shared.downloaderinui.BasicDownloadItem
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUiRegistry
import com.abdownloadmanager.shared.storage.appsettings.BaseTrueNasSettingsStorage
import com.abdownloadmanager.shared.util.ApiKeyUtil
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.category.CategorySelectionMode
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.contexts.RemovedBy
import ir.amirab.downloader.downloaditem.contexts.User
import ir.amirab.downloader.downloaditem.hls.HLSDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.monitor.CompletedDownloadItemState
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.utils.OnDuplicateStrategy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TrueNasIntegrationHandler : IntegrationHandler, KoinComponent {
    private val downloadSystem by inject<DownloadSystem>()
    private val queueManager by inject<QueueManager>()
    private val appSettings by inject<BaseTrueNasSettingsStorage>()
    private val downloaderInUiRegistry by inject<DownloaderInUiRegistry>()
    private val categoryManager by inject<CategoryManager>()
    private val httpClient by inject<OkHttpClient>()
    private val failedDownloadErrors by inject<com.abdownloadmanager.shared.util.downloaderror.faileddownloads.IFailedDownloadErrorStorage>()

    override suspend fun addDownloadByGui(request: AddDownloadsFromIntegration) {
        request.items.forEach { item ->
            val props = convertToDownloadSystemCredentials(item)
            val downloaderInUi = downloaderInUiRegistry.getDownloaderOf(props.credentials)
                ?: error("Downloader for ${props.credentials::class.qualifiedName} not found")
            val downloadItem = downloaderInUi.createBareDownloadItemFromCredentials(
                props.credentials,
                basicDownloadItem = BasicDownloadItem(
                    folder = appSettings.defaultDownloadFolder.value,
                    name = props.suggestedName?.takeIf { it.isNotBlank() }
                        ?: props.credentials.link.substringAfterLast("/").ifBlank { "download" },
                ),
            )
            val id = downloadSystem.addDownload(
                newDownload = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    onDuplicateStrategy = OnDuplicateStrategy.default(),
                    extraConfig = null,
                    context = EmptyContext,
                ),
                queueId = null,
                categoryId = null,
            )
            if (request.options.silentStart) {
                downloadSystem.userManualResume(id)
            }
        }
    }

    override fun getSettings(): ApiSettingsModel = ApiSettingsModel(
        downloadFolder = appSettings.defaultDownloadFolder.value,
        maxConcurrentDownloads = appSettings.maxConcurrentDownloads.value,
        threadCount = appSettings.threadCount.value,
        speedLimit = appSettings.speedLimit.value,
        maxDownloadRetryCount = appSettings.maxDownloadRetryCount.value,
        dynamicPartCreation = appSettings.dynamicPartCreation.value,
        sparseFileAllocation = appSettings.useSparseFileAllocation.value,
        useAverageSpeed = appSettings.useAverageSpeed.value,
        autoStartOnBoot = appSettings.autoStartOnBoot.value,
        useCategoryByDefault = appSettings.useCategoryByDefault.value,
        apiEnabled = appSettings.apiEnabled.value,
        apiPort = appSettings.apiPort.value,
        apiAuthEnabled = appSettings.apiAuthEnabled.value,
        trackDeletedFilesOnDisk = appSettings.trackDeletedFilesOnDisk.value,
        deletePartialFileOnDownloadCancellation = appSettings.deletePartialFileOnDownloadCancellation.value,
    )

    override suspend fun updateSettings(settings: ApiSettingsModel, apiKey: String?) {
        require(settings.downloadFolder.isNotBlank())
        require(settings.maxConcurrentDownloads in 1..128)
        require(settings.threadCount in 1..128)
        require(settings.speedLimit >= 0)
        require(settings.maxDownloadRetryCount in 0..100)
        require(settings.apiPort in 1..65535)

        if (settings.apiAuthEnabled) {
            if (!apiKey.isNullOrBlank()) {
                require(ApiKeyUtil.isValidKey(apiKey)) { "Invalid API key" }
                appSettings.apiAuthKey.value = apiKey
            } else {
                require(ApiKeyUtil.isValidKey(appSettings.apiAuthKey.value)) {
                    "A valid API key is required when API authentication is enabled"
                }
            }
        } else if (!apiKey.isNullOrBlank()) {
            require(ApiKeyUtil.isValidKey(apiKey)) { "Invalid API key" }
            appSettings.apiAuthKey.value = apiKey
        }

        appSettings.defaultDownloadFolder.value = settings.downloadFolder
        appSettings.maxConcurrentDownloads.value = settings.maxConcurrentDownloads
        appSettings.threadCount.value = settings.threadCount
        appSettings.speedLimit.value = settings.speedLimit
        appSettings.maxDownloadRetryCount.value = settings.maxDownloadRetryCount
        appSettings.dynamicPartCreation.value = settings.dynamicPartCreation
        appSettings.useSparseFileAllocation.value = settings.sparseFileAllocation
        appSettings.useAverageSpeed.value = settings.useAverageSpeed
        appSettings.autoStartOnBoot.value = settings.autoStartOnBoot
        appSettings.useCategoryByDefault.value = settings.useCategoryByDefault
        appSettings.apiPort.value = settings.apiPort
        appSettings.apiAuthEnabled.value = settings.apiAuthEnabled
        appSettings.trackDeletedFilesOnDisk.value = settings.trackDeletedFilesOnDisk
        appSettings.deletePartialFileOnDownloadCancellation.value = settings.deletePartialFileOnDownloadCancellation
        appSettings.apiEnabled.value = settings.apiEnabled
    }

    override fun listQueues(): List<ApiQueueModel> {
        val statesById = downloadSystem.downloadMonitor.downloadListFlow.value.associateBy { it.id }
        return queueManager.getAll().map { downloadQueue ->
            val queueModel = downloadQueue.getQueueModel()
            ApiQueueModel(
                id = queueModel.id,
                name = queueModel.name,
                active = queueModel.queueItems.count { id ->
                    val state = statesById[id]
                    state is ProcessingDownloadItemState &&
                        (state.status is DownloadJobStatus.Downloading || state.status is DownloadJobStatus.Resuming)
                },
                queued = queueModel.queueItems.count { id ->
                    val state = statesById[id]
                    state is ProcessingDownloadItemState && state.status == DownloadJobStatus.IDLE
                },
                total = queueModel.queueItems.size,
                running = downloadQueue.isQueueActive,
                maxConcurrent = queueModel.maxConcurrent,
                items = queueModel.queueItems,
                schedulerEnabled = queueModel.scheduledTimes.enabledStartTime || queueModel.scheduledTimes.enabledEndTime,
                activeDays = queueModel.scheduledTimes.daysOfWeek.map { it.name },
                autoStartEnabled = queueModel.scheduledTimes.enabledStartTime,
                startTime = queueModel.scheduledTimes.startTime.toString(),
                autoStopEnabled = queueModel.scheduledTimes.enabledEndTime,
                endTime = queueModel.scheduledTimes.endTime.toString(),
                stopQueueOnEmpty = queueModel.stopQueueOnEmpty,
            )
        }
    }

    override fun browse(path: String?): ApiBrowserResponse {
        val root = java.io.File(appSettings.defaultDownloadFolder.value).canonicalFile
        val relative = path?.trim()?.trim('/') ?: ""
        val target = java.io.File(root, relative).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + java.io.File.separator)) { "Invalid browser path" }
        require(target.isDirectory) { "Not a directory" }

        val parent = if (target.path == root.path) null else target.relativeTo(root).path
            .replace(java.io.File.separatorChar, '/')
            .let { value -> value.substringBeforeLast('/', "").ifBlank { null } }

        val items = target.listFiles()
            ?.sortedWith(compareBy<java.io.File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?.map {
                val itemPath = it.relativeTo(root).path.replace(java.io.File.separatorChar, '/')
                ApiBrowserItem(it.name, itemPath, it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified())
            } ?: emptyList()

        return ApiBrowserResponse(relative, parent, items)
    }

    override fun listCategories(): List<ApiCategoryModel> = categoryManager.getCategories().map {
        ApiCategoryModel(
            it.id,
            it.name,
            it.path,
            it.usePath,
            it.acceptedFileTypes,
            it.acceptedUrlPatterns,
            it.items,
            categoryManager.isDefaultCategory(it),
        )
    }

    override suspend fun addCategory(
        name: String,
        path: String,
        usePath: Boolean,
        fileTypes: List<String>,
        urlPatterns: List<String>,
    ): Long {
        require(name.isNotBlank())
        require(!usePath || path.isNotBlank())
        val category = Category(
            -1L,
            name.trim(),
            "",
            path.trim(),
            usePath,
            fileTypes.map { it.trim().trimStart('.') }.filter { it.isNotBlank() },
            urlPatterns.map { it.trim() }.filter { it.isNotBlank() },
        )
        categoryManager.addCustomCategory(category)
        return categoryManager.getCategories().maxByOrNull { it.id }?.id
            ?: error("Unable to determine new category ID")
    }

    override suspend fun renameCategory(id: Long, name: String) {
        require(name.isNotBlank())
        categoryManager.updateCategory(id) { it.copy(name = name.trim()) }
    }

    override suspend fun deleteCategory(id: Long) {
        require(!categoryManager.isDefaultCategory(categoryManager.getCategoryById(id) ?: error("Category not found")))
        categoryManager.deleteCategory(id)
    }

    override fun listDownloads(): List<ApiDownloadModel> {
        val queueMembership = queueManager.getAll().flatMap { queue ->
            val model = queue.getQueueModel()
            model.queueItems.map { itemId -> itemId to (model.id to model.name) }
        }.toMap()

        return downloadSystem.downloadMonitor.downloadListFlow.value.map { item ->
            val membership = queueMembership[item.id]
            when (item) {
                is ProcessingDownloadItemState -> ApiDownloadModel(
                    id = item.id,
                    name = item.name,
                    folder = item.folder,
                    size = item.contentLength,
                    progress = item.progress,
                    percent = item.percent,
                    speed = item.speed,
                    eta = item.remainingTime,
                    status = when (item.status) {
                        is DownloadJobStatus.Downloading, is DownloadJobStatus.Resuming -> "Downloading"
                        is DownloadJobStatus.PreparingFile -> "Preparing"
                        is DownloadJobStatus.Retrying -> "Retrying"
                        is DownloadJobStatus.Canceled -> "Paused"
                        DownloadJobStatus.IDLE -> "Queued"
                        DownloadJobStatus.Finished -> "Completed"
                    },
                    downloadLink = item.downloadLink,
                    dateAdded = item.dateAdded,
                    startTime = item.startTime,
                    completeTime = item.completeTime,
                    queueId = membership?.first,
                    queueName = membership?.second,
                    connections = item.parts.count { it.status is ir.amirab.downloader.part.PartDownloadStatus.IsActive },
                    maxConnections = appSettings.threadCount.value,
                    categoryName = categoryManager.getCategoryOfItem(item.id)?.name,
                    errorTitle = failedDownloadErrors.reasons.value[item.id]?.title,
                    errorDescription = failedDownloadErrors.reasons.value[item.id]?.description,
                    errorSuggestion = failedDownloadErrors.reasons.value[item.id]?.suggestion,
                    errorMessage = failedDownloadErrors.reasons.value[item.id]?.throwableMessage,
                )

                is CompletedDownloadItemState -> ApiDownloadModel(
                    id = item.id,
                    name = item.name,
                    folder = item.folder,
                    size = item.contentLength,
                    progress = item.contentLength,
                    percent = 100,
                    speed = 0,
                    eta = 0,
                    status = "Completed",
                    downloadLink = item.downloadLink,
                    dateAdded = item.dateAdded,
                    startTime = item.startTime,
                    completeTime = item.completeTime,
                    queueId = membership?.first,
                    queueName = membership?.second,
                    categoryName = categoryManager.getCategoryOfItem(item.id)?.name,
                )
            }
        }.sortedByDescending { it.dateAdded }
    }

    override suspend fun pauseDownload(id: Long) {
        downloadSystem.manualPause(id)
    }

    override suspend fun resumeDownload(id: Long) {
        downloadSystem.userManualResume(id)
    }

    override suspend fun retryDownload(id: Long) {
        downloadSystem.reset(id)
        downloadSystem.userManualResume(id)
    }

    override suspend fun removeDownload(id: Long, alsoRemoveFile: Boolean) {
        downloadSystem.removeDownload(id, alsoRemoveFile, RemovedBy(User))
    }

    override fun startQueue(id: Long) {
        queueManager.getQueue(id).start()
    }

    override fun stopQueue(id: Long) {
        queueManager.getQueue(id).stop()
    }

    override suspend fun addQueue(name: String): Long {
        require(name.isNotBlank())
        queueManager.addQueue(name.trim())
        return queueManager.getAll().maxOf { it.id }
    }

    override suspend fun deleteQueue(id: Long) {
        require(queueManager.canDelete(id)) { "Queue cannot be deleted" }
        queueManager.deleteQueue(id)
    }

    override suspend fun renameQueue(id: Long, name: String) {
        require(name.isNotBlank())
        queueManager.getQueue(id).setName(name.trim())
    }

    override suspend fun setQueueConcurrency(id: Long, maxConcurrent: Int) {
        require(maxConcurrent in 1..128)
        queueManager.getQueue(id).setMaxConcurrent(maxConcurrent)
    }

    override suspend fun setQueueSchedule(
        id: Long,
        enabled: Boolean,
        activeDays: List<String>,
        autoStartEnabled: Boolean,
        startTime: String,
        autoStopEnabled: Boolean,
        endTime: String,
        stopQueueOnEmpty: Boolean,
    ) {
        val queue = queueManager.getQueue(id)
        val current = queue.getQueueModel().scheduledTimes
        val days = activeDays.map { DayOfWeek.valueOf(it) }.toSet().ifEmpty { current.daysOfWeek }
        queue.setScheduledTimes {
            copy(
                daysOfWeek = days,
                startTime = LocalTime.parse(startTime),
                endTime = LocalTime.parse(endTime),
                enabledStartTime = enabled && autoStartEnabled,
                enabledEndTime = enabled && autoStopEnabled,
            )
        }
        queue.setStopQueueOnEmpty(stopQueueOnEmpty)
    }

    override suspend fun assignDownloadToQueue(downloadId: Long, queueId: Long) {
        queueManager.addToQueue(queueId, downloadId)
    }

    override suspend fun removeDownloadFromQueue(downloadId: Long) {
        queueManager.findItemInQueue(downloadId)?.let { queueManager.getQueue(it).removeFromQueue(downloadId) }
    }

    override suspend fun moveQueueItem(downloadId: Long, direction: Int) {
        val qid = queueManager.findItemInQueue(downloadId) ?: return
        queueManager.getQueue(qid).move(listOf(downloadId), direction)
    }

    override suspend fun updateDownload(id: Long, link: String, preferredConnectionCount: Int?) {\n        require(link.isNotBlank())\n        require(preferredConnectionCount == null || preferredConnectionCount in 1..128)\n        downloadSystem.editDownload(id, { item ->\n            item.link = link\n            item.preferredConnectionCount = preferredConnectionCount\n            if (item.name.isBlank() || item.name == "download") item.name = suggestedFileName(link)\n        }, null)\n    }\n\n    override suspend fun inspectDownloadLinks(text: String): List<ApiLinkInfo> {\n        return text.lineSequence().map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }.distinct().map { link ->\n            runCatching {\n                var response = httpClient.newCall(Request.Builder().url(link).head().build()).execute()
                if (!response.isSuccessful && response.code == 405) { response.close(); response = httpClient.newCall(Request.Builder().url(link).header("Range", "bytes=0-0").build()).execute() }
                response.use {
                    val size = it.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull()
                        ?: it.header("Content-Length")?.toLongOrNull()
                    val name = suggestedFileName(link)\n                    ApiLinkInfo(link, name, size, size?.let(::formatSize), if (it.isSuccessful) "Ready" else "HTTP " + it.code, null)\n                }\n            }.getOrElse { error -> ApiLinkInfo(link, suggestedFileName(link), null, null, "Unavailable", error.message) }\n        }.toList()\n    }\n\n    override suspend fun addDownload(task: NewDownloadTask): Long {
        require(task.downloadSource.link.isNotBlank())
        val props = convertToDownloadSystemCredentials(task.downloadSource)
        val downloaderInUi = downloaderInUiRegistry.getDownloaderOf(props.credentials)
            ?: error("Downloader for ${props.credentials::class.qualifiedName} not found")
        val downloadItem = downloaderInUi.createBareDownloadItemFromCredentials(
            props.credentials,
            basicDownloadItem = BasicDownloadItem(
                folder = task.folder?.takeIf { it.isNotBlank() } ?: appSettings.defaultDownloadFolder.value,
                name = task.name?.takeIf { it.isNotBlank() }
                    ?: props.suggestedName?.takeIf { it.isNotBlank() }
                    ?: suggestedFileName(task.downloadSource.link),
            ),
        )
        val newDownload = NewDownloadItemProps(
            downloadItem = downloadItem,
            onDuplicateStrategy = OnDuplicateStrategy.default(),
            extraConfig = null,
            context = EmptyContext,
        )
        val categorySelectionMode = task.categoryId?.let { CategorySelectionMode.Fixed(it) }
            ?: if (appSettings.useCategoryByDefault.value) CategorySelectionMode.Auto else null

        val id = if (categorySelectionMode == null) {
            downloadSystem.addDownload(newDownload, task.queueId, null)
        } else {
            downloadSystem.addDownload(
                newItemsToAdd = listOf(newDownload),
                queueId = task.queueId,
                categorySelectionMode = categorySelectionMode,
            ).single()
        }

        if (task.startQueue && task.queueId != null) {
            queueManager.getQueue(task.queueId).start()
        } else if (task.startDownload) {
            downloadSystem.userManualResume(id)
        }
        return id
    }

    private data class ConvertedCredentials(
        val credentials: IDownloadCredentials,
        val suggestedName: String?,
    )

    private fun convertToDownloadSystemCredentials(it: IDownloadCredentialsFromIntegration): ConvertedCredentials {
        val credentials = when (it) {
            is HttpDownloadCredentialsFromIntegration -> HttpDownloadCredentials(
                link = it.link,
                headers = it.headers,
                downloadPage = it.downloadPage,
            )

            is HLSDownloadCredentialsFromIntegration -> HLSDownloadCredentials(
                link = it.link,
                headers = it.headers,
                downloadPage = it.downloadPage,
            )
        }
        return ConvertedCredentials(credentials, it.suggestedName)
    }
}

private fun suggestedFileName(link: String): String { val raw=link.substringBefore("?").substringBefore("#").substringAfterLast("/").ifBlank { "download" }; return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8) }.getOrDefault(raw).ifBlank { "download" } }
private fun formatSize(size: Long): String { if(size<0)return "Unknown size"; val units=arrayOf("B","KB","MB","GB","TB"); var value=size.toDouble(); var index=0; while(value>=1024&&index<units.lastIndex){value/=1024;index++}; return if(index==0) "$size B" else String.format(java.util.Locale.US,"%.1f %s",value,units[index]) }
