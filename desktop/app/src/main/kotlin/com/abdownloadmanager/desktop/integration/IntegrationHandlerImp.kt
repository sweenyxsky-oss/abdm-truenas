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
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.contexts.RemovedBy
import ir.amirab.downloader.downloaditem.contexts.User
import ir.amirab.downloader.downloaditem.hls.HLSDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.monitor.CompletedDownloadItemState
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.queue.QueueManager
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import ir.amirab.downloader.utils.OnDuplicateStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class IntegrationHandlerImp : IntegrationHandler, KoinComponent {
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
            require(!apiKey.isNullOrBlank() && ApiKeyUtil.isValidKey(apiKey)) { "A valid API key is required when API authentication is enabled" }
            appSettings.apiAuthKey.value = apiKey
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
        appSettings.appSettings.autoStartOnBoot.value = settings.autoStartOnBoot
        appSettings.appSettings.useCategoryByDefault.value = settings.useCategoryByDefault
        appSettings.apiPort.value = settings.apiPort
        appSettings.apiAuthEnabled.value = settings.apiAuthEnabled
        appSettings.trackDeletedFilesOnDisk.value = settings.trackDeletedFilesOnDisk
        appSettings.appSettings.deletePartialFileOnDownloadCancellation.value = settings.deletePartialFileOnDownloadCancellation
        appSettings.apiEnabled.value = settings.apiEnabled
    }

    override fun listQueues(): List<ApiQueueModel> {
        return queueManager.getAll().map { downloadQueue ->
            val queueModel = downloadQueue.getQueueModel()
            ApiQueueModel(
                id = queueModel.id,
                name = queueModel.name,
                active = queueModel.queueItems.count { id -> downloadSystem.downloadMonitor.downloadListFlow.value.any { it.id == id && it is ProcessingDownloadItemState && (it.status is DownloadJobStatus.Downloading || it.status is DownloadJobStatus.Resuming) } },
                queued = queueModel.queueItems.count { id -> downloadSystem.downloadMonitor.downloadListFlow.value.any { it.id == id && (it is ProcessingDownloadItemState) && it.status == DownloadJobStatus.IDLE } },
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
        val category = Category(-1L, name, "", path, usePath, fileTypes.map { it.trim().trimStart('.') }.filter { it.isNotBlank() }, urlPatterns.map { it.trim() }.filter { it.isNotBlank() })
        categoryManager.addCustomCategory(category)
        return categoryManager.getCategories().maxByOrNull { it.id }?.id ?: error("Unable to determine new category ID")
    }

    override suspend fun renameCategory(id: Long, name: String) {
        categoryManager.updateCategory(id) { it.copy(name = name) }
    }

    override suspend fun deleteCategory(id: Long) {
        require(!categoryManager.isDefaultCategory(categoryManager.getCategoryById(id) ?: error("Category not found")))
        categoryManager.deleteCategory(id)
    }

    override fun listDownloads(): List<ApiDownloadModel> {
        return downloadSystem.downloadMonitor.downloadListFlow.value.map { item ->
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
                    queueId = queueManager.findItemInQueue(item.id),
                    queueName = queueManager.findItemInQueue(item.id)?.let { queueManager.getQueue(it).getQueueModel().name },
                )
                is CompletedDownloadItemState -> ApiDownloadModel(
                    id = item.id, name = item.name, folder = item.folder,
                    size = item.contentLength, progress = item.contentLength,
                    percent = 100, speed = 0, eta = 0,
                    status = "Completed", downloadLink = item.downloadLink,
                    dateAdded = item.dateAdded, startTime = item.startTime,
                    completeTime = item.completeTime,
                    queueId = queueManager.findItemInQueue(item.id),
                    queueName = queueManager.findItemInQueue(item.id)?.let { queueManager.getQueue(it).getQueueModel().name },
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
        downloadSystem.removeDownload(
            id = id,
            alsoRemoveFile = alsoRemoveFile,
            context = RemovedBy(User),
        )
    }

    override fun startQueue(id: Long) { queueManager.getQueue(id).start() }

    override fun stopQueue(id: Long) { queueManager.getQueue(id).stop() }

    override suspend fun addQueue(name: String): Long { queueManager.addQueue(name); return queueManager.getAll().maxOf { it.id } }
    override suspend fun deleteQueue(id: Long) { queueManager.deleteQueue(id) }
    override suspend fun renameQueue(id: Long, name: String) { queueManager.getQueue(id).setName(name) }
    override suspend fun setQueueConcurrency(id: Long, maxConcurrent: Int) { require(maxConcurrent > 0); queueManager.getQueue(id).setMaxConcurrent(maxConcurrent) }

    override suspend fun setQueueSchedule(
        id: Long,
        enabled: Boolean,
        activeDays: List<String>,
        autoStartEnabled: Boolean,
        startTime: String,
        autoStopEnabled: Boolean,
        endTime: String,
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
    }

    override suspend fun assignDownloadToQueue(downloadId: Long, queueId: Long) { queueManager.addToQueue(queueId, downloadId) }
    override suspend fun removeDownloadFromQueue(downloadId: Long) { queueManager.findItemInQueue(downloadId)?.let { queueManager.getQueue(it).removeFromQueue(downloadId) } }
    override suspend fun moveQueueItem(downloadId: Long, direction: Int) { val qid = queueManager.findItemInQueue(downloadId) ?: return; queueManager.getQueue(qid).move(listOf(downloadId), direction) }

    override suspend fun addDownload(task: NewDownloadTask): Long {
        val addDownloaderInUiProps = convertToDownloadSystemCredentials(task.downloadSource)
        val downloaderInUi = downloaderInUiRegistry.getDownloaderOf(
            addDownloaderInUiProps.credentials
        ) ?: error("Downloader for ${addDownloaderInUiProps.credentials::class.qualifiedName} not found")
        val downloadItem = downloaderInUi.createBareDownloadItem(
            addDownloaderInUiProps.credentials,
            basicDownloadItem = BasicDownloadItem(
                folder = task.folder ?: appSettings.saveLocation.value,
                name = task.name ?: addDownloaderInUiProps.extraConfig.suggestedName
                ?: task.downloadSource.link.substringAfterLast("/"),
            ),
        )
        val id = downloadSystem.addDownload(
            newDownload = NewDownloadItemProps(
                downloadItem = downloadItem,
                onDuplicateStrategy = OnDuplicateStrategy.default(),
                extraConfig = null,
                context = EmptyContext,
            ),
            queueId = task.queueId,
            categoryId = task.categoryId,
        )
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
