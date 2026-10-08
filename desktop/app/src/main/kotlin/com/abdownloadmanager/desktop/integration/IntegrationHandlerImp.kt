package com.abdownloadmanager.desktop.integration

import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.repository.AppRepository
import com.abdownloadmanager.integration.IntegrationHandler
import com.abdownloadmanager.integration.model.*
import com.abdownloadmanager.shared.downloaderinui.BasicDownloadItem
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUiRegistry
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.pages.adddownload.ImportOptions
import com.abdownloadmanager.shared.pages.adddownload.SilentImportOptions
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.ApiKeyUtil
import com.abdownloadmanager.shared.storage.appsettings.BaseAppSettingsStorage
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.category.CategorySelectionMode
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.contexts.RemovedBy
import ir.amirab.downloader.downloaditem.contexts.User
import ir.amirab.downloader.downloaditem.hls.HLSDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.monitor.CompletedDownloadItemState
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.downloader.queue.QueueManager
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import ir.amirab.downloader.utils.OnDuplicateStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val HEADLESS_PROPERTY = "abdm.headless"

class IntegrationHandlerImp : IntegrationHandler, KoinComponent {
    private data class PartSpeedSample(val downloaded: Long, val timestamp: Long)
    private val partSpeedSamples = ConcurrentHashMap<String, PartSpeedSample>()
    val appComponent by inject<AppComponent>()
    val downloadSystem by inject<DownloadSystem>()
    val queueManager by inject<QueueManager>()
    val appSettings by inject<AppRepository>()
    private val downloaderInUiRegistry by inject<DownloaderInUiRegistry>()
    private val categoryManager by inject<CategoryManager>()
    private val rawSettings by inject<BaseAppSettingsStorage>()

    override suspend fun addDownloadByGui(
        request: AddDownloadsFromIntegration
    ) {
        if (System.getProperty(HEADLESS_PROPERTY) == "true") {
            // TrueNAS headless mode has no desktop window, so the GUI "add download"
            // dialog can never appear. Add captured browser downloads directly instead.
            val results = request.items.distinctBy { it.link }.map { item ->
                runCatching {
                    addDownload(NewDownloadTask(downloadSource = item, startDownload = true))
                }.onFailure { it.printStackTrace() }
            }
            // If nothing could be added, fail so the extension lets Chromium handle it.
            if (results.isNotEmpty() && results.none { it.isSuccess }) {
                throw results.first().exceptionOrNull()!!
            }
            return
        }
        val list = request.items
        val options = request.options
        appComponent.externalCredentialComingIntoApp(
            list.map {
                convertToDownloadSystemCredentials(it)
            },
            options = ImportOptions(
                silentImport = if (options.silentAdd) {
                    SilentImportOptions(
                        silentDownload = options.silentStart
                    )
                } else null
            )
        )
    }

    override fun getSettings(): ApiSettingsModel = ApiSettingsModel(
        downloadFolder = appSettings.saveLocation.value,
        maxConcurrentDownloads = appSettings.maxConcurrentDownloads.value,
        threadCount = appSettings.threadCount.value,
        speedLimit = appSettings.speedLimiter.value,
        maxDownloadRetryCount = appSettings.maxDownloadRetryCount.value,
        dynamicPartCreation = appSettings.dynamicPartCreation.value,
        sparseFileAllocation = appSettings.useSparseFileAllocation.value,
        useAverageSpeed = appSettings.useAverageSpeed.value,
        autoStartOnBoot = rawSettings.autoStartOnBoot.value,
        useCategoryByDefault = rawSettings.useCategoryByDefault.value,
        apiEnabled = appSettings.apiEnabled.value,
        apiPort = appSettings.apiPort.value,
        apiAuthEnabled = appSettings.apiAuthEnabled.value,
        trackDeletedFilesOnDisk = appSettings.trackDeletedFilesOnDisk.value,
        deletePartialFileOnDownloadCancellation = rawSettings.deletePartialFileOnDownloadCancellation.value,
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
                require(ApiKeyUtil.isValidKey(appSettings.apiAuthKey.value)) { "A valid API key is required when API authentication is enabled" }
            }
        } else if (!apiKey.isNullOrBlank()) {
            require(ApiKeyUtil.isValidKey(apiKey)) { "Invalid API key" }
            appSettings.apiAuthKey.value = apiKey
        }
        appSettings.saveLocation.value = settings.downloadFolder
        appSettings.maxConcurrentDownloads.value = settings.maxConcurrentDownloads
        appSettings.threadCount.value = settings.threadCount
        appSettings.speedLimiter.value = settings.speedLimit
        appSettings.maxDownloadRetryCount.value = settings.maxDownloadRetryCount
        appSettings.dynamicPartCreation.value = settings.dynamicPartCreation
        appSettings.useSparseFileAllocation.value = settings.sparseFileAllocation
        appSettings.useAverageSpeed.value = settings.useAverageSpeed
        rawSettings.autoStartOnBoot.value = settings.autoStartOnBoot
        rawSettings.useCategoryByDefault.value = settings.useCategoryByDefault
        appSettings.apiPort.value = settings.apiPort
        appSettings.apiAuthEnabled.value = settings.apiAuthEnabled
        appSettings.trackDeletedFilesOnDisk.value = settings.trackDeletedFilesOnDisk
        rawSettings.deletePartialFileOnDownloadCancellation.value = settings.deletePartialFileOnDownloadCancellation
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
        val root = java.io.File(appSettings.saveLocation.value).canonicalFile
        val relative = path?.trim()?.trim('/') ?: ""
        val target = java.io.File(root, relative).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + java.io.File.separator)) { "Invalid browser path" }
        require(target.isDirectory) { "Not a directory" }
        val parent = if (target.path == root.path) null else target.relativeTo(root).path.replace(java.io.File.separatorChar, '/').let { value ->
            value.substringBeforeLast('/', "").ifBlank { null }
        }
        val items = target.listFiles()?.sortedWith(compareBy<java.io.File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?.map {
                val itemPath = it.relativeTo(root).path.replace(java.io.File.separatorChar, '/')
                ApiBrowserItem(it.name, itemPath, it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified())
            } ?: emptyList()
        return ApiBrowserResponse(relative, parent, items)
    }

    override fun listCategories(): List<ApiCategoryModel> = categoryManager.getCategories().map {
        ApiCategoryModel(it.id, it.name, it.path, it.usePath, it.acceptedFileTypes, it.acceptedUrlPatterns, it.items, categoryManager.isDefaultCategory(it))
    }

    override suspend fun addCategory(name: String, path: String, usePath: Boolean, fileTypes: List<String>, urlPatterns: List<String>): Long {
        require(name.isNotBlank())
        require(!usePath || path.isNotBlank())
        val category = Category(-1L, name.trim(), "", path.trim(), usePath, fileTypes.map { it.trim().trimStart('.') }.filter { it.isNotBlank() }, urlPatterns.map { it.trim() }.filter { it.isNotBlank() })
        categoryManager.addCustomCategory(category)
        return categoryManager.getCategories().maxByOrNull { it.id }?.id ?: error("Unable to determine new category ID")
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
                    id = item.id, name = item.name, folder = item.folder,
                    size = item.contentLength, progress = item.progress,
                    percent = item.percent, speed = item.speed,
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
                    dateAdded = item.dateAdded, startTime = item.startTime,
                    completeTime = item.completeTime,
                    queueId = membership?.first,
                    queueName = membership?.second,
                    connections = item.parts.count { it.status is PartDownloadStatus.IsActive },
                    maxConnections = appSettings.threadCount.value,
                )
                is CompletedDownloadItemState -> ApiDownloadModel(
                    id = item.id, name = item.name, folder = item.folder,
                    size = item.contentLength, progress = item.contentLength,
                    percent = 100, speed = 0, eta = 0,
                    status = "Completed", downloadLink = item.downloadLink,
                    dateAdded = item.dateAdded, startTime = item.startTime,
                    completeTime = item.completeTime,
                    queueId = membership?.first,
                    queueName = membership?.second,
                )
            }
        }.sortedByDescending { it.dateAdded }
    }

    override fun listDownloadParts(id: Long): List<ApiDownloadPart> {
        val item = downloadSystem.downloadMonitor.downloadListFlow.value.firstOrNull { it.id == id }
            ?: return emptyList()
        if (item !is ProcessingDownloadItemState) return emptyList()
        val now = System.currentTimeMillis()
        val activeKeys = HashSet<String>()
        val parts = item.parts.sortedBy { it.id }.map { part ->
            val key = "$id:${part.id}"
            activeKeys += key
            val downloaded = part.howMuchProceed
            val previous = partSpeedSamples.put(key, PartSpeedSample(downloaded, now))
            val speed = if (previous == null) {
                0L
            } else {
                val elapsed = now - previous.timestamp
                if (elapsed <= 0L || downloaded <= previous.downloaded) 0L
                else ((downloaded - previous.downloaded) * 1000L / elapsed).coerceAtLeast(0L)
            }
            val status = when (part.status) {
                PartDownloadStatus.IDLE -> "Idle"
                PartDownloadStatus.Connecting -> "Connecting"
                PartDownloadStatus.ReceivingData -> "Receiving data"
                PartDownloadStatus.Completed -> "Finished"
                is PartDownloadStatus.Canceled -> "Disconnected"
            }
            val error = (part.status as? PartDownloadStatus.Canceled)?.e?.message
            ApiDownloadPart(
                id = part.id,
                downloaded = downloaded,
                size = part.length,
                percent = part.percent,
                speed = speed,
                status = status,
                error = error,
            )
        }
        partSpeedSamples.keys.removeIf { it.startsWith(id.toString() + ":") && it !in activeKeys }
        return parts
    }

    override suspend fun updateDownload(id: Long, link: String, preferredConnectionCount: Int?) {
        require(link.isNotBlank())
        require(preferredConnectionCount == null || preferredConnectionCount in 1..128)
        val info = inspectDownloadLinks(link).firstOrNull()
        val resolvedName = info?.name?.takeIf { it.isNotBlank() && it != "download" } ?: suggestedFileName(link)
        downloadSystem.editDownload(id, { item ->
            item.link = link
            item.preferredConnectionCount = preferredConnectionCount
            if (item.name.isBlank() || item.name == "download" || item.name.startsWith("http://") || item.name.startsWith("https://")) {
                item.name = resolvedName
            }
        }, null)
    }

    override suspend fun inspectDownloadLinks(text: String): List<ApiLinkInfo> {
        val client = okhttp3.OkHttpClient()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .map { url ->
                try {
                    var response = client.newCall(okhttp3.Request.Builder().url(url).head().build()).execute()
                    if (!response.isSuccessful && response.code == 405) {
                        response.close()
                        response = client.newCall(
                            okhttp3.Request.Builder().url(url).header("Range", "bytes=0-0").build()
                        ).execute()
                    }
                    response.use {
                        val length = it.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull()
                            ?: it.header("Content-Length")?.toLongOrNull()
                        val name = contentDispositionFileName(it.header("Content-Disposition"))
                            ?: suggestedFileName(it.request.url.toString())
                        ApiLinkInfo(url, name, length, length?.let { size -> formatSize(size) }, if (it.isSuccessful) "Ready" else "HTTP " + it.code, null)
                    }
                } catch (e: Exception) {
                    ApiLinkInfo(url, suggestedFileName(url), null, null, "Error", e.message)
                }
            }
            .toList()
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
        downloadSystem.removeDownload(
            id = id,
            alsoRemoveFile = alsoRemoveFile,
            context = RemovedBy(User),
        )
    }

    override fun startQueue(id: Long) { queueManager.getQueue(id).start() }

    override fun stopQueue(id: Long) { queueManager.getQueue(id).stop() }

    override suspend fun addQueue(name: String): Long { require(name.isNotBlank()); queueManager.addQueue(name.trim()); return queueManager.getAll().maxOf { it.id } }
    override suspend fun deleteQueue(id: Long) { require(queueManager.canDelete(id)) { "Queue cannot be deleted" }; queueManager.deleteQueue(id) }
    override suspend fun renameQueue(id: Long, name: String) { require(name.isNotBlank()); queueManager.getQueue(id).setName(name.trim()) }
    override suspend fun setQueueConcurrency(id: Long, maxConcurrent: Int) { require(maxConcurrent in 1..128); queueManager.getQueue(id).setMaxConcurrent(maxConcurrent) }

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

    override suspend fun assignDownloadToQueue(downloadId: Long, queueId: Long) { queueManager.addToQueue(queueId, downloadId) }
    override suspend fun removeDownloadFromQueue(downloadId: Long) { queueManager.findItemInQueue(downloadId)?.let { queueManager.getQueue(it).removeFromQueue(downloadId) } }
    override suspend fun moveQueueItem(downloadId: Long, direction: Int) { val qid = queueManager.findItemInQueue(downloadId) ?: return; queueManager.getQueue(qid).move(listOf(downloadId), direction) }

    override suspend fun addDownload(task: NewDownloadTask): Long {
        require(task.downloadSource.link.isNotBlank())
        val addDownloaderInUiProps = convertToDownloadSystemCredentials(task.downloadSource)
        val downloaderInUi = downloaderInUiRegistry.getDownloaderOf(
            addDownloaderInUiProps.credentials
        ) ?: error("Downloader for ${addDownloaderInUiProps.credentials::class.qualifiedName} not found")
        val explicitName = task.name?.takeIf { it.isNotBlank() }
        // Same as the desktop "Add download" dialog: ask the server first (using the
        // browser's cookies/referer headers), so the name comes from Content-Disposition
        // or the final URL after redirects instead of the raw clicked link.
        val serverName = if (explicitName != null) null else runCatching {
            withTimeoutOrNull(30_000L) {
                val checker = downloaderInUi.createLinkChecker(addDownloaderInUiProps.credentials)
                checker.check()
                val info = checker.responseInfo.value
                if (info == null || !info.isSuccessFul || info.isWebPage) null
                else checker.suggestedName.value?.takeIf { it.isNotBlank() }
            }
        }.onFailure { it.printStackTrace() }.getOrNull()
        val downloadItem = downloaderInUi.createBareDownloadItem(
            addDownloaderInUiProps.credentials,
            basicDownloadItem = BasicDownloadItem(
                folder = task.folder?.takeIf { it.isNotBlank() } ?: appSettings.saveLocation.value,
                name = explicitName
                    ?: serverName
                    ?: addDownloaderInUiProps.extraConfig.suggestedName?.takeIf { it.isNotBlank() }
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
            ?: if (rawSettings.useCategoryByDefault.value) CategorySelectionMode.Auto else null
        val id = if (categorySelectionMode == null) {
            downloadSystem.addDownload(
                newDownload = newDownload,
                queueId = task.queueId,
                categoryId = null,
            )
        } else {
            downloadSystem.addDownload(
                newItemsToAdd = listOf(newDownload),
                queueId = task.queueId,
                categorySelectionMode = categorySelectionMode,
            ).single()
        }
        val queueId = task.queueId
        // either start the queue or manually start the download
        // both of them at the same time is not good idea
        if (task.startQueue && queueId != null) {
            val queue = queueManager.getQueue(queueId)
            queue.start()
        } else {
            if (task.startDownload) {
                downloadSystem.userManualResume(id)
            }
        }
        return id
    }

    private fun suggestedFileName(link: String): String {
        val raw = link.substringBefore("?").substringBefore("#").substringAfterLast("/").ifBlank { "download" }
        return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8) }.getOrDefault(raw).ifBlank { "download" }
    }

    private fun contentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val encoded = Regex("(?i)(?:^|;)\\s*filename\\*\\s*=\\s*(?:UTF-8''|\\\"?)([^;\\\"]+)").find(header)?.groupValues?.get(1)
        if (!encoded.isNullOrBlank()) {
            return runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }
        val plain = Regex("(?i)(?:^|;)\\s*filename\\s*=\\s*\\\"([^\\\"]+)\\\"").find(header)?.groupValues?.get(1)
            ?: Regex("(?i)(?:^|;)\\s*filename\\s*=\\s*([^;]+)").find(header)?.groupValues?.get(1)
        return plain?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun formatSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) "${value.toLong()} ${units[index]}" else "${String.format("%.1f", value)} ${units[index]}"
    }

    companion object {
        private fun convertToDownloadSystemCredentials(it: IDownloadCredentialsFromIntegration): AddDownloadCredentialsInUiProps {
            val credentials = when (it) {
                is HttpDownloadCredentialsFromIntegration -> {
                    HttpDownloadCredentials(
                        link = it.link,
                        headers = it.headers,
                        downloadPage = it.downloadPage,
                    )
                }

                is HLSDownloadCredentialsFromIntegration -> {
                    HLSDownloadCredentials(
                        link = it.link,
                        headers = it.headers,
                        downloadPage = it.downloadPage,
                    )
                }
            }
            return AddDownloadCredentialsInUiProps(
                credentials = credentials,
                extraConfig = AddDownloadCredentialsInUiProps.Configs(
                    suggestedName = it.suggestedName,
                )
            )
        }
    }
}
