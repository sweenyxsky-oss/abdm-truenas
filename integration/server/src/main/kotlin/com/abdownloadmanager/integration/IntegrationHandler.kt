package com.abdownloadmanager.integration

import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiDownloadModel
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.NewDownloadTask

interface IntegrationHandler{
    suspend fun addDownloadByGui(request: AddDownloadsFromIntegration)
    fun listQueues(): List<ApiQueueModel>
    fun listDownloads(): List<ApiDownloadModel>
    suspend fun addDownload(task: NewDownloadTask): Long
    suspend fun pauseDownload(id: Long)
    suspend fun resumeDownload(id: Long)
    suspend fun retryDownload(id: Long)
    suspend fun removeDownload(id: Long, alsoRemoveFile: Boolean)
    fun startQueue(id: Long)
    fun stopQueue(id: Long)
    suspend fun addQueue(name: String): Long
    suspend fun deleteQueue(id: Long)
    suspend fun renameQueue(id: Long, name: String)
    suspend fun setQueueConcurrency(id: Long, maxConcurrent: Int)
    suspend fun assignDownloadToQueue(downloadId: Long, queueId: Long)
    suspend fun removeDownloadFromQueue(downloadId: Long)
    suspend fun moveQueueItem(downloadId: Long, direction: Int)
}
