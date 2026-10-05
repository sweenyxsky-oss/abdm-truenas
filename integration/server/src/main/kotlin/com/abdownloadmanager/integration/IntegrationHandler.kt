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
}
