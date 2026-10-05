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
import ir.amirab.downloader.utils.OnDuplicateStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class IntegrationHandlerImp : IntegrationHandler, KoinComponent {
    val appComponent by inject<AppComponent>()
    val downloadSystem by inject<DownloadSystem>()
    val queueManager by inject<QueueManager>()
    val appSettings by inject<AppRepository>()
    private val downloaderInUiRegistry by inject<DownloaderInUiRegistry>()

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

    override fun listQueues(): List<ApiQueueModel> {
        return queueManager.getAll().map { downloadQueue ->
            val queueModel = downloadQueue.getQueueModel()
            ApiQueueModel(
                id = queueModel.id,
                name = queueModel.name,
                active = queueModel.queueItems.count { id -> downloadSystem.downloadMonitor.downloadListFlow.value.any { it.id == id && (it is ProcessingDownloadItemState) && it.status is DownloadJobStatus.Downloading } },
                queued = queueModel.queueItems.count { id -> downloadSystem.downloadMonitor.downloadListFlow.value.any { it.id == id && (it is ProcessingDownloadItemState) && it.status == DownloadJobStatus.IDLE } },
                total = queueModel.queueItems.size,
                running = downloadQueue.isQueueActive,
            )
        }
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
                )
                is CompletedDownloadItemState -> ApiDownloadModel(
                    id = item.id, name = item.name, folder = item.folder,
                    size = item.contentLength, progress = item.contentLength,
                    percent = 100, speed = 0, eta = 0,
                    status = "Completed", downloadLink = item.downloadLink,
                    dateAdded = item.dateAdded, startTime = item.startTime,
                    completeTime = item.completeTime,
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
    override suspend fun assignDownloadToQueue(downloadId: Long, queueId: Long) { queueManager.addToQueue(queueId, downloadId) }
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
